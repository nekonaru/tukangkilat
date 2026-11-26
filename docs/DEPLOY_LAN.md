# Demo backend lewat WiFi laptop

Panduan ini menjalankan backend di laptop supaya beberapa HP bisa memakai satu server yang sama. Tidak perlu kartu kredit, tidak perlu akun hosting, tidak perlu koneksi internet.

## Yang disiapkan

- File `start-lan-server.bat` di root repo.
- Database H2 lokal di folder `data/`. File ini dibuat otomatis saat server pertama kali jalan.
- Akun demo: `admin` / `admin12345`, `rina` / `rina12345`, `budi` / `budi12345`, `sari` / `sari12345`.

## Langkah singkat

1. Pasang Java 21 di laptop. Cek dengan `java -version`.
2. Hubungkan laptop ke WiFi yang sama dengan HP yang akan dipakai.
3. Double-click `start-lan-server.bat`.
4. Tunggu sampai muncul baris yang bertuliskan `Started MainKt` atau log server lainnya. Jendela terminal harus tetap terbuka.
5. Di terminal akan tampil daftar IP LAN laptop. Salin salah satu yang bukan `127.0.0.1`, misalnya `192.168.1.5`.
6. Di setiap HP, buka aplikasi Tukang Kilat, masuk ke dialog alamat server di layar Login, lalu isi:

```text
http://192.168.1.5:8080
```

7. Login di HP sesuai peran. HP konsumen pakai `rina`, HP tukang pakai `budi`.
8. Di HP tukang, aktifkan status online dan izinkan lokasi.
9. Buat pesanan dari HP konsumen. Status dan tawaran akan muncul di HP tukang.

## Firewall

Saat server pertama kali dijalankan, Windows Defender bisa menampilkan popup. Pilih **Allow** atau **Izinkan** untuk jaringan privat. Kalau popup tidak muncul dan HP tidak bisa terhubung, tambahkan aturan inbound untuk port `8080` pada jaringan privat.

## Cek cepat

Dari browser HP, buka:

```text
http://IP_LAPTOP:8080/health
```

Balasan harus:

```json
{"status":"ok"}
```

Kalau tidak bisa diakses, cek hal ini:

- Laptop dan HP berada di WiFi yang sama, bukan jaringan tamu yang terisolasi.
- Firewall mengizinkan port `8080`.
- Jendela server masih terbuka.
- Alamat di HP tidak ada trailing slash dan memakai `http`, bukan `https`.

## Menghentikan dan mengulang

Tekan `Ctrl+C` di jendela terminal atau tutup jendela tersebut. Untuk memulai lagi, double-click `start-lan-server.bat`. Data pesanan tetap ada di folder `data/`.

Kalau ingin menghapus semua data demo, hentikan server lalu hapus folder `data/`. Server berikutnya akan membuat database baru dan mengisi akun demo lagi.

## Kembali ke mode lokal di HP

Kosongkan kembali kolom alamat server di layar Login. Aplikasi akan memakai backend tertanam di dalam APK seperti semula.

## Catatan

- Server ini hanya bisa diakses dari perangkat dalam WiFi yang sama.
- Jangan dipakai untuk data sungguhan.
- JWT secret dibuat acak setiap kali server dijalankan, jadi sesi lama akan otomatis logout setelah server diulang. Itu normal untuk demo.
