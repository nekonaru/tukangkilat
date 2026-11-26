package id.tukangkilat.app.data.api

import id.tukangkilat.app.data.local.ServerConfigManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import id.tukangkilat.app.data.local.SessionManager
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import okhttp3.MediaType.Companion.toMediaType
import javax.inject.Inject
import javax.inject.Singleton

/** Adds Authorization header for API calls when a token is present. */
@Singleton
class AuthInterceptor @Inject constructor(
    private val sessionManager: SessionManager
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking { sessionManager.accessTokenSnapshot() }
        val request = if (!token.isNullOrEmpty()) {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }
        return chain.proceed(request)
    }
}

/**
 * On 401, refreshes the session once and retries. Uses a separate bare client
 * so the refresh call never goes through this authenticator again.
 */
@Singleton
class TokenAuthenticator @Inject constructor(
    private val sessionManager: SessionManager,
    private val serverConfig: ServerConfigManager
) : Authenticator {

    private val bareClient = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private var pendingRefresh: CompletableDeferred<String?>? = null

    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.priorResponse != null) return null
        val refresh = runBlocking { sessionManager.refreshToken() } ?: return null
        val token = runBlocking {
            val waiter: CompletableDeferred<String?>
            val owner: Boolean
            synchronized(lock) {
                val existing = pendingRefresh
                if (existing != null) {
                    waiter = existing
                    owner = false
                } else {
                    waiter = CompletableDeferred()
                    pendingRefresh = waiter
                    owner = true
                }
            }
            if (owner) {
                val result = performRefresh(refresh)
                waiter.complete(result)
                synchronized(lock) { pendingRefresh = null }
                result
            } else {
                waiter.await()
            }
        } ?: return null
        return response.request.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
    }

    private suspend fun performRefresh(refresh: String): String? = try {
        val body = """{"refreshToken":"$refresh"}"""
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("${serverConfig.current()}/auth/refresh")
            .post(body)
            .build()
        bareClient.newCall(request).execute().use { res ->
            if (res.code == 401) {
                // Definitely invalid: clear once, even if several calls raced here.
                sessionManager.logout()
                null
            } else if (!res.isSuccessful) {
                // Transient server error: let the caller retry later.
                null
            } else {
                val text = res.body?.string() ?: return@use null
                val parsed = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                    .decodeFromString(id.tukangkilat.shared.LoginResponse.serializer(), text)
                sessionManager.updateTokens(parsed.accessToken, parsed.refreshToken)
                parsed.accessToken
            }
        }
    } catch (e: Exception) {
        null
    }
}
