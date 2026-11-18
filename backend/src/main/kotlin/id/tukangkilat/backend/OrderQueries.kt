package id.tukangkilat.backend

import id.tukangkilat.shared.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/** Order reads and common order operations, shared by consumer and technician. */
object OrderQueries {

    private val ACTIVE_STATUSES = setOf(
        OrderStatus.TEKNISI_DITEMUKAN,
        OrderStatus.DALAM_PERJALANAN,
        OrderStatus.TIBA,
        OrderStatus.SEDANG_BEKERJA,
        OrderStatus.MENUNGGU_KONFIRMASI
    )

    fun isActiveStatus(status: OrderStatus): Boolean = status in ACTIVE_STATUSES

    fun loadServiceName(serviceId: Long): String =
        ServicesTable.selectAll().where { ServicesTable.id eq serviceId }.singleOrNull()?.get(ServicesTable.name) ?: ""

    fun loadSlotLabel(slotId: Long): String =
        TimeSlotsTable.selectAll().where { TimeSlotsTable.id eq slotId }.singleOrNull()?.get(TimeSlotsTable.label) ?: ""

    fun loadAddress(addressId: Long): ResultRow? =
        AddressesTable.selectAll().where { AddressesTable.id eq addressId }.singleOrNull()

    fun loadOrderRow(orderId: Long): ResultRow =
        OrdersTable.selectAll().where { OrdersTable.id eq orderId }.singleOrNull()
            ?: throw NotFoundException("Pesanan tidak ditemukan")

    /** Verify the caller can access [row]: owner consumer, assigned technician, or admin. */
    fun assertCanAccess(row: ResultRow, user: AuthUser) {
        val ok = when (user.role) {
            Role.ADMIN -> true
            Role.KONSUMEN -> row[OrdersTable.consumerId] == user.id
            Role.TUKANG -> row[OrdersTable.technicianId] == user.id
        }
        if (!ok) throw ForbiddenException("Anda tidak punya akses ke pesanan ini")
    }

    fun loadPhotos(orderId: Long): List<OrderPhotoDto> =
        (OrderPhotosTable innerJoin PhotosTable).selectAll()
            .where { OrderPhotosTable.orderId eq orderId }
            .map {
                OrderPhotoDto(
                    id = it[PhotosTable.id],
                    kind = PhotoKind.valueOf(it[OrderPhotosTable.kind]),
                    url = "/photos/${it[PhotosTable.publicId]}"
                )
            }

    fun loadEvents(orderId: Long): List<OrderEventDto> =
        OrderEventsTable.selectAll().where { OrderEventsTable.orderId eq orderId }
            .orderBy(OrderEventsTable.createdAt to SortOrder.ASC, OrderEventsTable.id to SortOrder.ASC)
            .map {
                OrderEventDto(
                    status = OrderStatus.valueOf(it[OrderEventsTable.status]),
                    actorId = it[OrderEventsTable.actorId],
                    createdAt = it[OrderEventsTable.createdAt].toString()
                )
            }

    fun loadTechnicianPublic(technicianId: Long): TechnicianPublicDto? =
        UsersTable.selectAll().where { UsersTable.id eq technicianId }.singleOrNull()?.let { u ->
            val profile = TechnicianProfilesTable.selectAll().where { TechnicianProfilesTable.userId eq technicianId }.singleOrNull()
            TechnicianPublicDto(
                id = technicianId,
                fullName = u[UsersTable.fullName],
                ratingAvg = profile?.get(TechnicianProfilesTable.ratingAvg) ?: 0.0,
                jobsCount = profile?.get(TechnicianProfilesTable.jobsCount) ?: 0,
                photoUrl = profile?.get(TechnicianProfilesTable.photoUrl),
                phone = u[UsersTable.phone]
            )
        }

    fun loadEta(row: ResultRow): EtaDto? {
        val technicianId = row[OrdersTable.technicianId] ?: return null
        val status = OrderStatus.valueOf(row[OrdersTable.status])
        if (status !in setOf(OrderStatus.TEKNISI_DITEMUKAN, OrderStatus.DALAM_PERJALANAN, OrderStatus.TIBA, OrderStatus.SEDANG_BEKERJA)) return null
        val profile = TechnicianProfilesTable.selectAll().where { TechnicianProfilesTable.userId eq technicianId }.singleOrNull() ?: return null
        val tLat = profile[TechnicianProfilesTable.lastLat] ?: return null
        val tLng = profile[TechnicianProfilesTable.lastLng] ?: return null
        val address = loadAddress(row[OrdersTable.addressId]) ?: return null
        val distance = Geo.haversineKm(tLat, tLng, address[AddressesTable.lat], address[AddressesTable.lng])
        val minutes = Geo.estimateMinutes(distance)
        val arriveAt = OffsetDateTime.now().plusMinutes(minutes.toLong())
        return EtaDto(distanceKm = Math.round(distance * 10) / 10.0, minutes = minutes, arriveAt = arriveAt.toLocalTime().format(DateTimeFormatter.ofPattern("HH.mm")))
    }

    fun toOrderDto(row: ResultRow): OrderDto {
        val service = ServicesTable.selectAll().where { ServicesTable.id eq row[OrdersTable.serviceId] }.single()
        val address = loadAddress(row[OrdersTable.addressId])!!
        val technicianId = row[OrdersTable.technicianId]
        val technicianProfile = technicianId?.let {
            TechnicianProfilesTable.selectAll().where { TechnicianProfilesTable.userId eq it }.singleOrNull()
        }
        return OrderDto(
            id = row[OrdersTable.id],
            code = row[OrdersTable.code],
            status = OrderStatus.valueOf(row[OrdersTable.status]),
            serviceId = row[OrdersTable.serviceId],
            serviceName = service[ServicesTable.name],
            description = row[OrdersTable.description],
            addressId = row[OrdersTable.addressId],
            addressLabel = address[AddressesTable.label],
            addressFull = address[AddressesTable.fullAddress],
            addressLat = address[AddressesTable.lat],
            addressLng = address[AddressesTable.lng],
            scheduledDate = row[OrdersTable.scheduledDate].toString(),
            slotLabel = loadSlotLabel(row[OrdersTable.slotId]),
            serviceFee = row[OrdersTable.serviceFee],
            visitFee = row[OrdersTable.visitFee],
            discount = row[OrdersTable.discount],
            total = row[OrdersTable.total],
            paymentMethod = row[OrdersTable.paymentMethod]?.let { PaymentMethod.valueOf(it) },
            paymentStatus = PaymentStatus.valueOf(row[OrdersTable.paymentStatus]),
            finishNote = row[OrdersTable.finishNote],
            technician = technicianId?.let { loadTechnicianPublic(it) },
            technicianLat = technicianProfile?.get(TechnicianProfilesTable.lastLat),
            technicianLng = technicianProfile?.get(TechnicianProfilesTable.lastLng),
            eta = loadEta(row),
            photos = loadPhotos(row[OrdersTable.id]),
            events = loadEvents(row[OrdersTable.id]),
            createdAt = row[OrdersTable.createdAt].toString()
        )
    }

    fun toOrderSummary(row: ResultRow): OrderSummaryDto {
        val service = ServicesTable.selectAll().where { ServicesTable.id eq row[OrdersTable.serviceId] }.single()
        return OrderSummaryDto(
            id = row[OrdersTable.id],
            code = row[OrdersTable.code],
            status = OrderStatus.valueOf(row[OrdersTable.status]),
            serviceName = service[ServicesTable.name],
            scheduledDate = row[OrdersTable.scheduledDate].toString(),
            slotLabel = loadSlotLabel(row[OrdersTable.slotId]),
            total = row[OrdersTable.total],
            paymentStatus = PaymentStatus.valueOf(row[OrdersTable.paymentStatus]),
            createdAt = row[OrdersTable.createdAt].toString()
        )
    }
}
