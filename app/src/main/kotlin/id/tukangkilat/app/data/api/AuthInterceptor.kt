package id.tukangkilat.app.data.api

import id.tukangkilat.app.BuildConfig
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
        val token = runBlocking { sessionManager.accessToken() }
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
    private val sessionManager: SessionManager
) : Authenticator {

    private val bareClient = OkHttpClient.Builder().build()

    override fun authenticate(route: Route?, response: Response): Request? {
        // only retry once per response chain
        if (response.priorResponse != null) return null
        val refresh = runBlocking { sessionManager.refreshToken() } ?: return null

        return runBlocking {
            try {
                val body = """{"refreshToken":"$refresh"}"""
                    .toRequestBody("application/json".toMediaType())
                val refreshRequest = Request.Builder()
                    .url("${BuildConfig.BASE_URL}/auth/refresh")
                    .post(body)
                    .build()
                bareClient.newCall(refreshRequest).execute().use { res ->
                    if (!res.isSuccessful) {
                        sessionManager.logout()
                        return@runBlocking null
                    }
                    val text = res.body?.string() ?: return@runBlocking null
                    val parsed = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                        .decodeFromString(id.tukangkilat.shared.LoginResponse.serializer(), text)
                    sessionManager.updateTokens(parsed.accessToken, parsed.refreshToken)
                    response.request.newBuilder()
                        .header("Authorization", "Bearer ${parsed.accessToken}")
                        .build()
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}
