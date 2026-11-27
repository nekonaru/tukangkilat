# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [4.0.0] - 2026-11-27

### Added

- **Service catalog**: searchable catalog with skill filters, price and rating filters, sorting, categories and service bundles.
- **Price estimator**: computes service fee, visit fee, peak-hour surge, bundle discount, promo, voucher and membership discount before ordering.
- **Wallet payments**: pay orders from wallet balance with cashback, daily limit and automatic top up.
- **Loyalty tiers**: Silver, Gold and Platinum with discounts, priority and free visit benefits.
- **Vouchers and referrals**: admin-issued vouchers, referral codes and reward points for both parties.
- **Installments**: simulated 3 and 6 month plans with monthly payment tracking.
- **Subscription controls**: pause, resume and skip the next billing date.
- **Review interactions**: helpful votes and technician replies on reviews.
- **Order extras**: digital signature pad, per-step work checklist with photo proof, and refund requests with admin resolution.
- **Preferences backend**: theme, language, font scale, reduced motion and quiet hours stored per user, plus an offline action queue.
- Migration V10 backs all new tables and is included in export/import.

## [3.1.0] - 2026-11-27

### Added

- **Order reminders**: one-hour-before notifications for scheduled visits, with Android 13 permission handling.
- **Review photos**: consumers can attach up to five photos to a rating; technicians see them with a full-screen preview in the workbench.
- **Auto-online schedule**: technicians set weekly online windows; the maintenance tick toggles their online state for days with a schedule.
- **Chat unread badges**: direct message lists show unread counts and opening a conversation marks it as read.
- **Chat moderation**: users can report a direct message; admins can hide the message or reject the report.
- **CSV export**: admins can share orders and wallet transactions as CSV files.
- **Encrypted backup**: admins can export and restore the database with AES-GCM and a passphrase (PBKDF2, 120000 iterations).
- **Batch portfolio upload**: technicians can add up to five portfolio photos at once.

### Changed

- Database export now includes V6 to V9 tables. Version 2 and version 3 archives can still be imported.
- Order detail shows an SLA countdown to the scheduled start time.

## [3.0.0] - 2026-11-27

### Added

- **Consumer wallet and loyalty**: simulated top up, balance history, loyalty points and point redemption.
- **Subscriptions**: recurring service plans with interval, start date, next run and cancellation. Due subscriptions create orders automatically from the maintenance tick.
- **Technician workbench**: availability blocks, photo portfolio, performance stats and payout account setup. Dispatch skips technicians on blocked dates.
- **Admin control center**: dispute resolution with simulated refund, audit log, announcements visible to all users, editable system configuration and technician leaderboard.
- Migrations V6, V7 and V8 back the new feature sets and are included in export/import.

### Changed

- Announcements appear in the notification center.
- New screens follow the existing design system and screen-entrance motion.

## [2.21.0] - 2026-11-26

### Added

- **Real QR payment**: bank transfer and e-wallet payments generate a scannable QR with an EMVCo-style QRIS payload and CRC checksum. Ten national banks and five e-wallets can be selected before paying.
- **Nearby technicians**: the technician directory now receives the consumer address as the reference point and shows distance. Each card has a direct chat button.
- **Direct negotiation**: consumers and technicians can chat without an order. Technicians can send a price proposal with their own description, service, date and time; consumers accept or reject it from the Chat tab. Acceptance creates an order with the negotiated price.
- **Photo previews**: technician registration shows the selected KTP and selfie images with a zoomable preview, and the order form shows local photo previews before upload.
- **Customer-chosen time**: the order form has a time picker in addition to preset slots.

### Changed

- New migration V5 adds `direct_messages` and `proposals`; the tables are included in database export and import.
- New negotiation screens use the standard screen-entrance motion, and the payment QR fades in smoothly.

## [2.20.0] - 2026-11-26

### Changed

- **Faster startup**: demo password hashes are precomputed instead of running five BCrypt cost-12 hashes inside the loading gate. The system uploader account is now created only when a public registration photo is uploaded.
- **Lighter requests**: the AndroidKeyStore key is cached, interceptors read only the access token, and the server address is cached for image requests. This removes repeated KeyStore reads and token decryption per request.
- **Native feel**: reduced-motion users no longer get continuous skeleton animation, time formatters are hoisted out of composition, geocoding runs off the main thread, and payment/account collectors no longer stack on reload.

### Fixed

- Orders can now be cancelled after a technician is found, and a technician can be released back to searching from the UI.
- Closing an issue report no longer strands the consumer order in `DILAPORKAN`.
- Demo technicians start with a location near the seeded address and dispatch accepts a slightly older fix, so offers work on a fresh install.
- Server validation messages are now shown instead of a generic error, logout always clears the local session, and cancel/submit actions update state immediately.
- The searching screen handles cancellation and failed loads instead of spinning forever, and technician job lists include `MENUNGGU_KONFIRMASI`.

## [2.19.0] - 2026-11-26

### Fixed

- **Tab navigation**: switching tabs from any screen now always emits a navigation event. The previous `popUpTo(target)` option made the call a no-op once that tab was already in the back stack, which left the user stuck and forced a back press.
- **Text fields**: an empty label no longer hides the placeholder. Username and Password hints are visible before typing, with an explicit placeholder color.
- **Account screen**: the profile header is compact, rows are grouped under Akun and Teknisi sections, and spacing follows the spacing tokens.
- **Login button**: it now spans the form width and uses the shared loading state instead of a separate spinner.
- **Startup smoothness**: the continuous loading animation was removed while the embedded backend boots, reducing frame drops on slower devices.

## [2.18.0] - 2026-11-26

### Fixed

- **Startup crash**: Room rejected a migration whose start version was also registered in `fallbackToDestructiveMigrationFrom`. Version 3 now uses the explicit `3 -> 4 -> 5` path and only older caches are rebuilt.
- **Embedded backend startup**: legacy schema detection no longer asks H2 for index metadata with a null table name. It queries the `payments` table, with a regression test.
- **Light theme brand mark**: the white brand icon was invisible on the light home screen. It is now tinted with the primary color.

## [2.17.0] - 2026-11-26

### Added

- **Server connection check and help**: Settings now has a one-tap `/health` probe and an expandable FAQ covering ordering, missing technicians and LAN setup.
- **Notification filter**: the notification center can show all items or only unread ones.
- Public registration photo upload so technician sign-up can complete before an account exists.

### Fixed

- Dead controls are wired: notification rows open their order, the chat bell opens notifications, the about row opens Settings, "Cari teknisi pengganti" and "Lihat pendapatan" now navigate, and "Tentang" is no longer inert.
- Reschedule is disabled when the order failed to load, and the call button is hidden when no phone number exists.
- Technician location now uses fresh fused updates while online instead of a possibly stale last fix, so dispatch can actually find them.
- Photos now load with the bearer token and the configured server address through a shared Coil loader. Admin verification URLs are normalized so the `/photos/` prefix is not doubled.
- Refresh is single-flight; concurrent 401s share one call and transient server errors no longer force logout.
- Room cache rebuilds safely when upgrading from very old versions.
- Searching and order status cancel old pollers and WebSocket collectors; the searching screen navigates only once.
- Payment retry voids the previous pending payment, the countdown uses the real expiry timestamp, and admin snackbars and the orders search query survive refresh.
- The map only rebuilds markers when their values change, the offer countdown is local to each card, and photo reads and geocoding moved off the main thread.

## [2.16.0] - 2026-11-26

### Added

- **Theme switcher and Settings**: choose System, Light or Dark appearance from a new Settings screen. The same screen manages the server address and explains local mode.
- **Server-side admin analytics**: `GET /admin/analytics` aggregates all orders instead of the newest 50, with an integration test. The admin summary uses it and falls back to the old client calculation on older servers.
- **Automatic no-technician resolution**: after the search window ends, the order is cancelled by the system and a paid order is refunded.

### Fixed

- Password fields again apply the masking transformation and keyboard options, so the show/hide toggle works.
- The role-based bottom navigation is now actually wired, with a reachable Chat tab and chat list screen.
- Text on light containers uses on-container roles so it stays readable in both themes.
- Snackbar messages on the searching and status screens are displayed and then cleared, so repeated messages re-trigger.
- Five screens no longer fetch twice on first composition. Chat cancels its previous collector and poll before starting new ones, and scrolls only when the last message changes.
- Identity resets during database import now run after the data commit, and the import screen stays mounted so the restart and session clear actually execute.
- Legacy databases adopt only the migrations they really contain.
- A second pending payment is rejected, the expiry sweep skips conflicting rows, ratings no longer reset the seeded job count, and ETA is based on the last known technician location.
- Embedded readiness no longer blocks the UI when a remote LAN server is configured.

## [2.15.0] - 2026-11-26

### Changed

- **Design system overhaul**: light and dark Material 3 color schemes from the Figma foundation, Plus Jakarta Sans typography, spacing and radius tokens, and a theme extension for success and warning colors.
- Reusable components with light and dark previews: buttons, fields, status chips, service/technician/order cards, price summary, rating stars, timeline, chat bubble, notification item, state views, job offer card, snackbar, dialogs, sheet, filter chip, skeleton, availability switch, FAB, top app bar and role-based navigation bar.
- All screens now resolve legacy UI names through the new tokens, use the role-based navigation bar, and use Indonesian "kamu" wording.
- Action icons have content descriptions and payment status changes are announced to accessibility services.

## [2.14.0] - 2026-11-26

### Added

- **LAN server for demos**: `start-lan-server.bat` starts the backend on a laptop with a local H2 database and prints the laptop LAN IP. Several phones on the same WiFi can share that server. See `docs/DEPLOY_LAN.md`.
- H2 is now a runtime dependency of the backend so the laptop server can use a local database file.

### Changed

- The Android network security config allows plain HTTP to private LAN addresses, which is required for the laptop demo server.

## [2.13.0] - 2026-11-26

### Added

- **Online backend option**: the app can connect to one shared Ktor server so orders placed on one device appear on another. Includes a root `Dockerfile`, backend distribution packaging and `docs/DEPLOY_ONLINE.md`.

### Fixed

- **Startup regression**: H2 in PostgreSQL mode exposes a system table named `users`. Schema detection now checks only the `public` schema, so a fresh install installs migrations correctly. Installs that were left with version markers but no schema repair themselves on the next launch. Detection lives in a JVM helper with a regression test.
- **WebSocket address**: the WebSocket client now uses the server address configured on the login screen, including `wss` for HTTPS, and reconnects when that address changes.

## [2.12.0] - 2026-11-26

### Added

- **Technician directory**: consumers can browse verified technicians, search by name or skill, filter by skill, see rating and job count, and save or remove favorites. New `GET /consumer/technicians` endpoint with an integration test.
- **Address editor**: the Akun screen can now add and edit saved addresses, including a **Gunakan lokasi saya** GPS action with geocoding and manual coordinate fallback.
- **Persisted notifications**: the notification center now survives process death. A Room migration adds a `cached_notifications` table without clearing favorites or cached orders.

### Fixed

- **Database transfer**: export and import now include `refresh_tokens`, reject unsafe photo names and column names, bound archive sizes, remove stale photos, and keep the schema consistent.
- **Embedded backend**: migrations are tracked in a version table, applied with a version marker, idempotent statements and a comment-aware SQL splitter; existing installs are adopted without re-running old migrations. Startup failures now show a retry screen.
- **Backend correctness**: order codes advance on collision, duplicate offers and repeated no-technician notices are prevented, withdrawals lock the technician row, refresh-token rotation locks the token row, stale locations are cleared on going offline, and the rate limiter evicts old buckets.
- **Client fixes**: silent refresh uses the configured server address, payment retry navigates after success, withdrawal feedback is no longer overwritten, technician home shows today's earnings, and slots are no longer fetched twice.
- **Crash handling**: uncaught exceptions are logged and delegated to the platform handler instead of being silently swallowed.

## [2.11.0] - 2026-11-26

### Added

- **Reorder**: the receipt screen now has a **Pesan lagi layanan ini** button that opens the order form with the same service preselected. Works from the rating flow and from the completed-orders history.

## [2.10.0] - 2026-11-26

### Added

- **Favorite technicians**: from a completed order's receipt, consumers can tap **Simpan teknisi favorit**. The favorites list is available in the Akun tab and individual entries can be removed. Data is stored locally in Room, with no new endpoint.
- Cache database upgraded to version 3 with a `favorite_technicians` table.

## [2.9.0] - 2026-11-26

### Added

- **Create services**: the admin panel can now add a new service (name, starting price, visit fee) with a **Layanan baru** button, in addition to editing existing ones. Adds `POST /admin/services` and an integration test.
- **Camera for problem reports**: the Lapor masalah screen now also has an **Ambil foto** button, completing K-14 alongside the order form and finish screen.

## [2.8.0] - 2026-11-25

### Added

- **Admin analytics**: the summary now shows a bar chart of orders over the last 7 days and the three most common order statuses. The data is computed from the existing admin order list, with no new endpoint or chart dependency.

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

[4.0.0]: https://github.com/nekonaru/tukangkilat/releases/tag/v4.0.0
[3.1.0]: https://github.com/nekonaru/tukangkilat/releases/tag/v3.1.0
[3.0.0]: https://github.com/nekonaru/tukangkilat/releases/tag/v3.0.0
[2.21.0]: https://github.com/nekonaru/tukangkilat/releases/tag/v2.21.0
[2.20.0]: https://github.com/nekonaru/tukangkilat/releases/tag/v2.20.0
[2.19.0]: https://github.com/nekonaru/tukangkilat/releases/tag/v2.19.0
[2.18.0]: https://github.com/nekonaru/tukangkilat/releases/tag/v2.18.0
[2.17.0]: https://github.com/nekonaru/tukangkilat/releases/tag/v2.17.0
[2.16.0]: https://github.com/nekonaru/tukangkilat/releases/tag/v2.16.0
[2.15.0]: https://github.com/nekonaru/tukangkilat/releases/tag/v2.15.0
[2.14.0]: https://github.com/nekonaru/tukangkilat/releases/tag/v2.14.0
[2.13.0]: https://github.com/nekonaru/tukangkilat/releases/tag/v2.13.0
[2.12.0]: https://github.com/nekonaru/tukangkilat/releases/tag/v2.12.0
[2.11.0]: https://github.com/nekonaru/tukangkilat/releases/tag/v2.11.0
[2.10.0]: https://github.com/nekonaru/tukangkilat/releases/tag/v2.10.0
[2.9.0]: https://github.com/nekonaru/tukangkilat/releases/tag/v2.9.0
[2.8.0]: https://github.com/nekonaru/tukangkilat/releases/tag/v2.8.0
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
