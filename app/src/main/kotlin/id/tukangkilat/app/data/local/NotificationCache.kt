package id.tukangkilat.app.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/** Persisted in-app notifications so the history survives process death. */
@Entity(tableName = "cached_notifications")
data class CachedNotification(
    @PrimaryKey val id: Long,
    val title: String,
    val body: String,
    val createdAt: Long,
    val read: Boolean,
    val orderId: Long? = null
)

@Dao
interface NotificationDao {
    @Query("SELECT * FROM cached_notifications ORDER BY createdAt DESC")
    suspend fun all(): List<CachedNotification>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: CachedNotification)

    @Query("UPDATE cached_notifications SET read = 1")
    suspend fun markAllRead()

    @Query("DELETE FROM cached_notifications")
    suspend fun clear()

    @Query(
        "DELETE FROM cached_notifications WHERE id NOT IN " +
            "(SELECT id FROM cached_notifications ORDER BY createdAt DESC LIMIT 50)"
    )
    suspend fun trim()
}
