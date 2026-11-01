/* Prueba en el PC del mismo remux.c que usa la app:
 *   remux_cli entrada.mts salida.mp4 [aac] [mtime_ms]
 */
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

#include "remux.h"

static int on_progress(void *o, double f)
{
    (void)o;
    fprintf(stderr, "\rprogreso %5.1f%%", f * 100);
    const char *cancel = getenv("CANCEL_AT"); /* para probar la cancelación */
    return !(cancel && f >= atof(cancel));
}

static void on_info(void *o, const char *v, const char *a, int64_t ms, int src)
{
    (void)o;
    fprintf(stderr, "VIDEO: %s\nAUDIO: %s\nFECHA_MS: %lld\nFUENTE_FECHA: %d\n", v, a,
            (long long)ms, src);
}

int main(int argc, char **argv)
{
    if (argc < 3) {
        fprintf(stderr, "uso: %s entrada.mts salida.mp4 [aac] [mtime_ms]\n", argv[0]);
        return 2;
    }
    int in = open(argv[1], O_RDONLY);
    int out = open(argv[2], O_RDWR | O_CREAT | O_TRUNC, 0644);
    if (in < 0 || out < 0) {
        perror("open");
        return 2;
    }
    struct stat st;
    fstat(in, &st);
    RemuxOptions opt = {
        .in_fd = in,
        .out_fd = out,
        .in_size = getenv("NO_SIZE") ? 0 : (int64_t)st.st_size,
        .audio_mode = argc > 3 && !strcmp(argv[3], "aac") ? REMUX_AUDIO_AAC : REMUX_AUDIO_COPY,
        .fallback_time_ms = argc > 4 ? atoll(argv[4]) : 0,
    };
    RemuxCallbacks cb = {.progress = on_progress, .info = on_info};
    char err[512];
    int r = mts_remux(&opt, &cb, err, sizeof(err));
    fprintf(stderr, "\n");
    close(in);
    close(out);
    if (r) {
        fprintf(stderr, "ERROR (%d): %s\n", r, err);
        return 1;
    }
    fprintf(stderr, "OK\n");
    return 0;
}
