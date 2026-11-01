package dev.nico.mtsamp4

/** Lo llama el código nativo durante la conversión (por JNI, por nombre). */
interface RemuxListener {
    /** Progreso entre 0 y 1. Devuelve false para cancelar. */
    fun onProgress(fraction: Float): Boolean

    /** Pistas encontradas y fecha de grabación (ms UTC, 0 si no hay). */
    fun onInfo(video: String, audio: String, recordedMs: Long, dateSource: Int)

    /** Hora local del móvil a ms UTC (para fechas de cámara sin zona horaria). */
    fun localToUtcMs(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int): Long
}

object NativeRemuxer {
    const val AUDIO_COPY = 0
    const val AUDIO_AAC = 1

    const val DATE_NONE = 0
    const val DATE_CAMERA = 1
    const val DATE_FILE = 2

    const val CANCELLED = "__CANCELLED__"

    init {
        System.loadLibrary("mtsremux")
    }

    /**
     * Convierte el .mts de [inFd] en un MP4 escrito en [outFd] (que debe estar
     * vacío y abierto en lectura/escritura). Devuelve null si todo va bien,
     * [CANCELLED] si se cancela o un mensaje de error.
     */
    @JvmStatic
    external fun remux(
        inFd: Int,
        outFd: Int,
        inSize: Long,
        audioMode: Int,
        fallbackMs: Long,
        listener: RemuxListener,
    ): String?
}
