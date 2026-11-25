package id.tukangkilat.app.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import id.tukangkilat.shared.AddressDto
import id.tukangkilat.shared.ServiceDto

@Entity(tableName = "cached_services")
data class CachedService(
    @PrimaryKey val id: Long,
    val name: String,
    val icon: String,
    val basePrice: Long,
    val visitFee: Long,
    val isActive: Boolean
)

@Entity(tableName = "cached_addresses")
data class CachedAddress(
    @PrimaryKey val id: Long,
    val label: String,
    val fullAddress: String,
    val lat: Double,
    val lng: Double,
    val isPrimary: Boolean
)

@Dao
interface CatalogDao {
    @Query("SELECT * FROM cached_services ORDER BY id") suspend fun services(): List<CachedService>
    @Query("SELECT * FROM cached_addresses ORDER BY isPrimary DESC, id") suspend fun addresses(): List<CachedAddress>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertServices(items: List<CachedService>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAddresses(items: List<CachedAddress>)
    @Query("DELETE FROM cached_services") suspend fun clearServices()
    @Query("DELETE FROM cached_addresses") suspend fun clearAddresses()
}

fun ServiceDto.toCached() = CachedService(id, name, icon, basePrice, visitFee, isActive)
fun CachedService.toDto() = ServiceDto(id, name, icon, basePrice, visitFee, isActive)

fun AddressDto.toCached() = CachedAddress(id, label, fullAddress, lat, lng, isPrimary)
fun CachedAddress.toDto() = AddressDto(id, label, fullAddress, lat, lng, isPrimary)
