package io.github.eelnicoo.pocketcode.api

import io.github.eelnicoo.pocketcode.data.Settings
import io.github.eelnicoo.pocketcode.oauth.ClaudeOAuth
import io.github.eelnicoo.pocketcode.tools.ToolRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Messages API client (raw HTTP + SSE) that runs the agent loop: stream a reply,
 * execute requested tools, send results back, repeat until the model is done.
 */
class ClaudeClient(
    private val oauth: ClaudeOAuth,
    private val registry: ToolRegistry,
) {
    data class Config(
        val model: String,
        val maxTokens: Int,
        /** output_config.effort, or null to leave the model default. */
        val effort: String?,
        /** Extra system context (device, workspace, date). */
        val context: String,
    )

    /** Receives progress while a turn runs. Called from a background thread. */
    interface Listener {
        /** The in-progress assistant content, or null when nothing is streaming. */
        fun onDraft(blocks: List<JsonObject>?)
        /** A message was appended to the history. */
        fun onAppend(message: JsonObject)
        fun onToolRunning(id: String, running: Boolean)
        /** A non-fatal note to show the user. */
        fun onNotice(text: String)
        /** Claude declined the last human turn (stop_reason "refusal"). */
        fun onRefusal() {}
        /** Earlier thinking blocks were rejected and removed from the history being sent. */
        fun onThinkingStripped() {}
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.MINUTES)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    // Optional request features. If the server rejects one for a model (HTTP 400 naming
    // that parameter), it is switched off for that model for the rest of the session and
    // the request is retried without it. Several chats may run at once, hence the sets.
    private val noFallbacks = ConcurrentHashMap.newKeySet<String>()
    private val noCache = ConcurrentHashMap.newKeySet<String>()
    private val noThinking = ConcurrentHashMap.newKeySet<String>()
    private val noEffort = ConcurrentHashMap.newKeySet<String>()
    private val noBlockBinding = ConcurrentHashMap.newKeySet<String>()

    /**
     * Runs one agentic turn on top of [history] (which must end with a user message).
     * Returns the updated history. Cancellation propagates as CancellationException;
     * every message already appended was reported through [Listener.onAppend].
     */
    suspend fun run(history: List<JsonObject>, cfg: Config, listener: Listener): List<JsonObject> {
        var h = history
        repeat(MAX_ITERATIONS) {
            val asm = streamWithRetry(h, cfg, listener) {
                // Keep the cleaned history for the rest of this run, not just one request.
                h = History.withoutThinking(h)
                listener.onThinkingStripped()
            }
            listener.onDraft(null)

            if (asm.stopReason == "refusal") {
                // Discard the partial reply; the user can rephrase or switch model.
                listener.onNotice("Claude no ha podido responder a esto. Edita tu mensaje o cambia de modelo.")
                listener.onRefusal()
                return h
            }

            val content = History.sanitizeAssistant(asm.finalContent())
            if (content.isEmpty()) {
                if (asm.stopReason == "max_tokens") listener.onNotice("La respuesta se cortó por longitud.")
                return h
            }
            val assistant = History.message("assistant", content)
            currentCoroutineContext().ensureActive()
            h = h + assistant
            listener.onAppend(assistant)

            val toolUses = content.filter { History.type(it) == "tool_use" }
            when {
                toolUses.isNotEmpty() && asm.stopReason == "max_tokens" -> {
                    val results = toolUses.mapNotNull { History.str(it, "id") }.map {
                        History.toolResult(it, "La llamada se cortó por límite de longitud; vuelve a intentarlo con menos contenido.", true)
                    }
                    val user = History.message("user", results)
                    h = h + user
                    listener.onAppend(user)
                }
                toolUses.isNotEmpty() -> {
                    val results = toolUses.map { executeTool(it, asm, listener) }
                    // A stopped run must not report results into whatever chat is now open.
                    currentCoroutineContext().ensureActive()
                    val user = History.message("user", results)
                    h = h + user
                    listener.onAppend(user)
                }
                asm.stopReason == "pause_turn" -> Unit // server tool paused: resend as-is
                asm.stopReason == "max_tokens" -> {
                    listener.onNotice("La respuesta se cortó por longitud.")
                    return h
                }
                else -> return h
            }
        }
        listener.onNotice("Se alcanzó el máximo de pasos seguidos; escribe para continuar.")
        return h
    }

    private suspend fun executeTool(tu: JsonObject, asm: StreamAssembler, listener: Listener): JsonObject {
        // After Stop, the remaining tools of the batch must not start.
        currentCoroutineContext().ensureActive()
        val id = History.str(tu, "id") ?: ""
        val name = History.str(tu, "name") ?: ""
        asm.invalidToolInputs[id]?.let { raw ->
            return History.toolResult(id, "La entrada de la herramienta no era JSON válido: ${raw.take(500)}", true)
        }
        val input = tu["input"] as? JsonObject ?: JsonObject(emptyMap())
        listener.onToolRunning(id, true)
        try {
            val res = registry.execute(name, input)
            val text = if (res.text.length > MAX_TOOL_OUTPUT) {
                res.text.take(MAX_TOOL_OUTPUT) + "\n…[salida truncada, ${res.text.length} caracteres en total]"
            } else res.text
            return History.toolResult(id, text.ifEmpty { "(sin salida)" }, res.isError)
        } finally {
            listener.onToolRunning(id, false)
        }
    }

    private suspend fun streamWithRetry(
        h: List<JsonObject>,
        cfg: Config,
        listener: Listener,
        onStripped: () -> Unit,
    ): StreamAssembler {
        var attempt = 0
        var refreshed = false
        var history = h
        var strippedThinking = false
        val model = cfg.model
        while (true) {
            val asm = StreamAssembler()
            try {
                streamOnce(history, cfg, asm, listener)
                return asm
            } catch (e: ApiException) {
                listener.onDraft(null)
                if (asm.sawContent) throw e
                val raw = e.rawMessage.lowercase()
                // Errors about the history ("messages.3.content.0: ...") never mean that a
                // request parameter is unsupported, so they must not switch features off.
                val aboutParams = !raw.startsWith("messages")
                when {
                    e.status == 401 && !refreshed -> {
                        refreshed = true
                        oauth.refresh().getOrElse { throw ApiException(401, null, "Sesión caducada. Vuelve a iniciar sesión en Ajustes.") }
                        continue
                    }
                    // A replayed thinking block was rejected (bound to another model or prefix):
                    // resend once without earlier thinking blocks. Checked before any feature
                    // switch so such an error never turns thinking off.
                    e.status == 400 && !strippedThinking &&
                        ("signature" in raw || "bound to" in raw || ("thinking" in raw && !aboutParams)) -> {
                        strippedThinking = true
                        history = History.withoutThinking(history)
                        onStripped()
                        continue
                    }
                    e.status == 400 && aboutParams && model !in noBlockBinding && ("block_binding" in raw || "thinking-binding" in raw) -> {
                        noBlockBinding.add(model); continue
                    }
                    e.status == 400 && aboutParams && model !in noFallbacks && ("fallbacks" in raw || "server-side-fallback" in raw) -> {
                        noFallbacks.add(model); continue
                    }
                    e.status == 400 && aboutParams && model !in noCache && "cache_control" in raw -> {
                        noCache.add(model); continue
                    }
                    e.status == 400 && aboutParams && model !in noEffort && ("output_config" in raw || "effort" in raw) -> {
                        noEffort.add(model); continue
                    }
                    e.status == 400 && aboutParams && model !in noThinking &&
                        ("thinking.display" in raw || "thinking.type" in raw || raw.startsWith("thinking")) -> {
                        noThinking.add(model); continue
                    }
                    e.retryable && attempt < MAX_RETRIES -> {
                        attempt++
                        delay(1500L shl (attempt - 1))
                        continue
                    }
                    else -> throw e
                }
            } catch (e: java.io.IOException) {
                listener.onDraft(null)
                if (asm.sawContent || attempt >= MAX_RETRIES) {
                    throw ApiException(null, null, "Error de red: ${e.message ?: "conexión interrumpida"}")
                }
                attempt++
                delay(1500L shl (attempt - 1))
            }
        }
    }

    private suspend fun streamOnce(h: List<JsonObject>, cfg: Config, asm: StreamAssembler, listener: Listener) {
        val token = oauth.validAccessToken()
            ?: throw ApiException(401, null, "No has iniciado sesión con tu cuenta Claude.")
        val fallbacks = cfg.model !in noFallbacks && Settings.modelInfo(cfg.model).supportsFallback
        val thinking = cfg.model !in noThinking
        val binding = thinking && cfg.model !in noBlockBinding
        val body = buildRequest(h, cfg, fallbacks, thinking, binding)
        val betas = buildList {
            add(OAUTH_BETA)
            if (fallbacks) add(FALLBACK_BETA)
            if (binding) add(BLOCK_BINDING_BETA)
        }.joinToString(",")
        val req = Request.Builder()
            .url("$API_BASE/v1/messages")
            .header("Authorization", "Bearer $token")
            .header("anthropic-version", "2023-06-01")
            .header("anthropic-beta", betas)
            .header("content-type", "application/json")
            .header("accept", "text/event-stream")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        // Bridge OkHttp's callback thread into this coroutine through a channel.
        val channel = Channel<String>(Channel.UNLIMITED)
        val sse = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                channel.trySend(data)
            }
            override fun onClosed(eventSource: EventSource) { channel.close() }
            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                channel.close(toException(t, response))
            }
        }
        val es = EventSources.createFactory(http).newEventSource(req, sse)
        try {
            var lastEmit = 0L
            var lastRev = -1
            for (data in channel) {
                // Buffered events must not keep painting after Stop.
                currentCoroutineContext().ensureActive()
                asm.handle(data)
                val now = System.currentTimeMillis()
                if (asm.revision != lastRev && now - lastEmit >= DRAFT_INTERVAL_MS) {
                    listener.onDraft(asm.snapshot())
                    lastEmit = now
                    lastRev = asm.revision
                }
                if (asm.done) break
            }
            if (!asm.done && asm.stopReason == null) {
                throw java.io.IOException("el stream terminó antes de tiempo")
            }
        } finally {
            es.cancel()
        }
    }

    private fun toException(t: Throwable?, response: Response?): Throwable {
        if (response != null && !response.isSuccessful) {
            val raw = runCatching { response.body?.string() }.getOrNull().orEmpty()
            val err = runCatching { (json.parseToJsonElement(raw).jsonObject["error"] as? JsonObject) }.getOrNull()
            val type = (err?.get("type") as? JsonPrimitive)?.contentOrNull
            val message = (err?.get("message") as? JsonPrimitive)?.contentOrNull ?: raw.take(300)
            return ApiException(response.code, type, friendly(response.code, message), rawMessage = message)
        }
        return t as? java.io.IOException ?: java.io.IOException(t?.message ?: "fallo de red", t)
    }

    private fun friendly(code: Int, message: String): String = when (code) {
        401 -> "Sesión no válida (401). $message"
        403 -> "Sin permiso (403). $message"
        404 -> "No encontrado (404): ¿modelo disponible en tu cuenta? $message"
        429 -> "Límite de uso alcanzado (429). $message"
        529 -> "Claude está saturado ahora mismo (529). $message"
        else -> "Error $code: $message"
    }

    private fun buildRequest(
        h: List<JsonObject>,
        cfg: Config,
        fallbacks: Boolean,
        thinking: Boolean,
        binding: Boolean,
    ): JsonObject = buildJsonObject {
        put("model", cfg.model)
        put("max_tokens", cfg.maxTokens)
        put("stream", true)
        put("system", buildJsonArray {
            add(History.textBlock(IDENTITY))
            add(History.textBlock(SYSTEM_PROMPT + "\n\n" + cfg.context))
        })
        put("messages", JsonArray(h))
        put("tools", JsonArray(registry.defs()))
        if (thinking) {
            put("thinking", buildJsonObject {
                put("type", "adaptive")
                put("display", "summarized")
                if (binding) {
                    // Drop (instead of rejecting) thinking blocks whose prefix no longer matches.
                    put("block_binding", buildJsonObject { put("prefix_mismatch_behavior", "drop_block") })
                }
            })
        }
        if (cfg.model !in noEffort && cfg.effort != null) {
            put("output_config", buildJsonObject { put("effort", cfg.effort) })
        }
        if (cfg.model !in noCache) put("cache_control", buildJsonObject { put("type", "ephemeral") })
        if (fallbacks) put("fallbacks", "default")
    }

    /**
     * Asks a fast model for a short chat title. Returns null on any failure so the
     * caller can keep its fallback title.
     */
    suspend fun generateTitle(firstUser: String, firstReply: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val token = oauth.validAccessToken() ?: return@runCatching null
            val prompt = "Escribe un título muy breve (máximo 6 palabras) para esta conversación, " +
                "en el mismo idioma que el mensaje del usuario. Responde solo con el título, " +
                "sin comillas ni punto final.\n\n<usuario>${firstUser.take(1500)}</usuario>\n" +
                "<respuesta>${firstReply.take(800)}</respuesta>"
            val body = buildJsonObject {
                put("model", TITLE_MODEL)
                put("max_tokens", 2000)
                put("system", buildJsonArray { add(History.textBlock(IDENTITY)) })
                put("messages", buildJsonArray { add(History.message("user", listOf(History.textBlock(prompt)))) })
                put("output_config", buildJsonObject { put("effort", "low") })
            }
            val req = Request.Builder()
                .url("$API_BASE/v1/messages")
                .header("Authorization", "Bearer $token")
                .header("anthropic-version", "2023-06-01")
                .header("anthropic-beta", OAUTH_BETA)
                .header("content-type", "application/json")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                val obj = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                if ((obj["stop_reason"] as? JsonPrimitive)?.contentOrNull == "refusal") return@runCatching null
                val text = (obj["content"] as? JsonArray).orEmpty()
                    .mapNotNull { it as? JsonObject }
                    .filter { History.type(it) == "text" }
                    .joinToString("") { History.str(it, "text") ?: "" }
                cleanTitle(text)
            }
        }.getOrNull()
    }

    companion object {
        const val API_BASE = "https://api.anthropic.com"
        const val OAUTH_BETA = "oauth-2025-04-20"
        const val FALLBACK_BETA = "server-side-fallback-2026-07-01"
        const val BLOCK_BINDING_BETA = "thinking-binding-controls-2026-08-01"
        const val TITLE_MODEL = "claude-haiku-5-5"
        const val MAX_ITERATIONS = 60
        const val MAX_RETRIES = 3
        const val MAX_TOOL_OUTPUT = 100_000
        const val DRAFT_INTERVAL_MS = 50L

        // Claude subscription OAuth requires the Claude Code identity as the first system block.
        const val IDENTITY = "You are Claude Code, Anthropic's official CLI for Claude."

        fun cleanTitle(raw: String): String? {
            val t = raw.lines().firstOrNull { it.isNotBlank() }?.trim()
                ?.trim('"', '\'', '«', '»', '“', '”', '*', '#', ' ')
                ?.removeSuffix(".")
                ?.trim() ?: return null
            if (t.isEmpty()) return null
            return if (t.length > 60) t.take(58).trimEnd() + "…" else t
        }

        private const val SYSTEM_PROMPT = """You are running inside PocketCode, a mobile Claude Code client on the user's own Android phone. You help with coding, files and device tasks.

You have tools to read, write and edit files, list and search directories, run shell commands and build Android APKs on the device. Prefer using the tools over guessing, and act with them rather than only describing steps. Reply in the user's language. Use Markdown: fenced code blocks with a language tag for code, short paragraphs, and lists or tables when they help. Keep answers concise; the screen is small."""
    }
}
