package id.tukangkilat.app.data.api

import id.tukangkilat.app.data.local.ServerConfigManager
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/** Applies the runtime backend address to every request without rebuilding Retrofit. */
class BaseUrlInterceptor @Inject constructor(
    private val serverConfig: ServerConfigManager
) : Interceptor {

    companion object {
        @Volatile
        private var cachedUrl: String? = null

        /** Call after saving a new address so the next request picks it up. */
        fun invalidate() { cachedUrl = null }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val configured = cachedUrl ?: runBlocking { serverConfig.current() }.also { cachedUrl = it }
        val target = (configured + "/").toHttpUrlOrNull() ?: return chain.proceed(original)
        val current = original.url
        val rewritten = original.newBuilder()
            .url(
                current.newBuilder()
                    .scheme(target.scheme)
                    .host(target.host)
                    .port(target.port)
                    .build()
            )
            .build()
        return chain.proceed(rewritten)
    }
}
