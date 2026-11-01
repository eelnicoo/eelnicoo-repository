package dev.nico.mtsamp4

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class Status { PENDING, RUNNING, DONE, FAILED, CANCELLED }

data class ConversionItem(
    val id: Long,
    val source: Uri,
    val name: String,
    val size: Long,
    val modifiedMs: Long,
    val audioAac: Boolean,
    val status: Status = Status.PENDING,
    val progress: Float = 0f,
    val videoInfo: String? = null,
    val audioInfo: String? = null,
    val recordedMs: Long = 0,
    val dateSource: Int = NativeRemuxer.DATE_NONE,
    val output: Uri? = null,
    val outputName: String? = null,
    val outputSize: Long = 0,
    val error: String? = null,
    val startedAt: Long = 0,
    val finishedAt: Long = 0,
) {
    val finished get() = status == Status.DONE || status == Status.FAILED || status == Status.CANCELLED
}

/** Cola de conversiones compartida por la pantalla y el servicio. */
object Conversions {
    private const val TAG = "Conversions"
    private val lock = Any()
    private val nextId = AtomicLong(1)
    private val cancelled = ConcurrentHashMap.newKeySet<Long>()
    private val io = Executors.newSingleThreadExecutor()

    private val _items = MutableStateFlow<List<ConversionItem>>(emptyList())
    val items: StateFlow<List<ConversionItem>> = _items.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    /** Mensaje corto para la pantalla (por ejemplo, «no hay .MTS en la carpeta»). */
    val notice: StateFlow<String?> = _notice.asStateFlow()

    fun clearNotice() {
        _notice.value = null
    }

    private fun mutate(transform: (List<ConversionItem>) -> List<ConversionItem>) {
        synchronized(lock) { _items.value = transform(_items.value) }
    }

    fun update(id: Long, transform: (ConversionItem) -> ConversionItem) =
        mutate { list -> list.map { if (it.id == id) transform(it) else it } }

    fun get(id: Long): ConversionItem? = _items.value.firstOrNull { it.id == id }

    fun hasPending(): Boolean = _items.value.any { it.status == Status.PENDING }

    /** Saca el siguiente de la cola y lo marca como en curso. */
    fun takeNext(): ConversionItem? = synchronized(lock) {
        val next = _items.value.firstOrNull { it.status == Status.PENDING } ?: return null
        val running = next.copy(
            status = Status.RUNNING,
            progress = 0f,
            startedAt = System.currentTimeMillis(),
        )
        _items.value = _items.value.map { if (it.id == next.id) running else it }
        running
    }

    fun isCancelled(id: Long) = id in cancelled

    fun cancel(id: Long) {
        cancelled.add(id)
        mutate { list ->
            list.map {
                if (it.id == id && it.status == Status.PENDING) {
                    it.copy(status = Status.CANCELLED, finishedAt = System.currentTimeMillis())
                } else {
                    it
                }
            }
        }
    }

    fun cancelAll() {
        _items.value.filter { !it.finished }.forEach { cancel(it.id) }
    }

    fun retry(context: Context, id: Long) {
        cancelled.remove(id)
        val aac = Settings.audioAac(context)
        update(id) {
            ConversionItem(
                id = it.id,
                source = it.source,
                name = it.name,
                size = it.size,
                modifiedMs = it.modifiedMs,
                audioAac = aac,
            )
        }
        ConversionService.start(context)
    }

    fun removeFinished() = mutate { list -> list.filter { !it.finished } }

    /** Añade archivos elegidos o compartidos. */
    fun enqueue(context: Context, uris: List<Uri>) {
        val app = context.applicationContext
        val aac = Settings.audioAac(app)
        io.execute {
            val found = uris.distinct().map { describe(app, it, aac) }
            add(found)
            ConversionService.start(app)
        }
    }

    /** Añade todos los .MTS/.M2TS de una carpeta (y sus subcarpetas). */
    fun enqueueTree(context: Context, tree: Uri) {
        val app = context.applicationContext
        val aac = Settings.audioAac(app)
        _scanning.value = true
        io.execute {
            try {
                val found = mutableListOf<ConversionItem>()
                val rootId = DocumentsContract.getTreeDocumentId(tree)
                scanTree(app, tree, rootId, 0, aac, found)
                found.sortBy { it.name.lowercase() }
                if (found.isEmpty()) {
                    _notice.value = "No hay archivos .MTS ni .M2TS en esa carpeta."
                } else {
                    add(found)
                    ConversionService.start(app)
                }
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo leer la carpeta", e)
                _notice.value = "No se pudo leer la carpeta."
            } finally {
                _scanning.value = false
            }
        }
    }

    private fun add(newItems: List<ConversionItem>) {
        if (newItems.isEmpty()) return
        mutate { it + newItems }
    }

    private fun scanTree(
        context: Context,
        tree: Uri,
        docId: String,
        depth: Int,
        aac: Boolean,
        out: MutableList<ConversionItem>,
    ) {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        val dirs = mutableListOf<String>()
        context.contentResolver.query(children, projection, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                val mime = c.getString(2)
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    dirs += id
                } else if (isTransportStreamName(name)) {
                    out += ConversionItem(
                        id = nextId.getAndIncrement(),
                        source = DocumentsContract.buildDocumentUriUsingTree(tree, id),
                        name = name,
                        size = if (c.isNull(3)) 0 else c.getLong(3),
                        modifiedMs = if (c.isNull(4)) 0 else c.getLong(4),
                        audioAac = aac,
                    )
                }
            }
        }
        if (depth < 8) dirs.forEach { scanTree(context, tree, it, depth + 1, aac, out) }
    }

    fun isTransportStreamName(name: String): Boolean {
        val n = name.lowercase()
        return n.endsWith(".mts") || n.endsWith(".m2ts") || n.endsWith(".m2t")
    }

    private fun describe(context: Context, uri: Uri, aac: Boolean): ConversionItem {
        var name: String? = null
        var size = 0L
        var modified = 0L
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    name = c.stringOrNull(OpenableColumns.DISPLAY_NAME)
                    size = c.longOrZero(OpenableColumns.SIZE)
                    modified = c.longOrZero(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                    if (modified == 0L) {
                        // MediaStore guarda la fecha en segundos.
                        modified = c.longOrZero(MediaStore.MediaColumns.DATE_MODIFIED) * 1000
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo leer la información de $uri", e)
        }
        val fallbackName = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        return ConversionItem(
            id = nextId.getAndIncrement(),
            source = uri,
            name = name ?: fallbackName ?: "video.mts",
            size = size,
            modifiedMs = modified,
            audioAac = aac,
        )
    }

    private fun Cursor.stringOrNull(column: String): String? {
        val i = getColumnIndex(column)
        return if (i >= 0 && !isNull(i)) getString(i) else null
    }

    private fun Cursor.longOrZero(column: String): Long {
        val i = getColumnIndex(column)
        return if (i >= 0 && !isNull(i)) getLong(i) else 0L
    }
}

object Settings {
    private const val FILE = "ajustes"
    private const val AUDIO_AAC = "audio_aac"

    fun audioAac(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(AUDIO_AAC, false)

    fun setAudioAac(context: Context, value: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putBoolean(AUDIO_AAC, value).apply()
    }
}
