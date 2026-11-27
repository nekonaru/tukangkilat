package id.tukangkilat.backend

import id.tukangkilat.shared.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.vendors.ForUpdateOption
import java.time.OffsetDateTime

/** Technician-side operations: presence, location, offers, job actions, earnings. */
object TechnicianService {

    // ---------- Presence and profile ----------

    fun setOnline(userId: Long, online: Boolean) = transaction {
        assertVerified(userId)
        TechnicianProfilesTable.update({ TechnicianProfilesTable.userId eq userId }) {
            it[isOnline] = online
            if (!online) {
                // Dispatch must not reuse a stale coordinate after the technician comes back.
                it[lastLat] = null
                it[lastLng] = null
                it[lastLocationAt] = null
            }
        }
    }

    suspend fun updateLocation(userId: Long, lat: Double, lng: Double) {
        transaction {
            TechnicianProfilesTable.update({ TechnicianProfilesTable.userId eq userId }) {
                it[lastLat] = lat
                it[lastLng] = lng
                it[lastLocationAt] = OffsetDateTime.now()
            }
        }
        val tracked = transaction {
            OrdersTable.selectAll().where {
                (OrdersTable.technicianId eq userId) and
                    (OrdersTable.status eq OrderStatus.DALAM_PERJALANAN.name)
            }.map { it[OrdersTable.id] to it[OrdersTable.consumerId] }
        }
        for ((orderId, consumerId) in tracked) {
            val payload = kotlinx.serialization.json.JsonObject(
                mapOf(
                    "lat" to kotlinx.serialization.json.JsonPrimitive(lat),
                    "lng" to kotlinx.serialization.json.JsonPrimitive(lng)
                )
            )
            WsHub.sendTo(consumerId, WsEvent(WsEventType.TECHNICIAN_LOCATION, orderId, payload))
        }
    }

    fun getProfile(userId: Long): TechnicianProfileDto = transaction {
        val user = UsersTable.selectAll().where { UsersTable.id eq userId }.single()
        val profile = TechnicianProfilesTable.selectAll().where { TechnicianProfilesTable.userId eq userId }.single()
        TechnicianProfileDto(
            id = userId,
            fullName = user[UsersTable.fullName],
            username = user[UsersTable.username],
            phone = user[UsersTable.phone],
            skills = profile[TechnicianProfilesTable.skills].split(",").filter { it.isNotBlank() },
            serviceRadiusKm = profile[TechnicianProfilesTable.serviceRadiusKm],
            verificationStatus = profile[TechnicianProfilesTable.verificationStatus],
            isOnline = profile[TechnicianProfilesTable.isOnline],
            ratingAvg = profile[TechnicianProfilesTable.ratingAvg],
            jobsCount = profile[TechnicianProfilesTable.jobsCount],
            photoUrl = profile[TechnicianProfilesTable.photoUrl]
        )
    }

    fun updateProfile(userId: Long, req: TechnicianUpdateRequest): TechnicianProfileDto = transaction {
        val radius = req.serviceRadiusKm
        if (radius != null) {
            TechnicianProfilesTable.update({ TechnicianProfilesTable.userId eq userId }) {
                it[TechnicianProfilesTable.serviceRadiusKm] = radius.coerceIn(1, 50)
            }
        }
        val newSkills = req.skills
        if (newSkills != null && newSkills.isNotEmpty()) {
            TechnicianProfilesTable.update({ TechnicianProfilesTable.userId eq userId }) {
                it[TechnicianProfilesTable.skills] = newSkills.joinToString(",") { s -> s.trim().uppercase() }
            }
        }
        getProfile(userId)
    }

    // ---------- Offers ----------

    fun listOffers(userId: Long): List<OfferDto> = transaction {
        val now = OffsetDateTime.now()
        // expire stale offers for this technician first
        JobOffersTable.update({
            (JobOffersTable.technicianId eq userId) and
                (JobOffersTable.status eq OfferStatus.MENUNGGU.name) and
                (JobOffersTable.expiresAt less now)
        }) { it[JobOffersTable.status] = OfferStatus.KEDALUWARSA.name }

        (JobOffersTable innerJoin OrdersTable innerJoin ServicesTable innerJoin AddressesTable)
            .selectAll()
            .where {
                (JobOffersTable.technicianId eq userId) and
                    (JobOffersTable.status eq OfferStatus.MENUNGGU.name)
            }
            .orderBy(JobOffersTable.createdAt to SortOrder.DESC)
            .map { row ->
                offerDto(row)
            }
    }

    private fun offerDto(row: ResultRow): OfferDto {
        val orderId = row[OrdersTable.id]
        val addressLat = row[AddressesTable.lat]
        val addressLng = row[AddressesTable.lng]
        // distance is computed by dispatcher and stored on the offer via orders address; recompute from profile if present
        val profile = TechnicianProfilesTable.selectAll()
            .where { TechnicianProfilesTable.userId eq row[JobOffersTable.technicianId] }.singleOrNull()
        val lat = profile?.get(TechnicianProfilesTable.lastLat)
        val lng = profile?.get(TechnicianProfilesTable.lastLng)
        val distance = if (lat != null && lng != null) Geo.haversineKm(lat, lng, addressLat, addressLng) else 0.0
        val payout = Pricing.technicianPayout(row[OrdersTable.serviceFee], row[OrdersTable.visitFee])
        return OfferDto(
            id = row[JobOffersTable.id],
            orderId = orderId,
            orderCode = row[OrdersTable.code],
            serviceName = row[ServicesTable.name],
            description = row[OrdersTable.description],
            distanceKm = Math.round(distance * 10) / 10.0,
            scheduledDate = row[OrdersTable.scheduledDate].toString(),
            slotLabel = OrderQueries.loadSlotLabel(row[OrdersTable.slotId]),
            estimatedPayout = payout,
            expiresAt = row[JobOffersTable.expiresAt].toString(),
            status = OfferStatus.valueOf(row[JobOffersTable.status]),
            isRevisit = row[JobOffersTable.isRevisit]
        )
    }

    suspend fun acceptOffer(userId: Long, offerId: Long): OrderDto {
        val orderId = transaction {
            val offer = JobOffersTable.selectAll().where { JobOffersTable.id eq offerId }.singleOrNull()
                ?: throw NotFoundException("Tawaran tidak ditemukan")
            if (offer[JobOffersTable.technicianId] != userId) throw ForbiddenException("Tawaran ini bukan untuk Anda")
            if (offer[JobOffersTable.status] != OfferStatus.MENUNGGU.name) {
                throw ConflictException("Tawaran sudah ${offer[JobOffersTable.status].lowercase()} atau kedaluwarsa")
            }
            if (offer[JobOffersTable.expiresAt].isBefore(OffsetDateTime.now())) {
                JobOffersTable.update({ JobOffersTable.id eq offerId }) { it[JobOffersTable.status] = OfferStatus.KEDALUWARSA.name }
                throw ConflictException("Tawaran sudah kedaluwarsa")
            }
            val orderId = offer[JobOffersTable.orderId]
            val now = OffsetDateTime.now()
            JobOffersTable.update({ JobOffersTable.id eq offerId }) { it[JobOffersTable.status] = OfferStatus.DITERIMA.name }
            // other active offers for the same order are expired
            JobOffersTable.update({
                (JobOffersTable.orderId eq orderId) and
                    (JobOffersTable.status eq OfferStatus.MENUNGGU.name) and
                    (JobOffersTable.id neq offerId)
            }) { it[JobOffersTable.status] = OfferStatus.KEDALUWARSA.name }
            OrdersTable.update({ OrdersTable.id eq orderId }) {
                it[OrdersTable.technicianId] = userId
                it[OrdersTable.updatedAt] = now
            }
            OrderService.transitionInTx(orderId, OrderStatus.TEKNISI_DITEMUKAN, userId, OrderStateMachine.Actor.TUKANG)
            orderId
        }
        // notify both parties that a technician was found
        sendOfferAccepted(orderId, userId)
        return transaction { OrderQueries.toOrderDto(OrderQueries.loadOrderRow(orderId)) }
    }

    private suspend fun sendOfferAccepted(orderId: Long, technicianId: Long) {
        val consumerId = transaction { OrderQueries.loadOrderRow(orderId)[OrdersTable.consumerId] }
        WsHub.sendStatus(consumerId, orderId)
        WsHub.sendStatus(technicianId, orderId)
    }

    suspend fun rejectOffer(userId: Long, offerId: Long, req: RejectOfferRequest) {
        val orderId = transaction {
            val offer = JobOffersTable.selectAll().where { JobOffersTable.id eq offerId }.singleOrNull()
                ?: throw NotFoundException("Tawaran tidak ditemukan")
            if (offer[JobOffersTable.technicianId] != userId) throw ForbiddenException("Tawaran ini bukan untuk Anda")
            if (offer[JobOffersTable.status] != OfferStatus.MENUNGGU.name) {
                throw ConflictException("Tawaran sudah diproses")
            }
            JobOffersTable.update({ JobOffersTable.id eq offerId }) {
                it[JobOffersTable.status] = OfferStatus.DITOLAK.name
                it[JobOffersTable.rejectReason] = req.reason.trim().take(250).ifBlank { null }
            }
            offer[JobOffersTable.orderId]
        }
        WsHub.sendStatus(consumerOf(orderId), orderId)
    }

    // ---------- Job actions ----------

    /** One big dispatcher for the ordered work actions. Only the assigned technician may call them. */
    suspend fun jobAction(userId: Long, orderId: Long, action: String, req: FinishWorkRequest? = null): OrderDto {
        val result = transaction {
            val row = OrderQueries.loadOrderRow(orderId)
            if (row[OrdersTable.technicianId] != userId) {
                throw ForbiddenException("Pesanan ini bukan milik Anda")
            }
            val status = OrderStatus.valueOf(row[OrdersTable.status])
            when (action) {
                "depart" -> {
                    if (status != OrderStatus.TEKNISI_DITEMUKAN) throw ConflictException("Tidak bisa berangkat dari status $status")
                    OrderService.transitionInTx(orderId, OrderStatus.DALAM_PERJALANAN, userId, OrderStateMachine.Actor.TUKANG)
                }
                "arrive" -> {
                    if (status != OrderStatus.DALAM_PERJALANAN) throw ConflictException("Tidak bisa tiba dari status $status")
                    OrderService.transitionInTx(orderId, OrderStatus.TIBA, userId, OrderStateMachine.Actor.TUKANG)
                }
                "start" -> {
                    if (status != OrderStatus.TIBA) throw ConflictException("Tidak bisa mulai bekerja dari status $status")
                    OrderService.transitionInTx(orderId, OrderStatus.SEDANG_BEKERJA, userId, OrderStateMachine.Actor.TUKANG)
                }
                "finish" -> {
                    if (status != OrderStatus.SEDANG_BEKERJA) throw ConflictException("Tidak bisa menyelesaikan dari status $status")
                    val finishReq = req ?: throw BadRequestException("Data penyelesaian wajib diisi")
                    if (finishReq.note.isBlank()) throw BadRequestException("Catatan pekerjaan wajib diisi")
                    if (finishReq.photoUrls.isEmpty()) throw BadRequestException("Minimal 1 foto bukti wajib diunggah")
                    if (row[OrdersTable.paymentMethod] == PaymentMethod.TUNAI.name) {
                        if (!finishReq.cashReceived) {
                            throw BadRequestException("Centang konfirmasi uang tunai sudah diterima")
                        }
                        OrdersTable.update({ OrdersTable.id eq orderId }) {
                            it[OrdersTable.paymentStatus] = PaymentStatus.LUNAS.name
                            it[OrdersTable.finishNote] = finishReq.note.trim()
                        }
                        PaymentsTable.update({
                            (PaymentsTable.orderId eq orderId) and (PaymentsTable.status eq PaymentStatus.BELUM_DIBAYAR.name)
                        }) {
                            it[PaymentsTable.status] = PaymentStatus.LUNAS.name
                            it[PaymentsTable.paidAt] = OffsetDateTime.now()
                        }
                    } else {
                        OrdersTable.update({ OrdersTable.id eq orderId }) {
                            it[OrdersTable.finishNote] = finishReq.note.trim()
                        }
                    }
                    OrderService.linkPhotoPublicIds(orderId, finishReq.photoUrls.take(5), PhotoKind.BUKTI)
                    OrderService.transitionInTx(orderId, OrderStatus.MENUNGGU_KONFIRMASI, userId, OrderStateMachine.Actor.TUKANG)
                }
                else -> throw BadRequestException("Aksi tidak dikenal")
            }
            OrderQueries.toOrderDto(OrderQueries.loadOrderRow(orderId))
        }
        OrderService.notifyOrderParties(orderId)
        return result
    }

    // ---------- Reviews received ----------

    fun reviews(userId: Long): List<ReviewDto> = transaction {
        (RatingsTable innerJoin OrdersTable innerJoin ServicesTable)
            .selectAll()
            .where { OrdersTable.technicianId eq userId }
            .orderBy(RatingsTable.createdAt to SortOrder.DESC)
            .map { row ->
                val orderId = row[RatingsTable.orderId]
                val photos = ReviewPhotosTable.selectAll()
                    .where { ReviewPhotosTable.orderId eq orderId }
                    .orderBy(ReviewPhotosTable.id to SortOrder.ASC)
                    .map { it[ReviewPhotosTable.photoUrl] }
                ReviewDto(
                    orderId = orderId,
                    orderCode = row[OrdersTable.code],
                    serviceName = row[ServicesTable.name],
                    stars = row[RatingsTable.stars],
                    tags = row[RatingsTable.tags].split(",").filter { it.isNotBlank() },
                    comment = row[RatingsTable.comment],
                    photos = photos,
                    createdAt = row[RatingsTable.createdAt].toString()
                )
            }
    }

    // ---------- Earnings ----------

    fun earnings(userId: Long): EarningsDto = transaction {
        val rows = OrdersTable.selectAll().where {
            (OrdersTable.technicianId eq userId) and (OrdersTable.status eq OrderStatus.SELESAI.name)
        }.orderBy(OrdersTable.updatedAt to SortOrder.DESC).toList()

        var balance = 0L
        var totalEarned = 0L
        val entries = rows.map { row ->
            val serviceFee = row[OrdersTable.serviceFee]
            val visitFee = row[OrdersTable.visitFee]
            val commission = Pricing.commission(serviceFee)
            val net = Pricing.technicianPayout(serviceFee, visitFee)
            balance += net
            totalEarned += net
            EarningEntryDto(
                orderId = row[OrdersTable.id],
                orderCode = row[OrdersTable.code],
                serviceName = OrderQueries.loadServiceName(row[OrdersTable.serviceId]),
                serviceFee = serviceFee,
                commission = commission,
                visitFee = visitFee,
                net = net,
                finishedAt = row[OrdersTable.updatedAt].toString()
            )
        }
        val withdrawn = WithdrawalsTable.selectAll()
            .where { WithdrawalsTable.technicianId eq userId }
            .sumOf { it[WithdrawalsTable.amount] }
        EarningsDto(
            balance = balance - withdrawn,
            totalEarned = totalEarned,
            commissionRate = Pricing.COMMISSION_RATE,
            entries = entries
        )
    }

    // ---------- Withdrawals (simulation, PRD T-11) ----------

    fun withdraw(userId: Long, req: WithdrawRequest): WithdrawalDto = transaction {
        assertVerified(userId)
        if (req.amount <= 0) throw BadRequestException("Jumlah penarikan harus lebih dari 0")
        // Serialize concurrent withdrawals for this technician with a row lock.
        UsersTable.selectAll().where { UsersTable.id eq userId }
            .forUpdate(ForUpdateOption.ForUpdate).singleOrNull()
            ?: throw BadRequestException("Teknisi tidak ditemukan")
        val earned = OrdersTable.selectAll().where {
            (OrdersTable.technicianId eq userId) and (OrdersTable.status eq OrderStatus.SELESAI.name)
        }.sumOf { Pricing.technicianPayout(it[OrdersTable.serviceFee], it[OrdersTable.visitFee]) }
        val withdrawn = WithdrawalsTable.selectAll()
            .where { WithdrawalsTable.technicianId eq userId }
            .sumOf { it[WithdrawalsTable.amount] }
        if (req.amount > earned - withdrawn) throw ConflictException("Saldo tidak cukup")
        val id = WithdrawalsTable.insert {
            it[technicianId] = userId
            it[amount] = req.amount
            it[status] = "SELESAI"
            it[createdAt] = OffsetDateTime.now()
        } get WithdrawalsTable.id
        WithdrawalDto(id, req.amount, "SELESAI", OffsetDateTime.now().toString())
    }

    fun withdrawals(userId: Long): List<WithdrawalDto> = transaction {
        WithdrawalsTable.selectAll()
            .where { WithdrawalsTable.technicianId eq userId }
            .orderBy(WithdrawalsTable.createdAt to SortOrder.DESC)
            .map { WithdrawalDto(it[WithdrawalsTable.id], it[WithdrawalsTable.amount], it[WithdrawalsTable.status], it[WithdrawalsTable.createdAt].toString()) }
    }

    // ---------- Internal helpers ----------

    private fun assertVerified(userId: Long) {
        val profile = TechnicianProfilesTable.selectAll().where { TechnicianProfilesTable.userId eq userId }.singleOrNull()
            ?: throw NotFoundException("Profil tukang tidak ditemukan")
        when (profile[TechnicianProfilesTable.verificationStatus]) {
            TechnicianVerificationStatus.TERVERIFIKASI.name -> Unit
            TechnicianVerificationStatus.MENUNGGU_VERIFIKASI.name ->
                throw ForbiddenException("Akun Anda sedang menunggu verifikasi admin")
            TechnicianVerificationStatus.DITOLAK.name ->
                throw ForbiddenException("Pendaftaran Anda ditolak. Hubungi admin")
            else -> throw ForbiddenException("Akun Anda ditangguhkan. Hubungi admin")
        }
    }

    private fun consumerOf(orderId: Long): Long =
        transaction { OrderQueries.loadOrderRow(orderId)[OrdersTable.consumerId] }
}
