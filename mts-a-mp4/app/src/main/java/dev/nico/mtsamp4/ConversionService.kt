package dev.nico.mtsamp4

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.StatFs
import android.provider.MediaStore
import android.text.format.Formatter
import android.util.Log
import java.io.FileNotFoundException
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Servicio en primer plano que va convirtiendo la cola, para que siga aunque
 * la app pase a segundo plano o se apague la pantalla.
 */
class ConversionService : Service() {

    companion object {
        private const val TAG = "ConversionService"
        private const val CHANNEL_PROGRESS = "progreso"
        private const val CHANNEL_DONE = "terminado"
        private const val NOTIF_PROGRESS = 1
        private const val NOTIF_DONE = 2
        private const val ACTION_CANCEL = "dev.nico.mtsamp4.CANCELAR"

        /** Carpeta de destino dentro de Películas. */
        const val OUTPUT_DIR = "MTS a MP4"

        private val lock = Any()

        @Volatile
        private var running = false

        val isRunning get() = running

        fun start(context: Context) {
            try {
                context.startForegroundService(Intent(context, ConversionService::class.java))
            } catch (e: Exception) {
                // Android no deja empezar desde segundo plano: se reintenta al volver a la app.
                Log.w(TAG, "No se pudo iniciar el servicio", e)
            }
        }
    }

    @Volatile
    private var latestStartId = 0
    private lateinit var nm: NotificationManager
    private var lastNotifyAt = 0L
    private var doneCount = 0
    private var failedCount = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_PROGRESS, "Conversión en curso", NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false) },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_DONE, "Conversión terminada", NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground(progressNotification("Preparando…", -1))
        if (intent?.action == ACTION_CANCEL) Conversions.cancelAll()
        synchronized(lock) {
            latestStartId = startId
            if (!running) {
                running = true
                doneCount = 0
                failedCount = 0
                Thread({ runQueue() }, "conversion").start()
            }
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        // Android 15+: límite de tiempo de los servicios en primer plano.
        Conversions.cancelAll()
    }

    private fun startInForeground(n: Notification) {
        val type = if (Build.VERSION.SDK_INT >= 35) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        }
        startForeground(NOTIF_PROGRESS, n, type)
    }

    private fun runQueue() {
        while (true) {
            val item = Conversions.takeNext()
            if (item != null) {
                convert(item)
                continue
            }
            synchronized(lock) {
                if (!Conversions.hasPending() && stopSelfResult(latestStartId)) {
                    running = false
                }
            }
            if (!running) break
            Thread.sleep(50)
        }
        if (doneCount + failedCount > 0) notifyFinished()
    }

    // ------------------------------------------------------------------

    private fun convert(item: ConversionItem) {
        val cr = contentResolver
        var outUri: Uri? = null
        var error: String? = null
        var cancelled = false

        try {
            val free = try {
                StatFs(Environment.getExternalStorageDirectory().path).availableBytes
            } catch (e: Exception) {
                Long.MAX_VALUE
            }
            val needed = item.size + 64L * 1024 * 1024
            if (item.size > 0 && free < needed) {
                throw UserError(
                    "No hay espacio: hacen falta unos ${size(needed)} libres y quedan ${size(free)}.",
                )
            }

            val input = cr.openFileDescriptor(item.source, "r")
                ?: throw UserError("No se pudo abrir el archivo.")
            input.use { inPfd ->
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, outputName(item.name))
                    put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/$OUTPUT_DIR")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val created = cr.insert(collection, values)
                    ?: throw UserError("No se pudo crear el MP4 en Películas.")
                outUri = created
                val output = cr.openFileDescriptor(created, "rw")
                    ?: throw UserError("No se pudo escribir el MP4.")
                output.use { outPfd ->
                    val mode = if (item.audioAac) NativeRemuxer.AUDIO_AAC else NativeRemuxer.AUDIO_COPY
                    val result = NativeRemuxer.remux(
                        inPfd.fd,
                        outPfd.fd,
                        item.size,
                        mode,
                        item.modifiedMs,
                        Listener(item),
                    )
                    if (result == NativeRemuxer.CANCELLED) {
                        cancelled = true
                    } else if (result != null) {
                        error = result
                    }
                    Unit
                }
                Unit
            }
        } catch (e: UserError) {
            error = e.message
        } catch (e: SecurityException) {
            Log.w(TAG, "Sin permiso", e)
            error = "Sin permiso para leer el archivo. Vuelve a elegirlo."
        } catch (e: FileNotFoundException) {
            Log.w(TAG, "No encontrado", e)
            error = "No se encuentra el archivo (¿se ha movido o se ha quitado la tarjeta?)."
        } catch (e: Throwable) {
            Log.e(TAG, "Fallo al convertir", e)
            error = "Error inesperado: ${e.message ?: e.javaClass.simpleName}"
        }

        val now = System.currentTimeMillis()
        val target = outUri
        if (error == null && !cancelled && target != null) {
            try {
                cr.update(target, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                var finalName = outputName(item.name)
                var finalSize = 0L
                cr.query(
                    target,
                    arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE),
                    null,
                    null,
                    null,
                )?.use { c ->
                    if (c.moveToFirst()) {
                        finalName = c.getString(0) ?: finalName
                        finalSize = c.getLong(1)
                    }
                }
                doneCount++
                Conversions.update(item.id) {
                    it.copy(
                        status = Status.DONE,
                        progress = 1f,
                        output = target,
                        outputName = finalName,
                        outputSize = finalSize,
                        finishedAt = now,
                    )
                }
                return
            } catch (e: Exception) {
                Log.e(TAG, "No se pudo publicar el MP4", e)
                error = "El MP4 se creó pero no se pudo guardar en la galería."
            }
        }

        if (target != null) {
            try {
                cr.delete(target, null, null)
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo borrar el MP4 incompleto", e)
            }
        }
        if (cancelled) {
            Conversions.update(item.id) { it.copy(status = Status.CANCELLED, finishedAt = now) }
        } else {
            failedCount++
            Conversions.update(item.id) { it.copy(status = Status.FAILED, error = error, finishedAt = now) }
        }
    }

    private inner class Listener(private val item: ConversionItem) : RemuxListener {
        override fun onProgress(fraction: Float): Boolean {
            Conversions.update(item.id) { it.copy(progress = fraction) }
            val now = System.currentTimeMillis()
            if (now - lastNotifyAt > 700) {
                lastNotifyAt = now
                val active = Conversions.items.value.count {
                    it.status == Status.RUNNING || it.status == Status.PENDING
                }
                val title = if (active > 1) {
                    "Convirtiendo ${item.name} (quedan $active)"
                } else {
                    "Convirtiendo ${item.name}"
                }
                nm.notify(NOTIF_PROGRESS, progressNotification(title, (fraction * 100).toInt()))
            }
            return !Conversions.isCancelled(item.id)
        }

        override fun onInfo(video: String, audio: String, recordedMs: Long, dateSource: Int) {
            Conversions.update(item.id) {
                it.copy(videoInfo = video, audioInfo = audio, recordedMs = recordedMs, dateSource = dateSource)
            }
        }

        override fun localToUtcMs(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int): Long =
            try {
                LocalDateTime.of(year, month, day, hour, minute, second)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            } catch (e: Exception) {
                Long.MIN_VALUE
            }
    }

    private class UserError(message: String) : Exception(message)

    // ------------------------------------------------------------------

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun progressNotification(title: String, percent: Int): Notification {
        val cancel = PendingIntent.getService(
            this,
            1,
            Intent(this, ConversionService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = Notification.Builder(this, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent())
            .addAction(Notification.Action.Builder(null, "Cancelar", cancel).build())
        if (percent >= 0) {
            b.setContentText("$percent %").setProgress(100, percent, false)
        } else {
            b.setProgress(0, 0, true)
        }
        if (Build.VERSION.SDK_INT >= 31) {
            b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        return b.build()
    }

    private fun notifyFinished() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val title = when {
            failedCount == 0 && doneCount == 1 -> "Vídeo convertido"
            failedCount == 0 -> "$doneCount vídeos convertidos"
            doneCount == 0 && failedCount == 1 -> "No se pudo convertir el vídeo"
            doneCount == 0 -> "No se pudo convertir ningún vídeo"
            else -> "$doneCount convertidos, $failedCount con error"
        }
        val n = Notification.Builder(this, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText("Están en Películas › $OUTPUT_DIR")
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()
        nm.notify(NOTIF_DONE, n)
    }

    private fun size(bytes: Long) = Formatter.formatShortFileSize(this, bytes)
}

/** «00012.MTS» -> «00012.mp4». */
fun outputName(input: String): String {
    val base = input.substringBeforeLast('.', input).ifBlank { "video" }
    return "$base.mp4"
}
