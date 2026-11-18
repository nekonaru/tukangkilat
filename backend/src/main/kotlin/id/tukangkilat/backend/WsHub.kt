package id.tukangkilat.backend

import id.tukangkilat.shared.WsEvent
import id.tukangkilat.shared.WsEventType
import io.ktor.websocket.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/** Registry of open WebSocket sessions per user id. */
object WsHub {
    private val mutex = Mutex()
    private val sessions = mutableMapOf<Long, MutableSet<WebSocketSession>>()

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun add(userId: Long, session: WebSocketSession) = mutex.withLock {
        sessions.getOrPut(userId) { mutableSetOf() }.add(session)
    }

    suspend fun remove(userId: Long, session: WebSocketSession) = mutex.withLock {
        sessions[userId]?.remove(session)
        if (sessions[userId]?.isEmpty() == true) sessions.remove(userId)
    }

    /** Send an event to every open session of [userId]. */
    suspend fun sendTo(userId: Long, event: WsEvent) {
        val targets = mutex.withLock { sessions[userId]?.toList() ?: emptyList() }
        val text = json.encodeToString(WsEvent.serializer(), event)
        for (session in targets) {
            runCatching { session.send(Frame.Text(text)) }
        }
    }

    suspend fun sendStatus(userId: Long, orderId: Long) {
        sendTo(userId, WsEvent(WsEventType.ORDER_STATUS, orderId))
    }
}
