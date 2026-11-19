package id.tukangkilat.app.ui.util

/** Extract a human-readable message from a Retrofit/serialization error blob. */
fun Throwable.cleanApiError(): String {
    val raw = message ?: return "Terjadi kesalahan"
    val idx = raw.indexOf("\"message\"")
    if (idx >= 0) {
        val start = raw.indexOf('"', idx + 9) + 1
        val end = raw.indexOf('"', start)
        if (start > 0 && end > start) return raw.substring(start, end)
    }
    return raw.take(160)
}
