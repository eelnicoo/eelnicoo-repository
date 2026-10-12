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
        try {
            val proc: Process = if (shizuku) {
                newShizukuProcess(cmd, null, dir)
            } else {
                ProcessBuilder(*cmd)
                    .directory(workingDir())
                    .redirectErrorStream(true)
                    .start()
            }
            val output = StringBuilder()
            val reader = proc.inputStream.bufferedReader()
            val errReader = if (shizuku) proc.errorStream.bufferedReader() else null
            val finished = waitForWithTimeout(proc, timeout)
            output.append(reader.readText())
            errReader?.let { output.append(it.readText()) }
            val label = if (shizuku) "[shizuku]" else "[sandbox]"
            if (!finished) {
                proc.destroy()
                return@withContext ToolResult("$label timeout tras ${timeout}s\n$output", true)
            }
            val code = proc.exitValue()
            val head = "$label exit=$code\n"
            ToolResult(head + output.toString().take(60_000), isError = code != 0)
        } catch (t: Throwable) {
            ToolResult("Error ejecutando comando: ${t.message}", true)
        }
    }

    private fun waitForWithTimeout(proc: Process, timeoutSec: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutSec * 1000L
        while (System.currentTimeMillis() < deadline) {
            if (!proc.isAlive) return true
            Thread.sleep(50)
        }
        return !proc.isAlive
    }

    companion object {
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
