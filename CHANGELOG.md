# Changelog

Semua perubahan penting proyek ini didokumentasikan di file ini.

Format mengikuti [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), dan proyek ini mematuhi [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [4.3.0] - 2026-11-27

### Ditambahkan

- **Deep link**: `tukangkilat://order/{id}`, `tukangkilat://chat/{peer}`, dan `tukangkilat://promo/{code}` membuka layar yang sesuai langsung.
- **Endpoint info server**: status, mode, versi, dan jumlah migrasi tampil di Pengaturan.
- **FAQ dari server**: Pengaturan bisa memuat halaman FAQ yang bisa diubah dari backend.
- **Konsolidasi akhir**: seluruh gelombang v4 untuk katalog, perdagangan, teknisi, platform, dan kualitas kini dirilis.

## [4.2.0] - 2026-11-27

### Ditambahkan

- **Ringkasan platform**: pengguna, pesanan, pendapatan, sengketa terbuka, pelanggaran SLA, dan ulasan dilaporkan dalam satu layar.
- **Feature flag**: sakelar per peran yang bisa dibaca aplikasi lewat endpoint publik.
- **Halaman konten**: FAQ dan informasi lain yang bisa diubah, dengan endpoint baca publik.
- **Siaran terjadwal**: pengumuman bertarget peran yang dikirim otomatis pada waktu tertentu.
- **Moderasi ulasan**: konsumen bisa melaporkan ulasan; admin bisa menyembunyikan atau menolak laporan.
- **Kebijakan dan pelanggaran SLA**: target yang bisa diatur, deteksi otomatis, dan kompensasi dompet simulasi.
- **Catatan admin dan laporan mingguan**: catatan internal dan ringkasan mingguan otomatis.
- Migrasi V12 mendukung tabel baru dan ikut dalam ekspor serta impor.

## [4.1.0] - 2026-11-27

### Ditambahkan

- **Dokumen teknisi**: unggah KTP, sertifikat, dan dokumen lain dengan tanggal kedaluwarsa serta verifikasi admin.
- **Wilayah layanan**: menentukan area cakupan atau area pengecualian. Dispatcher melewati lokasi yang dikecualikan.
- **Aturan terima otomatis**: menerima tawaran di atas batas harga dengan batas jarak tertentu.
- **Rencana rute**: pekerjaan hari ini diurutkan berdasarkan jarak dari lokasi terakhir teknisi.
- **Check-in dan check-out**: kedatangan hanya dalam radius 500 meter dari alamat pekerjaan.
- **Material dan biaya**: mencatat barang yang dipakai per pesanan.
- **Badge dan level**: badge dari jumlah pekerjaan, rating, dan tingkat penerimaan tawaran.
- **Template chat, pengajuan libur, jurnal kerja, dan checklist peralatan** untuk operasional harian.
- **Statistik performa**: waktu respons, waktu selesai, tingkat pembatalan, dan progres level.
- Migrasi V11 mendukung tabel baru dan ikut dalam ekspor serta impor.

## [4.0.0] - 2026-11-27

### Ditambahkan

- **Katalog layanan**: pencarian, filter keahlian, harga, rating, urutan, kategori, dan paket bundling.
- **Estimasi harga**: menghitung biaya jasa, kunjungan, surge jam sibuk, diskon paket, promo, voucher, dan diskon member sebelum memesan.
- **Pembayaran dompet**: bayar pesanan dari saldo dengan cashback, batas harian, dan top up otomatis.
- **Tier loyalitas**: Silver, Gold, dan Platinum dengan diskon, prioritas, dan gratis kunjungan.
- **Voucher dan referral**: voucher dari admin, kode referral, dan poin reward untuk kedua pihak.
- **Cicilan**: paket 3 dan 6 bulan simulasi dengan catatan pembayaran.
- **Kontrol langganan**: jeda, lanjut, dan lewati tanggal berikutnya.
- **Interaksi ulasan**: vote membantu dan balasan teknisi.
- **Tambahan pesanan**: tanda tangan digital, checklist pekerjaan bertahap dengan foto bukti, dan pengajuan refund dengan keputusan admin.
- **Preferensi pengguna**: tema, bahasa, ukuran teks, pengurangan animasi, jam tenang, dan antrean aksi offline tersimpan per pengguna.
- Migrasi V10 mendukung semua tabel baru dan ikut dalam ekspor serta impor.

## [3.1.0] - 2026-11-27

### Ditambahkan

- **Pengingat pesanan**: notifikasi satu jam sebelum jadwal kunjungan, dengan izin notifikasi Android 13.
- **Foto ulasan**: konsumen bisa melampirkan sampai lima foto pada penilaian; teknisi melihatnya dengan pratinjau layar penuh di ruang kerja.
- **Jadwal online otomatis**: teknisi menentukan jendela online mingguan; jadwal perawatan mengubah status online pada hari yang dijadwalkan.
- **Penanda pesan belum dibaca**: daftar pesan langsung menampilkan jumlah belum dibaca dan membuka percakapan menandainya sebagai dibaca.
- **Moderasi chat**: pengguna bisa melaporkan pesan langsung; admin bisa menyembunyikan pesan atau menolak laporan.
- **Ekspor CSV**: admin bisa membagikan pesanan dan transaksi dompet sebagai berkas CSV.
- **Cadangan terenkripsi**: admin bisa mengekspor dan memulihkan database dengan AES-GCM dan kata sandi (PBKDF2, 120000 iterasi).
- **Unggah portofolio bersamaan**: teknisi bisa menambahkan sampai lima foto portofolio sekaligus.

### Diubah

- Ekspor database sekarang mencakup tabel V6 sampai V9. Arsip versi 2 dan versi 3 tetap bisa diimpor.
- Detail pesanan menampilkan hitung mundur SLA menuju jam mulai yang dijadwalkan.

## [3.0.0] - 2026-11-27

### Ditambahkan

- **Dompet dan poin konsumen**: top up simulasi, riwayat saldo, poin loyalitas, dan penukaran poin.
- **Langganan layanan**: paket berulang dengan interval, tanggal mulai, jadwal berikutnya, dan pembatalan. Langganan yang jatuh tempo membuat pesanan otomatis dari jadwal perawatan.
- **Ruang kerja teknisi**: blokir jadwal, portofolio foto, statistik performa, dan rekening pencairan. Dispatcher melewati teknisi yang diblokir pada tanggal tersebut.
- **Pusat kendali admin**: penyelesaian sengketa dengan refund simulasi, log audit, pengumuman yang tampil untuk semua pengguna, konfigurasi sistem yang bisa diubah, dan papan peringkat teknisi.
- Migrasi V6, V7, dan V8 mendukung fitur baru dan ikut dalam ekspor serta impor database.

### Diubah

- Pengumuman tampil di pusat notifikasi.
- Layar baru mengikuti design system dan animasi masuk yang sudah ada.

## [2.21.0] - 2026-11-26

### Ditambahkan

- **Pembayaran QR asli**: transfer bank dan dompet digital kini menghasilkan QR yang bisa dipindai dengan payload bergaya EMVCo QRIS dan checksum CRC. Sepuluh bank nasional dan lima dompet digital bisa dipilih sebelum membayar.
- **Teknisi sekitar**: direktori teknisi kini memakai alamat konsumen sebagai titik acuan dan menampilkan jarak. Setiap kartu punya tombol chat langsung.
- **Negosiasi langsung**: konsumen dan teknisi bisa berchat tanpa pesanan. Teknisi bisa mengirim penawaran harga beserta layanan, tanggal, dan jam; konsumen menerima atau menolak dari tab Chat. Penerimaan membuat pesanan dengan harga hasil negosiasi.
- **Pratinjau foto**: pendaftaran teknisi menampilkan foto KTP dan selfie yang dipilih dengan pratinjau yang bisa diperbesar, dan form pemesanan menampilkan foto lokal sebelum diunggah.
- **Jam pilihan konsumen**: form pemesanan memiliki pemilih jam selain slot bawaan.

### Diubah

- Migrasi V5 menambahkan tabel `direct_messages` dan `proposals`; keduanya ikut tercakup dalam ekspor dan impor database.
- Layar negosiasi baru memakai animasi masuk standar, dan QR pembayaran muncul dengan transisi halus.

## [2.20.0] - 2026-11-26

### Diubah

- **Startup lebih cepat**: hash password demo dihitung sebelumnya, bukan lima kali BCrypt cost 12 di dalam layar pemuatan. Akun sistem untuk unggahan pendaftaran kini dibuat hanya saat dibutuhkan.
- **Request lebih ringan**: kunci AndroidKeyStore disimpan sekali, interceptor hanya membaca access token, dan alamat server di-cache untuk gambar. Ini menghilangkan pembacaan KeyStore dan dekripsi berulang per request.
- **Lebih native**: pengguna reduced motion tidak lagi mendapat animasi skeleton berkelanjutan, formater waktu dipindah keluar komposisi, geocoding berjalan di luar utas utama, dan collector pembayaran serta akun tidak menumpuk saat dimuat ulang.

### Diperbaiki

- Pesanan kini bisa dibatalkan setelah teknisi ditemukan, dan teknisi bisa dilepaskan kembali ke pencarian dari layar.
- Penutupan laporan tidak lagi menggantung pesanan konsumen di status `DILAPORKAN`.
- Teknisi demo mulai dengan lokasi dekat alamat contoh dan dispatcher menerima lokasi yang sedikit lebih lama, sehingga tawaran berjalan di instalasi baru.
- Pesan validasi server kini ditampilkan, logout selalu menghapus sesi lokal, dan aksi batal serta kirim langsung memperbarui tampilan.
- Layar pencarian kini menangani pembatalan dan kegagalan muat, serta daftar pekerjaan teknisi menyertakan `MENUNGGU_KONFIRMASI`.

## [2.19.0] - 2026-11-26

### Diperbaiki

- **Navigasi tab**: berpindah tab dari layar mana pun sekarang selalu memicu navigasi. Opsi `popUpTo(target)` sebelumnya membuat aksi itu jadi no-op bila tab tersebut sudah ada di back stack, sehingga pengguna terpaksa menekan tombol kembali.
- **Bidang teks**: label kosong tidak lagi menyembunyikan placeholder. Petunjuk Username dan Password tampil sebelum mengetik, dengan warna placeholder yang jelas.
- **Layar Akun**: header profil lebih ringkas, baris dikelompokkan di bawah bagian Akun dan Teknisi, serta jarak memakai token desain.
- **Tombol LOGIN**: kini memenuhi lebar formulir dan memakai state loading yang sama, bukan spinner terpisah.
- **Kelancaran startup**: animasi berkelanjutan saat backend tertanam disiapkan dihapus untuk mengurangi frame drop pada perangkat lambat.

## [2.18.0] - 2026-11-26

### Diperbaiki

- **Crash saat membuka aplikasi**: Room menolak migrasi yang nomor awalnya juga terdaftar di `fallbackToDestructiveMigrationFrom`. Versi 3 kini memakai jalur eksplisit `3 -> 4 -> 5`, dan hanya cache lebih lama yang dibangun ulang.
- **Startup backend tertanam**: deteksi skema lama tidak lagi meminta metadata indeks H2 dengan nama tabel kosong. Kini indeks dicari di tabel `payments`, beserta tes regresi.
- **Ikon brand tema terang**: ikon putih sebelumnya tak terlihat di Beranda. Sekarang memakai warna primary.

## [2.17.0] - 2026-11-26

### Ditambahkan

- **Cek koneksi server dan bantuan**: Pengaturan kini punya tombol pemeriksaan `/health` dan FAQ yang bisa dibuka, mencakup pemesanan, teknisi tidak ditemukan, dan pengaturan LAN.
- **Filter notifikasi**: pusat notifikasi bisa menampilkan semua item atau hanya yang belum dibaca.
- Unggah foto pendaftaran teknisi kini publik, sehingga pendaftaran bisa selesai sebelum akun dibuat.

### Diperbaiki

- Tombol mati kini berfungsi: baris notifikasi membuka pesanan, lonceng chat membuka notifikasi, baris Tentang membuka Pengaturan, "Cari teknisi pengganti" dan "Lihat pendapatan" benar-benar berpindah halaman.
- Jadwal ulang dinonaktifkan saat pesanan gagal dimuat, dan tombol telepon disembunyikan bila nomor tidak tersedia.
- Lokasi teknisi kini memakai pembaruan lokasi segar saat online, bukan `lastLocation` yang bisa basih, sehingga tawaran benar-benar sampai.
- Foto kini dimuat dengan token dan alamat server yang dikonfigurasi lewat satu ImageLoader Coil. URL verifikasi admin dinormalisasi agar prefiks `/photos/` tidak dobel.
- Refresh token kini single-flight; beberapa 401 bersamaan memakai satu panggilan, dan galat server sementara tidak memaksa logout.
- Cache Room bisa dibangun ulang dengan aman saat naik dari versi lama.
- Layar pencarian dan status membatalkan polling serta pengumpul WebSocket lama; navigasi pencarian hanya terjadi sekali.
- Pembayaran ulang membatalkan pembayaran pending lama, hitung mundur memakai waktu kedaluwarsa asli, dan snackbar admin serta kata kunci pencarian tidak hilang setelah refresh.
- Peta hanya membangun ulang penanda saat nilainya berubah, hitung mundur tawaran lokal per kartu, dan pembacaan foto serta geocoding dipindah dari utas utama.

## [2.16.0] - 2026-11-26

### Ditambahkan

- **Pengalih tema dan Pengaturan**: pilih tampilan Sistem, Terang, atau Gelap dari layar Pengaturan baru. Layar yang sama mengatur alamat server dan menjelaskan mode lokal.
- **Analitik admin di server**: `GET /admin/analytics` menghitung semua pesanan, bukan hanya 50 terbaru, beserta tes integrasi. Ringkasan admin memakainya dan tetap punya fallback ke perhitungan lama.
- **Penyelesaian otomatis saat teknisi tidak ada**: setelah jendela pencarian berakhir, pesanan dibatalkan sistem dan pembayaran yang sudah lunas dikembalikan.

### Diperbaiki

- Bidang password kembali menerapkan penyamaran dan opsi keyboard, sehingga tombol tampil/sembunyi berfungsi.
- Navigasi bawah per peran kini benar-benar terpasang, dengan tab Chat dan layar daftar chat yang bisa dibuka.
- Teks di atas kontainer terang memakai peran warna on-container sehingga tetap terbaca di dua tema.
- Pesan snackbar di layar pencarian dan status kini ditampilkan lalu dihapus, sehingga pesan yang sama bisa muncul lagi.
- Lima layar tidak lagi mengambil data dua kali saat pertama dibuka. Chat membatalkan pengumpul dan polling lama sebelum memulai yang baru, serta hanya menggulir saat pesan terakhir berubah.
- Pengaturan ulang ID saat impor database kini dijalankan setelah commit data, dan layar impor tetap terpasang sehingga restart serta penghapusan sesi benar-benar berjalan.
- Database lama hanya mengadopsi migrasi yang memang sudah ada di dalamnya.
- Pembayaran pending kedua ditolak, sweep kedaluwarsa melewati baris yang bertabrakan, penilaian tidak lagi menghapus jumlah pekerjaan seed, dan ETA dihitung dari lokasi teknisi terakhir.
- Kesiapan backend tertanam tidak lagi memblokir UI ketika server LAN jarak jauh dikonfigurasi.

## [2.15.0] - 2026-11-26

### Diubah

- **Rombaan design system**: skema warna Material 3 terang dan gelap dari fondasi Figma, tipografi Plus Jakarta Sans, token jarak dan radius, serta ekstensi tema untuk warna success dan warning.
- Komponen reusable dengan preview terang dan gelap: tombol, bidang isian, label status, kartu layanan/teknisi/pesanan, rincian harga, bintang penilaian, linimasa, gelemper chat, item notifikasi, tampilan keadaan, kartu tawaran, snackbar, dialog, lembar bawah, chip pilihan, skeleton, sakelar ketersediaan, FAB, bilah atas, dan navigasi bawah per peran.
- Semua layar kini memakai token melalui nama UI lama, bilah navigasi per peran, serta kata "kamu" pada teks Bahasa Indonesia.
- Ikon aksi memiliki contentDescription dan perubahan status pembayaran diumumkan ke layanan aksesibilitas.

## [2.14.0] - 2026-11-26

### Ditambahkan

- **Server LAN untuk demo**: `start-lan-server.bat` menjalankan backend di laptop dengan database H2 lokal dan menampilkan IP LAN laptop. Beberapa HP pada WiFi yang sama bisa memakai server itu. Lihat `docs/DEPLOY_LAN.md`.
- H2 kini menjadi dependency runtime backend supaya server laptop bisa memakai file database lokal.

### Diubah

- Konfigurasi keamanan jaringan Android mengizinkan HTTP polos ke alamat LAN privat, yang dibutuhkan server demo di laptop.

## [2.13.0] - 2026-11-26

### Ditambahkan

- **Opsi backend online**: aplikasi bisa terhubung ke satu server Ktor bersama, sehingga pesanan dari satu HP langsung muncul di HP lain. Termasuk `Dockerfile` di root, packaging distribusi backend, dan `docs/DEPLOY_ONLINE.md`.

### Diperbaiki

- **Regresi startup**: H2 mode PostgreSQL punya tabel sistem bernama `users`. Deteksi skema kini hanya memeriksa schema `public`, sehingga instalasi baru menjalankan migrasi dengan benar. Instalasi yang tertinggal dengan penanda versi tanpa skema akan memperbaiki diri saat peluncuran berikutnya. Logika deteksi ada di helper JVM beserta tes regresi.
- **Alamat WebSocket**: klien WebSocket kini memakai alamat server yang diisi di layar Login, termasuk `wss` untuk HTTPS, dan menyambung ulang saat alamat berubah.

## [2.12.0] - 2026-11-26

### Ditambahkan

- **Direktori teknisi**: konsumen dapat menjelajahi teknisi terverifikasi, mencari berdasarkan nama atau keahlian, memfilter keahlian, melihat rating dan jumlah pekerjaan, serta menyimpan atau menghapus favorit. Endpoint baru `GET /consumer/technicians` beserta tes integrasi.
- **editor alamat**: layar Akun kini dapat menambah dan mengubah alamat tersimpan, termasuk tombol **Gunakan lokasi saya** dengan geocoding dan isian koordinat manual.
- **Notifikasi tersimpan**: pusat notifikasi kini bertahan setelah aplikasi dimulai ulang. Migrasi Room menambahkan tabel `cached_notifications` tanpa menghapus favorit atau pesanan tersimpan.

### Diperbaiki

- **Transfer database**: ekspor dan impor kini menyertakan `refresh_tokens`, menolak nama foto dan kolom tidak aman, membatasi ukuran arsip, membersihkan foto lama, dan menjaga konsistensi skema.
- **Backend tertanam**: migrasi dilacak dalam tabel versi, dijalankan dengan penanda versi, pernyataan idempoten, dan pemisah SQL yang memahami komentar; instalasi lama diadopsi tanpa menjalankan ulang migrasi lama. Kegagalan startup kini menampilkan layar coba lagi.
- **Kebenaran backend**: kode pesanan maju saat bentrok, tawaran ganda dan notifikasi teknisi kosong berulang dicegah, penarikan mengunci baris teknisi, rotasi refresh token mengunci baris token, lokasi basih dihapus saat teknisi offline, dan pembatas laju membuang bucket lama.
- **Perbaikan klien**: refresh senyap memakai alamat server yang dikonfigurasi, pembayaran ulang berpindah layar setelah berhasil, notifikasi penarikan tidak tertimpa, beranda teknisi menampilkan pendapatan hari ini, dan slot tidak diambil dua kali.
- **Penanganan crash**: pengecualian tidak tertangani dicatat dan diteruskan ke penangan sistem, bukan diabaikan diam-diam.

## [2.11.0] - 2026-11-26

### Ditambahkan

- **Pesan lagi**: layar struk kini punya tombol **Pesan lagi layanan ini** yang membuka form Pemesanan dengan layanan yang sama sudah dipilih. Berlaku untuk struk dari alur penilaian maupun riwayat pesanan selesai.

## [2.10.0] - 2026-11-26

### Ditambahkan

- **Teknisi favorit**: dari struk pesanan selesai, konsumen bisa menekan **Simpan teknisi favorit**. Daftar favorit tersedia di tab Akun dan bisa dihapus satu per satu. Data disimpan lokal di Room, tidak memerlukan endpoint baru.
- Basis data cache naik ke versi 3 dengan tabel `favorite_technicians`.

## [2.9.0] - 2026-11-26

### Ditambahkan

- **Buat layanan baru**: panel admin kini bisa menambah layanan (nama, harga mulai, biaya kunjungan) lewat tombol **Layanan baru**, selain mengubah layanan yang ada. Endpoint `POST /admin/services` dan tes integrasi terkait.
- **Kamera untuk lapor masalah**: layar Lapor masalah kini juga punya tombol **Ambil foto**, melengkapi K-14 seperti form Pemesanan dan Selesaikan pekerjaan.

## [2.8.0] - 2026-11-25

### Ditambahkan

- **Analitik panel admin**: ringkasan kini menampilkan grafik batang pesanan 7 hari terakhir dan tiga status pesanan terbanyak. Data dihitung dari daftar pesanan admin yang sudah ada, tanpa endpoint baru atau dependensi grafik.

## [2.7.0] - 2026-11-25

### Diubah

- **Beranda offline-first (kriteria non-fungsional PRD)**: layanan dan alamat kini disimpan di cache Room bersama pesanan. Beranda langsung menampilkan data tersimpan saat dibuka, lalu menyegarkannya dari backend. Jika backend tidak bisa dijangka, data terakhir tetap tampil.
- Basis data cache dinaikkan ke versi 2 dengan tabel `cached_services` dan `cached_addresses`. Karena memakai `fallbackToDestructiveMigration`, hanya cache yang dikosongkan saat pembaruan; data backend tertanam tidak terpengaruh.

## [2.6.0] - 2026-11-25

### Ditambahkan

- **Pencarian pesanan**: tab Pesanan kini punya kolom cari berdasarkan kode pesanan atau nama layanan. Filter bekerja pada tab Aktif dan Riwayat.

### Diubah

- **Aksesibilitas**: `TKTextButton` kini punya tinggi sentuh minimal 48 dp sesuai kriteria non-fungsional PRD.

## [2.5.0] - 2026-11-25

### Ditambahkan

- **Ambil foto dengan kamera (K-05, T-08)**: form Pemesanan dan layar Selesaikan pekerjaan kini punya tombol **Ambil foto** memakai kamera perangkat melalui `ACTION_IMAGE_CAPTURE` dan `FileProvider`. Foto langsung diunggah ke backend seperti foto dari galeri, tanpa izin kamera tambahan.

## [2.4.0] - 2026-11-25

### Ditambahkan

- **Tarik saldo teknisi (T-11)**: tombol **Tarik saldo** kini membuat catatan penarikan sungguhan di backend, bukan hanya notifikasi lokal. Saldo berkurang dan riwayat penarikan tampil di layar Pendapatan. Minimal penarikan Rp1 dan tidak boleh melebihi saldo.
- Migrasi `V4__withdrawals.sql` menambah tabel `withdrawals`, termasuk indeks teknisi dan waktu. Tabel ini juga ikut diekspor dan diimpor bersama data lain.

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

[4.3.0]: https://github.com/nekonaru/tukangkilat/releases/tag/v4.3.0
[4.2.0]: https://github.com/nekonaru/tukangkilat/releases/tag/v4.2.0
[4.1.0]: https://github.com/nekonaru/tukangkilat/releases/tag/v4.1.0
[4.0.0]: https://github.com/nekonaru/tukangkilat/releases/tag/v4.0.0
[3.1.0]: https://github.com/nekonaru/tukangkilat/releases/tag/v3.1.0
[3.0.0]: https://github.com/nekonaru/tukangkilat/releases/tag/v3.0.0
[2.21.0]: https://github.com/nekonaru/tukangkilat/releases/tag/v2.21.0
[2.20.0]: https://github.com/nekonaru/tukangkilat/releases/tag/v2.20.0
[2.19.0]: https://github.com/nekonaru/tukangkilat/releases/tag/v2.19.0
[2.18.0]: https://github.com/nekonaru/tukangkilat/releases/tag/v2.18.0
[2.17.0]: https://github.com/nekonaru/tukangkilat/releases/tag/v2.17.0
[2.16.0]: https://github.com/nekonaru/tukangkilat/releases/tag/v2.16.0
[2.15.0]: https://github.com/nekonaru/tukangkilat/releases/tag/v2.15.0
[2.14.0]: https://github.com/nekonaru/tukangkilat/releases/tag/v2.14.0
[2.13.0]: https://github.com/nekonaru/tukangkilat/releases/tag/v2.13.0
[2.12.0]: https://github.com/nekonaru/tukangkilat/releases/tag/v2.12.0
[2.11.0]: https://github.com/nekonaru/tukangkilat/releases/tag/v2.11.0
[2.10.0]: https://github.com/nekonaru/tukangkilat/releases/tag/v2.10.0
[2.9.0]: https://github.com/nekonaru/tukangkilat/releases/tag/v2.9.0
[2.8.0]: https://github.com/nekonaru/tukangkilat/releases/tag/v2.8.0
[2.7.0]: https://github.com/nekonaru/tukangkilat/releases/tag/v2.7.0
[2.6.0]: https://github.com/nekonaru/tukangkilat/releases/tag/v2.6.0
[2.5.0]: https://github.com/nekonaru/tukangkilat/releases/tag/v2.5.0
[2.4.0]: https://github.com/nekonaru/tukangkilat/releases/tag/v2.4.0
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
