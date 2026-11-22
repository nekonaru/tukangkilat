package id.tukangkilat.app.data.local

import android.content.Context
import android.content.SharedPreferences
import id.tukangkilat.backend.AppConfig
import id.tukangkilat.backend.AndroidCompat
import id.tukangkilat.backend.ServerErrorHook
import id.tukangkilat.backend.Seed
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
    private const val MIGRATIONS = "V1__init.sql,V2__add_promo_active.sql,V3__dispatch_indexes.sql"
    const val BASE_URL = "http://127.0.0.1:8080"

    private var server: EmbeddedServer<*, *>? = null
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    fun databasePath(context: Context): File = File(context.filesDir, "database/$DB_NAME")

    fun uploadDir(context: Context): File = File(context.filesDir, "uploads")

    private fun jdbcUrl(context: Context): String =
        "jdbc:h2:file:${databasePath(context).absolutePath};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"

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
        databasePath(context).parentFile?.mkdirs()
        val uploads = uploadDir(context)
        ensureUploadDir(uploads.absolutePath)

        Class.forName("org.h2.Driver")
        AndroidCompat.useSafeTimestampRead = true
        ServerErrorHook.log = { android.util.Log.e("TukangKilatBackend", "Unhandled", it) }
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
        _ready.value = true
    }

    private fun createSchemaIfNeeded(context: Context, url: String): Boolean =
        DriverManager.getConnection(url, "", "").use { connection ->
            val exists = try {
                connection.createStatement().use { st ->
                    st.executeQuery("SELECT 1 FROM users LIMIT 1").use { it.next() }
                }
            } catch (e: Exception) {
                false
            }
            if (exists) return false
            val loader = LocalBackend::class.java.classLoader
            val statements = MIGRATIONS.split(",").flatMap { name ->
                val sql = loader.getResourceAsStream("db/migration/$name")
                    ?.bufferedReader()?.use { it.readText() }
                    ?: error("Migrasi $name tidak ditemukan di APK")
                sql.lines()
                    .filterNot { it.trim().startsWith("--") }
                    .joinToString("\n")
                    .split(";")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
            }
            connection.createStatement().use { st ->
                statements.forEach { st.execute(it) }
            }
            true
        }
}
