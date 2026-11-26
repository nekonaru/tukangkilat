package id.tukangkilat.backend

import java.sql.Connection

/**
 * Helpers for the embedded Android database. Kept in the JVM module so the detection logic can
 * be tested without an Android device.
 */
object EmbeddedSchema {

    /**
     * H2 in PostgreSQL mode exposes an information_schema table also named "users".
     * Only the application table in the public schema counts as an installed schema.
     */
    fun usersTableExists(connection: Connection): Boolean =
        connection.metaData.getTables(null, "public", "users", arrayOf("TABLE")).use { it.next() }

    /** Clear version markers when they exist but the schema does not, so migrations can rerun. */
    fun resetVersionMarkers(connection: Connection) {
        connection.createStatement().use { it.execute("DELETE FROM local_schema_version") }
    }
}
