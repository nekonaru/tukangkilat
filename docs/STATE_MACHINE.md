# Diagram status pesanan

Alur mengikuti PRD bagian 4. Semua transisi terjadi lewat `OrderService` di backend, dalam transaksi database, dan selalu menulis baris `order_events`.

```mermaid
stateDiagram-v2
    [*] --> MENUNGGU_PEMBAYARAN : konsumen menekan Lanjut ke pembayaran

    MENUNGGU_PEMBAYARAN --> MENCARI_TEKNISI : pembayaran berhasil (transfer/dompet/tunai/simulasi)
    MENUNGGU_PEMBAYARAN --> PEMBAYARAN_GAGAL : pembayaran ditolak atau kedaluwarsa 30 menit
    MENUNGGU_PEMBAYARAN --> DIBATALKAN : konsumen batal

    PEMBAYARAN_GAGAL --> MENUNGGU_PEMBAYARAN : coba lagi dengan metode lain
    PEMBAYARAN_GAGAL --> DIBATALKAN : konsumen batal

    MENCARI_TEKNISI --> TEKNISI_DITEMUKAN : tukang menerima tawaran
    MENCARI_TEKNISI --> DIBATALKAN : konsumen batal / 5x tawaran gagal / 10 menit tanpa teknisi

    TEKNISI_DITEMUKAN --> DALAM_PERJALANAN : tukang menekan Berangkat
    TEKNISI_DITEMUKAN --> MENCARI_TEKNISI : tukang menolak atau tawaran kedaluwarsa (5x total)

    DALAM_PERJALANAN --> TIBA : tukang menekan Saya sudah tiba
    TIBA --> SEDANG_BEKERJA : tukang menekan Mulai bekerja
    SEDANG_BEKERJA --> MENUNGGU_KONFIRMASI : tukang menekan Selesaikan (foto bukti + catatan)

    MENUNGGU_KONFIRMASI --> SELESAI : konsumen menekan Sudah beres, atau otomatis 24 jam
    MENUNGGU_KONFIRMASI --> DILAPORKAN : konsumen menekan Belum, lapor masalah

    DILAPORKAN --> KUNJUNGAN_ULANG : admin menjadwalkan kunjungan ulang
    KUNJUNGAN_ULANG --> TEKNISI_DITEMUKAN : tukang yang sama (prioritas) menerima
    KUNJUNGAN_ULANG --> MENCARI_TEKNISI : tidak ada tukang yang cocok
    KUNJUNGAN_ULANG --> DIBATALKAN : admin menutup pesanan

    SELESAI --> [*]
    DIBATALKAN --> [*]
```

## Aturan bisnis penting

- Hanya server yang boleh mengubah status. Setiap transisi divalidasi oleh `canTransition(from, to, actor)` dari modul `shared`, dalam satu transaksi, lalu menulis `order_events` dan mem-broadcast WebSocket.
- Pelanggaran transisi dikembalikan sebagai HTTP 409 dengan kode `ILLEGAL_TRANSITION`.
- Tawaran ke tukang kedaluwarsa dalam 60 detik. Satu pesanan maksimal 5 kali tawaran; tukang yang sudah ditawar tidak ditawar lagi dalam siklus yang sama.
- `reschedule` membersihkan semua tawaran aktif lalu memulai siklus penugasan ulang dari nol.
- Kunjungan ulang memilih tukang yang sama sebagai kandidat pertama (prioritas), bukan sebagai kandidat eksklusif.
- Tidak ada uang nyata bergerak. Semua pembayaran memakai `SimulatorProvider`; "berhasil" dan "gagal" dipicu lewat endpoint admin `POST /admin/payments/{orderId}/simulate-success` dan `simulate-fail`.
