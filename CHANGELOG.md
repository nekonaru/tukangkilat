# Changelog

Semua perubahan penting proyek ini didokumentasikan di file ini.

Format mengikuti [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), dan proyek ini mematuhi [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.2.6] - 2026-11-21

### Peningkatan

- **Performa**: Beranda konsumen kini memuat layanan, alamat, dan pesanan secara paralel; Pemesanan memuat layanan dan alamat secara paralel; beranda teknisi memuat profil, pesanan, penghasilan, dan tawaran secara paralel. Waktu muat dashboard kini ditentukan oleh permintaan terlama, bukan jumlah permintaan.

## [1.2.5] - 2026-11-20

### Diubah

- **Desain layar 02 (Beranda)**: baris lokasi kini menampilkan label alamat diikuti alamat lengkap, misalnya "Rumah · Jl. Tebet Barat Dalam...", sesuai draf.
- **Tes integrasi**: dua tes baru mengunci alur gagal bayar lalu bayar ulang dengan metode lain, serta CRUD alamat dan jadwal ulang saat teknisi belum ditentukan. Total tes backend menjadi 20.

## [1.2.4] - 2026-11-20

### Diubah

- **Backend**: migrasi `V3__dispatch_indexes.sql` menambah indeks komposit untuk query perawatan (`payments(status, expires_at)`, `orders(status, updated_at)`), penawaran kerja (`job_offers(order_id, status, expires_at)`), dan pencarian teknisi terverifikasi online. Migrasi terverifikasi di PostgreSQL dan H2.
- **Desain layar 10 (Pembayaran gagal)**: ikon kartu merah dalam kotak merah muda sesuai draf.
- **Audit alur**: lapor masalah sampai kunjungan ulang (tawaran kembali ke teknisi yang sama) dan pembatalan pesanan kini terverifikasi dengan 8 pengecekan end-to-end.

## [1.2.3] - 2026-11-20

### Diubah

- **Desain layar 08 (Penilaian)**: label jumlah bintang kini Oranye kilat, tag cepat memakai bingkai oranye dengan teks oranye, dan kartu struk memakai hijau pucat dengan centang di dalam kartu, sesuai draf.
- **Desain layar 12 (Lapor masalah)**: alasan yang dipilih kini berbingkai oranye; unggah foto kini berupa kotak putus-putus selebar layar dengan keterangan "Maksimal 3 foto · JPG atau PNG", sesuai draf.
- **Dokumentasi**: `docs/DESIGN_AUDIT.md` memakai Biru malam `#1B274D` dan mencatat penyesuaian 08, 12, dan chip slot waktu.

## [1.2.2] - 2026-11-20

### Diubah

- **Backend**: perawatan otomatis (kedaluwarsa pembayaran dan konfirmasi otomatis setelah 24 jam) sekarang berjalan di dalam transaksi database. Sebelumnya kedua fungsi melempar `IllegalStateException: No transaction in context` pada setiap tick, sehingga pembayaran kedaluwarsa dan konfirmasi lama tidak pernah diproses. Log backend bersih.
- **Aplikasi**: layar Beranda, Pesanan, beranda dan daftar pekerjaan tukang, serta ringkasan admin kini memuat ulang data saat aplikasi kembali ke latar depan. Status terbaru langsung tampil tanpa menutup aplikasi.
- **Pemesanan**: chip slot waktu yang tersedia kini memakai teks Biru malam di atas kartu putih dengan garis tepi sesuai rancangan. Sebelumnya teks putih di atas kartu putih membuat jam tidak terbaca.
- **Tes**: tes integrasi baru untuk kedua fungsi perawatan otomatis. `.gitignore` mengabaikan folder `backend/uploads/` yang dibuat saat unggah foto.

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

[1.2.6]: https://github.com/nekonaru/tukangkilat/releases/tag/v1.2.6
[1.2.5]: https://github.com/nekonaru/tukangkilat/releases/tag/v1.2.5
[1.2.4]: https://github.com/nekonaru/tukangkilat/releases/tag/v1.2.4
[1.2.3]: https://github.com/nekonaru/tukangkilat/releases/tag/v1.2.3
[1.2.2]: https://github.com/nekonaru/tukangkilat/releases/tag/v1.2.2
[1.2.1]: https://github.com/nekonaru/tukangkilat/releases/tag/v1.2.1
[1.2.0]: https://github.com/nekonaru/tukangkilat/releases/tag/v1.2.0
[1.1.0]: https://github.com/nekonaru/tukangkilat/releases/tag/v1.1.0
[1.0.0]: https://github.com/nekonaru/tukangkilat/releases/tag/v1.0.0
