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
import io.github.eelnicoo.pocketcode.api.ApiException
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
    /** Attachments still being processed. */
    val pendingAttachments: Int = 0,
    /** Bumped when the composer should take focus (edit, suggestion). */
    val focusTick: Int = 0,
) {
    val attaching: Boolean get() = pendingAttachments > 0
}

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

    /** All ChatStore access goes through this single thread, in order. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val ioSerial = Dispatchers.IO.limitedParallelism(1)

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state
    val settingsState get() = settings.state

    private var agentJob: Job? = null

    /** Id of the newest agent run; callbacks of older runs must not touch the UI. */
    @Volatile private var activeRun = 0

    /** Bumped on every navigation (new chat, open chat, send); async loads check it. */
    private var navSeq = 0

    /** Chats deleted in this session. Only touched on [ioSerial]. */
    private val deletedIds = HashSet<String>()

    /** Frozen system context of the chat on screen (see [ChatFile.context]). */
    private var chatContext: String? = null

    /** Composer content saved when an edit starts, restored on cancel. */
    private var savedDraft: Pair<String, List<Attachment>>? = null

    init {
        viewModelScope.launch(ioSerial) {
            val chats = safeStore { store.list() } ?: emptyList()
            _state.update { it.copy(chats = chats) }
            // Reopen the last chat if it was used recently; otherwise start fresh, like Claude.
            val lastId = settings.lastChatId
            val recent = chats.firstOrNull { it.id == lastId && System.currentTimeMillis() - it.updatedAt < RESUME_WINDOW_MS }
                ?: return@launch
            val chat = safeStore { store.load(recent.id) } ?: return@launch
            withContext(Dispatchers.Main) {
                val s = _state.value
                val pristine = navSeq == 0 && s.chatId == null && s.messages.isEmpty() && !s.busy &&
                    s.composerText.isEmpty() && s.attachments.isEmpty()
                if (pristine) show(chat)
            }
        }
    }

    // ------------------------------------------------------------------ navigation

    fun newChat() {
        stopAgent()
        navSeq++
        settings.lastChatId = null
        chatContext = null
        savedDraft = null
        _state.update { ChatUiState(chats = it.chats) }
    }

    fun openChat(id: String) {
        if (id == _state.value.chatId) return
        stopAgent()
        val seq = ++navSeq
        viewModelScope.launch(ioSerial) {
            val chat = safeStore { store.load(id) }
            withContext(Dispatchers.Main) {
                if (navSeq != seq) return@withContext
                if (chat == null) {
                    _state.update { it.copy(notice = "No se pudo abrir el chat.") }
                } else {
                    show(chat)
                    settings.lastChatId = id
                }
            }
        }
    }

    private fun show(chat: ChatFile) {
        chatContext = chat.context
        savedDraft = null
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
                pendingAttachments = 0,
            )
        }
    }

    fun renameChat(id: String, title: String) {
        val t = title.trim().ifEmpty { return }
        if (_state.value.chatId == id) _state.update { it.copy(title = t) }
        viewModelScope.launch(ioSerial) {
            val meta = safeStore { store.list() }?.firstOrNull { it.id == id } ?: return@launch
            safeStore { store.updateMeta(meta.copy(title = t, titled = true)) }
            refreshList()
        }
    }

    fun toggleStar(id: String) {
        viewModelScope.launch(ioSerial) {
            val meta = safeStore { store.list() }?.firstOrNull { it.id == id } ?: return@launch
            val starred = !meta.starred
            safeStore { store.updateMeta(meta.copy(starred = starred)) }
            refreshList()
            _state.update { if (it.chatId == id) it.copy(starred = starred) else it }
        }
    }

    fun deleteChat(id: String) {
        if (_state.value.chatId == id) newChat()
        viewModelScope.launch(ioSerial) {
            // Tombstone first, so a stopping run's final save cannot bring it back.
            deletedIds.add(id)
            safeStore { store.delete(id) }
            refreshList()
        }
    }

    private fun refreshList() {
        val chats = safeStore { store.list() } ?: return
        _state.update { it.copy(chats = chats) }
    }

    // ------------------------------------------------------------------ composer

    fun setComposerText(text: String) = _state.update { it.copy(composerText = text) }

    /** Fills the composer from a greeting suggestion and focuses it. */
    fun applySuggestion(text: String) = _state.update { it.copy(composerText = text, focusTick = it.focusTick + 1) }

    fun dismissNotice() = _state.update { it.copy(notice = null) }

    fun showNotice(text: String) = _state.update { it.copy(notice = text) }

    fun setModel(id: String) { settings.model = id }

    fun setExtendedThinking(on: Boolean) { settings.extendedThinking = on }

    fun removeAttachment(id: String) = _state.update { s -> s.copy(attachments = s.attachments.filterNot { it.id == id }) }

    /** Puts a sent message back in the composer; sending replaces it and what followed. */
    fun startEdit(index: Int) {
        val s = _state.value
        if (s.busy) return
        val msg = s.messages.getOrNull(index) ?: return
        if (!History.isHumanTurn(msg)) return
        if (s.editingIndex == null) savedDraft = s.composerText to s.attachments
        val blocks = History.blocks(msg)
        val text = blocks.filter { History.type(it) == "text" }
            .map { History.str(it, "text") ?: "" }
            .filterNot { it.startsWith(History.ATTACHMENT_PREFIX) }
            .joinToString("\n\n")
        _state.update { it.copy(editingIndex = index, composerText = text, attachments = emptyList(), focusTick = it.focusTick + 1) }
        // Former attachments come back as removable chips.
        blocks.forEach { b ->
            val t = History.type(b)
            val isAttachmentText = t == "text" && (History.str(b, "text") ?: "").startsWith(History.ATTACHMENT_PREFIX)
            if (t == "image" || t == "document" || isAttachmentText) addAttachment { attachmentFromBlock(b) }
        }
    }

    fun cancelEdit() {
        val draft = savedDraft
        savedDraft = null
        _state.update {
            it.copy(
                editingIndex = null,
                composerText = draft?.first ?: "",
                attachments = draft?.second ?: emptyList(),
            )
        }
    }

    fun send() {
        val s = _state.value
        if (s.busy || s.attaching) return
        val text = s.composerText.trim()
        if (text.isEmpty() && s.attachments.isEmpty()) return

        val content = ArrayList<JsonObject>()
        s.attachments.forEach { content.add(blockFor(it)) }
        if (text.isNotEmpty()) content.add(History.textBlock(text))

        val editing = s.editingIndex
        val base = if (editing != null) s.messages.subList(0, editing.coerceAtMost(s.messages.size)) else s.messages
        val history = History.appendUser(History.repair(base, "Interrumpido."), content)
        savedDraft = null
        navSeq++

        val now = System.currentTimeMillis()
        val meta = currentMeta(now) ?: ChatMeta(
            id = ChatStore.newId(),
            title = History.fallbackTitle(History.firstUserText(history)),
            createdAt = now,
            updatedAt = now,
            model = settings.model,
        )
        val context = chatContext ?: buildContext().also { chatContext = it }
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
        runAgent(history, meta, context)
    }

    /**
     * Regenerates the last answer, or continues a turn that was cut short (stopped,
     * network error, step limit) when the history ends with a user message.
     */
    fun retry() {
        val s = _state.value
        if (s.busy) return
        val repaired = History.repair(s.messages, "Interrumpido.")
        val last = repaired.lastOrNull() ?: return
        val history = if (History.role(last) == "assistant") History.truncateForRetry(repaired) else repaired
        if (history.isEmpty() || History.role(history.last()) != "user") return
        val meta = currentMeta(System.currentTimeMillis()) ?: return
        val context = chatContext ?: buildContext().also { chatContext = it }
        _state.update { it.copy(notice = null) }
        runAgent(history, meta, context)
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

    private fun runAgent(history: List<JsonObject>, meta0: ChatMeta, context: String) {
        val mine = ++activeRun
        var meta = meta0.copy(model = settings.model)
        var current = history
        var lastDraft: List<JsonObject>? = null
        var refused = false

        /** True while this run still owns the screen. */
        fun owns(s: ChatUiState) = activeRun == mine && s.chatId == meta.id

        _state.update { it.copy(messages = history, draft = null, busy = true, runningTools = emptySet()) }
        persist(meta, current, context)

        val listener = object : ClaudeClient.Listener {
            override fun onDraft(blocks: List<JsonObject>?) {
                if (blocks != null) lastDraft = blocks
                _state.update { if (owns(it)) it.copy(draft = blocks) else it }
            }
            override fun onAppend(message: JsonObject) {
                current = current + message
                lastDraft = null
                _state.update { if (owns(it)) it.copy(messages = current, draft = null) else it }
                meta = meta.copy(updatedAt = System.currentTimeMillis())
                persist(meta, current, context)
            }
            override fun onToolRunning(id: String, running: Boolean) = _state.update { s ->
                if (!owns(s)) s else s.copy(runningTools = if (running) s.runningTools + id else s.runningTools - id)
            }
            override fun onNotice(text: String) = _state.update { if (owns(it)) it.copy(notice = text) else it }
            override fun onRefusal() { refused = true }
        }

        /** Keeps what Claude had already written when a run ends early. */
        fun keepPartial() {
            val partial = lastDraft.orEmpty()
                .filter { History.type(it) == "text" && !History.str(it, "text").isNullOrBlank() }
                .map { History.textBlock(History.str(it, "text") ?: "") }
            if (partial.isNotEmpty() && current.lastOrNull()?.let { History.role(it) } == "user") {
                current = current + History.message("assistant", partial)
            }
            lastDraft = null
        }

        agentJob = viewModelScope.launch {
            var error: Exception? = null
            try {
                withContext(Dispatchers.IO) { client.run(history, config(context), listener) }
            } catch (c: CancellationException) {
                keepPartial()
                throw c
            } catch (e: Exception) {
                keepPartial()
                error = e
            } finally {
                withContext(NonCancellable) {
                    current = History.repair(current, "Interrumpido.")
                    meta = meta.copy(updatedAt = System.currentTimeMillis())
                    persist(meta, current, context)
                    if (activeRun == mine) {
                        _state.update {
                            if (it.chatId != meta.id) it else it.copy(
                                messages = current,
                                draft = null,
                                busy = false,
                                runningTools = emptySet(),
                                notice = error?.let { e -> e.message ?: "Error desconocido" } ?: it.notice,
                            )
                        }
                    }
                }
            }
            // Only reached when the run was not cancelled.
            if (activeRun != mine || _state.value.chatId != meta.id) return@launch
            val lastHuman = History.lastHumanIndex(current)
            val endsWithHuman = lastHuman >= 0 && lastHuman == current.lastIndex
            val rejectedInput = (error as? ApiException)?.status.let { it == 400 || it == 413 }
            if (endsWithHuman && (refused || rejectedInput)) {
                // Let the user fix the message (or drop an attachment) instead of resending it.
                startEdit(lastHuman)
            } else if (error == null) {
                maybeTitle(meta.id, current)
            }
        }
    }

    /**
     * Saves the transcript. User-owned metadata (title, star) always comes from the store,
     * so a run never undoes a rename or star made while it was running.
     */
    private fun persist(meta: ChatMeta, messages: List<JsonObject>, context: String) {
        viewModelScope.launch(ioSerial + NonCancellable) {
            if (meta.id in deletedIds) return@launch
            safeStore {
                val stored = store.list().firstOrNull { it.id == meta.id }
                val m = if (stored == null) meta else meta.copy(
                    title = stored.title,
                    starred = stored.starred,
                    titled = stored.titled || meta.titled,
                    createdAt = stored.createdAt,
                )
                store.save(ChatFile(m, messages, context))
            }
            refreshList()
        }
    }

    private fun maybeTitle(id: String, messages: List<JsonObject>) {
        val firstUser = History.firstUserText(messages)
        val firstReply = History.assistantText(messages).take(800)
        if (firstUser.isBlank() && firstReply.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val known = withContext(ioSerial) { safeStore { store.list() }?.firstOrNull { it.id == id } }
            if (known == null || known.titled) return@launch
            val title = client.generateTitle(firstUser, firstReply) ?: return@launch
            withContext(ioSerial) {
                if (id in deletedIds) return@withContext
                val latest = safeStore { store.list() }?.firstOrNull { it.id == id } ?: return@withContext
                if (latest.titled) return@withContext
                safeStore { store.updateMeta(latest.copy(title = title, titled = true)) }
                refreshList()
                _state.update { if (it.chatId == id) it.copy(title = title) else it }
            }
        }
    }

    /** Runs a store operation, turning I/O failures into a notice instead of a crash. */
    private inline fun <T> safeStore(block: () -> T): T? = try {
        block()
    } catch (t: Throwable) {
        _state.update { it.copy(notice = "No se pudo guardar o leer el historial: ${t.message ?: t.javaClass.simpleName}") }
        null
    }

    private fun config(context: String) = ClaudeClient.Config(
        model = settings.model,
        maxTokens = settings.maxTokens,
        effort = if (settings.extendedThinking) "high" else "low",
        context = context,
    )

    /**
     * Context for the system prompt, built once per chat and then frozen: changing the
     * system prompt mid-conversation would invalidate earlier thinking blocks.
     */
    private fun buildContext(): String = buildString {
        append("Dispositivo: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}).\n")
        append("Directorio de trabajo: ${workspace.resolve(".").canonicalPath}\n")
        append("La salida de run_shell indica [shizuku] (permisos de depuración) o [sandbox] (solo la app).\n")
        append("Fecha de inicio de la conversación: ${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.")
        val name = settings.displayName.trim()
        if (name.isNotEmpty()) append("\nEl usuario se llama $name.")
    }

    // ------------------------------------------------------------------ attachments

    fun addImage(uri: Uri) = addAttachment { image(uri) }

    fun addFile(uri: Uri) = addAttachment {
        val mime = app.contentResolver.getType(uri) ?: ""
        if (mime.startsWith("image/")) image(uri) else document(uri, mime)
    }

    private fun addAttachment(make: () -> Attachment) {
        val seq = navSeq
        _state.update { it.copy(pendingAttachments = it.pendingAttachments + 1) }
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching(make)
            withContext(Dispatchers.Main) {
                // The chat changed meanwhile: its state (and counter) was already reset.
                if (navSeq != seq) return@withContext
                _state.update { s ->
                    val pending = (s.pendingAttachments - 1).coerceAtLeast(0)
                    result.fold(
                        onSuccess = { s.copy(attachments = s.attachments + it, pendingAttachments = pending) },
                        onFailure = { s.copy(notice = it.message ?: "No se pudo adjuntar el archivo.", pendingAttachments = pending) },
                    )
                }
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
        return Attachment(
            id = ChatStore.newId(),
            kind = Attachment.Kind.IMAGE,
            name = displayName(uri),
            mediaType = "image/jpeg",
            data = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP),
            thumbnail = thumbnailOf(bmp),
        )
    }

    private fun thumbnailOf(bmp: Bitmap): ImageBitmap {
        val s = 192f / maxOf(bmp.width, bmp.height)
        val t = if (s < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * s).toInt().coerceAtLeast(1), (bmp.height * s).toInt().coerceAtLeast(1), true) else bmp
        return t.asImageBitmap()
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

    /** Rebuilds a composer attachment from a block of an already-sent message. */
    private fun attachmentFromBlock(b: JsonObject): Attachment = when (History.type(b)) {
        "image" -> {
            val src = b["source"] as? JsonObject ?: error("Imagen sin datos")
            val data = History.str(src, "data") ?: error("Imagen sin datos")
            val bytes = Base64.decode(data, Base64.DEFAULT)
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = 4 })
            Attachment(ChatStore.newId(), Attachment.Kind.IMAGE, "imagen", History.str(src, "media_type") ?: "image/jpeg",
                data = data, thumbnail = bmp?.let { thumbnailOf(it) })
        }
        "document" -> {
            val src = b["source"] as? JsonObject ?: error("Documento sin datos")
            Attachment(ChatStore.newId(), Attachment.Kind.PDF, History.str(b, "title") ?: "documento.pdf", "application/pdf",
                data = History.str(src, "data"))
        }
        else -> {
            val raw = History.str(b, "text") ?: ""
            val name = raw.removePrefix(History.ATTACHMENT_PREFIX).substringBefore("\">")
            val body = raw.substringAfter("\">\n", "").removeSuffix("\n</archivo>")
            Attachment(ChatStore.newId(), Attachment.Kind.TEXT, name, "text/plain", text = body)
        }
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
