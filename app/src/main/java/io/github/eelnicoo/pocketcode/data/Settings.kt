package io.github.eelnicoo.pocketcode.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A selectable Claude model, as shown in the model picker. */
data class ModelInfo(
    val id: String,
    val name: String,
    val description: String,
    /** Whether server-side refusal fallbacks (`fallbacks: "default"`) apply to this model. */
    val supportsFallback: Boolean,
)

/**
 * Encrypted storage for credentials and preferences. OAuth tokens never leave the
 * device and are held in an EncryptedSharedPreferences file (AES-256).
 */
class Settings private constructor(private val prefs: SharedPreferences) {

    private val _state = MutableStateFlow(snapshot())
    val state: StateFlow<SettingsState> = _state

    data class SettingsState(
        val loggedIn: Boolean,
        val model: String,
        val workingDir: String,
        val useShizuku: Boolean,
        val maxTokens: Int,
        val displayName: String,
        val chatFont: String,
        val themeMode: String,
        val extendedThinking: Boolean,
    )

    private fun snapshot() = SettingsState(
        loggedIn = accessToken != null,
        model = model,
        workingDir = workingDir,
        useShizuku = useShizuku,
        maxTokens = maxTokens,
        displayName = displayName,
        chatFont = chatFont,
        themeMode = themeMode,
        extendedThinking = extendedThinking,
    )

    private fun refresh() { _state.value = snapshot() }

    private fun putString(key: String, v: String?) { prefs.edit().putString(key, v).apply(); refresh() }

    var accessToken: String?
        get() = prefs.getString(K_ACCESS, null)
        set(v) = putString(K_ACCESS, v)

    var refreshToken: String?
        get() = prefs.getString(K_REFRESH, null)
        set(v) { prefs.edit().putString(K_REFRESH, v).apply() }

    var expiresAt: Long
        get() = prefs.getLong(K_EXPIRES, 0L)
        set(v) { prefs.edit().putLong(K_EXPIRES, v).apply() }

    var model: String
        get() = prefs.getString(K_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(v) = putString(K_MODEL, v)

    var workingDir: String
        get() = prefs.getString(K_WORKDIR, "") ?: ""
        set(v) = putString(K_WORKDIR, v)

    var useShizuku: Boolean
        get() = prefs.getBoolean(K_SHIZUKU, false)
        set(v) { prefs.edit().putBoolean(K_SHIZUKU, v).apply(); refresh() }

    var maxTokens: Int
        get() = prefs.getInt(K_MAXTOK_V2, DEFAULT_MAX_TOKENS)
        set(v) { prefs.edit().putInt(K_MAXTOK_V2, v).apply(); refresh() }

    /** Name used in the greeting ("Buenas tardes, …"). */
    var displayName: String
        get() = prefs.getString(K_NAME, "") ?: ""
        set(v) = putString(K_NAME, v)

    /** "serif" (Claude default for replies) or "sans". */
    var chatFont: String
        get() = prefs.getString(K_FONT, "serif") ?: "serif"
        set(v) = putString(K_FONT, v)

    /** "system", "light" or "dark". */
    var themeMode: String
        get() = prefs.getString(K_THEME, "system") ?: "system"
        set(v) = putString(K_THEME, v)

    /** Extended thinking: maps to a higher effort level on the request. */
    var extendedThinking: Boolean
        get() = prefs.getBoolean(K_THINK, true)
        set(v) { prefs.edit().putBoolean(K_THINK, v).apply(); refresh() }

    var lastChatId: String?
        get() = prefs.getString(K_LAST_CHAT, null)
        set(v) { prefs.edit().putString(K_LAST_CHAT, v).apply() }

    // Transient PKCE values during an in-flight login.
    var pendingVerifier: String?
        get() = prefs.getString(K_VERIFIER, null)
        set(v) { prefs.edit().putString(K_VERIFIER, v).apply() }

    var pendingState: String?
        get() = prefs.getString(K_STATE, null)
        set(v) { prefs.edit().putString(K_STATE, v).apply() }

    fun saveTokens(access: String, refresh: String?, expiresInSeconds: Long) {
        prefs.edit().putString(K_ACCESS, access).apply()
        if (refresh != null) refreshToken = refresh
        expiresAt = System.currentTimeMillis() + expiresInSeconds * 1000L
        refresh()
    }

    fun logout() {
        prefs.edit()
            .remove(K_ACCESS).remove(K_REFRESH).remove(K_EXPIRES)
            .remove(K_VERIFIER).remove(K_STATE)
            .apply()
        refresh()
    }

    companion object {
        const val DEFAULT_MODEL = "claude-opus-5-5"
        const val DEFAULT_MAX_TOKENS = 64000

        val MODELS = listOf(
            ModelInfo("claude-opus-5-5", "Opus 5.5", "El más capaz para tareas complejas y código", true),
            ModelInfo("claude-sonnet-5-5", "Sonnet 5.5", "Inteligente y rápido para el día a día", true),
            ModelInfo("claude-haiku-5-5", "Haiku 5.5", "El más rápido para cosas sencillas", false),
            ModelInfo("claude-fable-5-1", "Fable 5.1", "Razonamiento máximo para lo más difícil", true),
            ModelInfo("claude-opus-4-8", "Opus 4.8", "Generación anterior", false),
        )

        fun modelInfo(id: String): ModelInfo =
            MODELS.firstOrNull { it.id == id }
                ?: ModelInfo(id, id.removePrefix("claude-"), "Modelo personalizado", false)

        private const val K_ACCESS = "oauth_access"
        private const val K_REFRESH = "oauth_refresh"
        private const val K_EXPIRES = "oauth_expires"
        private const val K_MODEL = "model"
        private const val K_WORKDIR = "working_dir"
        private const val K_SHIZUKU = "use_shizuku"
        private const val K_MAXTOK_V2 = "max_tokens_v2"
        private const val K_NAME = "display_name"
        private const val K_FONT = "chat_font"
        private const val K_THEME = "theme_mode"
        private const val K_THINK = "extended_thinking"
        private const val K_LAST_CHAT = "last_chat_id"
        private const val K_VERIFIER = "pkce_verifier"
        private const val K_STATE = "pkce_state"

        @Volatile private var INSTANCE: Settings? = null

        fun get(context: Context): Settings = INSTANCE ?: synchronized(this) {
            INSTANCE ?: create(context.applicationContext).also { INSTANCE = it }
        }

        private fun create(context: Context): Settings {
            val key = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val prefs = EncryptedSharedPreferences.create(
                context,
                "secure_prefs",
                key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
            return Settings(prefs)
        }
    }
}
