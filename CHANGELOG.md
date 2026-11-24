# Changelog

Semua perubahan penting proyek ini didokumentasikan di file ini.

Format mengikuti [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), dan proyek ini mematuhi [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.3.0] - 2026-11-25

### Ditambahkan

- **Pusat notifikasi**: perubahan status pesanan, tawaran kerja, tawaran kedaluarsa, dan pesan masuk kini tercatat sebagai notifikasi dalam aplikasi. Buka lewat menu **Notifikasi** di Akun konsumen, Akun teknisi, atau panel admin. Tersedia **Tandai dibaca** dan **Hapus semua**.
- Ini memenuhi fallback PRD K-18 dan T-14: tanpa FCM, WebSocket tetap menghasilkan notifikasi yang bisa dilihat pengguna.

## [2.2.0] - 2026-11-24

### Diubah

- **Desain dan animasi**: tombol utama dan kartu kini memberi umpan balik tekan memakai spring tactile. Layar masuk, Beranda, Pesanan, dan panel admin memiliki entrance halus (fade dan geser 18 px, 300 ms). Logo pada layar "Menyiapkan aplikasi..." bernapas lembut.
- Semua animasi memakai primitive Compose bawaan, tanpa dependensi baru, dan otomatis mati ketika pengguna mengaktifkan pengaturan "hapus animasi" di Android.
- Animasi hanya mengubah `alpha` dan transform, sehingga tidak memicu ulang layout dan tetap ringan.

## [2.1.0] - 2026-11-24

### Ditambahkan

- **Struk PDF (K-20)**: tombol **Bagikan struk PDF** pada layar struk membuat PDF memakai `PdfDocument` bawaan Android dan membagikannya lewat `FileProvider`. Tidak ada dependensi baru.
- **Pelacakan lokasi berkala (T-03, T-07)**: teknisi mengirim lokasi tiap 15 detik saat online dan tiap 5 detik saat status `DALAM_PERJALANAN`, memenuhi alur pelacakan langsung di PRD.

## [2.0.3] - 2026-11-24

### Diubah

- **Native**: Android 12+ kini memakai splash screen sistem dengan tanda merek di atas Biru malam. Tidak ada dependensi tambahan.
- **Lancar**: H2 disetel dengan cache 8 MB dan write delay 500 ms agar operasi lokal lebih ringan. Waktu inisialisasi backend dicatat di logcat pada build debug untuk pemantauan.
- File `themes.xml` dipisah per versi Android agar atribut splash hanya dipakai di API 31+.

## [2.0.2] - 2026-11-24

### Perbaikan bug

- **Kompatibilitas instalasi**: APK kini ditandatangani dengan skema v1, v2, dan v3 sekaligus. Beberapa installer OEM menolak APK yang hanya bertanda tangan v2 dengan pesan "paket tampaknya tidak valid".
- Jika aplikasi versi lama masih terpasang, terutama APK debug, hapus dulu aplikasi tersebut sebelum memasang rilis ini karena tanda tangan debug berbeda dengan rilis.

### Diubah

- **Native**: mengaktifkan callback tombol kembali prediktif di Android 13+.
- Log error backend hanya ditulis di logcat pada build debug, sehingga rilis lebih bersih.

## [2.0.1] - 2026-11-23

### Diubah

- **Performa dan ukuran**: rilis kini memakai R8 dan resource shrinking. APK turun dari sekitar 23 MB menjadi 7,6 MB.
- **Ringan**: permission yang tidak dipakai dihapus (kamera, notifikasi, foreground service, lokasi latar belakang). Jejak H2 dinonaktifkan agar tidak membuat file trace di perangkat.
- **Lancar**: alamat server di-cache dalam memori, jadi tidak ada pembacaan DataStore pada setiap permintaan API. Layar "Menyiapkan aplikasi..." kini menampilkan logo merek.
- **Stabil**: parser timestamp Android menerima format H2 dengan pemisah spasi dan offset tanpa titik dua. Penanganan 401 stale tidak lagi membuat aplikasi crash. Aturan R8 ditambahkan agar JWT dan bcrypt tetap bekerja setelah minifikasi.

## [2.0.0] - 2026-11-23

### Ditambahkan

- **Backend tertanam**: Ktor, Exposed, dan H2 mode PostgreSQL kini berjalan di dalam proses aplikasi. Tidak perlu Docker, PostgreSQL, atau server terpisah untuk memakai aplikasi.
- **Ekspor dan impor database**: panel admin memiliki menu **Ekspor database** dan **Impor database**. Arsip zip berisi semua baris tabel dalam `database.json` dan foto terkait. Impor menimpa seluruh data setelah validasi dan bisa digulung balik jika gagal, lalu sesi dihapus agar pengguna masuk ulang.

### Diubah

- Default alamat API menjadi `http://127.0.0.1:8080`. Network security config mengizinkan cleartext hanya untuk localhost, emulator, dan `.lan`.
- Inisialisasi backend berjalan di background dengan layar "Menyiapkan aplikasi..." agar tidak memicu ANR.
- Column type timestamp khusus di Android memakai `getString`, karena `ResultSet.getObject(index, Class)` tidak tersedia di `java.sql` Android.
- Modul `backend` tetap bisa dijalankan sebagai server JVM untuk pengembangan dan tes.

## [1.3.0] - 2026-11-21

### Ditambahkan

- **Alamat server**: tautan **Atur alamat server** di layar masuk memungkinkan satu APK dipakai di emulator maupun ponsel fisik. Alamat disimpan di perangkat dan dipakai setiap permintaan lewat interceptor OkHttp. Kosongkan kolom untuk kembali ke `http://10.0.2.2:8080`.

### Perbaikan bug

- **Pesan error**: teks teknis mentah (misalnya "failed to connect to /10.0.2.2 ... after 15000ms") tidak lagi tampil di UI. Semua layar kini memakai pesan Bahasa Indonesia yang pendek dan jelas.
- **Layar masuk**: kolom username dan password tidak lagi berbingkai merah saat gagal karena masalah jaringan. Bingkai merah hanya muncul untuk username/password salah atau akun terkunci.
- **Jaringan**: cleartext diizinkan untuk alamat backend lokal yang dikonfigurasi, sesuai kebutuhan demo.

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

[2.3.0]: https://github.com/nekonaru/tukangkilat/releases/tag/v2.3.0
[2.2.0]: https://github.com/nekonaru/tukangkilat/releases/tag/v2.2.0
[2.1.0]: https://github.com/nekonaru/tukangkilat/releases/tag/v2.1.0
[2.0.3]: https://github.com/nekonaru/tukangkilat/releases/tag/v2.0.3
[2.0.2]: https://github.com/nekonaru/tukangkilat/releases/tag/v2.0.2
[2.0.1]: https://github.com/nekonaru/tukangkilat/releases/tag/v2.0.1
[2.0.0]: https://github.com/nekonaru/tukangkilat/releases/tag/v2.0.0
[1.3.0]: https://github.com/nekonaru/tukangkilat/releases/tag/v1.3.0
[1.2.6]: https://github.com/nekonaru/tukangkilat/releases/tag/v1.2.6
[1.2.5]: https://github.com/nekonaru/tukangkilat/releases/tag/v1.2.5
[1.2.4]: https://github.com/nekonaru/tukangkilat/releases/tag/v1.2.4
[1.2.3]: https://github.com/nekonaru/tukangkilat/releases/tag/v1.2.3
[1.2.2]: https://github.com/nekonaru/tukangkilat/releases/tag/v1.2.2
[1.2.1]: https://github.com/nekonaru/tukangkilat/releases/tag/v1.2.1
[1.2.0]: https://github.com/nekonaru/tukangkilat/releases/tag/v1.2.0
[1.1.0]: https://github.com/nekonaru/tukangkilat/releases/tag/v1.1.0
[1.0.0]: https://github.com/nekonaru/tukangkilat/releases/tag/v1.0.0
