package id.tukangkilat.backend

import org.junit.jupiter.api.Test
import java.io.File
import java.sql.DriverManager
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EmbeddedSchemaTest {

    private fun freshUrl(): String {
        val dir = File(System.getProperty("java.io.tmpdir"), "schema-test-" + System.nanoTime())
        dir.mkdirs()
        return "jdbc:h2:file:" + File(dir, "appdb").absolutePath +
            ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;TRACE_LEVEL_FILE=0"
    }

    @Test
    fun `fresh H2 database is not mistaken for an installed app schema`() {
        Class.forName("org.h2.Driver")
        DriverManager.getConnection(freshUrl(), "", "").use { conn ->
            conn.createStatement().use { st ->
                st.execute(
                    "CREATE TABLE IF NOT EXISTS local_schema_version (" +
                        "version INT PRIMARY KEY, applied_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP)"
                )
            }
            assertFalse(EmbeddedSchema.usersTableExists(conn), "H2 system users table must be ignored")
            conn.createStatement().use { st ->
                st.execute("CREATE TABLE users (id BIGINT PRIMARY KEY, username VARCHAR(64))")
            }
            assertTrue(EmbeddedSchema.usersTableExists(conn), "public.users must be detected")
            conn.createStatement().use { st ->
                st.execute("INSERT INTO local_schema_version (version) VALUES (1)")
            }
            EmbeddedSchema.resetVersionMarkers(conn)
            conn.createStatement().use { st ->
                st.executeQuery("SELECT COUNT(*) FROM local_schema_version").use { rs ->
                    rs.next()
                    assertTrue(rs.getInt(1) == 0, "markers must be cleared")
                }
            }
            val versions = EmbeddedSchema.detectedVersions(conn)
            assertTrue(1 in versions, "users table means V1 is present")
            assertTrue(2 !in versions, "no is_active column means V2 is absent")
        }
    }
}
