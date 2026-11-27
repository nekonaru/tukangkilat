package id.tukangkilat.app.data.local

import android.content.Context
import android.content.SharedPreferences
import id.tukangkilat.app.BuildConfig
import id.tukangkilat.backend.AppConfig
import id.tukangkilat.backend.AndroidCompat
import id.tukangkilat.backend.ServerErrorHook
import id.tukangkilat.backend.Seed
import id.tukangkilat.backend.EmbeddedSchema
import id.tukangkilat.backend.ensureUploadDir
import id.tukangkilat.backend.module
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.Database
import java.io.File
import java.security.SecureRandom
import java.sql.DriverManager
import java.util.Base64

/**
 * Runs the whole Tukang Kilat backend inside the app process.
 * Data lives in a local H2 database (PostgreSQL compatibility mode). The existing
 * API client talks to a loopback Ktor server on port 8080.
 */
object LocalBackend {

    private const val PORT = 8080
    private const val DB_NAME = "tukangkilat"
    const val BASE_URL = "http://127.0.0.1:8080"

    /** Migration files and the schema version they produce. Append only, never reorder. */
    private val MIGRATIONS = listOf(
        1 to "V1__init.sql",
        2 to "V2__add_promo_active.sql",
        3 to "V3__dispatch_indexes.sql",
        4 to "V4__withdrawals.sql",
        5 to "V5__direct_chat_proposals.sql",
        6 to "V6__wallet_loyalty_subscriptions.sql",
        7 to "V7__technician_workbench.sql",
        8 to "V8__admin_control.sql",
        9 to "V9__platform_polish.sql",
        10 to "V10__commerce.sql",
        11 to "V11__technician_ops.sql",
        12 to "V12__platform_admin.sql",
        13 to "V13__trust_identity.sql",
        14 to "V14__marketplace.sql"
    )

    private var server: EmbeddedServer<*, *>? = null
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()
    private val _startupError = MutableStateFlow<String?>(null)
    val startupError: StateFlow<String?> = _startupError.asStateFlow()

    fun databasePath(context: Context): File = File(context.filesDir, "database/$DB_NAME")

    fun uploadDir(context: Context): File = File(context.filesDir, "uploads")

    private fun jdbcUrl(context: Context): String =
        "jdbc:h2:file:${databasePath(context).absolutePath};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;TRACE_LEVEL_FILE=0;CACHE_SIZE=8192;WRITE_DELAY=500"

    private fun jwtSecret(context: Context): String {
        val prefs: SharedPreferences = context.getSharedPreferences("tukangkilat_local", Context.MODE_PRIVATE)
        prefs.getString("jwt_secret", null)?.let { return it }
        val bytes = ByteArray(48)
        SecureRandom().nextBytes(bytes)
        val secret = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        prefs.edit().putString("jwt_secret", secret).apply()
        return secret
    }

    /** Initialize schema, seed demo data and start the loopback server. Safe to call once per process. */
    suspend fun start(context: Context) = withContext(Dispatchers.IO) {
        if (server != null) return@withContext
        _startupError.value = null
        val startedAt = System.currentTimeMillis()
        try {
            databasePath(context).parentFile?.mkdirs()
            val uploads = uploadDir(context)
            ensureUploadDir(uploads.absolutePath)

            Class.forName("org.h2.Driver")
            AndroidCompat.useSafeTimestampRead = true
            ServerErrorHook.log = if (BuildConfig.DEBUG) {
                { android.util.Log.e("TukangKilatBackend", "Unhandled", it) }
            } else null
            val url = jdbcUrl(context)
            val isFresh = createSchemaIfNeeded(context, url)

            if (isFresh) {
                // Tokens from a previous backend are invalid for this local database.
                File(context.filesDir, "datastore/tukangkilat_session.preferences_pb").delete()
            }

            Database.connect(url = url, driver = "org.h2.Driver", user = "", password = "")

            val localConfig = AppConfig(
                dbUrl = url,
                dbUser = "",
                dbPassword = "",
                jwtSecret = jwtSecret(context),
                jwtIssuer = "tukangkilat-local",
                uploadDir = uploads.absolutePath,
                port = PORT,
                startDispatcher = true,
                dispatcherTickMs = 15_000L,
                seedDemoData = true,
                demoAdminPassword = "admin12345",
                demoConsumerPassword = "rina12345",
                demoTechPassword = "budi12345",
                demoTech2Password = "sari12345",
                h2InMemory = false
            )
            Seed.seedIfEmpty(localConfig)

            server = embeddedServer(CIO, port = PORT, host = "127.0.0.1") {
                module(localConfig)
            }.start(wait = false)
            if (BuildConfig.DEBUG) {
                android.util.Log.d("TukangKilatBackend", "Ready in ${System.currentTimeMillis() - startedAt} ms")
            }
            _ready.value = true
        } catch (e: Exception) {
            server = null
            _ready.value = false
            _startupError.value = e.message ?: e.javaClass.simpleName
            if (BuildConfig.DEBUG) android.util.Log.e("TukangKilatBackend", "Start failed", e)
        }
    }

    /** Retry startup after a failure, for example a temporary port conflict. */
    suspend fun retry(context: Context) {
        stopForImport()
        start(context)
    }

    /** Stop the loopback server and its background dispatchers before replacing the database. */
    suspend fun stopForImport() = withContext(Dispatchers.IO) {
        server?.stop()
        server = null
        // Keep ready = true: the Import screen stays mounted so its coroutine survives.
    }

    /** Start again after an import has replaced the local database. */
    suspend fun restartAfterImport(context: Context) = withContext(Dispatchers.IO) {
        start(context)
    }

    /**
     * Bring the local schema to the latest version and return true when the database is brand new.
     * Existing databases without a version marker are adopted at the current version instead of
     * re-running old migrations, which would fail on already existing tables.
     */
    private fun createSchemaIfNeeded(context: Context, url: String): Boolean =
        DriverManager.getConnection(url, "", "").use { connection ->
            connection.createStatement().use { st ->
                st.execute(
                    "CREATE TABLE IF NOT EXISTS local_schema_version (" +
                        "version INT PRIMARY KEY, applied_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP)"
                )
            }
            val applied = mutableSetOf<Int>()
            connection.createStatement().use { st ->
                st.executeQuery("SELECT version FROM local_schema_version").use { rs ->
                    while (rs.next()) applied += rs.getInt(1)
                }
            }
            // Detect the table itself, not row presence. An empty users table from an
            // interrupted legacy install must still be adopted instead of re-created.
            val tableExists = EmbeddedSchema.usersTableExists(connection)
            if (!tableExists && applied.isNotEmpty()) {
                // A previous build could mark migrations as applied without creating the schema.
                // Clear those markers so this launch can install the schema from scratch.
                EmbeddedSchema.resetVersionMarkers(connection)
                applied.clear()
            }
            if (tableExists && applied.isEmpty()) {
                // Adopt only the migrations this legacy database actually contains.
                EmbeddedSchema.detectedVersions(connection).forEach { version ->
                    connection.createStatement().use { st ->
                        st.execute("INSERT INTO local_schema_version (version) VALUES ($version)")
                    }
                    applied += version
                }
            }
            val loader = LocalBackend::class.java.classLoader
            MIGRATIONS.filter { (version, _) -> version !in applied }.forEach { (version, name) ->
                val sql = loader.getResourceAsStream("db/migration/$name")
                    ?.bufferedReader()?.use { it.readText() }
                    ?: error("Migrasi $name tidak ditemukan di APK")
                // H2 commits DDL implicitly, so a partial migration cannot be rolled back.
                // Making statements idempotent lets the next launch finish the same version.
                val idempotentSql = sql
                    .replace(Regex("CREATE TABLE (?!IF NOT EXISTS)"), "CREATE TABLE IF NOT EXISTS ")
                    .replace(Regex("CREATE INDEX (?!IF NOT EXISTS)"), "CREATE INDEX IF NOT EXISTS ")
                val statements = splitSql(idempotentSql)
                connection.autoCommit = false
                try {
                    connection.createStatement().use { st -> statements.forEach { st.execute(it) } }
                    connection.createStatement().use { st ->
                        st.execute("INSERT INTO local_schema_version (version) VALUES ($version)")
                    }
                    connection.commit()
                } catch (e: Exception) {
                    connection.rollback()
                    throw e
                } finally {
                    connection.autoCommit = true
                }
            }
            !tableExists
        }

    /** Split SQL on semicolons that are outside strings and comments. */
    private fun splitSql(sql: String): List<String> {
        val statements = mutableListOf<String>()
        val current = StringBuilder()
        var inString = false
        var inLineComment = false
        var inBlockComment = false
        var i = 0
        while (i < sql.length) {
            val c = sql[i]
            val next = if (i + 1 < sql.length) sql[i + 1] else '\u0000'
            when {
                inLineComment -> {
                    if (c == '\n') {
                        inLineComment = false
                        current.append(c)
                    }
                }
                inBlockComment -> {
                    if (c == '*' && next == '/') {
                        inBlockComment = false
                        i++
                    }
                }
                inString -> {
                    current.append(c)
                    if (c == '\'') {
                        if (next == '\'') {
                            current.append(next)
                            i++
                        } else {
                            inString = false
                        }
                    }
                }
                c == '-' && next == '-' -> {
                    inLineComment = true
                    i++
                }
                c == '/' && next == '*' -> {
                    inBlockComment = true
                    i++
                }
                c == '\'' -> {
                    inString = true
                    current.append(c)
                }
                c == ';' -> {
                    val statement = current.toString().trim()
                    if (statement.isNotEmpty()) statements += statement
                    current.setLength(0)
                }
                else -> current.append(c)
            }
            i++
        }
        val tail = current.toString().trim()
        if (tail.isNotEmpty()) statements += tail
        return statements
    }
}