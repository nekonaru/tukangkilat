# Changelog

Semua perubahan penting proyek ini didokumentasikan di file ini.

Format mengikuti [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), dan proyek ini mematuhi [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.2.1] - 2026-11-19

### Diubah

- Ikon launcher, adaptive icon (foreground dan monokrom), serta tanda merek dalam aplikasi dirender ulang dari master beresolusi tinggi dengan tepi yang lebih tajam.
- Layar pembayaran: kartu metode pembayaran terpilih kini berbingkai Oranye kilat dengan ikon oranye sesuai rancangan.
- README: logo merek ditambahkan di header; tangkapan layar (Beranda, Pesanan, Akun) diperbarui dengan tampilan baru; folder `assets/` ditambahkan ke struktur proyek.

## [1.2.0] - 2026-11-19

### Ditambahkan

- **Identitas visual**: ikon aplikasi kini memakai tanda persis dari Identitas Visual (Atap Kilat): ikon launcher kotak, lingkaran, adaptive icon dengan lapisan monokrom untuk ikon tematik Android 13+.
- **Bahasa default**: dokumentasi dan metadata proyek memakai Bahasa Indonesia (`README.md`, `CHANGELOG.md` sebagai dokumen utama; versi Inggris tersedia di `README.en.md` dan `CHANGELOG.en.md`); deskripsi repositori memakai Bahasa Indonesia.

### Diubah

- **UI**: layar Masuk (Bolt + wordmark dua warna "Tukang"/"Kilat", ikon VerifiedUser + tagline sesuai Rancangan), Beranda (wordmark dua warna, kartu layanan tanpa chevron).
- **Palet**: Biru malam `#1B274D`, Biru kabut `#B9CAD6` per Identitas Visual (nilai sebelumnya tidak persis sama).

## [1.1.0] - 2026-11-19

### Ditambahkan

- **Tukang**: bagian "Ulasan saya" di layar akun menampilkan ulasan (bintang, tag, komentar, tanggal) dari konsumen.
- **Tukang**: bagian "Jadwal berikutnya" di layar beranda menampilkan pekerjaan terjadwal yang akan datang.
- **Tukang**: endpoint ulasan `GET /technician/reviews` dengan tes integrasi.
- **Admin**: pengelolaan kode promo (`GET/PUT /admin/promo-codes`) dengan daftar, buat, aktifkan dan nonaktifkan; diskon tampil langsung di alur pembayaran konsumen.
- **Konsumen**: tombol "Bagikan struk" di layar struk.
- Tes backend: siklus promo (buat, nonaktifkan, tolak di pembayaran), RBAC rute promo admin, ulasan teknisi setelah penilaian.

## [1.0.0] - 2026-11-19

### Ditambahkan

- **Aplikasi konsumen**: 12 layar (masuk, beranda, pemesanan, pembayaran, pencarian teknisi, status pesanan, pekerjaan selesai, konfirmasi, penilaian, lapor masalah, tab pesanan, akun) terhubung ke backend sungguhan.
- **Aplikasi tukang**: beranda dengan sakelar online/offline, tawaran pekerjaan real-time dengan hitung mundur 60 detik, aksi pekerjaan berurutan (berangkat, tiba, mulai bekerja, selesaikan dengan foto bukti), pendapatan dengan komisi platform 15%, pengelolaan akun.
- **Panel admin**: ringkasan, verifikasi tukang (KTP dan selfie), laporan masalah dengan penjadwalan kunjungan ulang, filter pesanan, simulasi pembayaran, pengelolaan layanan.
- **Backend**: Ktor dengan autentikasi JWT (access token 15 menit, refresh token 14 hari dengan rotasi), RBAC di setiap rute, hash password bcrypt dengan penguncian setelah 5 percobaan gagal (5 menit), state machine pesanan dengan validasi aktor, dispatcher tukang terdekat yang online dengan keahlian sesuai dalam radius, kode promo, event WebSocket dengan fallback polling, dan migrasi Flyway.
- **Data demo**: layanan (Perbaikan Listrik, Pipa Bocor, Servis AC), slot (09.00, 10.00-11.00, 13.00), promo `HEMAT25`, akun demo untuk admin, konsumen, dan dua teknisi.
- **Verifikasi**: tes integrasi backend mencakup autentikasi, RBAC, siklus pesanan lengkap, simulator pembayaran, aturan pembatalan, dan isolasi akses; tes unit state machine; build debug Android di CI; scan rahasia gitleaks.

[1.2.1]: https://github.com/nekonaru/tukangkilat/releases/tag/v1.2.1
[1.2.0]: https://github.com/nekonaru/tukangkilat/releases/tag/v1.2.0
[1.1.0]: https://github.com/nekonaru/tukangkilat/releases/tag/v1.1.0
[1.0.0]: https://github.com/nekonaru/tukangkilat/releases/tag/v1.0.0
