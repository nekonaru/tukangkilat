package id.tukangkilat.backend

import id.tukangkilat.shared.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.vendors.ForUpdateOption
import java.time.OffsetDateTime

open class AuthService {
    /** Allow tests to substitute bcrypt cost. Not used in production path. */

    fun registerConsumer(req: RegisterRequest, jwt: JwtService): LoginResponse {
        validatePassword(req.password)
        if (req.username.isBlank() || req.fullName.isBlank() || req.phone.isBlank()) {
            throw BadRequestException("Nama, username, dan nomor HP wajib diisi")
        }
        val username = req.username.trim().lowercase()
        return transaction {
            if (UsersTable.selectAll().where { UsersTable.username eq username }.any()) {
                throw ConflictException("Username sudah dipakai")
            }
            val now = OffsetDateTime.now()
            val userId = UsersTable.insert {
                it[UsersTable.username] = username
                it[passwordHash] = Passwords.hash(req.password)
                it[role] = Role.KONSUMEN.name
                it[fullName] = req.fullName.trim()
                it[phone] = req.phone.trim()
                it[UsersTable.status] = "AKTIF"
                it[failedLoginAttempts] = 0
                it[createdAt] = now
            } get UsersTable.id
            val address = req.address
            val lat = req.lat
            val lng = req.lng
            if (!address.isNullOrBlank() && lat != null && lng != null) {
                AddressesTable.insert {
                    it[this.userId] = userId
                    it[label] = "Rumah"
                    it[fullAddress] = address
                    it[this.lat] = lat
                    it[this.lng] = lng
                    it[isPrimary] = true
                }
            }
            buildLoginResponse(userId, Role.KONSUMEN, username, req.fullName.trim(), jwt)
        }
    }

    fun registerTechnician(req: RegisterTechnicianRequest): UserBrief {
        validatePassword(req.password)
        if (req.fullName.isBlank() || req.username.isBlank() || req.phone.isBlank()) {
            throw BadRequestException("Nama, username, dan nomor HP wajib diisi")
        }
        if (req.skills.isEmpty()) throw BadRequestException("Pilih minimal satu keahlian")
        if (req.ktpPhotoUrl.isBlank() || req.selfiePhotoUrl.isBlank()) {
            throw BadRequestException("Foto KTP dan selfie wajib diunggah")
        }
        val username = req.username.trim().lowercase()
        return transaction {
            if (UsersTable.selectAll().where { UsersTable.username eq username }.any()) {
                throw ConflictException("Username sudah dipakai")
            }
            val now = OffsetDateTime.now()
            val userId = UsersTable.insert {
                it[UsersTable.username] = username
                it[passwordHash] = Passwords.hash(req.password)
                it[role] = Role.TUKANG.name
                it[fullName] = req.fullName.trim()
                it[phone] = req.phone.trim()
                it[UsersTable.status] = "MENUNGGU_VERIFIKASI"
                it[failedLoginAttempts] = 0
                it[createdAt] = now
            } get UsersTable.id
            TechnicianProfilesTable.insert {
                it[TechnicianProfilesTable.userId] = userId
                it[skills] = req.skills.joinToString(",") { s -> s.trim().uppercase() }
                it[ktpPhoto] = req.ktpPhotoUrl
                it[selfiePhoto] = req.selfiePhotoUrl
                it[verificationStatus] = TechnicianVerificationStatus.MENUNGGU_VERIFIKASI.name
                it[isOnline] = false
                it[serviceRadiusKm] = req.serviceRadiusKm.coerceIn(1, 50)
            }
            UserBrief(userId, username, req.fullName.trim(), req.phone.trim(), Role.TUKANG, "MENUNGGU_VERIFIKASI")
        }
    }

    fun login(req: LoginRequest, jwt: JwtService): LoginResponse {
        val username = req.username.trim().lowercase()
        val row = transaction {
            UsersTable.selectAll().where { UsersTable.username eq username }.limit(1).singleOrNull()
        } ?: throw UnauthorizedException("Username atau password salah")

        val now = OffsetDateTime.now()
        val lockedUntilValue = row[UsersTable.lockedUntil]
        if (lockedUntilValue != null && lockedUntilValue.isAfter(now)) {
            throw ForbiddenException("Akun dikunci sementara karena terlalu banyak percobaan. Coba lagi dalam beberapa menit")
        }

        if (!Passwords.verify(req.password, row[UsersTable.passwordHash])) {
            // commit the failed attempt in its own transaction so it persists across the exception
            transaction {
                val current = UsersTable.selectAll().where { UsersTable.id eq row[UsersTable.id] }.single()[UsersTable.failedLoginAttempts]
                val attempts = current + 1
                UsersTable.update({ UsersTable.id eq row[UsersTable.id] }) {
                    if (attempts >= 5) {
                        it[UsersTable.lockedUntil] = OffsetDateTime.now().plusMinutes(5)
                        it[failedLoginAttempts] = 0
                    } else {
                        it[failedLoginAttempts] = attempts
                    }
                }
            }
            throw UnauthorizedException("Username atau password salah")
        }

        val role = Role.valueOf(row[UsersTable.role])
        if (role == Role.TUKANG) {
            val profile = transaction {
                TechnicianProfilesTable.selectAll()
                    .where { TechnicianProfilesTable.userId eq row[UsersTable.id] }.limit(1).singleOrNull()
            }
            val v = profile?.get(TechnicianProfilesTable.verificationStatus)
                ?: TechnicianVerificationStatus.MENUNGGU_VERIFIKASI.name
            when (v) {
                TechnicianVerificationStatus.MENUNGGU_VERIFIKASI.name ->
                    throw ForbiddenException("Akun tukang sedang menunggu verifikasi admin")
                TechnicianVerificationStatus.DITOLAK.name ->
                    throw ForbiddenException("Pendaftaran tukang ditolak. Hubungi admin untuk informasi lebih lanjut")
                TechnicianVerificationStatus.DITANGGUHKAN.name ->
                    throw ForbiddenException("Akun tukang ditangguhkan. Hubungi admin")
                else -> Unit
            }
        }
        if (row[UsersTable.status] != "AKTIF") {
            throw ForbiddenException("Akun tidak aktif. Hubungi admin")
        }

        return transaction {
            UsersTable.update({ UsersTable.id eq row[UsersTable.id] }) {
                it[failedLoginAttempts] = 0
                it[UsersTable.lockedUntil] = null
            }
            buildLoginResponse(row[UsersTable.id], role, username, row[UsersTable.fullName], jwt)
        }
    }

    fun refresh(req: RefreshRequest, jwt: JwtService): LoginResponse {
        val hash = RefreshTokens.sha256(req.refreshToken)
        val now = OffsetDateTime.now()
        return transaction {
            val tokenRow = RefreshTokensTable.selectAll()
                .where { RefreshTokensTable.tokenHash eq hash }.limit(1)
                .forUpdate(ForUpdateOption.ForUpdate).singleOrNull()
                ?: throw UnauthorizedException("Refresh token tidak valid")
            if (tokenRow[RefreshTokensTable.revoked] || tokenRow[RefreshTokensTable.expiresAt].isBefore(now)) {
                throw UnauthorizedException("Sesi berakhir, silakan masuk lagi")
            }
            val userRow = UsersTable.selectAll().where { UsersTable.id eq tokenRow[RefreshTokensTable.userId] }.single()
            if (userRow[UsersTable.status] != "AKTIF") throw ForbiddenException("Akun tidak aktif. Hubungi admin")
            // rotate: revoke the old refresh token
            RefreshTokensTable.update({ RefreshTokensTable.id eq tokenRow[RefreshTokensTable.id] }) { it[revoked] = true }
            buildLoginResponse(userRow[UsersTable.id], Role.valueOf(userRow[UsersTable.role]), userRow[UsersTable.username], userRow[UsersTable.fullName], jwt)
        }
    }

    fun logout(refreshToken: String) {
        val hash = RefreshTokens.sha256(refreshToken)
        transaction {
            RefreshTokensTable.update({ RefreshTokensTable.tokenHash eq hash }) { it[revoked] = true }
        }
    }

    fun changePassword(userId: Long, oldPassword: String, newPassword: String) {
        validatePassword(newPassword)
        transaction {
            val row = UsersTable.selectAll().where { UsersTable.id eq userId }.single()
            if (!Passwords.verify(oldPassword, row[UsersTable.passwordHash])) {
                throw BadRequestException("Password lama salah")
            }
            UsersTable.update({ UsersTable.id eq userId }) {
                it[passwordHash] = Passwords.hash(newPassword)
                it[passwordChangedAt] = OffsetDateTime.now()
            }
            // revoke all other sessions
            RefreshTokensTable.update({ RefreshTokensTable.userId eq userId }) { it[revoked] = true }
        }
    }

    fun issueLoginResponseFor(userId: Long, jwt: JwtService): LoginResponse = transaction {
        val row = UsersTable.selectAll().where { UsersTable.id eq userId }.single()
        buildLoginResponse(userId, Role.valueOf(row[UsersTable.role]), row[UsersTable.username], row[UsersTable.fullName], jwt)
    }

    private fun buildLoginResponse(userId: Long, role: Role, username: String, fullName: String, jwt: JwtService): LoginResponse {
        val access = jwt.createAccessToken(userId, role, username, fullName)
        val refresh = RefreshTokens.generate()
        val now = OffsetDateTime.now()
        RefreshTokensTable.insert {
            it[RefreshTokensTable.userId] = userId
            it[tokenHash] = RefreshTokens.sha256(refresh)
            it[expiresAt] = now.plusSeconds(JwtService.REFRESH_TOKEN_TTL_MS / 1000)
            it[revoked] = false
            it[createdAt] = now
        }
        val phone = UsersTable.selectAll().where { UsersTable.id eq userId }.singleOrNull()?.get(UsersTable.phone)
        return LoginResponse(
            accessToken = access,
            refreshToken = refresh,
            role = role,
            user = UserBrief(userId, username, fullName, phone, role, "AKTIF")
        )
    }

    private fun validatePassword(password: String) {
        if (password.length < 8) throw BadRequestException("Password minimal 8 karakter")
    }
}
