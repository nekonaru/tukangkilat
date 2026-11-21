package id.tukangkilat.app.ui.util

/** Extract a short, user-safe Indonesian message from a Retrofit/serialization/network error. */
fun Throwable.cleanApiError(): String {
    val raw = message ?: return "Terjadi kesalahan. Coba lagi."
    val idx = raw.indexOf("\"message\"")
    if (idx >= 0) {
        val start = raw.indexOf('"', idx + 9) + 1
        val end = raw.indexOf('"', start)
        if (start > 0 && end > start) return raw.substring(start, end)
    }
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
