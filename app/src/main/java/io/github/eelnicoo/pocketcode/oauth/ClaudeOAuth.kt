package io.github.eelnicoo.pocketcode.oauth

import android.net.Uri
import io.github.eelnicoo.pocketcode.data.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Authorization-Code + PKCE login against the user's own Claude account, using the
 * same public client id and manual-paste redirect that the Claude Code CLI uses.
 *
 * Flow:
 *  1. [buildAuthorizeUrl] -> open in a browser/Custom Tab.
 *  2. The user authorizes and copies the "code#state" string the callback page shows.
 *  3. [exchangeCode] trades it for access/refresh tokens, stored encrypted.
 *  4. [refresh] renews the access token when it is about to expire.
 */
class ClaudeOAuth(private val settings: Settings) {

    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient()

    fun buildAuthorizeUrl(): String {
        val verifier = randomUrlSafe(64)
        val state = randomUrlSafe(32)
        settings.pendingVerifier = verifier
        settings.pendingState = state
        val challenge = s256(verifier)
        return Uri.parse("$AUTH_BASE/oauth/authorize").buildUpon()
            .appendQueryParameter("code", "true")
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("scope", SCOPES)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("state", state)
            .build()
            .toString()
    }

    /** Accepts either "code#state" (as shown by the callback page) or a bare code. */
    suspend fun exchangeCode(pasted: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val trimmed = pasted.trim()
            val code: String
            val state: String
            if (trimmed.contains('#')) {
                val parts = trimmed.split('#', limit = 2)
                code = parts[0]
                state = parts.getOrNull(1) ?: (settings.pendingState ?: "")
            } else {
                code = trimmed
                state = settings.pendingState ?: ""
            }
            val verifier = settings.pendingVerifier
                ?: error("No hay un inicio de sesión en curso. Pulsa “Iniciar sesión” de nuevo.")

            val body = buildJsonObject {
                put("grant_type", "authorization_code")
                put("code", code)
                put("state", state)
                put("client_id", CLIENT_ID)
                put("redirect_uri", REDIRECT_URI)
                put("code_verifier", verifier)
            }
            postToken(body)
            settings.pendingVerifier = null
            settings.pendingState = null
        }
    }

    /** Returns a valid access token, refreshing if it expires within 60s. */
    suspend fun validAccessToken(): String? {
        val token = settings.accessToken ?: return null
        if (System.currentTimeMillis() < settings.expiresAt - 60_000L) return token
        return refresh().getOrNull() ?: token
    }

    suspend fun refresh(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val rt = settings.refreshToken ?: error("No hay refresh token; vuelve a iniciar sesión.")
            val body = buildJsonObject {
                put("grant_type", "refresh_token")
                put("refresh_token", rt)
                put("client_id", CLIENT_ID)
            }
            postToken(body)
            settings.accessToken!!
        }
    }

    private fun postToken(body: JsonObject) {
        val req = Request.Builder()
            .url("$TOKEN_BASE/v1/oauth/token")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("Token endpoint ${resp.code}: $text")
            val obj = json.parseToJsonElement(text) as JsonObject
            val access = obj["access_token"]?.jsonPrimitive?.content
                ?: error("Respuesta sin access_token")
            val refresh = obj["refresh_token"]?.jsonPrimitive?.content
            val expires = obj["expires_in"]?.jsonPrimitive?.content?.toLongOrNull() ?: 3600L
            settings.saveTokens(access, refresh, expires)
        }
    }

    private fun randomUrlSafe(bytes: Int): String {
        val b = ByteArray(bytes)
        SecureRandom().nextBytes(b)
        return android.util.Base64.encodeToString(
            b, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
        )
    }

    private fun s256(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray())
        return android.util.Base64.encodeToString(
            digest, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
        )
    }

    companion object {
        // Public client id used by the Claude Code CLI OAuth app.
        const val CLIENT_ID = "9d1c250a-e61b-44d9-88ed-5944d1962f5e"
        const val AUTH_BASE = "https://claude.ai"
        const val TOKEN_BASE = "https://console.anthropic.com"
        const val REDIRECT_URI = "https://console.anthropic.com/oauth/code/callback"
        const val SCOPES = "org:create_api_key user:profile user:inference"
    }
}
