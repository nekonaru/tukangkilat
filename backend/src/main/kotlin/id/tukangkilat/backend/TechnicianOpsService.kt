package id.tukangkilat.backend

import id.tukangkilat.shared.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.time.OffsetDateTime

/** v4 technician operations: documents, areas, auto accept, field work and performance. */
object TechnicianOpsService {

    // ---------- Documents ----------

    fun documents(technicianId: Long): List<TechDocumentDto> = transaction {
        TechDocumentsTable.selectAll().where { TechDocumentsTable.technicianId eq technicianId }
            .orderBy(TechDocumentsTable.createdAt to SortOrder.DESC).map(::documentDto)
    }

    private fun documentDto(row: ResultRow) = TechDocumentDto(
        row[TechDocumentsTable.id], row[TechDocumentsTable.docType], row[TechDocumentsTable.fileUrl],
        row[TechDocumentsTable.expiresAt]?.toString(), row[TechDocumentsTable.status],
        row[TechDocumentsTable.note], row[TechDocumentsTable.createdAt].toString()
    )

    fun addDocument(technicianId: Long, req: AddDocumentRequest): TechDocumentDto = transaction {
        if (req.docType.isBlank() || req.fileUrl.isBlank()) throw BadRequestException("Jenis dan berkas wajib")
        if (req.docType.trim().length > 32 || req.fileUrl.length > 512) throw BadRequestException("Data dokumen terlalu panjang")
        val normalizedType = req.docType.trim().uppercase()
        val expires = req.expiresAt?.takeIf { it.isNotBlank() }?.let {
            try { LocalDate.parse(it) } catch (e: Exception) { throw BadRequestException("Format tanggal tidak valid") }
        }
        val existing = TechDocumentsTable.selectAll().where {
            (TechDocumentsTable.technicianId eq technicianId) and (TechDocumentsTable.docType eq normalizedType)
        }.singleOrNull()
        val id = if (existing == null) {
            TechDocumentsTable.insert {
                it[this.technicianId] = technicianId
                it[docType] = normalizedType
                it[fileUrl] = req.fileUrl.trim()
                it[this.expiresAt] = expires
                it[status] = "MENUNGGU"
                it[note] = ""
                it[createdAt] = OffsetDateTime.now()
            } get TechDocumentsTable.id
        } else {
            TechDocumentsTable.update({ TechDocumentsTable.id eq existing[TechDocumentsTable.id] }) {
                it[fileUrl] = req.fileUrl.trim()
                it[this.expiresAt] = expires
                it[status] = "MENUNGGU"
                it[note] = ""
            }
            existing[TechDocumentsTable.id]
        }
        documentDto(TechDocumentsTable.selectAll().where { TechDocumentsTable.id eq id }.single())
    }

    fun adminDocuments(status: String?): List<TechDocumentDto> = transaction {
        val query = if (status.isNullOrBlank()) TechDocumentsTable.selectAll()
        else TechDocumentsTable.selectAll().where { TechDocumentsTable.status eq status }
        query.orderBy(TechDocumentsTable.createdAt to SortOrder.DESC).limit(100).map(::documentDto)
    }

    fun verifyDocument(adminId: Long, id: Long, approve: Boolean, note: String) = transaction {
        val row = TechDocumentsTable.selectAll().where { TechDocumentsTable.id eq id }
            .singleOrNull() ?: throw NotFoundException("Dokumen tidak ditemukan")
        TechDocumentsTable.update({ TechDocumentsTable.id eq id }) {
            it[status] = if (approve) "TERVERIFIKASI" else "DITOLAK"
            it[this.note] = note.take(255)
        }
        EcosystemService.log(adminId, "VERIFY_DOCUMENT", id.toString())
        documentDto(TechDocumentsTable.selectAll().where { TechDocumentsTable.id eq id }.single())
    }

    fun expiringDocuments(technicianId: Long): List<TechDocumentDto> = transaction {
        val limit = LocalDate.now().plusDays(30)
        TechDocumentsTable.selectAll().where {
            (TechDocumentsTable.technicianId eq technicianId) and
                (TechDocumentsTable.expiresAt lessEq limit)
        }.map(::documentDto)
    }

    // ---------- Service areas ----------

    fun areas(technicianId: Long): List<ServiceAreaDto> = transaction {
        ServiceAreasTable.selectAll().where { ServiceAreasTable.technicianId eq technicianId }
            .orderBy(ServiceAreasTable.createdAt to SortOrder.ASC).map {
                ServiceAreaDto(
                    it[ServiceAreasTable.id], it[ServiceAreasTable.areaName], it[ServiceAreasTable.lat],
                    it[ServiceAreasTable.lng], it[ServiceAreasTable.radiusKm], it[ServiceAreasTable.isExcluded]
                )
            }
    }

    fun addArea(technicianId: Long, req: AddServiceAreaRequest): ServiceAreaDto = transaction {
        if (req.areaName.isBlank()) throw BadRequestException("Nama wilayah wajib")
        if (req.radiusKm !in 1..100) throw BadRequestException("Radius 1 sampai 100 km")
        val id = ServiceAreasTable.insert {
            it[this.technicianId] = technicianId
            it[areaName] = req.areaName.trim().take(120)
            it[lat] = req.lat
            it[lng] = req.lng
            it[radiusKm] = req.radiusKm
            it[isExcluded] = req.isExcluded
            it[createdAt] = OffsetDateTime.now()
        } get ServiceAreasTable.id
        areas(technicianId).first { it.id == id }
    }

    fun deleteArea(technicianId: Long, id: Long) = transaction {
        val deleted = ServiceAreasTable.deleteWhere {
            with(SqlExpressionBuilder) {
                (ServiceAreasTable.id eq id) and (ServiceAreasTable.technicianId eq technicianId)
            }
        }
        if (deleted == 0) throw NotFoundException("Wilayah tidak ditemukan")
    }

    internal fun isAreaBlocked(technicianId: Long, lat: Double, lng: Double): Boolean = transaction {
        ServiceAreasTable.selectAll().where { ServiceAreasTable.technicianId eq technicianId }
            .any { row ->
                val distance = Geo.haversineKm(lat, lng, row[ServiceAreasTable.lat], row[ServiceAreasTable.lng])
                distance <= row[ServiceAreasTable.radiusKm] && row[ServiceAreasTable.isExcluded]
            }
    }

    // ---------- Auto accept ----------

    fun autoAccept(technicianId: Long): AutoAcceptDto = transaction {
        val row = AutoAcceptRulesTable.selectAll()
            .where { AutoAcceptRulesTable.technicianId eq technicianId }.singleOrNull()
        if (row == null) AutoAcceptDto(false, 200_000, 20, "")
        else AutoAcceptDto(
            row[AutoAcceptRulesTable.enabled], row[AutoAcceptRulesTable.minPrice],
            row[AutoAcceptRulesTable.maxDistance], row[AutoAcceptRulesTable.serviceIds]
        )
    }

    fun saveAutoAccept(technicianId: Long, req: SaveAutoAcceptRequest) = transaction {
        if (req.minPrice < 0 || req.maxDistance !in 1..100 || req.serviceIds.length > 255) {
            throw BadRequestException("Aturan terima otomatis tidak valid")
        }
        val exists = AutoAcceptRulesTable.selectAll()
            .where { AutoAcceptRulesTable.technicianId eq technicianId }.any()
        if (exists) {
            AutoAcceptRulesTable.update({ AutoAcceptRulesTable.technicianId eq technicianId }) {
                it[enabled] = req.enabled
                it[minPrice] = req.minPrice
                it[maxDistance] = req.maxDistance
                it[serviceIds] = req.serviceIds
                it[updatedAt] = OffsetDateTime.now()
            }
        } else {
            AutoAcceptRulesTable.insert {
                it[this.technicianId] = technicianId
                it[enabled] = req.enabled
                it[minPrice] = req.minPrice
                it[maxDistance] = req.maxDistance
                it[serviceIds] = req.serviceIds
                it[updatedAt] = OffsetDateTime.now()
            }
        }
        autoAccept(technicianId)
    }

    internal fun shouldAutoAccept(technicianId: Long, orderId: Long): Boolean = transaction {
        val rule = autoAccept(technicianId)
        if (!rule.enabled) return@transaction false
        val order = OrderQueries.loadOrderRow(orderId)
        if (order[OrdersTable.total] < rule.minPrice) return@transaction false
        if (rule.serviceIds.isNotBlank() && order[OrdersTable.serviceId].toString() !in
            rule.serviceIds.split(",").map { it.trim() }) {
            return@transaction false
        }
        val tech = TechnicianProfilesTable.selectAll()
            .where { TechnicianProfilesTable.userId eq technicianId }.singleOrNull()
        if (tech?.get(TechnicianProfilesTable.lastLat) != null && tech[TechnicianProfilesTable.lastLng] != null) {
            val address = AddressesTable.selectAll()
                .where { AddressesTable.id eq order[OrdersTable.addressId] }.singleOrNull()
            if (address != null) {
                val distance = Geo.haversineKm(
                    tech[TechnicianProfilesTable.lastLat]!!, tech[TechnicianProfilesTable.lastLng]!!,
                    address[AddressesTable.lat], address[AddressesTable.lng]
                )
                if (distance > rule.maxDistance) return@transaction false
            }
        }
        true
    }

    // ---------- Field operations ----------

    fun route(technicianId: Long): List<RouteStopDto> = transaction {
        val tech = TechnicianProfilesTable.selectAll()
            .where { TechnicianProfilesTable.userId eq technicianId }.singleOrNull()
        var lat = tech?.get(TechnicianProfilesTable.lastLat)
        var lng = tech?.get(TechnicianProfilesTable.lastLng)
        val today = LocalDate.now()
        val orders = OrdersTable.selectAll().where {
            (OrdersTable.technicianId eq technicianId) and
                (OrdersTable.status eq OrderStatus.DALAM_PERJALANAN.name) and
                (OrdersTable.scheduledDate eq today)
        }.orderBy(OrdersTable.scheduledDate to SortOrder.ASC).toList()
        if ((lat == null || lng == null) && orders.isNotEmpty()) {
            val firstAddress = AddressesTable.selectAll()
                .where { AddressesTable.id eq orders.first()[OrdersTable.addressId] }.singleOrNull()
            lat = firstAddress?.get(AddressesTable.lat) ?: 0.0
            lng = firstAddress?.get(AddressesTable.lng) ?: 0.0
        }
        val originLat = lat ?: 0.0
        val originLng = lng ?: 0.0
        orders.map { row ->
            val address = AddressesTable.selectAll().where { AddressesTable.id eq row[OrdersTable.addressId] }.singleOrNull()
            val service = ServicesTable.selectAll().where { ServicesTable.id eq row[OrdersTable.serviceId] }.singleOrNull()
            RouteStopDto(
                row[OrdersTable.id], row[OrdersTable.code], service?.get(ServicesTable.name) ?: "Layanan",
                address?.get(AddressesTable.lat) ?: 0.0, address?.get(AddressesTable.lng) ?: 0.0,
                Math.round(Geo.haversineKm(originLat, originLng, address?.get(AddressesTable.lat) ?: 0.0,
                    address?.get(AddressesTable.lng) ?: 0.0) * 10) / 10.0,
                row[OrdersTable.scheduledDate].toString(), row[OrdersTable.status]
            )
        }.sortedBy { it.distanceKm }
    }

    fun checkin(technicianId: Long, orderId: Long, req: CheckinRequest): CheckinDto = transaction {
        if (req.kind !in listOf("DATANG", "PULANG")) throw BadRequestException("Jenis check-in tidak valid")
        if (!req.lat.isFinite() || !req.lng.isFinite() || req.lat !in -90.0..90.0 || req.lng !in -180.0..180.0) {
            throw BadRequestException("Koordinat tidak valid")
        }
        val order = OrderQueries.loadOrderRow(orderId)
        if (order[OrdersTable.technicianId] != technicianId) throw ForbiddenException("Bukan pesanan Anda")
        val status = OrderStatus.valueOf(order[OrdersTable.status])
        if (status in listOf(OrderStatus.SELESAI, OrderStatus.DIBATALKAN, OrderStatus.DILAPORKAN)) {
            throw ConflictException("Pesanan sudah ditutup")
        }
        if (req.kind == "DATANG" && status !in listOf(
                OrderStatus.DALAM_PERJALANAN, OrderStatus.TIBA, OrderStatus.SEDANG_BEKERJA
            )) {
            throw ConflictException("Check-in datang belum tersedia pada status ini")
        }
        val address = AddressesTable.selectAll().where { AddressesTable.id eq order[OrdersTable.addressId] }.singleOrNull()
            ?: throw NotFoundException("Alamat tidak ditemukan")
        val distance = Math.round(Geo.haversineKm(req.lat, req.lng, address[AddressesTable.lat], address[AddressesTable.lng]) * 1000).toInt()
        if (req.kind == "DATANG" && distance > 500) {
            throw BadRequestException("Check-in hanya dalam radius 500 meter dari lokasi")
        }
        val existing = CheckinsTable.selectAll().where {
            (CheckinsTable.orderId eq orderId) and (CheckinsTable.kind eq req.kind)
        }.singleOrNull()
        val id = if (existing != null) {
            CheckinsTable.update({ CheckinsTable.id eq existing[CheckinsTable.id] }) {
                it[lat] = req.lat
                it[lng] = req.lng
                it[distanceM] = distance
                it[createdAt] = OffsetDateTime.now()
            }
            existing[CheckinsTable.id]
        } else {
            CheckinsTable.insert {
                it[this.orderId] = orderId
                it[this.technicianId] = technicianId
                it[kind] = req.kind
                it[lat] = req.lat
                it[lng] = req.lng
                it[distanceM] = distance
                it[createdAt] = OffsetDateTime.now()
            } get CheckinsTable.id
        }
        val row = CheckinsTable.selectAll().where { CheckinsTable.id eq id }.single()
        CheckinDto(id, orderId, req.kind, row[CheckinsTable.lat], row[CheckinsTable.lng], row[CheckinsTable.distanceM], row[CheckinsTable.createdAt].toString())
    }

    fun checkins(technicianId: Long, orderId: Long): List<CheckinDto> = transaction {
        val order = OrderQueries.loadOrderRow(orderId)
        if (order[OrdersTable.technicianId] != technicianId) throw ForbiddenException("Bukan pesanan Anda")
        CheckinsTable.selectAll().where { CheckinsTable.orderId eq orderId }
            .orderBy(CheckinsTable.createdAt to SortOrder.ASC).map {
                CheckinDto(it[CheckinsTable.id], orderId, it[CheckinsTable.kind], it[CheckinsTable.lat],
                    it[CheckinsTable.lng], it[CheckinsTable.distanceM], it[CheckinsTable.createdAt].toString())
            }
    }

    fun materials(technicianId: Long, orderId: Long?): List<MaterialDto> = transaction {
        val query = if (orderId == null) MaterialsQuery(technicianId) else MaterialsQuery(technicianId, orderId)
        query.map {
            MaterialDto(it[JobMaterialsTable.id], it[JobMaterialsTable.orderId], it[JobMaterialsTable.name],
                it[JobMaterialsTable.quantity], it[JobMaterialsTable.cost])
        }
    }

    private fun MaterialsQuery(technicianId: Long, orderId: Long? = null) =
        if (orderId == null) JobMaterialsTable.selectAll().where { JobMaterialsTable.technicianId eq technicianId }
        else JobMaterialsTable.selectAll().where {
            (JobMaterialsTable.technicianId eq technicianId) and (JobMaterialsTable.orderId eq orderId)
        }

    fun addMaterial(technicianId: Long, req: AddMaterialRequest): MaterialDto = transaction {
        if (req.name.isBlank() || req.quantity <= 0 || req.cost < 0) throw BadRequestException("Data material tidak valid")
        val order = OrderQueries.loadOrderRow(req.orderId)
        if (order[OrdersTable.technicianId] != technicianId) throw ForbiddenException("Bukan pesanan Anda")
        val id = JobMaterialsTable.insert {
            it[orderId] = req.orderId
            it[this.technicianId] = technicianId
            it[name] = req.name.trim().take(120)
            it[quantity] = req.quantity
            it[cost] = req.cost
            it[createdAt] = OffsetDateTime.now()
        } get JobMaterialsTable.id
        materials(technicianId, req.orderId).first { it.id == id }
    }

    // ---------- Badges, templates, leave, journal, equipment ----------

    fun badges(technicianId: Long): List<BadgeDto> = transaction {
        evaluateBadges(technicianId)
        TechBadgesTable.selectAll().where { TechBadgesTable.technicianId eq technicianId }
            .orderBy(TechBadgesTable.earnedAt to SortOrder.DESC).map {
                BadgeDto(it[TechBadgesTable.id], it[TechBadgesTable.code], it[TechBadgesTable.title], it[TechBadgesTable.earnedAt].toString())
            }
    }

    private fun evaluateBadges(technicianId: Long) {
        val profile = TechnicianProfilesTable.selectAll()
            .where { TechnicianProfilesTable.userId eq technicianId }.singleOrNull() ?: return
        val jobs = profile[TechnicianProfilesTable.jobsCount]
        val total = JobOffersTable.selectAll().where { JobOffersTable.technicianId eq technicianId }.count().toInt()
        val accepted = JobOffersTable.selectAll().where {
            (JobOffersTable.technicianId eq technicianId) and (JobOffersTable.status eq OfferStatus.DITERIMA.name)
        }.count().toInt()
        val candidates = mutableListOf<Pair<String, String>>()
        if (jobs >= 5) candidates += "KERJA5" to "Lima pekerjaan selesai"
        if (jobs >= 25) candidates += "KERJA25" to "Dua puluh lima pekerjaan"
        if (profile[TechnicianProfilesTable.ratingAvg] >= 4.8) candidates += "RATING48" to "Rating di atas 4.8"
        if (total > 0 && accepted * 100 / total >= 80) candidates += "TERIMA80" to "Tingkat terima 80 persen"
        candidates.forEach { (code, title) ->
            if (!TechBadgesTable.selectAll().where {
                    (TechBadgesTable.technicianId eq technicianId) and (TechBadgesTable.code eq code)
                }.any()) {
                TechBadgesTable.insert {
                    it[this.technicianId] = technicianId
                    it[this.code] = code
                    it[this.title] = title
                    it[earnedAt] = OffsetDateTime.now()
                }
            }
        }
    }

    fun templates(technicianId: Long): List<TemplateDto> = transaction {
        ChatTemplatesTable.selectAll().where { ChatTemplatesTable.technicianId eq technicianId }
            .orderBy(ChatTemplatesTable.createdAt to SortOrder.DESC).map {
                TemplateDto(it[ChatTemplatesTable.id], it[ChatTemplatesTable.title], it[ChatTemplatesTable.body])
            }
    }

    fun addTemplate(technicianId: Long, req: AddTemplateRequest): TemplateDto = transaction {
        if (req.title.isBlank() || req.body.isBlank() || req.body.length > 500) throw BadRequestException("Template tidak valid")
        val id = ChatTemplatesTable.insert {
            it[this.technicianId] = technicianId
            it[title] = req.title.trim().take(80)
            it[body] = req.body.trim().take(500)
            it[createdAt] = OffsetDateTime.now()
        } get ChatTemplatesTable.id
        templates(technicianId).first { it.id == id }
    }

    fun deleteTemplate(technicianId: Long, id: Long) = transaction {
        val deleted = ChatTemplatesTable.deleteWhere {
            with(SqlExpressionBuilder) {
                (ChatTemplatesTable.id eq id) and (ChatTemplatesTable.technicianId eq technicianId)
            }
        }
        if (deleted == 0) throw NotFoundException("Template tidak ditemukan")
    }

    fun resolveLeave(adminId: Long, id: Long, approve: Boolean) = transaction {
        val row = LeaveRequestsTable.selectAll().where { LeaveRequestsTable.id eq id }
            .singleOrNull() ?: throw NotFoundException("Pengajuan libur tidak ditemukan")
        if (row[LeaveRequestsTable.status] != "MENUNGGU") throw ConflictException("Pengajuan sudah diproses")
        LeaveRequestsTable.update({ LeaveRequestsTable.id eq id }) {
            it[status] = if (approve) "DISETUJUI" else "DITOLAK"
        }
        EcosystemService.log(adminId, "RESOLVE_LEAVE", id.toString())
    }

    fun leaves(technicianId: Long): List<LeaveDto> = transaction {
        LeaveRequestsTable.selectAll().where { LeaveRequestsTable.technicianId eq technicianId }
            .orderBy(LeaveRequestsTable.startDate to SortOrder.DESC).map {
                LeaveDto(it[LeaveRequestsTable.id], it[LeaveRequestsTable.startDate].toString(),
                    it[LeaveRequestsTable.endDate].toString(), it[LeaveRequestsTable.reason], it[LeaveRequestsTable.status])
            }
    }

    fun addLeave(technicianId: Long, req: AddLeaveRequest): LeaveDto = transaction {
        val start = try { LocalDate.parse(req.startDate) } catch (e: Exception) { throw BadRequestException("Tanggal mulai tidak valid") }
        val end = try { LocalDate.parse(req.endDate) } catch (e: Exception) { throw BadRequestException("Tanggal selesai tidak valid") }
        if (end.isBefore(start) || start.isBefore(LocalDate.now())) throw BadRequestException("Rentang tanggal tidak valid")
        val id = LeaveRequestsTable.insert {
            it[this.technicianId] = technicianId
            it[startDate] = start
            it[endDate] = end
            it[reason] = req.reason.trim().take(255)
            it[status] = "MENUNGGU"
            it[createdAt] = OffsetDateTime.now()
        } get LeaveRequestsTable.id
        leaves(technicianId).first { it.id == id }
    }

    fun journal(technicianId: Long): List<JournalDto> = transaction {
        WorkJournalsTable.selectAll().where { WorkJournalsTable.technicianId eq technicianId }
            .orderBy(WorkJournalsTable.entryDate to SortOrder.DESC).limit(50).map {
                JournalDto(it[WorkJournalsTable.id], it[WorkJournalsTable.entryDate].toString(), it[WorkJournalsTable.note])
            }
    }

    fun addJournal(technicianId: Long, req: AddJournalRequest): JournalDto = transaction {
        if (req.note.isBlank()) throw BadRequestException("Catatan wajib diisi")
        val date = try { LocalDate.parse(req.entryDate) } catch (e: Exception) { throw BadRequestException("Tanggal tidak valid") }
        val id = WorkJournalsTable.insert {
            it[this.technicianId] = technicianId
            it[entryDate] = date
            it[note] = req.note.trim().take(2000)
            it[createdAt] = OffsetDateTime.now()
        } get WorkJournalsTable.id
        journal(technicianId).first { it.id == id }
    }

    fun equipment(technicianId: Long): List<EquipmentDto> = transaction {
        if (!EquipmentItemsTable.selectAll().where { EquipmentItemsTable.technicianId eq technicianId }.any()) {
            listOf("Multimeter", "Kunci pas", "Obeng set", "Tang", "Alat uji AC").forEach { name ->
                EquipmentItemsTable.insert {
                    it[this.technicianId] = technicianId
                    it[this.name] = name
                    it[checked] = false
                    it[updatedAt] = OffsetDateTime.now()
                }
            }
        }
        EquipmentItemsTable.selectAll().where { EquipmentItemsTable.technicianId eq technicianId }
            .orderBy(EquipmentItemsTable.id to SortOrder.ASC).map {
                EquipmentDto(it[EquipmentItemsTable.id], it[EquipmentItemsTable.name], it[EquipmentItemsTable.checked])
            }
    }

    fun setEquipment(technicianId: Long, req: SetEquipmentRequest) = transaction {
        if (req.name.isBlank()) throw BadRequestException("Nama alat wajib")
        val row = EquipmentItemsTable.selectAll().where {
            (EquipmentItemsTable.technicianId eq technicianId) and (EquipmentItemsTable.name eq req.name.trim())
        }.singleOrNull() ?: throw NotFoundException("Alat tidak ditemukan")
        EquipmentItemsTable.update({ EquipmentItemsTable.id eq row[EquipmentItemsTable.id] }) {
            it[checked] = req.checked
            it[updatedAt] = OffsetDateTime.now()
        }
    }

    internal fun canDispatch(technicianId: Long, date: LocalDate): Boolean = transaction {
        val onLeave = LeaveRequestsTable.selectAll().where {
            (LeaveRequestsTable.technicianId eq technicianId) and
                (LeaveRequestsTable.status eq "DISETUJUI") and
                (LeaveRequestsTable.startDate lessEq date) and
                (LeaveRequestsTable.endDate greaterEq date)
        }.any()
        if (onLeave) return@transaction false
        val ktp = TechDocumentsTable.selectAll().where {
            (TechDocumentsTable.technicianId eq technicianId) and (TechDocumentsTable.docType eq "KTP")
        }.singleOrNull()
        if (ktp != null && ktp[TechDocumentsTable.status] == "DITOLAK") return@transaction false
        val expired = TechDocumentsTable.selectAll().where {
            (TechDocumentsTable.technicianId eq technicianId) and
                (TechDocumentsTable.status eq "TERVERIFIKASI") and
                (TechDocumentsTable.expiresAt lessEq date)
        }.any()
        !expired
    }

    fun opsStats(technicianId: Long): OpsStatsDto = transaction {
        val offers = JobOffersTable.selectAll().where { JobOffersTable.technicianId eq technicianId }.toList()
        val rejected = offers.count { it[JobOffersTable.status] == OfferStatus.DITOLAK.name }
        val responseTimes = offers.filter { it[JobOffersTable.respondedAt] != null }.map { row ->
            java.time.Duration.between(row[JobOffersTable.createdAt], row[JobOffersTable.respondedAt]).toMinutes()
        }
        val responseMinutes = if (responseTimes.isEmpty()) 0 else (responseTimes.sum() / responseTimes.size).toInt()
        val durations = OrdersTable.selectAll().where {
            (OrdersTable.technicianId eq technicianId) and (OrdersTable.status eq OrderStatus.SELESAI.name)
        }.map { row ->
            java.time.Duration.between(row[OrdersTable.createdAt], row[OrdersTable.updatedAt]).toMinutes() / 60.0
        }
        val completionHours = if (durations.isEmpty()) 0.0 else
            Math.round((durations.sum() / durations.size) * 10) / 10.0
        val completed = TechnicianService.earnings(technicianId).totalEarned
        val jobs = TechnicianProfilesTable.selectAll()
            .where { TechnicianProfilesTable.userId eq technicianId }
            .singleOrNull()?.get(TechnicianProfilesTable.jobsCount) ?: 0
        OpsStatsDto(
            responseMinutes = responseMinutes,
            completionHours = completionHours,
            cancellationRate = if (offers.isEmpty()) 0 else rejected * 100 / offers.size,
            level = when {
                jobs >= 25 -> 3
                jobs >= 5 -> 2
                else -> 1
            },
            levelProgress = when {
                jobs >= 25 -> 100
                jobs >= 5 -> (jobs - 5) * 100 / 20
                else -> jobs * 100 / 5
            },
            totalEarnings = completed
        )
    }
}
