# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2026-11-19

### Added

- **Consumer app**: 12 screens (login, home, order form, payment, searching, order status, work done, confirmation, rating, problem report, orders tab, account) wired to the real backend.
- **Technician app**: home with online/offline toggle, real-time job offers with 60-second countdown, ordered job actions (depart, arrive, start, finish with photo proof), earnings with 15% platform commission, account management.
- **Admin panel**: summary, technician verification (KTP and selfie), problem reports with revisit scheduling, orders filter, payment simulation, service management.
- **Backend**: Ktor with JWT auth (15-minute access tokens with 14-day refresh rotation), RBAC on every route, bcrypt password hashing with brute-force lockout (5 attempts = 5-minute lock), order state machine with actor-aware transitions, dispatcher that assigns the nearest online technician with the right skill within radius, promo codes, WebSocket events with polling fallback, and Flyway migrations.
- **Demo data**: seeded services (Perbaikan Listrik, Pipa Bocor, Servis AC), slots (09.00, 10.00-11.00, 13.00), promo `HEMAT25`, demo accounts for admin, consumer, and two technicians.
- **Verification**: backend integration tests covering auth, RBAC, full order lifecycle, payment simulator, cancellation rules, and access isolation; shared module state machine tests; Android debug build in CI; gitleaks secret scan.

[1.0.0]: https://github.com/nekonaru/tukangkilat/releases/tag/v1.0.0
