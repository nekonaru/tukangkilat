package id.tukangkilat.backend

/** Runtime configuration sourced exclusively from environment variables. */
data class AppConfig(
    val dbUrl: String,
    val dbUser: String,
    val dbPassword: String,
    val jwtSecret: String,
    val jwtIssuer: String,
    val uploadDir: String,
    val port: Int,
    val startDispatcher: Boolean,
    val dispatcherTickMs: Long,
    val seedDemoData: Boolean,
    val demoAdminPassword: String?,
    val demoConsumerPassword: String?,
    val demoTechPassword: String?,
    val demoTech2Password: String?,
    val h2InMemory: Boolean
) {
    companion object {
        fun fromEnv(env: Map<String, String> = System.getenv()): AppConfig {
            val h2 = (env["DB_URL"] ?: "jdbc:postgresql://localhost:5432/tukangkilat").startsWith("jdbc:h2")
            return AppConfig(
                dbUrl = env["DB_URL"] ?: "jdbc:postgresql://localhost:5432/tukangkilat",
                dbUser = env["DB_USER"] ?: "tukangkilat",
                dbPassword = env["DB_PASSWORD"] ?: "tukangkilat",
                jwtSecret = env["JWT_SECRET"] ?: "dev-only-insecure-secret-change-me-32bytes!!",
                jwtIssuer = env["JWT_ISSUER"] ?: "tukangkilat",
                uploadDir = env["UPLOAD_DIR"] ?: "uploads",
                port = env["PORT"]?.toIntOrNull() ?: 8080,
                startDispatcher = env["START_DISPATCHER"]?.toBooleanStrictOrNull() ?: true,
                dispatcherTickMs = env["DISPATCHER_TICK_MS"]?.toLongOrNull() ?: 15_000L,
                seedDemoData = env["SEED_DEMO_DATA"]?.toBooleanStrictOrNull() ?: true,
                demoAdminPassword = env["DEMO_ADMIN_PASSWORD"],
                demoConsumerPassword = env["DEMO_CONSUMER_PASSWORD"],
                demoTechPassword = env["DEMO_TECH_PASSWORD"],
                demoTech2Password = env["DEMO_TECH2_PASSWORD"],
                h2InMemory = h2
            )
        }
    }
}
