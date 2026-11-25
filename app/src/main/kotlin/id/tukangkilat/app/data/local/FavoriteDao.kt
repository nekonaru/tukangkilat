package id.tukangkilat.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import id.tukangkilat.shared.TechnicianPublicDto

@Entity(tableName = "favorite_technicians")
data class FavoriteTechnician(
    @PrimaryKey val userId: Long,
    val fullName: String,
    val ratingAvg: Double,
    val jobsCount: Int,
    val addedAt: Long = System.currentTimeMillis()
)

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorite_technicians ORDER BY addedAt DESC") suspend fun all(): List<FavoriteTechnician>
    @Query("SELECT userId FROM favorite_technicians") suspend fun ids(): List<Long>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(item: FavoriteTechnician)
    @Query("DELETE FROM favorite_technicians WHERE userId = :userId") suspend fun remove(userId: Long)
}

fun TechnicianPublicDto.toFavorite() = FavoriteTechnician(id, fullName, ratingAvg, jobsCount)
