# Status design system v2.15.0

## Yang sudah sesuai

- Skema warna terang dan gelap mengikuti nilai Figma. Success dan warning tersedia lewat `tukangKilatColors()`.
- Font Plus Jakarta Sans dibundel di `res/font` dan dipetakan ke skala Display 36 sampai Label 12.
- Token jarak 8/16/24/32/40/48/64, margin 16 dp, target sentuh 48 dp, dan radius 8/12/16/24 dipakai di komponen.
- Elevasi 0 sampai 5 dipetakan ke elevasi Material 3.
- Pustaka komponen di `ui/components/DesignSystem.kt` memiliki preview terang dan gelap.
- Navigasi bawah memakai empat tab konsumen, empat tab tukang, dan lima tab admin.
- Ikon aksi memiliki contentDescription. Status pembayaran memakai live region.
- Teks UI memakai "kamu". Logika bisnis, ViewModel, repository, backend, dan mesin status tidak diubah.

## Yang belum sepenuhnya sesuai

- Belum ada mockup per layar dari Figma, jadi susunan tiap layar masih mengikuti struktur lama. Komponen dan token sudah dipakai, tetapi komposisi akhir belum diverifikasi satu per satu terhadap desain visual.
- Uji terang dan gelap baru dilakukan lewat preview komponen dan kompilasi. Uji interaktif di emulator tidak stabil pada lingkungan ini.
- Sebagian besar layar masih memakai `ScreenHeader` lama, bukan `TKTopAppBar` varian beranda. Tombol, bidang isian, dan warna sudah mengikuti token, tetapi bilah atas belum seragam di semua layar.
- Beberapa teks dengan `maxLines` masih bisa terpotong pada font scaling ekstrem. Belum ada uji otomatis untuk pengaturan ukuran font sistem.

## Perintah verifikasi

```text
./gradlew :shared:test :backend:test :app:assembleDebug
```

## Pembaruan v2.16.0

- Pengalih tema (Sistem/Terang/Gelap) tersedia di layar Pengaturan dan dipersistenkan dengan DataStore.
- Beberapa temuan audit pasca-redesain sudah diperbaiki: password field, snackbar, duplikat fetch, Chat polling, warna teks pada kontainer terang, navigasi bawah, dan state analitik admin.
- Belum terselesaikan: variasi bobot font variable masih bergantung pada dukungan sistem, sebagian bilah atas layar memakai `ScreenHeader` lama, dan uji interaktif terang/gelap penuh belum tersedia.

## Pembaruan v2.17.0

- Banyak tombol dan jalur aksi yang sebelumnya mati kini tersambung, termasuk notifikasi, chat, pengaturan, teknisi pengganti, dan pendapatan.
- Unggah foto pendaftaran teknisi, pemuatan foto bertoken, lokasi teknisi segar, dan refresh token single-flight diperbaiki.
- Masih belum: sebagian layar detail tidak punya tombol kembali pada keadaan gagal, dan uji interaktif penuh di perangkat belum tersedia.
