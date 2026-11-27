package id.tukangkilat.backend

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.date


object DirectMessagesTable : Table("direct_messages") {
    val id = long("id").autoIncrement()
    val consumerId = long("consumer_id")
    val technicianId = long("technician_id")
    val senderId = long("sender_id")
    val body = text("body")
    val readAt = safeTimestampWithTimeZone("read_at").nullable()
    val createdAt = safeTimestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object ReviewPhotosTable : Table("review_photos") {
    val id = long("id").autoIncrement()
    val orderId = long("order_id").references(OrdersTable.id)
    val photoUrl = varchar("photo_url", 512)
    val createdAt = safeTimestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object AutoOnlineSchedulesTable : Table("auto_online_schedules") {
    val id = long("id").autoIncrement()
    val technicianId = long("technician_id").references(UsersTable.id)
    val dayOfWeek = integer("day_of_week")
    val startTime = varchar("start_time", 5)
    val endTime = varchar("end_time", 5)
    val enabled = bool("enabled")
    val createdAt = safeTimestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object MessageReportsTable : Table("message_reports") {
    val id = long("id").autoIncrement()
    val messageId = long("message_id").references(DirectMessagesTable.id)
    val reporterId = long("reporter_id").references(UsersTable.id)
    val reason = varchar("reason", 255)
    val status = varchar("status", 16)
    val resolvedBy = long("resolved_by").references(UsersTable.id).nullable()
    val createdAt = safeTimestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object ServiceCategoriesTable : Table("service_categories") {
    val id = long("id").autoIncrement()
    val name = varchar("name", 80)
    val icon = varchar("icon", 64)
    val sortOrder = integer("sort_order")
    val isActive = bool("is_active")
    override val primaryKey = PrimaryKey(id)
}

object ServiceBundlesTable : Table("service_bundles") {
    val id = long("id").autoIncrement()
    val name = varchar("name", 120)
    val description = text("description")
    val serviceIds = varchar("service_ids", 255)
    val discountPercent = integer("discount_percent")
    val isActive = bool("is_active")
    val createdAt = safeTimestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object PromoCampaignsTable : Table("promo_campaigns") {
    val id = long("id").autoIncrement()
    val code = varchar("code", 64)
    val discountType = varchar("discount_type", 16)
    val discountValue = long("discount_value")
    val minTotal = long("min_total")
    val maxDiscount = long("max_discount")
    val quota = integer("quota")
    val used = integer("used")
    val startsAt = safeTimestampWithTimeZone("starts_at").nullable()
    val endsAt = safeTimestampWithTimeZone("ends_at").nullable()
    val isActive = bool("is_active")
    val createdAt = safeTimestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object VouchersTable : Table("vouchers") {
    val id = long("id").autoIncrement()
    val code = varchar("code", 64)
    val consumerId = long("consumer_id").references(UsersTable.id)
    val amount = long("amount")
    val status = varchar("status", 16)
    val expiresAt = safeTimestampWithTimeZone("expires_at").nullable()
    val usedAt = safeTimestampWithTimeZone("used_at").nullable()
    val createdAt = safeTimestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object ReferralsTable : Table("referrals") {
    val id = long("id").autoIncrement()
    val code = varchar("code", 32)
    val consumerId = long("consumer_id").references(UsersTable.id)
    val invitedUserId = long("invited_user_id").references(UsersTable.id).nullable()
    val points = integer("points")
    val status = varchar("status", 16)
    val createdAt = safeTimestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object InstallmentPlansTable : Table("installment_plans") {
    val id = long("id").autoIncrement()
    val orderId = long("order_id").references(OrdersTable.id)
    val consumerId = long("consumer_id").references(UsersTable.id)
    val months = integer("months")
    val total = long("total")
    val monthly = long("monthly")
    val paidMonths = integer("paid_months")
    val status = varchar("status", 16)
    val createdAt = safeTimestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object MembershipTiersTable : Table("membership_tiers") {
    val code = varchar("code", 16)
    val minLifetimePoints = integer("min_lifetime_points")
    val discountPercent = integer("discount_percent")
    val priorityWeight = integer("priority_weight")
    val freeVisit = bool("free_visit")
    val benefits = varchar("benefits", 255)
    override val primaryKey = PrimaryKey(code)
}

object WalletSettingsTable : Table("wallet_settings") {
    val userId = long("user_id").references(UsersTable.id)
    val autoTopupEnabled = bool("auto_topup_enabled")
    val autoTopupAmount = long("auto_topup_amount")
    val autoTopupThreshold = long("auto_topup_threshold")
    val dailyLimit = long("daily_limit")
    val cashbackPercent = integer("cashback_percent")
    override val primaryKey = PrimaryKey(userId)
}

object ReviewVotesTable : Table("review_votes") {
    val reviewOrderId = long("review_order_id").references(RatingsTable.orderId)
    val userId = long("user_id").references(UsersTable.id)
    val helpful = bool("helpful")
    val createdAt = safeTimestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(reviewOrderId, userId)
}

object TechnicianRepliesTable : Table("technician_replies") {
    val reviewOrderId = long("review_order_id").references(RatingsTable.orderId)
    val reply = text("reply")
    val createdAt = safeTimestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(reviewOrderId)
}

object SignaturesTable : Table("signatures") {
    val id = long("id").autoIncrement()
    val orderId = long("order_id").references(OrdersTable.id)
    val signerId = long("signer_id").references(UsersTable.id)
    val role = varchar("role", 16)
    val dataUrl = varchar("data_url", 512)
    val createdAt = safeTimestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object ChecklistTemplatesTable : Table("checklist_templates") {
    val id = long("id").autoIncrement()
    val serviceId = long("service_id").references(ServicesTable.id)
    val title = varchar("title", 120)
    val stepOrder = integer("step_order")
    override val primaryKey = PrimaryKey(id)
}

object ChecklistItemsTable : Table("checklist_items") {
    val id = long("id").autoIncrement()
    val orderId = long("order_id").references(OrdersTable.id)
    val templateId = long("template_id").references(ChecklistTemplatesTable.id)
    val done = bool("done")
    val photoUrl = varchar("photo_url", 512)
    val note = varchar("note", 255)
    val updatedAt = safeTimestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object RefundRequestsTable : Table("refund_requests") {
    val id = long("id").autoIncrement()
    val orderId = long("order_id").references(OrdersTable.id)
    val consumerId = long("consumer_id").references(UsersTable.id)
    val amount = long("amount")
    val reason = text("reason")
    val status = varchar("status", 16)
    val note = varchar("note", 255)
    val createdAt = safeTimestampWithTimeZone("created_at")
    val resolvedAt = safeTimestampWithTimeZone("resolved_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

object UserPreferencesTable : Table("user_preferences") {
    val userId = long("user_id").references(UsersTable.id)
    val theme = varchar("theme", 16)
    val language = varchar("language", 8)
    val fontScale = integer("font_scale")
    val reducedMotion = bool("reduced_motion")
    val quietStart = varchar("quiet_start", 5)
    val quietEnd = varchar("quiet_end", 5)
    val updatedAt = safeTimestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(userId)
}

object OfflineQueueTable : Table("offline_queue") {
    val id = long("id").autoIncrement()
    val userId = long("user_id").references(UsersTable.id)
    val path = varchar("path", 255)
    val payload = text("payload")
    val status = varchar("status", 16)
    val createdAt = safeTimestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object TechDocumentsTable : Table("tech_documents") {
    val id = long("id").autoIncrement()
    val technicianId = long("technician_id").references(UsersTable.id)
    val docType = varchar("doc_type", 32)
    val fileUrl = varchar("file_url", 512)
    val expiresAt = date("expires_at").nullable()
    val status = varchar("status", 16)
    val note = varchar("note", 255)
    val createdAt = safeTimestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object ServiceAreasTable : Table("service_areas") {
    val id = long("id").autoIncrement()
    val technicianId = long("technician_id").references(UsersTable.id)
    val areaName = varchar("area_name", 120)
    val lat = double("lat")
    val lng = double("lng")
    val radiusKm = integer("radius_km")
    val isExcluded = bool("is_excluded")
    val createdAt = safeTimestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object AutoAcceptRulesTable : Table("auto_accept_rules") {
    val technicianId = long("technician_id").references(UsersTable.id)
    val enabled = bool("enabled")
    val minPrice = long("min_price")
    val maxDistance = integer("max_distance")
    val serviceIds = varchar("service_ids", 255)
    val updatedAt = safeTimestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(technicianId)
}

object CheckinsTable : Table("checkins") {
    val id = long("id").autoIncrement()
    val orderId = long("order_id").references(OrdersTable.id)
    val technicianId = long("technician_id").references(UsersTable.id)
    val kind = varchar("kind", 16)
    val lat = double("lat")
    val lng = double("lng")
    val distanceM = integer("distance_m")
    val createdAt = safeTimestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object JobMaterialsTable : Table("job_materials") {
    val id = long("id").autoIncrement()
    val orderId = long("order_id").references(OrdersTable.id)
    val technicianId = long("technician_id").references(UsersTable.id)
    val name = varchar("name", 120)
    val quantity = integer("quantity")
    val cost = long("cost")
    val createdAt = safeTimestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object TechBadgesTable : Table("tech_badges") {
    val id = long("id").autoIncrement()
    val technicianId = long("technician_id").references(UsersTable.id)
    val code = varchar("code", 48)
    val title = varchar("title", 120)
    val earnedAt = safeTimestampWithTimeZone("earned_at")
    override val primaryKey = PrimaryKey(id)
}

object ChatTemplatesTable : Table("chat_templates") {
    val id = long("id").autoIncrement()
    val technicianId = long("technician_id").references(UsersTable.id)
    val title = varchar("title", 80)
    val body = varchar("body", 500)
    val createdAt = safeTimestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object LeaveRequestsTable : Table("leave_requests") {
    val id = long("id").autoIncrement()
    val technicianId = long("technician_id").references(UsersTable.id)
    val startDate = date("start_date")
    val endDate = date("end_date")
    val reason = varchar("reason", 255)
    val status = varchar("status", 16)
    val createdAt = safeTimestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object WorkJournalsTable : Table("work_journals") {
    val id = long("id").autoIncrement()
    val technicianId = long("technician_id").references(UsersTable.id)
    val entryDate = date("entry_date")
    val note = text("note")
    val createdAt = safeTimestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object EquipmentItemsTable : Table("equipment_items") {
    val id = long("id").autoIncrement()
    val technicianId = long("technician_id").references(UsersTable.id)
    val name = varchar("name", 120)
    val checked = bool("checked")
    val updatedAt = safeTimestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object FeatureFlagsTable : Table("feature_flags") {
    val key = varchar("flag_key", 64)
    val enabled = bool("enabled")
    val targetRole = varchar("target_role", 16)
    val note = varchar("note", 255)
    val updatedAt = safeTimestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(key)
}

object ContentPagesTable : Table("content_pages") {
    val id = long("id").autoIncrement()
    val code = varchar("code", 64)
    val title = varchar("title", 120)
    val body = text("body")
    val isActive = bool("is_active")
    val updatedAt = safeTimestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object ScheduledBroadcastsTable : Table("scheduled_broadcasts") {
    val id = long("id").autoIncrement()
    val title = varchar("title", 120)
    val body = text("body")
    val targetRole = varchar("target_role", 16)
    val sendAt = safeTimestampWithTimeZone("send_at")
    val status = varchar("status", 16)
    val createdBy = long("created_by").references(UsersTable.id)
    val createdAt = safeTimestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object ReviewReportsTable : Table("review_reports") {
    val id = long("id").autoIncrement()
    val reviewOrderId = long("review_order_id").references(RatingsTable.orderId)
    val reporterId = long("reporter_id").references(UsersTable.id)
    val reason = varchar("reason", 255)
    val status = varchar("status", 16)
    val resolvedBy = long("resolved_by").references(UsersTable.id).nullable()
    val createdAt = safeTimestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object SlaPoliciesTable : Table("sla_policies") {
    val code = varchar("code", 32)
    val minutes = integer("minutes")
    val compensation = long("compensation")
    val isActive = bool("is_active")
    override val primaryKey = PrimaryKey(code)
}

object SlaBreachesTable : Table("sla_breaches") {
    val id = long("id").autoIncrement()
    val orderId = long("order_id").references(OrdersTable.id)
    val policyCode = varchar("policy_code", 32).references(SlaPoliciesTable.code)
    val minutesLate = integer("minutes_late")
    val compensated = bool("compensated")
    val createdAt = safeTimestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object AdminNotesTable : Table("admin_notes") {
    val id = long("id").autoIncrement()
    val adminId = long("admin_id").references(UsersTable.id)
    val targetType = varchar("target_type", 32)
    val targetId = long("target_id")
    val note = text("note")
    val createdAt = safeTimestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object WeeklyReportsTable : Table("weekly_reports") {
    val id = long("id").autoIncrement()
    val weekStart = date("week_start")
    val orders = integer("orders")
    val revenue = long("revenue")
    val newUsers = integer("new_users")
    val disputes = integer("disputes")
    val createdAt = safeTimestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object TrustedDevicesTable : Table("trusted_devices") {
    val id = long("id").autoIncrement()
    val userId = long("user_id").references(UsersTable.id)
    val deviceName = varchar("device_name", 120)
    val deviceId = varchar("device_id", 120)
    val trusted = bool("trusted")
    val createdAt = safeTimestampWithTimeZone("created_at")
    val lastSeen = safeTimestampWithTimeZone("last_seen")
    override val primaryKey = PrimaryKey(id)
}

object UserSessionsTable : Table("user_sessions") {
    val id = long("id").autoIncrement()
    val userId = long("user_id").references(UsersTable.id)
    val deviceName = varchar("device_name", 120)
    val ipAddress = varchar("ip_address", 64)
    val startedAt = safeTimestampWithTimeZone("started_at")
    val lastActive = safeTimestampWithTimeZone("last_active")
    val revoked = bool("revoked")
    override val primaryKey = PrimaryKey(id)
}

object ConsentsTable : Table("consents") {
    val userId = long("user_id").references(UsersTable.id)
    val kind = varchar("kind", 32)
    val granted = bool("granted")
    val updatedAt = safeTimestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(userId, kind)
}

object UserBlocksTable : Table("user_blocks") {
    val id = long("id").autoIncrement()
    val userId = long("user_id").references(UsersTable.id)
    val blockedId = long("blocked_id").references(UsersTable.id)
    val createdAt = safeTimestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object SosAlertsTable : Table("sos_alerts") {
    val id = long("id").autoIncrement()
    val userId = long("user_id").references(UsersTable.id)
    val orderId = long("order_id").references(OrdersTable.id).nullable()
    val lat = double("lat")
    val lng = double("lng")
    val note = varchar("note", 255)
    val status = varchar("status", 16)
    val createdAt = safeTimestampWithTimeZone("created_at")
    val resolvedAt = safeTimestampWithTimeZone("resolved_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

object AppLocksTable : Table("app_locks") {
    val userId = long("user_id").references(UsersTable.id)
    val pinHash = varchar("pin_hash", 128)
    val enabled = bool("enabled")
    val failedCount = integer("failed_count")
    val lockedUntil = safeTimestampWithTimeZone("locked_until").nullable()
    val updatedAt = safeTimestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(userId)
}

object TwoFactorsTable : Table("two_factors") {
    val userId = long("user_id").references(UsersTable.id)
    val enabled = bool("enabled")
    val otpCode = varchar("otp_code", 16)
    val otpExpires = safeTimestampWithTimeZone("otp_expires").nullable()
    val updatedAt = safeTimestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(userId)
}

object LoginAlertsTable : Table("login_alerts") {
    val id = long("id").autoIncrement()
    val userId = long("user_id").references(UsersTable.id)
    val deviceName = varchar("device_name", 120)
    val ipAddress = varchar("ip_address", 64)
    val createdAt = safeTimestampWithTimeZone("created_at")
    val isRead = bool("is_read")
    override val primaryKey = PrimaryKey(id)
}

object SubadminRolesTable : Table("subadmin_roles") {
    val id = long("id").autoIncrement()
    val adminId = long("admin_id").references(UsersTable.id)
    val roleName = varchar("role_name", 48)
    val permissions = varchar("permissions", 512)
    val createdAt = safeTimestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object IncidentsTable : Table("incidents") {
    val id = long("id").autoIncrement()
    val code = varchar("code", 32)
    val title = varchar("title", 120)
    val description = text("description")
    val severity = varchar("severity", 16)
    val status = varchar("status", 16)
    val runbook = text("runbook")
    val createdBy = long("created_by").references(UsersTable.id)
    val createdAt = safeTimestampWithTimeZone("created_at")
    val resolvedAt = safeTimestampWithTimeZone("resolved_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

object RetentionPoliciesTable : Table("retention_policies") {
    val code = varchar("code", 48)
    val policyTable = varchar("policy_table", 64)
    val days = integer("days")
    val enabled = bool("enabled")
    val updatedAt = safeTimestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(code)
}

object BackupJobsTable : Table("backup_jobs") {
    val id = long("id").autoIncrement()
    val name = varchar("name", 120)
    val frequency = varchar("frequency", 16)
    val enabled = bool("enabled")
    val lastRun = safeTimestampWithTimeZone("last_run").nullable()
    val nextRun = safeTimestampWithTimeZone("next_run").nullable()
    val createdBy = long("created_by").references(UsersTable.id)
    val createdAt = safeTimestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object CustomBundlesTable : Table("custom_bundles") {
    val id = long("id").autoIncrement()
    val consumerId = long("consumer_id").references(UsersTable.id)
    val name = varchar("name", 120)
    val serviceIds = varchar("service_ids", 255)
    val totalPrice = long("total_price")
    val discount = long("discount")
    val createdAt = safeTimestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object FlashSalesTable : Table("flash_sales") {
    val id = long("id").autoIncrement()
    val serviceId = long("service_id").references(ServicesTable.id)
    val discountPercent = integer("discount_percent")
    val quota = integer("quota")
    val used = integer("used")
    val startsAt = safeTimestampWithTimeZone("starts_at")
    val endsAt = safeTimestampWithTimeZone("ends_at")
    val isActive = bool("is_active")
    val createdAt = safeTimestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object GroupBuysTable : Table("group_buys") {
    val id = long("id").autoIncrement()
    val consumerId = long("consumer_id").references(UsersTable.id)
    val serviceId = long("service_id").references(ServicesTable.id)
    val areaName = varchar("area_name", 120)
    val targetCount = integer("target_count")
    val joinedCount = integer("joined_count")
    val status = varchar("status", 16)
    val createdAt = safeTimestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object JobBidsTable : Table("job_bids") {
    val id = long("id").autoIncrement()
    val orderId = long("order_id").references(OrdersTable.id)
    val technicianId = long("technician_id").references(UsersTable.id)
    val price = long("price")
    val note = varchar("note", 255)
    val status = varchar("status", 16)
    val createdAt = safeTimestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object GiftCardsTable : Table("gift_cards") {
    val id = long("id").autoIncrement()
    val code = varchar("code", 32)
    val senderId = long("sender_id").references(UsersTable.id)
    val receiverId = long("receiver_id").references(UsersTable.id).nullable()
    val amount = long("amount")
    val status = varchar("status", 16)
    val createdAt = safeTimestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object PointShopItemsTable : Table("point_shop_items") {
    val code = varchar("code", 48)
    val name = varchar("name", 120)
    val points = integer("points")
    val description = varchar("description", 255)
    override val primaryKey = PrimaryKey(code)
}

object PointRedemptionsTable : Table("point_redemptions") {
    val id = long("id").autoIncrement()
    val userId = long("user_id").references(UsersTable.id)
    val itemCode = varchar("item_code", 48).references(PointShopItemsTable.code)
    val points = integer("points")
    val createdAt = safeTimestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object TipsTable : Table("tips") {
    val id = long("id").autoIncrement()
    val orderId = long("order_id").references(OrdersTable.id)
    val consumerId = long("consumer_id").references(UsersTable.id)
    val technicianId = long("technician_id").references(UsersTable.id)
    val amount = long("amount")
    val note = varchar("note", 255)
    val createdAt = safeTimestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object WishlistsTable : Table("wishlists") {
    val id = long("id").autoIncrement()
    val consumerId = long("consumer_id").references(UsersTable.id)
    val serviceId = long("service_id").references(ServicesTable.id)
    val targetPrice = long("target_price")
    val createdAt = safeTimestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object PriceAlertsTable : Table("price_alerts") {
    val id = long("id").autoIncrement()
    val consumerId = long("consumer_id").references(UsersTable.id)
    val serviceId = long("service_id").references(ServicesTable.id)
    val targetPrice = long("target_price")
    val triggered = bool("triggered")
    val createdAt = safeTimestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object PriceHistoryTable : Table("price_history") {
    val id = long("id").autoIncrement()
    val serviceId = long("service_id").references(ServicesTable.id)
    val price = long("price")
    val recordedAt = safeTimestampWithTimeZone("recorded_at")
    override val primaryKey = PrimaryKey(id)
}

object LedgerEntriesTable : Table("ledger_entries") {
    val id = long("id").autoIncrement()
    val code = varchar("code", 32)
    val account = varchar("account", 64)
    val debit = long("debit")
    val credit = long("credit")
    val reference = varchar("reference", 128)
    val createdAt = safeTimestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object CommissionRulesTable : Table("commission_rules") {
    val code = varchar("code", 48)
    val serviceId = long("service_id").references(ServicesTable.id).nullable()
    val percent = integer("percent")
    val priority = integer("priority")
    val isActive = bool("is_active")
    val updatedAt = safeTimestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(code)
}

object ReputationScoresTable : Table("reputation_scores") {
    val technicianId = long("technician_id").references(UsersTable.id)
    val quality = integer("quality")
    val response = integer("response")
    val punctuality = integer("punctuality")
    val satisfaction = integer("satisfaction")
    val overall = integer("overall")
    val updatedAt = safeTimestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(technicianId)
}

object CertificationsTable : Table("certifications") {
    val id = long("id").autoIncrement()
    val technicianId = long("technician_id").references(UsersTable.id)
    val code = varchar("code", 48)
    val title = varchar("title", 120)
    val expiresAt = date("expires_at")
    val status = varchar("status", 16)
    val score = integer("score")
    val createdAt = safeTimestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object CoursesTable : Table("courses") {
    val code = varchar("code", 48)
    val title = varchar("title", 120)
    val minutes = integer("minutes")
    val rewardPoints = integer("reward_points")
    override val primaryKey = PrimaryKey(code)
}

object EnrollmentsTable : Table("enrollments") {
    val id = long("id").autoIncrement()
    val technicianId = long("technician_id").references(UsersTable.id)
    val courseCode = varchar("course_code", 48).references(CoursesTable.code)
    val progress = integer("progress")
    val completed = bool("completed")
    val updatedAt = safeTimestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object GearItemsTable : Table("gear_items") {
    val id = long("id").autoIncrement()
    val technicianId = long("technician_id").references(UsersTable.id)
    val name = varchar("name", 120)
    val pricePoints = integer("price_points")
    val owned = bool("owned")
    val createdAt = safeTimestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object TeamsTable : Table("teams") {
    val id = long("id").autoIncrement()
    val leadId = long("lead_id").references(UsersTable.id)
    val name = varchar("name", 120)
    val createdAt = safeTimestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object TeamHelpersTable : Table("team_helpers") {
    val id = long("id").autoIncrement()
    val teamId = long("team_id").references(TeamsTable.id)
    val helperId = long("helper_id").references(UsersTable.id)
    val status = varchar("status", 16)
    val createdAt = safeTimestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object AvailabilityHeatmapTable : Table("availability_heatmap") {
    val id = long("id").autoIncrement()
    val technicianId = long("technician_id").references(UsersTable.id)
    val dayOfWeek = integer("day_of_week")
    val hour = integer("hour_slot")
    val weight = integer("weight")
    override val primaryKey = PrimaryKey(id)
}

object WorkPermitsTable : Table("work_permits") {
    val id = long("id").autoIncrement()
    val orderId = long("order_id").references(OrdersTable.id)
    val technicianId = long("technician_id").references(UsersTable.id)
    val permitCode = varchar("permit_code", 32)
    val issuedAt = safeTimestampWithTimeZone("issued_at")
    val closedAt = safeTimestampWithTimeZone("closed_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

object EarningsForecastsTable : Table("earnings_forecasts") {
    val id = long("id").autoIncrement()
    val technicianId = long("technician_id").references(UsersTable.id)
    val period = varchar("period", 16)
    val expected = long("expected")
    val createdAt = safeTimestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object TaxSimulationsTable : Table("tax_simulations") {
    val id = long("id").autoIncrement()
    val technicianId = long("technician_id").references(UsersTable.id)
    val gross = long("gross")
    val taxPercent = integer("tax_percent")
    val taxAmount = long("tax_amount")
    val net = long("net")
    val createdAt = safeTimestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object PayoutSchedulesTable : Table("payout_schedules") {
    val technicianId = long("technician_id").references(UsersTable.id)
    val frequency = varchar("frequency", 16)
    val minAmount = long("min_amount")
    val enabled = bool("enabled")
    val updatedAt = safeTimestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(technicianId)
}

object QualityScoresTable : Table("quality_scores") {
    val id = long("id").autoIncrement()
    val orderId = long("order_id").references(OrdersTable.id)
    val technicianId = long("technician_id").references(UsersTable.id)
    val score = integer("score")
    val note = varchar("note", 255)
    val createdAt = safeTimestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object StorefrontsTable : Table("storefronts") {
    val technicianId = long("technician_id").references(UsersTable.id)
    val headline = varchar("headline", 120)
    val description = text("description")
    val customPrice = long("custom_price")
    val updatedAt = safeTimestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(technicianId)
}

object SyncOperationsTable : Table("sync_operations") {
    val id = long("id").autoIncrement()
    val userId = long("user_id").references(UsersTable.id)
    val entity = varchar("entity", 64)
    val entityId = varchar("entity_id", 64)
    val operation = varchar("operation", 16)
    val payload = text("payload")
    val version = integer("version")
    val status = varchar("status", 16)
    val conflict = bool("conflict")
    val createdAt = safeTimestampWithTimeZone("created_at")
    val syncedAt = safeTimestampWithTimeZone("synced_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

object ChatKeysTable : Table("chat_keys") {
    val id = long("id").autoIncrement()
    val userId = long("user_id").references(UsersTable.id)
    val publicKey = varchar("public_key", 512)
    val fingerprint = varchar("fingerprint", 64)
    val active = bool("active")
    val createdAt = safeTimestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object RateLimitsTable : Table("rate_limits") {
    val code = varchar("code", 64)
    val maxCalls = integer("max_calls")
    val windowSeconds = integer("window_sec")
    val enabled = bool("enabled")
    val updatedAt = safeTimestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(code)
}

object ApiVersionsTable : Table("api_versions") {
    val version = varchar("version", 16)
    val status = varchar("status", 16)
    val note = varchar("note", 255)
    val releasedAt = safeTimestampWithTimeZone("released_at")
    override val primaryKey = PrimaryKey(version)
}

object WidgetConfigsTable : Table("widget_configs") {
    val userId = long("user_id").references(UsersTable.id)
    val widgetType = varchar("widget_type", 48)
    val enabled = bool("enabled")
    val position = integer("position")
    val updatedAt = safeTimestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(userId, widgetType)
}

object ScheduledJobsTable : Table("scheduled_jobs") {
    val id = long("id").autoIncrement()
    val code = varchar("code", 64)
    val name = varchar("name", 120)
    val intervalMs = long("interval_ms")
    val enabled = bool("enabled")
    val lastRun = safeTimestampWithTimeZone("last_run").nullable()
    val createdAt = safeTimestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object ProposalsTable : Table("proposals") {
    val id = long("id").autoIncrement()
    val consumerId = long("consumer_id")
    val technicianId = long("technician_id")
    val serviceId = long("service_id")
    val description = text("description")
    val price = long("price")
    val scheduledDate = date("scheduled_date")
    val customTime = varchar("custom_time", 32).nullable()
    val status = varchar("status", 16)
    val createdAt = safeTimestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object WalletsTable : Table("wallets") {
    val userId = long("user_id")
    val balance = long("balance")
    val updatedAt = safeTimestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(userId)
}

object WalletTransactionsTable : Table("wallet_transactions") {
    val id = long("id").autoIncrement()
    val userId = long("user_id")
    val amount = long("amount")
    val type = varchar("type", 16)
    val reference = varchar("reference", 64)
    val createdAt = safeTimestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object LoyaltyTable : Table("loyalty_points") {
    val userId = long("user_id")
    val points = integer("points")
    val lifetime = integer("lifetime")
    override val primaryKey = PrimaryKey(userId)
}

object SubscriptionsTable : Table("subscriptions") {
    val id = long("id").autoIncrement()
    val consumerId = long("consumer_id")
    val serviceId = long("service_id")
    val addressId = long("address_id")
    val intervalDays = integer("interval_days")
    val nextDate = date("next_date")
    val status = varchar("status", 16)
    val createdAt = safeTimestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object AvailabilityBlocksTable : Table("availability_blocks") {
    val id = long("id").autoIncrement()
    val technicianId = long("technician_id")
    val blockDate = date("block_date")
    val slotLabel = varchar("slot_label", 32)
    val createdAt = safeTimestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object PortfoliosTable : Table("technician_portfolios") {
    val id = long("id").autoIncrement()
    val technicianId = long("technician_id")
    val photoUrl = varchar("photo_url", 512)
    val caption = varchar("caption", 255)
    val createdAt = safeTimestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object PayoutAccountsTable : Table("payout_accounts") {
    val technicianId = long("technician_id")
    val bank = varchar("bank", 64)
    val accountNo = varchar("account_no", 64)
    val accountName = varchar("account_name", 120)
    val updatedAt = safeTimestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(technicianId)
}

object DisputesTable : Table("disputes") {
    val id = long("id").autoIncrement()
    val reportId = long("report_id")
    val orderId = long("order_id")
    val amount = long("amount")
    val decision = varchar("decision", 16)
    val adminId = long("admin_id").nullable()
    val note = text("note")
    val createdAt = safeTimestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object AuditLogsTable : Table("audit_logs") {
    val id = long("id").autoIncrement()
    val adminId = long("admin_id")
    val action = varchar("action", 64)
    val target = varchar("target", 64)
    val createdAt = safeTimestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object AnnouncementsTable : Table("announcements") {
    val id = long("id").autoIncrement()
    val adminId = long("admin_id")
    val title = varchar("title", 120)
    val body = text("body")
    val createdAt = safeTimestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object SystemConfigTable : Table("system_config") {
    val key = varchar("config_key", 64)
    val value = varchar("config_value", 255)
    override val primaryKey = PrimaryKey(key)
}

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
    val categoryId = long("category_id").references(ServiceCategoriesTable.id).nullable()
    val durationMinutes = integer("duration_minutes").default(60)
    val surgeEligible = bool("surge_eligible").default(false)
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
    val respondedAt = safeTimestampWithTimeZone("responded_at").nullable()
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
