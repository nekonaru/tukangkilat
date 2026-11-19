# Open questions and decisions

PRD dipakai sebagai sumber kebenaran. Hal-hal berikut memutuskan celah PRD atau pilihan implementasi. Urutan: PRD bagian yang paling dekat diberi tahu di setiap item.

## Keputusan implementasi

1. **Login tidak konsisten (PRD §2 celah 1)**: mengikuti layar 01 (username + password). Layar 09 diubah menjadi "Username atau password salah" dengan tata letak yang sama dengan desain. OTP tidak dibuat.
2. **Biaya kunjungan flat Rp25.000** untuk semua layanan (PRD §2 celah 3, menunggu konfirmasi pemilik produk; saat ini flat 25.000 seperti layar 04).
3. **Tampilan tukang dan admin dirancang sendiri** mengikuti token desain dan tata letak kerja: label MODE TUKANG / MODE ADMIN di header, bilah status online/offline, satu aksi utama besar di bawah layar (tukang). Halaman tukang memakai bottom nav Beranda, Pekerjaan, Pendapatan, Akun; admin hanya ringkasan fungsional.
4. **API prefix**: semua rute diawali `/consumer`, `/technician`, atau `/admin` sesuai aturan RBAC PRD §3. Endpoint lain (mis. `POST /photos`, `POST /account/password`) tersedia untuk semua peran terautentikasi.
5. **Status pesanan**: perubahan hanya lewat `OrderService` dengan `canTransition(from, to, actor)` dari modul shared. Transisi tidak sah ditolak HTTP 409 (`ILLEGAL_TRANSITION`). Tukang tidak bisa konfirmasi selesai atau lapor masalah atas nama konsumen; konsumen tidak bisa menekan aksi tukang.
6. **Auto-complete 24 jam (PRD §4)**: order `MENUNGGU_KONFIRMASI` yang tidak direspons dalam 24 jam berubah otomatis menjadi `SELESAI` oleh coroutine backend (metode `autoCompleteStaleConfirmation`).
7. **Tidak ada tukang (PRD §4)**: setelah 5 kali percobaan atau 10 menit tanpa tukang, konsumen menerima peristiwa `TIDAK_ADA_TEKNISI` dan boleh menjadwalkan ulang (`/reschedule`) atau batal dengan pengembalian penuh.
8. **Reschedule membersihkan tawaran**: `/reschedule` menghapus tawaran aktif agar siklus dispatch dimulai dari nol dengan jadwal baru. Kode pesanan tetap.
9. **Kunjungan ulang (PRD §6 T-10)**: `AdminService.revisitReport` menghapus tawaran lama pesanan lalu men-dispatch ulang; tukang sebelumnya menjadi kandidat pertama (`is_revisit = true`), bukan satu-satunya kandidat.
10. **Pembayaran tunai (PRD §2 celah 6)**: pesanan langsung mencari teknisi; status bayar `BELUM_DIBAYAR` hingga tukang menandai uang diterima saat menyelesaikan pekerjaan (wajib centang di `TechFinishScreen`).
11. **Promo HEMAT25**: `NOMINAL` Rp25.000, kuota 100. Pengaplikasian promo dilakukan atomik di `OrderService.pay()` (sekali pakai per pesanan).
12. **Penyimpanan foto**: folder `uploads/` di server, nama acak (`public_id`), validasi MIME dari magic bytes (JPEG/PNG, maks 5 MB), disajikan lewat rute terautentikasi `/photos/{public_id}`; foto yang terhubung ke pesanan hanya bisa dibaca pihak terkait.
13. **Tidak ada FCM**: notifikasi push memakai WebSocket `/ws` (peristiwa tawaran, status, lokasi, chat) dengan fallback polling tiap 5-10 detik. Alasan: FCM butuh `google-services.json` dan akun Firebase yang bukan bagian dari repositori.
14. **Room cache pesanan**: `Beranda` dan `Pesanan` menampilkan data cache dulu lalu memperbarui dari API bila tersedia (PRD non-fungsional: layar harus tampil dari cache).
15. **Session terenkripsi**: token disimpan di DataStore dan dienkripsi dengan AES-256-GCM memakai Android KeyStore. DataStore `fallbackToDestructiveMigration` untuk Room hanya pengembangan.
16. **Berbagi struk (K-20)**: tidak diimplementasikan. Struk ditampilkan di layar (`ReceiptScreen`); ekspor PDF tidak dikerjakan karena prioritas C di PRD.
17. **Tunggu konfirmasi otomatis vs peringatan**: tidak ada notifikasi saat 24 jam akan habis; perubahan status otomatis itu sendiri adalah sumber linimasa.
18. **`GET /orders/{id}` oleh admin**: diizinkan (panel admin bisa melihat detail pesanan apa pun).

## Keterbatasan yang disengaja

- Pembayaran memakai `SimulatorProvider`; tidak ada gateway nyata.
- Tidak ada OTP SMS; login memakai username + password.
- Tidak ada pencairan dana nyata ke rekening tukang; "tarik saldo" di layar Pendapatan mencatat permintaan (simulasi).
- Tidak ada mode iOS, web publik, atau tablet khusus.
- Cleartext HTTP hanya diizinkan untuk `10.0.2.2`, `localhost`, dan domain `.lan` (untuk pengembangan); produksi harus HTTPS.

## Hal yang perlu dikonfirmasi pemilik produk

- Apakah biaya kunjungan flat Rp25.000 untuk semua layanan (saat ini flat, sesuai PRD §2 celah 3).
- Apakah harga layanan dan biaya kunjungan perlu diubah sebelum rilis publik.
- Apakah `HEMAT25` boleh dipakai tanpa batas per akun (saat ini hanya kuota global 100).
