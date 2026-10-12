package io.github.eelnicoo.pocketcode.build

import android.content.Context
import com.android.apksig.ApkSigner
import com.android.tools.r8.CompilationMode
import com.android.tools.r8.D8
import com.android.tools.r8.D8Command
import com.android.tools.r8.OutputMode
import org.eclipse.jdt.core.compiler.batch.BatchCompiler
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * On-device Android build toolchain, entirely pure-JVM so it runs on ART:
 *  - [compileJava]  Java sources -> .class  (Eclipse ECJ)
 *  - [dex]          .class / .jar -> classes.dex  (D8)
 *  - [signApk]      sign/zip-align an APK  (apksig, v1+v2+v3)
 *  - [rebuildApk]   splice fresh dex into a template APK, then sign  (installable output)
 *
 * Kotlin compilation and aapt2 resource linking are NOT available on-device, so the
 * supported build model is "template APK + recompiled Java/dex", which needs none of them.
 */
class ApkBuilder(private val context: Context) {

    data class Result(val ok: Boolean, val log: String, val output: File? = null)

    private val workRoot: File by lazy { File(context.filesDir, "apkbuild").apply { mkdirs() } }

    // ---- signing key (bundled debug keystore in assets) ----

    private data class Signer(val key: PrivateKey, val certs: List<X509Certificate>)

    private fun loadSigner(): Signer {
        val ks = KeyStore.getInstance("PKCS12")
        context.assets.open("debug.keystore").use { ks.load(it, STORE_PASS.toCharArray()) }
        val alias = ALIAS
        val key = ks.getKey(alias, KEY_PASS.toCharArray()) as PrivateKey
        val chain = ks.getCertificateChain(alias).map { it as X509Certificate }
        return Signer(key, chain)
    }

    // ---- ECJ: compile Java ----

    fun compileJava(sourceDir: File, classpath: List<File>, outDir: File): Result {
        outDir.mkdirs()
        val sources = sourceDir.walkTopDown().filter { it.isFile && it.extension == "java" }.toList()
        if (sources.isEmpty()) return Result(false, "No se encontraron archivos .java en ${sourceDir.path}")
        val cp = classpath.joinToString(File.pathSeparator) { it.absolutePath }
        val args = buildList {
            add("-8")
            add("-nowarn")
            add("-proc:none")
            if (cp.isNotEmpty()) { add("-cp"); add(cp) }
            add("-d"); add(outDir.absolutePath)
            sources.forEach { add(it.absolutePath) }
        }.toTypedArray()
        val out = StringWriter()
        val err = StringWriter()
        val ok = BatchCompiler.compile(args, PrintWriter(out), PrintWriter(err), null)
        val log = buildString {
            append("ECJ ${if (ok) "OK" else "FALLÓ"} (${sources.size} archivos)\n")
            if (out.toString().isNotBlank()) append(out)
            if (err.toString().isNotBlank()) append(err)
        }
        return Result(ok, log, if (ok) outDir else null)
    }

    // ---- D8: dex ----

    fun dex(inputs: List<File>, libs: List<File>, minApi: Int, outDir: File): Result {
        outDir.mkdirs()
        return try {
            val cmd = D8Command.builder()
                .addProgramFiles(inputs.map { it.toPath() })
                .apply { if (libs.isNotEmpty()) addLibraryFiles(libs.map { it.toPath() }) }
                .setMinApiLevel(minApi)
                .setMode(CompilationMode.DEBUG)
                .setOutput(outDir.toPath(), OutputMode.DexIndexed)
                .build()
            D8.run(cmd)
            val dex = File(outDir, "classes.dex")
            if (dex.exists()) Result(true, "D8 OK -> ${dex.path}", dex)
            else Result(false, "D8 no generó classes.dex")
        } catch (t: Throwable) {
            Result(false, "D8 falló: ${t.message}")
        }
    }

    // ---- apksig: sign ----

    fun signApk(input: File, output: File, minSdk: Int): Result {
        return try {
            val signer = loadSigner()
            val config = ApkSigner.SignerConfig.Builder("pocketcode", signer.key, signer.certs).build()
            output.parentFile?.mkdirs()
            val b = ApkSigner.Builder(listOf(config))
                .setInputApk(input)
                .setOutputApk(output)
                .setMinSdkVersion(minSdk)
                .setV1SigningEnabled(true)
                .setV2SigningEnabled(true)
                .setV3SigningEnabled(true)
            b.build().sign()
            Result(true, "APK firmado -> ${output.path}", output)
        } catch (t: Throwable) {
            Result(false, "Firma falló: ${t.message}")
        }
    }

    // ---- splice fresh dex into a template apk, re-sign ----

    fun rebuildApk(templateApk: File, dex: File, output: File, minSdk: Int): Result {
        val unsigned = File(workRoot, "unsigned-${System.currentTimeMillis()}.apk")
        try {
            ZipFile(templateApk).use { zip ->
                ZipOutputStream(unsigned.outputStream().buffered()).use { zos ->
                    // copy every entry except old dex and existing signatures
                    val entries = zip.entries()
                    val seenDex = HashSet<String>()
                    while (entries.hasMoreElements()) {
                        val e = entries.nextElement()
                        val n = e.name
                        if (n.startsWith("META-INF/") &&
                            (n.endsWith(".SF") || n.endsWith(".RSA") || n.endsWith(".DSA") || n.endsWith(".EC"))
                        ) continue
                        if (n == "classes.dex") { seenDex.add(n); continue }
                        zos.putNextEntry(ZipEntry(n))
                        zip.getInputStream(e).use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                    // add fresh dex
                    zos.putNextEntry(ZipEntry("classes.dex"))
                    dex.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        } catch (t: Throwable) {
            return Result(false, "No se pudo reconstruir el APK: ${t.message}")
        }
        val signed = signApk(unsigned, output, minSdk)
        unsigned.delete()
        return signed
    }

    fun newBuildDir(name: String): File = File(workRoot, name).apply { mkdirs() }

    val bundledLibs: List<File> get() = emptyList()

    companion object {
        private const val STORE_PASS = "android"
        private const val KEY_PASS = "android"
        private const val ALIAS = "pocketcode"
    }
}
