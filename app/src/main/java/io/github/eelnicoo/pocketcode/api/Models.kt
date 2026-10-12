package io.github.eelnicoo.pocketcode.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Minimal, hand-written DTOs for the Anthropic Messages API. We keep them small and
 * tolerant: the wire format carries many more fields than we model, so the JSON parser
 * is configured with ignoreUnknownKeys = true.
 */

@Serializable
data class MessagesRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val system: List<SystemBlock>? = null,
    val messages: List<ApiMessage>,
    val tools: List<ToolDef>? = null,
    val stream: Boolean = true,
    val thinking: Thinking? = null,
)

@Serializable
data class Thinking(val type: String = "adaptive")

@Serializable
data class SystemBlock(
    val type: String = "text",
    val text: String,
)

@Serializable
data class ToolDef(
    val name: String,
    val description: String,
    @SerialName("input_schema") val inputSchema: JsonObject,
)

@Serializable
data class ApiMessage(
    val role: String,
    val content: List<ContentBlock>,
)

/**
 * Content block. A single permissive shape covers every variant we send or receive
 * (text, tool_use, tool_result, thinking). Null fields are omitted on the wire.
 */
@Serializable
data class ContentBlock(
    val type: String,
    val text: String? = null,
    // tool_use
    val id: String? = null,
    val name: String? = null,
    val input: JsonElement? = null,
    // tool_result
    @SerialName("tool_use_id") val toolUseId: String? = null,
    val content: JsonElement? = null,
    @SerialName("is_error") val isError: Boolean? = null,
    // thinking
    val thinking: String? = null,
    val signature: String? = null,
)

// ---- Streaming (SSE) event envelopes ----

@Serializable
data class StreamEvent(
    val type: String,
    val index: Int? = null,
    @SerialName("content_block") val contentBlock: ContentBlock? = null,
    val delta: StreamDelta? = null,
    val message: StreamMessage? = null,
    val error: StreamError? = null,
)

@Serializable
data class StreamDelta(
    val type: String? = null,
    val text: String? = null,
    @SerialName("partial_json") val partialJson: String? = null,
    val thinking: String? = null,
    @SerialName("stop_reason") val stopReason: String? = null,
)

@Serializable
data class StreamMessage(
    val role: String? = null,
    @SerialName("stop_reason") val stopReason: String? = null,
)

@Serializable
data class StreamError(val type: String? = null, val message: String? = null)
