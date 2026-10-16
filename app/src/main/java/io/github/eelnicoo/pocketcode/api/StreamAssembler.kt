package io.github.eelnicoo.pocketcode.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/** An error reported by the API, either as an HTTP status or an SSE `error` event. */
class ApiException(
    val status: Int?,
    val errorType: String?,
    message: String,
    /** The server's own error message (without our friendly prefix). */
    val rawMessage: String = message,
) : Exception(message) {
    val retryable: Boolean
        get() = status in RETRYABLE_STATUS || errorType == "overloaded_error" || errorType == "api_error"

    companion object {
        val RETRYABLE_STATUS = setOf(408, 409, 429, 500, 502, 503, 504, 529)
    }
}

/**
 * Rebuilds the assistant message from Messages API SSE events. Each content block is
 * kept exactly as the server sent it (unknown block types and fields included), with
 * deltas applied: text, thinking, signature, tool input JSON and citations. Keeping the
 * raw blocks is what lets thinking blocks be echoed back unchanged on the next request.
 */
class StreamAssembler {
    private val json = Json { ignoreUnknownKeys = true }

    private val blocks = sortedMapOf<Int, MutableMap<String, JsonElement>>()
    private val textBuf = HashMap<Int, StringBuilder>()
    private val thinkingBuf = HashMap<Int, StringBuilder>()
    private val inputBuf = HashMap<Int, StringBuilder>()
    private val citations = HashMap<Int, MutableList<JsonElement>>()

    var stopReason: String? = null
        private set
    var model: String? = null
        private set
    var done: Boolean = false
        private set
    /** True once any content block has started (retries are only safe before that). */
    var sawContent: Boolean = false
        private set
    /** Changes whenever the visible content changes; used to throttle UI updates. */
    var revision: Int = 0
        private set

    /** tool_use ids whose streamed input was not valid JSON, mapped to the raw text. */
    val invalidToolInputs = HashMap<String, String>()

    /** Feeds one SSE `data:` payload. Throws [ApiException] for `error` events. */
    fun handle(data: String) {
        val ev = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull() ?: return
        when (ev.str("type")) {
            "message_start" -> model = (ev["message"] as? JsonObject)?.str("model")
            "content_block_start" -> {
                val idx = ev.int("index") ?: return
                val cb = ev["content_block"] as? JsonObject ?: return
                blocks[idx] = cb.toMutableMap()
                when (cb.str("type")) {
                    "text" -> textBuf[idx] = StringBuilder(cb.str("text") ?: "")
                    "thinking" -> thinkingBuf[idx] = StringBuilder(cb.str("thinking") ?: "")
                }
                if (cb.containsKey("input") && cb.str("type")?.endsWith("tool_use") == true) {
                    inputBuf[idx] = StringBuilder()
                }
                sawContent = true
                revision++
            }
            "content_block_delta" -> {
                val idx = ev.int("index") ?: return
                val d = ev["delta"] as? JsonObject ?: return
                when (d.str("type")) {
                    "text_delta" -> textBuf.getOrPut(idx) { StringBuilder() }.append(d.str("text") ?: "")
                    "thinking_delta" -> thinkingBuf.getOrPut(idx) { StringBuilder() }.append(d.str("thinking") ?: "")
                    "signature_delta" -> blocks[idx]?.set("signature", JsonPrimitive(d.str("signature") ?: ""))
                    "input_json_delta" -> inputBuf.getOrPut(idx) { StringBuilder() }.append(d.str("partial_json") ?: "")
                    "citations_delta" -> d["citation"]?.let { citations.getOrPut(idx) { mutableListOf() }.add(it) }
                }
                revision++
            }
            "content_block_stop" -> {
                val idx = ev.int("index") ?: return
                finalizeInput(idx)
                revision++
            }
            "message_delta" -> {
                (ev["delta"] as? JsonObject)?.str("stop_reason")?.let { stopReason = it }
            }
            "message_stop" -> done = true
            "error" -> {
                val err = ev["error"] as? JsonObject
                throw ApiException(null, err?.str("type"), err?.str("message") ?: "Error del servidor")
            }
        }
    }

    private fun finalizeInput(idx: Int) {
        val buf = inputBuf.remove(idx) ?: return
        val block = blocks[idx] ?: return
        val raw = buf.toString().trim()
        if (raw.isEmpty()) {
            block["input"] = JsonObject(emptyMap())
            return
        }
        val parsed = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull()
        if (parsed != null) {
            block["input"] = parsed
        } else {
            block["input"] = JsonObject(emptyMap())
            (block["id"] as? JsonPrimitive)?.contentOrNull?.let { invalidToolInputs[it] = raw }
        }
    }

    /** Current content blocks, in order, with all deltas applied. */
    fun snapshot(): List<JsonObject> = blocks.map { (idx, fields) ->
        val m = LinkedHashMap(fields)
        textBuf[idx]?.let { m["text"] = JsonPrimitive(it.toString()) }
        thinkingBuf[idx]?.let { m["thinking"] = JsonPrimitive(it.toString()) }
        citations[idx]?.let { list ->
            val existing = (m["citations"] as? JsonArray)?.toList() ?: emptyList()
            m["citations"] = JsonArray(existing + list)
        }
        // While a tool's input is still streaming, expose what has arrived so far.
        inputBuf[idx]?.let { m["_partial_input"] = JsonPrimitive(it.toString()) }
        JsonObject(m)
    }

    /**
     * The finished content: drops streaming-only keys, thinking blocks that never got a
     * signature (incomplete) and empty text blocks.
     */
    fun finalContent(): List<JsonObject> = snapshot().mapNotNull { b ->
        val type = b.str("type")
        when {
            b.containsKey("_partial_input") -> null
            type == "thinking" && b.str("signature").isNullOrEmpty() -> null
            type == "text" && b.str("text").isNullOrEmpty() && !b.containsKey("citations") -> null
            else -> b
        }
    }

    /** Text-only content, for keeping a partial answer when the user presses stop. */
    fun partialTextContent(): List<JsonObject> = snapshot()
        .filter { it.str("type") == "text" && !it.str("text").isNullOrBlank() }
        .map { History.textBlock(it.str("text") ?: "") }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
}
