package id.tukangkilat.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import id.tukangkilat.app.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.serverDataStore by preferencesDataStore(name = "tukangkilat_server")

private val KEY_SERVER_URL = stringPreferencesKey("server_url")

/** Stores the backend address chosen at runtime, so one APK works on emulator and on a real device. */
@Singleton
class ServerConfigManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val serverUrl: Flow<String> = context.serverDataStore.data.map { prefs ->
        prefs[KEY_SERVER_URL]?.takeIf { it.isNotBlank() } ?: BuildConfig.BASE_URL
    }

    suspend fun current(): String = serverUrl.first()

    suspend fun save(url: String) {
        context.serverDataStore.edit { prefs ->
            val trimmed = url.trim().trimEnd('/')
            if (trimmed.isBlank()) prefs.remove(KEY_SERVER_URL)
            else prefs[KEY_SERVER_URL] = trimmed
        }
    }
}
