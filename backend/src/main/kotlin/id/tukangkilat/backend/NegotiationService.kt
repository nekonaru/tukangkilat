package id.tukangkilat.backend

import id.tukangkilat.shared.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.time.OffsetDateTime

/** Direct chat and price proposals between a consumer and a technician. */
object NegotiationService {

    fun listMessages(userId: Long, peerId: Long): List<DirectMessageDto> = transaction {
        DirectMessagesTable.selectAll().where {
            ((DirectMessagesTable.consumerId eq userId) and (DirectMessagesTable.technicianId eq peerId)) or
                ((DirectMessagesTable.consumerId eq peerId) and (DirectMessagesTable.technicianId eq userId))
        }.orderBy(DirectMessagesTable.createdAt to SortOrder.ASC).limit(200).map { row ->
            val sender = UsersTable.selectAll().where { UsersTable.id eq row[DirectMessagesTable.senderId] }
                .singleOrNull()?.get(UsersTable.fullName) ?: "Pengguna"
            DirectMessageDto(
                id = row[DirectMessagesTable.id],
                consumerId = row[DirectMessagesTable.consumerId],
                technicianId = row[DirectMessagesTable.technicianId],
                senderId = row[DirectMessagesTable.senderId],
                senderName = sender,
                body = row[DirectMessagesTable.body],
                createdAt = row[DirectMessagesTable.createdAt].toString()
            )
        }
    }

    suspend fun sendMessage(userId: Long, peerId: Long, body: String, consumerSide: Boolean): DirectMessageDto {
        val text = body.trim()
        if (text.isBlank()) throw BadRequestException("Pesan tidak boleh kosong")
        if (text.length > 2000) throw BadRequestException("Pesan maksimal 2000 karakter")
        val pair = if (consumerSide) userId to peerId else peerId to userId
        val created = transaction {
            DirectMessagesTable.insert {
                it[consumerId] = pair.first
                it[technicianId] = pair.second
                it[senderId] = userId
                it[DirectMessagesTable.body] = text
                it[createdAt] = OffsetDateTime.now()
            } get DirectMessagesTable.id
        }
        WsHub.sendTo(peerId, WsEvent(WsEventType.CHAT_MESSAGE, null))
        return listMessages(userId, peerId).first { it.id == created }
    }

    fun recentPeers(userId: Long, consumerSide: Boolean): List<Long> = transaction {
        val column = if (consumerSide) DirectMessagesTable.technicianId else DirectMessagesTable.consumerId
        DirectMessagesTable.select(column).where {
            if (consumerSide) DirectMessagesTable.consumerId eq userId else DirectMessagesTable.technicianId eq userId
        }.map { it[column] }.distinct().take(50)
    }

    suspend fun createProposal(technicianId: Long, req: CreateProposalRequest): ProposalDto {
        if (req.description.isBlank()) throw BadRequestException("Ceritakan permasalahannya")
        if (req.price <= 0) throw BadRequestException("Harga harus lebih dari 0")
        val id = transaction {
            ProposalsTable.insert {
                it[ProposalsTable.consumerId] = req.consumerId
                it[ProposalsTable.technicianId] = technicianId
                it[serviceId] = req.serviceId
                it[description] = req.description.trim()
                it[price] = req.price
                it[scheduledDate] = LocalDate.parse(req.scheduledDate)
                it[customTime] = req.customTime?.trim()?.ifBlank { null }
                it[status] = "MENUNGGU"
                it[createdAt] = OffsetDateTime.now()
            } get ProposalsTable.id
        }
        WsHub.sendTo(req.consumerId, WsEvent(WsEventType.CHAT_MESSAGE, null))
        return getProposal(id)
    }

    fun proposalsFor(userId: Long, consumerSide: Boolean): List<ProposalDto> = transaction {
        ProposalsTable.selectAll().where {
            if (consumerSide) ProposalsTable.consumerId eq userId else ProposalsTable.technicianId eq userId
        }.orderBy(ProposalsTable.createdAt to SortOrder.DESC).limit(50).map { toDto(it) }
    }

    suspend fun respond(consumerId: Long, proposalId: Long, accept: Boolean): ProposalDto {
        val proposal = transaction {
            val row = ProposalsTable.selectAll().where {
                (ProposalsTable.id eq proposalId) and (ProposalsTable.consumerId eq consumerId)
            }.singleOrNull() ?: throw NotFoundException("Penawaran tidak ditemukan")
            if (row[ProposalsTable.status] != "MENUNGGU") throw ConflictException("Penawaran sudah diproses")
            ProposalsTable.update({ ProposalsTable.id eq proposalId }) {
                it[status] = if (accept) "DITERIMA" else "DITOLAK"
            }
            toDto(row)
        }
        WsHub.sendTo(proposal.technicianId, WsEvent(WsEventType.CHAT_MESSAGE, null))
        return proposal
    }

    /** Creates the actual order when the consumer accepts a negotiated price. */
    fun createOrderFromProposal(proposalId: Long): Long = transaction {
        val proposal = ProposalsTable.selectAll().where { ProposalsTable.id eq proposalId }
            .singleOrNull() ?: throw NotFoundException("Penawaran tidak ditemukan")
        if (proposal[ProposalsTable.status] != "DITERIMA") throw ConflictException("Penawaran belum diterima")
        val service = ServicesTable.selectAll()
            .where { ServicesTable.id eq proposal[ProposalsTable.serviceId] }.single()
        val visitFee = service[ServicesTable.visitFee]
        val serviceFee = (proposal[ProposalsTable.price] - visitFee).coerceAtLeast(0)
        val timeLabel = proposal[ProposalsTable.customTime] ?: "09.00"
        val rawSlot = TimeSlotsTable.selectAll().where { TimeSlotsTable.label eq timeLabel }.singleOrNull()
            ?.get(TimeSlotsTable.id) ?: TimeSlotsTable.insert {
                it[label] = timeLabel
                it[capacity] = 999
            } get TimeSlotsTable.id
        val slot: Long = rawSlot.toString().toLongOrNull()
            ?: throw IllegalStateException("Slot tidak valid")
        val address = AddressesTable.selectAll()
            .where { (AddressesTable.userId eq proposal[ProposalsTable.consumerId]) and (AddressesTable.isPrimary eq true) }
            .singleOrNull() ?: throw NotFoundException("Alamat utama konsumen tidak ditemukan")
        val now = OffsetDateTime.now()
        val code = OrderService.generateOrderCodeForNegotiation()
        OrdersTable.insert {
            it[OrdersTable.code] = code
            it[consumerId] = proposal[ProposalsTable.consumerId]
            it[technicianId] = proposal[ProposalsTable.technicianId]
            it[serviceId] = proposal[ProposalsTable.serviceId]
            it[description] = proposal[ProposalsTable.description]
            it[addressId] = address[AddressesTable.id]
            it[scheduledDate] = proposal[ProposalsTable.scheduledDate]
            it[OrdersTable.slotId] = slot
            it[status] = OrderStatus.TEKNISI_DITEMUKAN.name
            it[this.serviceFee] = serviceFee
            it[this.visitFee] = visitFee
            it[discount] = 0L
            it[total] = proposal[ProposalsTable.price]
            it[paymentMethod] = PaymentMethod.TUNAI.name
            it[paymentStatus] = PaymentStatus.LUNAS.name
            it[createdAt] = now
            it[updatedAt] = now
        } get OrdersTable.id
    }

    private fun toDto(row: ResultRow): ProposalDto {
        val technician = UsersTable.selectAll().where { UsersTable.id eq row[ProposalsTable.technicianId] }
            .singleOrNull()?.get(UsersTable.fullName) ?: "Teknisi"
        val service = ServicesTable.selectAll().where { ServicesTable.id eq row[ProposalsTable.serviceId] }
            .singleOrNull()?.get(ServicesTable.name) ?: "Layanan"
        return ProposalDto(
            id = row[ProposalsTable.id],
            consumerId = row[ProposalsTable.consumerId],
            technicianId = row[ProposalsTable.technicianId],
            technicianName = technician,
            serviceId = row[ProposalsTable.serviceId],
            serviceName = service,
            description = row[ProposalsTable.description],
            price = row[ProposalsTable.price],
            scheduledDate = row[ProposalsTable.scheduledDate].toString(),
            customTime = row[ProposalsTable.customTime],
            status = row[ProposalsTable.status],
            createdAt = row[ProposalsTable.createdAt].toString()
        )
    }

    private fun getProposal(id: Long): ProposalDto = transaction {
        toDto(ProposalsTable.selectAll().where { ProposalsTable.id eq id }.single())
    }
}
