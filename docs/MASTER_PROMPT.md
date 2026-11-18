# Master Prompt Tukang Kilat

Nov 18, 2026 · @Nico

Master prompt ini dipakai bersama PRD Tukang Kilat untuk meminta agen AI pengkode (Claude Code atau sejenisnya) membangun sistemnya bertahap. Jalankan Prompt 0 sekali di awal sesi, lalu Prompt 1 sampai 5 berurutan; mulai prompt berikutnya hanya setelah yang sebelumnya lulus build dan tes.

## Persiapan

Sebelum Prompt 0, simpan PRD sebagai `docs/PRD.md` dan 12 gambar desain di `docs/design/` pada repositori kosong, supaya agen bisa membacanya. Tiap prompt di bawah ditulis untuk disalin utuh.

## Prompt 0: konteks dan aturan kerja

```markdown
KAMU: senior Kotlin engineer yang membangun sistem utuh bernama "Tukang Kilat" untuk tugas akhir kuliah. Hasilnya harus benar-benar berjalan, bukan mock.

SUMBER KEBENARAN
- docs/PRD.md adalah spesifikasi. Baca seluruhnya sebelum menulis kode.
- docs/design/*.jpg adalah 12 layar desain konsumen. Tata letak, teks, dan alur layar konsumen harus mengikutinya. Keputusan untuk celah desain sudah dicatat di PRD bagian 2; ikuti keputusan itu.
- Bila PRD dan pikiranmu bertentangan, PRD menang. Bila PRD diam, pilih default paling sederhana yang aman, lalu catat di docs/OPEN_QUESTIONS.md. Bertanya ke saya hanya bila benar-benar terhenti.

SISTEM YANG DIBANGUN
- Satu aplikasi Android native (Kotlin, Jetpack Compose, Material 3) dengan TIGA antarmuka: KONSUMEN, TUKANG, ADMIN. Satu layar login. Server menentukan peran akun, aplikasi memilih grafik navigasi dari peran tersebut.
- Backend Kotlin: Ktor + Exposed + Flyway + PostgreSQL, JWT, WebSocket.
- Modul shared (Kotlin JVM) berisi DTO, enum, dan mesin status pesanan yang dipakai backend dan Android.
- Monorepo Gradle Kotlin DSL dengan version catalog: shared/, backend/, app/, docker-compose.yml, docs/.

ATURAN KERJA (tidak boleh dilanggar)
1. Tidak ada data palsu yang ditulis langsung di layar. Semua data dari API. Seed hanya untuk akun demo, layanan, dan slot.
2. Pemeriksaan peran dilakukan di server pada setiap rute. Menyembunyikan menu di aplikasi bukan pengamanan.
3. Perubahan status pesanan hanya lewat satu service yang memakai tabel transisi PRD bagian 4, dalam transaksi database, dan menulis baris order_events.
4. Setiap fitur dikirim bersama tes. Jalankan build dan tes sebelum menyatakan selesai; jika merah, perbaiki dulu, jangan lanjut.
5. Jangan menambah fitur di luar PRD. Jangan menghapus fitur PRD tanpa mencatat alasannya.
6. Teks UI dalam Bahasa Indonesia; nama kode, tabel, dan komentar dalam Bahasa Inggris.
7. Rahasia (kunci JWT, kredensial database) hanya dari variabel lingkungan; sediakan .env.example. Jangan commit rahasia.
8. Setiap layar punya tiga keadaan: memuat, kosong, galat (dengan tombol coba lagi).
9. Commit kecil dan sering dengan pesan jelas.

TOKEN DESAIN (perkiraan dari gambar; samakan dengan desain)
- Latar biru tua sekitar #1B2F4D, aksen oranye sekitar #E8681E, kartu terang, sukses hijau, galat merah, tombol penuh berujung membulat, ikon garis, font sans-serif bersih.
- Simpan sebagai tema Compose tunggal; jangan menulis warna langsung di layar.

FORMAT LAPORAN di akhir setiap tugas
- Apa yang selesai, cara menjalankan (perintah persis), hasil tes, apa yang belum, dan keputusan yang kamu ambil.

Balas "SIAP" lalu ringkas pemahamanmu dalam 10 baris: sistem, tiga peran, alur status pesanan, dan urutan fase. Jangan menulis kode dulu.
```

## Prompt 1: fondasi, autentikasi, validasi peran

```markdown
FASE 1 dari 5: FONDASI. Acuan: PRD bagian 3, 8, 9.

Kerjakan berurutan:

1. Buat monorepo Gradle (Kotlin DSL, version catalog) dengan modul shared, backend, app. Kunci versi stabil terbaru. Tambahkan docker-compose.yml untuk PostgreSQL dan .env.example.

2. Backend:
   - Konfigurasi Ktor (content negotiation kotlinx.serialization, status pages dengan galat seragam { code, message }, CORS tertutup, logging).
   - Flyway V1 yang membuat SEMUA tabel di PRD bagian 9 dengan kunci asing dan indeks.
   - Auth: POST /auth/register (konsumen), /auth/register-technician (status MENUNGGU_VERIFIKASI), /auth/login, /auth/refresh, /auth/logout.
   - Password di-hash BCrypt atau Argon2. Access token JWT 15 menit dengan klaim userId dan role. Refresh token 14 hari, disimpan sebagai hash, bisa dicabut.
   - Login memberi pesan umum "Username atau password salah" untuk semua kegagalan kredensial. Setelah 5 kali gagal beruntun, kunci akun 5 menit. Akun tukang yang MENUNGGU_VERIFIKASI, DITOLAK, atau DITANGGUHKAN ditolak dengan kode galat berbeda dan pesan jelas.
   - Pasang pembantu RBAC, misalnya authorize(Role.KONSUMEN), dan terapkan pada grup rute /consumer, /technician, /admin (atau awalan yang kamu pilih, selama sesuai PRD).
   - Seed (hanya bila tabel kosong): 1 admin; 1 konsumen demo (username rina); 2 tukang demo terverifikasi (username budi dan satu lagi); layanan Perbaikan Listrik Rp125.000, Pipa Bocor Rp100.000, Servis AC Rp85.000 dengan biaya kunjungan Rp25.000; slot 09.00, 10.00–11.00, 13.00. Password demo dibaca dari env, bukan ditulis di kode.

3. shared: DTO auth, enum Role dan OrderStatus (nilai sesuai PRD bagian 4), dan fungsi murni canTransition(from, to, actor). Tulis tes unit untuk setiap transisi sah dan beberapa yang tidak sah.

4. Android (app):
   - Hilt, Navigation Compose, tema Compose dari token desain, Retrofit + kotlinx.serialization, DataStore untuk token (terenkripsi).
   - LoginScreen mengikuti layar 01. Kegagalan login mengikuti tata letak layar 09 tetapi dengan teks "Username atau password salah" dan kolom username. Tautan "Daftar" membuka registrasi konsumen; sediakan juga jalur "Daftar sebagai tukang".
   - SessionManager menyimpan token dan peran, memulihkan sesi saat aplikasi dibuka, menyegarkan token otomatis, dan memaksa logout bila peran dari server tidak cocok.
   - Router berbasis peran: KONSUMEN ke ConsumerGraph, TUKANG ke TechnicianGraph, ADMIN ke AdminGraph. Untuk fase ini tiap graph cukup menampilkan satu layar berisi nama peran dan tombol keluar.

5. Tes yang wajib lulus:
   - Login berhasil untuk tiga peran dan mengembalikan role yang benar.
   - Konsumen memanggil rute tukang dan admin: 403. Tukang memanggil rute admin: 403. Tanpa token: 401.
   - Tukang MENUNGGU_VERIFIKASI tidak bisa login.
   - 5 kali gagal mengunci akun.
   - ViewModel login: peran menentukan tujuan navigasi.

Kriteria selesai: docker compose up, backend jalan, aplikasi di emulator bisa login dengan tiga akun demo dan mendarat di graph yang benar. Berikan laporan sesuai format.
```

## Prompt 2: backend pesanan, tawaran, pembayaran, realtime

```markdown
FASE 2 dari 5: INTI BACKEND. Acuan: PRD bagian 4, 6, 9.

1. Mesin status. Buat OrderService sebagai satu-satunya pintu perubahan status. Setiap perubahan: cek canTransition dari modul shared, jalankan dalam transaksi, tulis order_events, lalu siarkan peristiwa lewat WebSocket. Transisi tidak sah mengembalikan 409.

2. Pemesanan konsumen.
   - GET /services, GET /slots?date= (slot penuh atau lampau tidak ditawarkan), CRUD /addresses.
   - POST /orders: validasi deskripsi wajib, tanggal tidak lampau, slot valid, alamat milik konsumen. Hitung service_fee dan visit_fee dari tabel services, terapkan promo_codes bila ada, simpan total. Kode pesanan TK-ddMMyy-NN dengan NN urutan harian (dibuat atomik, tidak boleh bentrok). Status awal MENUNGGU_PEMBAYARAN.
   - GET /orders dan GET /orders/{id} hanya untuk pemilik. Sertakan linimasa dari order_events, data tukang (nama, rating, jumlah pekerjaan, foto), dan posisi terakhir tukang.

3. Pembayaran.
   - Antarmuka PaymentProvider dengan implementasi SimulatorProvider. Transfer bank dan dompet digital: buat payments PENDING dengan nomor pembayaran acak (misal awalan BCA- untuk transfer) dan kedaluwarsa 30 menit.
   - Endpoint admin POST /admin/payments/{orderId}/simulate-success dan simulate-fail. Berhasil: status MENCARI_TEKNISI dan payment_status LUNAS. Gagal atau kedaluwarsa: PEMBAYARAN_GAGAL.
   - Tunai: langsung MENCARI_TEKNISI, payment_status BELUM_DIBAYAR sampai tukang menandai tunai diterima.
   - Coba lagi atau ganti metode dari PEMBAYARAN_GAGAL kembali ke MENUNGGU_PEMBAYARAN dengan pesanan yang sama.

4. Dispatcher tukang (coroutine terjadwal).
   - Pada MENCARI_TEKNISI pilih tukang: terverifikasi, online, punya keahlian layanan, dalam service_radius_km, tidak bentrok jadwal; urutkan jarak (Haversine) lalu rating.
   - Buat job_offers MENUNGGU dengan expires_at 60 detik. Terima: status TEKNISI_DITEMUKAN. Tolak atau lewat waktu: tawarkan ke tukang berikutnya, maksimal 5 percobaan, tidak mengulang tukang yang sama.
   - Bila tak ada tukang setelah 5 percobaan atau 10 menit, kirim peristiwa TIDAK_ADA_TEKNISI; konsumen boleh jadwal ulang (POST /orders/{id}/reschedule) atau batal dengan dana kembali penuh.
   - Saat tukang menolak, konsumen tetap melihat status MENCARI_TEKNISI (layar 11); jadwal dan total tidak berubah.

5. Aksi tukang: PUT /technician/online, POST /technician/location, GET /technician/offers, POST /offers/{id}/accept dan /reject (dengan alasan), POST /technician/orders/{id}/depart, /arrive, /start, /finish. /finish wajib minimal 1 foto BUKTI dan catatan; untuk pesanan tunai wajib flag cashReceived. Tukang tidak boleh mengakses pesanan yang bukan miliknya.

6. Setelah selesai: POST /orders/{id}/confirm (SELESAI), otomatis SELESAI 24 jam setelah MENUNGGU_KONFIRMASI bila tak ada respon; POST /orders/{id}/report (alasan salah satu dari tiga di layar 12, keterangan, maksimal 3 foto) membuat issue_reports dan status DILAPORKAN; admin POST /admin/reports/{id}/revisit mengubah menjadi KUNJUNGAN_ULANG dan membuat tawaran baru (prioritas tukang yang sama). POST /orders/{id}/rating hanya sekali per pesanan, memperbarui rating_avg dan jobs_count tukang.

7. Pembatalan: POST /orders/{id}/cancel dengan aturan PRD bagian 4 (sebelum DALAM_PERJALANAN dana penuh; sesudahnya biaya kunjungan tidak kembali; setelah TIBA ditolak).

8. ETA: hitung dari jarak garis lurus tukang ke alamat dengan kecepatan 20 km/jam; kembalikan jarak (km), menit, dan jam tiba untuk layar 05.

9. Foto: POST /photos menerima multipart JPG atau PNG maksimal 5 MB, periksa tipe sebenarnya (bukan hanya ekstensi), simpan dengan nama acak, kembalikan URL. Sajikan lewat rute yang memeriksa hak akses pesanan.

10. Realtime: WS /ws memakai JWT. Peristiwa: ORDER_STATUS, OFFER_NEW, OFFER_EXPIRED, TECHNICIAN_LOCATION, CHAT_MESSAGE. Pastikan pengguna hanya menerima peristiwa pesanannya sendiri. Sediakan endpoint GET sebagai cadangan polling.

11. Chat: GET dan POST /orders/{id}/messages untuk konsumen dan tukang pesanan tersebut.

12. Pendapatan: GET /technician/earnings: per pesanan selesai, biaya jasa dikurangi komisi 15%, ditambah biaya kunjungan penuh; total dan riwayat.

Tes wajib:
- Tes unit: hitung biaya, promo, pemilihan tukang, pembatalan, ETA.
- Tes integrasi alur penuh (daftar, login, pesan, bayar via simulasi, tawaran diterima, depart, arrive, start, finish, confirm, rating) dan alur tolak, bayar gagal, lapor masalah lalu kunjungan ulang.
- Tes kebolehan akses: konsumen A tidak bisa membaca pesanan konsumen B; tukang tak bisa menyelesaikan pesanan orang lain.

Kriteria selesai: seluruh alur bisa dijalankan dengan Postman atau tes integrasi tanpa aplikasi Android. Sertakan koleksi Postman di docs/. Berikan laporan sesuai format.
```

## Prompt 3: aplikasi Android sisi konsumen

```markdown
FASE 3 dari 5: ANTARMUKA KONSUMEN. Acuan: PRD bagian 2 dan 5, gambar docs/design/01 sampai 12.

Bangun ConsumerGraph yang cocok dengan desain. Buka setiap gambar desain dan samakan susunan, teks, urutan elemen, dan perilaku tombol. Semua data dari API fase 2.

Struktur: ui/consumer/{home, order, payment, status, completion, rating, report, orders, account}, tiap fitur punya ViewModel dengan UiState (StateFlow), repository, dan model domain terpisah dari DTO. Cache pesanan di Room agar Beranda dan tab Pesanan tampil dulu dari cache.

Layar dan perilaku:

1. Beranda (02): lokasi dari alamat utama; kolom cari menyaring layanan; daftar layanan dan harga "Mulai Rp..." dari GET /services; kartu pesanan aktif (kode, status, layanan, jadwal) bila ada, ketuk membuka Status pesanan; bottom nav Beranda, Pesanan, Akun.

2. Pemesanan (03): kartu layanan terpilih, kolom "Ceritakan masalahnya" (wajib), tambah foto opsional (Photo Picker atau CameraX, maksimal 3, unggah lewat /photos), pemilih tanggal, chip slot dari GET /slots (slot penuh dinonaktifkan), alamat kunjungan dengan "Ubah", peta, dan "Gunakan lokasi saya" (izin lokasi runtime, FusedLocation, geocoding balik, tangani izin ditolak). Tombol "Lanjut ke pembayaran" memanggil POST /orders.

3. Pembayaran (04): ringkasan dari data pesanan, biaya jasa, biaya kunjungan, total, tiga metode, kolom kode promo (validasi ke server), "Bayar sekarang" memanggil POST /orders/{id}/pay. Transfer bank dan dompet digital menampilkan nomor pembayaran beserta hitung mundur kedaluwarsa dan memantau status lewat WebSocket. Tunai langsung lanjut. Pembayaran gagal atau kedaluwarsa menampilkan layar 10 dengan "Coba lagi" dan "Ganti metode pembayaran" yang kembali ke layar 04 dengan data tetap.

4. Mencari teknisi (11): tampil saat MENCARI_TEKNISI dan setelah tukang menolak; animasi cincin, kartu pesanan, catatan "Jadwal dan total tidak berubah", tombol "Jadwal ulang" membuka pemilih tanggal dan slot lalu memanggil /reschedule. Saat TEKNISI_DITEMUKAN otomatis pindah ke layar 05.

5. Status pesanan (05): peta (MapLibre atau osmdroid) dengan marker rumah dan marker tukang yang bergerak dari peristiwa TECHNICIAN_LOCATION; teks "Tiba sekitar HH.mm" dan "Teknisi berjarak X km · ±N menit" dari ETA server; kartu tukang dengan Telepon (intent ACTION_DIAL) dan Pesan (layar chat); linimasa Teknisi ditemukan, Dalam perjalanan, Tiba, Sedang bekerja dengan jam dari order_events; "Batalkan pesanan" dengan dialog konfirmasi dan alasan, tersembunyi setelah TIBA.

6. Pekerjaan selesai (06): terbuka otomatis saat MENUNGGU_KONFIRMASI; foto bukti (Coil, dapat diperbesar), jam selesai, catatan tukang, tombol "Konfirmasi hasil pekerjaan".

7. Konfirmasi hasil (07): "Sudah beres" memanggil /confirm lalu ke Penilaian; "Belum, lapor masalah" ke Lapor masalah.

8. Lapor masalah (12): pilihan tunggal "Masalah belum teratasi", "Masalah muncul kembali", "Hasil pekerjaan kurang rapi"; keterangan wajib; unggah foto maksimal 3 (JPG atau PNG); "Minta kunjungan ulang" memanggil /report, lalu kembali ke Status pesanan.

9. Penilaian (08): bintang 1 sampai 5 dengan label (5 = "Sangat puas"), tag cepat (Tepat waktu, Ramah, Rapi), komentar, "Kirim penilaian"; setelah terkirim tampil struk ringkas (kode, tanggal, nama, layanan, alamat, biaya jasa, biaya kunjungan, metode, total) dan "Kembali ke Beranda".

10. Tab Pesanan: daftar Aktif dan Riwayat; ketuk membuka layar sesuai status atau struk.

11. Tab Akun: profil, ubah password, kelola alamat (tambah, ubah, jadikan utama), keluar.

12. Navigasi mengikuti status: bila aplikasi dibuka kembali saat ada pesanan aktif, arahkan ke layar yang sesuai statusnya. Gunakan WebSocket untuk pembaruan dan polling 10 detik sebagai cadangan.

Tes: ViewModel pemesanan (validasi), pembayaran (gagal lalu coba lagi), status (linimasa dari peristiwa); tes UI Compose untuk alur Login, Beranda, Pemesanan, Pembayaran. Jalankan skenario PRD nomor 1, 2, 3, dan 4 secara manual dengan backend asli dan Postman/akun tukang demo sebagai pengganti aplikasi tukang.

Kriteria selesai: seluruh 12 layar tampil sesuai desain dengan data nyata dan skenario 1 sampai 4 berjalan dari sisi konsumen. Berikan laporan sesuai format.
```

## Prompt 4: antarmuka tukang dan admin

```markdown
FASE 4 dari 5: ANTARMUKA TUKANG DAN ADMIN. Acuan: PRD bagian 3, 6, 7.

Desain tukang dan admin belum ada. Rancang sendiri dengan token desain dan komponen yang sama dengan sisi konsumen (kartu, tombol oranye, ikon garis, latar biru tua) tetapi dengan tata letak berorientasi kerja. Bedakan jelas dari tampilan konsumen: label "Mode Tukang" di header, satu tombol aksi utama besar yang menempel di bawah layar, dan bilah status Online/Offline di atas. Bottom nav tukang: Beranda, Pekerjaan, Pendapatan, Akun.

A. TechnicianGraph (ui/technician)
1. Beranda T1: sakelar Online/Offline (PUT /technician/online); saat Online, jalankan foreground service dengan notifikasi tetap yang mengirim POST /technician/location tiap 15 detik (5 sampai 10 detik saat ada pekerjaan DALAM_PERJALANAN). Tangani izin lokasi (termasuk latar belakang) dan Doze dengan wajar. Tampil ringkasan hari ini, kartu pekerjaan aktif, jadwal berikutnya.
2. Tawaran T2: bottom sheet saat peristiwa OFFER_NEW (WebSocket, cadangan polling GET /technician/offers) dengan hitung mundur 60 detik; layanan, deskripsi, foto, jarak, jadwal, estimasi pendapatan; Terima, atau Tolak dengan pilihan alasan. Nomor HP konsumen tidak ditampilkan sebelum diterima.
3. Detail pekerjaan T3: data konsumen, alamat, peta dengan rute dan tombol "Buka navigasi" (intent peta eksternal), Telepon, Pesan (chat), deskripsi dan foto masalah. Satu tombol aksi yang berubah sesuai status: Berangkat, Saya sudah tiba, Mulai bekerja, Selesaikan pekerjaan. Setiap ketukan memanggil endpoint yang sesuai dan menangani galat 409.
4. Selesaikan T4: unggah 1 sampai 5 foto bukti, catatan wajib; untuk pesanan tunai tampil kotak centang "Uang tunai Rp... sudah diterima" yang wajib dicentang.
5. Pekerjaan T5: tab Tawaran, Terjadwal, Riwayat.
6. Pendapatan T6: saldo, rincian per pekerjaan (tarif, komisi, bersih), tombol tarik saldo yang mencatat permintaan (simulasi, tanpa transfer sungguhan).
7. Akun T7: profil, keahlian, radius layanan, rating rata-rata dan ulasan yang diterima, ubah password, keluar.
8. Pendaftaran tukang: formulir data diri, keahlian, foto KTP dan selfie; setelah kirim tampil layar "Menunggu verifikasi admin".
9. Chat per pesanan memakai layar dan komponen yang sama dengan konsumen.

B. AdminGraph (ui/admin), sederhana dan fungsional
1. Ringkasan: pesanan hari ini, tukang online, laporan terbuka.
2. Verifikasi tukang: daftar menunggu, detail dengan foto, Setujui atau Tolak (alasan).
3. Laporan masalah: daftar, detail dengan foto, "Jadwalkan kunjungan ulang" atau "Tutup".
4. Semua pesanan: filter status, tombol "Simulasikan pembayaran berhasil" dan "Simulasikan gagal" untuk pembayaran PENDING.
5. Layanan: ubah nama, harga mulai, biaya kunjungan, aktif atau nonaktif. Akun: tangguhkan atau aktifkan.

C. Keamanan antarmuka
- ConsumerGraph, TechnicianGraph, AdminGraph tidak boleh bisa dinavigasi satu sama lain. Bila server mengembalikan 403 pada rute peran, paksa logout dan tampilkan pesan.

Tes: ViewModel tawaran (hitung mundur dan kedaluwarsa), aksi pekerjaan (urutan status yang benar), selesaikan (validasi foto dan catatan); tes UI untuk login tukang dan alur terima sampai selesai.

Uji menyeluruh di dua emulator atau perangkat: satu konsumen, satu tukang, dan admin lewat sesi ketiga atau Postman. Jalankan PRD skenario 1 sampai 6.

Kriteria selesai: skenario 1 sampai 6 lulus end-to-end, posisi tukang di layar 05 berasal dari perangkat tukang sungguhan atau emulator dengan lokasi simulasi. Berikan laporan sesuai format.
```

## Prompt 5: pengujian, perapian, serah terima

```markdown
FASE 5 dari 5: PENYEMPURNAAN. Acuan: PRD bagian 10 dan seluruh kriteria selesai.

1. Audit kepatuhan PRD: buat docs/TRACEABILITY.md berupa tabel ID kebutuhan (K-xx, T-xx, A-xx) ke berkas kode dan tes yang memenuhinya. Kebutuhan yang belum terpenuhi ditandai jelas dan dikerjakan sekarang bila prioritasnya M.
2. Audit desain: bandingkan tiap layar konsumen dengan gambar desain dan perbaiki selisih tata letak, teks, dan warna. Tuliskan temuan di docs/DESIGN_AUDIT.md.
3. Audit keamanan: periksa IDOR (akses pesanan orang lain), rute tanpa RBAC, unggahan, token (kedaluwarsa, pencabutan), log yang bocor, dan batas laju. Perbaiki dan tambahkan tes regresi.
4. Ketahanan: aplikasi tidak crash saat offline, token kedaluwarsa, atau server mati; tampilkan galat dengan tombol coba lagi. WebSocket menyambung ulang otomatis.
5. Chat dan notifikasi: pastikan chat berjalan dua arah. Bila FCM dipakai, tambahkan; bila tidak, pastikan notifikasi lokal muncul untuk tawaran dan perubahan status saat aplikasi di latar belakang.
6. Jalankan seluruh tes backend dan Android, perbaiki yang gagal, dan hasilkan laporan tes ringkas di docs/TEST_REPORT.md.
7. Dokumentasi: README (cara menjalankan dari nol, akun demo, perintah), diagram ERD dan diagram status pesanan (Mermaid) di docs/, koleksi Postman, dan skrip demo berisi langkah skenario 1 sampai 6.
8. Build APK debug dan APK rilis ditandatangani (keystore lokal, tidak di-commit). Pastikan satu APK membuka tiga peran sesuai akun.
9. Laporan akhir: daftar fitur selesai, daftar yang ditunda beserta alasan, pengetahuan yang perlu dijelaskan saat sidang (arsitektur, validasi peran, mesin status, penugasan tukang, simulasi pembayaran), dan keterbatasan yang jujur.

Kriteria selesai: semua kotak di PRD bagian 10 "Kriteria selesai" dapat dicentang dengan bukti.
```

**Catatan untuk Niko**

- Jalankan satu prompt per sesi agar konteks tidak membengkak; tiap sesi mulai dengan Prompt 0 singkat atau minta agen membaca docs/PRD.md dan laporan fase sebelumnya.
- Periksa sendiri hasil tiap fase di emulator sebelum lanjut. Agen bisa melaporkan "selesai" padahal ada layar yang belum tersambung.
- Pembayaran memakai simulator. Bila dosen meminta gateway nyata, ganti implementasi `PaymentProvider` dengan sandbox penyedia pilihan; sisanya tidak berubah.
