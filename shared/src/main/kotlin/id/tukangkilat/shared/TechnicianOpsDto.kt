package id.tukangkilat.shared

import kotlinx.serialization.Serializable

@Serializable
data class TechDocumentDto(
    val id: Long,
    val docType: String,
    val fileUrl: String,
    val expiresAt: String?,
    val status: String,
    val note: String,
    val createdAt: String
)

@Serializable
data class AddDocumentRequest(val docType: String, val fileUrl: String, val expiresAt: String? = null)

@Serializable
data class VerifyDocumentRequest(val approve: Boolean, val note: String = "")

@Serializable
data class ServiceAreaDto(
    val id: Long,
    val areaName: String,
    val lat: Double,
    val lng: Double,
    val radiusKm: Int,
    val isExcluded: Boolean
)

@Serializable
data class AddServiceAreaRequest(
    val areaName: String,
    val lat: Double,
    val lng: Double,
    val radiusKm: Int = 10,
    val isExcluded: Boolean = false
)

@Serializable
data class AutoAcceptDto(
    val enabled: Boolean,
    val minPrice: Long,
    val maxDistance: Int,
    val serviceIds: String
)

@Serializable
data class SaveAutoAcceptRequest(
    val enabled: Boolean,
    val minPrice: Long,
    val maxDistance: Int,
    val serviceIds: String
)

@Serializable
data class RouteStopDto(
    val orderId: Long,
    val code: String,
    val serviceName: String,
    val lat: Double,
    val lng: Double,
    val distanceKm: Double,
    val scheduledDate: String,
    val status: String
)

@Serializable
data class CheckinDto(
    val id: Long,
    val orderId: Long,
    val kind: String,
    val lat: Double,
    val lng: Double,
    val distanceM: Int,
    val createdAt: String
)

@Serializable
data class CheckinRequest(val kind: String, val lat: Double, val lng: Double)

@Serializable
data class MaterialDto(val id: Long, val orderId: Long, val name: String, val quantity: Int, val cost: Long)

@Serializable
data class AddMaterialRequest(val orderId: Long, val name: String, val quantity: Int, val cost: Long)

@Serializable
data class BadgeDto(val id: Long, val code: String, val title: String, val earnedAt: String)

@Serializable
data class TemplateDto(val id: Long, val title: String, val body: String)

@Serializable
data class AddTemplateRequest(val title: String, val body: String)

@Serializable
data class LeaveDto(val id: Long, val startDate: String, val endDate: String, val reason: String, val status: String)

@Serializable
data class AddLeaveRequest(val startDate: String, val endDate: String, val reason: String)

@Serializable
data class JournalDto(val id: Long, val entryDate: String, val note: String)

@Serializable
data class AddJournalRequest(val entryDate: String, val note: String)

@Serializable
data class EquipmentDto(val id: Long, val name: String, val checked: Boolean)

@Serializable
data class SetEquipmentRequest(val name: String, val checked: Boolean)

@Serializable
data class OpsStatsDto(
    val responseMinutes: Int,
    val completionHours: Double,
    val cancellationRate: Int,
    val level: Int,
    val levelProgress: Int,
    val totalEarnings: Long
)
