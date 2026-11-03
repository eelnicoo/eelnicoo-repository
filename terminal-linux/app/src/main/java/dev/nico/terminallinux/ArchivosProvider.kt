package dev.nico.terminallinux

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileNotFoundException

/**
 * Deja que otras apps lean (solo leer) un archivo de Ubuntu o de la memoria de la tablet
 * cuando se abre con «abrir». Cada archivo se comparte con un permiso temporal por URI.
 */
class ArchivosProvider : ContentProvider() {

    override fun onCreate() = true

    override fun getType(uri: Uri): String = mimeOf(File(uri.path ?: ""))

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val file = allowed(uri) ?: throw FileNotFoundException(uri.toString())
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val file = allowed(uri) ?: throw FileNotFoundException(uri.toString())
        val cols = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val cursor = MatrixCursor(cols)
        cursor.addRow(
            cols.map {
                when (it) {
                    OpenableColumns.DISPLAY_NAME -> file.name
                    OpenableColumns.SIZE -> file.length()
                    else -> null
                }
            },
        )
        return cursor
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0

    /** Solo archivos de Ubuntu o de la memoria compartida; nunca otra cosa de la app. */
    private fun allowed(uri: Uri): File? {
        val ctx = context ?: return null
        val file = File(uri.path ?: return null).canonicalFile
        if (!file.isFile) return null
        val roots = listOf(Linux(ctx).rootfs, Environment.getExternalStorageDirectory())
            .map { it.canonicalPath + File.separator }
        return if (roots.any { file.path.startsWith(it) }) file else null
    }

    companion object {
        fun uriFor(ctx: Context, file: File): Uri = Uri.Builder()
            .scheme("content")
            .authority(ctx.packageName + ".archivos")
            .path(file.absolutePath)
            .build()

        fun mimeOf(file: File): String {
            val ext = file.extension.lowercase()
            return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
                ?: when (ext) {
                    "md", "log", "sh", "py", "js", "ts", "kt", "json", "yml", "yaml", "toml", "c", "h", "rs" -> "text/plain"
                    else -> "application/octet-stream"
                }
        }
    }
}
