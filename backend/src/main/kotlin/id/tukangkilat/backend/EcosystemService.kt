package id.tukangkilat.backend

import id.tukangkilat.shared.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.time.OffsetDateTime

/** Services for the v3 ecosystem: wallet, loyalty, subscriptions and technician workbench. */
object EcosystemService {

    private fun ensureWallet(userId: Long) {
        if (WalletsTable.selectAll().where { WalletsTable.userId eq userId }.any()) return
        WalletsTable.insert { it[this.userId] = userId; it[balance] = 0L; it[updatedAt] = OffsetDateTime.now() }
    }

    private fun ensureLoyalty(userId: Long) {
        if (LoyaltyTable.selectAll().where { LoyaltyTable.userId eq userId }.any()) return
        LoyaltyTable.insert { it[this.userId] = userId; it[points] = 0; it[lifetime] = 0 }
    }

    fun wallet(userId: Long): WalletDto = transaction {
        ensureWallet(userId)
        val balance = WalletsTable.selectAll().where { WalletsTable.userId eq userId }
            .single()[WalletsTable.balance]
        val tx = WalletTransactionsTable.selectAll().where { WalletTransactionsTable.userId eq userId }
            .orderBy(WalletTransactionsTable.createdAt to SortOrder.DESC).limit(50).map { row ->
                WalletTransactionDto(
                    id = row[WalletTransactionsTable.id],
                    amount = row[WalletTransactionsTable.amount],
                    type = row[WalletTransactionsTable.type],
                    reference = row[WalletTransactionsTable.reference],
                    createdAt = row[WalletTransactionsTable.createdAt].toString()
                )
            }
        WalletDto(balance, tx)
    }

    fun topUp(userId: Long, amount: Long): WalletDto {
        if (amount <= 0) throw BadRequestException("Nominal harus lebih dari 0")
        transaction {
            ensureWallet(userId)
            WalletsTable.update({ WalletsTable.userId eq userId }) {
                with(SqlExpressionBuilder) { it[balance] = balance + amount }
                it[updatedAt] = OffsetDateTime.now()
            }
            WalletTransactionsTable.insert {
                it[this.userId] = userId
                it[this.amount] = amount
                it[type] = "TOP_UP"
                it[reference] = "TOP-" + System.currentTimeMillis().toString().takeLast(8)
                it[createdAt] = OffsetDateTime.now()
            }
        }
        return wallet(userId)
    }

    fun loyalty(userId: Long): LoyaltyDto = transaction {
        ensureLoyalty(userId)
        val row = LoyaltyTable.selectAll().where { LoyaltyTable.userId eq userId }.single()
        LoyaltyDto(row[LoyaltyTable.points], row[LoyaltyTable.lifetime])
    }

    fun redeem(userId: Long, points: Int): LoyaltyDto {
        if (points <= 0) throw BadRequestException("Poin tidak valid")
        transaction {
            ensureLoyalty(userId)
            val current = LoyaltyTable.selectAll().where { LoyaltyTable.userId eq userId }.single()[LoyaltyTable.points]
            if (points > current) throw ConflictException("Poin tidak cukup")
            LoyaltyTable.update({ LoyaltyTable.userId eq userId }) {
                it[LoyaltyTable.points] = current - points
            }
        }
        return loyalty(userId)
    }

    fun addLoyaltyForOrder(userId: Long, orderId: Long) = transaction {
        ensureLoyalty(userId)
        val earned = 10
        LoyaltyTable.update({ LoyaltyTable.userId eq userId }) {
            with(SqlExpressionBuilder) {
                it[points] = points + earned
                it[lifetime] = lifetime + earned
            }
        }
        WalletTransactionsTable.insert {
            it[this.userId] = userId
            it[amount] = 0L
            it[type] = "POIN-$orderId"
            it[reference] = "POIN-$earned"
            it[createdAt] = OffsetDateTime.now()
        }
    }

    fun subscriptions(userId: Long): List<SubscriptionDto> = transaction {
        SubscriptionsTable.selectAll().where { SubscriptionsTable.consumerId eq userId }
            .orderBy(SubscriptionsTable.nextDate to SortOrder.ASC).map { row ->
                val service = ServicesTable.selectAll().where { ServicesTable.id eq row[SubscriptionsTable.serviceId] }
                    .singleOrNull()?.get(ServicesTable.name) ?: "Layanan"
                val address = AddressesTable.selectAll().where { AddressesTable.id eq row[SubscriptionsTable.addressId] }
                    .singleOrNull()?.get(AddressesTable.label) ?: "Alamat"
                SubscriptionDto(
                    id = row[SubscriptionsTable.id],
                    serviceId = row[SubscriptionsTable.serviceId],
                    serviceName = service,
                    addressId = row[SubscriptionsTable.addressId],
                    addressLabel = address,
                    intervalDays = row[SubscriptionsTable.intervalDays],
                    nextDate = row[SubscriptionsTable.nextDate].toString(),
                    status = row[SubscriptionsTable.status]
                )
            }
    }

    fun addSubscription(userId: Long, req: CreateSubscriptionRequest): SubscriptionDto {
        if (req.intervalDays <= 0) throw BadRequestException("Interval harus lebih dari 0")
        val start = try { LocalDate.parse(req.startDate) } catch (e: Exception) { throw BadRequestException("Format tanggal tidak valid") }
        if (start.isBefore(LocalDate.now())) throw BadRequestException("Tanggal tidak boleh lampau")
        transaction {
            ServicesTable.selectAll().where { (ServicesTable.id eq req.serviceId) and (ServicesTable.isActive eq true) }
                .singleOrNull() ?: throw NotFoundException("Layanan tidak ditemukan")
            AddressesTable.selectAll().where { (AddressesTable.id eq req.addressId) and (AddressesTable.userId eq userId) }
                .singleOrNull() ?: throw NotFoundException("Alamat tidak ditemukan")
            SubscriptionsTable.insert {
                it[consumerId] = userId
                it[serviceId] = req.serviceId
                it[addressId] = req.addressId
                it[intervalDays] = req.intervalDays
                it[nextDate] = start
                it[status] = "AKTIF"
                it[createdAt] = OffsetDateTime.now()
            }
        }
        return subscriptions(userId).last()
    }

    fun cancelSubscription(userId: Long, id: Long) = transaction {
        val updated = SubscriptionsTable.update({
            (SubscriptionsTable.id eq id) and (SubscriptionsTable.consumerId eq userId)
        }) { it[status] = "DIBATALKAN" }
        if (updated == 0) throw NotFoundException("Langganan tidak ditemukan")
    }

    fun processDueSubscriptions(): Int {
        val due = transaction {
            SubscriptionsTable.selectAll().where {
                (SubscriptionsTable.status eq "AKTIF") and (SubscriptionsTable.nextDate lessEq LocalDate.now())
            }.map { it[SubscriptionsTable.id] to it[SubscriptionsTable.consumerId] }
        }
        var created = 0
        for ((id, consumerId) in due) {
            try {
                val row = transaction {
                    SubscriptionsTable.selectAll().where { SubscriptionsTable.id eq id }.single()
                }
                val slot = transaction { TimeSlotsTable.selectAll().orderBy(TimeSlotsTable.id).limit(1).singleOrNull()?.get(TimeSlotsTable.id) }
                    ?: continue
                OrderService.createOrder(consumerId, CreateOrderRequest(
                    serviceId = row[SubscriptionsTable.serviceId],
                    description = "Pesanan otomatis dari langganan",
                    addressId = row[SubscriptionsTable.addressId],
                    scheduledDate = LocalDate.now().plusDays(1).toString(),
                    slotId = slot
                ))
                transaction {
                    SubscriptionsTable.update({ SubscriptionsTable.id eq id }) {
                        it[nextDate] = LocalDate.now().plusDays(row[SubscriptionsTable.intervalDays].toLong())
                    }
                }
                created++
            } catch (_: Exception) {}
        }
        return created
    }

    fun blocks(technicianId: Long): List<AvailabilityBlockDto> = transaction {
        AvailabilityBlocksTable.selectAll().where { AvailabilityBlocksTable.technicianId eq technicianId }
            .orderBy(AvailabilityBlocksTable.blockDate to SortOrder.ASC).map {
                AvailabilityBlockDto(it[AvailabilityBlocksTable.id], it[AvailabilityBlocksTable.blockDate].toString(), it[AvailabilityBlocksTable.slotLabel])
            }
    }

    fun addBlock(technicianId: Long, req: CreateAvailabilityBlockRequest): AvailabilityBlockDto {
        val date = try { LocalDate.parse(req.date) } catch (e: Exception) { throw BadRequestException("Format tanggal tidak valid") }
        transaction {
            AvailabilityBlocksTable.insert {
                it[this.technicianId] = technicianId
                it[blockDate] = date
                it[slotLabel] = req.slotLabel
                it[createdAt] = OffsetDateTime.now()
            }
        }
        return blocks(technicianId).last()
    }

    fun deleteBlock(technicianId: Long, id: Long) = transaction {
        AvailabilityBlocksTable.deleteWhere {
            with(SqlExpressionBuilder) { (AvailabilityBlocksTable.id eq id) and (AvailabilityBlocksTable.technicianId eq technicianId) }
        }
    }

    fun portfolio(technicianId: Long): List<PortfolioDto> = transaction {
        PortfoliosTable.selectAll().where { PortfoliosTable.technicianId eq technicianId }
            .orderBy(PortfoliosTable.createdAt to SortOrder.DESC).map {
                PortfolioDto(it[PortfoliosTable.id], it[PortfoliosTable.photoUrl], it[PortfoliosTable.caption])
            }
    }

    fun addPortfolio(technicianId: Long, req: AddPortfolioRequest): PortfolioDto {
        if (req.photoUrl.isBlank()) throw BadRequestException("Foto wajib diisi")
        transaction {
            PortfoliosTable.insert {
                it[this.technicianId] = technicianId
                it[photoUrl] = req.photoUrl
                it[caption] = req.caption
                it[createdAt] = OffsetDateTime.now()
            }
        }
        return portfolio(technicianId).first()
    }

    fun deletePortfolio(technicianId: Long, id: Long) = transaction {
        PortfoliosTable.deleteWhere {
            with(SqlExpressionBuilder) { (PortfoliosTable.id eq id) and (PortfoliosTable.technicianId eq technicianId) }
        }
    }

    fun payoutAccount(technicianId: Long): PayoutAccountDto? = transaction {
        PayoutAccountsTable.selectAll().where { PayoutAccountsTable.technicianId eq technicianId }
            .singleOrNull()?.let {
                PayoutAccountDto(it[PayoutAccountsTable.bank], it[PayoutAccountsTable.accountNo], it[PayoutAccountsTable.accountName])
            }
    }

    fun savePayoutAccount(technicianId: Long, req: SavePayoutAccountRequest) = transaction {
        if (PayoutAccountsTable.selectAll().where { PayoutAccountsTable.technicianId eq technicianId }.any()) {
            PayoutAccountsTable.update({ PayoutAccountsTable.technicianId eq technicianId }) {
                it[bank] = req.bank; it[accountNo] = req.accountNo
                it[accountName] = req.accountName; it[updatedAt] = OffsetDateTime.now()
            }
        } else {
            PayoutAccountsTable.insert {
                it[this.technicianId] = technicianId
                it[bank] = req.bank; it[accountNo] = req.accountNo
                it[accountName] = req.accountName; it[updatedAt] = OffsetDateTime.now()
            }
        }
    }

    fun stats(technicianId: Long): TechnicianStatsDto = transaction {
        val profile = TechnicianProfilesTable.selectAll().where { TechnicianProfilesTable.userId eq technicianId }.singleOrNull()
        val total = JobOffersTable.selectAll().where { JobOffersTable.technicianId eq technicianId }.count().toInt()
        val accepted = JobOffersTable.selectAll().where {
            (JobOffersTable.technicianId eq technicianId) and (JobOffersTable.status eq OfferStatus.DITERIMA.name)
        }.count().toInt()
        TechnicianStatsDto(
            ratingAvg = profile?.get(TechnicianProfilesTable.ratingAvg) ?: 0.0,
            jobsCount = profile?.get(TechnicianProfilesTable.jobsCount) ?: 0,
            totalOffers = total,
            acceptedOffers = accepted,
            acceptRate = if (total > 0) accepted * 100 / total else 0
        )
    }

    fun disputes(): List<DisputeDto> = transaction {
        DisputesTable.selectAll().orderBy(DisputesTable.createdAt to SortOrder.DESC).map {
            DisputeDto(
                it[DisputesTable.id], it[DisputesTable.reportId], it[DisputesTable.orderId],
                it[DisputesTable.amount], it[DisputesTable.decision], it[DisputesTable.note],
                it[DisputesTable.createdAt].toString()
            )
        }
    }

    fun openDispute(reportId: Long): DisputeDto {
        val orderId = transaction {
            IssueReportsTable.selectAll().where { IssueReportsTable.id eq reportId }
                .singleOrNull()?.get(IssueReportsTable.orderId) ?: throw NotFoundException("Laporan tidak ditemukan")
        }
        transaction {
            if (!DisputesTable.selectAll().where { DisputesTable.reportId eq reportId }.any()) {
                DisputesTable.insert {
                    it[this.reportId] = reportId
                    it[this.orderId] = orderId
                    it[amount] = 0L
                    it[decision] = "MENUNGGU"
                    it[note] = ""
                    it[createdAt] = OffsetDateTime.now()
                }
            }
        }
        return disputes().first { it.reportId == reportId }
    }

    suspend fun resolveDispute(adminId: Long, id: Long, req: ResolveDisputeRequest): DisputeDto {
        val resolved = transaction {
            val row = DisputesTable.selectAll().where { DisputesTable.id eq id }
                .singleOrNull() ?: throw NotFoundException("Sengketa tidak ditemukan")
            DisputesTable.update({ DisputesTable.id eq id }) {
                it[decision] = req.decision
                it[amount] = req.amount.coerceAtLeast(0)
                it[note] = req.note
                it[this.adminId] = adminId
            }
            if (req.decision.equals("DISETUJUI", true)) {
                val orderId = row[DisputesTable.orderId]
                PaymentsTable.update({
                    (PaymentsTable.orderId eq orderId) and (PaymentsTable.status eq PaymentStatus.LUNAS.name)
                }) { it[status] = PaymentStatus.KEMBALI.name }
                OrdersTable.update({ OrdersTable.id eq orderId }) {
                    it[paymentStatus] = PaymentStatus.KEMBALI.name
                }
            }
            DisputeDto(
                row[DisputesTable.id], row[DisputesTable.reportId], row[DisputesTable.orderId],
                req.amount.coerceAtLeast(0), req.decision, req.note, row[DisputesTable.createdAt].toString()
            )
        }
        WsHub.sendTo(adminId, WsEvent(WsEventType.ORDER_STATUS, resolved.orderId))
        return resolved
    }

    fun audit(adminId: Long? = null): List<AuditLogDto> = transaction {
        val query = if (adminId == null) AuditLogsTable.selectAll() else AuditLogsTable.selectAll().where { AuditLogsTable.adminId eq adminId }
        query.orderBy(AuditLogsTable.createdAt to SortOrder.DESC).limit(100).map {
            AuditLogDto(it[AuditLogsTable.id], it[AuditLogsTable.adminId], it[AuditLogsTable.action], it[AuditLogsTable.target], it[AuditLogsTable.createdAt].toString())
        }
    }

    fun log(adminId: Long, action: String, target: String) = transaction {
        AuditLogsTable.insert {
            it[this.adminId] = adminId
            it[this.action] = action
            it[this.target] = target
            it[createdAt] = OffsetDateTime.now()
        }
    }

    fun announcements(): List<AnnouncementDto> = transaction {
        AnnouncementsTable.selectAll().orderBy(AnnouncementsTable.createdAt to SortOrder.DESC).limit(50).map {
            AnnouncementDto(it[AnnouncementsTable.id], it[AnnouncementsTable.title], it[AnnouncementsTable.body], it[AnnouncementsTable.createdAt].toString())
        }
    }

    suspend fun announce(adminId: Long, req: CreateAnnouncementRequest): AnnouncementDto {
        val dto = transaction {
            val id = AnnouncementsTable.insert {
                it[this.adminId] = adminId
                it[title] = req.title
                it[body] = req.body
                it[createdAt] = OffsetDateTime.now()
            } get AnnouncementsTable.id
            AnnouncementDto(id, req.title, req.body, OffsetDateTime.now().toString())
        }
        val users = transaction { UsersTable.selectAll().map { it[UsersTable.id] } }
        users.forEach { WsHub.sendTo(it, WsEvent(WsEventType.CHAT_MESSAGE, null)) }
        return dto
    }

    fun configs(): List<SystemConfigDto> = transaction {
        SystemConfigTable.selectAll().map { SystemConfigDto(it[SystemConfigTable.key], it[SystemConfigTable.value]) }
    }

    fun setConfig(key: String, value: String) = transaction {
        if (SystemConfigTable.selectAll().where { SystemConfigTable.key eq key }.any()) {
            SystemConfigTable.update({ SystemConfigTable.key eq key }) { it[SystemConfigTable.value] = value }
        } else {
            SystemConfigTable.insert {
                it[SystemConfigTable.key] = key
                it[SystemConfigTable.value] = value
            }
        }
    }

    fun leaders(): List<TechnicianLeaderDto> = transaction {
        TechnicianProfilesTable.innerJoin(UsersTable, { TechnicianProfilesTable.userId }, { UsersTable.id })
            .selectAll().where { TechnicianProfilesTable.verificationStatus eq TechnicianVerificationStatus.TERVERIFIKASI.name }
            .orderBy(TechnicianProfilesTable.ratingAvg to SortOrder.DESC).limit(20).map { row ->
                val techId = row[TechnicianProfilesTable.userId]
                val total = JobOffersTable.selectAll().where { JobOffersTable.technicianId eq techId }.count().toInt()
                val accepted = JobOffersTable.selectAll().where {
                    (JobOffersTable.technicianId eq techId) and (JobOffersTable.status eq OfferStatus.DITERIMA.name)
                }.count().toInt()
                TechnicianLeaderDto(
                    techId,
                    row[UsersTable.fullName],
                    row[TechnicianProfilesTable.ratingAvg],
                    row[TechnicianProfilesTable.jobsCount],
                    if (total > 0) accepted * 100 / total else 0
                )
            }
    }
}
