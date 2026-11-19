# Skrip demo: 6 skenario uji utama

Jalankan `docker compose up -d db` lalu `./gradlew :backend:run` di terminal pertama. Aplikasi Android di emulator sudah tersambung ke `10.0.2.2:8080` (atau IP LAN lewat `-PbaseUrl=`). Akun demo: `rina`/`rina12345` (konsumen), `budi`/`budi12345` (tukang), `admin`/`admin12345` (admin).

## Skenario 1: pesanan normal, transfer

1. Login sebagai `rina`, pilih Perbaikan Listrik, deskripsikan masalah, pilih besok slot 10.00-11.00, lanjut pembayaran, pilih Transfer, tekan Bayar.
2. Catat kode pesanan (format `TK-ddMMyy-NN`).
3. Login sebagai `admin` (sesi admin di tab lain atau Postman), panggil `POST /admin/payments/{orderId}/simulate-success`.
4. Login sebagai tukang (`budi`), pastikan Online, buka tab Pekerjaan, terima tawaran (60 detik).
5. Tekan Berangkat, Tiba, Mulai bekerja, unggah foto bukti (ambil dari galeri/emulator) dan catatan, Selesaikan.
6. Login sebagai `rina`, buka Pesanan lalu pesanan tersebut, tekan "Konfirmasi hasil pekerjaan", "Sudah beres", beri bintang 5, Kirim.
7. Periksa `order_events` di database: MENUNGGU_PEMBAYARAN → MENCARI_TEKNISI → TEKNISI_DITEMUKAN → DALAM_PERJALANAN → TIBA → SEDANG_BEKERJA → MENUNGGU_KONFIRMASI → SELESAI.

## Skenario 2: tukang menolak

1. Login `rina`, buat pesanan yang sama (layanan Pipa Bocor atau Listrik), bayar tunai.
2. Login sebagai tukang `budi`, **tolak** tawaran dengan alasan apa pun.
3. Konsumen melihat layar "Mencari teknisi lain" dan tetap di status MENCARI_TEKNISI; jadwal dan total tidak berubah.
4. Tukang `sari` (atau `budi` kembali) menerima tawaran; pesanan kembali ke TEKNISI_DITEMUKAN dan alur berjalan seperti skenario 1.
5. Ulangi sampai 5 kali penolakan → sistem menandai konsumen dengan peristiwa TIDAK_ADA_TEKNISI; konsumen dapat menekan "Jadwal ulang" atau batal dengan dana kembali.

## Skenario 3: pembayaran gagal lalu sukses

1. Login `rina`, buat pesanan, bayar transfer, JANGAN simulasikan dulu.
2. Tutup aplikasi, buka Postman, panggil `simulate-fail` untuk pesanan itu.
3. Buka app: layar "Pembayaran belum berhasil" muncul dengan total Rp150.000 dan info dana kembali 1-3 hari kerja.
4. Tekan "Coba lagi" (atau "Ganti metode pembayaran" lalu pilih Tunai atau Dompet digital).
5. Untuk metode non-tunai, admin memanggil `simulate-success`; pesanan otomatis masuk ke alur pencarian teknisi.

## Skenario 4: lapor masalah lalu kunjungan ulang

1. Selesaikan satu pesanan (skenario 1).
2. Di layar Konfirmasi hasil, tekan "Belum, lapor masalah".
3. Pilih alasan (mis. "Masalah belum teratasi"), isi keterangan, unggah foto bila ada, tekan "Minta kunjungan ulang".
4. Login sebagai `admin`, buka Laporan masalah, buka laporan tersebut, tekan "Jadwalkan kunjungan ulang".
5. Sistem men-dispatch ulang; tukang yang sama menjadi kandidat pertama (prioritas). Bila tukang itu online, tawaran datang ke tukang tersebut dengan `is_revisit = true`.

## Skenario 5: tukang belum diverifikasi

1. Pada layar login, tekan "Daftar sebagai tukang", isi formulir lengkap dengan foto KTP dan selfie, kirim.
2. Coba login dengan akun tersebut: ditolak dengan pesan "menunggu verifikasi admin" (HTTP 403).
3. Login sebagai `admin`, buka Verifikasi tukang, buka data tukang baru, tekan Setujui.
4. Coba login lagi: berhasil masuk ke antarmuka tukang.

## Skenario 6: akses peran ditolak

1. Login sebagai `rina`.
2. Dari Postman atau dari aplikasi, panggil `GET /technician/offers` atau `GET /admin/summary` dengan token konsumen: server menjawab **HTTP 403** dengan `{"code":"FORBIDDEN",...}`.
3. Panggil tanpa token: **HTTP 401**.
4. Tukang memanggil `GET /admin/summary`: **HTTP 403**.

## Checklist akhir demo

- [ ] Skenario 1 selesai sampai struk menampilkan "Pesanan selesai"
- [ ] Skenario 2 pesanan kembali ke tukang setelah penolakan
- [ ] Skenario 3 gagal lalu berhasil tanpa data ganda
- [ ] Skenario 4 kunjungan ulang dijadwalkan oleh admin
- [ ] Skenario 5 tukang baru masuk hanya setelah disetujui
- [ ] Skenario 6 RBAC menolak akses lintas peran
