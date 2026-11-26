# Deploy backend online gratis

Panduan ini membuat satu backend bersama yang bisa diakses dari beberapa HP. Pesanan yang dibuat di satu HP langsung terlihat oleh teknisi di HP lain karena semua perangkat memakai server dan database yang sama.

Kebutuhan: akun GitHub, akun Neon, dan akun Render. Ketiganya punya paket gratis tanpa kartu kredit.

## 1. Database di Neon

1. Daftar di `https://console.neon.tech` memakai GitHub, lalu buat project baru.
2. Isi nama project `tukangkilat`. Pilih region `AWS US East 1 (N. Virginia)` atau yang terdekat. Aktifkan hanya **Postgres database**. Klik **Create project**.
3. Di halaman **Connect to your branch**, pilih branch `production`, database `neondb`, role `neondb_owner`. Pastikan **Connection pooling** aktif.
4. Klik **Show password**, lalu **Copy snippet**. Simpan connection string dan password di tempat aman. Jangan taruh di repo dan jangan kirim ke chat.

Connection string Neon berbentuk:

```text
postgresql://neondb_owner:PASSWORD@ep-xxxx-pooler.region.aws.neon.tech/neondb?sslmode=require
```

Untuk backend, ubah ke bentuk JDBC dengan menambah `jdbc:` dan membuang `channel_binding`:

```text
jdbc:postgresql://ep-xxxx-pooler.region.aws.neon.tech/neondb?sslmode=require
```

## 2. Web service di Render

1. Daftar di `https://dashboard.render.com` memakai GitHub.
2. Klik **New**, lalu **Web Service**, lalu hubungkan repository `nekonaru/tukangkilat`.
3. Isi form:

| Kolom | Nilai |
|-------|-------|
| Name | `tukangkilat` |
| Language | `Docker` |
| Branch | `main` |
| Region | `Ohio (US East)` atau terdekat |
| Root Directory | kosong |
| Dockerfile Path | kosong, memakai default `./Dockerfile` |
| Instance Type | **Free** |

4. Jangan pakai **Create a Postgres database** di Render. Database sudah ada di Neon.
5. Tambahkan environment variable berikut di bagian **Environment Variables**:

| Nama | Nilai |
|------|-------|
| `DB_URL` | connection string JDBC dari Neon |
| `DB_USER` | `neondb_owner` |
| `DB_PASSWORD` | password dari Neon |
| `JWT_SECRET` | string acak sendiri, minimal 32 karakter |
| `JWT_ISSUER` | `tukangkilat` |
| `PORT` | boleh kosong, Render mengisi otomatis |
| `UPLOAD_DIR` | `/tmp/uploads` |
| `SEED_DEMO_DATA` | `true` |
| `DEMO_ADMIN_PASSWORD` | password admin demo |
| `DEMO_CONSUMER_PASSWORD` | password konsumen demo |
| `DEMO_TECH_PASSWORD` | password teknisi pertama |
| `DEMO_TECH2_PASSWORD` | password teknisi kedua |

Keempat password demo wajib diisi. Kalau kosong, seed tidak membuat akun `admin`, `rina`, `budi`, dan `sari`.

6. Klik **Deploy web service**. Tunggu status menjadi **Live**.

## 3. Cek server

Buka alamat yang diberikan Render di browser:

```text
https://namaservice.onrender.com/health
```

Balasan yang benar:

```json
{"status":"ok"}
```

Paket gratis Render tidur setelah 15 menit tanpa traffic dan bangun lagi sekitar satu menit. Sebelum demo, buka dulu `/health` supaya server siap.

## 4. Pasang APK di dua HP

1. Unduh APK rilis terbaru dari halaman Releases.
2. Buka aplikasi, masuk ke dialog alamat server di layar Login, lalu isi alamat Render tanpa trailing slash, misalnya `https://namaservice.onrender.com`.
3. Lakukan di kedua HP dengan alamat yang sama.
4. Login `rina` di HP konsumen dan `budi` di HP tukang.
5. Di HP tukang, aktifkan status online dan izinkan lokasi.
6. Buat pesanan di HP konsumen, bayar, lalu simulasikan pembayaran sukses dari panel admin.
7. Tawaran harus muncul di HP tukang. Kalau WebSocket terputus saat server bangun ulang, aplikasi memakai polling sebagai cadangan.

## 5. Kembali ke mode lokal

Kosongkan lagi kolom alamat server di layar Login. Aplikasi akan memakai backend tertanam di dalam APK seperti sebelumnya. Data lokal dan data online tidak tercampur.

## Batasan paket gratis

- Render menyimpan file uploads secara sementara. Foto bisa hilang saat service restart atau deploy ulang.
- Neon gratis memberi 1 GB storage dan 100 CU-hours per bulan per project.
- Render gratis memberi 750 instance hours per bulan dan tidur saat idle.
- Jangan memakai ini untuk data produksi sungguhan.
