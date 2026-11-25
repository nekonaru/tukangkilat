package id.tukangkilat.backend

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.date


object UsersTable : Table("users") {
    val id = long("id").autoIncrement()
    val username = varchar("username", 64).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val role = varchar("role", 16)
    val fullName = varchar("full_name", 120)
    val phone = varchar("phone", 32)
    val status = varchar("status", 24)
    val failedLoginAttempts = integer("failed_login_attempts").default(0)
    val lockedUntil = safeTimestampWithTimeZone("locked_until").nullable()
    val passwordChangedAt = safeTimestampWithTimeZone("password_changed_at").nullable()
    val createdAt = safeTimestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object TechnicianProfilesTable : Table("technician_profiles") {
    val userId = long("user_id").references(UsersTable.id)
    val skills = text("skills") // comma-separated skill codes, e.g. LISTRIK,PIPA
    val ktpPhoto = varchar("ktp_photo", 512).nullable()
    val selfiePhoto = varchar("selfie_photo", 512).nullable()
    val photoUrl = varchar("photo_url", 512).nullable()
    val verificationStatus = varchar("verification_status", 32)
    val rejectionReason = varchar("rejection_reason", 255).nullable()
    val isOnline = bool("is_online").default(false)
    val lastLat = double("last_lat").nullable()
    val lastLng = double("last_lng").nullable()
    val lastLocationAt = safeTimestampWithTimeZone("last_location_at").nullable()
    val ratingAvg = double("rating_avg").default(0.0)
    val jobsCount = integer("jobs_count").default(0)
    val serviceRadiusKm = integer("service_radius_km").default(10)
    override val primaryKey = PrimaryKey(userId)
}

object AddressesTable : Table("addresses") {
    val id = long("id").autoIncrement()
    val userId = long("user_id").references(UsersTable.id)
    val label = varchar("label", 64)
    val fullAddress = text("full_address")
    val lat = double("lat")
    val lng = double("lng")
    val isPrimary = bool("is_primary").default(false)
    override val primaryKey = PrimaryKey(id)
}

object ServicesTable : Table("services") {
    val id = long("id").autoIncrement()
    val name = varchar("name", 120)
    val icon = varchar("icon", 64)
    val skill = varchar("skill", 32)
    val basePrice = long("base_price")
    val visitFee = long("visit_fee")
    val isActive = bool("is_active").default(true)
    override val primaryKey = PrimaryKey(id)
}

object TimeSlotsTable : Table("time_slots") {
    val id = long("id").autoIncrement()
    val label = varchar("label", 32).uniqueIndex()
    val capacity = integer("capacity")
    override val primaryKey = PrimaryKey(id)
}

object PromoCodesTable : Table("promo_codes") {
    val code = varchar("code", 32)
    val promoType = varchar("promo_type", 16)
    val promoValue = long("promo_value")
    val validUntil = safeTimestampWithTimeZone("valid_until")
    val quota = integer("quota")
    val isActive = bool("is_active").default(true)
    override val primaryKey = PrimaryKey(code)
}

object OrdersTable : Table("orders") {
    val id = long("id").autoIncrement()
    val code = varchar("code", 32).uniqueIndex()
    val consumerId = long("consumer_id").references(UsersTable.id)
    val technicianId = long("technician_id").references(UsersTable.id).nullable()
    val serviceId = long("service_id").references(ServicesTable.id)
    val description = text("description")
    val addressId = long("address_id").references(AddressesTable.id)
    val scheduledDate = date("scheduled_date")
    val slotId = long("slot_id").references(TimeSlotsTable.id)
    val status = varchar("status", 32)
    val serviceFee = long("service_fee")
    val visitFee = long("visit_fee")
    val discount = long("discount").default(0L)
    val total = long("total")
    val paymentMethod = varchar("payment_method", 24).nullable()
    val paymentStatus = varchar("payment_status", 24)
    val finishNote = text("finish_note").nullable()
    val createdAt = safeTimestampWithTimeZone("created_at")
    val updatedAt = safeTimestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object PhotosTable : Table("photos") {
    val id = long("id").autoIncrement()
    val publicId = varchar("public_id", 64).uniqueIndex()
    val uploaderId = long("uploader_id").references(UsersTable.id)
    val filename = varchar("filename", 255)
    val mime = varchar("mime", 64)
    val sizeBytes = integer("size_bytes")
    val createdAt = safeTimestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object OrderPhotosTable : Table("order_photos") {
    val id = long("id").autoIncrement()
    val orderId = long("order_id").references(OrdersTable.id)
    val photoId = long("photo_id").references(PhotosTable.id)
    val kind = varchar("kind", 16)
    override val primaryKey = PrimaryKey(id)
}

object OrderEventsTable : Table("order_events") {
    val id = long("id").autoIncrement()
    val orderId = long("order_id").references(OrdersTable.id)
    val status = varchar("status", 32)
    val actorId = long("actor_id").references(UsersTable.id).nullable()
    val createdAt = safeTimestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object JobOffersTable : Table("job_offers") {
    val id = long("id").autoIncrement()
    val orderId = long("order_id").references(OrdersTable.id)
    val technicianId = long("technician_id").references(UsersTable.id)
    val status = varchar("status", 16)
    val expiresAt = safeTimestampWithTimeZone("expires_at")
    val rejectReason = varchar("reject_reason", 255).nullable()
    val isRevisit = bool("is_revisit").default(false)
    val createdAt = safeTimestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object PaymentsTable : Table("payments") {
    val id = long("id").autoIncrement()
    val orderId = long("order_id").references(OrdersTable.id)
    val method = varchar("method", 24)
    val amount = long("amount")
    val referenceNo = varchar("reference_no", 64).nullable()
    val status = varchar("status", 24)
    val paidAt = safeTimestampWithTimeZone("paid_at").nullable()
    val expiresAt = safeTimestampWithTimeZone("expires_at").nullable()
    val createdAt = safeTimestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object RatingsTable : Table("ratings") {
    val orderId = long("order_id").references(OrdersTable.id)
    val stars = integer("stars")
    val tags = text("tags") // comma-separated
    val comment = text("comment")
    val createdAt = safeTimestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(orderId)
}

object IssueReportsTable : Table("issue_reports") {
    val id = long("id").autoIncrement()
    val orderId = long("order_id").references(OrdersTable.id)
    val reason = varchar("reason", 32)
    val description = text("description")
    val status = varchar("status", 24)
    val createdAt = safeTimestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object MessagesTable : Table("messages") {
    val id = long("id").autoIncrement()
    val orderId = long("order_id").references(OrdersTable.id)
    val senderId = long("sender_id").references(UsersTable.id)
    val body = text("body")
    val createdAt = safeTimestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object WithdrawalsTable : Table("withdrawals") {
    val id = long("id").autoIncrement()
    val technicianId = long("technician_id").references(UsersTable.id)
    val amount = long("amount")
    val status = varchar("status", 24)
    val createdAt = safeTimestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object RefreshTokensTable : Table("refresh_tokens") {
    val id = long("id").autoIncrement()
    val userId = long("user_id").references(UsersTable.id)
    val tokenHash = varchar("token_hash", 128)
    val expiresAt = safeTimestampWithTimeZone("expires_at")
    val revoked = bool("revoked").default(false)
    val createdAt = safeTimestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}
