package id.tukangkilat.app.data.local

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.sql.DriverManager
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Portable snapshot of the embedded backend: all rows as JSON plus uploaded photos. */
object DatabaseTransfer {

    private const val VERSION = 10
    private const val DATA_FILE = "database.json"
    private const val MAX_JSON_BYTES = 64 * 1024 * 1024
    private const val MAX_PHOTO_BYTES = 8 * 1024 * 1024
    private const val PHOTO_NAME_REGEX = "[A-Za-z0-9._-]+"

    /** Parent tables come before child tables so a reverse-order delete satisfies foreign keys. */
    private val TABLES = listOf(
        "service_categories", "users", "refresh_tokens", "technician_profiles", "addresses", "services", "time_slots",
        "promo_codes", "photos", "orders", "order_photos", "order_events",
        "job_offers", "payments", "ratings", "issue_reports", "messages", "withdrawals",
        "direct_messages", "proposals", "wallets", "wallet_transactions", "loyalty_points",
        "subscriptions", "availability_blocks", "technician_portfolios", "payout_accounts",
        "disputes", "audit_logs", "announcements", "system_config", "review_photos",
        "auto_online_schedules", "message_reports", "service_bundles", "promo_campaigns",
        "vouchers", "referrals", "installment_plans", "membership_tiers", "wallet_settings",
        "review_votes", "technician_replies", "signatures", "checklist_templates",
        "checklist_items", "refund_requests", "user_preferences", "offline_queue",
        "tech_documents", "service_areas", "auto_accept_rules", "checkins", "job_materials",
        "tech_badges", "chat_templates", "leave_requests", "work_journals", "equipment_items",
        "feature_flags", "content_pages", "scheduled_broadcasts", "review_reports", "sla_policies",
        "sla_breaches", "admin_notes", "weekly_reports",
        "trusted_devices", "user_sessions", "consents", "user_blocks", "sos_alerts",
        "app_locks", "two_factors", "login_alerts", "subadmin_roles", "incidents",
        "retention_policies", "backup_jobs",
        "custom_bundles", "flash_sales", "group_buys", "job_bids", "gift_cards",
        "point_shop_items", "point_redemptions", "tips", "wishlists", "price_alerts",
        "price_history", "ledger_entries", "commission_rules",
        "reputation_scores", "certifications", "courses", "enrollments", "gear_items", "teams",
        "team_helpers", "availability_heatmap", "work_permits", "earnings_forecasts",
        "tax_simulations", "payout_schedules", "quality_scores", "storefronts"
    )

    /** Tables present in every snapshot format, used to validate older archives. */
    private val LEGACY_TABLES = listOf(
        "users", "refresh_tokens", "technician_profiles", "addresses", "services", "time_slots",
        "promo_codes", "photos", "orders", "order_photos", "order_events", "job_offers",
        "payments", "ratings", "issue_reports", "messages", "withdrawals", "direct_messages", "proposals"
    )

    private val AUTO_ID_TABLES = setOf(
        "users", "refresh_tokens", "addresses", "services", "time_slots", "photos", "orders",
        "order_photos", "order_events", "job_offers", "payments", "issue_reports", "messages", "withdrawals",
        "direct_messages", "proposals", "wallet_transactions", "subscriptions", "availability_blocks",
        "technician_portfolios", "disputes", "audit_logs", "announcements", "review_photos",
        "auto_online_schedules", "message_reports", "service_categories", "service_bundles",
        "promo_campaigns", "vouchers", "referrals", "installment_plans", "signatures",
        "checklist_templates", "checklist_items", "refund_requests", "offline_queue",
        "tech_documents", "service_areas", "checkins", "job_materials", "tech_badges",
        "chat_templates", "leave_requests", "work_journals", "equipment_items", "content_pages", "scheduled_broadcasts", "review_reports", "sla_breaches",
        "admin_notes", "weekly_reports", "custom_bundles", "flash_sales", "group_buys",
        "job_bids", "gift_cards", "point_redemptions", "tips", "wishlists", "price_alerts",
        "price_history", "ledger_entries", "certifications", "enrollments", "gear_items",
        "teams", "team_helpers", "work_permits", "tax_simulations", "quality_scores", "availability_heatmap", "earnings_forecasts"
    )

    private val SAFE_TABLES = TABLES.toSet()

    @Serializable
    data class Snapshot(
        val version: Int,
        val exportedAt: String,
        val tables: Map<String, List<Map<String, String?>>>
    )

    private fun jdbcUrl(context: Context): String =
        "jdbc:h2:file:${LocalBackend.databasePath(context).absolutePath};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;TRACE_LEVEL_FILE=0;CACHE_SIZE=8192"

    /** Write a zip snapshot to [out]. Never close [out], the caller owns the stream. */
    fun export(context: Context, out: OutputStream) {
        Class.forName("org.h2.Driver")
        val snapshot: Snapshot
        val photoNames = mutableListOf<String>()
        DriverManager.getConnection(jdbcUrl(context), "", "").use { conn ->
            val tables = linkedMapOf<String, List<Map<String, String?>>>()
            TABLES.forEach { table ->
                val rows = mutableListOf<Map<String, String?>>()
                conn.createStatement().use { st ->
                    st.executeQuery("SELECT * FROM $table").use { rs ->
                        val md = rs.metaData
                        val columns = (1..md.columnCount).map { md.getColumnLabel(it) }
                        while (rs.next()) {
                            val row = linkedMapOf<String, String?>()
                            columns.forEachIndexed { index, name -> row[name] = rs.getString(index + 1) }
                            rows += row
                        }
                    }
                }
                tables[table] = rows
                if (table == "photos") {
                    rows.forEach { row -> row["filename"]?.let { photoNames += it } }
                }
            }
            snapshot = Snapshot(VERSION, Instant.now().toString(), tables)
        }
        val json = Json.encodeToString(Snapshot.serializer(), snapshot)
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(DATA_FILE))
            zip.write(json.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            photoNames.forEach { name ->
                val file = File(LocalBackend.uploadDir(context), name)
                if (file.exists()) {
                    zip.putNextEntry(ZipEntry("photos/$name"))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    /** Replace all local data with [in]. Throws on invalid or incompatible snapshots. */
    fun import(context: Context, input: InputStream) {
        var json = ""
        val photos = mutableMapOf<String, ByteArray>()
        var jsonBytes = 0
        var totalPhotoBytes = 0
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                when {
                    entry.name == DATA_FILE -> {
                        val bytes = readBounded(zip, MAX_JSON_BYTES)
                        jsonBytes += bytes.size
                        require(jsonBytes <= MAX_JSON_BYTES) { "Arsip database terlalu besar" }
                        json = bytes.toString(Charsets.UTF_8)
                    }
                    entry.name.startsWith("photos/") -> {
                        val name = entry.name.removePrefix("photos/")
                        require(name.isNotBlank() && !name.contains('/') && !name.contains('\\') &&
                            name != "." && name != ".." && name.matches(Regex(PHOTO_NAME_REGEX))) {
                            "Nama foto tidak valid: $name"
                        }
                        val bytes = readBounded(zip, MAX_PHOTO_BYTES)
                        totalPhotoBytes += bytes.size
                        require(totalPhotoBytes <= MAX_JSON_BYTES) { "Ukuran foto total terlalu besar" }
                        photos[name] = bytes
                    }
                }
                zip.closeEntry()
            }
        }
        if (json.isBlank()) error("Arsip tidak berisi database.json")
        val snapshot = Json.decodeFromString<Snapshot>(json)
        require(snapshot.version in 2..VERSION) { "Versi arsip tidak didukung: ${snapshot.version}" }
        val tablesToRestore = TABLES.filter { snapshot.tables.containsKey(it) }
        require(tablesToRestore.containsAll(LEGACY_TABLES)) { "Arsip tidak berisi tabel dasar" }
        snapshot.tables.keys.forEach { require(it in SAFE_TABLES) { "Tabel asing ditolak: $it" } }
        snapshot.tables.forEach { (table, rows) ->
            rows.forEach { row ->
                row.keys.forEach { column -> require(column.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))) {
                    "Nama kolom tidak valid: $column"
                } }
            }
            require(table in SAFE_TABLES) { "Tabel asing ditolak: $table" }
        }

        Class.forName("org.h2.Driver")
        DriverManager.getConnection(jdbcUrl(context), "", "").use { conn ->
            conn.autoCommit = false
            try {
                TABLES.asReversed().forEach { table ->
                    conn.createStatement().use { it.execute("DELETE FROM $table") }
                }
                tablesToRestore.forEach { table ->
                    snapshot.tables[table]?.forEach { row -> insertRow(conn, table, row) }
                }
                conn.commit()
                // H2 implicitly commits DDL, so identity resets must run after the data commit.
                val identityTables = AUTO_ID_TABLES + setOf(
                    "trusted_devices", "user_sessions", "sos_alerts", "login_alerts",
                    "subadmin_roles", "incidents", "backup_jobs"
                )
                val restarts = tablesToRestore.filter { it in identityTables }.map { table ->
                    val rows = snapshot.tables[table].orEmpty()
                    val max = rows.mapNotNull { it["id"]?.toLongOrNull() }.maxOrNull() ?: 0
                    "ALTER TABLE $table ALTER COLUMN id RESTART WITH ${max + 1}"
                }
                restarts.forEach { sql -> conn.createStatement().use { it.execute(sql) } }
            } catch (e: Exception) {
                conn.rollback()
                throw e
            } finally {
                conn.autoCommit = true
            }
        }
        val dir = LocalBackend.uploadDir(context)
        dir.mkdirs()
        // Remove photos that are no longer referenced by the imported snapshot.
        dir.listFiles()?.forEach { file -> if (file.isFile && file.name !in photos) file.delete() }
        photos.forEach { (name, bytes) -> File(dir, name).writeBytes(bytes) }
        File(context.filesDir, "datastore/tukangkilat_session.preferences_pb").delete()
    }

    private fun readBounded(input: InputStream, limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            total += read
            require(total <= limit) { "Entry arsip melebihi batas ukuran" }
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private fun insertRow(conn: java.sql.Connection, table: String, row: Map<String, String?>) {
        if (row.isEmpty()) return
        val columns = row.keys.joinToString(",") { quote(it) }
        val values = row.values.joinToString(",") { value ->
            if (value == null) "NULL" else "'${value.replace("'", "''")}'"
        }
        conn.createStatement().use { it.execute("INSERT INTO $table ($columns) VALUES ($values)") }
    }

    private fun quote(name: String) = "\"${name.replace("\"", "\"\"")}\""
}
