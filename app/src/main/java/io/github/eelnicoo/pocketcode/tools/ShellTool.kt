package io.github.eelnicoo.pocketcode.tools

import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import rikka.shizuku.Shizuku
import java.io.File

/**
 * Runs shell commands. If Shizuku is enabled, running and permitted, commands execute
 * with Shizuku's elevated (adb-shell) privileges — which on GrapheneOS and other
 * non-rooted devices is the clean way to reach the wider system. Otherwise commands run
 * inside the app's own (unprivileged) sandbox via Runtime.exec.
 */
class ShellTool(
    private val useShizuku: () -> Boolean,
    private val workingDir: () -> File,
) : Tool {
    override val name = "run_shell"
    override val description =
        "Ejecuta un comando de shell y devuelve su salida (stdout+stderr) y código de salida. " +
            "Con Shizuku activo, se ejecuta con permisos de sistema (adb)."
    override val inputSchema = objectSchema(
        Prop("command", "string", "Comando a ejecutar con sh -c"),
        Prop("timeout_sec", "integer", "Tiempo máximo en segundos (por defecto 60)", required = false),
    )

    override suspend fun run(input: JsonObject): ToolResult = withContext(Dispatchers.IO) {
        val command = input["command"]?.jsonPrimitive?.content
            ?: return@withContext ToolResult("Falta 'command'", true)
        val timeout = input["timeout_sec"]?.jsonPrimitive?.content?.toLongOrNull() ?: 60L
        val cmd = arrayOf("sh", "-c", command)
        val dir = workingDir().absolutePath

        val shizuku = useShizuku() && shizukuReady()
        // Shizuku runs as the shell user, which cannot enter the app's private storage
        // (the default workspace); start those commands in /data/local/tmp instead.
        val shizukuDir = if (shizuku && isAppPrivate(dir)) SHELL_TMP else dir
        try {
            val proc: Process = if (shizuku) {
                newShizukuProcess(cmd, null, shizukuDir)
            } else {
                ProcessBuilder(*cmd)
                    .directory(workingDir())
                    .redirectErrorStream(true)
                    .start()
            }
            // Drain stdout/stderr while the command runs; otherwise a chatty command fills
            // the pipe buffer, blocks, and never exits.
            val output = StringBuffer()
            val readers = buildList {
                add(drain(proc.inputStream, output))
                if (shizuku) add(drain(proc.errorStream, output))
            }
            val finished = waitFor(proc, timeout)
            if (!finished) proc.destroy()
            readers.forEach { it.join(2000) }
            val label = when {
                !shizuku -> "[sandbox]"
                shizukuDir != dir -> "[shizuku cwd=$SHELL_TMP: el directorio de trabajo es privado de la app]"
                else -> "[shizuku]"
            }
            if (!finished) {
                return@withContext ToolResult("$label timeout tras ${timeout}s\n$output", true)
            }
            val code = proc.exitValue()
            val head = "$label exit=$code\n"
            ToolResult(head + output.toString(), isError = code != 0)
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (t: Throwable) {
            // Reflection wraps whatever Shizuku threw; report the real cause.
            val cause = (t as? java.lang.reflect.InvocationTargetException)?.targetException ?: t
            ToolResult("Error ejecutando comando: ${cause.message ?: cause.javaClass.simpleName}", true)
        }
    }

    private fun drain(stream: java.io.InputStream, out: StringBuffer): Thread =
        kotlin.concurrent.thread(isDaemon = true, name = "shell-drain") {
            runCatching {
                stream.bufferedReader().use { r ->
                    val buf = CharArray(8192)
                    while (true) {
                        val n = r.read(buf)
                        if (n < 0) break
                        if (out.length < MAX_OUTPUT) out.append(buf, 0, minOf(n, MAX_OUTPUT - out.length))
                    }
                }
            }
        }

    /** Polls until the process exits or the timeout passes; destroys it if cancelled. */
    private suspend fun waitFor(proc: Process, timeoutSec: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutSec * 1000L
        try {
            while (System.currentTimeMillis() < deadline) {
                if (!alive(proc)) return true
                kotlinx.coroutines.delay(50)
            }
            return !alive(proc)
        } catch (c: kotlinx.coroutines.CancellationException) {
            proc.destroy()
            throw c
        }
    }

    /**
     * ShizukuRemoteProcess doesn't override isAlive; the inherited version calls
     * exitValue(), whose "still running" error reaches us as an IllegalArgumentException
     * across binder instead of the IllegalThreadStateException isAlive expects.
     */
    private fun alive(p: Process): Boolean =
        if (p is rikka.shizuku.ShizukuRemoteProcess) p.alive() else p.isAlive

    companion object {
        private const val MAX_OUTPUT = 200_000
        private const val SHELL_TMP = "/data/local/tmp"

        internal fun isAppPrivate(path: String): Boolean =
            path.startsWith("/data/user/") || path.startsWith("/data/data/") || path.startsWith("/data/user_de/")

        fun shizukuReady(): Boolean = try {
            Shizuku.pingBinder() &&
                !Shizuku.isPreV11() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }

        /**
         * Shizuku.newProcess is a hidden API; invoke it reflectively. Returns a Process
         * (ShizukuRemoteProcess) running with Shizuku's privileges.
         */
        fun newShizukuProcess(cmd: Array<String>, env: Array<String>?, dir: String?): Process {
            val m = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java,
            )
            m.isAccessible = true
            return m.invoke(null, cmd, env, dir) as Process
        }
    }
}
