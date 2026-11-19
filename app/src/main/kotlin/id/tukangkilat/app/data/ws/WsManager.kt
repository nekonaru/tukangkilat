package id.tukangkilat.app.data.ws

import id.tukangkilat.app.BuildConfig
import id.tukangkilat.app.data.local.SessionManager
import id.tukangkilat.shared.WsEvent
import id.tukangkilat.shared.WsEventType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Persistent WebSocket client for order events, with automatic reconnection. */
@Singleton
class WsManager @Inject constructor(
    private val sessionManager: SessionManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _events = MutableSharedFlow<WsEvent>(extraBufferCapacity = 64)
    val events: Flow<WsEvent> = _events.asSharedFlow()

    private val json = Json { ignoreUnknownKeys = true }
    private var socket: WebSocket? = null
    private var running = false

    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    @OptIn(FlowPreview::class)
    fun start() {
        if (running) return
        running = true
        scope.launch {
            var backoffMs = 1_000L
            while (isActive && running) {
                val token = sessionManager.accessToken()
                if (token == null) {
                    delay(5_000)
                    continue
                }
                val request = Request.Builder()
                    .url("${BuildConfig.BASE_URL.replace("http", "ws", ignoreCase = true)}/ws?token=$token")
                    .build()
                val opened = try {
                    client.newWebSocket(request, object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket, response: Response) {
                            backoffMs = 1_000L
                        }

                        override fun onMessage(webSocket: WebSocket, text: String) {
                            runCatching { json.decodeFromString(WsEvent.serializer(), text) }
                                .onSuccess { _events.tryEmit(it) }
                        }

                        override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {}

                        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                            webSocket.close(code, reason)
                        }

                        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                            socket = null
                        }

                        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                            socket = null
                        }
                    }).also { socket = it }
                } catch (e: Exception) {
                    null
                }
                if (opened != null) {
                    // stay until the socket dies, polling socket reference
                    while (socket != null) {
                        delay(500)
                        if (!running) break
                    }
                }
                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(30_000L)
            }
        }
    }

    fun stop() {
        running = false
        socket?.close(1000, "bye")
        socket = null
    }
}
