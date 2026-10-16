package io.github.eelnicoo.pocketcode.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.File

/** Lightweight description of a saved conversation, shown in the history drawer. */
@Serializable
data class ChatMeta(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val starred: Boolean = false,
    val model: String? = null,
    /** True once the title came from the model or the user (stop auto-titling). */
    val titled: Boolean = false,
)

/** A full conversation: metadata plus the raw Messages API history. */
@Serializable
data class ChatFile(
    val meta: ChatMeta,
    val messages: List<JsonObject>,
)

/**
 * Persists conversations as one JSON file per chat plus an `index.json` with every
 * chat's metadata, so the drawer can list history without parsing whole transcripts.
 * Writes go to a temp file first and are then renamed, so a crash never leaves a
 * half-written chat behind. All public methods are thread-safe.
 */
class ChatStore(private val dir: File) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    private val lock = Any()
    private var index: MutableMap<String, ChatMeta>? = null

    init { dir.mkdirs() }

    private fun chatFile(id: String) = File(dir, "$id.json")
    private val indexFile get() = File(dir, "index.json")

    private fun loadIndexLocked(): MutableMap<String, ChatMeta> {
        index?.let { return it }
        val map = LinkedHashMap<String, ChatMeta>()
        val fromIndex = runCatching {
            json.decodeFromString(ListSerializer(ChatMeta.serializer()), indexFile.readText())
        }.getOrNull()
        if (fromIndex != null) {
            fromIndex.filter { chatFile(it.id).exists() }.forEach { map[it.id] = it }
        } else {
            // Index missing or corrupt: rebuild it from the chat files themselves.
            dir.listFiles { f -> f.isFile && f.name.endsWith(".json") && f.name != "index.json" }
                ?.forEach { f ->
                    runCatching { json.decodeFromString(ChatFile.serializer(), f.readText()).meta }
                        .getOrNull()?.let { map[it.id] = it }
                }
            writeIndexLocked(map)
        }
        index = map
        return map
    }

    private fun writeIndexLocked(map: Map<String, ChatMeta>) {
        atomicWrite(indexFile, json.encodeToString(ListSerializer(ChatMeta.serializer()), map.values.toList()))
    }

    private fun atomicWrite(target: File, text: String) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(target)) {
            target.delete()
            if (!tmp.renameTo(target)) {
                target.writeText(text)
                tmp.delete()
            }
        }
    }

    /** All chats, most recently updated first. */
    fun list(): List<ChatMeta> = synchronized(lock) {
        loadIndexLocked().values.sortedByDescending { it.updatedAt }
    }

    fun load(id: String): ChatFile? = synchronized(lock) {
        val f = chatFile(id)
        if (!f.exists()) return null
        runCatching { json.decodeFromString(ChatFile.serializer(), f.readText()) }.getOrNull()
    }

    fun save(chat: ChatFile) = synchronized(lock) {
        atomicWrite(chatFile(chat.meta.id), json.encodeToString(ChatFile.serializer(), chat))
        val map = loadIndexLocked()
        map[chat.meta.id] = chat.meta
        writeIndexLocked(map)
    }

    /** Updates only the metadata (title, star) of an existing chat. */
    fun updateMeta(meta: ChatMeta) = synchronized(lock) {
        val existing = load(meta.id) ?: return@synchronized
        save(existing.copy(meta = meta))
    }

    fun delete(id: String) = synchronized(lock) {
        chatFile(id).delete()
        val map = loadIndexLocked()
        map.remove(id)
        writeIndexLocked(map)
    }

    companion object {
        fun newId(): String = java.util.UUID.randomUUID().toString()
    }
}
