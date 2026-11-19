package id.tukangkilat.backend

import id.tukangkilat.shared.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.security.SecureRandom
import java.time.OffsetDateTime

/** Photo storage on local disk with magic-byte validation and per-order access checks. */
object PhotoService {

    private val secureRandom = SecureRandom()
    private const val MAX_BYTES = 5 * 1024 * 1024

    /** Validate and persist an uploaded photo. Returns the public id used in URLs. */
    fun save(uploadDir: String, uploaderId: Long, bytes: ByteArray, originalName: String): PhotoDto {
        if (bytes.size > MAX_BYTES) throw UnprocessableException("Ukuran foto maksimal 5 MB")
        val mime = sniffMime(bytes) ?: throw UnprocessableException("File harus berupa JPG atau PNG")
        if (mime !in setOf("image/jpeg", "image/png")) throw UnprocessableException("File harus berupa JPG atau PNG")

        val publicId = randomPublicId()
        val ext = if (mime == "image/jpeg") "jpg" else "png"
        ensureUploadDir(uploadDir)
        val file = File(uploadDir, "$publicId.$ext")
        file.writeBytes(bytes)

        val now = OffsetDateTime.now()
        transaction {
            PhotosTable.insert {
                it[PhotosTable.publicId] = publicId
                it[PhotosTable.uploaderId] = uploaderId
                it[PhotosTable.filename] = file.name
                it[PhotosTable.mime] = mime
                it[PhotosTable.sizeBytes] = bytes.size
                it[PhotosTable.createdAt] = now
            }
        }
        return PhotoDto(id = publicId, url = "/photos/$publicId", mime = mime, sizeBytes = bytes.size)
    }

    data class PhotoDto(val id: String, val url: String, val mime: String, val sizeBytes: Int)

    /** Resolve a photo for [user]; only the uploader or the parties of a linked order may read it. */
    fun resolveForRead(uploadDir: String, publicId: String, user: AuthUser): Pair<File, String> {
        val photo = transaction {
            PhotosTable.selectAll().where { PhotosTable.publicId eq publicId }.singleOrNull()
        } ?: throw NotFoundException("Foto tidak ditemukan")

        val allowed = transaction {
            if (user.role == Role.ADMIN) return@transaction true
            if (photo[PhotosTable.uploaderId] == user.id) return@transaction true
            val linkedOrderIds = (OrderPhotosTable innerJoin PhotosTable).selectAll()
                .where { PhotosTable.publicId eq publicId }
                .map { it[OrderPhotosTable.orderId] }
            linkedOrderIds.any { orderId ->
                val row = OrdersTable.selectAll().where { OrdersTable.id eq orderId }.single()
                when (user.role) {
                    Role.KONSUMEN -> row[OrdersTable.consumerId] == user.id
                    Role.TUKANG -> row[OrdersTable.technicianId] == user.id
                    Role.ADMIN -> true
                }
            }
        }
        if (!allowed) throw ForbiddenException("Anda tidak punya akses ke foto ini")

        val file = File(uploadDir, photo[PhotosTable.filename])
        if (!file.exists()) throw NotFoundException("Berkas foto tidak ditemukan di server")
        return file to photo[PhotosTable.mime]
    }

    private fun sniffMime(bytes: ByteArray): String? {
        if (bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) {
            return "image/jpeg"
        }
        if (bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() &&
            bytes[4] == 0x0D.toByte() && bytes[5] == 0x0A.toByte() &&
            bytes[6] == 0x1A.toByte() && bytes[7] == 0x0A.toByte()
        ) {
            return "image/png"
        }
        return null
    }

    private fun randomPublicId(): String {
        val bytes = ByteArray(24)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
