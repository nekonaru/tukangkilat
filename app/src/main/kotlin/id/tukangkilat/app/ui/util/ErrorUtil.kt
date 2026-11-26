package id.tukangkilat.app.ui.util

/** Extract a short, user-safe Indonesian message from a Retrofit/serialization/network error. */
fun Throwable.cleanApiError(): String {
    fun extract(raw: String): String? {
        val idx = raw.indexOf("\"message\"")
        if (idx < 0) return null
        val start = raw.indexOf('"', idx + 9) + 1
        val end = raw.indexOf('"', start)
        return if (start > 0 && end > start) raw.substring(start, end) else null
    }
    if (this is retrofit2.HttpException) {
        runCatching { response()?.errorBody()?.string() }
            .getOrNull()?.let { body -> extract(body)?.let { return it } }
    }
    val raw = message ?: return "Terjadi kesalahan. Coba lagi."
    extract(raw)?.let { return it }
    val lower = raw.lowercase()
    if (lower.contains("failed to connect") ||
        lower.contains("unable to resolve host") ||
        lower.contains("connection refused") ||
        lower.contains("network is unreachable") ||
        lower.contains("timed out") ||
        lower.contains("timeout") ||
        lower.contains("broken pipe") ||
        lower.contains("socket") ||
        lower.contains("eof") ||
        lower.contains("http failed") ||
        lower.contains("connect to /")
    ) {
        return "Tidak dapat terhubung ke server. Periksa koneksi dan alamat server, lalu coba lagi."
    }
    if (lower.contains("http 5") || lower.contains("server error")) {
        return "Server sedang bermasalah. Coba lagi nanti."
    }
    return "Terjadi kesalahan. Coba lagi."
}
