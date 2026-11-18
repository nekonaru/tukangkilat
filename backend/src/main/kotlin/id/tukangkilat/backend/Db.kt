package id.tukangkilat.backend

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import java.io.File

/** Database lifecycle: migrate with Flyway, connect Exposed. */
object Db {

    fun migrate(config: AppConfig) {
        val flyway = Flyway.configure()
            .dataSource(config.dbUrl, config.dbUser, config.dbPassword)
            .locations("classpath:db/migration")
            .baselineOnMigrate(true)
            .load()
        flyway.migrate()
    }

    fun connect(config: AppConfig): HikariDataSource {
        val hikari = HikariConfig().apply {
            jdbcUrl = config.dbUrl
            username = config.dbUser
            password = config.dbPassword
            maximumPoolSize = 10
            if (config.h2InMemory) {
                // H2 does not support the same driver class resolution as PostgreSQL
                driverClassName = "org.h2.Driver"
            }
        }
        val dataSource = HikariDataSource(hikari)
        Database.connect(dataSource)
        return dataSource
    }
}

/** Ensure the upload directory exists. */
fun ensureUploadDir(path: String) {
    File(path).mkdirs()
}
