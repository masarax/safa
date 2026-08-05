# Master Proposal & Execution Roadmap
**Project:** `com.safa.account` (Hundi System)

## Executive Summary
This roadmap outlines the transformation of `com.safa.account` into an enterprise-grade Hundi management application. The architecture relies on an offline-first mobile application, protected by robust Android hardware-backed encryption, paired with a scalable, real-time Laravel backend. The accounting model strictly enforces double-entry rules.

## Execution Timeline (Phased Approach)

### Phase 1: Core Foundation & Cryptography (Weeks 1-2)
- [ ] Initialize Android app with secure Keystore configurations.
- [ ] Implement SQLCipher Room Database with biometric-derived keys.
- [ ] Set up basic CI/CD pipeline (GitHub Actions).
- [ ] Scaffold Laravel backend with multi-tenant Eloquent scopes.

### Phase 2: Ledger & Double-Entry Engine (Weeks 3-4)
- [ ] Define backend migrations (Accounts, Transactions, Journal Entries).
- [ ] Implement local Android Room equivalents.
- [ ] Build the tamper-evident ledger logic (SHA-256 block hashing).
- [ ] Enforce debit/credit validation at the database and application levels.

### Phase 3: Sync Engine & Offline Capabilities (Weeks 5-6)
- [ ] Develop the Vector Clock / LWW conflict resolution engine.
- [ ] Implement Push/Pull endpoints in Laravel API.
- [ ] Integrate background work managers in Android for silent syncing.
- [ ] Add WebSockets (Laravel Reverb / Pusher) for real-time invalidation.

### Phase 4: UI/UX & Refinement (Weeks 7-8)
- [ ] Implement Jetpack Compose UI with MVVM architecture.
- [ ] Add real-time visual indicators for sync status (Pending/Synced/Conflict).
- [ ] Field-level AES-GCM encryption for notes integration into the UI.

### Phase 5: Hardening & Production Deployment (Week 9)
- [ ] Configure R8 ProGuard obfuscation rules.
- [ ] Set up Certificate Pinning (OkHttp & Network Security Config).
- [ ] Conduct API rate-limiting audits.
- [ ] Deploy Laravel backend to production cluster with SSL.
- [ ] Publish Android APK to internal testing track.

## Risk Mitigation
1. **Data Loss:** Vector clock syncing ensures offline changes aren't arbitrarily overwritten.
2. **Device Theft:** Biometric-bound database encryption ensures local data is inaccessible if the device is rooted or stolen.
3. **Ledger Tampering:** SHA-256 transaction hashing guarantees auditability and non-repudiation.
