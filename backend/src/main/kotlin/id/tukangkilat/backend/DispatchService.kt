package id.tukangkilat.backend

import id.tukangkilat.shared.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.util.concurrent.ConcurrentHashMap

/**
 * Assigns waiting orders to nearby verified online technicians.
 * Two sweeps run on a schedule: offer creation and offer/payment/auto-confirm maintenance.
 */
object DispatchService {

    private const val MAX_OFFERS_PER_ORDER = 5
    private const val OFFER_TTL_SECONDS = 60L
    private const val MAX_SEARCH_MINUTES = 10L
    private const val NO_TECH_NOTICE_COOLDOWN_MS = 10 * 60 * 1000L

    /** Last time a no-technician notice was sent per order. Process-local by design. */
    private val lastNoTechnicianNotice = ConcurrentHashMap<Long, Long>()

    /** Run one full dispatch pass. Safe to call frequently. */
    suspend fun dispatchRound() {
        val waiting = transaction {
            OrdersTable.selectAll().where {
                (OrdersTable.status eq OrderStatus.MENCARI_TEKNISI.name) or
                    (OrdersTable.status eq OrderStatus.KUNJUNGAN_ULANG.name)
            }.toList()
        }

        for (order in waiting) {
            val orderId = order[OrdersTable.id]
            val orderStatus = OrderStatus.valueOf(order[OrdersTable.status])
            try {
                offerNextTechnician(orderId, orderStatus)
            } catch (e: Exception) {
                // log and leave for next tick; dispatch must never crash the loop
                e.printStackTrace()
            }
        }
    }

    private suspend fun offerNextTechnician(orderId: Long, orderStatus: OrderStatus) {
        val loaded = transaction {
            val row = OrderQueries.loadOrderRow(orderId)
            val addr = OrderQueries.loadAddress(row[OrdersTable.addressId]) ?: return@transaction null
            row to addr
        } ?: return
        val orderRow = loaded.first
        val addressRow = loaded.second

        val offerState = transaction {
            val now = OffsetDateTime.now()
            // expire stale MENUNGGU offers for this order
            JobOffersTable.update({
                (JobOffersTable.orderId eq orderId) and
                    (JobOffersTable.status eq OfferStatus.MENUNGGU.name) and
                    (JobOffersTable.expiresAt less now)
            }) { it[JobOffersTable.status] = OfferStatus.KEDALUWARSA.name }

            val offers = JobOffersTable.selectAll().where { JobOffersTable.orderId eq orderId }.toList()
            val activeOffer = offers.firstOrNull { it[JobOffersTable.status] == OfferStatus.MENUNGGU.name }
            if (activeOffer != null) return@transaction OfferSnapshot(active = true, attempts = offers.size, exhausted = false)

            if (offers.size >= MAX_OFFERS_PER_ORDER) {
                return@transaction OfferSnapshot(active = false, attempts = offers.size, exhausted = true)
            }
            OfferSnapshot(active = false, attempts = offers.size, exhausted = false, offeredTechnicianIds = offers.map { it[JobOffersTable.technicianId] })
        }

        if (offerState.active) return

        if (offerState.exhausted) {
            // notify the consumer that no technician is available right now
            notifyNoTechnician(orderId)
            return
        }

        val candidates = findCandidates(orderId, orderRow, addressRow, offerState.offeredTechnicianIds)
        val next = candidates.firstOrNull() ?: run {
            // no candidate right now; if the search has been running long enough, surface it
            val ageMinutes = java.time.Duration.between(orderRow[OrdersTable.createdAt], OffsetDateTime.now()).toMinutes()
            if (ageMinutes >= MAX_SEARCH_MINUTES) {
                notifyNoTechnician(orderId)
            }
            return
        }

        var createdOfferId: Long? = null
        val inserted = transaction {
            val now = OffsetDateTime.now()
            // Re-check inside the insert transaction: the scheduled tick and a manual dispatch
            // may both have passed the earlier check.
            val hasActive = JobOffersTable.selectAll().where {
                (JobOffersTable.orderId eq orderId) and (JobOffersTable.status eq OfferStatus.MENUNGGU.name)
            }.any()
            val alreadyOffered = JobOffersTable.selectAll().where {
                (JobOffersTable.orderId eq orderId) and (JobOffersTable.technicianId eq next)
            }.any()
            if (hasActive || alreadyOffered) {
                false
            } else {
                createdOfferId = JobOffersTable.insert {
                    it[JobOffersTable.orderId] = orderId
                    it[JobOffersTable.technicianId] = next
                    it[JobOffersTable.status] = OfferStatus.MENUNGGU.name
                    it[JobOffersTable.expiresAt] = now.plusSeconds(OFFER_TTL_SECONDS)
                    it[JobOffersTable.isRevisit] = (orderStatus == OrderStatus.KUNJUNGAN_ULANG)
                    it[JobOffersTable.createdAt] = now
                } get JobOffersTable.id
                true
            }
        }
        if (inserted) {
            if (createdOfferId != null && TechnicianOpsService.shouldAutoAccept(next, orderId)) {
                runCatching { TechnicianService.acceptOffer(next, createdOfferId!!) }
            }
            WsHub.sendTo(next, WsEvent(WsEventType.OFFER_NEW, orderId))
        }
    }

    private suspend fun notifyNoTechnician(orderId: Long) {
        // No technician after the search window ends: cancel and refund, then notify once.
        // The consumer can reschedule or cancel from the searching screen; do not cancel here.
        // Cooldown, not a permanent flag: the order may re-enter the search state later.
        // A process-local map also avoids writing an unknown status into order_events,
        // which would break OrderQueries.loadEvents when the detail screen opens.
        val nowMs = System.currentTimeMillis()
        val previous = lastNoTechnicianNotice.putIfAbsent(orderId, nowMs)
        if (previous != null && nowMs - previous < NO_TECH_NOTICE_COOLDOWN_MS) return
        lastNoTechnicianNotice[orderId] = nowMs
        val consumerId = transaction { OrderQueries.loadOrderRow(orderId)[OrdersTable.consumerId] }
        val payload = kotlinx.serialization.json.JsonObject(
            mapOf("reason" to kotlinx.serialization.json.JsonPrimitive("TIDAK_ADA_TEKNISI"))
        )
        WsHub.sendTo(consumerId, WsEvent(WsEventType.ORDER_STATUS, orderId, payload))
    }

    private data class OfferSnapshot(
        val active: Boolean,
        val attempts: Int,
        val exhausted: Boolean,
        val offeredTechnicianIds: List<Long> = emptyList()
    )

    /** Candidate technicians: verified, online, skilled, within radius, conflict-free, nearest first. */
    private fun findCandidates(
        orderId: Long,
        orderRow: ResultRow,
        addressRow: ResultRow,
        alreadyOffered: List<Long>
    ): List<Long> {
        val serviceSkill = transaction {
            ServicesTable.selectAll().where { ServicesTable.id eq orderRow[OrdersTable.serviceId] }
                .single()[ServicesTable.skill]
        }
        val addressLat = addressRow[AddressesTable.lat]
        val addressLng = addressRow[AddressesTable.lng]
        val scheduledDate = orderRow[OrdersTable.scheduledDate]
        val slotId = orderRow[OrdersTable.slotId]
        val originalTechnician = orderRow[OrdersTable.technicianId] as Long?
        val now = OffsetDateTime.now()

        return transaction {
            (TechnicianProfilesTable innerJoin UsersTable).selectAll()
                .where {
                    (TechnicianProfilesTable.verificationStatus eq TechnicianVerificationStatus.TERVERIFIKASI.name) and
                        (TechnicianProfilesTable.isOnline eq true) and
                        (UsersTable.status eq "AKTIF")
                }.toList()
                .asSequence()
                .filter { row -> row[UsersTable.id] !in alreadyOffered }
                .filter { row ->
                    row[TechnicianProfilesTable.skills].split(",").map { it.trim().uppercase() }.contains(serviceSkill)
                }
                .filter { row ->
                    val lat = row[TechnicianProfilesTable.lastLat]
                    val lng = row[TechnicianProfilesTable.lastLng]
                    val lastAt = row[TechnicianProfilesTable.lastLocationAt]
                    if (lat == null || lng == null || lastAt == null) return@filter false
                    // Demo-friendly window: going online refreshes GPS, but a slightly older
                    // fix must not make the order spin until cancellation.
                    if (java.time.Duration.between(lastAt, now).toMinutes() > 120) return@filter false
                    val distance = Geo.haversineKm(lat, lng, addressLat, addressLng)
                    distance <= row[TechnicianProfilesTable.serviceRadiusKm]
                }
                .filter { row ->
                    // technician availability blocks exclude them from this date entirely
                    val techId = row[UsersTable.id]
                    AvailabilityBlocksTable.selectAll().where {
                        (AvailabilityBlocksTable.technicianId eq techId) and
                            (AvailabilityBlocksTable.blockDate eq scheduledDate)
                    }.none()
                }
                .filter { row ->
                    // v4 service areas can exclude a technician from this location
                    !TechnicianOpsService.isAreaBlocked(row[UsersTable.id], addressLat, addressLng)
                }
                .filter { row ->
                    // no schedule conflict: no other order at the same date+slot assigned to this technician
                    val techId = row[UsersTable.id]
                    val conflict = OrdersTable.selectAll().where {
                        (OrdersTable.technicianId eq techId) and
                            (OrdersTable.scheduledDate eq scheduledDate) and
                            (OrdersTable.slotId eq slotId) and
                            (OrdersTable.id neq orderId) and
                            (OrdersTable.status neq OrderStatus.DIBATALKAN.name) and
                            (OrdersTable.status neq OrderStatus.PEMBAYARAN_GAGAL.name) and
                            (OrdersTable.status neq OrderStatus.SELESAI.name)
                    }.any()
                    !conflict
                }
                .map { row ->
                    val lat = row[TechnicianProfilesTable.lastLat]!!
                    val lng = row[TechnicianProfilesTable.lastLng]!!
                    val distance = Geo.haversineKm(lat, lng, addressLat, addressLng)
                    Triple(row[UsersTable.id], distance, row[TechnicianProfilesTable.ratingAvg])
                }
                .sortedWith(compareBy({ it.second }, { -it.third }))
                .map { it.first }
                .toList()
                .let { list ->
                    // for revisit orders, prioritize the technician who handled the original job
                    if (originalTechnician != null && originalTechnician in list) {
                        listOf(originalTechnician) + (list - originalTechnician)
                    } else list
                }
        }
    }

    /** Maintenance pass: expire stale offers, fail stale payments, auto-complete confirmations. */
    suspend fun maintenanceRound() {
        try {
            OrderService.expireStalePayments()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            OrderService.autoCompleteStaleConfirmation(24)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
