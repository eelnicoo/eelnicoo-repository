package io.github.eelnicoo.pocketcode.tools

import io.github.eelnicoo.pocketcode.build.ApkBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * Agent-facing APK build tool. Supported modes (on-device, pure-JVM):
 *  - "sign"         : sign/re-sign an existing APK with the bundled debug key.
 *  - "compile_dex"  : compile .java sources (ECJ) and dex them (D8) -> classes.dex.
 *  - "rebuild_apk"  : compile+dex sources and splice the dex into a template APK, then sign
 *                     -> an installable APK (needs no aapt2 / Kotlin on device).
 */
class BuildApkTool(
    private val ws: Workspace,
    private val builder: ApkBuilder,
) : Tool {
    override val name = "build_apk"
    override val description =
        "Construye o firma APKs en el dispositivo. Modos: 'sign' (firmar un APK existente), " +
            "'compile_dex' (compilar .java y generar classes.dex), 'rebuild_apk' (compilar código, " +
            "inyectarlo en un APK plantilla y firmarlo => APK instalable). Kotlin y aapt2 no están " +
            "disponibles en el móvil, por eso el modelo soportado es 'plantilla APK + código Java recompilado'."
    override val inputSchema = objectSchema(
        Prop("mode", "string", "Uno de: sign, compile_dex, rebuild_apk"),
        Prop("source_dir", "string", "Directorio con fuentes .java (compile_dex, rebuild_apk)", required = false),
        Prop("android_jar", "string", "Ruta a android.jar/framework jar para el classpath de compilación", required = false),
        Prop("template_apk", "string", "APK plantilla cuyo classes.dex se reemplaza (rebuild_apk)", required = false),
        Prop("input_apk", "string", "APK de entrada a firmar (sign)", required = false),
        Prop("output_apk", "string", "Ruta del APK de salida (sign, rebuild_apk)", required = false),
        Prop("min_sdk", "integer", "minSdkVersion / minApi (por defecto 29)", required = false),
    )

    override suspend fun run(input: JsonObject): ToolResult = withContext(Dispatchers.IO) {
        val mode = input["mode"]?.jsonPrimitive?.content ?: return@withContext ToolResult("Falta 'mode'", true)
        val minSdk = input["min_sdk"]?.jsonPrimitive?.content?.toIntOrNull() ?: 29
        when (mode) {
            "sign" -> {
                val inApk = input.path("input_apk") ?: return@withContext ToolResult("Falta 'input_apk'", true)
                val outApk = input.path("output_apk") ?: return@withContext ToolResult("Falta 'output_apk'", true)
                val r = builder.signApk(ws.resolve(inApk), ws.resolve(outApk), minSdk)
                ToolResult(r.log, isError = !r.ok)
            }
            "compile_dex" -> {
                val srcDir = input.path("source_dir") ?: return@withContext ToolResult("Falta 'source_dir'", true)
                val classpath = input.path("android_jar")?.let { listOf(ws.resolve(it)) } ?: emptyList()
                val build = builder.newBuildDir("cd-${System.currentTimeMillis()}")
                val classes = File(build, "classes")
                val comp = builder.compileJava(ws.resolve(srcDir), classpath, classes)
                if (!comp.ok) return@withContext ToolResult(comp.log, true)
                val dexDir = File(build, "dex")
                val dex = builder.dex(listOf(classes), classpath, minSdk, dexDir)
                ToolResult(comp.log + "\n" + dex.log, isError = !dex.ok)
            }
            "rebuild_apk" -> {
                val srcDir = input.path("source_dir") ?: return@withContext ToolResult("Falta 'source_dir'", true)
                val template = input.path("template_apk") ?: return@withContext ToolResult("Falta 'template_apk'", true)
                val outApk = input.path("output_apk") ?: return@withContext ToolResult("Falta 'output_apk'", true)
                val classpath = input.path("android_jar")?.let { listOf(ws.resolve(it)) } ?: emptyList()
                val build = builder.newBuildDir("rb-${System.currentTimeMillis()}")
                val classes = File(build, "classes")
                val comp = builder.compileJava(ws.resolve(srcDir), classpath, classes)
                if (!comp.ok) return@withContext ToolResult(comp.log, true)
                val dexDir = File(build, "dex")
                val dex = builder.dex(listOf(classes), classpath, minSdk, dexDir)
                if (!dex.ok || dex.output == null) return@withContext ToolResult(comp.log + "\n" + dex.log, true)
                val r = builder.rebuildApk(ws.resolve(template), dex.output, ws.resolve(outApk), minSdk)
                ToolResult(comp.log + "\n" + dex.log + "\n" + r.log, isError = !r.ok)
            }
            else -> ToolResult("Modo desconocido: $mode", true)
        }
    }

    private fun JsonObject.path(key: String): String? = this[key]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
}
