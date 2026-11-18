package id.tukangkilat.shared

enum class Role {
    KONSUMEN,
    TUKANG,
    ADMIN
}

enum class OrderStatus {
    MENUNGGU_PEMBAYARAN,
    PEMBAYARAN_GAGAL,
    MENCARI_TEKNISI,
    TEKNISI_DITEMUKAN,
    DALAM_PERJALANAN,
    TIBA,
    SEDANG_BEKERJA,
    MENUNGGU_KONFIRMASI,
    DILAPORKAN,
    KUNJUNGAN_ULANG,
    SELESAI,
    DIBATALKAN
}

enum class PaymentMethod {
    TRANSFER,
    DOMPET_DIGITAL,
    TUNAI
}

enum class PaymentStatus {
    MENUNGGU,
    LUNAS,
    GAGAL,
    BELUM_DIBAYAR,
    KEMBALI
}

enum class OfferStatus {
    MENUNGGU,
    DITERIMA,
    DITOLAK,
    KEDALUWARSA
}

enum class IssueReason(val label: String) {
    BELUM_TERATASI("Masalah belum teratasi"),
    MUNCUL_KEMBALI("Masalah muncul kembali"),
    HASIL_KURANG_RAPI("Hasil pekerjaan kurang rapi")
}

enum class IssueStatus {
    TERBUKA,
    KUNJUNGAN_ULANG,
    DITUTUP
}

enum class TechnicianVerificationStatus {
    MENUNGGU_VERIFIKASI,
    TERVERIFIKASI,
    DITOLAK,
    DITANGGUHKAN
}

enum class PhotoKind {
    MASALAH,
    BUKTI,
    LAPORAN
}

enum class WsEventType {
    ORDER_STATUS,
    OFFER_NEW,
    OFFER_EXPIRED,
    TECHNICIAN_LOCATION,
    CHAT_MESSAGE
}
