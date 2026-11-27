package id.tukangkilat.backend

import id.tukangkilat.shared.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId

/** v4 commerce: catalog, pricing, vouchers, loyalty tiers and order extras. */
data class OrderPricing(
    val serviceFee: Long,
    val visitFee: Long,
    val discount: Long,
    val total: Long,
    val voucherUsed: String?
)

object CommerceService {

    private val jakarta = ZoneId.of("Asia/Jakarta")

    // ---------- Catalog ----------

    fun categories(): List<ServiceCategoryDto> = transaction {
        ServiceCategoriesTable.selectAll().where { ServiceCategoriesTable.isActive eq true }
            .orderBy(ServiceCategoriesTable.sortOrder to SortOrder.ASC)
            .map {
                ServiceCategoryDto(
                    it[ServiceCategoriesTable.id], it[ServiceCategoriesTable.name],
                    it[ServiceCategoriesTable.icon], it[ServiceCategoriesTable.sortOrder]
                )
            }
    }

    fun bundles(): List<ServiceBundleDto> = transaction {
        ServiceBundlesTable.selectAll().where { ServiceBundlesTable.isActive eq true }
            .orderBy(ServiceBundlesTable.id to SortOrder.ASC)
            .map { row ->
                ServiceBundleDto(
                    row[ServiceBundlesTable.id], row[ServiceBundlesTable.name], row[ServiceBundlesTable.description],
                    row[ServiceBundlesTable.serviceIds].split(",").mapNotNull { it.toLongOrNull() },
                    row[ServiceBundlesTable.discountPercent]
                )
            }
    }

    fun search(
        query: String?,
        skill: String?,
        categoryId: Long?,
        maxPrice: Long?,
        minRating: Double?,
        sort: String?
    ): List<ServiceSearchDto> = transaction {
        val ratings = (RatingsTable innerJoin OrdersTable)
            .select(RatingsTable.orderId, RatingsTable.stars, OrdersTable.serviceId)
            .toList()
        val avgByService = ratings.groupBy { it[OrdersTable.serviceId] }
            .mapValues { entry -> entry.value.map { it[RatingsTable.stars] }.average() }
        val rows = ServicesTable.selectAll().where { ServicesTable.isActive eq true }
            .orderBy(ServicesTable.basePrice to SortOrder.ASC).toList()
        val filtered = rows.filter { row ->
            val name = row[ServicesTable.name]
            (query.isNullOrBlank() || name.contains(query.trim(), true)) &&
                (skill.isNullOrBlank() || row[ServicesTable.skill].equals(skill, true)) &&
                (categoryId == null || row[ServicesTable.categoryId] == categoryId) &&
                (maxPrice == null || row[ServicesTable.basePrice] + row[ServicesTable.visitFee] <= maxPrice) &&
                (minRating == null || (avgByService[row[ServicesTable.id]] ?: 0.0) >= minRating)
        }
        val sorted = when (sort) {
            "rating" -> filtered.sortedByDescending { avgByService[it[ServicesTable.id]] ?: 0.0 }
            "name" -> filtered.sortedBy { it[ServicesTable.name] }
            "price_desc" -> filtered.sortedByDescending { it[ServicesTable.basePrice] }
            else -> filtered.sortedBy { it[ServicesTable.basePrice] }
        }
        sorted.map { row ->
            ServiceSearchDto(
                row[ServicesTable.id], row[ServicesTable.name], row[ServicesTable.skill],
                row[ServicesTable.basePrice], row[ServicesTable.visitFee], row[ServicesTable.durationMinutes],
                row[ServicesTable.categoryId], round1(avgByService[row[ServicesTable.id]] ?: 0.0)
            )
        }
    }

    private fun round1(value: Double) = Math.round(value * 10) / 10.0

    // ---------- Pricing ----------

    fun estimate(userId: Long, req: EstimateRequest): EstimateDto = transaction {
        val service = ServicesTable.selectAll().where { ServicesTable.id eq req.serviceId }
            .singleOrNull() ?: throw NotFoundException("Layanan tidak ditemukan")
        var serviceFee = service[ServicesTable.basePrice]
        var visitFee = service[ServicesTable.visitFee]
        val notes = mutableListOf<String>()

        val hour = LocalTime.now(jakarta).hour
        val surge = if (service[ServicesTable.surgeEligible] && hour in 17..21) 15 else 0
        if (surge > 0) {
            serviceFee += serviceFee * surge / 100
            notes += "Surge jam sibuk 15 persen"
        }

        val bundleDiscount = if (req.bundleId != null) {
            val bundle = ServiceBundlesTable.selectAll()
                .where { ServiceBundlesTable.id eq req.bundleId!! }.singleOrNull()
            if (bundle != null && req.serviceId in bundle[ServiceBundlesTable.serviceIds]
                    .split(",").mapNotNull { it.toLongOrNull() }) {
                val amount = serviceFee * bundle[ServiceBundlesTable.discountPercent] / 100
                notes += "Diskon paket ${bundle[ServiceBundlesTable.discountPercent]} persen"
                amount
            } else 0L
        } else 0L
        serviceFee -= bundleDiscount

        var promoDiscount = 0L
        req.promoCode?.takeIf { it.isNotBlank() }?.let { code ->
            val campaign = PromoCampaignsTable.selectAll()
                .where { (PromoCampaignsTable.code eq code.trim().uppercase()) and (PromoCampaignsTable.isActive eq true) }
                .singleOrNull() ?: throw BadRequestException("Kode promo tidak valid")
            val now = OffsetDateTime.now()
            campaign[PromoCampaignsTable.startsAt]?.let { if (now.isBefore(it)) throw BadRequestException("Promo belum dimulai") }
            campaign[PromoCampaignsTable.endsAt]?.let { if (now.isAfter(it)) throw BadRequestException("Promo sudah berakhir") }
            if (campaign[PromoCampaignsTable.quota] > 0 && campaign[PromoCampaignsTable.used] >= campaign[PromoCampaignsTable.quota]) {
                throw BadRequestException("Kuota promo habis")
            }
            val subtotal = serviceFee + visitFee
            if (subtotal < campaign[PromoCampaignsTable.minTotal]) throw BadRequestException("Minimum belanja belum tercapai")
            promoDiscount = if (campaign[PromoCampaignsTable.discountType] == "PERSEN") {
                subtotal * campaign[PromoCampaignsTable.discountValue] / 100
            } else campaign[PromoCampaignsTable.discountValue]
            if (campaign[PromoCampaignsTable.maxDiscount] > 0) {
                promoDiscount = promoDiscount.coerceAtMost(campaign[PromoCampaignsTable.maxDiscount])
            }
            promoDiscount = promoDiscount.coerceAtMost(subtotal)
            notes += "Promo ${campaign[PromoCampaignsTable.code]}"
        }

        var voucherDiscount = 0L
        req.voucherCode?.takeIf { it.isNotBlank() }?.let { code ->
            val voucher = VouchersTable.selectAll().where {
                (VouchersTable.code eq code.trim().uppercase()) and (VouchersTable.consumerId eq userId)
            }.singleOrNull() ?: throw BadRequestException("Voucher tidak ditemukan")
            if (voucher[VouchersTable.status] != "AKTIF") throw BadRequestException("Voucher sudah dipakai")
            voucher[VouchersTable.expiresAt]?.let { if (OffsetDateTime.now().isAfter(it)) throw BadRequestException("Voucher kedaluwarsa") }
            voucherDiscount = voucher[VouchersTable.amount].coerceAtMost(serviceFee + visitFee - promoDiscount)
            notes += "Voucher ${voucher[VouchersTable.code]}"
        }

        val tier = currentTier(userId)
        val tierDiscount = if (tier[MembershipTiersTable.discountPercent] > 0) {
            serviceFee * tier[MembershipTiersTable.discountPercent] / 100
        } else 0L
        if (tier[MembershipTiersTable.freeVisit]) {
            notes += "Gratis biaya kunjungan member ${tier[MembershipTiersTable.code]}"
            visitFee = 0L
        }
        val total = (serviceFee + visitFee - promoDiscount - voucherDiscount - tierDiscount).coerceAtLeast(0)
        EstimateDto(
            serviceFee, visitFee, surge, bundleDiscount, promoDiscount, voucherDiscount, tierDiscount, total, notes
        )
    }

    internal fun ensureCatalog() {
        ensureTiers()
        if (!ServiceCategoriesTable.selectAll().any()) {
            listOf("Listrik" to "LISTRIK", "Pipa" to "WATER", "AC" to "AC").forEachIndexed { index, pair ->
                ServiceCategoriesTable.insert {
                    it[name] = pair.first
                    it[icon] = pair.second
                    it[sortOrder] = index + 1
                    it[isActive] = true
                }
            }
        }
        if (!ServiceBundlesTable.selectAll().any()) {
            val ids = ServicesTable.selectAll().orderBy(ServicesTable.id to SortOrder.ASC)
                .limit(2).map { it[ServicesTable.id] }
            if (ids.size == 2) {
                ServiceBundlesTable.insert {
                    it[name] = "Paket Hemat"
                    it[description] = "Dua layanan sekaligus dengan diskon"
                    it[serviceIds] = ids.joinToString(",")
                    it[discountPercent] = 10
                    it[isActive] = true
                    it[createdAt] = OffsetDateTime.now()
                }
            }
        }
    }

    /** Applies v4 pricing to a real order. Consumes a voucher when used. */
    internal fun applyOrderPricing(
        consumerId: Long, serviceId: Long, bundleId: Long?, voucherCode: String?
    ): OrderPricing = transaction {
        ensureCatalog()
        val service = ServicesTable.selectAll().where { ServicesTable.id eq serviceId }
            .singleOrNull() ?: throw NotFoundException("Layanan tidak ditemukan")
        var serviceFee = service[ServicesTable.basePrice]
        var visitFee = service[ServicesTable.visitFee]
        var discount = 0L

        val hour = LocalTime.now(jakarta).hour
        if (service[ServicesTable.surgeEligible] && hour in 17..21) {
            val surge = serviceFee * 15 / 100
            serviceFee += surge
            discount -= surge
        }
        if (bundleId != null) {
            val bundle = ServiceBundlesTable.selectAll().where { ServiceBundlesTable.id eq bundleId }.singleOrNull()
            if (bundle != null && serviceId in bundle[ServiceBundlesTable.serviceIds].split(",").mapNotNull { it.toLongOrNull() }) {
                val amount = serviceFee * bundle[ServiceBundlesTable.discountPercent] / 100
                serviceFee -= amount
                discount += amount
            }
        }
        val tier = currentTier(consumerId)
        if (tier[MembershipTiersTable.freeVisit]) {
            discount += visitFee
            visitFee = 0
        } else if (tier[MembershipTiersTable.discountPercent] > 0) {
            val amount = serviceFee * tier[MembershipTiersTable.discountPercent] / 100
            serviceFee -= amount
            discount += amount
        }

        var voucherUsed: String? = null
        voucherCode?.takeIf { it.isNotBlank() }?.let { code ->
            val voucher = VouchersTable.selectAll().where {
                (VouchersTable.code eq code.trim().uppercase()) and (VouchersTable.consumerId eq consumerId)
            }.singleOrNull() ?: throw BadRequestException("Voucher tidak ditemukan")
            if (voucher[VouchersTable.status] != "AKTIF") throw BadRequestException("Voucher sudah dipakai")
            voucher[VouchersTable.expiresAt]?.let { if (OffsetDateTime.now().isAfter(it)) throw BadRequestException("Voucher kedaluwarsa") }
            val amount = voucher[VouchersTable.amount].coerceAtMost(serviceFee + visitFee)
            discount += amount
            voucherUsed = voucher[VouchersTable.code]
            VouchersTable.update({ VouchersTable.id eq voucher[VouchersTable.id] }) {
                it[status] = "DIPAKAI"
                it[usedAt] = OffsetDateTime.now()
            }
        }
        OrderPricing(serviceFee, visitFee, discount, (serviceFee + visitFee).coerceAtLeast(0), voucherUsed)
    }

    internal fun assertOrderParty(userId: Long, orderId: Long) {
        val row = OrderQueries.loadOrderRow(orderId)
        if (row[OrdersTable.consumerId] != userId && row[OrdersTable.technicianId] != userId) {
            throw ForbiddenException("Anda bukan peserta pesanan ini")
        }
    }

    private fun currentTier(userId: Long): ResultRow {
        ensureTiers()
        val lifetime = LoyaltyTable.selectAll().where { LoyaltyTable.userId eq userId }
            .singleOrNull()?.get(LoyaltyTable.lifetime)?.toInt() ?: 0
        return MembershipTiersTable.selectAll()
            .where { MembershipTiersTable.minLifetimePoints lessEq lifetime }
            .orderBy(MembershipTiersTable.minLifetimePoints to SortOrder.DESC)
            .limit(1).singleOrNull()
            ?: MembershipTiersTable.selectAll()
                .where { MembershipTiersTable.code eq "SILVER" }.single()
    }

    // ---------- Loyalty, vouchers, referral ----------

    fun tiers(userId: Long): List<TierDto> = transaction {
        ensureTiers()
        val currentCode = currentTier(userId)[MembershipTiersTable.code]
        MembershipTiersTable.selectAll().orderBy(MembershipTiersTable.minLifetimePoints to SortOrder.ASC).map {
            TierDto(
                it[MembershipTiersTable.code], it[MembershipTiersTable.minLifetimePoints],
                it[MembershipTiersTable.discountPercent], it[MembershipTiersTable.priorityWeight],
                it[MembershipTiersTable.freeVisit], it[MembershipTiersTable.benefits],
                it[MembershipTiersTable.code] == currentCode
            )
        }
    }

    internal fun ensureTiers() {
        if (MembershipTiersTable.selectAll().any()) return
        listOf(
            Triple("SILVER", 0, 0),
            Triple("GOLD", 500, 5),
            Triple("PLATINUM", 2000, 10)
        ).forEach { (code, min, discount) ->
            MembershipTiersTable.insert {
                it[MembershipTiersTable.code] = code
                it[minLifetimePoints] = min
                it[discountPercent] = discount
                it[priorityWeight] = if (code == "PLATINUM") 20 else if (code == "GOLD") 10 else 0
                it[freeVisit] = code == "PLATINUM"
                it[benefits] = if (code == "SILVER") "Dukungan standar" else if (code == "GOLD") "Diskon 5 persen dan prioritas" else "Diskon 10 persen, gratis kunjungan, prioritas tertinggi"
            }
        }
    }

    fun vouchers(userId: Long): List<VoucherDto> = transaction {
        VouchersTable.selectAll().where { VouchersTable.consumerId eq userId }
            .orderBy(VouchersTable.createdAt to SortOrder.DESC).map(::voucherDto)
    }

    private fun voucherDto(row: ResultRow) = VoucherDto(
        row[VouchersTable.id], row[VouchersTable.code], row[VouchersTable.amount], row[VouchersTable.status],
        row[VouchersTable.expiresAt]?.toString(), row[VouchersTable.createdAt].toString()
    )

    fun issueVoucher(userId: Long, amount: Long): VoucherDto = transaction {
        if (amount <= 0) throw BadRequestException("Nominal harus lebih dari 0")
        val code = "VCR" + System.currentTimeMillis().toString().takeLast(8)
        val id = VouchersTable.insert {
            it[VouchersTable.code] = code
            it[consumerId] = userId
            it[VouchersTable.amount] = amount
            it[status] = "AKTIF"
            it[expiresAt] = OffsetDateTime.now().plusDays(30)
            it[createdAt] = OffsetDateTime.now()
        } get VouchersTable.id
        voucherDto(VouchersTable.selectAll().where { VouchersTable.id eq id }.single())
    }

    fun referrals(userId: Long): List<ReferralDto> = transaction {
        val mine = ReferralsTable.selectAll().where { ReferralsTable.consumerId eq userId }
            .orderBy(ReferralsTable.createdAt to SortOrder.DESC).map(::referralDto)
        if (mine.isEmpty()) {
            val code = "TK" + userId.toString().padStart(4, '0').takeLast(4) + System.currentTimeMillis().toString().takeLast(4)
            ReferralsTable.insert {
                it[ReferralsTable.code] = code
                it[consumerId] = userId
                it[points] = 0
                it[status] = "AKTIF"
                it[createdAt] = OffsetDateTime.now()
            }
        }
        ReferralsTable.selectAll().where { ReferralsTable.consumerId eq userId }
            .orderBy(ReferralsTable.createdAt to SortOrder.DESC).map(::referralDto)
    }

    private fun referralDto(row: ResultRow) = ReferralDto(
        row[ReferralsTable.id], row[ReferralsTable.code], row[ReferralsTable.consumerId],
        row[ReferralsTable.invitedUserId], row[ReferralsTable.points], row[ReferralsTable.status],
        row[ReferralsTable.createdAt].toString()
    )

    fun redeemReferral(userId: Long, code: String): ReferralDto = transaction {
        val referral = ReferralsTable.selectAll().where {
            (ReferralsTable.code eq code.trim().uppercase()) and (ReferralsTable.consumerId neq userId)
        }.singleOrNull() ?: throw BadRequestException("Kode referral tidak valid")
        if (referral[ReferralsTable.invitedUserId] != null) throw ConflictException("Kode referral sudah dipakai")
        if (ReferralsTable.selectAll().where { ReferralsTable.invitedUserId eq userId }.any()) {
            throw ConflictException("Anda sudah memakai kode referral")
        }
        val inviter = referral[ReferralsTable.consumerId]
        ReferralsTable.update({ ReferralsTable.id eq referral[ReferralsTable.id] }) {
            it[invitedUserId] = userId
            it[points] = 100
            it[status] = "BERHASIL"
        }
        listOf(inviter, userId).forEach { id ->
            val row = LoyaltyTable.selectAll().where { LoyaltyTable.userId eq id }.singleOrNull()
            if (row == null) {
                LoyaltyTable.insert {
                    it[LoyaltyTable.userId] = id
                    it[points] = 100
                    it[lifetime] = 100
                }
            } else {
                LoyaltyTable.update({ LoyaltyTable.userId eq id }) {
                    it[points] = row[LoyaltyTable.points] + 100
                    it[lifetime] = row[LoyaltyTable.lifetime] + 100
                }
            }
        }
        referralDto(ReferralsTable.selectAll().where { ReferralsTable.id eq referral[ReferralsTable.id] }.single())
    }

    // ---------- Installments ----------

    fun installments(userId: Long): List<InstallmentDto> = transaction {
        InstallmentPlansTable.selectAll().where { InstallmentPlansTable.consumerId eq userId }
            .orderBy(InstallmentPlansTable.createdAt to SortOrder.DESC).map(::installmentDto)
    }

    private fun installmentDto(row: ResultRow) = InstallmentDto(
        row[InstallmentPlansTable.id], row[InstallmentPlansTable.orderId], row[InstallmentPlansTable.months],
        row[InstallmentPlansTable.total], row[InstallmentPlansTable.monthly], row[InstallmentPlansTable.paidMonths],
        row[InstallmentPlansTable.status], row[InstallmentPlansTable.createdAt].toString()
    )

    fun createInstallment(userId: Long, req: CreateInstallmentRequest): InstallmentDto = transaction {
        if (req.months !in listOf(3, 6)) throw BadRequestException("Pilih 3 atau 6 bulan")
        val order = OrderQueries.loadOrderRow(req.orderId)
        if (order[OrdersTable.consumerId] != userId) throw ForbiddenException("Bukan pesanan Anda")
        if (order[OrdersTable.total] < 500_000) throw BadRequestException("Minimum cicilan Rp500.000")
        if (order[OrdersTable.paymentStatus] != PaymentStatus.LUNAS.name) throw BadRequestException("Pesanan harus lunas")
        if (InstallmentPlansTable.selectAll().where { InstallmentPlansTable.orderId eq req.orderId }.any()) {
            throw ConflictException("Cicilan untuk pesanan ini sudah ada")
        }
        val total = order[OrdersTable.total]
        val monthly = (total + req.months - 1) / req.months
        val id = InstallmentPlansTable.insert {
            it[orderId] = req.orderId
            it[consumerId] = userId
            it[months] = req.months
            it[InstallmentPlansTable.total] = total
            it[InstallmentPlansTable.monthly] = monthly
            it[paidMonths] = 0
            it[status] = "AKTIF"
            it[createdAt] = OffsetDateTime.now()
        } get InstallmentPlansTable.id
        installmentDto(InstallmentPlansTable.selectAll().where { InstallmentPlansTable.id eq id }.single())
    }

    fun payInstallment(userId: Long, id: Long): InstallmentDto = transaction {
        val row = InstallmentPlansTable.selectAll().where {
            (InstallmentPlansTable.id eq id) and (InstallmentPlansTable.consumerId eq userId)
        }.singleOrNull() ?: throw NotFoundException("Cicilan tidak ditemukan")
        if (row[InstallmentPlansTable.status] != "AKTIF") throw ConflictException("Cicilan sudah lunas")
        val paid = row[InstallmentPlansTable.paidMonths] + 1
        InstallmentPlansTable.update({ InstallmentPlansTable.id eq id }) {
            it[paidMonths] = paid
            it[status] = if (paid >= row[InstallmentPlansTable.months]) "LUNAS" else "AKTIF"
        }
        installmentDto(InstallmentPlansTable.selectAll().where { InstallmentPlansTable.id eq id }.single())
    }

    // ---------- Wallet settings and payment ----------

    fun walletSettings(userId: Long): WalletSettingsDto = transaction {
        val row = WalletSettingsTable.selectAll().where { WalletSettingsTable.userId eq userId }
            .singleOrNull()
        if (row == null) {
            WalletSettingsDto(false, 100_000, 50_000, 500_000, 0)
        } else {
            WalletSettingsDto(
                row[WalletSettingsTable.autoTopupEnabled], row[WalletSettingsTable.autoTopupAmount],
                row[WalletSettingsTable.autoTopupThreshold], row[WalletSettingsTable.dailyLimit],
                row[WalletSettingsTable.cashbackPercent]
            )
        }
    }

    fun saveWalletSettings(userId: Long, req: SaveWalletSettingsRequest) = transaction {
        if (req.autoTopupAmount <= 0 || req.dailyLimit <= 0 || req.cashbackPercent !in 0..20) {
            throw BadRequestException("Pengaturan dompet tidak valid")
        }
        val exists = WalletSettingsTable.selectAll().where { WalletSettingsTable.userId eq userId }.any()
        if (exists) {
            WalletSettingsTable.update({ WalletSettingsTable.userId eq userId }) {
                it[autoTopupEnabled] = req.autoTopupEnabled
                it[autoTopupAmount] = req.autoTopupAmount
                it[autoTopupThreshold] = req.autoTopupThreshold
                it[dailyLimit] = req.dailyLimit
                it[cashbackPercent] = req.cashbackPercent
            }
        } else {
            WalletSettingsTable.insert {
                it[WalletSettingsTable.userId] = userId
                it[autoTopupEnabled] = req.autoTopupEnabled
                it[autoTopupAmount] = req.autoTopupAmount
                it[autoTopupThreshold] = req.autoTopupThreshold
                it[dailyLimit] = req.dailyLimit
                it[cashbackPercent] = req.cashbackPercent
            }
        }
    }

    fun payWithWallet(userId: Long, orderId: Long): WalletPaymentResultDto = transaction {
        val order = OrderQueries.loadOrderRow(orderId)
        if (order[OrdersTable.consumerId] != userId) throw ForbiddenException("Bukan pesanan Anda")
        if (OrderStatus.valueOf(order[OrdersTable.status]) != OrderStatus.MENUNGGU_PEMBAYARAN) {
            throw ConflictException("Pesanan tidak menunggu pembayaran")
        }
        val settings = walletSettings(userId)
        val total = order[OrdersTable.total]
        val todayStart = LocalDate.now(jakarta).atStartOfDay().atZone(jakarta).toOffsetDateTime()
        val spentToday = WalletTransactionsTable.selectAll().where {
            (WalletTransactionsTable.userId eq userId) and
                (WalletTransactionsTable.type eq "BAYAR") and
                (WalletTransactionsTable.createdAt greaterEq todayStart)
        }.sumOf { kotlin.math.abs(it[WalletTransactionsTable.amount]) }
        if (spentToday + total > settings.dailyLimit) throw BadRequestException("Melebihi batas harian dompet")
        val walletRow = WalletsTable.selectAll().where { WalletsTable.userId eq userId }.singleOrNull()
            ?: throw BadRequestException("Dompet belum tersedia")
        var balance = walletRow[WalletsTable.balance]
        if (balance < total) throw BadRequestException("Saldo dompet tidak cukup")
        balance -= total
        val cashback = total * settings.cashbackPercent / 100
        balance += cashback
        WalletsTable.update({ WalletsTable.userId eq userId }) {
            it[WalletsTable.balance] = balance
            it[updatedAt] = OffsetDateTime.now()
        }
        WalletTransactionsTable.insert {
            it[WalletTransactionsTable.userId] = userId
            it[amount] = -total
            it[type] = "BAYAR"
            it[reference] = order[OrdersTable.code]
            it[createdAt] = OffsetDateTime.now()
        }
        if (cashback > 0) {
            WalletTransactionsTable.insert {
                it[this.userId] = userId
                it[amount] = cashback
                it[type] = "CASHBACK"
                it[reference] = order[OrdersTable.code]
                it[createdAt] = OffsetDateTime.now()
            }
        }
        if (settings.autoTopupEnabled && balance < settings.autoTopupThreshold) {
            balance += settings.autoTopupAmount
            WalletsTable.update({ WalletsTable.userId eq userId }) {
                it[WalletsTable.balance] = balance
                it[updatedAt] = OffsetDateTime.now()
            }
            WalletTransactionsTable.insert {
                it[this.userId] = userId
                it[amount] = settings.autoTopupAmount
                it[type] = "AUTO_TOPUP"
                it[reference] = "SISTEM"
                it[createdAt] = OffsetDateTime.now()
            }
        }
        val now = OffsetDateTime.now()
        PaymentsTable.insert {
            it[PaymentsTable.orderId] = orderId
            it[method] = PaymentMethod.DOMPET_DIGITAL.name
            it[status] = PaymentStatus.LUNAS.name
            it[amount] = total
            it[paidAt] = now
            it[createdAt] = now
        }
        OrdersTable.update({ OrdersTable.id eq orderId }) {
            it[paymentMethod] = PaymentMethod.DOMPET_DIGITAL.name
            it[paymentStatus] = PaymentStatus.LUNAS.name
            it[updatedAt] = now
        }
        OrderService.transitionInTx(orderId, OrderStatus.MENCARI_TEKNISI, userId, OrderStateMachine.Actor.KONSUMEN)
        EcosystemService.addLoyaltyForOrder(userId, orderId)
        WalletPaymentResultDto(orderId, balance, cashback)
    }

    // ---------- Subscriptions ----------

    fun pauseSubscription(userId: Long, id: Long) = transaction {
        val updated = SubscriptionsTable.update({
            (SubscriptionsTable.id eq id) and (SubscriptionsTable.consumerId eq userId)
        }) { it[status] = "DIJEDA" }
        if (updated == 0) throw NotFoundException("Langganan tidak ditemukan")
    }

    fun resumeSubscription(userId: Long, id: Long) = transaction {
        val updated = SubscriptionsTable.update({
            (SubscriptionsTable.id eq id) and (SubscriptionsTable.consumerId eq userId) and
                (SubscriptionsTable.status eq "DIJEDA")
        }) { it[status] = "AKTIF" }
        if (updated == 0) throw NotFoundException("Langganan jeda tidak ditemukan")
    }

    fun skipSubscription(userId: Long, id: Long) = transaction {
        val row = SubscriptionsTable.selectAll().where {
            (SubscriptionsTable.id eq id) and (SubscriptionsTable.consumerId eq userId)
        }.singleOrNull() ?: throw NotFoundException("Langganan tidak ditemukan")
        if (row[SubscriptionsTable.status] != "AKTIF") throw ConflictException("Hanya langganan aktif yang bisa dilewati")
        SubscriptionsTable.update({ SubscriptionsTable.id eq id }) {
            it[nextDate] = LocalDate.parse(row[SubscriptionsTable.nextDate].toString())
                .plusDays(row[SubscriptionsTable.intervalDays].toLong())
        }
    }

    // ---------- Reviews ----------

    fun reviewDetails(technicianId: Long): List<ReviewDetailDto> = transaction {
        val rows = (RatingsTable innerJoin OrdersTable).selectAll()
            .where { OrdersTable.technicianId eq technicianId }
            .orderBy(RatingsTable.createdAt to SortOrder.DESC).toList()
        rows.map { row ->
            val orderId = row[RatingsTable.orderId]
            val votes = ReviewVotesTable.selectAll().where { ReviewVotesTable.reviewOrderId eq orderId }.toList()
            val photos = ReviewPhotosTable.selectAll().where { ReviewPhotosTable.orderId eq orderId }
                .map { it[ReviewPhotosTable.photoUrl] }
            val reply = TechnicianRepliesTable.selectAll()
                .where { TechnicianRepliesTable.reviewOrderId eq orderId }
                .singleOrNull()?.get(TechnicianRepliesTable.reply)
            ReviewDetailDto(
                orderId, row[OrdersTable.code], row[RatingsTable.stars], row[RatingsTable.comment], photos,
                votes.count { it[ReviewVotesTable.helpful] }, votes.count { !it[ReviewVotesTable.helpful] },
                reply, row[RatingsTable.createdAt].toString()
            )
        }
    }

    fun voteReview(userId: Long, orderId: Long, helpful: Boolean) = transaction {
        assertOrderParty(userId, orderId)
        val exists = ReviewVotesTable.selectAll().where {
            (ReviewVotesTable.reviewOrderId eq orderId) and (ReviewVotesTable.userId eq userId)
        }.any()
        if (exists) {
            ReviewVotesTable.update({
                (ReviewVotesTable.reviewOrderId eq orderId) and (ReviewVotesTable.userId eq userId)
            }) { it[ReviewVotesTable.helpful] = helpful }
        } else {
            ReviewVotesTable.insert {
                it[ReviewVotesTable.reviewOrderId] = orderId
                it[ReviewVotesTable.userId] = userId
                it[ReviewVotesTable.helpful] = helpful
                it[createdAt] = OffsetDateTime.now()
            }
        }
    }

    fun replyReview(technicianId: Long, orderId: Long, reply: String): ReviewDetailDto = transaction {
        val order = OrderQueries.loadOrderRow(orderId)
        if (order[OrdersTable.technicianId] != technicianId) throw ForbiddenException("Bukan ulasan pekerjaan Anda")
        if (reply.isBlank() || reply.length > 500) throw BadRequestException("Balasan 1 sampai 500 karakter")
        val exists = TechnicianRepliesTable.selectAll()
            .where { TechnicianRepliesTable.reviewOrderId eq orderId }.any()
        if (exists) {
            TechnicianRepliesTable.update({ TechnicianRepliesTable.reviewOrderId eq orderId }) {
                it[TechnicianRepliesTable.reply] = reply.trim()
                it[createdAt] = OffsetDateTime.now()
            }
        } else {
            TechnicianRepliesTable.insert {
                it[reviewOrderId] = orderId
                it[TechnicianRepliesTable.reply] = reply.trim()
                it[createdAt] = OffsetDateTime.now()
            }
        }
        reviewDetails(technicianId).first { it.orderId == orderId }
    }

    // ---------- Signatures and checklist ----------

    fun saveSignature(userId: Long, orderId: Long, ignoredRole: String, dataUrl: String): SignatureDto = transaction {
        if (dataUrl.isBlank() || dataUrl.length > 200_000) throw BadRequestException("Tanda tangan tidak valid")
        val order = OrderQueries.loadOrderRow(orderId)
        val role = UsersTable.selectAll().where { UsersTable.id eq userId }
            .singleOrNull()?.get(UsersTable.role) ?: throw ForbiddenException("Pengguna tidak ditemukan")
        if (role != Role.KONSUMEN.name && role != Role.TUKANG.name) throw ForbiddenException("Peran tidak didukung")
        if ((role == Role.KONSUMEN.name && order[OrdersTable.consumerId] != userId) ||
            (role == Role.TUKANG.name && order[OrdersTable.technicianId] != userId)) {
            throw ForbiddenException("Bukan pesanan Anda")
        }
        val exists = SignaturesTable.selectAll().where {
            (SignaturesTable.orderId eq orderId) and (SignaturesTable.role eq role)
        }.any()
        val id = if (exists) {
            SignaturesTable.update({
                (SignaturesTable.orderId eq orderId) and (SignaturesTable.role eq role)
            }) { it[SignaturesTable.dataUrl] = dataUrl }
            SignaturesTable.selectAll().where {
                (SignaturesTable.orderId eq orderId) and (SignaturesTable.role eq role)
            }.single()[SignaturesTable.id]
        } else {
            SignaturesTable.insert {
                it[SignaturesTable.orderId] = orderId
                it[signerId] = userId
                it[SignaturesTable.role] = role
                it[SignaturesTable.dataUrl] = dataUrl
                it[createdAt] = OffsetDateTime.now()
            } get SignaturesTable.id
        }
        signatureDto(SignaturesTable.selectAll().where { SignaturesTable.id eq id }.single())
    }

    private fun signatureDto(row: ResultRow) = SignatureDto(
        row[SignaturesTable.id], row[SignaturesTable.orderId], row[SignaturesTable.role],
        row[SignaturesTable.dataUrl], row[SignaturesTable.createdAt].toString()
    )

    fun signatures(userId: Long, orderId: Long): List<SignatureDto> = transaction {
        assertOrderParty(userId, orderId)
        SignaturesTable.selectAll().where { SignaturesTable.orderId eq orderId }
            .orderBy(SignaturesTable.createdAt to SortOrder.ASC).map(::signatureDto)
    }

    fun checklist(userId: Long, orderId: Long): List<ChecklistItemDto> = transaction {
        assertOrderParty(userId, orderId)
        val order = OrderQueries.loadOrderRow(orderId)
        val templates = ChecklistTemplatesTable.selectAll()
            .where { ChecklistTemplatesTable.serviceId eq order[OrdersTable.serviceId] }
            .orderBy(ChecklistTemplatesTable.stepOrder to SortOrder.ASC).toList()
        if (templates.isEmpty()) {
            ChecklistTemplatesTable.insert {
                it[serviceId] = order[OrdersTable.serviceId]
                it[title] = "Periksa kondisi awal"
                it[stepOrder] = 1
            }
            ChecklistTemplatesTable.insert {
                it[serviceId] = order[OrdersTable.serviceId]
                it[title] = "Kerjakan layanan"
                it[stepOrder] = 2
            }
            ChecklistTemplatesTable.insert {
                it[serviceId] = order[OrdersTable.serviceId]
                it[title] = "Bersihkan area kerja"
                it[stepOrder] = 3
            }
        }
        val currentTemplates = ChecklistTemplatesTable.selectAll()
            .where { ChecklistTemplatesTable.serviceId eq order[OrdersTable.serviceId] }
            .orderBy(ChecklistTemplatesTable.stepOrder to SortOrder.ASC).toList()
        currentTemplates.forEach { template ->
            if (!ChecklistItemsTable.selectAll().where {
                    (ChecklistItemsTable.orderId eq orderId) and
                        (ChecklistItemsTable.templateId eq template[ChecklistTemplatesTable.id])
                }.any()) {
                ChecklistItemsTable.insert {
                    it[ChecklistItemsTable.orderId] = orderId
                    it[templateId] = template[ChecklistTemplatesTable.id]
                    it[done] = false
                    it[photoUrl] = ""
                    it[note] = ""
                    it[updatedAt] = OffsetDateTime.now()
                }
            }
        }
        (ChecklistItemsTable innerJoin ChecklistTemplatesTable).selectAll()
            .where { ChecklistItemsTable.orderId eq orderId }
            .orderBy(ChecklistTemplatesTable.stepOrder to SortOrder.ASC)
            .map {
                ChecklistItemDto(
                    it[ChecklistItemsTable.id], it[ChecklistTemplatesTable.title],
                    it[ChecklistItemsTable.done], it[ChecklistItemsTable.photoUrl], it[ChecklistItemsTable.note]
                )
            }
    }

    fun updateChecklistItem(userId: Long, orderId: Long, id: Long, req: UpdateChecklistRequest): ChecklistItemDto = transaction {
        assertOrderParty(userId, orderId)
        val row = ChecklistItemsTable.selectAll().where {
            (ChecklistItemsTable.id eq id) and (ChecklistItemsTable.orderId eq orderId)
        }.singleOrNull() ?: throw NotFoundException("Langkah tidak ditemukan")
        if (req.photoUrl.length > 512 || req.note.length > 255) throw BadRequestException("Data terlalu panjang")
        ChecklistItemsTable.update({ ChecklistItemsTable.id eq id }) {
            it[done] = req.done
            it[photoUrl] = req.photoUrl
            it[note] = req.note
            it[updatedAt] = OffsetDateTime.now()
        }
        val updated = ChecklistItemsTable.selectAll().where { ChecklistItemsTable.id eq id }.single()
        val title = ChecklistTemplatesTable.selectAll()
            .where { ChecklistTemplatesTable.id eq row[ChecklistItemsTable.templateId] }
            .single()[ChecklistTemplatesTable.title]
        ChecklistItemDto(id, title, updated[ChecklistItemsTable.done], updated[ChecklistItemsTable.photoUrl], updated[ChecklistItemsTable.note])
    }

    // ---------- Refunds ----------

    fun refunds(consumerId: Long): List<RefundRequestDto> = transaction {
        RefundRequestsTable.selectAll().where { RefundRequestsTable.consumerId eq consumerId }
            .orderBy(RefundRequestsTable.createdAt to SortOrder.DESC).map(::refundDto)
    }

    private fun refundDto(row: ResultRow) = RefundRequestDto(
        row[RefundRequestsTable.id], row[RefundRequestsTable.orderId], row[RefundRequestsTable.amount],
        row[RefundRequestsTable.reason], row[RefundRequestsTable.status], row[RefundRequestsTable.note],
        row[RefundRequestsTable.createdAt].toString()
    )

    fun createRefund(userId: Long, orderId: Long, req: CreateRefundRequest): RefundRequestDto = transaction {
        val order = OrderQueries.loadOrderRow(orderId)
        if (order[OrdersTable.consumerId] != userId) throw ForbiddenException("Bukan pesanan Anda")
        if (order[OrdersTable.total] <= 0) throw BadRequestException("Nominal tidak valid")
        val amount = req.amount.coerceIn(1, order[OrdersTable.total])
        if (req.reason.isBlank()) throw BadRequestException("Alasan wajib diisi")
        if (RefundRequestsTable.selectAll().where { RefundRequestsTable.orderId eq orderId }.any()) {
            throw ConflictException("Pengajuan refund sudah dibuat")
        }
        val id = RefundRequestsTable.insert {
            it[RefundRequestsTable.orderId] = orderId
            it[consumerId] = userId
            it[RefundRequestsTable.amount] = amount
            it[reason] = req.reason.trim().take(500)
            it[status] = "MENUNGGU"
            it[note] = ""
            it[createdAt] = OffsetDateTime.now()
        } get RefundRequestsTable.id
        refundDto(RefundRequestsTable.selectAll().where { RefundRequestsTable.id eq id }.single())
    }

    fun adminRefunds(): List<RefundRequestDto> = transaction {
        RefundRequestsTable.selectAll().orderBy(RefundRequestsTable.createdAt to SortOrder.DESC)
            .limit(100).map(::refundDto)
    }

    fun resolveRefund(adminId: Long, id: Long, req: ResolveRefundRequest): RefundRequestDto = transaction {
        val row = RefundRequestsTable.selectAll().where { RefundRequestsTable.id eq id }
            .singleOrNull() ?: throw NotFoundException("Pengajuan tidak ditemukan")
        if (row[RefundRequestsTable.status] != "MENUNGGU") throw ConflictException("Pengajuan sudah diproses")
        RefundRequestsTable.update({ RefundRequestsTable.id eq id }) {
            it[status] = if (req.approve) "DISETUJUI" else "DITOLAK"
            it[amount] = if (req.approve) req.amount.coerceAtLeast(0) else 0
            it[note] = req.note.take(255)
            it[resolvedAt] = OffsetDateTime.now()
        }
        if (req.approve) {
            val amount = req.amount.coerceIn(0, row[RefundRequestsTable.amount])
            val wallet = WalletsTable.selectAll().where { WalletsTable.userId eq row[RefundRequestsTable.consumerId] }.singleOrNull()
            if (wallet != null) {
                WalletsTable.update({ WalletsTable.userId eq row[RefundRequestsTable.consumerId] }) {
                    it[balance] = wallet[WalletsTable.balance] + amount
                    it[updatedAt] = OffsetDateTime.now()
                }
                WalletTransactionsTable.insert {
                    it[WalletTransactionsTable.userId] = row[RefundRequestsTable.consumerId]
                    it[WalletTransactionsTable.amount] = amount
                    it[WalletTransactionsTable.type] = "REFUND"
                    it[WalletTransactionsTable.reference] = row[RefundRequestsTable.orderId].toString()
                    it[WalletTransactionsTable.createdAt] = OffsetDateTime.now()
                }
            }
            val orderId = row[RefundRequestsTable.orderId]
            PaymentsTable.update({
                (PaymentsTable.orderId eq orderId) and (PaymentsTable.status eq PaymentStatus.LUNAS.name)
            }) { it[status] = PaymentStatus.KEMBALI.name }
            OrdersTable.update({ OrdersTable.id eq orderId }) {
                it[paymentStatus] = PaymentStatus.KEMBALI.name
            }
        }
        EcosystemService.log(adminId, "RESOLVE_REFUND", id.toString())
        refundDto(RefundRequestsTable.selectAll().where { RefundRequestsTable.id eq id }.single())
    }

    // ---------- Preferences and offline queue ----------

    fun preferences(userId: Long): PreferencesDto = transaction {
        val row = UserPreferencesTable.selectAll().where { UserPreferencesTable.userId eq userId }.singleOrNull()
        if (row == null) PreferencesDto("SISTEM", "ID", 100, false, "", "")
        else PreferencesDto(
            row[UserPreferencesTable.theme], row[UserPreferencesTable.language], row[UserPreferencesTable.fontScale],
            row[UserPreferencesTable.reducedMotion], row[UserPreferencesTable.quietStart], row[UserPreferencesTable.quietEnd]
        )
    }

    fun savePreferences(userId: Long, req: SavePreferencesRequest) = transaction {
        if (req.theme !in listOf("SISTEM", "TERANG", "GELAP")) throw BadRequestException("Tema tidak valid")
        if (req.language !in listOf("ID", "EN")) throw BadRequestException("Bahasa tidak valid")
        if (req.fontScale !in 85..130) throw BadRequestException("Ukuran teks harus 85 sampai 130")
        val exists = UserPreferencesTable.selectAll().where { UserPreferencesTable.userId eq userId }.any()
        if (exists) {
            UserPreferencesTable.update({ UserPreferencesTable.userId eq userId }) {
                it[theme] = req.theme
                it[language] = req.language
                it[fontScale] = req.fontScale
                it[reducedMotion] = req.reducedMotion
                it[quietStart] = req.quietStart
                it[quietEnd] = req.quietEnd
                it[updatedAt] = OffsetDateTime.now()
            }
        } else {
            UserPreferencesTable.insert {
                it[UserPreferencesTable.userId] = userId
                it[theme] = req.theme
                it[language] = req.language
                it[fontScale] = req.fontScale
                it[reducedMotion] = req.reducedMotion
                it[quietStart] = req.quietStart
                it[quietEnd] = req.quietEnd
                it[updatedAt] = OffsetDateTime.now()
            }
        }
    }

    fun queue(userId: Long): List<OfflineActionDto> = transaction {
        OfflineQueueTable.selectAll().where { OfflineQueueTable.userId eq userId }
            .orderBy(OfflineQueueTable.createdAt to SortOrder.DESC).limit(50).map {
                OfflineActionDto(
                    it[OfflineQueueTable.id], it[OfflineQueueTable.path], it[OfflineQueueTable.payload],
                    it[OfflineQueueTable.status], it[OfflineQueueTable.createdAt].toString()
                )
            }
    }

    fun enqueue(userId: Long, req: QueueActionRequest): OfflineActionDto = transaction {
        if (req.path.isBlank() || req.payload.length > 4096) throw BadRequestException("Antrean tidak valid")
        val id = OfflineQueueTable.insert {
            it[OfflineQueueTable.userId] = userId
            it[path] = req.path.trim().take(255)
            it[payload] = req.payload
            it[status] = "MENUNGGU"
            it[createdAt] = OffsetDateTime.now()
        } get OfflineQueueTable.id
        val row = OfflineQueueTable.selectAll().where { OfflineQueueTable.id eq id }.single()
        OfflineActionDto(id, row[OfflineQueueTable.path], row[OfflineQueueTable.payload], row[OfflineQueueTable.status], row[OfflineQueueTable.createdAt].toString())
    }

    fun clearQueue(userId: Long) = transaction {
        OfflineQueueTable.deleteWhere { with(SqlExpressionBuilder) { OfflineQueueTable.userId eq userId } }
    }
}
