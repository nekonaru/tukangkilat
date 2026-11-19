# Audit Desain: Layar Konsumen vs Desain Acuan

Perbandingan layar konsumen di aplikasi dengan 12 gambar desain di `docs/design/`. Nilai warna diambil dari `docs/design/identitas-visual.png` (palet: Biru malam #1B2F4D, Oranye kilat #E8681E, Biru kabut #B9C4D6, Putih).

| No | Layar | Kesamat | Catatan perbaikan |
|----|-------|---------|---------------------|
| 01 | Masuk | Logo, tagline, kolom Username dan Password, tombol LOGIN penuh oranye | Sesuai desain |
| 02 | Beranda | Header logo, kolom cari oranye, 3 layanan dengan harga, kartu pesanan aktif, bottom nav | Sesuai desain |
| 03 | Pemesanan | Kartu layanan oranye, kolom cerita masalah, foto (0-3 via Photo Picker), tanggal, slot, alamat + peta, "Gunakan lokasi saya" | Foto memakai `AsyncImage`; peta memakai tile OpenStreetMap (osmdroid), tanpa API key |
| 04 | Pembayaran | Ringkasan, biaya jasa, biaya kunjungan, total, 3 metode, kode promo, "Bayar sekarang" | Kolom promo diberi placeholder "Kode promo (opsional)" (PRD: validasi ke server saat menekan Bayar) |
| 05 | Status pesanan | Peta, ETA, kartu tukang (Telepon, Pesan), linimasa 4 langkah berwaktu dari `order_events`, tombol batal sebelum TIBA | Sesuai desain; saat TIBA tombol batal disembunyikan |
| 06 | Pekerjaan selesai | Banner hijau, foto bukti, jam selesai, catatan teknisi, "Konfirmasi hasil pekerjaan" | Sesuai desain |
| 07 | Konfirmasi hasil | Ikon, pertanyaan, kartu pesanan, "Sudah beres" dan "Belum, lapor masalah" | Sesuai desain |
| 08 | Penilaian | Bintang 1-5, tag cepat (Tepat waktu, Ramah, Rapi), komentar, kirim, struk | Menampilkan struk setelah submit (di dalam layar yang sama) |
| 09 | Masuk gagal | "Username atau password salah" (teks diubah dari Nomor HP sesuai keputusan PRD §2) | Sama layout dengan desain |
| 10 | Pembayaran gagal | Rincian pesanan, info dana kembali 1-3 hari kerja, "Coba lagi" dan "Ganti metode" | "Coba lagi" memakai metode yang sama; "Ganti metode" kembali ke layar 04 |
| 11 | Teknisi menolak | "Mencari teknisi lain", kartu pesanan, catatan, reschedule | Tombol Jadwal ulang memicu dialog dan `/reschedule` |
| 12 | Lapor masalah | 3 alasan, keterangan wajib, foto maks 3, "Minta kunjungan ulang" | Sesuai desain |

## Temuan umum

- **Warna dan tipografi**: semua layar memakai token dari `ui/theme/Color.kt` (Ink, Orange, Mist, Cream); tidak ada warna hardcode di luar tema.
- **Ikonografi**: ikon garis Material Icons, konsisten dengan gaya garis di desain.
- **Bottom nav konsumen**: Beranda, Pesanan, Akun dengan ikon dan indikator aktif oranye, sesuai desain layar 02.
- **Top bar**: judul + tombol kembali di layar alur (`ScreenHeader`); layar Beranda menampilkan logo tanpa tombol kembali.
- **Status bar**: aplikasi edge-to-edge dengan status bar dan navigation bar biru tua.

## Selisih yang disengaja (bukan kekurangan)

1. **Peta** memakai OpenStreetMap (osmdroid) karena tidak butuh API key dan bekerja offline-ish; desain tidak menuntut penyedia tile tertentu.
2. **Layar 09** (Masuk gagal) menampilkan teks "Username atau password salah" sebagai pengganti "Nomor belum terdaftar" sesuai keputusan PRD.
3. **Tab Pesanan dan Akun** adalah implementasi baru (tidak didesain), mengikuti keputusan PRD bagian 2.
4. **Layar 08** menggabungkan form penilaian dan struk (satu layar) karena desain menampilkan keduanya berurutan di layar yang sama.
5. **Ikon aplikasi** diambil dari berkas `docs/design/identitas-visual.png` lalu dibuat ulang sebagai vector drawable (`ic_brand.xml`, `ic_launcher_foreground.xml`) dengan warna Identitas Visual: Biru malam dan Oranye kilat.
