package id.tukangkilat.shared

import kotlinx.serialization.Serializable

@Serializable
data class ApiError(
    val code: String,
    val message: String
)

// ---------- Auth ----------

@Serializable
data class RegisterRequest(
    val fullName: String,
    val username: String,
    val password: String,
    val phone: String,
    val address: String? = null,
    val lat: Double? = null,
    val lng: Double? = null
)

@Serializable
data class RegisterTechnicianRequest(
    val fullName: String,
    val username: String,
    val password: String,
    val phone: String,
    val skills: List<String>,
    val serviceRadiusKm: Int = 10,
    val ktpPhotoUrl: String,
    val selfiePhotoUrl: String
)

@Serializable
data class LoginRequest(
    val username: String,
    val password: String
)

@Serializable
data class RefreshRequest(
    val refreshToken: String
)

@Serializable
data class UserBrief(
    val id: Long,
    val username: String,
    val fullName: String,
    val phone: String? = null,
    val role: Role,
    val status: String
)

@Serializable
data class LoginResponse(
    val accessToken: String,
    val refreshToken: String,
    val role: Role,
    val user: UserBrief
)

// ---------- Catalog ----------

@Serializable
data class ServiceDto(
    val id: Long,
    val name: String,
    val icon: String,
    val basePrice: Long,
    val visitFee: Long,
    val isActive: Boolean
)

@Serializable
data class SlotDto(
    val id: Long,
    val label: String,
    val capacity: Int,
    val remaining: Int,
    val available: Boolean
)

// ---------- Addresses ----------

@Serializable
data class AddressDto(
    val id: Long,
    val label: String,
    val fullAddress: String,
    val lat: Double,
    val lng: Double,
    val isPrimary: Boolean
)

@Serializable
data class AddressUpsertRequest(
    val label: String,
    val fullAddress: String,
    val lat: Double,
    val lng: Double,
    val isPrimary: Boolean = false
)

// ---------- Orders ----------

@Serializable
data class CreateOrderRequest(
    val serviceId: Long,
    val description: String,
    val addressId: Long,
    val scheduledDate: String,
    val slotId: Long,
    val promoCode: String? = null,
    val photoUrls: List<String> = emptyList()
)

@Serializable
data class OrderEventDto(
    val status: OrderStatus,
    val actorId: Long?,
    val createdAt: String
)

@Serializable
data class TechnicianPublicDto(
    val id: Long,
    val fullName: String,
    val ratingAvg: Double,
    val jobsCount: Int,
    val photoUrl: String? = null,
    val phone: String? = null
)

@Serializable
data class OrderPhotoDto(
    val id: Long,
    val kind: PhotoKind,
    val url: String
)

@Serializable
data class EtaDto(
    val distanceKm: Double,
    val minutes: Int,
    val arriveAt: String
)

@Serializable
data class OrderDto(
    val id: Long,
    val code: String,
    val status: OrderStatus,
    val serviceId: Long,
    val serviceName: String,
    val description: String,
    val addressId: Long,
    val addressLabel: String,
    val addressFull: String,
    val addressLat: Double,
    val addressLng: Double,
    val scheduledDate: String,
    val slotLabel: String,
    val serviceFee: Long,
    val visitFee: Long,
    val discount: Long,
    val total: Long,
    val paymentMethod: PaymentMethod? = null,
    val paymentStatus: PaymentStatus,
    val finishNote: String? = null,
    val technician: TechnicianPublicDto? = null,
    val technicianLat: Double? = null,
    val technicianLng: Double? = null,
    val eta: EtaDto? = null,
    val photos: List<OrderPhotoDto> = emptyList(),
    val events: List<OrderEventDto> = emptyList(),
    val createdAt: String
)

@Serializable
data class OrderSummaryDto(
    val id: Long,
    val code: String,
    val status: OrderStatus,
    val serviceName: String,
    val scheduledDate: String,
    val slotLabel: String,
    val total: Long,
    val paymentStatus: PaymentStatus,
    val createdAt: String
)

@Serializable
data class PayRequest(
    val method: PaymentMethod,
    val promoCode: String? = null
)

@Serializable
data class PayResponse(
    val orderId: Long,
    val paymentId: Long,
    val method: PaymentMethod,
    val amount: Long,
    val referenceNo: String?,
    val status: PaymentStatus,
    val expiresAt: String?
)

@Serializable
data class RescheduleRequest(
    val scheduledDate: String,
    val slotId: Long
)

@Serializable
data class CancelRequest(
    val reason: String
)

@Serializable
data class ChangePasswordRequest(
    val oldPassword: String,
    val newPassword: String
)

@Serializable
data class ConfirmOrderRequest(
    val note: String? = null
)

@Serializable
data class ReportOrderRequest(
    val reason: IssueReason,
    val description: String,
    val photoUrls: List<String> = emptyList()
)

@Serializable
data class RatingRequest(
    val stars: Int,
    val tags: List<String> = emptyList(),
    val comment: String = ""
)

// ---------- Technician ----------

@Serializable
data class OnlineRequest(
    val online: Boolean
)

@Serializable
data class LocationRequest(
    val lat: Double,
    val lng: Double
)

@Serializable
data class OfferDto(
    val id: Long,
    val orderId: Long,
    val orderCode: String,
    val serviceName: String,
    val description: String,
    val distanceKm: Double,
    val scheduledDate: String,
    val slotLabel: String,
    val estimatedPayout: Long,
    val expiresAt: String,
    val status: OfferStatus,
    val isRevisit: Boolean
)

@Serializable
data class RejectOfferRequest(
    val reason: String
)

@Serializable
data class FinishWorkRequest(
    val note: String,
    val photoUrls: List<String>,
    val cashReceived: Boolean = false
)

@Serializable
data class EarningEntryDto(
    val orderId: Long,
    val orderCode: String,
    val serviceName: String,
    val serviceFee: Long,
    val commission: Long,
    val visitFee: Long,
    val net: Long,
    val finishedAt: String
)

@Serializable
data class EarningsDto(
    val balance: Long,
    val totalEarned: Long,
    val commissionRate: Double,
    val entries: List<EarningEntryDto>
)

@Serializable
data class WithdrawalDto(
    val id: Long,
    val amount: Long,
    val status: String,
    val createdAt: String
)

@Serializable
data class WithdrawRequest(val amount: Long)

@Serializable
data class TechnicianProfileDto(
    val id: Long,
    val fullName: String,
    val username: String,
    val phone: String,
    val skills: List<String>,
    val serviceRadiusKm: Int,
    val verificationStatus: String,
    val isOnline: Boolean,
    val ratingAvg: Double,
    val jobsCount: Int,
    val photoUrl: String? = null
)

@Serializable
data class TechnicianUpdateRequest(
    val skills: List<String>? = null,
    val serviceRadiusKm: Int? = null
)

// ---------- Chat ----------

@Serializable
data class MessageDto(
    val id: Long,
    val orderId: Long,
    val senderId: Long,
    val senderName: String,
    val body: String,
    val createdAt: String
)

@Serializable
data class SendMessageRequest(
    val body: String
)

// ---------- Admin ----------

@Serializable
data class AdminTechnicianDto(
    val userId: Long,
    val fullName: String,
    val username: String,
    val phone: String,
    val skills: List<String>,
    val verificationStatus: String,
    val ktpPhotoUrl: String?,
    val selfiePhotoUrl: String?,
    val ratingAvg: Double,
    val jobsCount: Int
)

@Serializable
data class RejectTechnicianRequest(
    val reason: String
)

@Serializable
data class IssueReportDto(
    val id: Long,
    val orderId: Long,
    val orderCode: String,
    val reason: IssueReason,
    val description: String,
    val photoUrls: List<String>,
    val status: IssueStatus,
    val createdAt: String
)

@Serializable
data class AdminSummaryDto(
    val ordersToday: Int,
    val techniciansOnline: Int,
    val openReports: Int
)

@Serializable
data class ServiceCreateRequest(
    val name: String,
    val icon: String = "bolt",
    val skill: String = "LISTRIK",
    val basePrice: Long,
    val visitFee: Long = 25_000
)

@Serializable
data class ServiceUpdateRequest(
    val name: String? = null,
    val basePrice: Long? = null,
    val visitFee: Long? = null,
    val isActive: Boolean? = null
)

@Serializable
data class UserStatusUpdateRequest(
    val status: String
)

// ---------- Technician reviews ----------

@Serializable
data class ReviewDto(
    val orderId: Long,
    val orderCode: String,
    val serviceName: String,
    val stars: Int,
    val tags: List<String> = emptyList(),
    val comment: String,
    val createdAt: String
)

// ---------- Promo admin ----------

@Serializable
data class PromoCodeDto(
    val code: String,
    val type: String,
    val value: Long,
    val validUntil: String,
    val quota: Int,
    val isActive: Boolean
)

@Serializable
data class PromoUpsertRequest(
    val code: String,
    val type: String,
    val value: Long,
    val validUntil: String,
    val quota: Int,
    val isActive: Boolean = true
)

// ---------- WebSocket ----------

@Serializable
data class WsEvent(
    val type: WsEventType,
    val orderId: Long? = null,
    val payload: kotlinx.serialization.json.JsonObject? = null
)
