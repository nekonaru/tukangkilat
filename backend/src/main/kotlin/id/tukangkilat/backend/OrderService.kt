package id.tukangkilat.backend

import id.tukangkilat.shared.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.vendors.ForUpdateOption
import java.security.SecureRandom
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/** All order writes. Status changes go through [transitionOrder] only. */
object OrderService {

    private val secureRandom = SecureRandom()
    private const val MAX_CODE_ATTEMPTS = 5

    /** Atomically append an order event and broadcast to both parties. */
    internal fun transitionInTx(orderId: Long, to: OrderStatus, actorId: Long?, actor: OrderStateMachine.Actor) {
        val row = OrderQueries.loadOrderRow(orderId)
        val current = OrderStatus.valueOf(row[OrdersTable.status])
        if (!OrderStateMachine.canTransition(current, to, actor)) {
            throw IllegalTransitionException(current.name, to.name)
        }
        val now = OffsetDateTime.now()
        OrdersTable.update({ OrdersTable.id eq orderId }) {
            it[OrdersTable.status] = to.name
            it[OrdersTable.updatedAt] = now
        }
        OrderEventsTable.insert {
            it[OrderEventsTable.orderId] = orderId
            it[OrderEventsTable.status] = to.name
            it[OrderEventsTable.actorId] = actorId
            it[createdAt] = now
        }
    }

    /** Transition outside a transaction, then notify both parties. */
    suspend fun transition(orderId: Long, to: OrderStatus, actorId: Long?, actor: OrderStateMachine.Actor) {
        transaction { transitionInTx(orderId, to, actorId, actor) }
        notifyOrderParties(orderId)
    }

    suspend fun notifyOrderParties(orderId: Long) {
        val row = transaction { OrderQueries.loadOrderRow(orderId) }
        val consumerId = row[OrdersTable.consumerId]
        val technicianId = row[OrdersTable.technicianId]
        WsHub.sendStatus(consumerId, orderId)
        if (technicianId != null) WsHub.sendStatus(technicianId, orderId)
    }

    // ---------- Catalog ----------

    fun listServices(): List<ServiceDto> = transaction {
        ServicesTable.selectAll().where { ServicesTable.isActive eq true }
            .orderBy(ServicesTable.id to SortOrder.ASC)
            .map {
                ServiceDto(
                    id = it[ServicesTable.id],
                    name = it[ServicesTable.name],
                    icon = it[ServicesTable.icon],
                    basePrice = it[ServicesTable.basePrice],
                    visitFee = it[ServicesTable.visitFee],
                    isActive = it[ServicesTable.isActive]
                )
            }
    }

    fun listSlots(date: LocalDate): List<SlotDto> = transaction {
        val today = LocalDate.now()
        TimeSlotsTable.selectAll().orderBy(TimeSlotsTable.id to SortOrder.ASC).map { slot ->
            val used = OrdersTable.selectAll().where {
                (OrdersTable.scheduledDate eq date) and
                    (OrdersTable.slotId eq slot[TimeSlotsTable.id]) and
                    (OrdersTable.status neq OrderStatus.DIBATALKAN.name) and
                    (OrdersTable.status neq OrderStatus.PEMBAYARAN_GAGAL.name)
            }.count()
            val remaining = (slot[TimeSlotsTable.capacity] - used).toInt().coerceAtLeast(0)
            SlotDto(
                id = slot[TimeSlotsTable.id],
                label = slot[TimeSlotsTable.label],
                capacity = slot[TimeSlotsTable.capacity],
                remaining = remaining,
                available = remaining > 0 && !date.isBefore(today)
            )
        }
    }

    // ---------- Addresses ----------

    fun listAddresses(userId: Long): List<AddressDto> = transaction {
        AddressesTable.selectAll().where { AddressesTable.userId eq userId }
            .orderBy(AddressesTable.isPrimary to SortOrder.DESC, AddressesTable.id to SortOrder.ASC)
            .map { it.toAddressDto() }
    }

    fun addAddress(userId: Long, req: AddressUpsertRequest): AddressDto = transaction {
        if (req.label.isBlank() || req.fullAddress.isBlank()) throw BadRequestException("Label dan alamat wajib diisi")
        val makePrimary = req.isPrimary || !AddressesTable.selectAll().where { AddressesTable.userId eq userId }.any()
        if (makePrimary) unsetPrimary(userId)
        val now = OffsetDateTime.now()
        val id = AddressesTable.insert {
            it[AddressesTable.userId] = userId
            it[label] = req.label.trim()
            it[fullAddress] = req.fullAddress.trim()
            it[lat] = req.lat
            it[lng] = req.lng
            it[isPrimary] = makePrimary
        } get AddressesTable.id
        AddressesTable.selectAll().where { AddressesTable.id eq id }.single().toAddressDto()
    }

    fun updateAddress(userId: Long, addressId: Long, req: AddressUpsertRequest): AddressDto = transaction {
        val existing = AddressesTable.selectAll()
            .where { (AddressesTable.id eq addressId) and (AddressesTable.userId eq userId) }
            .singleOrNull() ?: throw NotFoundException("Alamat tidak ditemukan")
        if (req.isPrimary) unsetPrimary(userId)
        AddressesTable.update({ AddressesTable.id eq addressId }) {
            it[label] = req.label.trim().ifBlank { existing[AddressesTable.label] }
            it[fullAddress] = req.fullAddress.trim().ifBlank { existing[AddressesTable.fullAddress] }
            it[lat] = req.lat
            it[lng] = req.lng
            it[isPrimary] = if (req.isPrimary) true else existing[AddressesTable.isPrimary]
        }
        AddressesTable.selectAll().where { AddressesTable.id eq addressId }.single().toAddressDto()
    }

    fun deleteAddress(userId: Long, addressId: Long) = transaction {
        val deleted = AddressesTable.deleteWhere {
            with(SqlExpressionBuilder) { (AddressesTable.id eq addressId) and (AddressesTable.userId eq userId) }
        }
        if (deleted == 0) throw NotFoundException("Alamat tidak ditemukan")
    }

    private fun unsetPrimary(userId: Long) {
        AddressesTable.update({ AddressesTable.userId eq userId }) { it[isPrimary] = false }
    }

    private fun ResultRow.toAddressDto() = AddressDto(
        id = this[AddressesTable.id],
        label = this[AddressesTable.label],
        fullAddress = this[AddressesTable.fullAddress],
        lat = this[AddressesTable.lat],
        lng = this[AddressesTable.lng],
        isPrimary = this[AddressesTable.isPrimary]
    )

    // ---------- Create order ----------

    fun createOrder(consumerId: Long, req: CreateOrderRequest): OrderDto = transaction {
        if (req.description.isBlank()) throw BadRequestException("Ceritakan masalahnya wajib diisi")
        val scheduledDate = try {
            LocalDate.parse(req.scheduledDate)
        } catch (e: Exception) {
            throw BadRequestException("Format tanggal tidak valid (YYYY-MM-DD)")
        }
        if (scheduledDate.isBefore(LocalDate.now())) throw BadRequestException("Tanggal tidak boleh di masa lalu")

        val service = ServicesTable.selectAll()
            .where { (ServicesTable.id eq req.serviceId) and (ServicesTable.isActive eq true) }
            .singleOrNull() ?: throw NotFoundException("Layanan tidak ditemukan atau tidak aktif")

        val slot = TimeSlotsTable.selectAll().where { TimeSlotsTable.id eq req.slotId }.forUpdate(ForUpdateOption.ForUpdate).singleOrNull()
            ?: throw NotFoundException("Slot waktu tidak ditemukan")

        val address = AddressesTable.selectAll()
            .where { (AddressesTable.id eq req.addressId) and (AddressesTable.userId eq consumerId) }
            .singleOrNull() ?: throw NotFoundException("Alamat tidak ditemukan")

        // slot availability
        val used = OrdersTable.selectAll().where {
            (OrdersTable.scheduledDate eq scheduledDate) and
                (OrdersTable.slotId eq slot[TimeSlotsTable.id]) and
                (OrdersTable.status neq OrderStatus.DIBATALKAN.name) and
                (OrdersTable.status neq OrderStatus.PEMBAYARAN_GAGAL.name)
        }.count()
        if (used >= slot[TimeSlotsTable.capacity]) {
            throw ConflictException("Slot ${slot[TimeSlotsTable.label]} sudah penuh untuk tanggal tersebut")
        }

        val serviceFee = service[ServicesTable.basePrice]
        val visitFee = service[ServicesTable.visitFee]

        // promo
        var discount = 0L
        val promoCode = req.promoCode?.trim()?.uppercase()?.ifBlank { null }
        var promoQuotaRemaining: Int? = null
        if (promoCode != null) {
            val promo = PromoCodesTable.selectAll().where { PromoCodesTable.code eq promoCode }.forUpdate(ForUpdateOption.ForUpdate).singleOrNull()
                ?: throw BadRequestException("Kode promo tidak valid")
            if (!promo[PromoCodesTable.isActive]) throw BadRequestException("Kode promo tidak aktif")
            if (promo[PromoCodesTable.validUntil].isBefore(OffsetDateTime.now())) {
                throw BadRequestException("Kode promo sudah kedaluwarsa")
            }
            promoQuotaRemaining = promo[PromoCodesTable.quota]
            if (promoQuotaRemaining <= 0) throw BadRequestException("Kuota kode promo habis")
            val subtotal = serviceFee + visitFee
            discount = when (promo[PromoCodesTable.promoType]) {
                "PERSEN" -> subtotal * promo[PromoCodesTable.promoValue] / 100
                else -> promo[PromoCodesTable.promoValue]
            }.coerceAtMost(subtotal)
        }

        val total = serviceFee + visitFee - discount
        val now = OffsetDateTime.now()
        val code = generateOrderCode()

        val orderId = OrdersTable.insert {
            it[OrdersTable.code] = code
            it[OrdersTable.consumerId] = consumerId
            it[technicianId] = null
            it[serviceId] = service[ServicesTable.id]
            it[description] = req.description.trim()
            it[addressId] = address[AddressesTable.id]
            it[OrdersTable.scheduledDate] = scheduledDate
            it[slotId] = slot[TimeSlotsTable.id]
            it[OrdersTable.status] = OrderStatus.MENUNGGU_PEMBAYARAN.name
            it[OrdersTable.serviceFee] = serviceFee
            it[OrdersTable.visitFee] = visitFee
            it[OrdersTable.discount] = discount
            it[OrdersTable.total] = total
            it[OrdersTable.paymentMethod] = null
            it[OrdersTable.paymentStatus] = PaymentStatus.MENUNGGU.name
            it[createdAt] = now
            it[updatedAt] = now
        } get OrdersTable.id

        if (promoCode != null && promoQuotaRemaining != null) {
            PromoCodesTable.update({ PromoCodesTable.code eq promoCode }) {
                it[PromoCodesTable.quota] = promoQuotaRemaining - 1
            }
        }
        linkPhotoPublicIds(orderId, req.photoUrls.take(3), PhotoKind.MASALAH)

        OrderEventsTable.insert {
            it[OrderEventsTable.orderId] = orderId
            it[status] = OrderStatus.MENUNGGU_PEMBAYARAN.name
            it[OrderEventsTable.actorId] = consumerId
            it[OrderEventsTable.createdAt] = now
        }

        OrderQueries.toOrderDto(OrderQueries.loadOrderRow(orderId))
    }

    private fun generateOrderCode(): String {
        val today = LocalDate.now()
        val prefix = "TK-" + today.format(DateTimeFormatter.ofPattern("ddMMyy")) + "-"
        repeat(MAX_CODE_ATTEMPTS) {
            val count = OrdersTable.selectAll().where { OrdersTable.code like "$prefix%" }.count()
            val candidate = prefix + (count + 1).toString().padStart(2, '0')
            val exists = OrdersTable.selectAll().where { OrdersTable.code eq candidate }.any()
            if (!exists) return candidate
        }
        return prefix + secureRandom.nextInt(90).plus(10).toString() + "-" + System.currentTimeMillis().toString().takeLast(4)
    }

    fun listMyOrders(consumerId: Long): List<OrderSummaryDto> = transaction {
        OrdersTable.selectAll().where { OrdersTable.consumerId eq consumerId }
            .orderBy(OrdersTable.createdAt to SortOrder.DESC)
            .map { OrderQueries.toOrderSummary(it) }
    }

    fun listOrdersForTechnician(technicianId: Long): List<OrderSummaryDto> = transaction {
        OrdersTable.selectAll().where { OrdersTable.technicianId eq technicianId }
            .orderBy(OrdersTable.updatedAt to SortOrder.DESC)
            .map { OrderQueries.toOrderSummary(it) }
    }

    fun getOrder(orderId: Long, user: AuthUser): OrderDto = transaction {
        val row = OrderQueries.loadOrderRow(orderId)
        OrderQueries.assertCanAccess(row, user)
        OrderQueries.toOrderDto(row)
    }

    // ---------- Payments ----------

    suspend fun pay(orderId: Long, consumerId: Long, req: PayRequest): PayResponse {
        val response = transaction {
            val row = OrderQueries.loadOrderRow(orderId)
            if (row[OrdersTable.consumerId] != consumerId) throw ForbiddenException("Anda tidak punya akses ke pesanan ini")
            var status = OrderStatus.valueOf(row[OrdersTable.status])
            if (status == OrderStatus.PEMBAYARAN_GAGAL) {
                transitionInTx(orderId, OrderStatus.MENUNGGU_PEMBAYARAN, consumerId, OrderStateMachine.Actor.KONSUMEN)
                status = OrderStatus.MENUNGGU_PEMBAYARAN
            }
            if (status != OrderStatus.MENUNGGU_PEMBAYARAN) {
                throw ConflictException("Pesanan tidak menunggu pembayaran")
            }
            val now = OffsetDateTime.now()

            // apply a promo code entered on the payment screen, if any
            var effectiveTotal = row[OrdersTable.total]
            val promoCode = req.promoCode?.trim()?.uppercase()?.ifBlank { null }
            if (promoCode != null) {
                if (row[OrdersTable.discount] > 0L) {
                    throw ConflictException("Pesanan sudah memakai kode promo")
                }
                val promo = PromoCodesTable.selectAll().where { PromoCodesTable.code eq promoCode }.forUpdate(ForUpdateOption.ForUpdate).singleOrNull()
                    ?: throw BadRequestException("Kode promo tidak valid")
                if (!promo[PromoCodesTable.isActive]) throw BadRequestException("Kode promo tidak aktif")
                if (promo[PromoCodesTable.validUntil].isBefore(now)) {
                    throw BadRequestException("Kode promo sudah kedaluwarsa")
                }
                val quota = promo[PromoCodesTable.quota]
                if (quota <= 0) throw BadRequestException("Kuota kode promo habis")
                val subtotal = row[OrdersTable.serviceFee] + row[OrdersTable.visitFee]
                val discount = when (promo[PromoCodesTable.promoType]) {
                    "PERSEN" -> subtotal * promo[PromoCodesTable.promoValue] / 100
                    else -> promo[PromoCodesTable.promoValue]
                }.coerceAtMost(subtotal)
                effectiveTotal = subtotal - discount
                OrdersTable.update({ OrdersTable.id eq orderId }) {
                    it[OrdersTable.discount] = discount
                    it[OrdersTable.total] = effectiveTotal
                    it[updatedAt] = now
                }
                PromoCodesTable.update({ PromoCodesTable.code eq promoCode }) {
                    it[PromoCodesTable.quota] = quota - 1
                }
            }
        when (req.method) {
            PaymentMethod.TUNAI -> {
                OrdersTable.update({ OrdersTable.id eq orderId }) {
                    it[OrdersTable.paymentMethod] = PaymentMethod.TUNAI.name
                    it[OrdersTable.paymentStatus] = PaymentStatus.BELUM_DIBAYAR.name
                    it[updatedAt] = now
                }
                transitionInTx(orderId, OrderStatus.MENCARI_TEKNISI, consumerId, OrderStateMachine.Actor.KONSUMEN)
                val paymentId = PaymentsTable.insert {
                    it[PaymentsTable.orderId] = orderId
                    it[PaymentsTable.method] = PaymentMethod.TUNAI.name
                    it[PaymentsTable.amount] = effectiveTotal
                    it[PaymentsTable.referenceNo] = null
                    it[PaymentsTable.status] = PaymentStatus.BELUM_DIBAYAR.name
                    it[createdAt] = now
                } get PaymentsTable.id
                PayResponse(orderId, paymentId, PaymentMethod.TUNAI, effectiveTotal, null, PaymentStatus.BELUM_DIBAYAR, null)
            }
            else -> {
                val reference = (if (req.method == PaymentMethod.TRANSFER) "BCA-" else "DOMPET-") + randomDigits(10)
                val expires = now.plusMinutes(30)
                OrdersTable.update({ OrdersTable.id eq orderId }) {
                    it[OrdersTable.paymentMethod] = req.method.name
                    it[OrdersTable.paymentStatus] = PaymentStatus.MENUNGGU.name
                    it[updatedAt] = now
                }
                val paymentId = PaymentsTable.insert {
                    it[PaymentsTable.orderId] = orderId
                    it[PaymentsTable.method] = req.method.name
                    it[PaymentsTable.amount] = effectiveTotal
                    it[PaymentsTable.referenceNo] = reference
                    it[PaymentsTable.status] = PaymentStatus.MENUNGGU.name
                    it[expiresAt] = expires
                    it[createdAt] = now
                } get PaymentsTable.id
                PayResponse(orderId, paymentId, req.method, effectiveTotal, reference, PaymentStatus.MENUNGGU, expires.toString())
            }
        }
    }
        notifyOrderParties(orderId)
        return response
    }

    /** Mark a pending payment as successful; order goes to MENCARI_TEKNISI. */
    suspend fun markPaymentSuccess(orderId: Long, actorId: Long?, actor: OrderStateMachine.Actor): Boolean {
        val ok = markPaymentSuccessTx(orderId, actorId, actor)
        if (ok) notifyOrderParties(orderId)
        return ok
    }

    private fun markPaymentSuccessTx(orderId: Long, actorId: Long?, actor: OrderStateMachine.Actor): Boolean {
        return try {
            transaction {
                val rows = PaymentsTable.selectAll().where {
                    (PaymentsTable.orderId eq orderId) and (PaymentsTable.status eq PaymentStatus.MENUNGGU.name)
                }.orderBy(PaymentsTable.id to SortOrder.DESC).limit(1).toList()
                val payment = rows.firstOrNull() ?: throw ConflictException("Tidak ada pembayaran menunggu untuk pesanan ini")
                val now = OffsetDateTime.now()
                PaymentsTable.update({ PaymentsTable.id eq payment[PaymentsTable.id] }) {
                    it[PaymentsTable.status] = PaymentStatus.LUNAS.name
                    it[PaymentsTable.paidAt] = now
                }
                OrdersTable.update({ OrdersTable.id eq orderId }) {
                    it[OrdersTable.paymentStatus] = PaymentStatus.LUNAS.name
                    it[updatedAt] = now
                }
                transitionInTx(orderId, OrderStatus.MENCARI_TEKNISI, actorId, actor)
            }
            true
        } catch (e: IllegalTransitionException) {
            false
        }
    }

    /** Mark a pending payment as failed; order goes to PEMBAYARAN_GAGAL. */
    suspend fun markPaymentFailed(orderId: Long, actorId: Long?, actor: OrderStateMachine.Actor): Boolean {
        val ok = markPaymentFailedTx(orderId, actorId, actor)
        if (ok) notifyOrderParties(orderId)
        return ok
    }

    private fun markPaymentFailedTx(orderId: Long, actorId: Long?, actor: OrderStateMachine.Actor): Boolean {
        return try {
            transaction {
                val rows = PaymentsTable.selectAll().where {
                    (PaymentsTable.orderId eq orderId) and (PaymentsTable.status eq PaymentStatus.MENUNGGU.name)
                }.orderBy(PaymentsTable.id to SortOrder.DESC).limit(1).toList()
                val payment = rows.firstOrNull() ?: throw ConflictException("Tidak ada pembayaran menunggu untuk pesanan ini")
                PaymentsTable.update({ PaymentsTable.id eq payment[PaymentsTable.id] }) {
                    it[PaymentsTable.status] = PaymentStatus.GAGAL.name
                }
                OrdersTable.update({ OrdersTable.id eq orderId }) {
                    it[OrdersTable.paymentStatus] = PaymentStatus.GAGAL.name
                    it[updatedAt] = OffsetDateTime.now()
                }
                transitionInTx(orderId, OrderStatus.PEMBAYARAN_GAGAL, actorId, actor)
            }
            true
        } catch (e: IllegalTransitionException) {
            false
        }
    }

    /** Expire pending payments older than their expiry; order goes to PEMBAYARAN_GAGAL. */
    suspend fun expireStalePayments(): Int {
        val now = OffsetDateTime.now()
        val stale = transaction {
            PaymentsTable.selectAll().where {
                (PaymentsTable.status eq PaymentStatus.MENUNGGU.name) and
                    (PaymentsTable.expiresAt less now)
            }.map { it[PaymentsTable.orderId] }
        }
        var count = 0
        for (orderId in stale) {
            if (markPaymentFailed(orderId, null, OrderStateMachine.Actor.SYSTEM)) count++
        }
        return count
    }

    // ---------- Cancel / reschedule ----------

    suspend fun cancel(orderId: Long, consumerId: Long, req: CancelRequest): OrderDto {
        val result = transaction {
            val row = OrderQueries.loadOrderRow(orderId)
            if (row[OrdersTable.consumerId] != consumerId) throw ForbiddenException("Anda tidak punya akses ke pesanan ini")
            val status = OrderStatus.valueOf(row[OrdersTable.status])
            when (status) {
                OrderStatus.MENUNGGU_PEMBAYARAN, OrderStatus.PEMBAYARAN_GAGAL,
                OrderStatus.MENCARI_TEKNISI, OrderStatus.TEKNISI_DITEMUKAN -> Unit
                else -> throw ConflictException("Pesanan tidak bisa dibatalkan pada status $status")
            }
            // full refund if already paid
            if (row[OrdersTable.paymentStatus] == PaymentStatus.LUNAS.name) {
                val paymentIds = PaymentsTable.selectAll().where {
                    (PaymentsTable.orderId eq orderId) and (PaymentsTable.status eq PaymentStatus.LUNAS.name)
                }.map { it[PaymentsTable.id] }
                for (pid in paymentIds) {
                    PaymentsTable.update({ PaymentsTable.id eq pid }) { it[PaymentsTable.status] = PaymentStatus.KEMBALI.name }
                }
                OrdersTable.update({ OrdersTable.id eq orderId }) { it[OrdersTable.paymentStatus] = PaymentStatus.KEMBALI.name }
            }
            expireOffersForOrder(orderId)
            transitionInTx(orderId, OrderStatus.DIBATALKAN, consumerId, OrderStateMachine.Actor.KONSUMEN)
            OrderQueries.toOrderDto(OrderQueries.loadOrderRow(orderId))
        }
        notifyOrderParties(orderId)
        return result
    }

    private fun expireOffersForOrder(orderId: Long) {
        JobOffersTable.update({
            (JobOffersTable.orderId eq orderId) and (JobOffersTable.status eq OfferStatus.MENUNGGU.name)
        }) { it[JobOffersTable.status] = OfferStatus.KEDALUWARSA.name }
    }

    suspend fun reschedule(orderId: Long, consumerId: Long, req: RescheduleRequest): OrderDto {
        val result = transaction {
        val row = OrderQueries.loadOrderRow(orderId)
        if (row[OrdersTable.consumerId] != consumerId) throw ForbiddenException("Anda tidak punya akses ke pesanan ini")
        val status = OrderStatus.valueOf(row[OrdersTable.status])
        if (status != OrderStatus.MENCARI_TEKNISI && status != OrderStatus.KUNJUNGAN_ULANG) {
            throw ConflictException("Jadwal hanya bisa diubah saat pesanan belum memiliki teknisi")
        }
        val scheduledDate = try {
            LocalDate.parse(req.scheduledDate)
        } catch (e: Exception) {
            throw BadRequestException("Format tanggal tidak valid (YYYY-MM-DD)")
        }
        if (scheduledDate.isBefore(LocalDate.now())) throw BadRequestException("Tanggal tidak boleh di masa lalu")
        val slot = TimeSlotsTable.selectAll().where { TimeSlotsTable.id eq req.slotId }.forUpdate(ForUpdateOption.ForUpdate).singleOrNull()
            ?: throw NotFoundException("Slot waktu tidak ditemukan")
        val used = OrdersTable.selectAll().where {
            (OrdersTable.scheduledDate eq scheduledDate) and
                (OrdersTable.slotId eq slot[TimeSlotsTable.id]) and
                (OrdersTable.status neq OrderStatus.DIBATALKAN.name) and
                (OrdersTable.status neq OrderStatus.PEMBAYARAN_GAGAL.name) and
                (OrdersTable.id neq orderId)
        }.count()
        if (used >= slot[TimeSlotsTable.capacity]) throw ConflictException("Slot ${slot[TimeSlotsTable.label]} penuh")
        OrdersTable.update({ OrdersTable.id eq orderId }) {
            it[OrdersTable.scheduledDate] = scheduledDate
            it[slotId] = slot[TimeSlotsTable.id]
            it[updatedAt] = OffsetDateTime.now()
        }
        // offers already sent no longer match the new schedule; clear them so the
        // dispatcher restarts with a full set of attempts for the new date and slot
        JobOffersTable.deleteWhere {
            with(SqlExpressionBuilder) { JobOffersTable.orderId eq orderId }
        }
        // the dispatcher picks the order up again because it is back to searching state
        OrderQueries.toOrderDto(OrderQueries.loadOrderRow(orderId))
        }
        notifyOrderParties(orderId)
        return result
    }

    // ---------- Confirm / report / rating ----------

    suspend fun confirm(orderId: Long, consumerId: Long, @Suppress("UNUSED_PARAMETER") req: ConfirmOrderRequest): OrderDto {
        val result = transaction {
            val row = OrderQueries.loadOrderRow(orderId)
            if (row[OrdersTable.consumerId] != consumerId) throw ForbiddenException("Anda tidak punya akses ke pesanan ini")
            transitionInTx(orderId, OrderStatus.SELESAI, consumerId, OrderStateMachine.Actor.KONSUMEN)
            OrderQueries.toOrderDto(OrderQueries.loadOrderRow(orderId))
        }
        notifyOrderParties(orderId)
        return result
    }

    suspend fun report(orderId: Long, consumerId: Long, req: ReportOrderRequest): OrderDto {
        if (req.description.isBlank()) throw BadRequestException("Keterangan masalah wajib diisi")
        val photoUrls = req.photoUrls.take(3)
        val result = transaction {
            val row = OrderQueries.loadOrderRow(orderId)
            if (row[OrdersTable.consumerId] != consumerId) throw ForbiddenException("Anda tidak punya akses ke pesanan ini")
            val reportId = IssueReportsTable.insert {
                it[IssueReportsTable.orderId] = orderId
                it[reason] = req.reason.name
                it[description] = req.description.trim()
                it[status] = IssueStatus.TERBUKA.name
                it[createdAt] = OffsetDateTime.now()
            } get IssueReportsTable.id
            linkPhotoPublicIds(orderId, photoUrls, PhotoKind.LAPORAN)
            transitionInTx(orderId, OrderStatus.DILAPORKAN, consumerId, OrderStateMachine.Actor.KONSUMEN)
            OrderQueries.toOrderDto(OrderQueries.loadOrderRow(orderId))
        }
        notifyOrderParties(orderId)
        return result
    }

    fun rate(orderId: Long, consumerId: Long, req: RatingRequest): OrderDto = transaction {
        if (req.stars !in 1..5) throw BadRequestException("Bintang harus antara 1 dan 5")
        val row = OrderQueries.loadOrderRow(orderId)
        if (row[OrdersTable.consumerId] != consumerId) throw ForbiddenException("Anda tidak punya akses ke pesanan ini")
        val status = OrderStatus.valueOf(row[OrdersTable.status])
        if (status != OrderStatus.SELESAI) throw ConflictException("Hanya pesanan selesai yang bisa dinilai")
        val already = RatingsTable.selectAll().where { RatingsTable.orderId eq orderId }.any()
        if (already) throw ConflictException("Pesanan ini sudah dinilai")
        val technicianId = row[OrdersTable.technicianId] ?: throw ConflictException("Pesanan ini tidak punya teknisi")
        RatingsTable.insert {
            it[RatingsTable.orderId] = orderId
            it[stars] = req.stars
            it[tags] = req.tags.joinToString(",")
            it[comment] = req.comment.trim()
            it[createdAt] = OffsetDateTime.now()
        }
        // recompute technician aggregates
        val aggregate = (RatingsTable innerJoin OrdersTable)
            .select(RatingsTable.stars.count(), RatingsTable.stars.avg())
            .where { OrdersTable.technicianId eq technicianId }
            .single()
        val jobs = (aggregate[RatingsTable.stars.count()] as Number).toInt()
        val avg = (aggregate[RatingsTable.stars.avg()] as? Number)?.toDouble() ?: 0.0
        TechnicianProfilesTable.update({ TechnicianProfilesTable.userId eq technicianId }) {
            it[ratingAvg] = Math.round(avg * 10) / 10.0
            it[jobsCount] = jobs
        }
        OrderQueries.toOrderDto(OrderQueries.loadOrderRow(orderId))
    }

    fun linkPhotoPublicIds(orderId: Long, photoUrls: List<String>, kind: PhotoKind) {
        // accept both bare public ids and "/photos/<id>" urls
        val photoPublicIds = photoUrls.map { it.trim().substringAfterLast('/') }.filter { it.isNotBlank() }
        if (photoPublicIds.isEmpty()) return
        val photos = PhotosTable.selectAll().where { PhotosTable.publicId inList photoPublicIds }.toList()
        if (photos.size != photoPublicIds.toSet().size) throw BadRequestException("Ada foto yang tidak ditemukan")
        for (p in photos) {
            OrderPhotosTable.insert {
                it[OrderPhotosTable.orderId] = orderId
                it[OrderPhotosTable.photoId] = p[PhotosTable.id]
                it[OrderPhotosTable.kind] = kind.name
            }
        }
    }

    // ---------- Auto complete ----------

    fun autoCompleteStaleConfirmation(maxAgeHours: Long = 24): Int {
        val cutoff = OffsetDateTime.now().minusHours(maxAgeHours)
        val stale = transaction {
            OrdersTable.selectAll().where {
                (OrdersTable.status eq OrderStatus.MENUNGGU_KONFIRMASI.name) and (OrdersTable.updatedAt less cutoff)
            }.map { it[OrdersTable.id] }
        }
        var count = 0
        for (orderId in stale) {
            try {
                transaction { transitionInTx(orderId, OrderStatus.SELESAI, null, OrderStateMachine.Actor.SYSTEM) }
                count++
            } catch (e: IllegalTransitionException) {
                // someone else transitioned it meanwhile; fine
            }
        }
        return count
    }

    private fun randomDigits(length: Int): String {
        val sb = StringBuilder()
        repeat(length) { sb.append(secureRandom.nextInt(10)) }
        return sb.toString()
    }
}
