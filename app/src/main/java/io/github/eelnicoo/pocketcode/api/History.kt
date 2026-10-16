package io.github.eelnicoo.pocketcode.api

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Pure helpers over the raw Messages API history (a list of `{role, content}` objects).
 * Every change here is append-only or a truncation, so prior thinking blocks keep the
 * exact prefix they were produced with.
 */
object History {

    fun role(m: JsonObject): String = m["role"]?.jsonPrimitive?.contentOrNull ?: ""

    /** Content blocks of a message; a plain-string content becomes one text block. */
    fun blocks(m: JsonObject): List<JsonObject> = when (val c = m["content"]) {
        is JsonArray -> c.mapNotNull { it as? JsonObject }
        is JsonPrimitive -> listOf(textBlock(c.contentOrNull ?: ""))
        else -> emptyList()
    }

    fun type(b: JsonObject): String = b["type"]?.jsonPrimitive?.contentOrNull ?: ""
    fun str(b: JsonObject, key: String): String? = (b[key] as? JsonPrimitive)?.contentOrNull

    fun textBlock(text: String): JsonObject = buildJsonObject {
        put("type", "text")
        put("text", text)
    }

    fun message(role: String, content: List<JsonObject>): JsonObject = buildJsonObject {
        put("role", role)
        put("content", JsonArray(content))
    }

    /** True for a user message that the person typed (not just tool results). */
    fun isHumanTurn(m: JsonObject): Boolean =
        role(m) == "user" && blocks(m).any { type(it) != "tool_result" }

    /** Plain text of a tool_result's content (string or list of text blocks). */
    fun toolResultText(b: JsonObject): String = when (val c = b["content"]) {
        is JsonPrimitive -> c.contentOrNull ?: ""
        is JsonArray -> c.mapNotNull { it as? JsonObject }
            .filter { type(it) == "text" }
            .joinToString("\n") { str(it, "text") ?: "" }
        else -> ""
    }

    fun toolResult(id: String, text: String, isError: Boolean): JsonObject = buildJsonObject {
        put("type", "tool_result")
        put("tool_use_id", id)
        put("content", buildJsonArray { add(textBlock(text)) })
        if (isError) put("is_error", true)
    }

    /**
     * Appends a human turn. If the history already ends with a human turn (for example
     * after a refusal discarded the reply) the new content is merged into it so roles
     * keep alternating.
     */
    fun appendUser(history: List<JsonObject>, content: List<JsonObject>): List<JsonObject> {
        val last = history.lastOrNull()
        return if (last != null && isHumanTurn(last)) {
            history.dropLast(1) + message("user", blocks(last) + content)
        } else {
            history + message("user", content)
        }
    }

    /**
     * Makes the history valid to send: every tool_use in a trailing assistant message
     * must be answered by a tool_result. Missing ones get an "interrupted" error result.
     */
    fun repair(history: List<JsonObject>, reason: String): List<JsonObject> {
        val last = history.lastOrNull() ?: return history
        if (role(last) != "assistant") return history
        val ids = blocks(last).filter { type(it) == "tool_use" }.mapNotNull { str(it, "id") }
        if (ids.isEmpty()) return history
        return history + message("user", ids.map { toolResult(it, reason, isError = true) })
    }

    /** Index of the last human turn, or -1. */
    fun lastHumanIndex(history: List<JsonObject>): Int = history.indexOfLast { isHumanTurn(it) }

    /** History truncated to end at the last human turn (used by "retry"). */
    fun truncateForRetry(history: List<JsonObject>): List<JsonObject> {
        val i = lastHumanIndex(history)
        return if (i < 0) history else history.subList(0, i + 1)
    }

    /**
     * Applies the server-side fallback echo rule to a finished assistant message: blocks
     * of a declined partial attempt (thinking, redacted_thinking, tool_use, unpaired
     * server_tool_use, unknown internal types) that come before the last `fallback`
     * block are dropped; text and paired server-tool blocks are kept.
     */
    fun sanitizeAssistant(content: List<JsonObject>): List<JsonObject> {
        val lastFallback = content.indexOfLast { type(it) == "fallback" }
        if (lastFallback < 0) return content
        val resultIds = content.filter { type(it).endsWith("_tool_result") }
            .mapNotNull { str(it, "tool_use_id") }.toSet()
        return content.filterIndexed { i, b ->
            if (i > lastFallback) return@filterIndexed true
            when (type(b)) {
                "text" -> true
                "server_tool_use" -> str(b, "id") in resultIds
                "fallback" -> false
                else -> type(b).endsWith("_tool_result")
            }
        }
    }

    /** First text the person typed in this conversation (for titles). */
    fun firstUserText(history: List<JsonObject>): String =
        history.firstOrNull { isHumanTurn(it) }?.let { m ->
            blocks(m).filter { type(it) == "text" }
                .map { str(it, "text") ?: "" }
                .firstOrNull { !it.startsWith(ATTACHMENT_PREFIX) }
        } ?: ""

    /** All visible assistant text of a message list, joined (for copy/share). */
    fun assistantText(messages: List<JsonObject>): String =
        messages.filter { role(it) == "assistant" }
            .flatMap { blocks(it) }
            .filter { type(it) == "text" }
            .joinToString("\n\n") { str(it, "text") ?: "" }
            .trim()

    /** A short fallback title built from the first message. */
    fun fallbackTitle(firstText: String): String {
        val clean = firstText.replace(Regex("\\s+"), " ").trim()
        if (clean.isEmpty()) return "Nuevo chat"
        return if (clean.length <= 42) clean else clean.take(40).trimEnd() + "…"
    }

    /** Text blocks the app adds for attached files start with this marker. */
    const val ATTACHMENT_PREFIX = "<archivo nombre=\""

    fun elementText(e: JsonElement?): String? = (e as? JsonPrimitive)?.contentOrNull

    fun arrayOrEmpty(e: JsonElement?): List<JsonElement> = (e as? JsonArray)?.jsonArray ?: emptyList()
}
