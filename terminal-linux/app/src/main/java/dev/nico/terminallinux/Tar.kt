package dev.nico.terminallinux

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

/** Lo que el extractor necesita del sistema de archivos y que Java no ofrece en Android 7. */
interface Fs {
    fun symlink(target: String, link: File)
    fun chmod(file: File, mode: Int)

    /** ¿Es un enlace simbólico? (sin seguirlo). */
    fun isSymlink(file: File): Boolean
}

/**
 * Extrae un archivo .tar (ya descomprimido) dentro de [dest] sin escribir nunca fuera de él.
 *
 * Entiende ustar, los nombres largos de GNU (L/K) y las cabeceras PAX (x/g), que es lo que usan
 * las imágenes de Ubuntu. Los enlaces duros se copian (Android no deja crearlos) y los
 * dispositivos y FIFOs se ignoran (proot usa los /dev de Android).
 */
class TarExtractor(private val dest: File, private val fs: Fs) {
    private val destCanon = dest.canonicalPath
    private val dirModes = ArrayList<Pair<File, Int>>()
    private val buffer = ByteArray(64 * 1024)

    var entries = 0
        private set

    fun extract(input: InputStream, onEntry: (String) -> Unit = {}) {
        dest.mkdirs()
        val header = ByteArray(BLOCK)
        var longName: String? = null
        var longLink: String? = null
        var pax: Map<String, String> = emptyMap()
        var zeroBlocks = 0

        while (readBlock(input, header)) {
            if (header.all { it == 0.toByte() }) {
                if (++zeroBlocks >= 2) break
                continue
            }
            zeroBlocks = 0
            checkChecksum(header)

            val type = header[156].toInt().toChar()
            val size = pax["size"]?.toLongOrNull() ?: number(header, 124, 12)

            when (type) {
                'L' -> { longName = readText(input, size).trimEnd('\u0000'); continue }
                'K' -> { longLink = readText(input, size).trimEnd('\u0000'); continue }
                'x' -> { pax = parsePax(readText(input, size)); continue }
                'g' -> { skip(input, size); continue }
            }

            val name = pax["path"] ?: longName ?: ustarName(header)
            val link = pax["linkpath"] ?: longLink ?: cString(header, 157, 100)
            val mode = number(header, 100, 8).toInt() and 0xFFF
            val mtime = pax["mtime"]?.substringBefore('.')?.toLongOrNull() ?: number(header, 136, 12)
            longName = null
            longLink = null
            pax = emptyMap()

            val rel = relative(name)
            if (rel == null) {
                skip(input, size)
                continue
            }
            if (rel.isEmpty()) {
                // La propia raíz («./»).
                skip(input, size)
                continue
            }
            val target = File(dest, rel)
            ensureInside(target.parentFile!!)
            onEntry(rel)
            entries++

            when (type) {
                '5' -> {
                    if (isSymlink(target)) target.delete()
                    target.mkdirs()
                    // Los permisos de las carpetas se ponen al final: una carpeta de solo lectura
                    // no dejaría escribir lo que va dentro. El dueño siempre puede escribir
                    // (dentro de proot «root» es el dueño real de todo).
                    dirModes += target to (mode or 0x1C0)
                    skip(input, size)
                }
                '2' -> {
                    target.parentFile?.mkdirs()
                    removeExisting(target)
                    fs.symlink(link, target)
                    skip(input, size)
                }
                '1' -> {
                    val relSource = relative(link)
                    val source = relSource?.let { File(dest, it) }
                    target.parentFile?.mkdirs()
                    removeExisting(target)
                    if (source != null && source.isFile && !isSymlink(source)) {
                        source.inputStream().use { inp -> writeFile(target, inp, source.length()) }
                        chmodFile(target, mode, mtime)
                    }
                    skip(input, size)
                }
                '0', '\u0000', '7' -> {
                    target.parentFile?.mkdirs()
                    removeExisting(target)
                    writeFile(target, input, size)
                    skipPadding(input, size)
                    chmodFile(target, mode, mtime)
                }
                else -> skip(input, size) // dispositivos, FIFOs…
            }
        }

        // Permisos y fechas de las carpetas, de dentro hacia fuera.
        for ((dir, mode) in dirModes.asReversed()) {
            try {
                fs.chmod(dir, mode)
            } catch (_: Exception) {
            }
        }
    }

    private fun chmodFile(file: File, mode: Int, mtime: Long) {
        // Se conservan setuid/setgid: proot los usa para que «sudo» funcione.
        val wanted = mode or 0x180
        try {
            fs.chmod(file, wanted)
        } catch (_: Exception) {
            try {
                fs.chmod(file, wanted and 0x1FF)
            } catch (_: Exception) {
            }
        }
        if (mtime > 0) file.setLastModified(mtime * 1000)
    }

    private fun ensureInside(dir: File) {
        val canon = dir.canonicalPath
        if (canon != destCanon && !canon.startsWith(destCanon + File.separator)) {
            throw IOException("Entrada fuera de la carpeta de destino: $dir")
        }
    }

    private fun removeExisting(file: File) {
        if (isSymlink(file) || file.isFile) file.delete()
    }

    private fun isSymlink(file: File): Boolean = fs.isSymlink(file)

    private fun writeFile(target: File, input: InputStream, size: Long) {
        FileOutputStream(target).use { out ->
            var left = size
            while (left > 0) {
                val n = input.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                if (n < 0) throw IOException("El archivo tar está cortado")
                out.write(buffer, 0, n)
                left -= n
            }
        }
    }

    private fun readText(input: InputStream, size: Long): String {
        if (size > 1_000_000) throw IOException("Cabecera tar demasiado grande")
        val bytes = ByteArray(size.toInt())
        readFully(input, bytes)
        skipPadding(input, size)
        return String(bytes, Charsets.UTF_8)
    }

    private fun skip(input: InputStream, size: Long) {
        var left = paddedSize(size)
        while (left > 0) {
            val n = input.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
            if (n < 0) throw IOException("El archivo tar está cortado")
            left -= n
        }
    }

    private fun skipPadding(input: InputStream, size: Long) {
        val pad = paddedSize(size) - size
        if (pad > 0) readFully(input, ByteArray(pad.toInt()))
    }

    private fun readBlock(input: InputStream, block: ByteArray): Boolean {
        var off = 0
        while (off < block.size) {
            val n = input.read(block, off, block.size - off)
            if (n < 0) {
                if (off == 0) return false
                throw IOException("El archivo tar está cortado")
            }
            off += n
        }
        return true
    }

    private fun readFully(input: InputStream, bytes: ByteArray) {
        var off = 0
        while (off < bytes.size) {
            val n = input.read(bytes, off, bytes.size - off)
            if (n < 0) throw IOException("El archivo tar está cortado")
            off += n
        }
    }

    companion object {
        private const val BLOCK = 512

        private fun paddedSize(size: Long) = (size + BLOCK - 1) / BLOCK * BLOCK

        /** Ruta relativa segura, o null si intenta salir de la carpeta («..»). */
        fun relative(name: String): String? {
            val parts = ArrayList<String>()
            for (p in name.split('/')) {
                when (p) {
                    "", "." -> {}
                    ".." -> return null
                    else -> parts += p
                }
            }
            return parts.joinToString("/")
        }

        fun cString(b: ByteArray, off: Int, len: Int): String {
            var end = off
            while (end < off + len && b[end] != 0.toByte()) end++
            return String(b, off, end - off, Charsets.UTF_8)
        }

        fun ustarName(h: ByteArray): String {
            val name = cString(h, 0, 100)
            val magic = cString(h, 257, 6)
            if (!magic.startsWith("ustar")) return name
            val prefix = cString(h, 345, 155)
            return if (prefix.isEmpty()) name else "$prefix/$name"
        }

        /** Número octal, o en base 256 si el primer byte lleva el bit alto (archivos > 8 GB). */
        fun number(b: ByteArray, off: Int, len: Int): Long {
            if (b[off].toInt() and 0x80 != 0) {
                var v = (b[off].toLong() and 0x7F)
                for (i in off + 1 until off + len) v = (v shl 8) or (b[i].toLong() and 0xFF)
                return v
            }
            var i = off
            val end = off + len
            while (i < end && (b[i] == ' '.code.toByte() || b[i] == 0.toByte())) i++
            var v = 0L
            while (i < end && b[i] >= '0'.code.toByte() && b[i] <= '7'.code.toByte()) {
                v = v * 8 + (b[i] - '0'.code.toByte())
                i++
            }
            return v
        }

        fun checkChecksum(h: ByteArray) {
            val stored = number(h, 148, 8)
            var sum = 0L
            for (i in h.indices) sum += if (i in 148 until 156) 32 else (h[i].toLong() and 0xFF)
            if (sum != stored) throw IOException("Archivo tar dañado (checksum)")
        }

        /** Registros PAX: «<longitud> <clave>=<valor>\n». */
        fun parsePax(text: String): Map<String, String> {
            val out = HashMap<String, String>()
            val bytes = text.toByteArray(Charsets.UTF_8)
            var pos = 0
            while (pos < bytes.size) {
                var sp = pos
                while (sp < bytes.size && bytes[sp] != ' '.code.toByte()) sp++
                val len = String(bytes, pos, sp - pos, Charsets.US_ASCII).trim().toIntOrNull() ?: break
                if (len <= 0 || pos + len > bytes.size) break
                val record = String(bytes, sp + 1, pos + len - sp - 2, Charsets.UTF_8)
                val eq = record.indexOf('=')
                if (eq > 0) out[record.substring(0, eq)] = record.substring(eq + 1)
                pos += len
            }
            return out
        }
    }
}
