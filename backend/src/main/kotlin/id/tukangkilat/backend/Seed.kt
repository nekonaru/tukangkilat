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

    // Demo password hashes are precomputed with cost 12 so a fresh install does not spend
    // seconds hashing fixed credentials inside the app startup gate. Custom env passwords
    // still use the normal hashing path.
    private val DEMO_HASHES = mapOf(
        "admin12345" to "\$2a\$12\$o1UcTY/9rr1Hw.uNu2YzQue3qpOPZvAS905bHiZzmjg.gwL28wvOS",
        "rina12345" to "\$2a\$12\$b5mxAgly08ZC5vd5U5swG.wGWOoUQBdKGd1gomdgAFu4JKSZfqshK",
        "budi12345" to "\$2a\$12\$yRUk2uMHNWIm8FBE.v5roeeyjlNApva.0vCEJctZcyBSSoxaVDN1O",
        "sari12345" to "\$2a\$12\$L.OXK06MbAQB/HDwMLgtiOTj1ktSIikfKk1i.iKro2qIHnDSFVeP6"
    )

    private val SYSTEM_HASH = "\$2a\$12\$o1UcTY/9rr1Hw.uNu2YzQue3qpOPZvAS905bHiZzmjg.gwL28wvOS"

    private fun hashFor(password: String): String = DEMO_HASHES[password] ?: Passwords.hash(password)

    /** Ensure the placeholder uploader used by public registration photos exists. */
    fun ensureSystemUser() {
        transaction {
            if (UsersTable.selectAll().where { UsersTable.id eq 0L }.any()) return@transaction
            UsersTable.insert {
                it[id] = 0L
                it[username] = "system_registration"
                it[passwordHash] = SYSTEM_HASH
                it[role] = Role.TUKANG.name
                it[fullName] = "Pendaftaran"
                it[phone] = "-"
                it[status] = "DITANGGUHKAN"
                it[failedLoginAttempts] = 0
                it[createdAt] = OffsetDateTime.now()
            }
        }
    }

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
                    it[passwordHash] = hashFor(password)
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
                    it[lastLat] = -6.2297
                    it[lastLng] = 106.8294
                    it[lastLocationAt] = now
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
