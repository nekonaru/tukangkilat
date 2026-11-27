package id.tukangkilat.shared

import kotlinx.serialization.Serializable

@Serializable
data class FeatureFlagDto(val key: String, val enabled: Boolean, val targetRole: String, val note: String)

@Serializable
data class SaveFeatureFlagRequest(val enabled: Boolean, val targetRole: String = "", val note: String = "")

@Serializable
data class ContentPageDto(val id: Long, val code: String, val title: String, val body: String, val isActive: Boolean)

@Serializable
data class SaveContentRequest(val code: String, val title: String, val body: String, val isActive: Boolean = true)

@Serializable
data class BroadcastDto(
    val id: Long,
    val title: String,
    val body: String,
    val targetRole: String,
    val sendAt: String,
    val status: String
)

@Serializable
data class CreateBroadcastRequest(val title: String, val body: String, val targetRole: String, val sendAt: String)

@Serializable
data class ReviewReportDto(
    val id: Long,
    val reviewOrderId: Long,
    val reporterId: Long,
    val reason: String,
    val status: String,
    val createdAt: String
)

@Serializable
data class ReportReviewRequest(val reason: String)

@Serializable
data class ResolveReviewReportRequest(val hideReview: Boolean)

@Serializable
data class SlaPolicyDto(val code: String, val minutes: Int, val compensation: Long, val isActive: Boolean)

@Serializable
data class SaveSlaPolicyRequest(val minutes: Int, val compensation: Long, val isActive: Boolean)

@Serializable
data class SlaBreachDto(
    val id: Long,
    val orderId: Long,
    val policyCode: String,
    val minutesLate: Int,
    val compensated: Boolean,
    val createdAt: String
)

@Serializable
data class AdminNoteDto(val id: Long, val adminId: Long, val targetType: String, val targetId: Long, val note: String, val createdAt: String)

@Serializable
data class AddAdminNoteRequest(val targetType: String, val targetId: Long, val note: String)

@Serializable
data class WeeklyReportDto(
    val id: Long,
    val weekStart: String,
    val orders: Int,
    val revenue: Long,
    val newUsers: Int,
    val disputes: Int
)

@Serializable
data class PlatformSummaryDto(
    val totalUsers: Int,
    val totalOrders: Int,
    val revenue: Long,
    val openDisputes: Int,
    val slaBreaches: Int,
    val flaggedReviews: Int,
    val backupVersion: String
)

@Serializable
data class ContentDto(val code: String, val title: String, val body: String)
