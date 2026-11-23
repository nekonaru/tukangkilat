package id.tukangkilat.backend

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ColumnType
import org.jetbrains.exposed.sql.IDateColumnType
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.JavaOffsetDateTimeColumnType
import java.sql.ResultSet
import java.time.OffsetDateTime

private val OFFSET_WITHOUT_COLON = Regex("([+-]\\d{2})$")

/** Set by the embedded Android backend to avoid JDBC APIs missing from Android's java.sql. */
object AndroidCompat {
    var useSafeTimestampRead: Boolean = false
}

/**
 * Drop-in replacement for [JavaOffsetDateTimeColumnType] that reads timestamps as String on Android.
 * On the JVM it delegates every operation to the original Exposed implementation.
 */
class SafeOffsetDateTimeColumnType : ColumnType<OffsetDateTime>(), IDateColumnType {
    private val delegate = JavaOffsetDateTimeColumnType()

    override val hasTimePart: Boolean get() = delegate.hasTimePart

    override fun sqlType(): String = delegate.sqlType()

    override fun nonNullValueToString(value: OffsetDateTime): String = delegate.nonNullValueToString(value)

    override fun valueFromDB(value: Any): OffsetDateTime =
        if (!AndroidCompat.useSafeTimestampRead) {
            delegate.valueFromDB(value)
        } else {
            when (value) {
                is OffsetDateTime -> value
                is String -> {
                    val iso = value.replaceFirst(' ', 'T')
                    OFFSET_WITHOUT_COLON.replace(iso, "${'$'}1:00").let { OffsetDateTime.parse(it) }
                }
                else -> delegate.valueFromDB(value)
            }
        }

    override fun readObject(rs: ResultSet, index: Int): Any? =
        if (AndroidCompat.useSafeTimestampRead) rs.getString(index) else delegate.readObject(rs, index)

    override fun notNullValueToDB(value: OffsetDateTime): Any = delegate.notNullValueToDB(value)

    override fun nonNullValueAsDefaultString(value: OffsetDateTime): String =
        delegate.nonNullValueAsDefaultString(value)
}

fun Table.safeTimestampWithTimeZone(name: String): Column<OffsetDateTime> =
    registerColumn(name, SafeOffsetDateTimeColumnType())
