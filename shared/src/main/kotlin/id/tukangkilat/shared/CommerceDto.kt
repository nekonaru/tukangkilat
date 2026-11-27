package id.tukangkilat.shared

import kotlinx.serialization.Serializable

@Serializable
data class ServiceCategoryDto(val id: Long, val name: String, val icon: String, val sortOrder: Int)

@Serializable
data class ServiceBundleDto(
    val id: Long,
    val name: String,
    val description: String,
    val serviceIds: List<Long>,
    val discountPercent: Int
)

@Serializable
data class ServiceSearchDto(
    val id: Long,
    val name: String,
    val skill: String,
    val basePrice: Long,
    val visitFee: Long,
    val durationMinutes: Int,
    val categoryId: Long?,
    val ratingAvg: Double
)

@Serializable
data class EstimateRequest(
    val serviceId: Long,
    val addressId: Long,
    val scheduledDate: String,
    val slotId: Long,
    val promoCode: String? = null,
    val voucherCode: String? = null,
    val bundleId: Long? = null
)

@Serializable
data class EstimateDto(
    val serviceFee: Long,
    val visitFee: Long,
    val surgePercent: Int,
    val bundleDiscount: Long,
    val promoDiscount: Long,
    val voucherDiscount: Long,
    val tierDiscount: Long,
    val total: Long,
    val notes: List<String>
)

@Serializable
data class VoucherDto(
    val id: Long,
    val code: String,
    val amount: Long,
    val status: String,
    val expiresAt: String?,
    val createdAt: String
)

@Serializable
data class ReferralDto(
    val id: Long,
    val code: String,
    val consumerId: Long,
    val invitedUserId: Long?,
    val points: Int,
    val status: String,
    val createdAt: String
)

@Serializable
data class RedeemReferralRequest(val code: String)

@Serializable
data class InstallmentDto(
    val id: Long,
    val orderId: Long,
    val months: Int,
    val total: Long,
    val monthly: Long,
    val paidMonths: Int,
    val status: String,
    val createdAt: String
)

@Serializable
data class CreateInstallmentRequest(val orderId: Long, val months: Int)

@Serializable
data class TierDto(
    val code: String,
    val minLifetimePoints: Int,
    val discountPercent: Int,
    val priorityWeight: Int,
    val freeVisit: Boolean,
    val benefits: String,
    val current: Boolean
)

@Serializable
data class WalletSettingsDto(
    val autoTopupEnabled: Boolean,
    val autoTopupAmount: Long,
    val autoTopupThreshold: Long,
    val dailyLimit: Long,
    val cashbackPercent: Int
)

@Serializable
data class SaveWalletSettingsRequest(
    val autoTopupEnabled: Boolean,
    val autoTopupAmount: Long,
    val autoTopupThreshold: Long,
    val dailyLimit: Long,
    val cashbackPercent: Int
)

@Serializable
data class ReviewVoteRequest(val helpful: Boolean)

@Serializable
data class ReviewReplyRequest(val reply: String)

@Serializable
data class ReviewDetailDto(
    val orderId: Long,
    val orderCode: String,
    val stars: Int,
    val comment: String,
    val photos: List<String>,
    val helpfulCount: Int,
    val notHelpfulCount: Int,
    val reply: String?,
    val createdAt: String
)

@Serializable
data class SignatureRequest(val dataUrl: String, val role: String)

@Serializable
data class SignatureDto(val id: Long, val orderId: Long, val role: String, val dataUrl: String, val createdAt: String)

@Serializable
data class ChecklistItemDto(
    val id: Long,
    val title: String,
    val done: Boolean,
    val photoUrl: String,
    val note: String
)

@Serializable
data class UpdateChecklistRequest(val done: Boolean, val photoUrl: String = "", val note: String = "")

@Serializable
data class RefundRequestDto(
    val id: Long,
    val orderId: Long,
    val amount: Long,
    val reason: String,
    val status: String,
    val note: String,
    val createdAt: String
)

@Serializable
data class CreateRefundRequest(val amount: Long, val reason: String)

@Serializable
data class ResolveRefundRequest(val approve: Boolean, val amount: Long, val note: String)

@Serializable
data class PreferencesDto(
    val theme: String,
    val language: String,
    val fontScale: Int,
    val reducedMotion: Boolean,
    val quietStart: String,
    val quietEnd: String
)

@Serializable
data class SavePreferencesRequest(
    val theme: String,
    val language: String,
    val fontScale: Int,
    val reducedMotion: Boolean,
    val quietStart: String,
    val quietEnd: String
)

@Serializable
data class OfflineActionDto(val id: Long, val path: String, val payload: String, val status: String, val createdAt: String)

@Serializable
data class QueueActionRequest(val path: String, val payload: String)

@Serializable
data class WalletPaymentResultDto(val orderId: Long, val balance: Long, val cashback: Long)
