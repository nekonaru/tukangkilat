package id.tukangkilat.app.data.repository

import id.tukangkilat.app.data.local.CachedNotification
import id.tukangkilat.app.data.local.NotificationDao
import id.tukangkilat.app.data.ws.WsManager
import id.tukangkilat.shared.WsEvent
import id.tukangkilat.shared.WsEventType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

data class AppNotification(
    val id: Long,
    val title: String,
    val body: String,
    val createdAt: Long,
    val read: Boolean = false
)

/**
 * In-app notification center. PRD allows a WebSocket fallback when FCM is unavailable;
 * this turns those live events into a visible, readable history that survives process death.
 */
@Singleton
class NotificationRepository @Inject constructor(
    wsManager: WsManager,
    private val dao: NotificationDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val nextId = AtomicLong(1)
    private val _notifications = MutableStateFlow<List<AppNotification>>(emptyList())
    val notifications: StateFlow<List<AppNotification>> = _notifications.asStateFlow()

    /** All persistence goes through one channel so writes stay ordered. */
    private val writes = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init {
        scope.launch {
            val saved = dao.all()
            _notifications.value = saved.map {
                AppNotification(it.id, it.title, it.body, it.createdAt, it.read)
            }
            nextId.set((saved.maxOfOrNull { it.id } ?: 0L) + 1L)
            launch {
                for (operation in writes) operation()
            }
            // Start collecting only after the saved history is loaded, so no live event is lost.
            wsManager.events.collect { event -> add(event) }
        }
    }

    private fun add(event: WsEvent) {
        if (event.type == WsEventType.TECHNICIAN_LOCATION) return
        val (title, body) = when (event.type) {
            WsEventType.ORDER_STATUS -> "Pesanan diperbarui" to "Status pesanan berubah. Buka detail untuk melihat yang terbaru."
            WsEventType.OFFER_NEW -> "Tawaran pekerjaan baru" to "Ada pesanan baru menunggu di beranda teknisi."
            WsEventType.OFFER_EXPIRED -> "Tawaran kedaluarsa" to "Penawaran sebelumnya sudah berakhir."
            WsEventType.CHAT_MESSAGE -> "Pesan baru" to "Ada pesan baru untuk pesanan Anda."
            WsEventType.TECHNICIAN_LOCATION -> return
        }
        val item = AppNotification(
            id = nextId.getAndIncrement(),
            title = title,
            body = if (event.orderId != null) "$body (${event.orderId})" else body,
            createdAt = System.currentTimeMillis()
        )
        _notifications.value = listOf(item) + _notifications.value.take(49)
        writes.trySend {
            dao.upsert(CachedNotification(item.id, item.title, item.body, item.createdAt, item.read))
            dao.trim()
        }
    }

    fun markAllRead() {
        _notifications.value = _notifications.value.map { it.copy(read = true) }
        writes.trySend { dao.markAllRead() }
    }

    fun clear() {
        _notifications.value = emptyList()
        writes.trySend { dao.clear() }
    }
}
