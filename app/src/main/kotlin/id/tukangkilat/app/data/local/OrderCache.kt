package id.tukangkilat.app.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import id.tukangkilat.shared.OrderStatus
import id.tukangkilat.shared.OrderSummaryDto
import id.tukangkilat.shared.PaymentStatus

/** Lightweight cache of the caller's orders so Home and the Orders tab render instantly. */
@Entity(tableName = "cached_orders")
data class CachedOrder(
    @PrimaryKey val id: Long,
    val code: String,
    val status: String,
    val serviceName: String,
    val scheduledDate: String,
    val slotLabel: String,
    val total: Long,
    val paymentStatus: String,
    val createdAt: String
)

@Dao
interface OrderDao {
    @Query("SELECT * FROM cached_orders ORDER BY createdAt DESC")
    suspend fun all(): List<CachedOrder>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(orders: List<CachedOrder>)

    @Query("DELETE FROM cached_orders")
    suspend fun clear()
}

@androidx.room.Database(
    entities = [
        CachedOrder::class, CachedService::class, CachedAddress::class,
        FavoriteTechnician::class, CachedNotification::class
    ],
    version = 5,
    exportSchema = false
)
abstract class TukangKilatDatabase : RoomDatabase() {
    abstract fun orderDao(): OrderDao
    abstract fun catalogDao(): CatalogDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun notificationDao(): NotificationDao
}

fun OrderSummaryDto.toCached() = CachedOrder(
    id = id,
    code = code,
    status = status.name,
    serviceName = serviceName,
    scheduledDate = scheduledDate,
    slotLabel = slotLabel,
    total = total,
    paymentStatus = paymentStatus.name,
    createdAt = createdAt
)

fun CachedOrder.toSummary() = OrderSummaryDto(
    id = id,
    code = code,
    status = runCatching { OrderStatus.valueOf(status) }.getOrDefault(OrderStatus.MENUNGGU_PEMBAYARAN),
    serviceName = serviceName,
    scheduledDate = scheduledDate,
    slotLabel = slotLabel,
    total = total,
    paymentStatus = runCatching { PaymentStatus.valueOf(paymentStatus) }.getOrDefault(PaymentStatus.MENUNGGU),
    createdAt = createdAt
)
