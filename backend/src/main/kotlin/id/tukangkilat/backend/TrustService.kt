package id.tukangkilat.backend

import id.tukangkilat.shared.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.security.MessageDigest
import java.time.LocalDate
import java.time.OffsetDateTime

/** v5 trust: devices, sessions, consent, SOS, app lock and admin trust operations. */
object TrustService {

    private val json = Json { ignoreUnknownKeys = true }

    private fun hashPin(userId: Long, pin: String): String {
        val input = "$userId:$pin:tukangkilat".toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(input)
        return digest.joinToString("") { "%02x".format(it) }
    }

    // ---------- Devices and sessions ----------

    fun devices(userId: Long): List<TrustedDeviceDto> = transaction {
        TrustedDevicesTable.selectAll().where { TrustedDevicesTable.userId eq userId }
            .orderBy(TrustedDevicesTable.lastSeen to SortOrder.DESC).map {
                TrustedDeviceDto(
                    it[TrustedDevicesTable.id], it[TrustedDevicesTable.deviceName], it[TrustedDevicesTable.deviceId],
                    it[TrustedDevicesTable.trusted], it[TrustedDevicesTable.createdAt].toString(),
                    it[TrustedDevicesTable.lastSeen].toString()
                )
            }
    }

    fun registerDevice(userId: Long, req: RegisterDeviceRequest): TrustedDeviceDto = transaction {
        if (req.deviceName.isBlank() || req.deviceId.isBlank()) throw BadRequestException("Data perangkat wajib")
        if (req.deviceId.length > 120 || req.deviceName.length > 120) throw BadRequestException("Data perangkat terlalu panjang")
        val existing = TrustedDevicesTable.selectAll().where {
            (TrustedDevicesTable.userId eq userId) and (TrustedDevicesTable.deviceId eq req.deviceId)
        }.singleOrNull()
        val id = if (existing == null) {
            TrustedDevicesTable.insert {
                it[this.userId] = userId
                it[deviceName] = req.deviceName.trim().take(120)
                it[deviceId] = req.deviceId.trim().take(120)
                it[trusted] = req.trusted
                it[createdAt] = OffsetDateTime.now()
                it[lastSeen] = OffsetDateTime.now()
            } get TrustedDevicesTable.id
        } else {
            TrustedDevicesTable.update({ TrustedDevicesTable.id eq existing[TrustedDevicesTable.id] }) {
                it[deviceName] = req.deviceName.trim().take(120)
                it[trusted] = req.trusted
                it[lastSeen] = OffsetDateTime.now()
            }
            existing[TrustedDevicesTable.id]
        }
        devices(userId).first { it.id == id }
    }

    fun setDeviceTrust(userId: Long, id: Long, trusted: Boolean) = transaction {
        val updated = TrustedDevicesTable.update({
            (TrustedDevicesTable.id eq id) and (TrustedDevicesTable.userId eq userId)
        }) { it[this.trusted] = trusted }
        if (updated == 0) throw NotFoundException("Perangkat tidak ditemukan")
    }

    fun removeDevice(userId: Long, id: Long) = transaction {
        val deleted = TrustedDevicesTable.deleteWhere {
            with(SqlExpressionBuilder) {
                (TrustedDevicesTable.id eq id) and (TrustedDevicesTable.userId eq userId)
            }
        }
        if (deleted == 0) throw NotFoundException("Perangkat tidak ditemukan")
    }

    fun sessions(userId: Long): List<SessionDto> = transaction {
        UserSessionsTable.selectAll().where { UserSessionsTable.userId eq userId }
            .orderBy(UserSessionsTable.lastActive to SortOrder.DESC).map {
                SessionDto(
                    it[UserSessionsTable.id], it[UserSessionsTable.deviceName], it[UserSessionsTable.ipAddress],
                    it[UserSessionsTable.startedAt].toString(), it[UserSessionsTable.lastActive].toString(),
                    it[UserSessionsTable.revoked]
                )
            }
    }

    fun recordSession(userId: Long, deviceName: String, ipAddress: String): SessionDto = transaction {
        val id = UserSessionsTable.insert {
            it[this.userId] = userId
            it[this.deviceName] = deviceName.take(120)
            it[this.ipAddress] = ipAddress.take(64)
            it[startedAt] = OffsetDateTime.now()
            it[lastActive] = OffsetDateTime.now()
            it[revoked] = false
        } get UserSessionsTable.id
        LoginAlertsTable.insert {
            it[this.userId] = userId
            it[this.deviceName] = deviceName.take(120)
            it[this.ipAddress] = ipAddress.take(64)
            it[createdAt] = OffsetDateTime.now()
            it[isRead] = false
        }
        sessions(userId).first { it.id == id }
    }

    fun revokeSession(userId: Long, id: Long) = transaction {
        val updated = UserSessionsTable.update({
            (UserSessionsTable.id eq id) and (UserSessionsTable.userId eq userId)
        }) { it[revoked] = true }
        if (updated == 0) throw NotFoundException("Sesi tidak ditemukan")
    }

    // ---------- Consent, blocks, alerts ----------

    fun consents(userId: Long): List<ConsentDto> = transaction {
        ConsentsTable.selectAll().where { ConsentsTable.userId eq userId }
            .orderBy(ConsentsTable.kind to SortOrder.ASC).map {
                ConsentDto(it[ConsentsTable.kind], it[ConsentsTable.granted], it[ConsentsTable.updatedAt].toString())
            }
    }

    fun saveConsent(userId: Long, req: SaveConsentRequest) = transaction {
        if (req.kind.isBlank() || req.kind.length > 32) throw BadRequestException("Jenis persetujuan tidak valid")
        val exists = ConsentsTable.selectAll().where {
            (ConsentsTable.userId eq userId) and (ConsentsTable.kind eq req.kind)
        }.any()
        if (exists) {
            ConsentsTable.update({
                (ConsentsTable.userId eq userId) and (ConsentsTable.kind eq req.kind)
            }) {
                it[granted] = req.granted
                it[updatedAt] = OffsetDateTime.now()
            }
        } else {
            ConsentsTable.insert {
                it[this.userId] = userId
                it[kind] = req.kind.trim().take(32)
                it[granted] = req.granted
                it[updatedAt] = OffsetDateTime.now()
            }
        }
        consents(userId).first { it.kind == req.kind.trim() }
    }

    fun blocks(userId: Long): List<BlockDto> = transaction {
        UserBlocksTable.selectAll().where { UserBlocksTable.userId eq userId }
            .orderBy(UserBlocksTable.createdAt to SortOrder.DESC).map {
                BlockDto(it[UserBlocksTable.id], it[UserBlocksTable.blockedId], it[UserBlocksTable.createdAt].toString())
            }
    }

    fun block(userId: Long, blockedId: Long): BlockDto = transaction {
        if (blockedId == userId) throw BadRequestException("Tidak bisa memblokir diri sendiri")
        if (!UsersTable.selectAll().where { UsersTable.id eq blockedId }.any()) throw NotFoundException("Pengguna tidak ditemukan")
        val existing = UserBlocksTable.selectAll().where {
            (UserBlocksTable.userId eq userId) and (UserBlocksTable.blockedId eq blockedId)
        }.singleOrNull()
        val id = existing?.get(UserBlocksTable.id) ?: UserBlocksTable.insert {
            it[this.userId] = userId
            it[this.blockedId] = blockedId
            it[createdAt] = OffsetDateTime.now()
        } get UserBlocksTable.id
        blocks(userId).first { it.id == id }
    }

    fun unblock(userId: Long, id: Long) = transaction {
        val deleted = UserBlocksTable.deleteWhere {
            with(SqlExpressionBuilder) {
                (UserBlocksTable.id eq id) and (UserBlocksTable.userId eq userId)
            }
        }
        if (deleted == 0) throw NotFoundException("Blokir tidak ditemukan")
    }

    fun loginAlerts(userId: Long): List<LoginAlertDto> = transaction {
        LoginAlertsTable.selectAll().where { LoginAlertsTable.userId eq userId }
            .orderBy(LoginAlertsTable.createdAt to SortOrder.DESC).limit(50).map {
                LoginAlertDto(
                    it[LoginAlertsTable.id], it[LoginAlertsTable.deviceName], it[LoginAlertsTable.ipAddress],
                    it[LoginAlertsTable.createdAt].toString(), it[LoginAlertsTable.isRead]
                )
            }
    }

    fun markAlertRead(userId: Long, id: Long) = transaction {
        LoginAlertsTable.update({
            (LoginAlertsTable.id eq id) and (LoginAlertsTable.userId eq userId)
        }) { it[isRead] = true }
    }

    // ---------- SOS ----------

    fun createSos(userId: Long, req: CreateSosRequest): SosAlertDto = transaction {
        if (!req.lat.isFinite() || !req.lng.isFinite()) throw BadRequestException("Koordinat tidak valid")
        if (req.orderId != null) {
            val order = OrderQueries.loadOrderRow(req.orderId!!)
            if (order[OrdersTable.consumerId] != userId && order[OrdersTable.technicianId] != userId) {
                throw ForbiddenException("Bukan pesanan Anda")
            }
        }
        val id = SosAlertsTable.insert {
            it[this.userId] = userId
            it[orderId] = req.orderId
            it[lat] = req.lat
            it[lng] = req.lng
            it[note] = req.note.trim().take(255)
            it[status] = "OPEN"
            it[createdAt] = OffsetDateTime.now()
        } get SosAlertsTable.id
        sosAlerts(userId).first { it.id == id }
    }

    fun sosAlerts(userId: Long): List<SosAlertDto> = transaction {
        SosAlertsTable.selectAll().where { SosAlertsTable.userId eq userId }
            .orderBy(SosAlertsTable.createdAt to SortOrder.DESC).map(::sosDto)
    }

    private fun sosDto(row: ResultRow) = SosAlertDto(
        row[SosAlertsTable.id], row[SosAlertsTable.userId], row[SosAlertsTable.orderId],
        row[SosAlertsTable.lat], row[SosAlertsTable.lng], row[SosAlertsTable.note],
        row[SosAlertsTable.status], row[SosAlertsTable.createdAt].toString()
    )

    fun adminSos(status: String?): List<SosAlertDto> = transaction {
        val query = if (status.isNullOrBlank()) SosAlertsTable.selectAll()
        else SosAlertsTable.selectAll().where { SosAlertsTable.status eq status }
        query.orderBy(SosAlertsTable.createdAt to SortOrder.DESC).limit(100).map(::sosDto)
    }

    fun resolveSos(adminId: Long, id: Long) = transaction {
        val row = SosAlertsTable.selectAll().where { SosAlertsTable.id eq id }
            .singleOrNull() ?: throw NotFoundException("Sinyal SOS tidak ditemukan")
        if (row[SosAlertsTable.status] != "OPEN") throw ConflictException("Sinyal sudah diproses")
        SosAlertsTable.update({ SosAlertsTable.id eq id }) {
            it[status] = "DITANGANI"
            it[resolvedAt] = OffsetDateTime.now()
        }
        EcosystemService.log(adminId, "RESOLVE_SOS", id.toString())
    }

    fun safeVisit(userId: Long, orderId: Long): SafeVisitDto = transaction {
        val order = OrderQueries.loadOrderRow(orderId)
        if (order[OrdersTable.consumerId] != userId && order[OrdersTable.technicianId] != userId) {
            throw ForbiddenException("Bukan pesanan Anda")
        }
        val code = "AMAN" + order[OrdersTable.code].takeLast(4).filter { it.isDigit() }.take(3).padEnd(3, '0')
        val verified = SignaturesTable.selectAll().where {
            (SignaturesTable.orderId eq orderId) and (SignaturesTable.signerId eq userId)
        }.any()
        SafeVisitDto(orderId, code, verified, verified)
    }

    // ---------- App lock and two factor ----------

    fun appLock(userId: Long): AppLockDto = transaction {
        val row = AppLocksTable.selectAll().where { AppLocksTable.userId eq userId }.singleOrNull()
        if (row == null) AppLockDto(false, 0, null)
        else AppLockDto(row[AppLocksTable.enabled], row[AppLocksTable.failedCount], row[AppLocksTable.lockedUntil]?.toString())
    }

    fun setPin(userId: Long, req: SetPinRequest): AppLockDto = transaction {
        if (req.pin.length !in 4..8 || !req.pin.all { it.isDigit() }) throw BadRequestException("PIN harus 4 sampai 8 digit")
        val exists = AppLocksTable.selectAll().where { AppLocksTable.userId eq userId }.any()
        if (exists) {
            AppLocksTable.update({ AppLocksTable.userId eq userId }) {
                it[pinHash] = hashPin(userId, req.pin)
                it[enabled] = req.enabled
                it[failedCount] = 0
                it[lockedUntil] = null
                it[updatedAt] = OffsetDateTime.now()
            }
        } else {
            AppLocksTable.insert {
                it[this.userId] = userId
                it[pinHash] = hashPin(userId, req.pin)
                it[enabled] = req.enabled
                it[failedCount] = 0
                it[updatedAt] = OffsetDateTime.now()
            }
        }
        appLock(userId)
    }

    fun verifyPin(userId: Long, pin: String): Boolean = transaction {
        val row = AppLocksTable.selectAll().where { AppLocksTable.userId eq userId }.singleOrNull()
            ?: throw BadRequestException("PIN belum diatur")
        row[AppLocksTable.lockedUntil]?.let {
            if (it.isAfter(OffsetDateTime.now())) throw ConflictException("App lock sedang dikunci sementara")
        }
        if (row[AppLocksTable.pinHash] != hashPin(userId, pin)) {
            val failed = row[AppLocksTable.failedCount] + 1
            AppLocksTable.update({ AppLocksTable.userId eq userId }) {
                it[failedCount] = failed
                it[lockedUntil] = if (failed >= 5) OffsetDateTime.now().plusMinutes(5) else null
                it[updatedAt] = OffsetDateTime.now()
            }
            false
        } else {
            AppLocksTable.update({ AppLocksTable.userId eq userId }) {
                it[failedCount] = 0
                it[lockedUntil] = null
                it[updatedAt] = OffsetDateTime.now()
            }
            true
        }
    }

    fun twoFactor(userId: Long): TwoFactorDto = transaction {
        val row = TwoFactorsTable.selectAll().where { TwoFactorsTable.userId eq userId }.singleOrNull()
        if (row == null) TwoFactorDto(false, "", null)
        else TwoFactorDto(row[TwoFactorsTable.enabled], row[TwoFactorsTable.otpCode], row[TwoFactorsTable.otpExpires]?.toString())
    }

    /** Simulated OTP: the code is returned so the demo can complete without SMS. */
    fun requestOtp(userId: Long): TwoFactorDto = transaction {
        val code = (100000..999999).random().toString()
        val exists = TwoFactorsTable.selectAll().where { TwoFactorsTable.userId eq userId }.any()
        if (exists) {
            TwoFactorsTable.update({ TwoFactorsTable.userId eq userId }) {
                it[otpCode] = code
                it[otpExpires] = OffsetDateTime.now().plusMinutes(5)
                it[updatedAt] = OffsetDateTime.now()
            }
        } else {
            TwoFactorsTable.insert {
                it[this.userId] = userId
                it[enabled] = false
                it[otpCode] = code
                it[otpExpires] = OffsetDateTime.now().plusMinutes(5)
                it[updatedAt] = OffsetDateTime.now()
            }
        }
        twoFactor(userId)
    }

    fun verifyOtp(userId: Long, code: String): TwoFactorDto = transaction {
        val row = TwoFactorsTable.selectAll().where { TwoFactorsTable.userId eq userId }
            .singleOrNull() ?: throw BadRequestException("OTP belum diminta")
        val expires = row[TwoFactorsTable.otpExpires]
            ?: throw BadRequestException("OTP belum tersedia")
        if (expires.isBefore(OffsetDateTime.now())) throw BadRequestException("OTP kedaluwarsa")
        if (row[TwoFactorsTable.otpCode] != code.trim()) throw BadRequestException("OTP tidak cocok")
        TwoFactorsTable.update({ TwoFactorsTable.userId eq userId }) {
            it[enabled] = true
            it[otpCode] = ""
            it[otpExpires] = null
            it[updatedAt] = OffsetDateTime.now()
        }
        twoFactor(userId)
    }

    fun disableTwoFactor(userId: Long) = transaction {
        TwoFactorsTable.update({ TwoFactorsTable.userId eq userId }) {
            it[enabled] = false
            it[otpCode] = ""
            it[otpExpires] = null
            it[updatedAt] = OffsetDateTime.now()
        }
    }

    // ---------- Privacy ----------

    fun privacySnapshot(userId: Long): PrivacySnapshotDto = transaction {
        val myOrderIds: List<Long> = OrdersTable.select(OrdersTable.id)
            .where { OrdersTable.consumerId eq userId }.map { it[OrdersTable.id] }
        PrivacySnapshotDto(
            orders = myOrderIds.size,
            addresses = AddressesTable.selectAll().where { AddressesTable.userId eq userId }.count().toInt(),
            reviews = RatingsTable.selectAll().where { RatingsTable.orderId inList myOrderIds }.count().toInt(),
            messages = DirectMessagesTable.selectAll().where { DirectMessagesTable.consumerId eq userId }.count().toInt(),
            walletTransactions = WalletTransactionsTable.selectAll()
                .where { WalletTransactionsTable.userId eq userId }.count().toInt(),
            devices = TrustedDevicesTable.selectAll().where { TrustedDevicesTable.userId eq userId }.count().toInt(),
            exportedAt = OffsetDateTime.now().toString()
        )
    }

    fun exportData(userId: Long): String = transaction {
        val snapshot = mapOf(
            "user" to UsersTable.selectAll().where { UsersTable.id eq userId }.map { row ->
                mapOf("id" to row[UsersTable.id].toString(), "username" to row[UsersTable.username],
                    "fullName" to row[UsersTable.fullName], "phone" to row[UsersTable.phone])
            },
            "addresses" to AddressesTable.selectAll().where { AddressesTable.userId eq userId }
                .map { mapOf("label" to it[AddressesTable.label], "address" to it[AddressesTable.fullAddress]) },
            "consents" to consents(userId).map { mapOf(it.kind to it.granted.toString()) }
        )
        json.encodeToString(snapshot)
    }

    fun deleteLocalPrivacyData(userId: Long) = transaction {
        ConsentsTable.deleteWhere { with(SqlExpressionBuilder) { ConsentsTable.userId eq userId } }
        UserBlocksTable.deleteWhere { with(SqlExpressionBuilder) { UserBlocksTable.userId eq userId } }
        TrustedDevicesTable.deleteWhere { with(SqlExpressionBuilder) { TrustedDevicesTable.userId eq userId } }
        LoginAlertsTable.deleteWhere { with(SqlExpressionBuilder) { LoginAlertsTable.userId eq userId } }
        AppLocksTable.deleteWhere { with(SqlExpressionBuilder) { AppLocksTable.userId eq userId } }
        TwoFactorsTable.deleteWhere { with(SqlExpressionBuilder) { TwoFactorsTable.userId eq userId } }
    }

    // ---------- Admin trust ----------

    fun subadmins(): List<SubadminDto> = transaction {
        SubadminRolesTable.selectAll().orderBy(SubadminRolesTable.createdAt to SortOrder.DESC).map {
            SubadminDto(it[SubadminRolesTable.id], it[SubadminRolesTable.adminId],
                it[SubadminRolesTable.roleName], it[SubadminRolesTable.permissions])
        }
    }

    fun saveSubadmin(req: SaveSubadminRequest): SubadminDto = transaction {
        if (req.roleName.isBlank() || req.permissions.isBlank()) throw BadRequestException("Data peran tidak valid")
        val existing = SubadminRolesTable.selectAll().where {
            (SubadminRolesTable.adminId eq req.adminId) and (SubadminRolesTable.roleName eq req.roleName)
        }.singleOrNull()
        val id = existing?.get(SubadminRolesTable.id) ?: SubadminRolesTable.insert {
            it[adminId] = req.adminId
            it[roleName] = req.roleName.trim().take(48)
            it[permissions] = req.permissions.trim().take(512)
            it[createdAt] = OffsetDateTime.now()
        } get SubadminRolesTable.id
        subadmins().first { it.id == id }
    }

    fun incidents(status: String?): List<IncidentDto> = transaction {
        val query = if (status.isNullOrBlank()) IncidentsTable.selectAll()
        else IncidentsTable.selectAll().where { IncidentsTable.status eq status }
        query.orderBy(IncidentsTable.createdAt to SortOrder.DESC).limit(100).map {
            IncidentDto(
                it[IncidentsTable.id], it[IncidentsTable.code], it[IncidentsTable.title], it[IncidentsTable.description],
                it[IncidentsTable.severity], it[IncidentsTable.status], it[IncidentsTable.runbook],
                it[IncidentsTable.createdAt].toString()
            )
        }
    }

    fun createIncident(adminId: Long, req: CreateIncidentRequest): IncidentDto = transaction {
        if (req.title.isBlank()) throw BadRequestException("Judul wajib diisi")
        if (req.severity !in listOf("LOW", "MEDIUM", "HIGH", "CRITICAL")) throw BadRequestException("Tingkat tidak valid")
        val code = "INC" + System.currentTimeMillis().toString().takeLast(8)
        val id = IncidentsTable.insert {
            it[this.code] = code
            it[title] = req.title.trim().take(120)
            it[description] = req.description.trim()
            it[severity] = req.severity
            it[status] = "OPEN"
            it[runbook] = req.runbook.trim()
            it[this.createdBy] = adminId
            it[createdAt] = OffsetDateTime.now()
        } get IncidentsTable.id
        incidents(null).first { it.id == id }
    }

    fun resolveIncident(adminId: Long, id: Long, note: String) = transaction {
        val row = IncidentsTable.selectAll().where { IncidentsTable.id eq id }
            .singleOrNull() ?: throw NotFoundException("Insiden tidak ditemukan")
        if (row[IncidentsTable.status] != "OPEN") throw ConflictException("Insiden sudah ditutup")
        IncidentsTable.update({ IncidentsTable.id eq id }) {
            it[status] = "DITUTUP"
            it[runbook] = (row[IncidentsTable.runbook] + "\n" + note.take(500)).take(4000)
            it[resolvedAt] = OffsetDateTime.now()
        }
        EcosystemService.log(adminId, "RESOLVE_INCIDENT", id.toString())
    }

    fun retentionPolicies(): List<RetentionDto> = transaction {
        RetentionPoliciesTable.selectAll().orderBy(RetentionPoliciesTable.code to SortOrder.ASC).map {
            RetentionDto(it[RetentionPoliciesTable.code], it[RetentionPoliciesTable.policyTable],
                it[RetentionPoliciesTable.days], it[RetentionPoliciesTable.enabled])
        }
    }

    fun saveRetention(code: String, req: SaveRetentionRequest): RetentionDto = transaction {
        if (req.days !in 1..3650) throw BadRequestException("Hari harus 1 sampai 3650")
        val exists = RetentionPoliciesTable.selectAll().where { RetentionPoliciesTable.code eq code }.any()
        if (exists) {
            RetentionPoliciesTable.update({ RetentionPoliciesTable.code eq code }) {
                it[policyTable] = req.tableName.trim().take(64)
                it[days] = req.days
                it[enabled] = req.enabled
                it[updatedAt] = OffsetDateTime.now()
            }
        } else {
            RetentionPoliciesTable.insert {
                it[this.code] = code.trim().take(48)
                it[policyTable] = req.tableName.trim().take(64)
                it[days] = req.days
                it[enabled] = req.enabled
                it[updatedAt] = OffsetDateTime.now()
            }
        }
        retentionPolicies().first { it.code == code.trim().take(48) }
    }

    fun backupJobs(): List<BackupJobDto> = transaction {
        BackupJobsTable.selectAll().orderBy(BackupJobsTable.createdAt to SortOrder.DESC).map {
            BackupJobDto(
                it[BackupJobsTable.id], it[BackupJobsTable.name], it[BackupJobsTable.frequency],
                it[BackupJobsTable.enabled], it[BackupJobsTable.lastRun]?.toString(), it[BackupJobsTable.nextRun]?.toString()
            )
        }
    }

    fun saveBackupJob(adminId: Long, req: SaveBackupJobRequest): BackupJobDto = transaction {
        if (req.name.isBlank() || req.frequency !in listOf("HARIAN", "MINGGUAN", "BULANAN")) {
            throw BadRequestException("Nama atau frekuensi tidak valid")
        }
        val id = BackupJobsTable.insert {
            it[name] = req.name.trim().take(120)
            it[frequency] = req.frequency
            it[enabled] = req.enabled
            it[createdBy] = adminId
            it[createdAt] = OffsetDateTime.now()
            it[nextRun] = OffsetDateTime.now().plusDays(if (req.frequency == "HARIAN") 1 else if (req.frequency == "MINGGUAN") 7 else 30)
        } get BackupJobsTable.id
        backupJobs().first { it.id == id }
    }

    fun runBackupJob(adminId: Long, id: Long): BackupJobDto = transaction {
        val row = BackupJobsTable.selectAll().where { BackupJobsTable.id eq id }
            .singleOrNull() ?: throw NotFoundException("Jadwal cadangan tidak ditemukan")
        val days = if (row[BackupJobsTable.frequency] == "HARIAN") 1L else if (row[BackupJobsTable.frequency] == "MINGGUAN") 7L else 30L
        BackupJobsTable.update({ BackupJobsTable.id eq id }) {
            it[lastRun] = OffsetDateTime.now()
            it[nextRun] = OffsetDateTime.now().plusDays(days)
        }
        EcosystemService.log(adminId, "RUN_BACKUP", id.toString())
        LocalDate.now().toString().let { backupJobs().first { job -> job.id == id } }
    }
}
