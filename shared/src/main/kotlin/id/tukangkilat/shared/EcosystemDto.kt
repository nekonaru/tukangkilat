package id.tukangkilat.shared

import kotlinx.serialization.Serializable

@Serializable
data class WalletTransactionDto(
    val id: Long,
    val amount: Long,
    val type: String,
    val reference: String,
    val createdAt: String
)

@Serializable
data class WalletDto(
    val balance: Long,
    val transactions: List<WalletTransactionDto>
)

@Serializable
data class TopUpRequest(val amount: Long)

@Serializable
data class LoyaltyDto(val points: Int, val lifetime: Int)

@Serializable
data class SubscriptionDto(
    val id: Long,
    val serviceId: Long,
    val serviceName: String,
    val addressId: Long,
    val addressLabel: String,
    val intervalDays: Int,
    val nextDate: String,
    val status: String
)

@Serializable
data class CreateSubscriptionRequest(
    val serviceId: Long,
    val addressId: Long,
    val intervalDays: Int,
    val startDate: String
)

@Serializable
data class AvailabilityBlockDto(val id: Long, val date: String, val slotLabel: String)

@Serializable
data class CreateAvailabilityBlockRequest(val date: String, val slotLabel: String)

@Serializable
data class PortfolioDto(val id: Long, val photoUrl: String, val caption: String)

@Serializable
data class AddPortfolioRequest(val photoUrl: String, val caption: String)

@Serializable
data class PayoutAccountDto(val bank: String, val accountNo: String, val accountName: String)

@Serializable
data class SavePayoutAccountRequest(val bank: String, val accountNo: String, val accountName: String)

@Serializable
data class TechnicianStatsDto(
    val ratingAvg: Double,
    val jobsCount: Int,
    val totalOffers: Int,
    val acceptedOffers: Int,
    val acceptRate: Int
)

@Serializable
data class DisputeDto(
    val id: Long,
    val reportId: Long,
    val orderId: Long,
    val amount: Long,
    val decision: String,
    val note: String,
    val createdAt: String
)

@Serializable
data class ResolveDisputeRequest(val decision: String, val amount: Long, val note: String)

@Serializable
data class AuditLogDto(val id: Long, val adminId: Long, val action: String, val target: String, val createdAt: String)

@Serializable
data class AnnouncementDto(val id: Long, val title: String, val body: String, val createdAt: String)

@Serializable
data class CreateAnnouncementRequest(val title: String, val body: String)

@Serializable
data class SystemConfigDto(val key: String, val value: String)

@Serializable
data class TechnicianLeaderDto(
    val technicianId: Long,
    val name: String,
    val ratingAvg: Double,
    val jobsCount: Int,
    val acceptRate: Int
)

@Serializable
data class DirectPeerDto(
    val peerId: Long,
    val unreadCount: Int,
    val lastMessage: String,
    val lastAt: String
)

@Serializable
data class AutoOnlineScheduleDto(
    val id: Long,
    val dayOfWeek: Int,
    val startTime: String,
    val endTime: String,
    val enabled: Boolean
)

@Serializable
data class CreateAutoScheduleRequest(
    val dayOfWeek: Int,
    val startTime: String,
    val endTime: String,
    val enabled: Boolean = true
)

@Serializable
data class MessageReportDto(
    val id: Long,
    val messageId: Long,
    val reporterId: Long,
    val reporterName: String,
    val messageBody: String,
    val reason: String,
    val status: String,
    val createdAt: String
)

@Serializable
data class ReportMessageRequest(val messageId: Long, val reason: String)
