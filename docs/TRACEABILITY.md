# Traceability Matrix: PRD to Code and Tests

Setiap kebutuhan PRD (K-xx konsumen, T-xx tukang, A-xx admin) dipetakan ke berkas kode dan tes yang memenuhinya. Tes backend yang dimaksud ada di `backend/src/test/kotlin/id/tukangkilat/backend/ApiIntegrationTest.kt` (test berjalan melawan H2 in-memory) dan di `shared/src/test/kotlin/id/tukangkilat/shared/`.

## Konsumen (K)

| ID | Prioritas | Implementasi | Tes |
|----|-----------|--------------|-----|
| K-01 | M | `ui/auth/LoginScreen.kt` (layar 01), layar 09 dengan teks "Username atau password salah" (`LoginScreen` + `cleanApiError`) | `login works for all three roles`, `wrong credentials get generic error`, `account locks after five failed attempts`, `login rate limiter` |
| K-02 | M | `POST /auth/register` → `AuthService.registerConsumer`, `ui/auth/RegisterScreen.kt` | `register` dipakai semua tes; validasi password di `ApiIntegrationTest` |
| K-03 | M | `HomeScreen.kt`, `HomeViewModel`, `GET /consumer/services` | `GET /consumer/services` dipanggil di `consumer cannot call technician or admin routes` |
| K-04 | M | `HomeScreen` kartu pesanan aktif dari `GET /consumer/orders` | diverifikasi manual di emulator; data dari `OrderDto` |
| K-05 | M | `OrderFormViewModel`, `OrderFormScreen` (deskripsi wajib, 0-3 foto dari galeri atau kamera, tanggal tidak lampau, slot) | `order lifecycle end to end` (validasi slot dan tanggal) |
| K-06 | M | `OrderFormViewModel.useCurrentLocation` (FusedLocation, Geocoder balik), `OrderRepository.addAddress` | diverifikasi manual di emulator |
| K-07 | M | `PaymentScreen`, `OrderService.pay`, `payments` table | `payment failure then retry with tuai method succeeds`, `order lifecycle end to end` |
| K-08 | M | `PaymentScreen`, `PEMBAYARAN_GAGAL` → `pay()` kembali ke `MENUNGGU_PEMBAYARAN` | `payment failure then retry with tuai method succeeds` |
| K-09 | M | `OrderStatusScreen`, `OrderStatusViewModel`, `OrderQueries.loadEta`, OsmMap | `order lifecycle end to end` (ETA muncul di detail) |
| K-10 | M | `POST /consumer/orders/{id}/cancel` → `OrderService.cancel` dengan aturan PRD §4 | `cancel is rejected after technician departs` |
| K-11 | M | `SearchingScreen`, `reschedule` | diverifikasi manual; `SearchingViewModel` terhubung WS `ORDER_STATUS` |
| K-12 | M | `DoneScreen`, `photoUrls` kind `BUKTI` | `order lifecycle end to end` |
| K-13 | M | `ConfirmScreen` → `POST /consumer/orders/{id}/confirm` | `order lifecycle end to end` |
| K-14 | M | `ReportScreen` → `POST /consumer/orders/{id}/report`, foto `LAPORAN` | diverifikasi manual; `OrderService.report` |
| K-15 | M | `RatingScreen` → `rating`, struk `ReceiptScreen` | `order lifecycle end to end` (rating + tolak rating ganda) |
| K-16 | M | `OrdersScreen` (Aktif / Riwayat), Room cache | `OrderDao`, `OrderRepository.myOrders` |
| K-17 | M | `AccountScreen` (profil, ubah password, alamat, keluar) | diverifikasi manual |
| K-18 | S | `NotificationRepository`, `NotificationsScreen`, menu **Notifikasi** di Akun konsumen | diverifikasi manual; event WS `ORDER_STATUS`, `OFFER_NEW`, `CHAT_MESSAGE` |
| K-19 | S | `ChatScreen`, `GET/POST /consumer/orders/{id}/messages` | diverifikasi manual; endpoint ada di `Main.kt` |
| K-20 | C | `ui/util/ReceiptPdf.kt` (PdfDocument + FileProvider), tombol "Bagikan struk PDF" di `RatingScreen` | diverifikasi manual lewat alur pesanan selesai |

## Tukang (T)

| ID | Prioritas | Implementasi | Tes |
|----|-----------|--------------|-----|
| T-01 | M | Login sama; `role = TUKANG` membuka `TechnicianGraph` (`TukangKilatApp`) | `login works for all three roles` |
| T-02 | M | `RegisterTechnicianScreen`, `POST /auth/register-technician`, foto KTP/selfie via `/photos` | `unverified technician cannot login with clear message` |
| T-03 | M | `TechHomeScreen` sakelar online/offline + `/technician/online`, `/technician/location` tiap 15 detik (endpoint backend; pengiriman periodik dijalankan saat layar terbuka) | diverifikasi manual |
| T-04 | M | Tawaran lewat WS `OFFER_NEW` + polling `GET /technician/offers`, hitung mundur 60 detik di `OfferCard` | `order lifecycle end to end` (offer diterima) |
| T-05 | M | Tolak tawaran dengan alasan di `TechJobsScreen` | diverifikasi manual; `rejectOffer` backend |
| T-06 | M | Aksi berurutan di `TechJobScreen` (Berangkat, Tiba, Mulai, Selesaikan) | `order lifecycle end to end`, `cancel is rejected after technician departs` |
| T-07 | M | `TechJobScreen` mengirim lokasi tiap 5 detik saat `DALAM_PERJALANAN` lewat `POST /technician/location` | diverifikasi manual |
| T-08 | M | `TechFinishScreen` wajib foto bukti + catatan; tombol **Ambil foto** memakai kamera; tunai wajib `cashReceived` | `order lifecycle end to end` |
| T-09 | M | Tunai: `cashReceived` → `paymentStatus = LUNAS` | diverifikasi manual |
| T-10 | S | Kunjungan ulang: `revisitReport` membersihkan tawaran lama lalu dispatch; kandidat pertama = tukang sebelumnya (`is_revisit = true`) | diverifikasi manual |
| T-11 | M | `TechEarningsScreen`, `GET /technician/earnings`, `GET/POST /technician/withdrawals`, tabel `withdrawals` | `technician can withdraw simulated balance and see history` |
| T-12 | M | `TechAccountScreen` (akun, rating, ulasan saya), `/technician/reviews` | `technician sees received reviews after rating` |
| T-13 | S | `ChatScreen` mode teknisi (`/technician/orders/{id}/messages`) | diverifikasi manual |
| T-14 | S | `NotificationRepository`, menu **Notifikasi** di Akun teknisi dan panel admin | diverifikasi manual; event WS diteruskan ke pusat notifikasi |

## Admin (A)

| ID | Prioritas | Implementasi | Tes |
|----|-----------|--------------|-----|
| A-01 | S | `AdminSummaryScreen`, `GET /admin/summary` | diverifikasi manual |
| A-02 | M | `AdminVerifyScreen`, `approve`/`reject` tukang | `unverified technician cannot login`; approve diverifikasi manual |
| A-03 | M | `AdminReportsScreen`, kunjungan ulang dan tutup laporan | diverifikasi manual |
| A-04 | M | `AdminOrdersScreen`, tombol simulasi pembayaran | `order lifecycle end to end`, `payment failure then retry with tuai method succeeds` |
| A-05 | S | `AdminServicesScreen` (layanan), `AdminPromoScreen` (kode promo), `/admin/services`, `/admin/promo-codes` | `admin can create and disable promo codes`, `non admin cannot manage promo codes` |
| A-06 | S | `AdminServicesScreen` + `/admin/users/{id}/status` (ditangguhkan memutus offline) | diverifikasi manual |

## Kriteria selesai (PRD §10)

| Kriteria | Status |
|----------|--------|
| Tiga peran masuk lewat satu layar login dan mendarat di antarmuka yang benar | Terpenuhi (diuji di emulator-5554 dan tes integrasi) |
| Enam skenario uji utama lulus di dua perangkat | Skenario 1-6 diverifikasi manual di emulator; tes integrasi mencakkup skenario 1-4 |
| Seluruh 12 layar desain konsumen tampil dan terhubung ke data nyata | Terpenuhi (Beranda terhubung ke `/consumer/*`, dst.) |
| Posisi tukang berasal dari perangkat/emulator tukang | Terpenuhi (lokasi dari perangkat Tukang, bukan data palsu) |
| Tidak ada data hardcode di layar; seed hanya akun demo dan layanan | Terpenuhi (audit: semua teks harga/waktu dari API) |
| Dokumen skripsi memuat ERD, alur status, dan hasil pengujian | ERD di `docs/ERD.md`; alur status di `docs/STATE_MACHINE.md`; hasil tes di atas |
