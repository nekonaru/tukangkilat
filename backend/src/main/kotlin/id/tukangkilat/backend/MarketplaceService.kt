package id.tukangkilat.backend

import id.tukangkilat.shared.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime

/** v5 marketplace: bundles, flash sales, group buying, bids, gifting and wishlists. */
object MarketplaceService {

    // ---------- Custom bundles ----------

    fun bundles(userId: Long): List<CustomBundleDto> = transaction {
        CustomBundlesTable.selectAll().where { CustomBundlesTable.consumerId eq userId }
            .orderBy(CustomBundlesTable.createdAt to SortOrder.DESC).map {
                CustomBundleDto(
                    it[CustomBundlesTable.id], it[CustomBundlesTable.name],
                    it[CustomBundlesTable.serviceIds].split(",").mapNotNull { id -> id.toLongOrNull() },
                    it[CustomBundlesTable.totalPrice], it[CustomBundlesTable.discount]
                )
            }
    }

    fun createBundle(userId: Long, req: CreateBundleRequest): CustomBundleDto = transaction {
        if (req.name.isBlank() || req.serviceIds.size !in 2..5) throw BadRequestException("Pilih 2 sampai 5 layanan")
        val ids = req.serviceIds.distinct()
        val rows = ServicesTable.selectAll().where { ServicesTable.id inList ids }.toList()
        if (rows.size != ids.size) throw BadRequestException("Ada layanan yang tidak ditemukan")
        val subtotal = rows.sumOf { it[ServicesTable.basePrice] + it[ServicesTable.visitFee] }
        val discount = subtotal * 10 / 100
        val id = CustomBundlesTable.insert {
            it[consumerId] = userId
            it[name] = req.name.trim().take(120)
            it[serviceIds] = ids.joinToString(",")
            it[totalPrice] = subtotal - discount
            it[this.discount] = discount
            it[createdAt] = OffsetDateTime.now()
        } get CustomBundlesTable.id
        bundles(userId).first { it.id == id }
    }

    // ---------- Flash sales ----------

    fun flashSales(): List<FlashSaleDto> = transaction {
        val now = OffsetDateTime.now()
        FlashSalesTable.selectAll().where {
            (FlashSalesTable.isActive eq true) and
                (FlashSalesTable.startsAt lessEq now) and
                (FlashSalesTable.endsAt greaterEq now)
        }.orderBy(FlashSalesTable.endsAt to SortOrder.ASC).map {
            FlashSaleDto(
                it[FlashSalesTable.id], it[FlashSalesTable.serviceId], it[FlashSalesTable.discountPercent],
                (it[FlashSalesTable.quota] - it[FlashSalesTable.used]).coerceAtLeast(0),
                it[FlashSalesTable.endsAt].toString()
            )
        }
    }

    fun createFlashSale(serviceId: Long, discountPercent: Int, quota: Int, hours: Int): FlashSaleDto = transaction {
        if (discountPercent !in 1..90 || quota <= 0 || hours <= 0) throw BadRequestException("Data promo tidak valid")
        if (!ServicesTable.selectAll().where { ServicesTable.id eq serviceId }.any()) throw NotFoundException("Layanan tidak ditemukan")
        val now = OffsetDateTime.now()
        val id = FlashSalesTable.insert {
            it[this.serviceId] = serviceId
            it[this.discountPercent] = discountPercent
            it[this.quota] = quota
            it[used] = 0
            it[startsAt] = now
            it[endsAt] = now.plusHours(hours.toLong())
            it[isActive] = true
            it[createdAt] = now
        } get FlashSalesTable.id
        flashSales().first { it.id == id }
    }

    // ---------- Group buys ----------

    fun groupBuys(): List<GroupBuyDto> = transaction {
        GroupBuysTable.selectAll().where { GroupBuysTable.status eq "OPEN" }
            .orderBy(GroupBuysTable.createdAt to SortOrder.DESC).map(::groupDto)
    }

    private fun groupDto(row: ResultRow) = GroupBuyDto(
        row[GroupBuysTable.id], row[GroupBuysTable.serviceId], row[GroupBuysTable.areaName],
        row[GroupBuysTable.targetCount], row[GroupBuysTable.joinedCount], row[GroupBuysTable.status]
    )

    fun createGroupBuy(userId: Long, req: CreateGroupBuyRequest): GroupBuyDto = transaction {
        if (req.areaName.isBlank() || req.targetCount !in 2..50) throw BadRequestException("Data beli bersama tidak valid")
        val id = GroupBuysTable.insert {
            it[consumerId] = userId
            it[serviceId] = req.serviceId
            it[areaName] = req.areaName.trim().take(120)
            it[targetCount] = req.targetCount
            it[joinedCount] = 1
            it[status] = "OPEN"
            it[createdAt] = OffsetDateTime.now()
        } get GroupBuysTable.id
        groupBuys().first { it.id == id }
    }

    fun joinGroupBuy(id: Long): GroupBuyDto = transaction {
        val row = GroupBuysTable.selectAll().where { GroupBuysTable.id eq id }
            .singleOrNull() ?: throw NotFoundException("Kelompok tidak ditemukan")
        if (row[GroupBuysTable.status] != "OPEN") throw ConflictException("Kelompok sudah ditutup")
        val joined = row[GroupBuysTable.joinedCount] + 1
        GroupBuysTable.update({ GroupBuysTable.id eq id }) {
            it[joinedCount] = joined
            it[status] = if (joined >= row[GroupBuysTable.targetCount]) "TERCAPAI" else "OPEN"
        }
        groupDto(GroupBuysTable.selectAll().where { GroupBuysTable.id eq id }.single())
    }

    // ---------- Bids ----------

    fun bids(orderId: Long): List<BidDto> = transaction {
        JobBidsTable.selectAll().where { JobBidsTable.orderId eq orderId }
            .orderBy(JobBidsTable.price to SortOrder.ASC).map(::bidDto)
    }

    private fun bidDto(row: ResultRow) = BidDto(
        row[JobBidsTable.id], row[JobBidsTable.orderId], row[JobBidsTable.technicianId],
        row[JobBidsTable.price], row[JobBidsTable.note], row[JobBidsTable.status]
    )

    fun placeBid(technicianId: Long, req: PlaceBidRequest): BidDto = transaction {
        if (req.price <= 0) throw BadRequestException("Harga harus lebih dari 0")
        val order = OrderQueries.loadOrderRow(req.orderId)
        if (OrderStatus.valueOf(order[OrdersTable.status]) != OrderStatus.MENCARI_TEKNISI) {
            throw ConflictException("Pesanan tidak menerima penawaran")
        }
        val existing = JobBidsTable.selectAll().where {
            (JobBidsTable.orderId eq req.orderId) and (JobBidsTable.technicianId eq technicianId)
        }.singleOrNull()
        val id = if (existing != null) {
            JobBidsTable.update({ JobBidsTable.id eq existing[JobBidsTable.id] }) {
                it[price] = req.price
                it[note] = req.note.trim().take(255)
            }
            existing[JobBidsTable.id]
        } else {
            JobBidsTable.insert {
                it[orderId] = req.orderId
                it[this.technicianId] = technicianId
                it[price] = req.price
                it[note] = req.note.trim().take(255)
                it[status] = "MENUNGGU"
                it[createdAt] = OffsetDateTime.now()
            } get JobBidsTable.id
        }
        bidDto(JobBidsTable.selectAll().where { JobBidsTable.id eq id }.single())
    }

    fun resolveBid(consumerId: Long, bidId: Long, accept: Boolean): BidDto = transaction {
        val bid = JobBidsTable.selectAll().where { JobBidsTable.id eq bidId }
            .singleOrNull() ?: throw NotFoundException("Penawaran tidak ditemukan")
        val order = OrderQueries.loadOrderRow(bid[JobBidsTable.orderId])
        if (order[OrdersTable.consumerId] != consumerId) throw ForbiddenException("Bukan pesanan Anda")
        if (bid[JobBidsTable.status] != "MENUNGGU") throw ConflictException("Penawaran sudah diproses")
        if (accept) {
            JobBidsTable.update({ JobBidsTable.id eq bidId }) { it[status] = "DITERIMA" }
            JobBidsTable.update({
                (JobBidsTable.orderId eq bid[JobBidsTable.orderId]) and (JobBidsTable.id neq bidId)
            }) { it[status] = "DITOLAK" }
            OrdersTable.update({ OrdersTable.id eq bid[JobBidsTable.orderId] }) {
                it[technicianId] = bid[JobBidsTable.technicianId]
                it[total] = bid[JobBidsTable.price]
                it[status] = OrderStatus.TEKNISI_DITEMUKAN.name
                it[updatedAt] = OffsetDateTime.now()
            }
            OrderEventsTable.insert {
                it[orderId] = bid[JobBidsTable.orderId]
                it[status] = OrderStatus.TEKNISI_DITEMUKAN.name
                it[actorId] = consumerId
                it[createdAt] = OffsetDateTime.now()
            }
        } else {
            JobBidsTable.update({ JobBidsTable.id eq bidId }) { it[status] = "DITOLAK" }
        }
        bidDto(JobBidsTable.selectAll().where { JobBidsTable.id eq bidId }.single())
    }

    // ---------- Gifts and points ----------

    fun giftCards(userId: Long): List<GiftCardDto> = transaction {
        GiftCardsTable.selectAll().where {
            (GiftCardsTable.senderId eq userId) or (GiftCardsTable.receiverId eq userId)
        }.orderBy(GiftCardsTable.createdAt to SortOrder.DESC).map(::giftDto)
    }

    private fun giftDto(row: ResultRow) = GiftCardDto(
        row[GiftCardsTable.id], row[GiftCardsTable.code], row[GiftCardsTable.amount],
        row[GiftCardsTable.status], row[GiftCardsTable.receiverId], row[GiftCardsTable.createdAt].toString()
    )

    fun createGiftCard(userId: Long, amount: Long): GiftCardDto = transaction {
        if (amount <= 0) throw BadRequestException("Nominal harus lebih dari 0")
        val code = "GIFT" + System.currentTimeMillis().toString().takeLast(8)
        val id = GiftCardsTable.insert {
            it[this.code] = code
            it[senderId] = userId
            it[this.amount] = amount
            it[status] = "AKTIF"
            it[createdAt] = OffsetDateTime.now()
        } get GiftCardsTable.id
        giftDto(GiftCardsTable.selectAll().where { GiftCardsTable.id eq id }.single())
    }

    fun redeemGift(userId: Long, code: String): GiftCardDto = transaction {
        val card = GiftCardsTable.selectAll().where {
            (GiftCardsTable.code eq code.trim().uppercase()) and (GiftCardsTable.status eq "AKTIF")
        }.singleOrNull() ?: throw BadRequestException("Kartu hadiah tidak valid")
        if (card[GiftCardsTable.senderId] == userId) throw BadRequestException("Tidak bisa memakai kartu sendiri")
        val wallet = WalletsTable.selectAll().where { WalletsTable.userId eq userId }.singleOrNull()
        if (wallet != null) {
            WalletsTable.update({ WalletsTable.userId eq userId }) {
                it[balance] = wallet[WalletsTable.balance] + card[GiftCardsTable.amount]
                it[updatedAt] = OffsetDateTime.now()
            }
            WalletTransactionsTable.insert {
                it[this.userId] = userId
                it[amount] = card[GiftCardsTable.amount]
                it[type] = "HADIAH"
                it[reference] = card[GiftCardsTable.code]
                it[createdAt] = OffsetDateTime.now()
            }
        }
        GiftCardsTable.update({ GiftCardsTable.id eq card[GiftCardsTable.id] }) {
            it[receiverId] = userId
            it[status] = "DIPAKAI"
        }
        giftDto(GiftCardsTable.selectAll().where { GiftCardsTable.id eq card[GiftCardsTable.id] }.single())
    }

    fun pointItems(): List<PointItemDto> = transaction {
        if (!PointShopItemsTable.selectAll().any()) {
            listOf(
                Triple("VCR10", "Voucher Rp10.000", 100),
                Triple("ONGKIR", "Gratis ongkiran", 150),
                Triple("PRIORITAS", "Prioritas dispatch", 250)
            ).forEach { (code, name, points) ->
                PointShopItemsTable.insert {
                    it[PointShopItemsTable.code] = code
                    it[this.name] = name
                    it[this.points] = points
                    it[description] = "Penukaran poin loyalitas"
                }
            }
        }
        PointShopItemsTable.selectAll().orderBy(PointShopItemsTable.points to SortOrder.ASC).map {
            PointItemDto(it[PointShopItemsTable.code], it[PointShopItemsTable.name], it[PointShopItemsTable.points], it[PointShopItemsTable.description])
        }
    }

    fun redeemPoint(userId: Long, itemCode: String): PointRedemptionDto = transaction {
        val item = PointShopItemsTable.selectAll().where { PointShopItemsTable.code eq itemCode }
            .singleOrNull() ?: throw NotFoundException("Item tidak ditemukan")
        val loyalty = LoyaltyTable.selectAll().where { LoyaltyTable.userId eq userId }
            .singleOrNull() ?: throw BadRequestException("Poin belum tersedia")
        if (loyalty[LoyaltyTable.points] < item[PointShopItemsTable.points]) throw BadRequestException("Poin tidak cukup")
        LoyaltyTable.update({ LoyaltyTable.userId eq userId }) {
            it[points] = loyalty[LoyaltyTable.points] - item[PointShopItemsTable.points]
        }
        val id = PointRedemptionsTable.insert {
            it[this.userId] = userId
            it[PointRedemptionsTable.itemCode] = item[PointShopItemsTable.code]
            it[points] = item[PointShopItemsTable.points]
            it[createdAt] = OffsetDateTime.now()
        } get PointRedemptionsTable.id
        PointRedemptionDto(id, item[PointShopItemsTable.code], item[PointShopItemsTable.points])
    }

    // ---------- Tips ----------

    fun tips(orderId: Long): TipDto? = transaction {
        TipsTable.selectAll().where { TipsTable.orderId eq orderId }
            .singleOrNull()?.let {
                TipDto(it[TipsTable.id], orderId, it[TipsTable.amount], it[TipsTable.note], it[TipsTable.createdAt].toString())
            }
    }

    fun createTip(userId: Long, req: CreateTipRequest): TipDto = transaction {
        if (req.amount <= 0) throw BadRequestException("Nominal tip harus lebih dari 0")
        val order = OrderQueries.loadOrderRow(req.orderId)
        if (order[OrdersTable.consumerId] != userId) throw ForbiddenException("Bukan pesanan Anda")
        val technicianId = order[OrdersTable.technicianId] ?: throw BadRequestException("Belum ada teknisi")
        if (TipsTable.selectAll().where { TipsTable.orderId eq req.orderId }.any()) throw ConflictException("Tip sudah diberikan")
        val id = TipsTable.insert {
            it[orderId] = req.orderId
            it[consumerId] = userId
            it[this.technicianId] = technicianId
            it[amount] = req.amount
            it[note] = req.note.trim().take(255)
            it[createdAt] = OffsetDateTime.now()
        } get TipsTable.id
        tips(req.orderId)!!
    }

    // ---------- Wishlist and price alerts ----------

    fun wishlists(userId: Long): List<WishlistDto> = transaction {
        WishlistsTable.selectAll().where { WishlistsTable.consumerId eq userId }
            .orderBy(WishlistsTable.createdAt to SortOrder.DESC).map {
                WishlistDto(
                    it[WishlistsTable.id], it[WishlistsTable.serviceId], it[WishlistsTable.targetPrice],
                    currentPrice(it[WishlistsTable.serviceId])
                )
            }
    }

    private fun currentPrice(serviceId: Long): Long = transaction {
        ServicesTable.selectAll().where { ServicesTable.id eq serviceId }
            .singleOrNull()?.get(ServicesTable.basePrice) ?: 0L
    }

    fun addWishlist(userId: Long, req: AddWishlistRequest): WishlistDto = transaction {
        if (req.targetPrice <= 0) throw BadRequestException("Harga target tidak valid")
        val existing = WishlistsTable.selectAll().where {
            (WishlistsTable.consumerId eq userId) and (WishlistsTable.serviceId eq req.serviceId)
        }.singleOrNull()
        val id = if (existing != null) {
            WishlistsTable.update({ WishlistsTable.id eq existing[WishlistsTable.id] }) {
                it[targetPrice] = req.targetPrice
            }
            existing[WishlistsTable.id]
        } else {
            WishlistsTable.insert {
                it[consumerId] = userId
                it[serviceId] = req.serviceId
                it[targetPrice] = req.targetPrice
                it[createdAt] = OffsetDateTime.now()
            } get WishlistsTable.id
        }
        wishlists(userId).first { it.id == id }
    }

    fun removeWishlist(userId: Long, id: Long) = transaction {
        val deleted = WishlistsTable.deleteWhere {
            with(SqlExpressionBuilder) {
                (WishlistsTable.id eq id) and (WishlistsTable.consumerId eq userId)
            }
        }
        if (deleted == 0) throw NotFoundException("Wishlist tidak ditemukan")
    }

    fun priceAlerts(userId: Long): List<PriceAlertDto> = transaction {
        PriceAlertsTable.selectAll().where { PriceAlertsTable.consumerId eq userId }
            .orderBy(PriceAlertsTable.createdAt to SortOrder.DESC).map {
                PriceAlertDto(it[PriceAlertsTable.id], it[PriceAlertsTable.serviceId], it[PriceAlertsTable.targetPrice], it[PriceAlertsTable.triggered])
            }
    }

    fun addPriceAlert(userId: Long, req: AddPriceAlertRequest): PriceAlertDto = transaction {
        if (req.targetPrice <= 0) throw BadRequestException("Harga target tidak valid")
        val id = PriceAlertsTable.insert {
            it[consumerId] = userId
            it[serviceId] = req.serviceId
            it[targetPrice] = req.targetPrice
            it[triggered] = false
            it[createdAt] = OffsetDateTime.now()
        } get PriceAlertsTable.id
        priceAlerts(userId).first { it.id == id }
    }

    fun evaluatePriceAlerts(): Int = transaction {
        var count = 0
        PriceAlertsTable.selectAll().where { PriceAlertsTable.triggered eq false }.forEach { alert ->
            val price = currentPrice(alert[PriceAlertsTable.serviceId])
            if (price > 0 && price <= alert[PriceAlertsTable.targetPrice]) {
                PriceAlertsTable.update({ PriceAlertsTable.id eq alert[PriceAlertsTable.id] }) { it[triggered] = true }
                count++
            }
        }
        count
    }

    fun priceHistory(serviceId: Long): List<PricePointDto> = transaction {
        PriceHistoryTable.selectAll().where { PriceHistoryTable.serviceId eq serviceId }
            .orderBy(PriceHistoryTable.recordedAt to SortOrder.DESC).limit(30).map {
                PricePointDto(it[PriceHistoryTable.price], it[PriceHistoryTable.recordedAt].toString())
            }
    }

    fun recordPrice(serviceId: Long) = transaction {
        val price = currentPrice(serviceId)
        PriceHistoryTable.insert {
            it[this.serviceId] = serviceId
            it[this.price] = price
            it[recordedAt] = OffsetDateTime.now()
        }
    }

    // ---------- Admin commerce ----------

    fun summary(): MarketSummaryDto = transaction {
        val now = OffsetDateTime.now()
        MarketSummaryDto(
            flashSales = FlashSalesTable.selectAll().where {
                (FlashSalesTable.isActive eq true) and (FlashSalesTable.endsAt greaterEq now)
            }.count().toInt(),
            openGroupBuys = GroupBuysTable.selectAll().where { GroupBuysTable.status eq "OPEN" }.count().toInt(),
            pendingBids = JobBidsTable.selectAll().where { JobBidsTable.status eq "MENUNGGU" }.count().toInt(),
            activeWishlists = WishlistsTable.selectAll().count().toInt(),
            triggeredAlerts = PriceAlertsTable.selectAll().where { PriceAlertsTable.triggered eq true }.count().toInt(),
            ledgerBalance = LedgerEntriesTable.selectAll().sumOf { it[LedgerEntriesTable.credit] - it[LedgerEntriesTable.debit] }
        )
    }

    fun ledger(): List<LedgerEntryDto> = transaction {
        LedgerEntriesTable.selectAll().orderBy(LedgerEntriesTable.createdAt to SortOrder.DESC).limit(100).map {
            LedgerEntryDto(
                it[LedgerEntriesTable.id], it[LedgerEntriesTable.code], it[LedgerEntriesTable.account],
                it[LedgerEntriesTable.debit], it[LedgerEntriesTable.credit], it[LedgerEntriesTable.reference],
                it[LedgerEntriesTable.createdAt].toString()
            )
        }
    }

    fun addLedger(req: AddLedgerRequest): LedgerEntryDto = transaction {
        if (req.account.isBlank() || (req.debit <= 0 && req.credit <= 0)) throw BadRequestException("Data buku besar tidak valid")
        val code = "LED" + System.currentTimeMillis().toString().takeLast(9)
        val id = LedgerEntriesTable.insert {
            it[this.code] = code
            it[account] = req.account.trim().take(64)
            it[debit] = req.debit.coerceAtLeast(0)
            it[credit] = req.credit.coerceAtLeast(0)
            it[reference] = req.reference.trim().take(128)
            it[createdAt] = OffsetDateTime.now()
        } get LedgerEntriesTable.id
        ledger().first { it.id == id }
    }

    fun commissionRules(): List<CommissionRuleDto> = transaction {
        CommissionRulesTable.selectAll().orderBy(CommissionRulesTable.priority to SortOrder.DESC).map {
            CommissionRuleDto(
                it[CommissionRulesTable.code], it[CommissionRulesTable.serviceId],
                it[CommissionRulesTable.percent], it[CommissionRulesTable.priority], it[CommissionRulesTable.isActive]
            )
        }
    }

    fun saveCommission(code: String, req: SaveCommissionRequest): CommissionRuleDto = transaction {
        if (req.percent !in 0..50) throw BadRequestException("Komisi harus 0 sampai 50 persen")
        val exists = CommissionRulesTable.selectAll().where { CommissionRulesTable.code eq code }.any()
        if (exists) {
            CommissionRulesTable.update({ CommissionRulesTable.code eq code }) {
                it[serviceId] = req.serviceId
                it[percent] = req.percent
                it[priority] = req.priority
                it[isActive] = req.isActive
                it[updatedAt] = OffsetDateTime.now()
            }
        } else {
            CommissionRulesTable.insert {
                it[CommissionRulesTable.code] = code.trim().take(48)
                it[serviceId] = req.serviceId
                it[percent] = req.percent
                it[priority] = req.priority
                it[isActive] = req.isActive
                it[updatedAt] = OffsetDateTime.now()
            }
        }
        commissionRules().first { it.code == code.trim().take(48) }
    }
}
