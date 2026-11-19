package id.tukangkilat.backend

import id.tukangkilat.shared.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime

/** Minimal admin panel backing service: verification, reports, orders, services, accounts. */
object AdminService {

    fun summary(): AdminSummaryDto = transaction {
        val todayStart = java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toOffsetDateTime()
        val ordersToday = OrdersTable.selectAll().where { OrdersTable.createdAt greaterEq todayStart }.count().toInt()
        val techsOnline = TechnicianProfilesTable.selectAll().where {
            (TechnicianProfilesTable.isOnline eq true) and
                (TechnicianProfilesTable.verificationStatus eq TechnicianVerificationStatus.TERVERIFIKASI.name)
        }.count().toInt()
        val openReports = IssueReportsTable.selectAll().where { IssueReportsTable.status eq IssueStatus.TERBUKA.name }.count().toInt()
        AdminSummaryDto(ordersToday = ordersToday, techniciansOnline = techsOnline, openReports = openReports)
    }

    private fun statusFilter(status: String?, build: SqlExpressionBuilder.() -> Op<Boolean>): Op<Boolean> =
        if (status.isNullOrBlank()) Op.TRUE else with(SqlExpressionBuilder, build)

    fun listTechnicians(status: String?): List<AdminTechnicianDto> = transaction {
        (TechnicianProfilesTable innerJoin UsersTable).selectAll().where {
            statusFilter(status) { TechnicianProfilesTable.verificationStatus eq status!!.uppercase() }
        }
            .orderBy(UsersTable.createdAt, SortOrder.ASC)
            .map { row ->
                AdminTechnicianDto(
                    userId = row[UsersTable.id],
                    fullName = row[UsersTable.fullName],
                    username = row[UsersTable.username],
                    phone = row[UsersTable.phone],
                    skills = row[TechnicianProfilesTable.skills].split(",").filter { it.isNotBlank() },
                    verificationStatus = row[TechnicianProfilesTable.verificationStatus],
                    ktpPhotoUrl = row[TechnicianProfilesTable.ktpPhoto]?.let { "/photos/$it" },
                    selfiePhotoUrl = row[TechnicianProfilesTable.selfiePhoto]?.let { "/photos/$it" },
                    ratingAvg = row[TechnicianProfilesTable.ratingAvg],
                    jobsCount = row[TechnicianProfilesTable.jobsCount]
                )
            }
    }

    fun approveTechnician(userId: Long) = transaction {
        val profile = TechnicianProfilesTable.selectAll().where { TechnicianProfilesTable.userId eq userId }.singleOrNull()
            ?: throw NotFoundException("Tukang tidak ditemukan")
        TechnicianProfilesTable.update({ TechnicianProfilesTable.userId eq userId }) {
            it[TechnicianProfilesTable.verificationStatus] = TechnicianVerificationStatus.TERVERIFIKASI.name
            it[TechnicianProfilesTable.rejectionReason] = null
        }
        UsersTable.update({ UsersTable.id eq userId }) { it[UsersTable.status] = "AKTIF" }
        profile
    }

    fun rejectTechnician(userId: Long, reason: String) = transaction {
        val profile = TechnicianProfilesTable.selectAll().where { TechnicianProfilesTable.userId eq userId }.singleOrNull()
            ?: throw NotFoundException("Tukang tidak ditemukan")
        if (reason.isBlank()) throw BadRequestException("Alasan penolakan wajib diisi")
        TechnicianProfilesTable.update({ TechnicianProfilesTable.userId eq userId }) {
            it[TechnicianProfilesTable.verificationStatus] = TechnicianVerificationStatus.DITOLAK.name
            it[TechnicianProfilesTable.rejectionReason] = reason.trim().take(250)
        }
        UsersTable.update({ UsersTable.id eq userId }) { it[UsersTable.status] = "DITOLAK" }
        profile
    }

    fun listReports(status: String?): List<IssueReportDto> = transaction {
        (IssueReportsTable innerJoin OrdersTable).selectAll().where {
            statusFilter(status) { IssueReportsTable.status eq status!!.uppercase() }
        }
            .orderBy(IssueReportsTable.createdAt, SortOrder.DESC)
            .map { row ->
                val orderId = row[IssueReportsTable.orderId]
                IssueReportDto(
                    id = row[IssueReportsTable.id],
                    orderId = orderId,
                    orderCode = row[OrdersTable.code],
                    reason = IssueReason.valueOf(row[IssueReportsTable.reason]),
                    description = row[IssueReportsTable.description],
                    photoUrls = OrderQueries.loadPhotos(orderId).filter { it.kind == PhotoKind.LAPORAN }.map { it.url },
                    status = IssueStatus.valueOf(row[IssueReportsTable.status]),
                    createdAt = row[IssueReportsTable.createdAt].toString()
                )
            }
    }

    /** Schedule a revisit: issue is marked, order goes back to dispatch with the same technician prioritized. */
    suspend fun revisitReport(reportId: Long, adminId: Long) {
        val orderId = transaction {
            val report = IssueReportsTable.selectAll().where { IssueReportsTable.id eq reportId }.singleOrNull()
                ?: throw NotFoundException("Laporan tidak ditemukan")
            if (report[IssueReportsTable.status] != IssueStatus.TERBUKA.name) {
                throw ConflictException("Laporan tidak lagi terbuka")
            }
            val orderId = report[IssueReportsTable.orderId]
            IssueReportsTable.update({ IssueReportsTable.id eq reportId }) { it[IssueReportsTable.status] = IssueStatus.KUNJUNGAN_ULANG.name }
            OrderService.transitionInTx(orderId, OrderStatus.KUNJUNGAN_ULANG, adminId, OrderStateMachine.Actor.ADMIN)
            // fresh dispatch: clear earlier offers so the cycle restarts and the same technician can be prioritized
            JobOffersTable.deleteWhere {
                with(SqlExpressionBuilder) {
                    JobOffersTable.orderId eq orderId
                }
            }
            orderId
        }
        // kick dispatcher immediately instead of waiting for the next tick
        DispatchService.dispatchRound()
    }

    fun closeReport(reportId: Long): Unit = transaction {
        val report = IssueReportsTable.selectAll().where { IssueReportsTable.id eq reportId }.singleOrNull()
            ?: throw NotFoundException("Laporan tidak ditemukan")
        IssueReportsTable.update({ IssueReportsTable.id eq reportId }) { it[IssueReportsTable.status] = IssueStatus.DITUTUP.name }
        Unit
    }

    fun listOrders(status: String?, limit: Int): List<OrderSummaryDto> = transaction {
        OrdersTable.selectAll().where {
            statusFilter(status) { OrdersTable.status eq status!!.uppercase() }
        }
            .orderBy(OrdersTable.createdAt, SortOrder.DESC)
            .limit(limit.coerceIn(1, 200))
            .map { OrderQueries.toOrderSummary(it) }
    }

    fun listServices(): List<ServiceDto> = OrderService.listServices()

    fun updateService(serviceId: Long, req: ServiceUpdateRequest): ServiceDto = transaction {
        val row = ServicesTable.selectAll().where { ServicesTable.id eq serviceId }.singleOrNull()
            ?: throw NotFoundException("Layanan tidak ditemukan")
        ServicesTable.update({ ServicesTable.id eq serviceId }) {
            req.name?.trim()?.take(120)?.takeIf { it.isNotBlank() }?.let { name -> it[ServicesTable.name] = name }
            req.basePrice?.let { price -> it[ServicesTable.basePrice] = price.coerceAtLeast(0) }
            req.visitFee?.let { fee -> it[ServicesTable.visitFee] = fee.coerceAtLeast(0) }
            req.isActive?.let { active -> it[ServicesTable.isActive] = active }
        }
        val updated = ServicesTable.selectAll().where { ServicesTable.id eq serviceId }.single()
        ServiceDto(
            id = updated[ServicesTable.id],
            name = updated[ServicesTable.name],
            icon = updated[ServicesTable.icon],
            basePrice = updated[ServicesTable.basePrice],
            visitFee = updated[ServicesTable.visitFee],
            isActive = updated[ServicesTable.isActive]
        )
    }

    fun setUserStatus(userId: Long, status: String): Unit = transaction {
        val allowed = setOf("AKTIF", "DITANGGUHKAN")
        val s = status.uppercase()
        if (s !in allowed) throw BadRequestException("Status harus AKTIF atau DITANGGUHKAN")
        val updated = UsersTable.update({ UsersTable.id eq userId }) { it[this.status] = s }
        if (updated == 0) throw NotFoundException("Pengguna tidak ditemukan")
        if (s == "DITANGGUHKAN") {
            // also force technician offline
            TechnicianProfilesTable.update({ TechnicianProfilesTable.userId eq userId }) { it[TechnicianProfilesTable.isOnline] = false }
        }
        Unit
    }

    suspend fun simulatePaymentSuccess(orderId: Long, adminId: Long) {
        val ok = OrderService.markPaymentSuccess(orderId, adminId, OrderStateMachine.Actor.ADMIN)
        if (!ok) throw IllegalTransitionException("unknown", "MENCARI_TEKNISI")
        DispatchService.dispatchRound()
    }

    suspend fun simulatePaymentFail(orderId: Long, adminId: Long) {
        val ok = OrderService.markPaymentFailed(orderId, adminId, OrderStateMachine.Actor.ADMIN)
        if (!ok) throw IllegalTransitionException("unknown", "PEMBAYARAN_GAGAL")
    }
}
