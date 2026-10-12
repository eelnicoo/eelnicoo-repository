package io.github.eelnicoo.pocketcode.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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
    )

    private fun snapshot() = SettingsState(
        loggedIn = accessToken != null,
        model = model,
        workingDir = workingDir,
        useShizuku = useShizuku,
        maxTokens = maxTokens,
    )

    private fun refresh() { _state.value = snapshot() }

    var accessToken: String?
        get() = prefs.getString(K_ACCESS, null)
        set(v) { prefs.edit().putString(K_ACCESS, v).apply(); refresh() }

    var refreshToken: String?
        get() = prefs.getString(K_REFRESH, null)
        set(v) { prefs.edit().putString(K_REFRESH, v).apply() }

    var expiresAt: Long
        get() = prefs.getLong(K_EXPIRES, 0L)
        set(v) { prefs.edit().putLong(K_EXPIRES, v).apply() }

    var model: String
        get() = prefs.getString(K_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(v) { prefs.edit().putString(K_MODEL, v).apply(); refresh() }

    var workingDir: String
        get() = prefs.getString(K_WORKDIR, "") ?: ""
        set(v) { prefs.edit().putString(K_WORKDIR, v).apply(); refresh() }

    var useShizuku: Boolean
        get() = prefs.getBoolean(K_SHIZUKU, false)
        set(v) { prefs.edit().putBoolean(K_SHIZUKU, v).apply(); refresh() }

    var maxTokens: Int
        get() = prefs.getInt(K_MAXTOK, 16000)
        set(v) { prefs.edit().putInt(K_MAXTOK, v).apply(); refresh() }

    // Transient PKCE values during an in-flight login.
    var pendingVerifier: String?
        get() = prefs.getString(K_VERIFIER, null)
        set(v) { prefs.edit().putString(K_VERIFIER, v).apply() }

    var pendingState: String?
        get() = prefs.getString(K_STATE, null)
        set(v) { prefs.edit().putString(K_STATE, v).apply() }

    fun saveTokens(access: String, refresh: String?, expiresInSeconds: Long) {
        prefs.edit()
            .putString(K_ACCESS, access)
            .apply()
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
        const val DEFAULT_MODEL = "claude-opus-4-8"
        val MODELS = listOf(
            "claude-opus-4-8",
            "claude-sonnet-4-5",
            "claude-haiku-4-5",
            "claude-opus-4-6",
            "claude-sonnet-4-6",
        )

        private const val K_ACCESS = "oauth_access"
        private const val K_REFRESH = "oauth_refresh"
        private const val K_EXPIRES = "oauth_expires"
        private const val K_MODEL = "model"
        private const val K_WORKDIR = "working_dir"
        private const val K_SHIZUKU = "use_shizuku"
        private const val K_MAXTOK = "max_tokens"
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
