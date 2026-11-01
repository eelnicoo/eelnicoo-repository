/*
 * MTS a MP4: remuxado MPEG-TS/AVCHD -> MP4 con FFmpeg.
 *
 * El vídeo se copia paquete a paquete (no se decodifica ni se recomprime), así
 * que la imagen es idéntica bit a bit. El audio se copia si el MP4 lo admite
 * (AC-3, E-AC-3, AAC, MP3, MP2); si no (LPCM, por ejemplo) o si se pide, se
 * convierte a AAC de alta tasa.
 *
 * Además lee la fecha de grabación que las cámaras AVCHD guardan en el vídeo
 * (bloque MDPM de los SEI de H.264) y la escribe como creation_time del MP4,
 * para que la galería ordene el vídeo por el día en que se grabó.
 */
#include "remux.h"

#include <errno.h>
#include <stdarg.h>
#include <stdio.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <time.h>
#include <unistd.h>

#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libavutil/audio_fifo.h>
#include <libavutil/channel_layout.h>
#include <libavutil/mathematics.h>
#include <libavutil/mem.h>
#include <libavutil/time.h>
#include <libswresample/swresample.h>

#define IO_BUF_SIZE (1 << 20)
/* Paquetes que se leen antes de escribir la cabecera, buscando la fecha. */
#define PREROLL_MAX_VIDEO_PKTS 120
#define PREROLL_MAX_BYTES (48 << 20)
#define PROGRESS_INTERVAL_US 150000

/* ------------------------------------------------------------------------ */
/* E/S sobre descriptores de archivo (los ParcelFileDescriptor de Android)   */

typedef struct FdIO {
    int fd;
    int64_t size;
} FdIO;

static int io_read(void *opaque, uint8_t *buf, int size)
{
    FdIO *io = opaque;
    for (;;) {
        ssize_t r = read(io->fd, buf, (size_t)size);
        if (r > 0)
            return (int)r;
        if (r == 0)
            return AVERROR_EOF;
        if (errno != EINTR)
            return AVERROR(errno);
    }
}

static int io_write(void *opaque, const uint8_t *buf, int size)
{
    FdIO *io = opaque;
    int done = 0;
    while (done < size) {
        ssize_t w = write(io->fd, buf + done, (size_t)(size - done));
        if (w < 0) {
            if (errno == EINTR)
                continue;
            return AVERROR(errno);
        }
        if (w == 0)
            return AVERROR(EIO);
        done += (int)w;
    }
    return size;
}

static int64_t io_seek(void *opaque, int64_t offset, int whence)
{
    FdIO *io = opaque;
    if (whence == AVSEEK_SIZE) {
        struct stat st;
        if (fstat(io->fd, &st) == 0 && S_ISREG(st.st_mode))
            return (int64_t)st.st_size;
        return io->size > 0 ? io->size : AVERROR(ENOSYS);
    }
    off_t r = lseek(io->fd, (off_t)offset, whence & ~AVSEEK_FORCE);
    return r < 0 ? AVERROR(errno) : (int64_t)r;
}

static int fd_is_seekable(int fd)
{
    return lseek(fd, 0, SEEK_CUR) >= 0;
}

/* ------------------------------------------------------------------------ */
/* Fecha de grabación AVCHD (MDPM)                                           */

typedef struct CamDate {
    int valid;
    int year, month, day, hour, minute, second;
    int tz_valid;
    int tz_minutes; /* desfase respecto a UTC, ya con el horario de verano */
} CamDate;

/* UUID 17ee8c60-f84d-11d9-8cd6-0800200c9a66 seguido de "MDPM". */
static const uint8_t MDPM_SIG[20] = {
    0x17, 0xee, 0x8c, 0x60, 0xf8, 0x4d, 0x11, 0xd9, 0x8c, 0xd6,
    0x08, 0x00, 0x20, 0x0c, 0x9a, 0x66, 'M', 'D', 'P', 'M',
};

static const uint8_t *find_bytes(const uint8_t *hay, size_t hlen,
                                 const uint8_t *needle, size_t nlen)
{
    if (hlen < nlen)
        return NULL;
    for (size_t i = 0; i + nlen <= hlen; i++) {
        if (hay[i] == needle[0] && !memcmp(hay + i, needle, nlen))
            return hay + i;
    }
    return NULL;
}

static int bcd(uint8_t v, int *out)
{
    int hi = v >> 4, lo = v & 0x0f;
    if (hi > 9 || lo > 9)
        return 0;
    *out = hi * 10 + lo;
    return 1;
}

/* Busca el bloque MDPM en un paquete H.264 y saca la fecha (etiquetas 0x18 y
 * 0x19, en BCD). Formato documentado por ExifTool (Image::ExifTool::H264). */
static int parse_mdpm(const uint8_t *data, int size, CamDate *cd)
{
    const uint8_t *p = find_bytes(data, (size_t)size, MDPM_SIG, sizeof(MDPM_SIG));
    if (!p)
        return 0;
    p += sizeof(MDPM_SIG);
    const uint8_t *end = data + size;

    /* Quita los bytes de prevención de emulación (00 00 03 -> 00 00). */
    uint8_t buf[1024];
    int n = 0, zeros = 0;
    for (; p < end && n < (int)sizeof(buf); p++) {
        uint8_t c = *p;
        if (zeros >= 2 && c == 0x03) {
            zeros = 0;
            continue;
        }
        buf[n++] = c;
        zeros = c == 0 ? zeros + 1 : 0;
    }
    if (n < 1)
        return 0;

    int count = buf[0], pos = 1;
    const uint8_t *e18 = NULL, *e19 = NULL;
    for (int i = 0; i < count && pos + 5 <= n; i++, pos += 5) {
        uint8_t tag = buf[pos];
        if (tag == 0x18)
            e18 = buf + pos + 1;
        else if (tag == 0x19 && e18) {
            e19 = buf + pos + 1;
            break;
        }
    }
    if (!e18 || !e19)
        return 0;

    int yh, yl, mo, d, h, mi, s;
    if (!bcd(e18[1], &yh) || !bcd(e18[2], &yl) || !bcd(e18[3], &mo) ||
        !bcd(e19[0], &d) || !bcd(e19[1], &h) || !bcd(e19[2], &mi) ||
        !bcd(e19[3], &s))
        return 0;
    int y = yh * 100 + yl;
    if (y < 1990 || y > 2099 || mo < 1 || mo > 12 || d < 1 || d > 31 ||
        h > 23 || mi > 59 || s > 59)
        return 0;

    cd->valid = 1;
    cd->year = y;
    cd->month = mo;
    cd->day = d;
    cd->hour = h;
    cd->minute = mi;
    cd->second = s;

    /* Byte de zona horaria: 0x40 horario de verano, 0x20 signo,
     * 0x1e horas, 0x01 media hora. 0xff = sin zona. */
    uint8_t tz = e18[0];
    if (tz == 0xff) {
        cd->tz_valid = 0;
    } else {
        int minutes = ((tz >> 1) & 0x0f) * 60 + ((tz & 0x01) ? 30 : 0);
        if (tz & 0x20)
            minutes = -minutes;
        if (tz & 0x40)
            minutes += 60;
        cd->tz_valid = 1;
        cd->tz_minutes = minutes;
    }
    return 1;
}

/* Días desde 1970-01-01 (algoritmo de Howard Hinnant). */
static int64_t days_from_civil(int y, int m, int d)
{
    y -= m <= 2;
    const int64_t era = (y >= 0 ? y : y - 399) / 400;
    const int64_t yoe = y - era * 400;
    const int64_t doy = (153 * (m + (m > 2 ? -3 : 9)) + 2) / 5 + d - 1;
    const int64_t doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
    return era * 146097 + doe - 719468;
}

static int64_t cam_date_to_utc_ms(const CamDate *cd, const RemuxCallbacks *cb)
{
    if (!cd->tz_valid && cb && cb->local_to_utc_ms) {
        int64_t ms = cb->local_to_utc_ms(cb->opaque, cd->year, cd->month, cd->day,
                                         cd->hour, cd->minute, cd->second);
        if (ms != INT64_MIN)
            return ms;
    }
    int64_t secs = days_from_civil(cd->year, cd->month, cd->day) * 86400 +
                   cd->hour * 3600 + cd->minute * 60 + cd->second;
    if (cd->tz_valid)
        secs -= (int64_t)cd->tz_minutes * 60;
    return secs * 1000;
}

static void format_iso8601(int64_t utc_ms, char *out, size_t len)
{
    time_t t = (time_t)(utc_ms / 1000);
    struct tm tm;
    gmtime_r(&t, &tm);
    strftime(out, len, "%Y-%m-%dT%H:%M:%S.000000Z", &tm);
}

/* ------------------------------------------------------------------------ */
/* Contexto                                                                  */

typedef struct OutStream {
    int in_index;
    int out_index;
    int is_video;
    int transcode;
    int seen_key;
    AVRational in_tb;
    int64_t ts_offset; /* en in_tb */
    int64_t last_dts;  /* en la base de tiempos de salida */

    /* Conversión de audio a AAC */
    AVCodecContext *dec;
    AVCodecContext *enc;
    SwrContext *swr;
    AVChannelLayout swr_in_layout;
    int swr_in_fmt;
    int swr_in_rate;
    AVAudioFifo *fifo;
    AVFrame *dframe;
    AVFrame *eframe;
    uint8_t **conv;
    int conv_cap;
    int64_t next_pts; /* en la base de tiempos del codificador */
} OutStream;

typedef struct Ctx {
    const RemuxOptions *opt;
    const RemuxCallbacks *cb;
    AVFormatContext *ifmt;
    AVFormatContext *ofmt;
    AVIOContext *ipb;
    AVIOContext *opb;
    FdIO in_io, out_io;
    OutStream *streams;
    int nb_streams;
    OutStream **map; /* índice de entrada -> OutStream (o NULL) */
    unsigned map_size;
    AVPacket *epkt;
    int64_t total_bytes;
    int64_t last_progress_us;
    double last_fraction;
} Ctx;

static void set_err(char *err, size_t errlen, const char *fmt, ...)
    __attribute__((format(printf, 3, 4)));

static void set_err(char *err, size_t errlen, const char *fmt, ...)
{
    if (!err || !errlen || err[0])
        return; /* conserva el primer error */
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(err, errlen, fmt, ap);
    va_end(ap);
}

static const char *codec_label(enum AVCodecID id)
{
    switch (id) {
    case AV_CODEC_ID_H264: return "H.264";
    case AV_CODEC_ID_HEVC: return "H.265";
    case AV_CODEC_ID_MPEG2VIDEO: return "MPEG-2";
    case AV_CODEC_ID_MPEG1VIDEO: return "MPEG-1";
    case AV_CODEC_ID_MPEG4: return "MPEG-4";
    case AV_CODEC_ID_VC1: return "VC-1";
    case AV_CODEC_ID_AC3: return "AC-3";
    case AV_CODEC_ID_EAC3: return "E-AC-3";
    case AV_CODEC_ID_AAC: return "AAC";
    case AV_CODEC_ID_AAC_LATM: return "AAC LATM";
    case AV_CODEC_ID_MP2: return "MP2";
    case AV_CODEC_ID_MP3: return "MP3";
    case AV_CODEC_ID_PCM_BLURAY:
    case AV_CODEC_ID_PCM_DVD: return "LPCM";
    case AV_CODEC_ID_DTS: return "DTS";
    case AV_CODEC_ID_TRUEHD: return "TrueHD";
    default: {
        const char *n = avcodec_get_name(id);
        return n ? n : "?";
    }
    }
}

static int video_copyable(enum AVCodecID id)
{
    switch (id) {
    case AV_CODEC_ID_H264:
    case AV_CODEC_ID_HEVC:
    case AV_CODEC_ID_MPEG2VIDEO:
    case AV_CODEC_ID_MPEG1VIDEO:
    case AV_CODEC_ID_MPEG4:
    case AV_CODEC_ID_VC1:
        return 1;
    default:
        return 0;
    }
}

static int audio_copyable(enum AVCodecID id)
{
    switch (id) {
    case AV_CODEC_ID_AAC:
    case AV_CODEC_ID_AC3:
    case AV_CODEC_ID_EAC3:
    case AV_CODEC_ID_MP3:
    case AV_CODEC_ID_MP2:
        return 1;
    default:
        return 0;
    }
}

static void describe_layout(const AVChannelLayout *l, char *out, size_t len)
{
    if (!av_channel_layout_compare(l, &(AVChannelLayout)AV_CHANNEL_LAYOUT_STEREO))
        snprintf(out, len, "estéreo");
    else if (!av_channel_layout_compare(l, &(AVChannelLayout)AV_CHANNEL_LAYOUT_MONO))
        snprintf(out, len, "mono");
    else if (av_channel_layout_describe(l, out, len) < 0)
        snprintf(out, len, "%d canales", l->nb_channels);
    else {
        /* "5.1(side)" -> "5.1" */
        char *paren = strchr(out, '(');
        if (paren && paren != out)
            *paren = 0;
    }
}

/* ------------------------------------------------------------------------ */
/* Escritura de paquetes                                                     */

/* Recibe el paquete ya en la base de tiempos de salida. Corrige marcas de
 * tiempo que el MP4 no acepta (igual que hace ffmpeg al copiar). */
static int write_out(Ctx *c, OutStream *os, AVPacket *pkt)
{
    if (pkt->dts == AV_NOPTS_VALUE)
        pkt->dts = pkt->pts;
    if (pkt->dts == AV_NOPTS_VALUE) {
        if (os->last_dts == AV_NOPTS_VALUE) {
            av_packet_unref(pkt);
            return 0;
        }
        pkt->dts = os->last_dts + FFMAX(pkt->duration, 1);
    }
    if (pkt->pts == AV_NOPTS_VALUE)
        pkt->pts = pkt->dts;
    if (os->last_dts != AV_NOPTS_VALUE && pkt->dts <= os->last_dts) {
        int64_t m = os->last_dts + 1;
        if (pkt->pts >= pkt->dts)
            pkt->pts = FFMAX(pkt->pts, m);
        pkt->dts = m;
    }
    if (pkt->pts < pkt->dts)
        pkt->pts = pkt->dts;
    os->last_dts = pkt->dts;
    pkt->stream_index = os->out_index;
    pkt->pos = -1;
    return av_interleaved_write_frame(c->ofmt, pkt);
}

static AVRational out_tb(Ctx *c, OutStream *os)
{
    return c->ofmt->streams[os->out_index]->time_base;
}

/* --- Audio a AAC -------------------------------------------------------- */

static int encode_write(Ctx *c, OutStream *os, AVFrame *f)
{
    int ret = avcodec_send_frame(os->enc, f);
    if (ret < 0 && ret != AVERROR_EOF)
        return ret;
    for (;;) {
        ret = avcodec_receive_packet(os->enc, c->epkt);
        if (ret == AVERROR(EAGAIN) || ret == AVERROR_EOF)
            return 0;
        if (ret < 0)
            return ret;
        av_packet_rescale_ts(c->epkt, os->enc->time_base, out_tb(c, os));
        ret = write_out(c, os, c->epkt);
        if (ret < 0)
            return ret;
    }
}

static int drain_fifo(Ctx *c, OutStream *os, int flush)
{
    const int fs = os->enc->frame_size > 0 ? os->enc->frame_size : 1024;
    while (av_audio_fifo_size(os->fifo) >= fs ||
           (flush && av_audio_fifo_size(os->fifo) > 0)) {
        int n = FFMIN(fs, av_audio_fifo_size(os->fifo));
        AVFrame *ef = os->eframe;
        av_frame_unref(ef);
        ef->nb_samples = n;
        ef->format = os->enc->sample_fmt;
        ef->sample_rate = os->enc->sample_rate;
        int ret = av_channel_layout_copy(&ef->ch_layout, &os->enc->ch_layout);
        if (ret < 0)
            return ret;
        ret = av_frame_get_buffer(ef, 0);
        if (ret < 0)
            return ret;
        if (av_audio_fifo_read(os->fifo, (void **)ef->data, n) < n)
            return AVERROR(EIO);
        ef->pts = os->next_pts;
        os->next_pts += n;
        ret = encode_write(c, os, ef);
        if (ret < 0)
            return ret;
    }
    return 0;
}

static int ensure_conv(OutStream *os, int samples)
{
    if (samples <= os->conv_cap)
        return 0;
    if (os->conv) {
        av_freep(&os->conv[0]);
        av_freep(&os->conv);
    }
    int ret = av_samples_alloc_array_and_samples(&os->conv, NULL,
                                                 os->enc->ch_layout.nb_channels,
                                                 samples, os->enc->sample_fmt, 0);
    if (ret < 0) {
        os->conv_cap = 0;
        return ret;
    }
    os->conv_cap = samples;
    return 0;
}

static int setup_swr(OutStream *os, const AVFrame *f)
{
    if (os->swr && os->swr_in_fmt == f->format && os->swr_in_rate == f->sample_rate &&
        !av_channel_layout_compare(&os->swr_in_layout, &f->ch_layout))
        return 0;
    swr_free(&os->swr);
    av_channel_layout_uninit(&os->swr_in_layout);
    int ret = swr_alloc_set_opts2(&os->swr, &os->enc->ch_layout, os->enc->sample_fmt,
                                  os->enc->sample_rate, &f->ch_layout, f->format,
                                  f->sample_rate, 0, NULL);
    if (ret < 0)
        return ret;
    ret = swr_init(os->swr);
    if (ret < 0)
        return ret;
    os->swr_in_fmt = f->format;
    os->swr_in_rate = f->sample_rate;
    return av_channel_layout_copy(&os->swr_in_layout, &f->ch_layout);
}

static int push_decoded(Ctx *c, OutStream *os, AVFrame *f)
{
    int ret = setup_swr(os, f);
    if (ret < 0)
        return ret;
    if (os->next_pts == AV_NOPTS_VALUE) {
        int64_t p = f->pts != AV_NOPTS_VALUE ? f->pts : f->best_effort_timestamp;
        os->next_pts = p != AV_NOPTS_VALUE ? av_rescale_q(p, os->in_tb, os->enc->time_base) : 0;
    }
    int out_count = swr_get_out_samples(os->swr, f->nb_samples);
    if (out_count < 0)
        return out_count;
    ret = ensure_conv(os, out_count + 256);
    if (ret < 0)
        return ret;
    int n = swr_convert(os->swr, os->conv, os->conv_cap,
                        (const uint8_t **)f->extended_data, f->nb_samples);
    if (n < 0)
        return n;
    if (n > 0 && av_audio_fifo_write(os->fifo, (void **)os->conv, n) < n)
        return AVERROR(ENOMEM);
    return drain_fifo(c, os, 0);
}

/* pkt == NULL vacía el decodificador. */
static int transcode_packet(Ctx *c, OutStream *os, AVPacket *pkt)
{
    int ret = avcodec_send_packet(os->dec, pkt);
    if (pkt)
        av_packet_unref(pkt);
    if (ret < 0 && ret != AVERROR_EOF && ret != AVERROR(EAGAIN))
        return 0; /* trama dañada: se salta, como hace ffmpeg */
    for (;;) {
        ret = avcodec_receive_frame(os->dec, os->dframe);
        if (ret == AVERROR(EAGAIN) || ret == AVERROR_EOF)
            return 0;
        if (ret < 0)
            return 0;
        ret = push_decoded(c, os, os->dframe);
        av_frame_unref(os->dframe);
        if (ret < 0)
            return ret;
    }
}

static int flush_transcoder(Ctx *c, OutStream *os)
{
    int ret = transcode_packet(c, os, NULL);
    if (ret < 0)
        return ret;
    if (os->swr) {
        for (;;) {
            ret = ensure_conv(os, 4096);
            if (ret < 0)
                return ret;
            int n = swr_convert(os->swr, os->conv, os->conv_cap, NULL, 0);
            if (n < 0)
                return n;
            if (n == 0)
                break;
            if (av_audio_fifo_write(os->fifo, (void **)os->conv, n) < n)
                return AVERROR(ENOMEM);
        }
    }
    if (os->next_pts == AV_NOPTS_VALUE)
        return 0; /* nunca llegó audio */
    ret = drain_fifo(c, os, 1);
    if (ret < 0)
        return ret;
    return encode_write(c, os, NULL);
}

static const AVChannelLayout *aac_layout_for(const AVChannelLayout *in)
{
    static const AVChannelLayout mono = AV_CHANNEL_LAYOUT_MONO;
    static const AVChannelLayout stereo = AV_CHANNEL_LAYOUT_STEREO;
    static const AVChannelLayout surround = AV_CHANNEL_LAYOUT_SURROUND;
    static const AVChannelLayout quad = AV_CHANNEL_LAYOUT_4POINT0;
    static const AVChannelLayout five0 = AV_CHANNEL_LAYOUT_5POINT0_BACK;
    static const AVChannelLayout five1 = AV_CHANNEL_LAYOUT_5POINT1_BACK;
    static const AVChannelLayout seven1 = AV_CHANNEL_LAYOUT_7POINT1;
    const AVChannelLayout *all[] = {&mono, &stereo, &surround, &quad, &five0, &five1, &seven1};
    for (size_t i = 0; i < sizeof(all) / sizeof(all[0]); i++)
        if (!av_channel_layout_compare(in, all[i]))
            return all[i];

    int ch = in->nb_channels;
    int has_lfe = in->order == AV_CHANNEL_ORDER_NATIVE &&
                  (in->u.mask & (AV_CH_LOW_FREQUENCY | AV_CH_LOW_FREQUENCY_2));
    int main_ch = ch - (has_lfe ? 1 : 0);
    if (main_ch <= 1)
        return &mono;
    if (main_ch == 2)
        return &stereo;
    switch (ch) {
    case 3: return &surround;
    case 4: return &quad;
    case 5: return &five0;
    case 6:
    case 7: return &five1;
    default: return &seven1;
    }
}

static int aac_rate_ok(int sr)
{
    static const int rates[] = {96000, 88200, 64000, 48000, 44100, 32000,
                                24000, 22050, 16000, 12000, 11025, 8000};
    for (size_t i = 0; i < sizeof(rates) / sizeof(rates[0]); i++)
        if (rates[i] == sr)
            return 1;
    return 0;
}

static int setup_transcoder(Ctx *c, OutStream *os, AVStream *ist, AVStream *ost)
{
    const AVCodec *dcodec = avcodec_find_decoder(ist->codecpar->codec_id);
    const AVCodec *ecodec = avcodec_find_encoder(AV_CODEC_ID_AAC);
    if (!dcodec || !ecodec)
        return AVERROR_DECODER_NOT_FOUND;

    os->dec = avcodec_alloc_context3(dcodec);
    if (!os->dec)
        return AVERROR(ENOMEM);
    int ret = avcodec_parameters_to_context(os->dec, ist->codecpar);
    if (ret < 0)
        return ret;
    os->dec->pkt_timebase = ist->time_base;
    AVDictionary *dopts = NULL;
    /* Sin compresión de rango dinámico: el sonido tal como se grabó. */
    av_dict_set(&dopts, "drc_scale", "0", 0);
    ret = avcodec_open2(os->dec, dcodec, &dopts);
    av_dict_free(&dopts);
    if (ret < 0)
        return ret;

    AVChannelLayout in_layout = {0};
    if (ist->codecpar->ch_layout.nb_channels > 0 &&
        ist->codecpar->ch_layout.order != AV_CHANNEL_ORDER_UNSPEC)
        ret = av_channel_layout_copy(&in_layout, &ist->codecpar->ch_layout);
    else
        av_channel_layout_default(&in_layout, FFMAX(ist->codecpar->ch_layout.nb_channels, 2));
    if (ret < 0)
        return ret;
    const AVChannelLayout *layout = aac_layout_for(&in_layout);
    av_channel_layout_uninit(&in_layout);

    int sr = ist->codecpar->sample_rate > 0 ? ist->codecpar->sample_rate : 48000;
    if (!aac_rate_ok(sr))
        sr = 48000;

    os->enc = avcodec_alloc_context3(ecodec);
    if (!os->enc)
        return AVERROR(ENOMEM);
    ret = av_channel_layout_copy(&os->enc->ch_layout, layout);
    if (ret < 0)
        return ret;
    int ch = layout->nb_channels;
    int64_t br = ch == 1 ? 192000 : FFMIN((int64_t)ch * 160000, 640000);
    os->enc->sample_fmt = AV_SAMPLE_FMT_FLTP;
    os->enc->sample_rate = sr;
    os->enc->bit_rate = br;
    os->enc->time_base = (AVRational){1, sr};
    if (c->ofmt->oformat->flags & AVFMT_GLOBALHEADER)
        os->enc->flags |= AV_CODEC_FLAG_GLOBAL_HEADER;
    ret = avcodec_open2(os->enc, ecodec, NULL);
    if (ret < 0)
        return ret;
    ret = avcodec_parameters_from_context(ost->codecpar, os->enc);
    if (ret < 0)
        return ret;
    ost->time_base = os->enc->time_base;

    os->fifo = av_audio_fifo_alloc(os->enc->sample_fmt, ch, 4096);
    os->dframe = av_frame_alloc();
    os->eframe = av_frame_alloc();
    if (!os->fifo || !os->dframe || !os->eframe)
        return AVERROR(ENOMEM);
    os->next_pts = AV_NOPTS_VALUE;
    return 0;
}

/* --- Bucle principal ----------------------------------------------------- */

static int handle_packet(Ctx *c, AVPacket *pkt)
{
    /* MPEG-TS puede añadir pistas a mitad del archivo: esas se ignoran. */
    OutStream *os = pkt->stream_index >= 0 && (unsigned)pkt->stream_index < c->map_size
                        ? c->map[pkt->stream_index] : NULL;
    if (!os) {
        av_packet_unref(pkt);
        return 0;
    }
    if (pkt->pts != AV_NOPTS_VALUE)
        pkt->pts -= os->ts_offset;
    if (pkt->dts != AV_NOPTS_VALUE)
        pkt->dts -= os->ts_offset;

    if (os->is_video && !os->seen_key) {
        /* Como ffmpeg: lo anterior al primer fotograma clave no se puede ver. */
        if (!(pkt->flags & AV_PKT_FLAG_KEY)) {
            av_packet_unref(pkt);
            return 0;
        }
        os->seen_key = 1;
    }

    if (os->transcode)
        return transcode_packet(c, os, pkt);

    av_packet_rescale_ts(pkt, os->in_tb, out_tb(c, os));
    return write_out(c, os, pkt);
}

static int report_progress(Ctx *c, int force)
{
    if (!c->cb || !c->cb->progress)
        return 1;
    int64_t now = av_gettime_relative();
    if (!force && now - c->last_progress_us < PROGRESS_INTERVAL_US)
        return 1;
    c->last_progress_us = now;
    double f = 0;
    if (c->total_bytes > 0 && c->ifmt->pb) {
        int64_t pos = avio_tell(c->ifmt->pb);
        if (pos > 0)
            f = (double)pos / (double)c->total_bytes;
    }
    if (f > 1)
        f = 1;
    if (f < c->last_fraction)
        f = c->last_fraction;
    c->last_fraction = f;
    return c->cb->progress(c->cb->opaque, f);
}

typedef struct PktNode {
    AVPacket *pkt;
    struct PktNode *next;
} PktNode;

static void free_queue(PktNode *q)
{
    while (q) {
        PktNode *n = q->next;
        av_packet_free(&q->pkt);
        av_free(q);
        q = n;
    }
}

static int error_text(int code, char *err, size_t errlen)
{
    char buf[AV_ERROR_MAX_STRING_SIZE];
    if (code == AVERROR(ENOSPC))
        set_err(err, errlen, "No queda espacio en el móvil para guardar el MP4.");
    else if (code == AVERROR(EIO))
        set_err(err, errlen, "Error de lectura o escritura (¿se ha quitado la tarjeta o el USB?).");
    else {
        av_strerror(code, buf, sizeof(buf));
        set_err(err, errlen, "Error al convertir: %s.", buf);
    }
    return code;
}

int mts_remux(const RemuxOptions *opt, const RemuxCallbacks *cb, char *err, size_t errlen)
{
    Ctx ctx;
    Ctx *c = &ctx;
    memset(c, 0, sizeof(*c));
    c->opt = opt;
    c->cb = cb;
    if (err && errlen)
        err[0] = 0;

    int ret = 0;
    int header_written = 0;
    PktNode *queue = NULL, **qtail = &queue;
    AVPacket *pkt = av_packet_alloc();
    c->epkt = av_packet_alloc();
    if (!pkt || !c->epkt) {
        ret = AVERROR(ENOMEM);
        goto fail;
    }

    /* --- Entrada --- */
    c->in_io.fd = opt->in_fd;
    c->in_io.size = opt->in_size;
    int seekable = fd_is_seekable(opt->in_fd);
    uint8_t *ibuf = av_malloc(IO_BUF_SIZE);
    if (!ibuf) {
        ret = AVERROR(ENOMEM);
        goto fail;
    }
    c->ipb = avio_alloc_context(ibuf, IO_BUF_SIZE, 0, &c->in_io, io_read, NULL,
                                seekable ? io_seek : NULL);
    if (!c->ipb) {
        av_free(ibuf);
        ret = AVERROR(ENOMEM);
        goto fail;
    }
    if (!seekable)
        c->ipb->seekable = 0;

    c->ifmt = avformat_alloc_context();
    if (!c->ifmt) {
        ret = AVERROR(ENOMEM);
        goto fail;
    }
    c->ifmt->pb = c->ipb;
    c->ifmt->flags |= AVFMT_FLAG_CUSTOM_IO;

    AVDictionary *iopts = NULL;
    av_dict_set(&iopts, "probesize", "20000000", 0);
    av_dict_set(&iopts, "analyzeduration", "10000000", 0);
    ret = avformat_open_input(&c->ifmt, NULL, NULL, &iopts);
    av_dict_free(&iopts);
    if (ret < 0) {
        set_err(err, errlen, "No se pudo abrir: no parece un vídeo MTS/M2TS válido.");
        goto fail;
    }
    ret = avformat_find_stream_info(c->ifmt, NULL);
    if (ret < 0) {
        set_err(err, errlen, "No se pudo analizar el vídeo (¿archivo dañado?).");
        goto fail;
    }

    c->total_bytes = opt->in_size > 0 ? opt->in_size : 0;
    if (c->total_bytes <= 0 && seekable)
        c->total_bytes = avio_size(c->ifmt->pb);

    int vidx = av_find_best_stream(c->ifmt, AVMEDIA_TYPE_VIDEO, -1, -1, NULL, 0);
    if (vidx < 0) {
        ret = AVERROR_STREAM_NOT_FOUND;
        set_err(err, errlen, "El archivo no tiene ninguna pista de vídeo.");
        goto fail;
    }
    AVStream *vst = c->ifmt->streams[vidx];
    if (!video_copyable(vst->codecpar->codec_id)) {
        ret = AVERROR_PATCHWELCOME;
        set_err(err, errlen, "El vídeo está en %s, que no cabe en un MP4 sin recomprimir.",
                codec_label(vst->codecpar->codec_id));
        goto fail;
    }

    /* --- Salida --- */
    ret = avformat_alloc_output_context2(&c->ofmt, NULL, "mp4", NULL);
    if (ret < 0 || !c->ofmt) {
        if (ret >= 0)
            ret = AVERROR_MUXER_NOT_FOUND;
        goto fail;
    }

    c->streams = av_calloc(c->ifmt->nb_streams, sizeof(*c->streams));
    c->map = av_calloc(c->ifmt->nb_streams, sizeof(*c->map));
    if (!c->streams || !c->map) {
        ret = AVERROR(ENOMEM);
        goto fail;
    }
    c->map_size = c->ifmt->nb_streams;

    char vdesc[160] = "";
    char adesc[256] = "";

    /* Primero el vídeo, luego las pistas de audio en su orden. */
    for (int pass = 0; pass < 2; pass++) {
        for (unsigned i = 0; i < c->ifmt->nb_streams; i++) {
            AVStream *ist = c->ifmt->streams[i];
            AVCodecParameters *par = ist->codecpar;
            int is_video = (int)i == vidx;
            if (pass == 0 && !is_video)
                continue;
            if (pass == 1 && (is_video || par->codec_type != AVMEDIA_TYPE_AUDIO))
                continue;
            if (pass == 1 && (par->codec_id == AV_CODEC_ID_NONE ||
                              par->ch_layout.nb_channels <= 0 || par->sample_rate <= 0)) {
                av_log(NULL, AV_LOG_WARNING, "Pista de audio %u sin parámetros: se omite\n", i);
                continue;
            }

            int transcode = 0;
            if (!is_video) {
                transcode = opt->audio_mode == REMUX_AUDIO_AAC || !audio_copyable(par->codec_id);
                if (transcode && !avcodec_find_decoder(par->codec_id)) {
                    av_log(NULL, AV_LOG_WARNING, "Audio %s sin decodificador: se omite\n",
                           codec_label(par->codec_id));
                    char tmp[64];
                    snprintf(tmp, sizeof(tmp), "%s%s (no compatible, omitido)",
                             adesc[0] ? " + " : "", codec_label(par->codec_id));
                    strncat(adesc, tmp, sizeof(adesc) - strlen(adesc) - 1);
                    continue;
                }
            }

            AVStream *ost = avformat_new_stream(c->ofmt, NULL);
            if (!ost) {
                ret = AVERROR(ENOMEM);
                goto fail;
            }
            OutStream *os = &c->streams[c->nb_streams++];
            os->in_index = (int)i;
            os->out_index = ost->index;
            os->is_video = is_video;
            os->transcode = transcode;
            os->in_tb = ist->time_base;
            os->last_dts = AV_NOPTS_VALUE;
            os->next_pts = AV_NOPTS_VALUE;
            int64_t start = c->ifmt->start_time != AV_NOPTS_VALUE ? c->ifmt->start_time : 0;
            os->ts_offset = av_rescale_q(start, AV_TIME_BASE_Q, ist->time_base);
            c->map[i] = os;

            const AVDictionaryEntry *lang = av_dict_get(ist->metadata, "language", NULL, 0);
            if (lang)
                av_dict_set(&ost->metadata, "language", lang->value, 0);

            if (!transcode) {
                ret = avcodec_parameters_copy(ost->codecpar, par);
                if (ret < 0)
                    goto fail;
                ost->codecpar->codec_tag = 0;
                ost->time_base = ist->time_base;
                ost->avg_frame_rate = ist->avg_frame_rate;
                ost->r_frame_rate = ist->r_frame_rate;
                ost->sample_aspect_ratio = ist->sample_aspect_ratio;
                if (!ost->codecpar->sample_aspect_ratio.num)
                    ost->codecpar->sample_aspect_ratio = ist->sample_aspect_ratio;
            } else {
                ret = setup_transcoder(c, os, ist, ost);
                if (ret < 0) {
                    set_err(err, errlen, "No se pudo preparar la conversión del audio a AAC.");
                    goto fail;
                }
            }

            if (is_video) {
                double fps = av_q2d(ist->avg_frame_rate);
                char fpsbuf[32] = "";
                if (fps > 0)
                    snprintf(fpsbuf, sizeof(fpsbuf), " · %.3g fps", fps);
                const char *scan = "";
                if (par->field_order == AV_FIELD_TT || par->field_order == AV_FIELD_BB ||
                    par->field_order == AV_FIELD_TB || par->field_order == AV_FIELD_BT)
                    scan = "i";
                snprintf(vdesc, sizeof(vdesc), "%s %d×%d%s%s · copiado sin recomprimir",
                         codec_label(par->codec_id), par->width, par->height, scan, fpsbuf);
            } else {
                char lay[48], tmp[160];
                describe_layout(&par->ch_layout, lay, sizeof(lay));
                if (transcode) {
                    snprintf(tmp, sizeof(tmp), "%s%s %s → AAC %d kb/s", adesc[0] ? " + " : "",
                             codec_label(par->codec_id), lay,
                             (int)(os->enc->bit_rate / 1000));
                } else {
                    snprintf(tmp, sizeof(tmp), "%s%s %s · %d kHz · copiado", adesc[0] ? " + " : "",
                             codec_label(par->codec_id), lay,
                             (par->sample_rate + 500) / 1000);
                }
                strncat(adesc, tmp, sizeof(adesc) - strlen(adesc) - 1);
            }
        }
    }
    if (!adesc[0])
        snprintf(adesc, sizeof(adesc), "sin audio");

    /* --- Lectura previa: busca la fecha de grabación --- */
    CamDate cd = {0};
    int video_pkts = 0;
    int64_t queued_bytes = 0;
    int eof = 0;
    int check_mdpm = vst->codecpar->codec_id == AV_CODEC_ID_H264;
    for (;;) {
        ret = av_read_frame(c->ifmt, pkt);
        if (ret == AVERROR_EOF) {
            eof = 1;
            ret = 0;
            break;
        }
        if (ret == AVERROR(EAGAIN))
            continue;
        if (ret < 0) {
            error_text(ret, err, errlen);
            goto fail;
        }
        if (pkt->stream_index == vidx) {
            video_pkts++;
            if (check_mdpm && !cd.valid)
                parse_mdpm(pkt->data, pkt->size, &cd);
        }
        PktNode *node = av_mallocz(sizeof(*node));
        if (!node) {
            ret = AVERROR(ENOMEM);
            goto fail;
        }
        node->pkt = av_packet_alloc();
        if (!node->pkt) {
            av_free(node);
            ret = AVERROR(ENOMEM);
            goto fail;
        }
        queued_bytes += pkt->size;
        av_packet_move_ref(node->pkt, pkt);
        *qtail = node;
        qtail = &node->next;
        if (cd.valid || !check_mdpm || video_pkts >= PREROLL_MAX_VIDEO_PKTS ||
            queued_bytes >= PREROLL_MAX_BYTES)
            break;
    }

    int64_t recorded_ms = 0;
    int date_source = REMUX_DATE_NONE;
    if (cd.valid) {
        recorded_ms = cam_date_to_utc_ms(&cd, cb);
        date_source = REMUX_DATE_CAMERA;
    } else if (opt->fallback_time_ms > 0) {
        recorded_ms = opt->fallback_time_ms;
        date_source = REMUX_DATE_FILE;
    }
    if (date_source != REMUX_DATE_NONE) {
        char iso[40];
        format_iso8601(recorded_ms, iso, sizeof(iso));
        av_dict_set(&c->ofmt->metadata, "creation_time", iso, 0);
    }
    if (cb && cb->info)
        cb->info(cb->opaque, vdesc, adesc, recorded_ms, date_source);

    /* --- Cabecera --- */
    c->out_io.fd = opt->out_fd;
    uint8_t *obuf = av_malloc(IO_BUF_SIZE);
    if (!obuf) {
        ret = AVERROR(ENOMEM);
        goto fail;
    }
    c->opb = avio_alloc_context(obuf, IO_BUF_SIZE, 1, &c->out_io, NULL, io_write, io_seek);
    if (!c->opb) {
        av_free(obuf);
        ret = AVERROR(ENOMEM);
        goto fail;
    }
    c->ofmt->pb = c->opb;
    c->ofmt->flags |= AVFMT_FLAG_CUSTOM_IO;

    ret = avformat_write_header(c->ofmt, NULL);
    if (ret < 0) {
        set_err(err, errlen, "No se pudo crear el MP4 con estas pistas.");
        goto fail;
    }
    header_written = 1;

    /* --- Copia --- */
    while (queue) {
        PktNode *node = queue;
        queue = node->next;
        if (!queue)
            qtail = &queue;
        ret = handle_packet(c, node->pkt);
        av_packet_free(&node->pkt);
        av_free(node);
        if (ret < 0) {
            error_text(ret, err, errlen);
            goto fail;
        }
    }

    while (!eof) {
        if (!report_progress(c, 0)) {
            ret = REMUX_CANCELLED;
            goto fail;
        }
        ret = av_read_frame(c->ifmt, pkt);
        if (ret == AVERROR_EOF)
            break;
        if (ret == AVERROR(EAGAIN))
            continue;
        if (ret < 0) {
            error_text(ret, err, errlen);
            goto fail;
        }
        ret = handle_packet(c, pkt);
        if (ret < 0) {
            error_text(ret, err, errlen);
            goto fail;
        }
    }

    for (int i = 0; i < c->nb_streams; i++) {
        if (c->streams[i].transcode) {
            ret = flush_transcoder(c, &c->streams[i]);
            if (ret < 0) {
                error_text(ret, err, errlen);
                goto fail;
            }
        }
    }

    ret = av_write_trailer(c->ofmt);
    header_written = 0;
    if (ret < 0) {
        error_text(ret, err, errlen);
        goto fail;
    }
    avio_flush(c->opb);
    if (c->opb->error < 0) {
        ret = c->opb->error;
        error_text(ret, err, errlen);
        goto fail;
    }
    if (cb && cb->progress)
        cb->progress(cb->opaque, 1.0);
    ret = 0;

fail:
    if (header_written)
        av_write_trailer(c->ofmt); /* libera el estado del muxer */
    free_queue(queue);
    av_packet_free(&pkt);
    av_packet_free(&c->epkt);
    for (int i = 0; i < c->nb_streams; i++) {
        OutStream *os = &c->streams[i];
        avcodec_free_context(&os->dec);
        avcodec_free_context(&os->enc);
        swr_free(&os->swr);
        av_channel_layout_uninit(&os->swr_in_layout);
        if (os->fifo)
            av_audio_fifo_free(os->fifo);
        av_frame_free(&os->dframe);
        av_frame_free(&os->eframe);
        if (os->conv) {
            av_freep(&os->conv[0]);
            av_freep(&os->conv);
        }
    }
    av_free(c->streams);
    av_free(c->map);
    if (c->ofmt) {
        c->ofmt->pb = NULL;
        avformat_free_context(c->ofmt);
    }
    if (c->opb) {
        av_freep(&c->opb->buffer);
        avio_context_free(&c->opb);
    }
    if (c->ifmt)
        avformat_close_input(&c->ifmt);
    if (c->ipb) {
        av_freep(&c->ipb->buffer);
        avio_context_free(&c->ipb);
    }
    if (ret < 0 && ret != REMUX_CANCELLED)
        error_text(ret, err, errlen);
    return ret;
}
