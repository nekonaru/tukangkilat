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

    /** Infer which of the pre-marker migrations a legacy database actually has. */
    fun detectedVersions(connection: Connection): Set<Int> {
        if (!usersTableExists(connection)) return emptySet()
        val versions = mutableSetOf(1)
        if (columnExists(connection, "promo_codes", "is_active")) versions += 2
        if (indexExists(connection, "idx_payments_status_expires")) versions += 3
        if (tableExists(connection, "withdrawals")) versions += 4
        return versions
    }

    private fun tableExists(connection: Connection, name: String): Boolean =
        connection.metaData.getTables(null, "public", name, arrayOf("TABLE")).use { it.next() }

    private fun columnExists(connection: Connection, table: String, column: String): Boolean =
        connection.metaData.getColumns(null, "public", table, column).use { it.next() }

    private fun indexExists(connection: Connection, name: String): Boolean =
        // H2 rejects a null table name, so query the table that owns the V3 indexes.
        connection.metaData.getIndexInfo(null, "public", "payments", false, false).use { rs ->
            while (rs.next()) {
                if (name.equals(rs.getString("INDEX_NAME"), ignoreCase = true)) return true
            }
            false
        }
}
