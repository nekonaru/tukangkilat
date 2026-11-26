package id.tukangkilat.app.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import id.tukangkilat.shared.Role
import id.tukangkilat.shared.UserBrief
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

private val Context.sessionDataStore by preferencesDataStore(name = "tukangkilat_session")

/** Token encryption backed by Android KeyStore (AES-256-GCM). */
private object TokenCipher {
    private const val ALIAS = "tukangkilat_session_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    private val cachedKey: SecretKey by lazy { secretKey() }

    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, cachedKey)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + encrypted, Base64.NO_WRAP)
    }

    fun decrypt(encoded: String): String? {
        return try {
            val raw = Base64.decode(encoded, Base64.NO_WRAP)
            val iv = raw.copyOfRange(0, 12)
            val payload = raw.copyOfRange(12, raw.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, cachedKey, GCMParameterSpec(128, iv))
            String(cipher.doFinal(payload), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }
}

data class SessionState(
    val isLoggedIn: Boolean,
    val role: Role?,
    val user: UserBrief?,
    val accessToken: String?
)

private val KEY_ROLE = stringPreferencesKey("role")
private val KEY_USER = stringPreferencesKey("user_json")
private val KEY_ACCESS = stringPreferencesKey("access_token_enc")
private val KEY_REFRESH = stringPreferencesKey("refresh_token_enc")
private val KEY_VERSION = intPreferencesKey("session_version")

private val json = Json { ignoreUnknownKeys = true }

/** Holds the authenticated session. Tokens are encrypted at rest with Android KeyStore. */
@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val session: Flow<SessionState> = context.sessionDataStore.data.map { prefs ->
        val rawRefresh = prefs[KEY_REFRESH]?.let { TokenCipher.decrypt(it) }
        if (rawRefresh.isNullOrEmpty()) {
            SessionState(isLoggedIn = false, role = null, user = null, accessToken = null)
        } else {
            val role = prefs[KEY_ROLE]?.let { runCatching { Role.valueOf(it) }.getOrNull() }
            val user = prefs[KEY_USER]?.let { runCatching { json.decodeFromString(UserBrief.serializer(), it) }.getOrNull() }
            val access = prefs[KEY_ACCESS]?.let { TokenCipher.decrypt(it) }
            SessionState(isLoggedIn = role != null && user != null, role = role, user = user, accessToken = access)
        }
    }

    suspend fun save(accessToken: String, refreshToken: String, role: Role, user: UserBrief) {
        context.sessionDataStore.edit { prefs ->
            prefs[KEY_ACCESS] = TokenCipher.encrypt(accessToken)
            prefs[KEY_REFRESH] = TokenCipher.encrypt(refreshToken)
            prefs[KEY_ROLE] = role.name
            prefs[KEY_USER] = json.encodeToString(user)
            prefs[KEY_VERSION] = 1
        }
    }

    suspend fun accessToken(): String? = session.first().accessToken

    /** Read only the encrypted access token; interceptors need no user/role parsing. */
    suspend fun accessTokenSnapshot(): String? =
        context.sessionDataStore.data.first()[KEY_ACCESS]?.let { TokenCipher.decrypt(it) }

    suspend fun refreshToken(): String? =
        context.sessionDataStore.data.first()[KEY_REFRESH]?.let { TokenCipher.decrypt(it) }

    /** Replace tokens without touching role/user (after a silent refresh). */
    suspend fun updateTokens(accessToken: String, refreshToken: String) {
        context.sessionDataStore.edit { prefs ->
            prefs[KEY_ACCESS] = TokenCipher.encrypt(accessToken)
            prefs[KEY_REFRESH] = TokenCipher.encrypt(refreshToken)
        }
    }

    suspend fun logout() {
        scope.launch {
            context.sessionDataStore.edit { it.clear() }
        }.join()
    }

    /** Clear the in-memory DataStore cache as well, used after a database import. */
    suspend fun clear() {
        context.sessionDataStore.edit { it.clear() }
    }
}
