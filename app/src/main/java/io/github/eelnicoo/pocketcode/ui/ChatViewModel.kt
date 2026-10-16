package io.github.eelnicoo.pocketcode.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.eelnicoo.pocketcode.api.ClaudeClient
import io.github.eelnicoo.pocketcode.api.History
import io.github.eelnicoo.pocketcode.build.ApkBuilder
import io.github.eelnicoo.pocketcode.data.ChatFile
import io.github.eelnicoo.pocketcode.data.ChatMeta
import io.github.eelnicoo.pocketcode.data.ChatStore
import io.github.eelnicoo.pocketcode.data.Settings
import io.github.eelnicoo.pocketcode.oauth.ClaudeOAuth
import io.github.eelnicoo.pocketcode.tools.BuildApkTool
import io.github.eelnicoo.pocketcode.tools.EditFileTool
import io.github.eelnicoo.pocketcode.tools.GrepTool
import io.github.eelnicoo.pocketcode.tools.ListDirTool
import io.github.eelnicoo.pocketcode.tools.ReadFileTool
import io.github.eelnicoo.pocketcode.tools.ShellTool
import io.github.eelnicoo.pocketcode.tools.ToolRegistry
import io.github.eelnicoo.pocketcode.tools.Workspace
import io.github.eelnicoo.pocketcode.tools.WriteFileTool
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A file or image attached in the composer, not yet sent. */
data class Attachment(
    val id: String,
    val kind: Kind,
    val name: String,
    val mediaType: String,
    /** Base64 payload for images / PDFs. */
    val data: String? = null,
    /** Text payload for text files. */
    val text: String? = null,
    val thumbnail: ImageBitmap? = null,
) {
    enum class Kind { IMAGE, PDF, TEXT }
}

data class ChatUiState(
    val chats: List<ChatMeta> = emptyList(),
    val chatId: String? = null,
    val title: String = "Nuevo chat",
    val starred: Boolean = false,
    val messages: List<JsonObject> = emptyList(),
    val draft: List<JsonObject>? = null,
    val runningTools: Set<String> = emptySet(),
    val busy: Boolean = false,
    val notice: String? = null,
    val composerText: String = "",
    val attachments: List<Attachment> = emptyList(),
    /** History index of the human message being edited, if any. */
    val editingIndex: Int? = null,
    val attaching: Boolean = false,
)

class ChatViewModel(private val app: Application) : AndroidViewModel(app) {

    val settings = Settings.get(app)
    val oauth = ClaudeOAuth(settings)
    private val store = ChatStore(File(app.filesDir, "chats"))

    private val workspace = Workspace {
        val dir = settings.workingDir.ifBlank { File(app.filesDir, "workspace").absolutePath }
        File(dir).apply { if (!exists()) mkdirs() }
    }
    private val registry = ToolRegistry(
        listOf(
            ReadFileTool(workspace),
            WriteFileTool(workspace),
            EditFileTool(workspace),
            ListDirTool(workspace),
            GrepTool(workspace),
            ShellTool(useShizuku = { settings.useShizuku }, workingDir = { workspace.resolve(".") }),
            BuildApkTool(workspace, ApkBuilder(app)),
        )
    )
    private val client = ClaudeClient(oauth, registry)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val ioSerial = Dispatchers.IO.limitedParallelism(1)

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state
    val settingsState get() = settings.state

    private var agentJob: Job? = null
    /** Non-blank non-text blocks kept from a message being edited (images, files). */
    private var editingKeep: List<JsonObject> = emptyList()

    init {
        viewModelScope.launch(ioSerial) {
            val chats = store.list()
            _state.update { it.copy(chats = chats) }
            // Reopen the last chat if it was used recently; otherwise start fresh, like Claude.
            val lastId = settings.lastChatId
            val recent = chats.firstOrNull { it.id == lastId && System.currentTimeMillis() - it.updatedAt < RESUME_WINDOW_MS }
            if (recent != null) store.load(recent.id)?.let { show(it) }
        }
    }

    // ------------------------------------------------------------------ navigation

    fun newChat() {
        stopAgent()
        settings.lastChatId = null
        editingKeep = emptyList()
        _state.update {
            ChatUiState(chats = it.chats, composerText = "", attachments = emptyList())
        }
    }

    fun openChat(id: String) {
        if (id == _state.value.chatId) return
        stopAgent()
        viewModelScope.launch(ioSerial) {
            val chat = store.load(id)
            if (chat == null) {
                _state.update { it.copy(notice = "No se pudo abrir el chat.", chats = store.list()) }
                return@launch
            }
            show(chat)
            settings.lastChatId = id
        }
    }

    private fun show(chat: ChatFile) {
        editingKeep = emptyList()
        _state.update {
            it.copy(
                chatId = chat.meta.id,
                title = chat.meta.title,
                starred = chat.meta.starred,
                messages = History.repair(chat.messages, "Interrumpido."),
                draft = null,
                runningTools = emptySet(),
                busy = false,
                notice = null,
                editingIndex = null,
                composerText = "",
                attachments = emptyList(),
            )
        }
    }

    fun renameChat(id: String, title: String) {
        val t = title.trim().ifEmpty { return }
        viewModelScope.launch(ioSerial) {
            val meta = store.list().firstOrNull { it.id == id } ?: return@launch
            store.updateMeta(meta.copy(title = t, titled = true))
            refreshList()
            if (_state.value.chatId == id) _state.update { it.copy(title = t) }
        }
    }

    fun toggleStar(id: String) {
        viewModelScope.launch(ioSerial) {
            val meta = store.list().firstOrNull { it.id == id } ?: return@launch
            store.updateMeta(meta.copy(starred = !meta.starred))
            refreshList()
            if (_state.value.chatId == id) _state.update { it.copy(starred = !meta.starred) }
        }
    }

    fun deleteChat(id: String) {
        if (_state.value.chatId == id) newChat()
        viewModelScope.launch(ioSerial) {
            store.delete(id)
            refreshList()
        }
    }

    private fun refreshList() {
        val chats = store.list()
        _state.update { it.copy(chats = chats) }
    }

    // ------------------------------------------------------------------ composer

    fun setComposerText(text: String) = _state.update { it.copy(composerText = text) }

    fun dismissNotice() = _state.update { it.copy(notice = null) }

    fun setModel(id: String) { settings.model = id }

    fun setExtendedThinking(on: Boolean) { settings.extendedThinking = on }

    fun removeAttachment(id: String) = _state.update { s -> s.copy(attachments = s.attachments.filterNot { it.id == id }) }

    fun startEdit(index: Int) {
        if (_state.value.busy) return
        val msg = _state.value.messages.getOrNull(index) ?: return
        if (!History.isHumanTurn(msg)) return
        val blocks = History.blocks(msg)
        val text = blocks.filter { History.type(it) == "text" }
            .map { History.str(it, "text") ?: "" }
            .filterNot { it.startsWith(History.ATTACHMENT_PREFIX) }
            .joinToString("\n\n")
        editingKeep = blocks.filter {
            val t = History.type(it)
            t != "tool_result" && (t != "text" || (History.str(it, "text") ?: "").startsWith(History.ATTACHMENT_PREFIX))
        }
        _state.update { it.copy(editingIndex = index, composerText = text) }
    }

    fun cancelEdit() {
        editingKeep = emptyList()
        _state.update { it.copy(editingIndex = null, composerText = "") }
    }

    fun send() {
        val s = _state.value
        if (s.busy || s.attaching) return
        val text = s.composerText.trim()
        if (text.isEmpty() && s.attachments.isEmpty() && editingKeep.isEmpty()) return

        val content = ArrayList<JsonObject>()
        if (s.editingIndex != null) content.addAll(editingKeep)
        s.attachments.forEach { content.add(blockFor(it)) }
        if (text.isNotEmpty()) content.add(History.textBlock(text))

        val base = if (s.editingIndex != null) s.messages.subList(0, s.editingIndex) else s.messages
        val history = History.appendUser(History.repair(base, "Interrumpido."), content)
        editingKeep = emptyList()

        val now = System.currentTimeMillis()
        val meta = currentMeta(now) ?: ChatMeta(
            id = ChatStore.newId(),
            title = History.fallbackTitle(History.firstUserText(history)),
            createdAt = now,
            updatedAt = now,
            model = settings.model,
        )
        settings.lastChatId = meta.id
        _state.update {
            it.copy(
                chatId = meta.id,
                title = meta.title,
                composerText = "",
                attachments = emptyList(),
                editingIndex = null,
                notice = null,
            )
        }
        runAgent(history, meta)
    }

    /** Regenerates the last answer. */
    fun retry() {
        val s = _state.value
        if (s.busy) return
        val history = History.truncateForRetry(History.repair(s.messages, "Interrumpido."))
        if (history.isEmpty() || !History.isHumanTurn(history.last())) return
        val meta = currentMeta(System.currentTimeMillis()) ?: return
        _state.update { it.copy(notice = null) }
        runAgent(history, meta)
    }

    fun stop() = stopAgent()

    private fun stopAgent() {
        agentJob?.cancel()
        agentJob = null
    }

    private fun currentMeta(now: Long): ChatMeta? {
        val id = _state.value.chatId ?: return null
        val known = _state.value.chats.firstOrNull { it.id == id }
        return (known ?: ChatMeta(id, _state.value.title, now, now)).copy(updatedAt = now)
    }

    // ------------------------------------------------------------------ agent

    private fun runAgent(history: List<JsonObject>, meta0: ChatMeta) {
        var meta = meta0.copy(model = settings.model)
        var current = history
        _state.update { it.copy(messages = history, draft = null, busy = true, runningTools = emptySet()) }
        persist(meta, current)

        val listener = object : ClaudeClient.Listener {
            override fun onDraft(blocks: List<JsonObject>?) = _state.update { it.copy(draft = blocks) }
            override fun onAppend(message: JsonObject) {
                current = current + message
                _state.update { it.copy(messages = current, draft = null) }
                meta = meta.copy(updatedAt = System.currentTimeMillis())
                persist(meta, current)
            }
            override fun onToolRunning(id: String, running: Boolean) = _state.update { s ->
                s.copy(runningTools = if (running) s.runningTools + id else s.runningTools - id)
            }
            override fun onNotice(text: String) = _state.update { it.copy(notice = text) }
        }

        val job = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { client.run(history, config(), listener) }
                maybeTitle(meta, current)
            } catch (c: CancellationException) {
                // Keep what was already written, like the Claude app does when you stop it.
                val partial = _state.value.draft.orEmpty()
                    .filter { History.type(it) == "text" && !History.str(it, "text").isNullOrBlank() }
                    .map { History.textBlock(History.str(it, "text") ?: "") }
                if (partial.isNotEmpty() && current.lastOrNull()?.let { History.role(it) } == "user") {
                    current = current + History.message("assistant", partial)
                }
                throw c
            } catch (e: Exception) {
                _state.update { it.copy(notice = e.message ?: "Error desconocido") }
            } finally {
                withContext(NonCancellable) {
                    current = History.repair(current, "Interrumpido por el usuario.")
                    if (_state.value.chatId == meta.id) {
                        _state.update { it.copy(messages = current, draft = null, busy = false, runningTools = emptySet()) }
                    }
                    meta = meta.copy(updatedAt = System.currentTimeMillis())
                    persist(meta, current)
                }
            }
        }
        agentJob = job
    }

    private fun persist(meta: ChatMeta, messages: List<JsonObject>) {
        viewModelScope.launch(ioSerial + NonCancellable) {
            store.save(ChatFile(meta, messages))
            refreshList()
        }
    }

    private fun maybeTitle(meta: ChatMeta, messages: List<JsonObject>) {
        val known = store.list().firstOrNull { it.id == meta.id }
        if (known?.titled == true || meta.titled) return
        val firstUser = History.firstUserText(messages)
        val firstReply = History.assistantText(messages).take(800)
        if (firstUser.isBlank() && firstReply.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val title = client.generateTitle(firstUser, firstReply) ?: return@launch
            withContext(ioSerial) {
                val latest = store.list().firstOrNull { it.id == meta.id } ?: return@withContext
                if (latest.titled) return@withContext
                store.updateMeta(latest.copy(title = title, titled = true))
                refreshList()
                if (_state.value.chatId == meta.id) _state.update { it.copy(title = title) }
            }
        }
    }

    private fun config() = ClaudeClient.Config(
        model = settings.model,
        maxTokens = settings.maxTokens,
        effort = if (settings.extendedThinking) "high" else "low",
        context = buildString {
            append("Dispositivo: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}).\n")
            append("Directorio de trabajo: ${workspace.resolve(".").canonicalPath}\n")
            append("Shizuku para run_shell: ${if (settings.useShizuku && ShellTool.shizukuReady()) "activo" else "inactivo (sandbox de la app)"}.\n")
            append("Fecha: ${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.")
            val name = settings.displayName.trim()
            if (name.isNotEmpty()) append("\nEl usuario se llama $name.")
        },
    )

    // ------------------------------------------------------------------ attachments

    fun addImage(uri: Uri) = addAttachment { image(uri) }

    fun addFile(uri: Uri) = addAttachment {
        val mime = app.contentResolver.getType(uri) ?: ""
        if (mime.startsWith("image/")) image(uri) else document(uri, mime)
    }

    private fun addAttachment(make: () -> Attachment) {
        _state.update { it.copy(attaching = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching(make)
            _state.update { s ->
                result.fold(
                    onSuccess = { s.copy(attachments = s.attachments + it, attaching = false) },
                    onFailure = { s.copy(notice = it.message ?: "No se pudo adjuntar el archivo.", attaching = false) },
                )
            }
        }
    }

    private fun displayName(uri: Uri): String =
        app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment ?: "archivo"

    private fun image(uri: Uri): Attachment {
        val cr = app.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_IMAGE_SIDE) sample *= 2
        val decoded = cr.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("No se pudo leer la imagen.")
        val rotation = runCatching {
            cr.openInputStream(uri)?.use { s ->
                when (ExifInterface(s).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        }.getOrDefault(0f)
        var bmp = decoded
        if (rotation != 0f) {
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rotation) }, true)
        }
        val scale = MAX_IMAGE_SIDE.toFloat() / maxOf(bmp.width, bmp.height)
        if (scale < 1f) {
            bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt().coerceAtLeast(1), (bmp.height * scale).toInt().coerceAtLeast(1), true)
        }
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
        val tScale = 192f / maxOf(bmp.width, bmp.height)
        val thumb = if (tScale < 1f) {
            Bitmap.createScaledBitmap(bmp, (bmp.width * tScale).toInt().coerceAtLeast(1), (bmp.height * tScale).toInt().coerceAtLeast(1), true)
        } else bmp
        return Attachment(
            id = ChatStore.newId(),
            kind = Attachment.Kind.IMAGE,
            name = displayName(uri),
            mediaType = "image/jpeg",
            data = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP),
            thumbnail = thumb.asImageBitmap(),
        )
    }

    private fun document(uri: Uri, mime: String): Attachment {
        val name = displayName(uri)
        val bytes = app.contentResolver.openInputStream(uri)?.use { input ->
            val buf = ByteArrayOutputStream()
            val chunk = ByteArray(64 * 1024)
            var total = 0
            while (true) {
                val n = input.read(chunk)
                if (n < 0) break
                total += n
                if (total > MAX_FILE_BYTES) error("El archivo es demasiado grande (máx. ${MAX_FILE_BYTES / 1_000_000} MB).")
                buf.write(chunk, 0, n)
            }
            buf.toByteArray()
        } ?: error("No se pudo leer el archivo.")
        if (mime == "application/pdf" || name.endsWith(".pdf", ignoreCase = true)) {
            return Attachment(ChatStore.newId(), Attachment.Kind.PDF, name, "application/pdf",
                data = Base64.encodeToString(bytes, Base64.NO_WRAP))
        }
        if (bytes.take(8000).any { it == 0.toByte() }) error("Solo se pueden adjuntar texto, PDF o imágenes.")
        var text = bytes.toString(Charsets.UTF_8)
        if (text.length > MAX_TEXT_CHARS) text = text.take(MAX_TEXT_CHARS) + "\n…[recortado]"
        return Attachment(ChatStore.newId(), Attachment.Kind.TEXT, name, mime.ifEmpty { "text/plain" }, text = text)
    }

    private fun blockFor(a: Attachment): JsonObject = when (a.kind) {
        Attachment.Kind.IMAGE -> buildJsonObject {
            put("type", "image")
            put("source", buildJsonObject {
                put("type", "base64")
                put("media_type", a.mediaType)
                put("data", a.data ?: "")
            })
        }
        Attachment.Kind.PDF -> buildJsonObject {
            put("type", "document")
            put("title", a.name)
            put("source", buildJsonObject {
                put("type", "base64")
                put("media_type", "application/pdf")
                put("data", a.data ?: "")
            })
        }
        Attachment.Kind.TEXT -> History.textBlock(
            "${History.ATTACHMENT_PREFIX}${a.name.replace("\"", "'")}\">\n${a.text}\n</archivo>"
        )
    }

    companion object {
        private const val RESUME_WINDOW_MS = 30 * 60 * 1000L
        private const val MAX_IMAGE_SIDE = 1568
        private const val MAX_FILE_BYTES = 15_000_000
        private const val MAX_TEXT_CHARS = 300_000
    }
}
