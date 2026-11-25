# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.7.0] - 2026-11-25

### Changed

- **Offline-first Beranda (PRD non-functional)**: services and addresses are now stored in the Room cache alongside orders. Beranda renders cached data immediately, then refreshes from the backend. If the backend cannot be reached, the last data stays visible.
- Cache database upgraded to version 2 with `cached_services` and `cached_addresses`. `fallbackToDestructiveMigration` clears only the cache on upgrade; embedded backend data is unaffected.

## [2.6.0] - 2026-11-25

### Added

- **Order search**: the Pesanan tab now has a search field for order code or service name. It filters both Aktif and Riwayat tabs.

### Changed

- **Accessibility**: `TKTextButton` now has a minimum 48 dp touch target, per the PRD non-functional criteria.

## [2.5.0] - 2026-11-25

### Added

- **Camera capture (K-05, T-08)**: the order form and technician finish screen now have an **Ambil foto** button using the device camera through `ACTION_IMAGE_CAPTURE` and `FileProvider`. The photo is uploaded through the same backend flow as gallery photos, with no extra camera permission.

## [2.4.0] - 2026-11-25

### Added

- **Technician withdrawals (T-11)**: **Tarik saldo** now creates a real withdrawal record on the backend instead of only a local snackbar. The balance decreases and withdrawal history appears on the Pendapatan screen. Minimum withdrawal is Rp1 and it cannot exceed the balance.
- Migration `V4__withdrawals.sql` adds the `withdrawals` table with technician and time indexes. The table is included in database export and import.

## [2.3.0] - 2026-11-25

### Added

- **Notification center**: order status changes, job offers, expired offers and chat messages are now recorded as in-app notifications. Open **Notifikasi** from the consumer account, technician account or admin panel. Includes **Tandai dibaca** and **Hapus semua**.
- This satisfies the PRD K-18 and T-14 fallback: without FCM, WebSocket events still produce notifications users can see.

## [2.2.0] - 2026-11-24

### Changed

- **Design and motion**: buttons and cards now give tactile spring press feedback. Login, Beranda, Pesanan and admin panel have a subtle entrance (fade plus 18 px slide, 300 ms). The brand mark pulses gently on the "Menyiapkan aplikasi..." screen.
- All animation uses built-in Compose primitives with no new dependency, and stops automatically when the user enables Android's "remove animations" setting.
- Only alpha and transform properties animate, so no layout pass is triggered and the app stays light.

## [2.1.0] - 2026-11-24

### Added

- **PDF receipt (K-20)**: **Bagikan struk PDF** on the receipt screen creates a PDF with Android's built-in `PdfDocument` and shares it through `FileProvider`. No new dependency.
- **Periodic location (T-03, T-07)**: technicians report their location every 15 seconds while online and every 5 seconds while `DALAM_PERJALANAN`, as required by the PRD tracking flow.

## [2.0.3] - 2026-11-24

### Changed

- **Native**: Android 12+ now uses the system splash screen with the brand mark on Biru malam. No extra dependency.
- **Smoother**: H2 is tuned with an 8 MB cache and 500 ms write delay. Backend initialization time is logged in debug builds for monitoring.
- `themes.xml` is split by Android version so splash attributes are used only on API 31+.

## [2.0.2] - 2026-11-24

### Bug fixes

- **Install compatibility**: the APK is now signed with v1, v2 and v3 schemes together. Some OEM installers reject v2-only APKs with "package appears invalid".
- If an older build is still installed, especially a debug APK, uninstall it first because debug and release signatures differ.

### Changed

- **Native**: enabled predictive back callback on Android 13+.
- Backend error logging is written to logcat only in debug builds.

## [2.0.1] - 2026-11-23

### Changed

- **Performance and size**: releases now use R8 and resource shrinking. APK size drops from about 23 MB to 7.6 MB.
- **Lighter**: unused permissions removed (camera, notifications, foreground service, background location). H2 trace file disabled so no trace file is created on the device.
- **Smoother**: the server address is cached in memory, so no DataStore read happens on every API request. The "Menyiapkan aplikasi..." screen now shows the brand mark.
- **Stability**: the Android timestamp parser accepts H2's space separator and offset without colon. Stale 401 responses no longer crash the app. R8 rules keep JWT and bcrypt working after minification.

## [2.0.0] - 2026-11-23

### Added

- **Embedded backend**: Ktor, Exposed and H2 in PostgreSQL mode now run inside the app process. Docker, PostgreSQL and a separate server are no longer needed to use the app.
- **Database export and import**: the admin panel has **Ekspor database** and **Impor database**. A zip archive holds all table rows in `database.json` plus related photos. Import validates the snapshot, replaces all data inside a rollbackable transaction and clears the session so the user signs in again.

### Changed

- Default API address is now `http://127.0.0.1:8080`. Cleartext is allowed only for localhost, the emulator and `.lan`.
- Backend initialization runs in the background behind a "Menyiapkan aplikasi..." gate to avoid ANRs.
- An Android-specific timestamp column type uses `getString`, because `ResultSet.getObject(index, Class)` is unavailable in Android's `java.sql`.
- The `backend` module still runs as a JVM server for development and tests.

## [1.3.0] - 2026-11-21

### Added

- **Server address**: an **Atur alamat server** link on the login screen lets one APK work on both emulator and a physical phone. The address is stored on the device and applied to every request by an OkHttp interceptor. Leave the field empty to restore `http://10.0.2.2:8080`.

### Fixed

- **Error messages**: raw technical text (for example "failed to connect to /10.0.2.2 ... after 15000ms") no longer appears in the UI. Every screen now uses a short, clear Indonesian message.
- **Login screen**: username and password fields no longer turn red on a network failure. Red borders only appear for wrong credentials or a locked account.
- **Network**: cleartext is allowed for the configured local backend address, as required for the demo.

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
