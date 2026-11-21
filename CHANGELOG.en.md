# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.2.6] - 2026-11-21

### Improvements

- **Performance**: consumer Beranda now loads services, addresses and orders in parallel; the order form loads services and addresses in parallel; technician home loads profile, orders, earnings and offers in parallel. Dashboard load time is now bound by the slowest request instead of the sum of requests.

## [1.2.5] - 2026-11-20

### Changed

- **Design screen 02 (Beranda)**: the location row now shows the address label followed by the full address, for example "Rumah · Jl. Tebet Barat Dalam...", per the draft.
- **Integration tests**: two new tests cover a failed payment retried with another method, plus address CRUD and rescheduling before a technician is assigned. Backend test count is now 20.

## [1.2.4] - 2026-11-20

### Changed

- **Backend**: migration `V3__dispatch_indexes.sql` adds composite indexes for maintenance queries (`payments(status, expires_at)`, `orders(status, updated_at)`), job offers (`job_offers(order_id, status, expires_at)`), and the search for verified online technicians. Verified on PostgreSQL and H2.
- **Design screen 10 (Payment failed)**: red card icon on a pale red rounded square, per the draft.
- **Audit**: report a problem through revisit (re-offer to the same technician) and order cancellation are now covered by 8 end-to-end checks.

## [1.2.3] - 2026-11-20

### Changed

- **Design screen 08 (Rating)**: the star count label is now Oranye kilat, quick tags use an orange border with orange text, and the receipt card uses pale green with the check inside the card, per the draft.
- **Design screen 12 (Report a problem)**: the selected reason now has an orange border; photo upload is now a full-width dashed box with "Maksimal 3 foto · JPG atau PNG", per the draft.
- **Docs**: `docs/DESIGN_AUDIT.md` uses Biru malam `#1B274D` and records the 08, 12 and time slot chip adjustments.

## [1.2.2] - 2026-11-20

### Fixed

- **Backend**: automatic maintenance (payment expiry and auto-complete after 24 hours) now runs inside a database transaction. Previously both functions threw `IllegalStateException: No transaction in context` on every tick, so expired payments and stale confirmations were never processed. The backend log is clean.
- **App**: Beranda, Pesanan, technician home and job list, and the admin summary now reload data when the app returns to the foreground. Fresh status appears without restarting the app.
- **Order form**: available time slot chips now use Biru malam text on a white card with a border, per the design. Previously white text on a white card made the times unreadable.
- **Tests**: new integration test for both maintenance functions. `.gitignore` now ignores the `backend/uploads/` folder created by photo uploads.

## [1.2.1] - 2026-11-19

### Changed

- Launcher icon, adaptive icon (foreground and monochrome) and the in-app brand mark are re-rendered from a high-resolution master with sharper edges.
- Payment screen: the selected payment method card now has an Oranye kilat border with an orange icon, per the design.
- README: brand logo added to the header; screenshots (Beranda, Pesanan, Akun) refreshed to the new look; the `assets/` folder is listed in the project structure.

## [1.2.0] - 2026-11-19

### Added

- **Visual identity**: the app icon now uses the exact brand mark (Atap Kilat): square and round launcher icons, adaptive icon with a monochrome layer for Android 13+ themed icons.
- **Default language**: documentation and project metadata are in Bahasa Indonesia (`README.md`, `CHANGELOG.md` are the primary documents; English versions live in `README.en.md` and `CHANGELOG.en.md`); the repository description is in Bahasa Indonesia.

### Changed

- **UI**: the login screen (Bolt icon + two-tone "Tukang"/"Kilat" wordmark, VerifiedUser icon + tagline per the design), the home screen (two-tone wordmark, service cards without chevron).
- **Palette**: Biru malam `#1B274D`, Biru kabut `#B9CAD6` per the brand sheet (previous values were not exact matches).

## [1.1.0] - 2026-11-19

### Added

- **Technician**: "Ulasan saya" section on the account screen listing reviews (stars, tags, comment and date) received from consumers.
- **Technician**: "Jadwal berikutnya" section on the home screen listing upcoming scheduled jobs.
- **Technician**: reviews endpoint `GET /technician/reviews` with integration test.
- **Admin**: promo code management (`GET/PUT /admin/promo-codes`) with list, create, activate and deactivate; discount shown live in consumer payment flow.
- **Consumer**: "Bagikan struk" share button on the receipt screen.
- Backend tests: promo lifecycle (create, disable, reject at payment), RBAC for promo admin routes, technician reviews after rating.

## [1.0.0] - 2026-11-19

### Added

- **Consumer app**: 12 screens (login, home, order form, payment, searching, order status, work done, confirmation, rating, problem report, orders tab, account) wired to the real backend.
- **Technician app**: home with online/offline toggle, real-time job offers with 60-second countdown, ordered job actions (depart, arrive, start, finish with photo proof), earnings with 15% platform commission, account management.
- **Admin panel**: summary, technician verification (KTP and selfie), problem reports with revisit scheduling, orders filter, payment simulation, service management.
- **Backend**: Ktor with JWT auth (15-minute access tokens with 14-day refresh rotation), RBAC on every route, bcrypt password hashing with brute-force lockout (5 attempts = 5-minute lock), order state machine with actor-aware transitions, dispatcher that assigns the nearest online technician with the right skill within radius, promo codes, WebSocket events with polling fallback, and Flyway migrations.
- **Demo data**: seeded services (Perbaikan Listrik, Pipa Bocor, Servis AC), slots (09.00, 10.00-11.00, 13.00), promo `HEMAT25`, demo accounts for admin, consumer, and two technicians.
- **Verification**: backend integration tests covering auth, RBAC, full order lifecycle, payment simulator, cancellation rules, and access isolation; shared module state machine tests; Android debug build in CI; gitleaks secret scan.

[1.2.6]: https://github.com/nekonaru/tukangkilat/releases/tag/v1.2.6
[1.2.5]: https://github.com/nekonaru/tukangkilat/releases/tag/v1.2.5
[1.2.4]: https://github.com/nekonaru/tukangkilat/releases/tag/v1.2.4
[1.2.3]: https://github.com/nekonaru/tukangkilat/releases/tag/v1.2.3
[1.2.2]: https://github.com/nekonaru/tukangkilat/releases/tag/v1.2.2
[1.2.1]: https://github.com/nekonaru/tukangkilat/releases/tag/v1.2.1
[1.2.0]: https://github.com/nekonaru/tukangkilat/releases/tag/v1.2.0
[1.1.0]: https://github.com/nekonaru/tukangkilat/releases/tag/v1.1.0
[1.0.0]: https://github.com/nekonaru/tukangkilat/releases/tag/v1.0.0
