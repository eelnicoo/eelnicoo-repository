package dev.nico.terminallinux

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Environment
import android.os.FileObserver
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.widget.Toast
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import java.io.File

/**
 * Servicio en primer plano que mantiene vivas las sesiones de terminal (y la instalación)
 * aunque la app quede en segundo plano o se cierre la pantalla.
 */
class TerminalService : Service() {

    enum class Tipo { USUARIO, ROOT, ANDROID }

    interface Oyente {
        fun onSesiones()
        fun onTexto(session: TerminalSession)
        fun onTitulo(session: TerminalSession)
        fun onTerminada(session: TerminalSession)
        fun onColores(session: TerminalSession)
        fun onInstalacion(p: Instalador.Progreso?, terminada: Boolean, error: String?)
    }

    inner class Enlace : Binder() {
        val servicio: TerminalService get() = this@TerminalService
    }

    private val enlace = Enlace()
    private val main = Handler(Looper.getMainLooper())
    lateinit var linux: Linux
        private set

    val sesiones = ArrayList<TerminalSession>()
    private val tipos = HashMap<TerminalSession, Tipo>()
    var oyente: Oyente? = null
    var actividadVisible = false

    // Instalación
    var instalando = false
        private set
    var progreso: Instalador.Progreso? = null
        private set
    var errorInstalacion: String? = null
        private set
    private var instalador: Instalador? = null

    private var wakeLock: PowerManager.WakeLock? = null
    val despierto get() = wakeLock?.isHeld == true

    private var observador: FileObserver? = null

    override fun onCreate() {
        super.onCreate()
        linux = Linux(this)
        linux.clearStaleSetupLock()
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CANAL, "Sesiones de terminal", NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false)
                },
            )
            nm.createNotificationChannel(
                NotificationChannel(CANAL_ABRIR, "Abrir enlaces de la terminal", NotificationManager.IMPORTANCE_HIGH),
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder = enlace

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACCION_SALIR -> salir()
            ACCION_DESPIERTO -> alternarDespierto()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        observador?.stopWatching()
        for (s in sesiones) s.finishIfRunning()
        sesiones.clear()
        soltarWakeLock()
        super.onDestroy()
    }

    // ------------------------------------------------------------ sesiones

    fun nuevaSesion(tipo: Tipo): TerminalSession {
        val sesion = when (tipo) {
            Tipo.ANDROID -> TerminalSession(
                "/system/bin/sh", filesDir.absolutePath, arrayOf("sh"),
                arrayOf(
                    "PATH=/system/bin:/system/xbin", "HOME=${filesDir.absolutePath}",
                    "TMPDIR=${cacheDir.absolutePath}", "TERM=xterm-256color",
                ),
                FILAS_HISTORIAL, cliente,
            )
            else -> {
                linux.prepare()
                val modo = if (tipo == Tipo.ROOT) Linux.Mode.ROOT else Linux.Mode.USER
                TerminalSession(
                    "/system/bin/sh", linux.base.absolutePath,
                    arrayOf("sh", linux.scriptFor(modo).absolutePath),
                    linux.hostEnv(), FILAS_HISTORIAL, cliente,
                )
            }
        }
        sesion.mSessionName = when (tipo) {
            Tipo.USUARIO -> linux.username
            Tipo.ROOT -> "root"
            Tipo.ANDROID -> "android"
        }
        sesiones += sesion
        tipos[sesion] = tipo
        vigilarAbrir()
        actualizarPrimerPlano()
        oyente?.onSesiones()
        return sesion
    }

    fun tipo(s: TerminalSession) = tipos[s]

    fun cerrarSesion(s: TerminalSession) {
        s.finishIfRunning()
        sesiones.remove(s)
        tipos.remove(s)
        actualizarPrimerPlano()
        oyente?.onSesiones()
    }

    fun salir() {
        for (s in sesiones.toList()) s.finishIfRunning()
        sesiones.clear()
        tipos.clear()
        instalador?.cancelado = true
        soltarWakeLock()
        actualizarPrimerPlano()
        oyente?.onSesiones()
        stopSelf()
    }

    private val cliente = object : TerminalSessionClient {
        override fun onTextChanged(changedSession: TerminalSession) {
            oyente?.onTexto(changedSession)
        }

        override fun onTitleChanged(changedSession: TerminalSession) {
            oyente?.onTitulo(changedSession)
        }

        override fun onSessionFinished(finishedSession: TerminalSession) {
            if (oyente != null) {
                oyente?.onTerminada(finishedSession)
            } else if (finishedSession.exitStatus == 0) {
                cerrarSesion(finishedSession)
            }
        }

        override fun onCopyTextToClipboard(session: TerminalSession, text: String?) {
            if (text.isNullOrEmpty()) return
            val cm = getSystemService(ClipboardManager::class.java)
            cm.setPrimaryClip(ClipData.newPlainText("Terminal", text))
            if (Build.VERSION.SDK_INT < 33) Toast.makeText(this@TerminalService, "Copiado", Toast.LENGTH_SHORT).show()
        }

        override fun onPasteTextFromClipboard(session: TerminalSession?) {
            pegar(session)
        }

        override fun onBell(session: TerminalSession) {}

        override fun onColorsChanged(session: TerminalSession) {
            oyente?.onColores(session)
        }

        override fun onTerminalCursorStateChange(state: Boolean) {}

        override fun getTerminalCursorStyle(): Int = TerminalEmulator.DEFAULT_TERMINAL_CURSOR_STYLE

        override fun logError(tag: String?, message: String?) {
            Log.e(tag ?: TAG, message ?: "")
        }

        override fun logWarn(tag: String?, message: String?) {
            Log.w(tag ?: TAG, message ?: "")
        }

        override fun logInfo(tag: String?, message: String?) {
            Log.i(tag ?: TAG, message ?: "")
        }

        override fun logDebug(tag: String?, message: String?) {}

        override fun logVerbose(tag: String?, message: String?) {}

        override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {
            Log.e(tag ?: TAG, message, e)
        }

        override fun logStackTrace(tag: String?, e: Exception?) {
            Log.e(tag ?: TAG, "", e)
        }
    }

    fun pegar(session: TerminalSession?) {
        val s = session ?: return
        val cm = getSystemService(ClipboardManager::class.java)
        val texto = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
        if (!texto.isNullOrEmpty()) s.emulator?.paste(texto)
    }

    // ------------------------------------------------------------ instalación

    fun instalar() {
        if (instalando) return
        instalando = true
        errorInstalacion = null
        progreso = Instalador.Progreso("Preparando…")
        actualizarPrimerPlano()
        val inst = Instalador(linux, cacheDir) { p ->
            main.post {
                progreso = p
                oyente?.onInstalacion(p, false, null)
                actualizarPrimerPlano()
            }
        }
        instalador = inst
        Thread({
            val error = try {
                inst.instalar()
                null
            } catch (e: Exception) {
                Log.e(TAG, "Instalación fallida", e)
                e.message ?: e.toString()
            }
            main.post {
                instalando = false
                instalador = null
                errorInstalacion = error
                actualizarPrimerPlano()
                oyente?.onInstalacion(progreso, true, error)
            }
        }, "instalador").start()
    }

    /** Borra Ubuntu (para reinstalarlo). Cierra antes todas las sesiones. */
    fun desinstalar(listo: () -> Unit) {
        for (s in sesiones.toList()) s.finishIfRunning()
        sesiones.clear()
        tipos.clear()
        observador?.stopWatching()
        observador = null
        actualizarPrimerPlano()
        Thread({
            linux.delete()
            main.post(listo)
        }, "desinstalador").start()
    }

    // ------------------------------------------------------------ primer plano y notificación

    private fun actualizarPrimerPlano() {
        if (sesiones.isEmpty() && !instalando) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            soltarWakeLock()
            return
        }
        val n = notificacion()
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(ID_NOTIF, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(ID_NOTIF, n)
            }
        } catch (e: Exception) {
            // Android puede negarlo si la app está en segundo plano; las sesiones siguen igual.
            Log.w(TAG, "No se pudo pasar a primer plano", e)
        }
    }

    private fun notificacion(): Notification {
        val abrirApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val texto = if (instalando) {
            progreso?.texto ?: "Instalando Ubuntu…"
        } else {
            val n = sesiones.size
            (if (n == 1) "1 sesión abierta" else "$n sesiones abiertas") +
                if (despierto) " · se mantiene activa con la pantalla apagada" else ""
        }
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CANAL) else @Suppress("DEPRECATION") Notification.Builder(this)
        b.setSmallIcon(R.drawable.ic_notificacion)
            .setContentTitle("Terminal Linux")
            .setContentText(texto)
            .setContentIntent(abrirApp)
            .setOngoing(true)
            .setShowWhen(false)
        if (instalando) {
            val f = progreso?.fraccion ?: -1f
            b.setProgress(100, (f * 100).toInt().coerceIn(0, 100), f < 0)
        } else {
            b.addAction(accion(ACCION_SALIR, "Salir", 1))
            b.addAction(accion(ACCION_DESPIERTO, if (despierto) "Permitir reposo" else "Mantener activa", 2))
        }
        return b.build()
    }

    private fun accion(accion: String, titulo: String, req: Int): Notification.Action {
        val pi = PendingIntent.getService(
            this, req, Intent(this, TerminalService::class.java).setAction(accion),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Action.Builder(null, titulo, pi).build()
    }

    fun alternarDespierto() {
        if (despierto) {
            soltarWakeLock()
        } else {
            val pm = getSystemService(PowerManager::class.java)
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TerminalLinux::sesiones").apply {
                setReferenceCounted(false)
                acquire()
            }
        }
        actualizarPrimerPlano()
    }

    private fun soltarWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    // ------------------------------------------------------------ «abrir» desde Ubuntu

    @Suppress("DEPRECATION")
    private fun vigilarAbrir() {
        if (observador != null) return
        val dir = linux.openRequests
        if (!dir.isDirectory) return
        dir.listFiles()?.forEach { if (it.name.endsWith(".pedido")) it.delete() }
        observador = object : FileObserver(dir.absolutePath, CLOSE_WRITE or MOVED_TO) {
            override fun onEvent(event: Int, path: String?) {
                if (path == null || !path.endsWith(".pedido")) return
                val f = File(dir, path)
                val objetivo = try {
                    f.readText().trim()
                } catch (_: Exception) {
                    return
                } finally {
                    f.delete()
                }
                if (objetivo.isNotEmpty()) main.post { abrir(objetivo) }
            }
        }.also { it.startWatching() }
    }

    private fun abrir(objetivo: String) {
        val intent = if (objetivo.contains("://") && !objetivo.startsWith("file://") || objetivo.startsWith("mailto:") ||
            objetivo.startsWith("tel:") || objetivo.startsWith("geo:")
        ) {
            Intent(Intent.ACTION_VIEW, Uri.parse(objetivo))
        } else {
            val ruta = if (objetivo.startsWith("file://")) Uri.parse(objetivo).path ?: return else objetivo
            val archivo = when {
                ruta == "/sdcard" || ruta.startsWith("/sdcard/") ->
                    File(Environment.getExternalStorageDirectory(), ruta.removePrefix("/sdcard"))
                ruta.startsWith("/storage/") -> File(ruta)
                else -> File(linux.rootfs, ruta)
            }
            if (!archivo.isFile) {
                Toast.makeText(this, "No se puede abrir: $ruta", Toast.LENGTH_SHORT).show()
                return
            }
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(ArchivosProvider.uriFor(this, archivo), ArchivosProvider.mimeOf(archivo))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (actividadVisible) {
            try {
                startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(this, "Ninguna app puede abrir esto", Toast.LENGTH_SHORT).show()
            }
        } else {
            // Android no deja abrir otra app desde segundo plano: se avisa con una notificación.
            val pi = PendingIntent.getActivity(
                this, objetivo.hashCode(), intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CANAL_ABRIR) else @Suppress("DEPRECATION") Notification.Builder(this)
            val n = b.setSmallIcon(R.drawable.ic_notificacion)
                .setContentTitle("Toca para abrir")
                .setContentText(objetivo)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
            getSystemService(NotificationManager::class.java).notify(objetivo.hashCode(), n)
        }
    }

    companion object {
        private const val TAG = "TerminalLinux"
        private const val CANAL = "sesiones"
        private const val CANAL_ABRIR = "abrir"
        private const val ID_NOTIF = 1
        private const val FILAS_HISTORIAL = 5000
        const val ACCION_SALIR = "dev.nico.terminallinux.SALIR"
        const val ACCION_DESPIERTO = "dev.nico.terminallinux.DESPIERTO"

        fun iniciar(ctx: Context) {
            ctx.startService(Intent(ctx, TerminalService::class.java))
        }
    }
}
