package io.github.eelnicoo.pocketcode.api

import io.github.eelnicoo.pocketcode.oauth.ClaudeOAuth
import io.github.eelnicoo.pocketcode.tools.ToolRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit

/** High-level UI events emitted while the agent runs. */
sealed interface AgentEvent {
    data class AssistantTextDelta(val text: String) : AgentEvent
    data class ThinkingDelta(val text: String) : AgentEvent
    data class ToolStarted(val id: String, val name: String, val input: String) : AgentEvent
    data class ToolFinished(val id: String, val output: String, val isError: Boolean) : AgentEvent
    data class Error(val message: String) : AgentEvent
    data object TurnComplete : AgentEvent
}

class ClaudeClient(
    private val oauth: ClaudeOAuth,
    private val registry: ToolRegistry,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.MINUTES)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Runs a full agentic turn: streams the assistant response, executes any tool calls,
     * and loops until the model stops requesting tools. [history] is mutated in place so
     * the caller keeps the running conversation.
     */
    fun runTurn(
        model: String,
        maxTokens: Int,
        history: MutableList<ApiMessage>,
    ): Flow<AgentEvent> = callbackFlow {
        try {
            val token = oauth.validAccessToken()
            if (token == null) {
                trySend(AgentEvent.Error("No has iniciado sesión con tu cuenta Claude."))
                close(); return@callbackFlow
            }

            var keepGoing = true
            var safety = 0
            while (keepGoing && safety < MAX_ITERATIONS) {
                safety++
                val assembled = streamOnce(token, model, maxTokens, history) { trySend(it) }
                history.add(ApiMessage("assistant", assembled.blocks))

                val toolUses = assembled.blocks.filter { it.type == "tool_use" }
                if (toolUses.isEmpty() || assembled.stopReason != "tool_use") {
                    keepGoing = false
                } else {
                    val results = ArrayList<ContentBlock>()
                    for (tu in toolUses) {
                        val name = tu.name ?: continue
                        val id = tu.id ?: continue
                        val inputObj = (tu.input as? JsonObject) ?: buildJsonObject {}
                        trySend(AgentEvent.ToolStarted(id, name, inputObj.toString()))
                        val res = registry.execute(name, inputObj)
                        trySend(AgentEvent.ToolFinished(id, res.text, res.isError))
                        val resultContent = kotlinx.serialization.json.buildJsonArray {
                            add(buildJsonObject {
                                put("type", "text")
                                put("text", res.text)
                            })
                        }
                        results.add(
                            ContentBlock(
                                type = "tool_result",
                                toolUseId = id,
                                content = resultContent,
                                isError = if (res.isError) true else null,
                            )
                        )
                    }
                    history.add(ApiMessage("user", results))
                }
            }
            trySend(AgentEvent.TurnComplete)
            close()
        } catch (t: Throwable) {
            trySend(AgentEvent.Error(t.message ?: t.toString()))
            close()
        }
        awaitClose { }
    }.flowOn(Dispatchers.IO)

    private class Assembled {
        val blocks = ArrayList<ContentBlock>()
        var stopReason: String? = null
    }

    /** Streams exactly one assistant response, returning its assembled content blocks. */
    private suspend fun streamOnce(
        token: String,
        model: String,
        maxTokens: Int,
        history: List<ApiMessage>,
        emit: (AgentEvent) -> Unit,
    ): Assembled = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        val req = MessagesRequest(
            model = model,
            maxTokens = maxTokens,
            system = SYSTEM_BLOCKS,
            messages = history,
            tools = registry.defs(),
            stream = true,
            thinking = null,
        )
        val payload = json.encodeToString(MessagesRequest.serializer(), req)
        val httpReq = Request.Builder()
            .url("$API_BASE/v1/messages")
            .header("Authorization", "Bearer $token")
            .header("anthropic-version", "2023-06-01")
            .header("anthropic-beta", OAUTH_BETA)
            .header("content-type", "application/json")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()

        val assembled = Assembled()
        // Accumulators keyed by streaming block index.
        val textBuf = HashMap<Int, StringBuilder>()
        val jsonBuf = HashMap<Int, StringBuilder>()
        val blockType = HashMap<Int, String>()
        val blockMeta = HashMap<Int, ContentBlock>()

        val listener = object : EventSourceListener() {
            override fun onEvent(es: EventSource, id: String?, type: String?, data: String) {
                try {
                    val ev = json.decodeFromString(StreamEvent.serializer(), data)
                    when (ev.type) {
                        "content_block_start" -> {
                            val idx = ev.index ?: return
                            val cb = ev.contentBlock ?: return
                            blockType[idx] = cb.type
                            blockMeta[idx] = cb
                            if (cb.type == "text") textBuf[idx] = StringBuilder()
                            if (cb.type == "tool_use") jsonBuf[idx] = StringBuilder()
                            if (cb.type == "thinking") textBuf[idx] = StringBuilder()
                        }
                        "content_block_delta" -> {
                            val idx = ev.index ?: return
                            val d = ev.delta ?: return
                            when (d.type) {
                                "text_delta" -> d.text?.let {
                                    textBuf.getOrPut(idx) { StringBuilder() }.append(it)
                                    emit(AgentEvent.AssistantTextDelta(it))
                                }
                                "thinking_delta" -> d.thinking?.let {
                                    textBuf.getOrPut(idx) { StringBuilder() }.append(it)
                                    emit(AgentEvent.ThinkingDelta(it))
                                }
                                "input_json_delta" -> d.partialJson?.let {
                                    jsonBuf.getOrPut(idx) { StringBuilder() }.append(it)
                                }
                            }
                        }
                        "content_block_stop" -> {
                            val idx = ev.index ?: return
                            finalizeBlock(idx, assembled, blockType, blockMeta, textBuf, jsonBuf)
                        }
                        "message_delta" -> {
                            ev.delta?.stopReason?.let { assembled.stopReason = it }
                        }
                        "error" -> {
                            val msg = ev.error?.message ?: "error de stream"
                            if (cont.isActive) cont.resumeWith(Result.failure(RuntimeException(msg)))
                        }
                        "message_stop" -> {
                            if (cont.isActive) cont.resumeWith(Result.success(assembled))
                            es.cancel()
                        }
                    }
                } catch (_: Throwable) {
                    // Ignore malformed keep-alive / unknown events.
                }
            }

            override fun onClosed(es: EventSource) {
                if (cont.isActive) cont.resumeWith(Result.success(assembled))
            }

            override fun onFailure(es: EventSource, t: Throwable?, response: okhttp3.Response?) {
                if (!cont.isActive) return
                val detail = response?.body?.string()?.take(500)
                val code = response?.code
                val msg = buildString {
                    append(t?.message ?: "fallo de red")
                    if (code != null) append(" (HTTP $code)")
                    if (!detail.isNullOrBlank()) append(": $detail")
                }
                cont.resumeWith(Result.failure(RuntimeException(msg)))
            }
        }

        val es = EventSources.createFactory(http).newEventSource(httpReq, listener)
        cont.invokeOnCancellation { es.cancel() }
    }

    private fun finalizeBlock(
        idx: Int,
        assembled: Assembled,
        blockType: Map<Int, String>,
        blockMeta: Map<Int, ContentBlock>,
        textBuf: Map<Int, StringBuilder>,
        jsonBuf: Map<Int, StringBuilder>,
    ) {
        when (blockType[idx]) {
            "text" -> assembled.blocks.add(
                ContentBlock(type = "text", text = textBuf[idx]?.toString() ?: "")
            )
            "thinking" -> {
                val meta = blockMeta[idx]
                assembled.blocks.add(
                    ContentBlock(
                        type = "thinking",
                        thinking = textBuf[idx]?.toString() ?: "",
                        signature = meta?.signature,
                    )
                )
            }
            "tool_use" -> {
                val meta = blockMeta[idx] ?: return
                val raw = jsonBuf[idx]?.toString().orEmpty().ifBlank { "{}" }
                val parsed = runCatching { json.parseToJsonElement(raw).jsonObject }
                    .getOrElse { buildJsonObject { } }
                assembled.blocks.add(
                    ContentBlock(
                        type = "tool_use",
                        id = meta.id,
                        name = meta.name,
                        input = parsed,
                    )
                )
            }
        }
    }

    companion object {
        const val API_BASE = "https://api.anthropic.com"
        const val OAUTH_BETA = "oauth-2025-04-20"
        const val MAX_ITERATIONS = 50

        // Claude subscription OAuth requires the Claude Code identity as the first system block.
        val SYSTEM_BLOCKS = listOf(
            SystemBlock(text = "You are Claude Code, Anthropic's official CLI for Claude."),
            SystemBlock(text = SYSTEM_PROMPT),
        )
    }
}

private const val SYSTEM_PROMPT = """You are running inside PocketCode, a mobile Claude Code client on the user's Android phone (GrapheneOS). You help the user with coding and device tasks.

You have tools to read, write and edit files, list and search directories, run shell commands, and build Android APKs on-device. Prefer tools over guessing. Keep answers concise and in the user's language. When a task touches files or the system, act with the tools rather than only describing steps."""
