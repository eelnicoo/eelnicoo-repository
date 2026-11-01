/* Puente JNI entre NativeRemuxer (Kotlin) y remux.c. */
#include <jni.h>
#include <stdarg.h>
#include <stdint.h>
#include <stdio.h>

#include <android/log.h>
#include <libavutil/log.h>

#include "remux.h"

#define TAG "MtsRemux"

typedef struct JniCtx {
    JNIEnv *env;
    jobject listener;
    jmethodID on_progress;
    jmethodID on_info;
    jmethodID local_to_utc;
} JniCtx;

static int j_progress(void *opaque, double fraction)
{
    JniCtx *j = opaque;
    jboolean keep = (*j->env)->CallBooleanMethod(j->env, j->listener, j->on_progress,
                                                 (jfloat)fraction);
    if ((*j->env)->ExceptionCheck(j->env)) {
        (*j->env)->ExceptionClear(j->env);
        return 0;
    }
    return keep == JNI_TRUE;
}

static void j_info(void *opaque, const char *video, const char *audio, int64_t recorded_ms,
                   int date_source)
{
    JniCtx *j = opaque;
    JNIEnv *env = j->env;
    jstring v = (*env)->NewStringUTF(env, video);
    jstring a = (*env)->NewStringUTF(env, audio);
    if (v && a)
        (*env)->CallVoidMethod(env, j->listener, j->on_info, v, a, (jlong)recorded_ms,
                               (jint)date_source);
    if ((*env)->ExceptionCheck(env))
        (*env)->ExceptionClear(env);
    if (v)
        (*env)->DeleteLocalRef(env, v);
    if (a)
        (*env)->DeleteLocalRef(env, a);
}

static int64_t j_local_to_utc(void *opaque, int y, int mo, int d, int h, int mi, int s)
{
    JniCtx *j = opaque;
    jlong r = (*j->env)->CallLongMethod(j->env, j->listener, j->local_to_utc, (jint)y, (jint)mo,
                                        (jint)d, (jint)h, (jint)mi, (jint)s);
    if ((*j->env)->ExceptionCheck(j->env)) {
        (*j->env)->ExceptionClear(j->env);
        return INT64_MIN;
    }
    return (int64_t)r;
}

static void log_callback(void *avcl, int level, const char *fmt, va_list vl)
{
    (void)avcl;
    if (level > AV_LOG_WARNING)
        return;
    char line[1024];
    vsnprintf(line, sizeof(line), fmt, vl);
    __android_log_write(level <= AV_LOG_ERROR ? ANDROID_LOG_ERROR : ANDROID_LOG_WARN, TAG, line);
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved)
{
    (void)vm;
    (void)reserved;
    av_log_set_callback(log_callback);
    return JNI_VERSION_1_6;
}

/*
 * Devuelve null si ha ido bien, "__CANCELLED__" si se ha cancelado o un
 * mensaje de error en español.
 */
JNIEXPORT jstring JNICALL
Java_dev_nico_mtsamp4_NativeRemuxer_remux(JNIEnv *env, jclass clazz, jint in_fd, jint out_fd,
                                          jlong in_size, jint audio_mode, jlong fallback_ms,
                                          jobject listener)
{
    (void)clazz;
    jclass lc = (*env)->GetObjectClass(env, listener);
    JniCtx j = {
        .env = env,
        .listener = listener,
        .on_progress = (*env)->GetMethodID(env, lc, "onProgress", "(F)Z"),
        .on_info = (*env)->GetMethodID(env, lc, "onInfo",
                                       "(Ljava/lang/String;Ljava/lang/String;JI)V"),
        .local_to_utc = (*env)->GetMethodID(env, lc, "localToUtcMs", "(IIIIII)J"),
    };
    (*env)->DeleteLocalRef(env, lc);
    if (!j.on_progress || !j.on_info || !j.local_to_utc) {
        (*env)->ExceptionClear(env);
        return (*env)->NewStringUTF(env, "Error interno: listener JNI incompleto.");
    }

    RemuxOptions opt = {
        .in_fd = in_fd,
        .out_fd = out_fd,
        .in_size = in_size,
        .audio_mode = audio_mode,
        .fallback_time_ms = fallback_ms,
    };
    RemuxCallbacks cb = {
        .opaque = &j,
        .progress = j_progress,
        .info = j_info,
        .local_to_utc_ms = j_local_to_utc,
    };
    char err[512] = {0};
    int r = mts_remux(&opt, &cb, err, sizeof(err));
    if (r == 0)
        return NULL;
    if (r == REMUX_CANCELLED)
        return (*env)->NewStringUTF(env, "__CANCELLED__");
    return (*env)->NewStringUTF(env, err[0] ? err : "Error desconocido al convertir.");
}
