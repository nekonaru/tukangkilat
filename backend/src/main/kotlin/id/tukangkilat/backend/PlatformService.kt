package id.tukangkilat.backend

import id.tukangkilat.shared.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.time.OffsetDateTime

/** v4 platform services: flags, content, broadcasts, moderation, SLA and reports. */
object PlatformService {

    // ---------- Feature flags ----------

    fun flags(): List<FeatureFlagDto> = transaction {
        FeatureFlagsTable.selectAll().orderBy(FeatureFlagsTable.key to SortOrder.ASC).map {
            FeatureFlagDto(it[FeatureFlagsTable.key], it[FeatureFlagsTable.enabled],
                it[FeatureFlagsTable.targetRole], it[FeatureFlagsTable.note])
        }
    }

    fun publicFlags(role: String): List<FeatureFlagDto> = transaction {
        FeatureFlagsTable.selectAll().where {
            (FeatureFlagsTable.enabled eq true) and
                ((FeatureFlagsTable.targetRole eq "") or (FeatureFlagsTable.targetRole eq role))
        }.map {
            FeatureFlagDto(it[FeatureFlagsTable.key], true, it[FeatureFlagsTable.targetRole], it[FeatureFlagsTable.note])
        }
    }

    fun setFlag(key: String, req: SaveFeatureFlagRequest): FeatureFlagDto = transaction {
        if (key.isBlank() || key.length > 64) throw BadRequestException("Kunci tidak valid")
        val exists = FeatureFlagsTable.selectAll().where { FeatureFlagsTable.key eq key }.any()
        if (exists) {
            FeatureFlagsTable.update({ FeatureFlagsTable.key eq key }) {
                it[enabled] = req.enabled
                it[targetRole] = req.targetRole
                it[note] = req.note.take(255)
                it[updatedAt] = OffsetDateTime.now()
            }
        } else {
            FeatureFlagsTable.insert {
                it[FeatureFlagsTable.key] = key
                it[enabled] = req.enabled
                it[targetRole] = req.targetRole
                it[note] = req.note.take(255)
                it[updatedAt] = OffsetDateTime.now()
            }
        }
        val row = FeatureFlagsTable.selectAll().where { FeatureFlagsTable.key eq key }.single()
        FeatureFlagDto(key, row[FeatureFlagsTable.enabled], row[FeatureFlagsTable.targetRole], row[FeatureFlagsTable.note])
    }

    // ---------- Content ----------

    fun content(): List<ContentPageDto> = transaction {
        ContentPagesTable.selectAll().orderBy(ContentPagesTable.code to SortOrder.ASC).map {
            ContentPageDto(it[ContentPagesTable.id], it[ContentPagesTable.code], it[ContentPagesTable.title],
                it[ContentPagesTable.body], it[ContentPagesTable.isActive])
        }
    }

    fun publicContent(code: String): ContentDto = transaction {
        val row = ContentPagesTable.selectAll().where {
            (ContentPagesTable.code eq code) and (ContentPagesTable.isActive eq true)
        }.singleOrNull() ?: throw NotFoundException("Konten tidak ditemukan")
        ContentDto(row[ContentPagesTable.code], row[ContentPagesTable.title], row[ContentPagesTable.body])
    }

    fun saveContent(req: SaveContentRequest): ContentPageDto = transaction {
        if (req.code.isBlank() || req.title.isBlank()) throw BadRequestException("Kode dan judul wajib")
        val existing = ContentPagesTable.selectAll().where { ContentPagesTable.code eq req.code }.singleOrNull()
        val id = if (existing == null) {
            ContentPagesTable.insert {
                it[code] = req.code.trim().take(64)
                it[title] = req.title.trim().take(120)
                it[body] = req.body
                it[isActive] = req.isActive
                it[updatedAt] = OffsetDateTime.now()
            } get ContentPagesTable.id
        } else {
            ContentPagesTable.update({ ContentPagesTable.id eq existing[ContentPagesTable.id] }) {
                it[title] = req.title.trim().take(120)
                it[body] = req.body
                it[isActive] = req.isActive
                it[updatedAt] = OffsetDateTime.now()
            }
            existing[ContentPagesTable.id]
        }
        content().first { it.id == id }
    }

    // ---------- Broadcasts ----------

    fun broadcasts(): List<BroadcastDto> = transaction {
        ScheduledBroadcastsTable.selectAll().orderBy(ScheduledBroadcastsTable.sendAt to SortOrder.DESC).limit(50).map {
            BroadcastDto(it[ScheduledBroadcastsTable.id], it[ScheduledBroadcastsTable.title],
                it[ScheduledBroadcastsTable.body], it[ScheduledBroadcastsTable.targetRole],
                it[ScheduledBroadcastsTable.sendAt].toString(), it[ScheduledBroadcastsTable.status])
        }
    }

    fun createBroadcast(adminId: Long, req: CreateBroadcastRequest): BroadcastDto = transaction {
        if (req.title.isBlank() || req.body.isBlank()) throw BadRequestException("Judul dan isi wajib")
        val sendAt = try { OffsetDateTime.parse(req.sendAt) } catch (e: Exception) { throw BadRequestException("Waktu kirim tidak valid") }
        if (sendAt.isBefore(OffsetDateTime.now())) throw BadRequestException("Waktu kirim harus di masa depan")
        val id = ScheduledBroadcastsTable.insert {
            it[title] = req.title.trim().take(120)
            it[body] = req.body.trim()
            it[targetRole] = req.targetRole
            it[ScheduledBroadcastsTable.sendAt] = sendAt
            it[status] = "MENUNGGU"
            it[createdBy] = adminId
            it[createdAt] = OffsetDateTime.now()
        } get ScheduledBroadcastsTable.id
        broadcasts().first { it.id == id }
    }

    fun processDueBroadcasts(): Int {
        val due = transaction {
            ScheduledBroadcastsTable.selectAll().where {
                (ScheduledBroadcastsTable.status eq "MENUNGGU") and
                    (ScheduledBroadcastsTable.sendAt lessEq OffsetDateTime.now())
            }.toList()
        }
        due.forEach { row ->
            transaction {
                ScheduledBroadcastsTable.update({ ScheduledBroadcastsTable.id eq row[ScheduledBroadcastsTable.id] }) {
                    it[status] = "TERKIRIM"
                }
                AnnouncementsTable.insert {
                    it[adminId] = row[ScheduledBroadcastsTable.createdBy]
                    it[title] = row[ScheduledBroadcastsTable.title]
                    it[body] = row[ScheduledBroadcastsTable.body]
                    it[createdAt] = OffsetDateTime.now()
                }
            }
        }
        return due.size
    }

    // ---------- Review moderation ----------

    fun reportReview(userId: Long, orderId: Long, req: ReportReviewRequest) = transaction {
        if (!RatingsTable.selectAll().where { RatingsTable.orderId eq orderId }.any()) {
            throw NotFoundException("Ulasan tidak ditemukan")
        }
        val exists = ReviewReportsTable.selectAll().where {
            (ReviewReportsTable.reviewOrderId eq orderId) and (ReviewReportsTable.reporterId eq userId)
        }.any()
        if (!exists) {
            ReviewReportsTable.insert {
                it[reviewOrderId] = orderId
                it[reporterId] = userId
                it[reason] = req.reason.trim().take(255)
                it[status] = "OPEN"
                it[createdAt] = OffsetDateTime.now()
            }
        }
    }

    fun reviewReports(): List<ReviewReportDto> = transaction {
        ReviewReportsTable.selectAll().orderBy(ReviewReportsTable.createdAt to SortOrder.DESC).limit(100).map {
            ReviewReportDto(it[ReviewReportsTable.id], it[ReviewReportsTable.reviewOrderId],
                it[ReviewReportsTable.reporterId], it[ReviewReportsTable.reason],
                it[ReviewReportsTable.status], it[ReviewReportsTable.createdAt].toString())
        }
    }

    fun resolveReviewReport(adminId: Long, id: Long, hideReview: Boolean) = transaction {
        val row = ReviewReportsTable.selectAll().where { ReviewReportsTable.id eq id }
            .singleOrNull() ?: throw NotFoundException("Laporan tidak ditemukan")
        if (row[ReviewReportsTable.status] != "OPEN") throw ConflictException("Laporan sudah diproses")
        ReviewReportsTable.update({ ReviewReportsTable.id eq id }) {
            it[status] = if (hideReview) "DISEMBUNYIKAN" else "DITOLAK"
            it[resolvedBy] = adminId
        }
        if (hideReview) {
            RatingsTable.update({ RatingsTable.orderId eq row[ReviewReportsTable.reviewOrderId] }) {
                it[comment] = "[ulasan disembunyikan oleh admin]"
            }
        }
        EcosystemService.log(adminId, "RESOLVE_REVIEW", id.toString())
    }

    // ---------- SLA ----------

    fun slaPolicies(): List<SlaPolicyDto> = transaction {
        SlaPoliciesTable.selectAll().orderBy(SlaPoliciesTable.code to SortOrder.ASC).map {
            SlaPolicyDto(it[SlaPoliciesTable.code], it[SlaPoliciesTable.minutes],
                it[SlaPoliciesTable.compensation], it[SlaPoliciesTable.isActive])
        }
    }

    fun saveSlaPolicy(code: String, req: SaveSlaPolicyRequest): SlaPolicyDto = transaction {
        if (req.minutes !in 5..1440 || req.compensation < 0) throw BadRequestException("Kebijakan SLA tidak valid")
        val exists = SlaPoliciesTable.selectAll().where { SlaPoliciesTable.code eq code }.any()
        if (exists) {
            SlaPoliciesTable.update({ SlaPoliciesTable.code eq code }) {
                it[minutes] = req.minutes
                it[compensation] = req.compensation
                it[isActive] = req.isActive
            }
        } else {
            SlaPoliciesTable.insert {
                it[SlaPoliciesTable.code] = code.take(32)
                it[minutes] = req.minutes
                it[compensation] = req.compensation
                it[isActive] = req.isActive
            }
        }
        val row = SlaPoliciesTable.selectAll().where { SlaPoliciesTable.code eq code }.single()
        SlaPolicyDto(code, row[SlaPoliciesTable.minutes], row[SlaPoliciesTable.compensation], row[SlaPoliciesTable.isActive])
    }

    fun breaches(): List<SlaBreachDto> = transaction {
        SlaBreachesTable.selectAll().orderBy(SlaBreachesTable.createdAt to SortOrder.DESC).limit(100).map {
            SlaBreachDto(it[SlaBreachesTable.id], it[SlaBreachesTable.orderId], it[SlaBreachesTable.policyCode],
                it[SlaBreachesTable.minutesLate], it[SlaBreachesTable.compensated], it[SlaBreachesTable.createdAt].toString())
        }
    }

    fun evaluateBreaches(): Int {
        val policies = transaction {
            SlaPoliciesTable.selectAll().where { SlaPoliciesTable.isActive eq true }.toList()
        }
        var created = 0
        policies.forEach { policy ->
            val code = policy[SlaPoliciesTable.code]
            val limitMinutes = policy[SlaPoliciesTable.minutes]
            val late = transaction {
                OrdersTable.selectAll().where {
                    (OrdersTable.status eq OrderStatus.MENUNGGU_KONFIRMASI.name) and
                        (OrdersTable.updatedAt lessEq OffsetDateTime.now().minusMinutes(limitMinutes.toLong()))
                }.toList()
            }
            late.forEach { order ->
                val orderId = order[OrdersTable.id]
                val exists = transaction {
                    SlaBreachesTable.selectAll().where {
                        (SlaBreachesTable.orderId eq orderId) and (SlaBreachesTable.policyCode eq code)
                    }.any()
                }
                if (!exists) {
                    transaction {
                        SlaBreachesTable.insert {
                            it[SlaBreachesTable.orderId] = orderId
                            it[policyCode] = code
                            it[minutesLate] = limitMinutes
                            it[compensated] = false
                            it[createdAt] = OffsetDateTime.now()
                        }
                    }
                    created++
                }
            }
        }
        return created
    }

    fun compensateBreach(id: Long): SlaBreachDto = transaction {
        val row = SlaBreachesTable.selectAll().where { SlaBreachesTable.id eq id }
            .singleOrNull() ?: throw NotFoundException("Pelanggaran tidak ditemukan")
        if (row[SlaBreachesTable.compensated]) throw ConflictException("Sudah dikompensasi")
        val policy = SlaPoliciesTable.selectAll()
            .where { SlaPoliciesTable.code eq row[SlaBreachesTable.policyCode] }.single()
        val order = OrderQueries.loadOrderRow(row[SlaBreachesTable.orderId])
        val userId = order[OrdersTable.consumerId]
        val wallet = WalletsTable.selectAll().where { WalletsTable.userId eq userId }.singleOrNull()
        if (wallet != null && policy[SlaPoliciesTable.compensation] > 0) {
            WalletsTable.update({ WalletsTable.userId eq userId }) {
                it[balance] = wallet[WalletsTable.balance] + policy[SlaPoliciesTable.compensation]
                it[updatedAt] = OffsetDateTime.now()
            }
            WalletTransactionsTable.insert {
                it[WalletTransactionsTable.userId] = userId
                it[WalletTransactionsTable.amount] = policy[SlaPoliciesTable.compensation]
                it[type] = "KOMPENSASI"
                it[reference] = row[SlaBreachesTable.orderId].toString()
                it[createdAt] = OffsetDateTime.now()
            }
        }
        SlaBreachesTable.update({ SlaBreachesTable.id eq id }) { it[compensated] = true }
        breaches().first { it.id == id }
    }

    // ---------- Notes and weekly reports ----------

    fun notes(targetType: String, targetId: Long): List<AdminNoteDto> = transaction {
        AdminNotesTable.selectAll().where {
            (AdminNotesTable.targetType eq targetType) and (AdminNotesTable.targetId eq targetId)
        }.orderBy(AdminNotesTable.createdAt to SortOrder.DESC).map {
            AdminNoteDto(it[AdminNotesTable.id], it[AdminNotesTable.adminId], it[AdminNotesTable.targetType],
                it[AdminNotesTable.targetId], it[AdminNotesTable.note], it[AdminNotesTable.createdAt].toString())
        }
    }

    fun addNote(adminId: Long, req: AddAdminNoteRequest) = transaction {
        if (req.note.isBlank()) throw BadRequestException("Catatan wajib diisi")
        val id = AdminNotesTable.insert {
            it[this.adminId] = adminId
            it[targetType] = req.targetType
            it[targetId] = req.targetId
            it[note] = req.note.trim().take(2000)
            it[createdAt] = OffsetDateTime.now()
        } get AdminNotesTable.id
        notes(req.targetType, req.targetId).first { it.id == id }
    }

    fun weeklyReports(): List<WeeklyReportDto> = transaction {
        WeeklyReportsTable.selectAll().orderBy(WeeklyReportsTable.weekStart to SortOrder.DESC).limit(20).map {
            WeeklyReportDto(it[WeeklyReportsTable.id], it[WeeklyReportsTable.weekStart].toString(),
                it[WeeklyReportsTable.orders], it[WeeklyReportsTable.revenue],
                it[WeeklyReportsTable.newUsers], it[WeeklyReportsTable.disputes])
        }
    }

    fun generateWeeklyReport(): WeeklyReportDto = transaction {
        val start = LocalDate.now().minusDays(7)
        val existing = WeeklyReportsTable.selectAll().where { WeeklyReportsTable.weekStart eq start }.singleOrNull()
        if (existing != null) return@transaction weeklyReports().first { it.id == existing[WeeklyReportsTable.id] }
        val orders = OrdersTable.selectAll().where { OrdersTable.createdAt greaterEq start.atStartOfDay().atOffset(java.time.ZoneOffset.UTC) }.count().toInt()
        val revenue = OrdersTable.selectAll().where {
            (OrdersTable.status eq OrderStatus.SELESAI.name) and
                (OrdersTable.createdAt greaterEq start.atStartOfDay().atOffset(java.time.ZoneOffset.UTC))
        }.sumOf { it[OrdersTable.total] }
        val newUsers = UsersTable.selectAll().where {
            UsersTable.createdAt greaterEq start.atStartOfDay().atOffset(java.time.ZoneOffset.UTC)
        }.count().toInt()
        val disputes = DisputesTable.selectAll().where {
            DisputesTable.createdAt greaterEq start.atStartOfDay().atOffset(java.time.ZoneOffset.UTC)
        }.count().toInt()
        val id = WeeklyReportsTable.insert {
            it[weekStart] = start
            it[this.orders] = orders
            it[this.revenue] = revenue
            it[this.newUsers] = newUsers
            it[this.disputes] = disputes
            it[createdAt] = OffsetDateTime.now()
        } get WeeklyReportsTable.id
        weeklyReports().first { it.id == id }
    }

    fun summary(): PlatformSummaryDto = transaction {
        PlatformSummaryDto(
            totalUsers = UsersTable.selectAll().count().toInt(),
            totalOrders = OrdersTable.selectAll().count().toInt(),
            revenue = OrdersTable.selectAll().where { OrdersTable.status eq OrderStatus.SELESAI.name }
                .sumOf { it[OrdersTable.total] },
            openDisputes = DisputesTable.selectAll().where { DisputesTable.decision eq "MENUNGGU" }.count().toInt(),
            slaBreaches = SlaBreachesTable.selectAll().where { SlaBreachesTable.compensated eq false }.count().toInt(),
            flaggedReviews = ReviewReportsTable.selectAll().where { ReviewReportsTable.status eq "OPEN" }.count().toInt(),
            backupVersion = "4"
        )
    }
}
