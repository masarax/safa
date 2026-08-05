# Comprehensive System Audit & Strategic Development Plan

## Executive Summary
This document provides a thorough audit of the existing **Hundi Ledger** Android application codebase and presents a detailed, phased strategic plan to upgrade it into a professional, multi-account financial management application using Jetpack Compose with Material Design 3 (M3) and a Laravel API backend.
The package application ID has been successfully migrated to `com.safa.account`.

---

## Phase 1: Deep Codebase Audit

### 1. Project Structure & Build Configuration
* **Namespace & Package**: `com.safa.account` (Updated).
* **Target SDK / Min SDK**: Android SDK target version 36 / minimum SDK version 24.
* **Build System & Dependencies**: Gradle with KSP (`libs.versions.toml`). Key libraries present:
  * **UI**: Jetpack Compose BOM, Material Design 3 (`androidx.compose.material3`), Material Icons Extended, Coil.
  * **Database**: Room database (`androidx.room.ktx`, `androidx.room.runtime`, KSP compiler).
  * **Networking**: Retrofit, Moshi, OkHttp, Logging Interceptor.
  * **Security**: AndroidX Biometric (`androidx.biometric`).
  * **Concurrency**: Kotlinx Coroutines & Flow.

### 2. UI Layer Audit
* **Current Framework**: Jetpack Compose.
* **Screen Architecture**: Needs migration to a robust Navigation component (e.g., Compose Navigation or Voyager) rather than simple state transitions in MainActivity.
* **Design System**: Themes exist in `ui/theme/`, but require full M3 standardization, dynamic palettes, and dark mode optimizations as defined in `UI_UX_DESIGN_SYSTEM_AND_SCREENS.md`.

### 3. Data Layer & Business Logic Audit
* **Database Schema (`data/model/Models.kt` & `data/database/`)**: Room entities exist for `Customer`, `Transaction`, `Rate`, and `AuditLog`. Requires `account_id` fields for multi-tenant isolation.
* **Repository & API Client**: `AppRepository.kt` connects Room DAOs with offline sync flags. `ApiService.kt` and `SyncManager.kt` manage API syncing. Need heavy refactoring for offline-first UUID-based sync.

---

## Phase 2: Strategic Development Plan

1. **Architecture & Hilt Setup**: Introduce Dagger Hilt for dependency injection across database, repository, and networking layers.
2. **Multi-Account Database Schema Migration**: Add `AccountEntity` and partition all entity records by `account_id`.
3. **Material Design 3 (M3) UI/UX Overhaul**: Standardize typography, M3 color tokens, and create modular components (`M3Scaffold`, `M3StatCard`, `M3TransactionRow`).
4. **Dynamic Operational Modes & Biometrics**: Implement dynamic mode filtering (Standard, Customer-Centric, Customer & Supplier, Rate-Based) and integrate `BiometricPrompt` authentication.
5. **Laravel API Integration**: Expand Retrofit endpoints to sync accounts, customers, suppliers, and transactions with a Laravel REST backend.
6. **Testing Strategy**: Comprehensive unit tests for ViewModels/Repositories and UI Compose Espresso/Roborazzi tests.

---

## Associated Documentation
Please refer to the following comprehensive guides generated in the `.gemini/` directory for detailed specifications:
- `FULL_DEEP_SYSTEM_AUDIT.md`: In-depth analysis of current vs required states.
- `ARCHITECTURE_AND_ROADMAP.md`: High-level architecture and phased rollout plan.
- `UI_UX_DESIGN_SYSTEM_AND_SCREENS.md`: M3 design principles and screen-by-screen breakdown.
- `LARAVEL_API_AND_ECOSYSTEM_SPEC.md`: Backend contracts, multi-tenant rules, and sync engine design.
- `SKILL_RULES_AND_DEVELOPMENT_GUIDE.md`: Coding standards, Compose best practices, and Git strategy.
