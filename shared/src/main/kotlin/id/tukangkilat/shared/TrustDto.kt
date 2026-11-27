package id.tukangkilat.shared

import kotlinx.serialization.Serializable

@Serializable
data class TrustedDeviceDto(
    val id: Long,
    val deviceName: String,
    val deviceId: String,
    val trusted: Boolean,
    val createdAt: String,
    val lastSeen: String
)

@Serializable
data class RegisterDeviceRequest(val deviceName: String, val deviceId: String, val trusted: Boolean = true)

@Serializable
data class SessionDto(
    val id: Long,
    val deviceName: String,
    val ipAddress: String,
    val startedAt: String,
    val lastActive: String,
    val revoked: Boolean
)

@Serializable
data class ConsentDto(val kind: String, val granted: Boolean, val updatedAt: String)

@Serializable
data class SaveConsentRequest(val kind: String, val granted: Boolean)

@Serializable
data class BlockDto(val id: Long, val blockedId: Long, val createdAt: String)

@Serializable
data class SosAlertDto(
    val id: Long,
    val userId: Long,
    val orderId: Long?,
    val lat: Double,
    val lng: Double,
    val note: String,
    val status: String,
    val createdAt: String
)

@Serializable
data class CreateSosRequest(val orderId: Long? = null, val lat: Double, val lng: Double, val note: String = "")

@Serializable
data class AppLockDto(val enabled: Boolean, val failedCount: Int, val lockedUntil: String?)

@Serializable
data class SetPinRequest(val pin: String, val enabled: Boolean)

@Serializable
data class VerifyPinRequest(val pin: String)

@Serializable
data class TwoFactorDto(val enabled: Boolean, val otpCode: String, val otpExpires: String?)

@Serializable
data class LoginAlertDto(
    val id: Long,
    val deviceName: String,
    val ipAddress: String,
    val createdAt: String,
    val isRead: Boolean
)

@Serializable
data class PrivacySnapshotDto(
    val orders: Int,
    val addresses: Int,
    val reviews: Int,
    val messages: Int,
    val walletTransactions: Int,
    val devices: Int,
    val exportedAt: String
)

@Serializable
data class SubadminDto(val id: Long, val adminId: Long, val roleName: String, val permissions: String)

@Serializable
data class SaveSubadminRequest(val adminId: Long, val roleName: String, val permissions: String)

@Serializable
data class IncidentDto(
    val id: Long,
    val code: String,
    val title: String,
    val description: String,
    val severity: String,
    val status: String,
    val runbook: String,
    val createdAt: String
)

@Serializable
data class CreateIncidentRequest(val title: String, val description: String, val severity: String, val runbook: String)

@Serializable
data class ResolveIncidentRequest(val note: String)

@Serializable
data class RetentionDto(val code: String, val tableName: String, val days: Int, val enabled: Boolean)

@Serializable
data class SaveRetentionRequest(val tableName: String, val days: Int, val enabled: Boolean)

@Serializable
data class BackupJobDto(
    val id: Long,
    val name: String,
    val frequency: String,
    val enabled: Boolean,
    val lastRun: String?,
    val nextRun: String?
)

@Serializable
data class SaveBackupJobRequest(val name: String, val frequency: String, val enabled: Boolean)

@Serializable
data class SafeVisitDto(val orderId: Long, val codeWord: String, val verifiedByConsumer: Boolean, val verifiedByTechnician: Boolean)
