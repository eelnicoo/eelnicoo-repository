package dev.nico.terminallinux

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

/**
 * Descarga la imagen oficial de Ubuntu Base 24.04 LTS de cdimage.ubuntu.com, comprueba su
 * SHA-256 y la descomprime en la carpeta de Linux. Se ejecuta en un hilo aparte.
 */
class Instalador(
    private val linux: Linux,
    private val cacheDir: File,
    private val progreso: (Progreso) -> Unit,
) {
    data class Progreso(val texto: String, val fraccion: Float = -1f)

    @Volatile
    var cancelado = false

    fun instalar() {
        val arch = linux.ubuntuArch ?: throw IOException("Esta tablet no es compatible (ni ARM ni x86_64).")

        progreso(Progreso("Buscando la última versión de Ubuntu 24.04…"))
        val sums = String(descargarPequeno(BASE + "SHA256SUMS"), Charsets.UTF_8)
        val (archivo, sha) = elegir(sums, arch)
            ?: throw IOException("No encuentro Ubuntu 24.04 para $arch en cdimage.ubuntu.com.")

        val tarball = File(cacheDir, archivo)
        descargar(BASE + archivo, tarball, sha)

        progreso(Progreso("Descomprimiendo Ubuntu…", 0f))
        linux.delete()
        linux.rootfs.mkdirs()
        val total = tarball.length().toFloat()
        val contador = Contador(FileInputStream(tarball))
        var ultima = 0L
        GZIPInputStream(BufferedInputStream(contador, 1 shl 16), 1 shl 16).use { gz ->
            TarExtractor(linux.rootfs, AndroidFs).extract(gz) {
                if (cancelado) throw IOException("Cancelado")
                val ahora = System.currentTimeMillis()
                if (ahora - ultima > 150) {
                    ultima = ahora
                    progreso(Progreso("Descomprimiendo Ubuntu…", contador.leidos / total))
                }
            }
        }
        tarball.delete()
        linux.markInstalled()
        progreso(Progreso("Ubuntu instalado", 1f))
    }

    private fun descargar(url: String, destino: File, sha: String) {
        // Si ya estaba entera de un intento anterior, no se vuelve a bajar.
        if (destino.isFile && sha256(destino) == sha) return

        var intento = 0
        while (true) {
            try {
                descargarConReanudacion(url, destino)
                break
            } catch (e: IOException) {
                if (cancelado || ++intento >= 4) throw e
                progreso(Progreso("Se cortó la descarga, reintentando…"))
                Thread.sleep(2000L * intento)
            }
        }
        progreso(Progreso("Comprobando la descarga…"))
        if (sha256(destino) != sha) {
            destino.delete()
            throw IOException("La descarga está dañada (SHA-256 no coincide). Vuelve a intentarlo.")
        }
    }

    private fun descargarConReanudacion(url: String, destino: File) {
        val yaTengo = if (destino.isFile) destino.length() else 0L
        val con = abrir(url)
        if (yaTengo > 0) con.setRequestProperty("Range", "bytes=$yaTengo-")
        val codigo = con.responseCode
        val reanuda = codigo == 206
        if (codigo != 200 && !reanuda) {
            if (codigo == 416) {
                // Ya estaba completa (o el archivo local es basura): se descarta y se empieza de cero.
                destino.delete()
                con.disconnect()
                throw IOException("Reintentando desde cero")
            }
            throw IOException("El servidor respondió $codigo")
        }
        val inicio = if (reanuda) yaTengo else 0L
        val total = inicio + con.contentLengthLong.coerceAtLeast(0)
        con.inputStream.use { inp ->
            FileOutputStream(destino, reanuda).use { out ->
                val buf = ByteArray(1 shl 16)
                var hecho = inicio
                var ultima = 0L
                while (true) {
                    if (cancelado) throw IOException("Cancelado")
                    val n = inp.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    hecho += n
                    val ahora = System.currentTimeMillis()
                    if (ahora - ultima > 200) {
                        ultima = ahora
                        val mb = "%.1f".format(java.util.Locale.ROOT, hecho / 1048576.0)
                        val de = if (total > 0) " de %.1f MB".format(java.util.Locale.ROOT, total / 1048576.0) else " MB"
                        progreso(Progreso("Descargando Ubuntu: $mb$de", if (total > 0) hecho.toFloat() / total else -1f))
                    }
                }
                if (total > 0 && hecho < total) throw IOException("La descarga se cortó")
            }
        }
    }

    private fun descargarPequeno(url: String): ByteArray {
        val con = abrir(url)
        if (con.responseCode != 200) throw IOException("No se pudo conectar con cdimage.ubuntu.com (${con.responseCode}). ¿Hay internet?")
        return con.inputStream.use { it.readBytes() }
    }

    private fun abrir(url: String): HttpURLConnection {
        val con = try {
            java.net.URI(url).toURL().openConnection() as HttpURLConnection
        } catch (e: IOException) {
            throw IOException("No hay conexión a internet.", e)
        }
        con.connectTimeout = 20_000
        con.readTimeout = 60_000
        con.instanceFollowRedirects = true
        con.setRequestProperty("User-Agent", "TerminalLinux/1.0 (Android)")
        return con
    }

    private class Contador(inp: InputStream) : FilterInputStream(inp) {
        @Volatile
        var leidos = 0L

        override fun read(): Int = super.read().also { if (it >= 0) leidos++ }

        override fun read(b: ByteArray, off: Int, len: Int): Int =
            super.read(b, off, len).also { if (it > 0) leidos += it }
    }

    companion object {
        const val BASE = "https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/"

        /** De SHA256SUMS, la versión 24.04.x más alta para [arch]: (archivo, sha256). */
        fun elegir(sums: String, arch: String): Pair<String, String>? {
            val re = Regex("""^([0-9a-f]{64}) \*?(ubuntu-base-24\.04(?:\.(\d+))?-base-${Regex.escape(arch)}\.tar\.gz)$""")
            return sums.lineSequence()
                .mapNotNull { re.find(it.trim()) }
                .maxByOrNull { it.groupValues[3].toIntOrNull() ?: 0 }
                ?.let { it.groupValues[2] to it.groupValues[1] }
        }

        fun sha256(f: File): String {
            val md = MessageDigest.getInstance("SHA-256")
            FileInputStream(f).use { inp ->
                val buf = ByteArray(1 shl 16)
                while (true) {
                    val n = inp.read(buf)
                    if (n < 0) break
                    md.update(buf, 0, n)
                }
            }
            return md.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
