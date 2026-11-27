package id.tukangkilat.shared

import kotlinx.serialization.Serializable

@Serializable
data class CustomBundleDto(val id: Long, val name: String, val serviceIds: List<Long>, val totalPrice: Long, val discount: Long)

@Serializable
data class CreateBundleRequest(val name: String, val serviceIds: List<Long>)

@Serializable
data class FlashSaleDto(val id: Long, val serviceId: Long, val discountPercent: Int, val remaining: Int, val endsAt: String)

@Serializable
data class GroupBuyDto(
    val id: Long,
    val serviceId: Long,
    val areaName: String,
    val targetCount: Int,
    val joinedCount: Int,
    val status: String
)

@Serializable
data class CreateGroupBuyRequest(val serviceId: Long, val areaName: String, val targetCount: Int)

@Serializable
data class BidDto(val id: Long, val orderId: Long, val technicianId: Long, val price: Long, val note: String, val status: String)

@Serializable
data class PlaceBidRequest(val orderId: Long, val price: Long, val note: String = "")

@Serializable
data class ResolveBidRequest(val accept: Boolean)

@Serializable
data class GiftCardDto(val id: Long, val code: String, val amount: Long, val status: String, val receiverId: Long?, val createdAt: String)

@Serializable
data class CreateGiftCardRequest(val amount: Long)

@Serializable
data class RedeemGiftRequest(val code: String)

@Serializable
data class PointItemDto(val code: String, val name: String, val points: Int, val description: String)

@Serializable
data class RedeemPointRequest(val itemCode: String)

@Serializable
data class PointRedemptionDto(val id: Long, val itemCode: String, val points: Int)

@Serializable
data class TipDto(val id: Long, val orderId: Long, val amount: Long, val note: String, val createdAt: String)

@Serializable
data class CreateTipRequest(val orderId: Long, val amount: Long, val note: String = "")

@Serializable
data class WishlistDto(val id: Long, val serviceId: Long, val targetPrice: Long, val currentPrice: Long)

@Serializable
data class AddWishlistRequest(val serviceId: Long, val targetPrice: Long)

@Serializable
data class PriceAlertDto(val id: Long, val serviceId: Long, val targetPrice: Long, val triggered: Boolean)

@Serializable
data class AddPriceAlertRequest(val serviceId: Long, val targetPrice: Long)

@Serializable
data class PricePointDto(val price: Long, val recordedAt: String)

@Serializable
data class MarketSummaryDto(
    val flashSales: Int,
    val openGroupBuys: Int,
    val pendingBids: Int,
    val activeWishlists: Int,
    val triggeredAlerts: Int,
    val ledgerBalance: Long
)

@Serializable
data class LedgerEntryDto(val id: Long, val code: String, val account: String, val debit: Long, val credit: Long, val reference: String, val createdAt: String)

@Serializable
data class AddLedgerRequest(val account: String, val debit: Long, val credit: Long, val reference: String)

@Serializable
data class CommissionRuleDto(val code: String, val serviceId: Long?, val percent: Int, val priority: Int, val isActive: Boolean)

@Serializable
data class SaveCommissionRequest(val serviceId: Long?, val percent: Int, val priority: Int, val isActive: Boolean)
