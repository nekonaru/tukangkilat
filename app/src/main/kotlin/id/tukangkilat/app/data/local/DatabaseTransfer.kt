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

    private const val VERSION = 1
    private const val DATA_FILE = "database.json"

    private val TABLES = listOf(
        "users", "technician_profiles", "addresses", "services", "time_slots",
        "promo_codes", "photos", "orders", "order_photos", "order_events",
        "job_offers", "payments", "ratings", "issue_reports", "messages", "withdrawals"
    )

    private val AUTO_ID_TABLES = setOf(
        "users", "addresses", "services", "time_slots", "photos", "orders",
        "order_photos", "order_events", "job_offers", "payments", "issue_reports", "messages", "withdrawals"
    )

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
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                when {
                    entry.name == DATA_FILE -> json = zip.readBytes().toString(Charsets.UTF_8)
                    entry.name.startsWith("photos/") ->
                        photos[entry.name.removePrefix("photos/")] = zip.readBytes()
                }
                zip.closeEntry()
            }
        }
        if (json.isBlank()) error("Arsip tidak berisi database.json")
        val snapshot = Json.decodeFromString<Snapshot>(json)
        require(snapshot.version == VERSION) { "Versi arsip tidak didukung: ${snapshot.version}" }
        TABLES.forEach { require(snapshot.tables.containsKey(it)) { "Tabel $it hilang dari arsip" } }

        Class.forName("org.h2.Driver")
        DriverManager.getConnection(jdbcUrl(context), "", "").use { conn ->
            conn.autoCommit = false
            try {
                TABLES.asReversed().forEach { table ->
                    conn.createStatement().use { it.execute("DELETE FROM $table") }
                }
                TABLES.forEach { table ->
                    snapshot.tables[table]?.forEach { row -> insertRow(conn, table, row) }
                }
                AUTO_ID_TABLES.forEach { table ->
                    val rows = snapshot.tables[table].orEmpty()
                    val max = rows.mapNotNull { it["id"]?.toLongOrNull() }.maxOrNull() ?: 0
                    conn.createStatement().use { it.execute("ALTER TABLE $table ALTER COLUMN id RESTART WITH ${max + 1}") }
                }
                conn.commit()
            } catch (e: Exception) {
                conn.rollback()
                throw e
            } finally {
                conn.autoCommit = true
            }
        }
        val dir = LocalBackend.uploadDir(context)
        dir.mkdirs()
        photos.forEach { (name, bytes) -> File(dir, name).writeBytes(bytes) }
        File(context.filesDir, "datastore/tukangkilat_session.preferences_pb").delete()
    }

    private fun insertRow(conn: java.sql.Connection, table: String, row: Map<String, String?>) {
        if (row.isEmpty()) return
        val columns = row.keys.joinToString(",") { quote(it) }
        val values = row.values.joinToString(",") { value ->
            if (value == null) "NULL" else "'${value.replace("'", "''")}'"
        }
        conn.createStatement().use { it.execute("INSERT INTO $table ($columns) VALUES ($values)") }
    }

    private fun quote(name: String) = "\"$name\""
}
