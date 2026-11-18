# PRD Tukang Kilat

Nov 18, 2026 · @Nico

Tukang Kilat adalah aplikasi Android native (Kotlin) yang mempertemukan konsumen dengan teknisi rumah terverifikasi untuk listrik, pipa, dan servis AC, lengkap dengan pembayaran, pelacakan, dan penilaian.

## 1. Ringkasan produk

Tujuan tugas akhir ini: membangun satu sistem utuh yang benar-benar berjalan, bukan sekadar prototipe, dengan tiga bagian: aplikasi Android (satu APK, tiga peran), server backend Kotlin, dan database.

**Masalah yang diselesaikan**

- Konsumen sulit menemukan tukang tepercaya, harganya tidak jelas, dan tidak bisa melacak kedatangannya.
- Tukang tidak punya jalur pesanan yang rapi, bukti kerja, dan reputasi yang terukur.

**Sasaran produk**

| Sasaran | Ukuran keberhasilan |
| --- | --- |
| Pesan cepat | Dari Beranda sampai pembayaran selesai dalam 2 menit atau kurang |
| Harga transparan | Total = biaya jasa + biaya kunjungan, terlihat sebelum bayar |
| Terpercaya | Tukang terverifikasi admin, ada rating, jumlah pekerjaan, dan bukti foto hasil kerja |
| Terlacak | Konsumen melihat status dan jarak tukang secara langsung |
| Utuh | Satu pesanan bisa berjalan dari pemesanan sampai penilaian, melibatkan konsumen, tukang, dan admin |

**Ruang lingkup**

- Masuk: login berbasis peran, pemesanan, pembayaran, penugasan tukang, pelacakan, bukti kerja, konfirmasi, penilaian, laporan masalah dan kunjungan ulang, chat sederhana, panel admin minimal.
- Tidak masuk: iOS, OTP SMS, payment gateway produksi (diganti simulator, lihat bagian 8), pencairan dana ke rekening tukang secara nyata, dan aplikasi web publik.

## 2. Temuan dari desain dan keputusan

Desain berisi 12 layar konsumen: 9 layar jalur utama dan 3 layar kegagalan. Palet visual: latar biru tua (sekitar #1B2F4D), aksen oranye (sekitar #E8681E), kartu terang, status sukses hijau, galat merah. Nilai warna ini perkiraan dari gambar, bukan dari berkas desain asli.

| No | Layar | Isi yang terlihat | Berpindah ke |
| --- | --- | --- | --- |
| 01 | Masuk | Logo, tagline "Ahlinya rumah, siap membantu", kolom Username dan Password, tombol LOGIN | 02, atau 09 bila gagal |
| 02 | Beranda | Lokasi "Rumah · Tebet, Jakarta Selatan", pencarian, 3 layanan (Listrik Rp125.000, Pipa Bocor Rp100.000, Servis AC Rp85.000), kartu pesanan aktif TK-271026 "Terjadwal", bottom nav Beranda/Pesanan/Akun | 03 |
| 03 | Pemesanan | Layanan terpilih, cerita masalah, foto opsional, tanggal, slot 09.00 / 10.00–11.00 / 13.00, alamat + peta + "Gunakan lokasi saya" | 04 |
| 04 | Pembayaran | Ringkasan, biaya jasa Rp125.000, biaya kunjungan Rp25.000, metode (Transfer bank BCA, Dompet digital, Tunai), kolom kode promo, "Bayar sekarang" | 05, atau 10 bila gagal |
| 05 | Status pesanan | Peta posisi tukang, estimasi tiba, kartu tukang (Budi Santoso, 4,9, 128 pekerjaan, Telepon, Pesan), linimasa 4 langkah, "Batalkan pesanan" | 06, atau 11 bila ditolak |
| 06 | Pekerjaan selesai | Bukti foto hasil kerja, waktu selesai, catatan tukang | 07 |
| 07 | Konfirmasi hasil | "Sudah beres" atau "Belum, lapor masalah" | 08 atau 12 |
| 08 | Penilaian | Bintang 1–5, tag cepat, komentar, struk lunas Rp150.000 | 02 |
| 09 | Masuk gagal | "Nomor belum terdaftar", kolom Nomor HP, "Coba lagi", "Daftar" | 01 |
| 10 | Pembayaran gagal | Rincian pesanan, info dana kembali 1–3 hari kerja, "Coba lagi", "Ganti metode pembayaran" | 04 |
| 11 | Teknisi menolak | "Mencari teknisi lain", jadwal dan total tidak berubah, "Jadwal ulang" | 05 saat pengganti ditemukan |
| 12 | Lapor masalah | 3 alasan, keterangan, maksimal 3 foto, "Minta kunjungan ulang" | 05 |

**Celah dan keputusan**

1. **Login tidak konsisten.** Layar 01 memakai Username + Password, layar 09 memakai Nomor HP + OTP. Keputusan: ikuti layar 01 (username dan password). Layar 09 diubah menjadi "Username atau password salah" dengan tata letak yang sama. OTP tidak dibuat.
2. **Total di layar 04 tidak terlihat.** Total dihitung biaya jasa + biaya kunjungan (Rp150.000, sesuai layar 08, 10, 11). Kolom promo di layar 04 tanpa label; diberi placeholder "Kode promo".
3. **Biaya kunjungan Rp25.000** hanya tampak untuk Perbaikan Listrik. Asumsi: flat Rp25.000 untuk semua layanan. Mohon konfirmasi bila berbeda.
4. **Tab Pesanan dan Akun** ada di bottom nav tetapi tidak didesain. Keputusan: Pesanan = daftar aktif dan riwayat; Akun = profil, alamat tersimpan, keluar.
5. **Kode pesanan** TK-271026 sama dengan tanggal 27-10-26 (ddMMyy). Agar unik, format menjadi TK-ddMMyy-NN dengan NN urutan harian; tampilan di desain tetap dipotong sebagai TK-ddMMyy bila NN = 01.
6. **Pembayaran tunai** tidak punya layar tersendiri. Keputusan: pesanan langsung mencari tukang, status bayar "Belum dibayar" sampai tukang menandai tunai diterima.
7. **Pembatalan** muncul di layar 05 saat tukang sudah berangkat. Aturan ada di bagian 4.
8. **Tampilan tukang dan admin** belum ada di desain; ditulis di bagian 6 dan 7.

## 3. Peran pengguna dan validasi login

Satu aplikasi, satu layar login (layar 01). Server menentukan peran akun, lalu aplikasi membuka antarmuka yang sesuai. Pengguna tidak memilih peran sendiri.

| Peran | Cara punya akun | Antarmuka setelah login |
| --- | --- | --- |
| KONSUMEN | Daftar mandiri (tombol Daftar) | 12 layar desain yang ada, bottom nav Beranda / Pesanan / Akun |
| TUKANG | Ajukan lewat "Daftar sebagai tukang" (data diri, keahlian, foto KTP dan selfie), aktif setelah disetujui admin | Antarmuka tukang (bagian 6) |
| ADMIN | Dibuat lewat seed database | Panel admin minimal (bagian 7) |

**Alur validasi login**

1. Pengguna mengisi username dan password, menekan LOGIN.
2. Aplikasi memanggil `POST /auth/login`. Server memeriksa password (hash Argon2 atau BCrypt) dan status akun.
3. Bila berhasil, server mengembalikan access token (JWT, 15 menit), refresh token (14 hari), `role`, dan profil singkat.
4. Aplikasi memilih grafik navigasi dari `role`: KONSUMEN ke ConsumerGraph, TUKANG ke TechnicianGraph, ADMIN ke AdminGraph.
5. Token disimpan terenkripsi di perangkat. Saat aplikasi dibuka lagi, sesi dipulihkan dan peran dibaca ulang dari server.

**Aturan**

- Pemeriksaan peran wajib di server pada setiap endpoint (`/consumer/*`, `/technician/*`, `/admin/*`). Menyembunyikan menu di aplikasi saja tidak cukup.
- Akun TUKANG berstatus MENUNGGU\_VERIFIKASI, DITOLAK, atau DITANGGUHKAN tidak bisa masuk; pesan galat jelas dan memakai tata letak layar 09.
- Satu username hanya punya satu peran. Orang yang ingin menjadi konsumen sekaligus tukang memakai dua akun.
- Galat login memakai pesan umum ("Username atau password salah") agar tidak membocorkan akun mana yang ada. Setelah 5 kali gagal berturut-turut, akun dikunci 5 menit.
- Logout menghapus token dan mengirim refresh token ke daftar cabut.

## 4. Siklus pesanan

Status pesanan adalah tulang punggung sistem: konsumen, tukang, dan admin membaca status yang sama dari server. Perubahan status hanya sah lewat transisi di tabel ini.

| Status | Layar konsumen | Pemicu | Berikutnya |
| --- | --- | --- | --- |
| MENUNGGU\_PEMBAYARAN | 04 | Konsumen menekan "Lanjut ke pembayaran" | MENCARI\_TEKNISI, PEMBAYARAN\_GAGAL, DIBATALKAN |
| PEMBAYARAN\_GAGAL | 10 | Pembayaran ditolak atau kedaluwarsa (30 menit) | MENUNGGU\_PEMBAYARAN, DIBATALKAN |
| MENCARI\_TEKNISI | 11 | Pembayaran sukses, atau metode tunai dipilih | TEKNISI\_DITEMUKAN, DIBATALKAN |
| TEKNISI\_DITEMUKAN | 05 ("Teknisi ditemukan") | Tukang menerima tawaran | DALAM\_PERJALANAN, MENCARI\_TEKNISI (tukang menolak) |
| DALAM\_PERJALANAN | 05 | Tukang menekan "Berangkat" | TIBA |
| TIBA | 05 | Tukang menekan "Saya sudah tiba" | SEDANG\_BEKERJA |
| SEDANG\_BEKERJA | 05 | Tukang menekan "Mulai bekerja" | MENUNGGU\_KONFIRMASI |
| MENUNGGU\_KONFIRMASI | 06, 07 | Tukang unggah bukti foto + catatan, menekan "Selesai" | SELESAI, DILAPORKAN |
| DILAPORKAN | 12 | Konsumen menekan "Belum, lapor masalah" | KUNJUNGAN\_ULANG |
| KUNJUNGAN\_ULANG | 05 | Admin atau sistem menjadwalkan ulang, tukang menerima | kembali ke alur TEKNISI\_DITEMUKAN |
| SELESAI | 08 | Konsumen menekan "Sudah beres", atau otomatis 24 jam setelah MENUNGGU\_KONFIRMASI | (akhir) |
| DIBATALKAN | — | Konsumen membatalkan, atau tidak ada tukang | (akhir) |

**Aturan bisnis**

- **Penugasan.** Server memilih tukang online dengan keahlian sesuai, jarak terdekat dari alamat, dan tanpa jadwal bentrok. Tawaran dikirim ke satu tukang dulu dan kedaluwarsa dalam 60 detik; bila ditolak atau lewat waktu, ditawarkan ke tukang berikutnya (maksimal 5 percobaan).
- **Tidak ada tukang.** Setelah 5 percobaan atau 10 menit, konsumen diberi pilihan "Jadwal ulang" atau batal dengan pengembalian dana penuh.
- **Pembatalan konsumen.** Sebelum DALAM\_PERJALANAN: dana kembali penuh. Sesudahnya sampai TIBA: biaya kunjungan Rp25.000 tidak dikembalikan. Setelah TIBA: tidak bisa dibatalkan, ajukan laporan. Ini asumsi yang bisa disesuaikan.
- **Waktu tukang tiba** dihitung dari jarak garis lurus dibagi kecepatan rata-rata 20 km/jam, ditampilkan sebagai "Tiba sekitar HH.mm" dan "±N menit" seperti layar 05.
- **Kunjungan ulang** tidak menambah biaya bagi konsumen.

## 5. Kebutuhan fungsional: aplikasi konsumen

Prioritas: **M** wajib, **S** sebaiknya ada, **C** bila waktu cukup.

| ID | Kebutuhan | Prioritas |
| --- | --- | --- |
| K-01 | Login username dan password (layar 01), validasi kolom kosong, pesan galat (layar 09 versi username) | M |
| K-02 | Registrasi konsumen: nama, username, password, nomor HP, alamat awal | M |
| K-03 | Beranda: sapaan lokasi dari alamat utama, kolom cari yang menyaring layanan, daftar layanan dan harga "Mulai Rp…" dari server | M |
| K-04 | Kartu pesanan aktif di Beranda (kode, status, nama layanan, jadwal); ketuk membuka layar 05 | M |
| K-05 | Pemesanan: deskripsi masalah wajib, 0–3 foto dari kamera atau galeri, pilih tanggal (tidak boleh lampau), pilih slot yang masih tersedia | M |
| K-06 | Alamat kunjungan: tampil alamat tersimpan, "Ubah" membuka daftar alamat, "Gunakan lokasi saya" mengambil GPS dan geocoding balik, peta menunjukkan pin | M |
| K-07 | Pembayaran: rincian biaya, 3 metode (transfer bank dengan nomor pembayaran otomatis, dompet digital, tunai), kode promo, "Bayar sekarang" | M |
| K-08 | Pembayaran gagal (layar 10): "Coba lagi" dan "Ganti metode pembayaran" kembali ke layar 04 dengan data tetap | M |
| K-09 | Status pesanan: peta dengan posisi tukang yang diperbarui, estimasi tiba, kartu tukang dengan Telepon (intent dial) dan Pesan (chat), linimasa dengan jam tiap langkah | M |
| K-10 | Batalkan pesanan dengan dialog konfirmasi dan alasan, sesuai aturan bagian 4 | M |
| K-11 | Tukang menolak (layar 11): animasi mencari, teks tetap, jadwal dan total tidak berubah, "Jadwal ulang" membuka pemilih tanggal dan slot baru | M |
| K-12 | Pekerjaan selesai (layar 06): foto bukti, jam selesai, catatan tukang, "Konfirmasi hasil pekerjaan" | M |
| K-13 | Konfirmasi (layar 07): "Sudah beres" lanjut ke penilaian; "Belum, lapor masalah" lanjut ke layar 12 | M |
| K-14 | Lapor masalah (layar 12): 3 alasan, keterangan, maksimal 3 foto JPG atau PNG, kirim sebagai permintaan kunjungan ulang | M |
| K-15 | Penilaian (layar 08): bintang 1–5 dengan label (5 = Sangat puas), tag cepat, komentar, kirim; struk ringkas tampil sesudahnya; "Kembali ke Beranda" | M |
| K-16 | Tab Pesanan: daftar aktif dan riwayat, ketuk menampilkan rincian atau struk | M |
| K-17 | Tab Akun: profil, ubah password, kelola alamat, keluar | M |
| K-18 | Notifikasi push untuk setiap perubahan status (FCM) | S |
| K-19 | Chat konsumen-tukang per pesanan | S |
| K-20 | Unduh atau bagikan struk sebagai PDF | C |

## 6. Kebutuhan fungsional: aplikasi tukang

Desain tukang belum ada, jadi bagian ini sekaligus menjadi arahan desainnya. Identitas merek sama dengan sisi konsumen (biru tua, oranye, kartu terang, ikon garis), tetapi tata letaknya berorientasi kerja: satu aksi utama besar menempel di bawah layar, ada bilah status "Online / Offline" di atas, dan label "Mode Tukang" di header agar jelas berbeda dari tampilan konsumen. Bottom nav: **Beranda, Pekerjaan, Pendapatan, Akun**.

**Layar yang dirancang**

| Layar | Isi |
| --- | --- |
| T1 Beranda | Sakelar Online/Offline, ringkasan hari ini (pekerjaan selesai, pendapatan), kartu pekerjaan aktif, daftar jadwal berikutnya |
| T2 Tawaran masuk | Bottom sheet dengan hitung mundur 60 detik: layanan, deskripsi masalah, foto, jarak, jadwal, estimasi pendapatan, tombol Terima dan Tolak (dengan pilihan alasan) |
| T3 Detail pekerjaan | Data konsumen, alamat, peta dengan rute dan tombol "Buka navigasi", Telepon, Pesan, deskripsi dan foto masalah, satu tombol aksi sesuai status |
| T4 Selesaikan pekerjaan | Unggah foto bukti (1–5), catatan pekerjaan, untuk pembayaran tunai: kotak centang "Uang tunai Rp… sudah diterima" |
| T5 Daftar pekerjaan | Tab Tawaran, Terjadwal, Riwayat |
| T6 Pendapatan | Saldo, rincian per pekerjaan (tarif, potongan platform, bersih), tarik saldo (simulasi) |
| T7 Akun | Profil, keahlian, radius layanan, rating dan ulasan yang diterima, ubah password, keluar |

**Kebutuhan**

| ID | Kebutuhan | Prioritas |
| --- | --- | --- |
| T-01 | Login memakai layar yang sama; role TUKANG membuka TechnicianGraph | M |
| T-02 | Pendaftaran tukang: data diri, keahlian (listrik, pipa, AC), foto KTP dan selfie, status "Menunggu verifikasi" | M |
| T-03 | Sakelar Online/Offline; hanya tukang Online yang menerima tawaran. Saat Online, aplikasi mengirim lokasi tiap 15 detik (foreground service dengan notifikasi) | M |
| T-04 | Terima tawaran lewat push atau WebSocket; hitung mundur 60 detik; Terima mengubah status ke TEKNISI\_DITEMUKAN | M |
| T-05 | Tolak tawaran dengan alasan; server meneruskan ke tukang lain dan konsumen melihat layar 11 | M |
| T-06 | Tombol aksi berurutan: Berangkat, Saya sudah tiba, Mulai bekerja, Selesaikan. Tiap ketukan mengubah status dan jamnya tampil di linimasa konsumen | M |
| T-07 | Selama DALAM\_PERJALANAN, lokasi dikirim tiap 5–10 detik dan dipakai konsumen di layar 05 | M |
| T-08 | Selesaikan wajib minimal 1 foto bukti dan catatan; isi tampil di layar 06 konsumen | M |
| T-09 | Pembayaran tunai: tukang menandai uang diterima, status bayar menjadi lunas | M |
| T-10 | Kunjungan ulang muncul sebagai tawaran khusus, diprioritaskan ke tukang yang sama bila tersedia | S |
| T-11 | Pendapatan: tarif jasa dikurangi komisi platform (asumsi 15% dari biaya jasa; biaya kunjungan penuh ke tukang), riwayat, tarik saldo simulasi | M |
| T-12 | Rating rata-rata, jumlah pekerjaan, dan ulasan masuk tampil di Akun | M |
| T-13 | Chat dengan konsumen per pesanan | S |
| T-14 | Notifikasi push tawaran dan perubahan jadwal | S |

Tukang yang ditangguhkan atau tidak Online tidak pernah mendapat tawaran. Tukang tidak melihat nomor HP konsumen sebelum menerima tawaran.

## 7. Panel admin minimal

Admin diperlukan agar sistem benar-benar utuh: tanpa admin, tukang tidak bisa diverifikasi dan laporan masalah (layar 12) tidak ada yang menangani. Panel ini ada di aplikasi yang sama (AdminGraph) dan sengaja dibuat sederhana.

| ID | Kebutuhan | Prioritas |
| --- | --- | --- |
| A-01 | Ringkasan: pesanan hari ini, tukang online, laporan terbuka | S |
| A-02 | Verifikasi tukang: lihat data dan foto, setujui atau tolak dengan alasan | M |
| A-03 | Laporan masalah: lihat keterangan dan foto, jadwalkan kunjungan ulang atau tutup | M |
| A-04 | Daftar semua pesanan dengan filter status; tombol "Simulasikan pembayaran berhasil" untuk demo transfer dan dompet digital | M |
| A-05 | Kelola layanan: ubah nama, harga mulai, biaya kunjungan, aktif atau nonaktif | S |
| A-06 | Tangguhkan atau aktifkan kembali akun tukang atau konsumen | S |

## 8. Arsitektur dan tech stack

Semuanya Kotlin: klien Android native, server Ktor, dan satu modul `shared` berisi DTO yang dipakai keduanya sehingga kontrak API tidak bisa melenceng. Versi pustaka dikunci di version catalog pada versi stabil terbaru saat proyek dimulai.

| Lapisan | Pilihan | Alasan |
| --- | --- | --- |
| Android UI | Jetpack Compose, Material 3, Navigation Compose | Native, cocok dengan desain kartu dan bottom nav |
| Arsitektur klien | MVVM + repository, UiState lewat StateFlow, Hilt | Mudah diuji dan dijelaskan di sidang |
| Jaringan | Retrofit + OkHttp + kotlinx.serialization, WebSocket OkHttp | Satu kontrak dengan server |
| Data lokal | Room (cache pesanan), DataStore (token terenkripsi, Tink) | Tetap bisa dibuka saat sinyal buruk |
| Peta dan lokasi | MapLibre atau osmdroid dengan tile OpenStreetMap, FusedLocationProvider | Tanpa kunci API berbayar |
| Media | Photo Picker, CameraX, Coil | Foto masalah dan bukti kerja |
| Tugas latar | Foreground service (lokasi tukang), WorkManager (unggah ulang) | Pelacakan tetap jalan |
| Notifikasi | Firebase Cloud Messaging (opsional, S) | Tanpa FCM, aplikasi jatuh ke WebSocket dan polling 10 detik |
| Backend | Ktor (Netty), Exposed, Flyway, kotlinx.serialization | Ringan dan Kotlin murni |
| Database | PostgreSQL (Docker Compose); H2 untuk pengujian | Relasional, cocok untuk pesanan dan pembayaran |
| Keamanan | JWT + refresh token, hash BCrypt atau Argon2, RBAC per rute, validasi input, batas laju | Validasi peran di server |
| Realtime | WebSocket `/ws` untuk status, tawaran, posisi tukang, chat | Linimasa layar 05 hidup |
| Penyimpanan foto | Folder di server, nama acak, batas 5 MB, hanya JPG dan PNG | Cukup untuk tugas akhir |
| Pembayaran | Antarmuka `PaymentProvider`; implementasi `SimulatorProvider` menerbitkan nomor pembayaran acak dan dikonfirmasi admin atau tombol simulasi; tunai ditangani tukang | Berfungsi penuh tanpa kontrak payment gateway |
| Deploy | Docker Compose lokal; opsional satu VPS atau layanan PaaS | Demo sidang tidak bergantung internet kampus |

**Struktur repositori**

```
tukangkilat/
  shared/       DTO, enum status, aturan validasi (Kotlin JVM)
  backend/      Ktor: routes, services, repositories, migrations
  app/          Android: ui/consumer, ui/technician, ui/admin, data, di
  docker-compose.yml
  docs/         PRD, diagram, koleksi Postman
```

## 9. Model data dan API

**Tabel utama**

| Tabel | Kolom penting |
| --- | --- |
| users | id, username (unik), password\_hash, role, full\_name, phone, status, created\_at |
| technician\_profiles | user\_id, skills, ktp\_photo, selfie\_photo, verification\_status, is\_online, last\_lat, last\_lng, rating\_avg, jobs\_count, service\_radius\_km |
| addresses | id, user\_id, label, full\_address, lat, lng, is\_primary |
| services | id, name, icon, base\_price, visit\_fee, is\_active |
| time\_slots | id, label (09.00, 10.00–11.00, 13.00), capacity |
| orders | id, code (TK-ddMMyy-NN), consumer\_id, technician\_id, service\_id, description, address\_id, scheduled\_date, slot\_id, status, service\_fee, visit\_fee, discount, total, payment\_method, payment\_status |
| order\_photos | id, order\_id, kind (MASALAH, BUKTI, LAPORAN), url |
| order\_events | id, order\_id, status, actor\_id, created\_at (sumber linimasa layar 05) |
| job\_offers | id, order\_id, technician\_id, status (MENUNGGU, DITERIMA, DITOLAK, KEDALUWARSA), expires\_at, reject\_reason |
| payments | id, order\_id, method, amount, reference\_no, status, paid\_at |
| promo\_codes | code, type, value, valid\_until, quota |
| ratings | order\_id, stars, tags, comment |
| issue\_reports | id, order\_id, reason, description, status, created\_at |
| messages | id, order\_id, sender\_id, body, created\_at |
| refresh\_tokens | id, user\_id, token\_hash, expires\_at, revoked |

**Endpoint**

| Endpoint | Peran | Fungsi |
| --- | --- | --- |
| POST /auth/register | publik | Daftar konsumen |
| POST /auth/register-technician | publik | Ajukan akun tukang |
| POST /auth/login | publik | Masuk, mengembalikan token + role |
| POST /auth/refresh, /auth/logout | semua | Perbarui sesi, keluar |
| GET /services, GET /slots?date= | konsumen | Layanan dan slot tersedia |
| GET/POST /addresses | konsumen | Alamat tersimpan |
| POST /orders | konsumen | Buat pesanan (status MENUNGGU\_PEMBAYARAN) |
| GET /orders, GET /orders/{id} | konsumen | Daftar dan rincian |
| POST /orders/{id}/pay | konsumen | Mulai pembayaran, kembalikan nomor pembayaran |
| POST /orders/{id}/cancel, /reschedule | konsumen | Batal, jadwal ulang |
| POST /orders/{id}/confirm, /rating, /report | konsumen | Konfirmasi, nilai, lapor masalah |
| POST /photos | konsumen, tukang | Unggah foto, kembalikan URL |
| GET /orders/{id}/messages, POST | konsumen, tukang | Chat |
| PUT /technician/online | tukang | Sakelar Online/Offline |
| POST /technician/location | tukang | Kirim posisi |
| GET /technician/offers, POST /offers/{id}/accept, /reject | tukang | Tawaran |
| POST /technician/orders/{id}/depart, /arrive, /start, /finish | tukang | Aksi pekerjaan |
| GET /technician/earnings | tukang | Pendapatan |
| GET/POST /admin/technicians/{id}/approve, /reject | admin | Verifikasi tukang |
| GET /admin/reports, POST /admin/reports/{id}/revisit, /close | admin | Laporan masalah |
| POST /admin/payments/{orderId}/simulate-success | admin | Simulasi pembayaran berhasil |
| WS /ws | semua | Peristiwa status, tawaran, posisi, chat |

Semua endpoint selain `/auth/*` publik memerlukan JWT dan diperiksa perannya di server. Respons galat memakai bentuk tunggal `{ code, message }` agar klien dapat menampilkan pesan di layar 09, 10, dan 11 dengan seragam.

## 10. Non-fungsional, pengujian, dan kriteria selesai

**Non-fungsional**

- Kinerja: respons API biasa di bawah 500 ms pada laptop demo; layar Beranda tampil dari cache Room sambil memuat data baru.
- Keandalan: perubahan status memakai transaksi database; transisi di luar tabel bagian 4 ditolak dengan galat 409.
- Keamanan: tidak ada rahasia di repositori (gunakan berkas lingkungan), password di-hash, unggahan divalidasi tipe dan ukuran, rute admin dan tukang tertutup untuk konsumen.
- Kegunaan: semua teks Bahasa Indonesia, tombol minimal 48 dp, kontras sesuai palet desain, keadaan kosong, memuat, dan galat tersedia di setiap layar.
- Perangkat: Android 8.0 (API 26) ke atas.

**Pengujian**

| Jenis | Cakupan minimal |
| --- | --- |
| Unit (backend) | Mesin status pesanan, hitung biaya dan promo, pemilihan tukang, validasi peran |
| Integrasi (backend) | Alur lengkap: daftar, login, pesan, bayar, terima, selesaikan, nilai, dengan H2 atau Testcontainers |
| Unit (Android) | ViewModel login (peran menentukan tujuan), pemesanan, pembayaran |
| UI (Compose) | Login ke tiga peran, alur pesan konsumen, aksi tukang |
| Manual (UAT) | Skenario bagian di bawah dijalankan dengan dua ponsel atau emulator |

**Skenario uji utama**

1. Konsumen memesan, bayar transfer, tukang menerima, berangkat, tiba, bekerja, selesai, konsumen konfirmasi dan menilai.
2. Tukang menolak: konsumen melihat layar 11, pengganti menerima, kembali ke layar 05.
3. Pembayaran gagal: layar 10, coba lagi dengan metode lain, berhasil.
4. Konsumen menekan "Belum, lapor masalah": admin menjadwalkan kunjungan ulang, tukang menerima.
5. Login dengan akun tukang belum diverifikasi: ditolak; setelah admin setuju: masuk ke antarmuka tukang.
6. Konsumen mencoba memanggil endpoint tukang atau admin: server menjawab 403.

**Roadmap**

| Fase | Hasil | Perkiraan |
| --- | --- | --- |
| 1 Fondasi | Repositori, database, autentikasi, validasi peran, kerangka navigasi tiga peran | Minggu 1–2 |
| 2 Alur konsumen | Layar 01–08 dan layar kegagalan 09–12 terhubung ke server | Minggu 3–4 |
| 3 Alur tukang | Tawaran, aksi pekerjaan, lokasi langsung, bukti kerja, pendapatan | Minggu 5–6 |
| 4 Admin dan pembayaran | Verifikasi, laporan, simulator pembayaran, promo | Minggu 7 |
| 5 Penyempurnaan | Chat, notifikasi, pengujian, dokumentasi, rekaman demo | Minggu 8 |

**Kriteria selesai**

- [ ] Ketiga peran masuk lewat layar login yang sama dan mendarat di antarmuka yang benar
- [ ] Enam skenario uji utama lulus di dua perangkat
- [ ] Seluruh 12 layar desain konsumen sesuai desain dan terhubung ke data nyata
- [ ] Posisi tukang di layar 05 berasal dari ponsel tukang, bukan data palsu
- [ ] Tidak ada data hardcode di layar; seed hanya untuk akun demo dan layanan
- [ ] Dokumen skripsi memuat diagram ERD, alur status, dan hasil pengujian
