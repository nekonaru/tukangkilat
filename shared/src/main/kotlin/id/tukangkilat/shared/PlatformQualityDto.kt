package id.tukangkilat.shared

import kotlinx.serialization.Serializable

@Serializable
data class SyncOperationDto(
    val id: Long,
    val entity: String,
    val entityId: String,
    val operation: String,
    val version: Int,
    val status: String,
    val conflict: Boolean,
    val createdAt: String
)

@Serializable
data class QueueSyncRequest(val entity: String, val entityId: String, val operation: String, val payload: String, val version: Int = 1)

@Serializable
data class SyncResultDto(val processed: Int, val conflicts: Int)

@Serializable
data class ChatKeyDto(val id: Long, val publicKey: String, val fingerprint: String, val active: Boolean, val createdAt: String)

@Serializable
data class GenerateKeyRequest(val publicKey: String)

@Serializable
data class RateLimitDto(val code: String, val maxCalls: Int, val windowSeconds: Int, val enabled: Boolean)

@Serializable
data class SaveRateLimitRequest(val maxCalls: Int, val windowSeconds: Int, val enabled: Boolean)

@Serializable
data class ApiVersionDto(val version: String, val status: String, val note: String, val releasedAt: String)

@Serializable
data class SaveApiVersionRequest(val status: String, val note: String)

@Serializable
data class WidgetConfigDto(val widgetType: String, val enabled: Boolean, val position: Int)

@Serializable
data class SaveWidgetRequest(val widgetType: String, val enabled: Boolean, val position: Int)

@Serializable
data class ScheduledJobDto(val id: Long, val code: String, val name: String, val intervalMs: Long, val enabled: Boolean, val lastRun: String?)

@Serializable
data class SaveJobRequest(val code: String, val name: String, val intervalMs: Long, val enabled: Boolean)
