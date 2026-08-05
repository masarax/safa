# Full Deep System Audit: Hundi Ledger (com.safa.account)

## 1. Project Context & Current State
The project is a foundational Android application aiming to become a sophisticated "Hundi" style accounting and money management system.
- **Current App ID**: `com.safa.account` (Updated from `com.aistudio.hundiledger.zrvxpt`).
- **Namespace**: `com.safa.account` (Updated from `com.example`).
- **Build System**: Gradle with KSP, Jetpack Compose BOM, Material Design 3.
- **Database**: Room Database for offline-first capabilities.
- **Networking**: Retrofit, OkHttp, Moshi for API communication.
- **Security**: AndroidX Biometric library for fingerprint/face authentication.
- **Concurrency**: Kotlin Coroutines and Flows.

## 2. Directory Structure Audit
- `app/src/main/java/`: Contains UI components, Models, DAOs, Repositories, ViewModels.
- `app/src/main/res/`: Android resources (drawables, values).
- `app/build.gradle.kts`: Configured for Compose, Room, Retrofit. Missing Hilt for DI (needs adding).
- `gradle/libs.versions.toml`: Version catalog managing dependencies.

## 3. UI Layer Current State vs Requirements
- **Current**: Minimal Compose setup. `MainActivity.kt` manages basic navigation.
- **Requirement**: A full Jetpack Compose M3 overhaul. Needs a robust `Design System` defined in `ui/theme/` (Colors, Typography, Shapes).
- **Gaps**: Lacks modular UI components (`M3Scaffold`, `M3Card`). Needs dedicated screens for Dashboard, Transactions (with advanced inputs), Settings, and Login (with Biometrics).

## 4. Data Layer & Business Logic Audit
- **Models (`Models.kt`)**: Existing models like `Customer`, `Transaction`, `Rate` lack multi-tenant `account_id` fields.
- **DAOs & Room (`AppDaos.kt`, `AppDatabase.kt`)**: Need updates to scope all queries to the active `account_id`.
- **Repository (`AppRepository.kt`)**: Needs to handle dynamic operational modes (Standard, Customer-Centric, Rate-Based).
- **Networking (`ApiService.kt`, `SyncManager.kt`)**: Needs to align with a new Laravel API backend. Current endpoints are likely stubs or missing multi-account headers.
- **State Management**: `HundiViewModel.kt` must be refactored to support state flows that react to the current operational mode and active account.

## 5. Security & Authentication Audit
- **Current**: `BiometricHelper.kt` exists as a stub/placeholder.
- **Requirement**: Full integration of `BiometricPrompt` on app launch/login, and secure storage of session tokens for the Laravel API (likely using EncryptedSharedPreferences or DataStore).

## 6. Multi-Account & Operational Modes Gap Analysis
- **Multi-Account**: The system lacks an `Account` entity. All local data must be partitioned. A global "Active Account" state must be injected into all repositories and ViewModels.
- **Operational Modes**: The app needs a preference setting (DataStore) to toggle between modes. The UI must reactively hide/show fields (e.g., hiding supplier fields in "Customer-Centric Mode") based on this flow.
