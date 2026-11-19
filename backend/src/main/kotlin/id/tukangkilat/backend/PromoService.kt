package id.tukangkilat.backend

import id.tukangkilat.shared.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime

/** Admin promo code management. */
object PromoService {

    fun list(): List<PromoCodeDto> = transaction {
        PromoCodesTable.selectAll()
            .orderBy(PromoCodesTable.code to SortOrder.ASC)
            .map { it.toPromoDto() }
    }

    fun upsert(req: PromoUpsertRequest): PromoCodeDto = transaction {
        val code = req.code.trim().uppercase()
        if (code.isBlank()) throw BadRequestException("Kode promo wajib diisi")
        if (req.type.uppercase() !in setOf("PERSEN", "NOMINAL")) {
            throw BadRequestException("Tipe promo harus PERSEN atau NOMINAL")
        }
        if (req.value <= 0) throw BadRequestException("Nilai promo harus lebih dari 0")
        if (req.type.uppercase() == "PERSEN" && req.value > 100) {
            throw BadRequestException("Diskon PERSEN maksimal 100")
        }
        val validUntil = try {
            OffsetDateTime.parse(req.validUntil)
        } catch (e: Exception) {
            throw BadRequestException("Format validUntil tidak valid (ISO-8601)")
        }
        if (validUntil.isBefore(OffsetDateTime.now())) {
            throw BadRequestException("Tanggal kedaluwarsa harus di masa depan")
        }
        val quota = req.quota
        if (quota < 0) throw BadRequestException("Kuota tidak boleh negatif")

        val existing = PromoCodesTable.selectAll().where { PromoCodesTable.code eq code }.singleOrNull()
        if (existing == null) {
            PromoCodesTable.insert {
                it[PromoCodesTable.code] = code
                it[PromoCodesTable.promoType] = req.type.uppercase()
                it[PromoCodesTable.promoValue] = req.value
                it[PromoCodesTable.validUntil] = validUntil
                it[PromoCodesTable.quota] = quota
                it[PromoCodesTable.isActive] = req.isActive
            }
        } else {
            PromoCodesTable.update({ PromoCodesTable.code eq code }) {
                it[PromoCodesTable.promoType] = req.type.uppercase()
                it[PromoCodesTable.promoValue] = req.value
                it[PromoCodesTable.validUntil] = validUntil
                it[PromoCodesTable.quota] = quota
                it[PromoCodesTable.isActive] = req.isActive
            }
        }
        PromoCodesTable.selectAll().where { PromoCodesTable.code eq code }.single().toPromoDto()
    }

    private fun ResultRow.toPromoDto() = PromoCodeDto(
        code = this[PromoCodesTable.code],
        type = this[PromoCodesTable.promoType],
        value = this[PromoCodesTable.promoValue],
        validUntil = this[PromoCodesTable.validUntil].toString(),
        quota = this[PromoCodesTable.quota],
        isActive = this[PromoCodesTable.isActive]
    )
}
