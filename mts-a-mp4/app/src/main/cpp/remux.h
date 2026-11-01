/*
 * MTS a MP4: cambia el contenedor de un vídeo MPEG-TS/AVCHD (.mts, .m2ts)
 * a MP4 sin recomprimir el vídeo.
 */
#ifndef MTS_REMUX_H
#define MTS_REMUX_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/* Qué hacer con el audio. */
enum {
    REMUX_AUDIO_COPY = 0, /* copiarlo tal cual si el MP4 lo admite (si no, AAC) */
    REMUX_AUDIO_AAC = 1,  /* convertirlo siempre a AAC */
};

/* De dónde sale la fecha de grabación que se escribe en el MP4. */
enum {
    REMUX_DATE_NONE = 0,
    REMUX_DATE_CAMERA = 1, /* metadatos AVCHD (MDPM) que graba la cámara */
    REMUX_DATE_FILE = 2,   /* fecha de modificación del archivo */
};

/* Valor de retorno de mts_remux() cuando se cancela. */
#define REMUX_CANCELLED 1

typedef struct RemuxCallbacks {
    void *opaque;
    /* Progreso entre 0 y 1. Devuelve 0 para cancelar. */
    int (*progress)(void *opaque, double fraction);
    /* Se llama una vez, justo antes de empezar a escribir el MP4. */
    void (*info)(void *opaque, const char *video, const char *audio,
                 int64_t recorded_ms, int date_source);
    /* Convierte una hora local (zona del móvil) en milisegundos UTC.
     * Puede ser NULL; entonces la hora se toma como UTC. */
    int64_t (*local_to_utc_ms)(void *opaque, int year, int month, int day,
                               int hour, int minute, int second);
} RemuxCallbacks;

typedef struct RemuxOptions {
    int in_fd;                /* descriptor de lectura del .mts */
    int out_fd;               /* descriptor de lectura/escritura del .mp4 (vacío) */
    int64_t in_size;          /* tamaño del .mts en bytes, o <= 0 si no se sabe */
    int audio_mode;           /* REMUX_AUDIO_* */
    int64_t fallback_time_ms; /* fecha del archivo (ms UTC), o 0 */
} RemuxOptions;

/*
 * Devuelve 0 si todo va bien, REMUX_CANCELLED si se cancela o un código de
 * error negativo. En caso de error deja un mensaje en español en err.
 */
int mts_remux(const RemuxOptions *opt, const RemuxCallbacks *cb,
              char *err, size_t errlen);

#ifdef __cplusplus
}
#endif

#endif /* MTS_REMUX_H */
