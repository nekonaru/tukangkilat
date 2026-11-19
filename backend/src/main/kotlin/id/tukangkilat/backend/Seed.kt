package id.tukangkilat.backend

import id.tukangkilat.shared.Role
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime

/** Insert demo seed data, but only when the users table is empty. */
object Seed {

    private val log = LoggerFactory.getLogger("Seed")

    fun seedIfEmpty(config: AppConfig) {
        if (!config.seedDemoData) return
        transaction {
            val hasUsers = UsersTable.selectAll().limit(1).any()
            if (hasUsers) {
                log.info("Seed dilewati: tabel users tidak kosong")
                return@transaction
            }
            val now = OffsetDateTime.now()
            fun insertUser(
                username: String,
                password: String?,
                role: Role,
                fullName: String,
                phone: String,
                status: String = "AKTIF"
            ): Long? {
                if (password.isNullOrBlank()) {
                    log.warn("Seed $username dilewati: password demo tidak disediakan di env")
                    return null
                }
                return UsersTable.insert {
                    it[UsersTable.username] = username
                    it[passwordHash] = Passwords.hash(password)
                    it[UsersTable.role] = role.name
                    it[UsersTable.fullName] = fullName
                    it[UsersTable.phone] = phone
                    it[UsersTable.status] = status
                    it[failedLoginAttempts] = 0
                    it[createdAt] = now
                } get UsersTable.id
            }

            insertUser("admin", config.demoAdminPassword, Role.ADMIN, "Admin Tukang Kilat", "081100000001")
            val rinaId = insertUser("rina", config.demoConsumerPassword, Role.KONSUMEN, "Rina Putri", "081234567890")
            val budiId = insertUser("budi", config.demoTechPassword, Role.TUKANG, "Budi Santoso", "081298765432")
            val sariId = insertUser("sari", config.demoTech2Password, Role.TUKANG, "Sari Wulandari", "081277788899")

            // rina demo address, Tebet, Jakarta Selatan
            if (rinaId != null) {
                AddressesTable.insert {
                    it[userId] = rinaId
                    it[label] = "Rumah"
                    it[fullAddress] = "Jl. Tebet Barat Dalam VIII No. 12, Jakarta Selatan"
                    it[lat] = -6.2297
                    it[lng] = 106.8294
                    it[isPrimary] = true
                }
            }

            for (techId in listOf(budiId, sariId)) {
                if (techId == null) continue
                TechnicianProfilesTable.insert {
                    it[userId] = techId
                    it[skills] = if (techId == budiId) "LISTRIK,PIPA,AC" else "PIPA,AC"
                    it[verificationStatus] = "TERVERIFIKASI"
                    it[isOnline] = false
                    it[ratingAvg] = if (techId == budiId) 4.9 else 4.8
                    it[jobsCount] = if (techId == budiId) 128 else 96
                    it[serviceRadiusKm] = 10
                }
            }

            ServicesTable.insert {
                it[name] = "Perbaikan Listrik"
                it[icon] = "bolt"
                it[skill] = "LISTRIK"
                it[basePrice] = 125_000
                it[visitFee] = 25_000
                it[isActive] = true
            }
            ServicesTable.insert {
                it[name] = "Pipa Bocor"
                it[icon] = "water_drop"
                it[skill] = "PIPA"
                it[basePrice] = 100_000
                it[visitFee] = 25_000
                it[isActive] = true
            }
            ServicesTable.insert {
                it[name] = "Servis AC"
                it[icon] = "ac_unit"
                it[skill] = "AC"
                it[basePrice] = 85_000
                it[visitFee] = 25_000
                it[isActive] = true
            }

            TimeSlotsTable.insert {
                it[label] = "09.00"
                it[capacity] = 10
            }
            TimeSlotsTable.insert {
                it[label] = "10.00-11.00"
                it[capacity] = 10
            }
            TimeSlotsTable.insert {
                it[label] = "13.00"
                it[capacity] = 10
            }

            PromoCodesTable.insert {
                it[code] = "HEMAT25"
                it[promoType] = "NOMINAL"
                it[promoValue] = 25_000
                it[validUntil] = now.plusYears(1)
                it[quota] = 100
            }
            log.info("Seed selesai: akun demo, 3 layanan, 3 slot, 1 promo")
        }
    }
}
