package io.github.eelnicoo.pocketcode.chat

import io.github.eelnicoo.pocketcode.api.History
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** One visual row of the conversation. */
sealed interface ChatRow { val key: String }

data class ImageRef(val mediaType: String, val data: String)

/** Something the person sent. [index] is its position in the API history. */
data class UserRow(
    override val key: String,
    val index: Int,
    val text: String,
    val images: List<ImageRef>,
    val files: List<String>,
) : ChatRow

/**
 * Everything Claude produced between two human turns: text, thinking and tool steps,
 * shown as a single answer the way the Claude app does.
 */
data class AssistantRow(
    override val key: String,
    val parts: List<Part>,
    val streaming: Boolean,
    val isLast: Boolean,
    val copyText: String,
) : ChatRow

sealed interface Part { val key: String }

data class TextPart(override val key: String, val text: String) : Part

data class ThinkingPart(override val key: String, val text: String, val streaming: Boolean) : Part

enum class ToolState { WRITING, RUNNING, DONE, ERROR, INTERRUPTED }

data class ToolPart(
    override val key: String,
    val id: String,
    val name: String,
    val input: JsonObject,
    /** Raw input JSON while it is still being streamed. */
    val partialInput: String?,
    val output: String?,
    val state: ToolState,
    /** True for Anthropic-hosted tools (web search / fetch). */
    val server: Boolean,
) : Part

object ChatRows {

    private data class Result(val text: String, val isError: Boolean)

    fun build(
        messages: List<JsonObject>,
        draft: List<JsonObject>?,
        running: Set<String>,
        busy: Boolean,
    ): List<ChatRow> {
        val results = HashMap<String, Result>()
        val serverResults = HashMap<String, JsonObject>()
        for (m in messages) for (b in History.blocks(m)) {
            val t = History.type(b)
            if (t == "tool_result") {
                val id = History.str(b, "tool_use_id") ?: continue
                results[id] = Result(History.toolResultText(b), (b["is_error"] as? JsonPrimitive)?.contentOrNull == "true")
            } else if (t.endsWith("_tool_result")) {
                History.str(b, "tool_use_id")?.let { serverResults[it] = b }
            }
        }
        draft?.forEach { b ->
            if (History.type(b).endsWith("_tool_result")) History.str(b, "tool_use_id")?.let { serverResults[it] = b }
        }

        val rows = ArrayList<ChatRow>()
        var parts: MutableList<Part>? = null
        var turnKey = "r-start"

        fun closeTurn() {
            val p = parts ?: return
            rows.add(AssistantRow(turnKey, p.toList(), streaming = false, isLast = false, copyText = ""))
            parts = null
        }

        messages.forEachIndexed { i, m ->
            when {
                History.isHumanTurn(m) -> {
                    closeTurn()
                    rows.add(userRow(i, m))
                    turnKey = "r$i"
                }
                History.role(m) == "assistant" -> {
                    val target = parts ?: mutableListOf<Part>().also { parts = it }
                    History.blocks(m).forEachIndexed { j, b ->
                        partFor("a$i-$j", b, results, serverResults, running, busy, draftBlock = false, lastDraft = false)
                            ?.let { target.add(it) }
                    }
                }
            }
        }
        if (draft != null) {
            val target = parts ?: mutableListOf<Part>().also { parts = it }
            draft.forEachIndexed { j, b ->
                partFor("d$j", b, results, serverResults, running, busy, draftBlock = true, lastDraft = j == draft.lastIndex)
                    ?.let { target.add(it) }
            }
        }
        val endsWithHuman = messages.lastOrNull()?.let { History.isHumanTurn(it) } == true
        if (busy && parts == null && endsWithHuman) parts = mutableListOf()
        closeTurn()

        // Mark the final answer as last (and streaming while the agent runs).
        val lastIdx = rows.indexOfLast { it is AssistantRow }
        if (lastIdx >= 0 && lastIdx == rows.lastIndex) {
            val r = rows[lastIdx] as AssistantRow
            rows[lastIdx] = r.copy(streaming = busy, isLast = true)
        }
        return rows.map { row ->
            if (row is AssistantRow) row.copy(copyText = row.parts.filterIsInstance<TextPart>().joinToString("\n\n") { it.text }.trim())
            else row
        }
    }

    private fun userRow(i: Int, m: JsonObject): UserRow {
        val texts = ArrayList<String>()
        val images = ArrayList<ImageRef>()
        val files = ArrayList<String>()
        for (b in History.blocks(m)) {
            when (History.type(b)) {
                "text" -> {
                    val t = History.str(b, "text") ?: ""
                    if (t.startsWith(History.ATTACHMENT_PREFIX)) {
                        files.add(t.removePrefix(History.ATTACHMENT_PREFIX).substringBefore('"'))
                    } else texts.add(t)
                }
                "image" -> {
                    val src = b["source"] as? JsonObject
                    val data = src?.let { History.str(it, "data") }
                    if (data != null) images.add(ImageRef(History.str(src, "media_type") ?: "image/jpeg", data))
                }
                "document" -> files.add(History.str(b, "title") ?: "documento.pdf")
            }
        }
        return UserRow("u$i", i, texts.joinToString("\n\n"), images, files)
    }

    private fun partFor(
        key: String,
        b: JsonObject,
        results: Map<String, Result>,
        serverResults: Map<String, JsonObject>,
        running: Set<String>,
        busy: Boolean,
        draftBlock: Boolean,
        lastDraft: Boolean,
    ): Part? = when (History.type(b)) {
        "text" -> {
            val t = History.str(b, "text") ?: ""
            if (t.isEmpty() && !draftBlock) null else TextPart(key, t)
        }
        "thinking" -> {
            val t = History.str(b, "thinking") ?: ""
            val streaming = draftBlock && lastDraft
            if (t.isBlank() && !streaming) null else ThinkingPart(key, t, streaming)
        }
        "tool_use" -> {
            val id = History.str(b, "id") ?: ""
            val partial = History.str(b, "_partial_input")
            val res = results[id]
            val state = when {
                partial != null -> ToolState.WRITING
                res != null -> if (res.isError) ToolState.ERROR else ToolState.DONE
                id in running -> ToolState.RUNNING
                busy -> ToolState.RUNNING
                else -> ToolState.INTERRUPTED
            }
            ToolPart(key, id, History.str(b, "name") ?: "?", b["input"] as? JsonObject ?: JsonObject(emptyMap()),
                partial, res?.text, state, server = false)
        }
        "server_tool_use" -> {
            val id = History.str(b, "id") ?: ""
            val partial = History.str(b, "_partial_input")
            val res = serverResults[id]
            val state = when {
                partial != null -> ToolState.WRITING
                res != null -> if (serverError(res)) ToolState.ERROR else ToolState.DONE
                busy -> ToolState.RUNNING
                else -> ToolState.DONE
            }
            ToolPart(key, id, History.str(b, "name") ?: "servidor", b["input"] as? JsonObject ?: JsonObject(emptyMap()),
                partial, res?.let { serverSummary(it) }, state, server = true)
        }
        else -> null
    }

    private fun serverError(res: JsonObject): Boolean {
        val c = res["content"] as? JsonObject ?: return false
        return History.type(c).endsWith("_error")
    }

    /** A readable summary of a server tool result (e.g. the pages a search found). */
    private fun serverSummary(res: JsonObject): String = when (val c = res["content"]) {
        is JsonArray -> c.mapNotNull { it as? JsonObject }.joinToString("\n") { r ->
            val title = History.str(r, "title") ?: ""
            val url = History.str(r, "url") ?: ""
            if (title.isNotEmpty()) "• $title — $url" else "• $url"
        }.ifEmpty { "(sin resultados)" }
        is JsonObject -> History.str(c, "error_code")?.let { "Error: $it" }
            ?: History.str(c, "url")
            ?: History.type(c)
        else -> ""
    }
}
