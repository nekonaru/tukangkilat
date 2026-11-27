package id.tukangkilat.backend

import id.tukangkilat.shared.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.time.OffsetDateTime

/** v5 technician economy: reputation, academy, teams, permits and finances. */
object TechEconomyService {

    fun reputation(technicianId: Long): ReputationDto = transaction {
        val profile = TechnicianProfilesTable.selectAll()
            .where { TechnicianProfilesTable.userId eq technicianId }.singleOrNull()
        val rating = profile?.get(TechnicianProfilesTable.ratingAvg) ?: 0.0
        val jobs = profile?.get(TechnicianProfilesTable.jobsCount) ?: 0
        val offers = JobOffersTable.selectAll().where { JobOffersTable.technicianId eq technicianId }.toList()
        val accepted = offers.count { it[JobOffersTable.status] == OfferStatus.DITERIMA.name }
        val quality = (rating * 20).toInt().coerceIn(0, 100)
        val response = if (offers.isEmpty()) 0 else accepted * 100 / offers.size
        val punctuality = if (jobs == 0) 0 else (jobs.coerceAtMost(20) * 5)
        val satisfaction = quality
        val overall = (quality + response + punctuality + satisfaction) / 4
        val existing = ReputationScoresTable.selectAll()
            .where { ReputationScoresTable.technicianId eq technicianId }.any()
        if (existing) {
            ReputationScoresTable.update({ ReputationScoresTable.technicianId eq technicianId }) {
                it[this.quality] = quality
                it[this.response] = response
                it[this.punctuality] = punctuality
                it[this.satisfaction] = satisfaction
                it[this.overall] = overall
                it[updatedAt] = OffsetDateTime.now()
            }
        } else {
            ReputationScoresTable.insert {
                it[this.technicianId] = technicianId
                it[this.quality] = quality
                it[this.response] = response
                it[this.punctuality] = punctuality
                it[this.satisfaction] = satisfaction
                it[this.overall] = overall
                it[updatedAt] = OffsetDateTime.now()
            }
        }
        ReputationDto(quality, response, punctuality, satisfaction, overall)
    }

    fun certifications(technicianId: Long): List<CertificationDto> = transaction {
        if (!CertificationsTable.selectAll().where { CertificationsTable.technicianId eq technicianId }.any()) {
            listOf("KTP" to "Identitas teknisi", "SERTIFIKAT" to "Sertifikat keahlian").forEach { (code, title) ->
                CertificationsTable.insert {
                    it[this.technicianId] = technicianId
                    it[this.code] = code
                    it[this.title] = title
                    it[expiresAt] = LocalDate.now().plusYears(1)
                    it[status] = "AKTIF"
                    it[score] = 0
                    it[createdAt] = OffsetDateTime.now()
                }
            }
        }
        CertificationStatus.expireIfNeeded(technicianId)
        CertificationsTable.selectAll().where { CertificationsTable.technicianId eq technicianId }
            .orderBy(CertificationsTable.expiresAt to SortOrder.ASC).map {
                CertificationDto(
                    it[CertificationsTable.id], it[CertificationsTable.code], it[CertificationsTable.title],
                    it[CertificationsTable.expiresAt].toString(), it[CertificationsTable.status], it[CertificationsTable.score]
                )
            }
    }

    private object CertificationStatus {
        fun expireIfNeeded(technicianId: Long) {
            CertificationsTable.update({
                (CertificationsTable.technicianId eq technicianId) and
                    (CertificationsTable.expiresAt less LocalDate.now()) and
                    (CertificationsTable.status eq "AKTIF")
            }) { it[status] = "KEDALUWARSA" }
        }
    }

    fun completeCertification(technicianId: Long, code: String, req: CompleteCertRequest): CertificationDto = transaction {
        if (req.score !in 0..100) throw BadRequestException("Nilai harus 0 sampai 100")
        val row = CertificationsTable.selectAll().where {
            (CertificationsTable.technicianId eq technicianId) and (CertificationsTable.code eq code)
        }.singleOrNull() ?: throw NotFoundException("Sertifikasi tidak ditemukan")
        val status = if (req.score >= 70) "AKTIF" else "GAGAL"
        CertificationsTable.update({ CertificationsTable.id eq row[CertificationsTable.id] }) {
            it[score] = req.score
            it[this.status] = status
            if (status == "AKTIF") it[expiresAt] = LocalDate.now().plusYears(1)
        }
        certifications(technicianId).first { it.id == row[CertificationsTable.id] }
    }

    fun courses(): List<CourseDto> = transaction {
        if (!CoursesTable.selectAll().any()) {
            listOf(
                Triple("LISTRIK_DASAR", "Dasar Instalasi Listrik", 30),
                Triple("AC_DASAR", "Dasar Servis AC", 40),
                Triple("PIPA_DASAR", "Dasar Perpipaan", 35),
                Triple("KESELAMATAN", "Keselamatan Kerja", 25)
            ).forEach { (code, title, minutes) ->
                CoursesTable.insert {
                    it[CoursesTable.code] = code
                    it[this.title] = title
                    it[this.minutes] = minutes
                    it[rewardPoints] = 50
                }
            }
        }
        CoursesTable.selectAll().orderBy(CoursesTable.code to SortOrder.ASC).map {
            CourseDto(it[CoursesTable.code], it[CoursesTable.title], it[CoursesTable.minutes], it[CoursesTable.rewardPoints])
        }
    }

    fun enrollments(technicianId: Long): List<EnrollmentDto> = transaction {
        EnrollmentsTable.selectAll().where { EnrollmentsTable.technicianId eq technicianId }
            .orderBy(EnrollmentsTable.updatedAt to SortOrder.DESC).map {
                EnrollmentDto(it[EnrollmentsTable.id], it[EnrollmentsTable.courseCode], it[EnrollmentsTable.progress], it[EnrollmentsTable.completed])
            }
    }

    fun enroll(technicianId: Long, code: String): EnrollmentDto = transaction {
        if (!CoursesTable.selectAll().where { CoursesTable.code eq code }.any()) throw NotFoundException("Kursus tidak ditemukan")
        val existing = EnrollmentsTable.selectAll().where {
            (EnrollmentsTable.technicianId eq technicianId) and (EnrollmentsTable.courseCode eq code)
        }.singleOrNull()
        val id = if (existing != null) {
            EnrollmentsTable.update({ EnrollmentsTable.id eq existing[EnrollmentsTable.id] }) {
                it[progress] = existing[EnrollmentsTable.progress]
                it[updatedAt] = OffsetDateTime.now()
            }
            existing[EnrollmentsTable.id]
        } else {
            EnrollmentsTable.insert {
                it[this.technicianId] = technicianId
                it[courseCode] = code
                it[progress] = 0
                it[completed] = false
                it[updatedAt] = OffsetDateTime.now()
            } get EnrollmentsTable.id
        }
        enrollments(technicianId).first { it.id == id }
    }

    fun completeCourse(technicianId: Long, code: String): EnrollmentDto = transaction {
        val row = EnrollmentsTable.selectAll().where {
            (EnrollmentsTable.technicianId eq technicianId) and (EnrollmentsTable.courseCode eq code)
        }.singleOrNull() ?: throw NotFoundException("Pendaftaran tidak ditemukan")
        if (!row[EnrollmentsTable.completed]) {
            val course = CoursesTable.selectAll().where { CoursesTable.code eq code }.single()
            EnrollmentsTable.update({ EnrollmentsTable.id eq row[EnrollmentsTable.id] }) {
                it[progress] = 100
                it[completed] = true
                it[updatedAt] = OffsetDateTime.now()
            }
            val loyalty = LoyaltyTable.selectAll().where { LoyaltyTable.userId eq technicianId }.singleOrNull()
            if (loyalty == null) {
                LoyaltyTable.insert {
                    it[LoyaltyTable.userId] = technicianId
                    it[points] = course[CoursesTable.rewardPoints]
                    it[lifetime] = course[CoursesTable.rewardPoints]
                }
            } else {
                LoyaltyTable.update({ LoyaltyTable.userId eq technicianId }) {
                    it[points] = loyalty[LoyaltyTable.points] + course[CoursesTable.rewardPoints]
                    it[lifetime] = loyalty[LoyaltyTable.lifetime] + course[CoursesTable.rewardPoints]
                }
            }
        }
        enrollments(technicianId).first { it.id == row[EnrollmentsTable.id] }
    }

    fun gear(technicianId: Long): List<GearDto> = transaction {
        if (!GearItemsTable.selectAll().where { GearItemsTable.technicianId eq technicianId }.any()) {
            listOf("Multimeter", "Tang listrik", "Alat vakum AC", "Kunci momo").forEach { name ->
                GearItemsTable.insert {
                    it[this.technicianId] = technicianId
                    it[this.name] = name
                    it[pricePoints] = 100
                    it[owned] = false
                    it[createdAt] = OffsetDateTime.now()
                }
            }
        }
        GearItemsTable.selectAll().where { GearItemsTable.technicianId eq technicianId }
            .orderBy(GearItemsTable.id to SortOrder.ASC).map {
                GearDto(it[GearItemsTable.id], it[GearItemsTable.name], it[GearItemsTable.pricePoints], it[GearItemsTable.owned])
            }
    }

    fun buyGear(technicianId: Long, id: Long): GearDto = transaction {
        val item = GearItemsTable.selectAll().where {
            (GearItemsTable.id eq id) and (GearItemsTable.technicianId eq technicianId)
        }.singleOrNull() ?: throw NotFoundException("Alat tidak ditemukan")
        if (item[GearItemsTable.owned]) throw ConflictException("Alat sudah dimiliki")
        val loyalty = LoyaltyTable.selectAll().where { LoyaltyTable.userId eq technicianId }
            .singleOrNull() ?: throw BadRequestException("Poin tidak cukup")
        if (loyalty[LoyaltyTable.points] < item[GearItemsTable.pricePoints]) throw BadRequestException("Poin tidak cukup")
        LoyaltyTable.update({ LoyaltyTable.userId eq technicianId }) {
            it[points] = loyalty[LoyaltyTable.points] - item[GearItemsTable.pricePoints]
        }
        GearItemsTable.update({ GearItemsTable.id eq id }) { it[owned] = true }
        gear(technicianId).first { it.id == id }
    }

    fun teams(technicianId: Long): List<TeamDto> = transaction {
        TeamsTable.selectAll().where { TeamsTable.leadId eq technicianId }
            .orderBy(TeamsTable.createdAt to SortOrder.DESC).map {
                TeamDto(it[TeamsTable.id], it[TeamsTable.leadId], it[TeamsTable.name])
            }
    }

    fun createTeam(technicianId: Long, req: CreateTeamRequest): TeamDto = transaction {
        if (req.name.isBlank()) throw BadRequestException("Nama tim wajib")
        val id = TeamsTable.insert {
            it[leadId] = technicianId
            it[name] = req.name.trim().take(120)
            it[createdAt] = OffsetDateTime.now()
        } get TeamsTable.id
        teams(technicianId).first { it.id == id }
    }

    fun helpers(technicianId: Long): List<HelperDto> = transaction {
        val teamIds = TeamsTable.select(TeamsTable.id).where { TeamsTable.leadId eq technicianId }
            .map { it[TeamsTable.id] }
        if (teamIds.isEmpty()) return@transaction emptyList()
        TeamHelpersTable.selectAll().where { TeamHelpersTable.teamId inList teamIds }
            .orderBy(TeamHelpersTable.createdAt to SortOrder.DESC).map {
                HelperDto(it[TeamHelpersTable.id], it[TeamHelpersTable.teamId], it[TeamHelpersTable.helperId], it[TeamHelpersTable.status])
            }
    }

    fun addHelper(technicianId: Long, req: AddHelperRequest): HelperDto = transaction {
        val team = TeamsTable.selectAll().where {
            (TeamsTable.id eq req.teamId) and (TeamsTable.leadId eq technicianId)
        }.singleOrNull() ?: throw ForbiddenException("Bukan tim Anda")
        if (req.helperId == technicianId) throw BadRequestException("Tidak bisa menambahkan diri sendiri")
        val existing = TeamHelpersTable.selectAll().where {
            (TeamHelpersTable.teamId eq req.teamId) and (TeamHelpersTable.helperId eq req.helperId)
        }.singleOrNull()
        if (existing != null) return@transaction HelperDto(
            existing[TeamHelpersTable.id], req.teamId, req.helperId, existing[TeamHelpersTable.status]
        )
        val id = TeamHelpersTable.insert {
            it[teamId] = req.teamId
            it[helperId] = req.helperId
            it[status] = "DISETUJUI"
            it[createdAt] = OffsetDateTime.now()
        } get TeamHelpersTable.id
        HelperDto(id, req.teamId, req.helperId, "DISETUJUI")
    }

    fun heatmap(technicianId: Long): List<HeatmapDto> = transaction {
        AvailabilityHeatmapTable.selectAll().where { AvailabilityHeatmapTable.technicianId eq technicianId }
            .orderBy(AvailabilityHeatmapTable.dayOfWeek to SortOrder.ASC).map {
                HeatmapDto(it[AvailabilityHeatmapTable.dayOfWeek], it[AvailabilityHeatmapTable.hour], it[AvailabilityHeatmapTable.weight])
            }
    }

    fun saveHeatmap(technicianId: Long, req: SaveHeatmapRequest): HeatmapDto = transaction {
        if (req.dayOfWeek !in 1..7 || req.hour !in 0..23 || req.weight !in 1..5) throw BadRequestException("Data heatmap tidak valid")
        val existing = AvailabilityHeatmapTable.selectAll().where {
            (AvailabilityHeatmapTable.technicianId eq technicianId) and
                (AvailabilityHeatmapTable.dayOfWeek eq req.dayOfWeek) and
                (AvailabilityHeatmapTable.hour eq req.hour)
        }.singleOrNull()
        if (existing != null) {
            AvailabilityHeatmapTable.update({ AvailabilityHeatmapTable.id eq existing[AvailabilityHeatmapTable.id] }) {
                it[weight] = req.weight
            }
        } else {
            AvailabilityHeatmapTable.insert {
                it[this.technicianId] = technicianId
                it[dayOfWeek] = req.dayOfWeek
                it[hour] = req.hour
                it[weight] = req.weight
            }
        }
        HeatmapDto(req.dayOfWeek, req.hour, req.weight)
    }

    fun issuePermit(technicianId: Long, orderId: Long): WorkPermitDto = transaction {
        val order = OrderQueries.loadOrderRow(orderId)
        if (order[OrdersTable.technicianId] != technicianId) throw ForbiddenException("Bukan pesanan Anda")
        val existing = WorkPermitsTable.selectAll().where { WorkPermitsTable.orderId eq orderId }.singleOrNull()
        if (existing != null) return@transaction permitDto(existing)
        val code = "WRK" + System.currentTimeMillis().toString().takeLast(8)
        val id = WorkPermitsTable.insert {
            it[this.orderId] = orderId
            it[this.technicianId] = technicianId
            it[permitCode] = code
            it[issuedAt] = OffsetDateTime.now()
        } get WorkPermitsTable.id
        permitDto(WorkPermitsTable.selectAll().where { WorkPermitsTable.id eq id }.single())
    }

    private fun permitDto(row: ResultRow) = WorkPermitDto(
        row[WorkPermitsTable.id], row[WorkPermitsTable.orderId], row[WorkPermitsTable.permitCode],
        row[WorkPermitsTable.issuedAt].toString(), row[WorkPermitsTable.closedAt]?.toString()
    )

    fun forecast(technicianId: Long, period: String): ForecastDto = transaction {
        if (period !in listOf("MINGGUAN", "BULANAN")) throw BadRequestException("Periode tidak valid")
        val earnings = TechnicianService.earnings(technicianId).totalEarned
        val multiplier = if (period == "MINGGUAN") 1.0 else 4.0
        val expected = (earnings * multiplier).toLong()
        val id = EarningsForecastsTable.insert {
            it[this.technicianId] = technicianId
            it[this.period] = period
            it[this.expected] = expected
            it[createdAt] = OffsetDateTime.now()
        } get EarningsForecastsTable.id
        ForecastDto(id, period, expected)
    }

    fun simulateTax(technicianId: Long, req: SimulateTaxRequest): TaxDto = transaction {
        if (req.gross < 0 || req.taxPercent !in 0..50) throw BadRequestException("Data pajak tidak valid")
        val tax = req.gross * req.taxPercent / 100
        val id = TaxSimulationsTable.insert {
            it[this.technicianId] = technicianId
            it[gross] = req.gross
            it[taxPercent] = req.taxPercent
            it[taxAmount] = tax
            it[net] = req.gross - tax
            it[createdAt] = OffsetDateTime.now()
        } get TaxSimulationsTable.id
        TaxDto(id, req.gross, req.taxPercent, tax, req.gross - tax)
    }

    fun payoutSchedule(technicianId: Long): PayoutScheduleDto = transaction {
        val row = PayoutSchedulesTable.selectAll().where { PayoutSchedulesTable.technicianId eq technicianId }
            .singleOrNull()
        if (row == null) PayoutScheduleDto("MINGGUAN", 100_000, false)
        else PayoutScheduleDto(row[PayoutSchedulesTable.frequency], row[PayoutSchedulesTable.minAmount], row[PayoutSchedulesTable.enabled])
    }

    fun savePayoutSchedule(technicianId: Long, req: SavePayoutScheduleRequest) = transaction {
        if (req.frequency !in listOf("HARIAN", "MINGGUAN", "BULANAN") || req.minAmount < 0) {
            throw BadRequestException("Jadwal penarikan tidak valid")
        }
        val exists = PayoutSchedulesTable.selectAll().where { PayoutSchedulesTable.technicianId eq technicianId }.any()
        if (exists) {
            PayoutSchedulesTable.update({ PayoutSchedulesTable.technicianId eq technicianId }) {
                it[frequency] = req.frequency
                it[minAmount] = req.minAmount
                it[enabled] = req.enabled
                it[updatedAt] = OffsetDateTime.now()
            }
        } else {
            PayoutSchedulesTable.insert {
                it[this.technicianId] = technicianId
                it[frequency] = req.frequency
                it[minAmount] = req.minAmount
                it[enabled] = req.enabled
                it[updatedAt] = OffsetDateTime.now()
            }
        }
    }

    fun qualityScores(technicianId: Long): List<QualityDto> = transaction {
        QualityScoresTable.selectAll().where { QualityScoresTable.technicianId eq technicianId }
            .orderBy(QualityScoresTable.createdAt to SortOrder.DESC).map {
                QualityDto(it[QualityScoresTable.id], it[QualityScoresTable.orderId], it[QualityScoresTable.score], it[QualityScoresTable.note])
            }
    }

    fun saveQuality(technicianId: Long, req: SaveQualityRequest): QualityDto = transaction {
        if (req.score !in 0..100) throw BadRequestException("Skor harus 0 sampai 100")
        val order = OrderQueries.loadOrderRow(req.orderId)
        if (order[OrdersTable.technicianId] != technicianId) throw ForbiddenException("Bukan pesanan Anda")
        val existing = QualityScoresTable.selectAll().where { QualityScoresTable.orderId eq req.orderId }.singleOrNull()
        val id = if (existing != null) {
            QualityScoresTable.update({ QualityScoresTable.id eq existing[QualityScoresTable.id] }) {
                it[score] = req.score
                it[note] = req.note.take(255)
            }
            existing[QualityScoresTable.id]
        } else {
            QualityScoresTable.insert {
                it[orderId] = req.orderId
                it[this.technicianId] = technicianId
                it[score] = req.score
                it[note] = req.note.take(255)
                it[createdAt] = OffsetDateTime.now()
            } get QualityScoresTable.id
        }
        qualityScores(technicianId).first { it.id == id }
    }

    fun storefront(technicianId: Long): StorefrontDto = transaction {
        val row = StorefrontsTable.selectAll().where { StorefrontsTable.technicianId eq technicianId }.singleOrNull()
        if (row == null) StorefrontDto("", "", 0)
        else StorefrontDto(row[StorefrontsTable.headline], row[StorefrontsTable.description], row[StorefrontsTable.customPrice])
    }

    fun saveStorefront(technicianId: Long, req: SaveStorefrontRequest) = transaction {
        if (req.customPrice < 0 || req.headline.length > 120) throw BadRequestException("Data toko tidak valid")
        val exists = StorefrontsTable.selectAll().where { StorefrontsTable.technicianId eq technicianId }.any()
        if (exists) {
            StorefrontsTable.update({ StorefrontsTable.technicianId eq technicianId }) {
                it[headline] = req.headline.trim().take(120)
                it[description] = req.description.trim()
                it[customPrice] = req.customPrice
                it[updatedAt] = OffsetDateTime.now()
            }
        } else {
            StorefrontsTable.insert {
                it[this.technicianId] = technicianId
                it[headline] = req.headline.trim().take(120)
                it[description] = req.description.trim()
                it[customPrice] = req.customPrice
                it[updatedAt] = OffsetDateTime.now()
            }
        }
    }
}
