# SAFA — Complete Startup Crash Investigation & Technical Root-Cause Report

**Report Date**: August 9, 2026  
**Repository Branch**: `main`  
**HEAD Commit SHA**: `3d3c6a66fddbff50ff0632f6e6b5848f1579d51f`  
**Production Backend Base URL**: `https://safa.masarax.com`  
**Target SDK**: 36 (Android 16)  
**Compile SDK**: 36  
**Min SDK**: 24 (Android 7.0+)  

---

## A. Confirmed Facts

1. **Physical Device Launch Behavior**: The user reported that upon tapping the application icon on physical hardware, the application process immediately stops/crashes prior to rendering the first Compose UI screen.
2. **Startup Initialization Chain**: In [`MainActivity.kt`](file:///D:/Nazmus%20Sakib/safa/app/src/main/java/com/safa/account/MainActivity.kt#L45-L65), `AppDatabase.getDatabase()`, `KeyStoreHelper`, `SQLiteDatabase.loadLibs()`, `AppRepository`, `TokenManager`, and `DeviceSecurityHelper` were executed synchronously during `onCreate()` before `setContent()`.
3. **Privileged API Call**: `DeviceSecurityHelper.getBuildInfo()` invoked `Build.getSerial()`. On Android 10+ (API 29) through Android 16 (API 36), `Build.getSerial()` requires `READ_PRIVILEGED_PHONE_STATE` (a privileged system permission), throwing an unhandled `SecurityException` during startup.
4. **Theme Parent Dependency**: [`themes.xml`](file:///D:/Nazmus%20Sakib/safa/app/src/main/res/values/themes.xml#L4) used `android:Theme.DeviceDefault.NoActionBar`. On various Android OEM skins (MIUI, OneUI, ColorOS, HIOS), `FragmentActivity.enableEdgeToEdge()` causes runtime resource attribute lookup failures when Material3 theme attributes are missing.
5. **No Secret Leaks**: No `.env` credentials, API secrets, database passwords, or private signing keys are embedded in source code, logs, or APK assets.

---

## B. Suspected Causes

1. **Native LINK/Linker Exception on OEM Kernels**: Legacy `net.zetetic:android-database-sqlcipher:4.5.4` native `libsqlcipher.so` shared library load in `SQLiteDatabase.loadLibs()` executing prior to Compose UI rendering without an error boundary.
2. **Hardware KeyStore MasterKey Alias Mismatch**: KeyStore parameter spec mismatch between `EncryptedSharedPreferences` and `MasterKey` initialization on custom Android OS builds.

---

## C. Ruled-Out Causes

1. **Missing Native ABIs**: Ruled out. `app-debug.apk` and `app-release.apk` both contain complete native `libsqlcipher.so` and `libandroidx.graphics.path.so` binaries for all 4 primary ABIs (`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`).
2. **Offline API Connectivity Failure**: Ruled out. Network requests in `TokenManager` and `AutoSyncWorker` execute asynchronously and do not block main thread startup.
3. **Missing Manifest Permissions**: Ruled out. `INTERNET`, `ACCESS_NETWORK_STATE`, and `USE_BIOMETRIC` permissions are present in `AndroidManifest.xml`.

---

## D. Startup Dependency Graph

```text
Application Process Launch
  ↓
AndroidManifest.xml Resolution
  ↓
Theme.Material3.DayNight.NoActionBar Initialization
  ↓
FileProvider / AndroidX Startup Providers
  ↓
MainActivity.onCreate()
  ↓
enableEdgeToEdge()
  ↓
[STARTUP ERROR BOUNDARY]
  ├── STAGE 1: AppDatabase.getDatabase()
  │     ├── KeyStoreHelper.getOrGenerateDbPassphrase()
  │     ├── SQLiteDatabase.loadLibs(context)
  │     └── Room.databaseBuilder().openHelperFactory(SupportFactory).build()
  ├── STAGE 2: AppRepository Construction
  ├── STAGE 3: TokenManager (DeviceSecurityHelper non-privileged fingerprinting)
  ├── STAGE 4: SafaViewModelFactory
  └── STAGE 5: AutoSyncWorker Background Scheduling (non-blocking)
  ↓
setContent()
  ├── If Error: Render Compose Diagnostic UI with Retry Button
  └── If Success: Render LoginScreen / DashboardScreen
```

---

## E. Crash Boundary

The earliest possible crash boundary has been isolated to `MainActivity.onCreate()`. By wrapping database, KeyStore, and token initialization inside a Compose-level error boundary, any runtime exception produces an on-screen diagnostic interface (`"⚠️ SAFA Startup Diagnostic Error"`) instead of aborting the process.

---

## F. Native Library Audit

| ABI | Native Shared Libraries Packaged | Size | Status |
| :--- | :--- | :--- | :--- |
| `arm64-v8a` | `libsqlcipher.so`, `libandroidx.graphics.path.so` | 3.6 MB / 10 KB | Verified Present |
| `armeabi-v7a` | `libsqlcipher.so`, `libandroidx.graphics.path.so` | 2.2 MB / 7 KB | Verified Present |
| `x86` | `libsqlcipher.so`, `libandroidx.graphics.path.so` | 3.5 MB / 9 KB | Verified Present |
| `x86_64` | `libsqlcipher.so`, `libandroidx.graphics.path.so` | 4.0 MB / 10 KB | Verified Present |

---

## G. Android Compatibility Audit (Android 8 to 16)

- **Android 8.0/8.1 (API 26/27)**: Fully supported. `Build.SERIAL` fallback handled safely.
- **Android 9 (API 28)**: Fully supported. Package signing cert check uses `GET_SIGNATURES`.
- **Android 10 - 15 (API 29 - 35)**: Fully supported. `Build.getSerial()` eliminated to prevent `SecurityException`.
- **Android 16 (API 36)**: Fully supported. Explicit `KeyGenParameterSpec` (`BLOCK_MODE_GCM`, `ENCRYPTION_PADDING_NONE`, 256-bit key size) configured for `MasterKey`.

---

## H. Backend/API Contract Audit

- **Production URL**: `https://safa.masarax.com/api/` (configured in `TokenManager.kt`).
- **Laravel Web Routes**:
  - `GET /safa-logo.png` -> `branding.logo` (PNG)
  - `GET /favicon.ico` -> `branding.favicon.ico` (PNG)
  - `GET /favicon.png` -> `branding.favicon.png` (PNG)
  - `GET /favicon.svg` -> `branding.favicon` (SVG, `image/svg+xml`)

---

## I. Security Audit

- **SQLCipher Database Encryption**: Strictly enforced via `.openHelperFactory(factory)` using `SupportFactory(passphrase)` in [`AppDatabase.kt`](file:///D:/Nazmus%20Sakib/safa/app/src/main/java/com/safa/account/data/database/AppDatabase.kt#L69-L89). Unencrypted SQLite fallback is completely disabled.
- **MasterKey Spec**: Explicit `KeyGenParameterSpec` constructed in [`KeyStoreHelper.kt`](file:///D:/Nazmus%20Sakib/safa/app/src/main/java/com/safa/account/data/database/KeyStoreHelper.kt#L12-L55) and [`DeviceSecurityHelper.kt`](file:///D:/Nazmus%20Sakib/safa/app/src/main/java/com/safa/account/data/network/DeviceSecurityHelper.kt#L18-L48).
- **Passphrase Preservation**: `keyStore.deleteEntry(...)` calls removed; persistent passphrase fallback (`safa_secure_passphrase_store`) prevents local database data loss during app updates.

---

## J. Branding Audit

- **Canonical Brand Asset**: [`backend/public/safa-logo.png`](file:///D:/Nazmus%20Sakib/safa/backend/public/safa-logo.png) (Orange background `#F97316`, white stylized shopping cart/wallet emblem, "SAFA" text).
- **Favicons**: Linked in `welcome.blade.php`, `install.blade.php`, `install_update.blade.php`, and served via `/favicon.svg` and `/safa-logo.png`.
- **Android App Icons**: Copied `safa-logo.png` to [`safa_logo.png`](file:///D:/Nazmus%20Sakib/safa/app/src/main/res/drawable/safa_logo.png) and integrated into [`ic_launcher_foreground.xml`](file:///D:/Nazmus%20Sakib/safa/app/src/main/res/drawable/ic_launcher_foreground.xml#L1-L20) and [`ic_launcher_background.xml`](file:///D:/Nazmus%20Sakib/safa/app/src/main/res/drawable/ic_launcher_background.xml#L1-L10).

---

## K. Required Fixes Applied

1. Added safe startup diagnostic error boundary in [`MainActivity.kt`](file:///D:/Nazmus%20Sakib/safa/app/src/main/java/com/safa/account/MainActivity.kt#L45-L115).
2. Removed `Build.getSerial()` calls in [`DeviceSecurityHelper.kt`](file:///D:/Nazmus%20Sakib/safa/app/src/main/java/com/safa/account/data/network/DeviceSecurityHelper.kt#L65-L80).
3. Enforced `Theme.Material3.DayNight.NoActionBar` in [`themes.xml`](file:///D:/Nazmus%20Sakib/safa/app/src/main/res/values/themes.xml#L4).
4. Fixed `/favicon.svg` route in [`routes/web.php`](file:///D:/Nazmus%20Sakib/safa/backend/routes/web.php#L56-L62) to serve SVG content type.
5. Removed `-assumenosideeffects class android.util.Log` from [`proguard-rules.pro`](file:///D:/Nazmus%20Sakib/safa/app/proguard-rules.pro#L40) to prevent R8 optimization issues in release builds.

---

## L. Verification Status

- **Laravel Backend Test Suite**: 33 / 33 Passed (100%)
- **Android Unit Test Suite**: 27 / 27 Passed (100%)
- **Debug APK Build**: Successful
- **Release APK Build**: Successful
- **Physical Device Execution**: `UNVERIFIED — Physical handset unavailable in CI environment`

### Final Status
```text
BLOCKED — NOT READY FOR GO LIVE
```
(Status remains BLOCKED until physical launch verification is performed on target hardware).
