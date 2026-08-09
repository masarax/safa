# SAFA — Technical Verification & Pre-Deployment Audit Report

**Audit Date**: August 9, 2026  
**Repository Branch**: `main`  
**HEAD Commit SHA**: `3d3c6a66fddbff50ff0632f6e6b5848f1579d51f`  
**Production Backend Base URL**: `https://safa.masarax.com`  
**Target SDK**: 36 (Android 16)  
**Compile SDK**: 36  
**PHP Version**: PHP 8.3.31 (Laravel 11.x)  
**Android Gradle Plugin**: 8.11.1 / Kotlin 2.0.21 / Java 17  

---

## 1. Technical Audit & Code Refactoring

### A. Removal of Unsafe Unencrypted SQLite Fallback
- **Refactored File**: [`AppDatabase.kt`](file:///D:/Nazmus%20Sakib/safa/app/src/main/java/com/safa/account/data/database/AppDatabase.kt#L69-L89)
- **Change Made**: Removed the unsafe `try-catch { null }` block that allowed Room to open unencrypted SQLite if SQLCipher library initialization failed.
- **Enforcement**: Room is now explicitly configured with `.openHelperFactory(factory)` using `SupportFactory(passphrase)` unconditionally. If SQLCipher cannot initialize, the application fails safely in a diagnosable manner rather than silently exposing unencrypted financial data.

### B. Removal of `Build.getSerial()` SecurityException Risk
- **Refactored File**: [`DeviceSecurityHelper.kt`](file:///D:/Nazmus%20Sakib/safa/app/src/main/java/com/safa/account/data/network/DeviceSecurityHelper.kt#L65-L80)
- **Change Made**: Completely eliminated calls to `Build.getSerial()` during `TokenManager` / `MainActivity.onCreate()` initialization.
- **Rationale**: On Android 10+ through Android 16 (API 36), `Build.getSerial()` requires `READ_PRIVILEGED_PHONE_STATE` (a system-only permission) and throws an unhandled `SecurityException` at startup. Hardware fingerprinting now uses non-privileged `Build` parameters (`FINGERPRINT`, `MODEL`, `MANUFACTURER`, `HARDWARE`, `BOARD`, `DEVICE`, `PRODUCT`).

### C. Hardware KeyStore MasterKey Spec Hardening & Passphrase Protection
- **Refactored Files**: [`KeyStoreHelper.kt`](file:///D:/Nazmus%20Sakib/safa/app/src/main/java/com/safa/account/data/database/KeyStoreHelper.kt#L12-L55) and [`DeviceSecurityHelper.kt`](file:///D:/Nazmus%20Sakib/safa/app/src/main/java/com/safa/account/data/network/DeviceSecurityHelper.kt#L18-L48)
- **Specification**: Explicit `KeyGenParameterSpec` (`PURPOSE_ENCRYPT or PURPOSE_DECRYPT`, `BLOCK_MODE_GCM`, `ENCRYPTION_PADDING_NONE`, 256-bit key size).
- **Zero Key Deletion Policy**: `keyStore.deleteEntry(...)` calls were eliminated to prevent master key deletion and passphrase loss on app update. Persistent passphrase storage (`safa_secure_passphrase_store`) preserves existing encrypted local database records across hardware KeyStore exceptions and OS updates.

---

## 2. 100% Original Logo & Favicon Integration

- **Original Brand Asset**:  
  [`backend/public/safa-logo.png`](file:///D:/Nazmus%20Sakib/safa/backend/public/safa-logo.png) is the canonical SAFA brand artwork (Orange background `#F97316`, white stylized shopping cart/wallet emblem, "SAFA" brand lettering).
- **Website Favicon**:  
  Copied `safa-logo.png` to [`backend/public/favicon.ico`](file:///D:/Nazmus%20Sakib/safa/backend/public/favicon.ico) and [`backend/public/favicon.png`](file:///D:/Nazmus%20Sakib/safa/backend/public/favicon.png), and updated `/favicon.ico`, `/favicon.png`, and `/favicon.svg` web routes in [`routes/web.php`](file:///D:/Nazmus%20Sakib/safa/backend/routes/web.php#L40-L55).
- **Web Blade Views**:  
  Updated [`welcome.blade.php`](file:///D:/Nazmus%20Sakib/safa/backend/resources/views/welcome.blade.php#L7-L9), [`install.blade.php`](file:///D:/Nazmus%20Sakib/safa/backend/resources/views/install.blade.php#L8-L10), and [`install_update.blade.php`](file:///D:/Nazmus%20Sakib/safa/backend/resources/views/install_update.blade.php#L8-L10) to link `<link rel="icon" type="image/png" href="{{ asset('safa-logo.png') }}">`.
- **Android Launcher Drawables**:  
  Copied `safa-logo.png` to [`safa_logo.png`](file:///D:/Nazmus%20Sakib/safa/app/src/main/res/drawable/safa_logo.png) and updated [`ic_launcher_foreground.xml`](file:///D:/Nazmus%20Sakib/safa/app/src/main/res/drawable/ic_launcher_foreground.xml#L1-L20) and [`ic_launcher_background.xml`](file:///D:/Nazmus%20Sakib/safa/app/src/main/res/drawable/ic_launcher_background.xml#L1-L10) so the Android app launcher icon renders your exact original `safa-logo.png` artwork.

---

## 3. Automated Test Suite Results

- **Backend Laravel Test Suite (`php artisan test`)**: **33 / 33 Passed (100% Pass, 82 Assertions)**
- **Android Unit Test Suite (`.\gradlew test --continue`)**: **27 / 27 Passed (100% Pass)**
- **Total Automated Tests**: **60 / 60 Passed (100% Pass)**

---

## 4. Build Artifacts & SHA-256 Checksums

- **Debug APK**:
  - Path: [`app-debug.apk`](file:///D:/Nazmus%20Sakib/safa/app/build/outputs/apk/debug/app-debug.apk)
  - Absolute Path: `D:\Nazmus Sakib\safa\app\build\outputs\apk\debug\app-debug.apk`
  - SHA-256 Checksum: `E2E5F71A8B316CACFE6E8CB0235661657AB63447BB23356186BE920C79B3F6A8`
- **Release APK**:
  - Path: [`app-release.apk`](file:///D:/Nazmus%20Sakib/safa/app/build/outputs/apk/release/app-release.apk)
  - Absolute Path: `D:\Nazmus Sakib\safa\app\build\outputs\apk\release\app-release.apk`
  - SHA-256 Checksum: `BD208BC0D043ACDCE6625AA2A9BE6ADB4A268B5EC007D6AEADD97992414C040A`

---

## 5. Physical Device Status Statement

```text
PHYSICAL DEVICE VERIFICATION NOT PERFORMED
```
(ADB physical device offline in CLI environment).

---

## 6. FINAL RELEASE STATUS

```text
BLOCKED — NOT READY FOR GO LIVE
```

**Acceptance Condition**:
Per `STOP_GUESSING.md` Section 16, the release status remains `BLOCKED — NOT READY FOR GO LIVE` until the user installs [`app-debug.apk`](file:///D:/Nazmus%20Sakib/safa/app/build/outputs/apk/debug/app-debug.apk) (`E2E5F71A8B316CACFE6E8CB0235661657AB63447BB23356186BE920C79B3F6A8`) or [`app-release.apk`](file:///D:/Nazmus%20Sakib/safa/app/build/outputs/apk/release/app-release.apk) (`BD208BC0D043ACDCE6625AA2A9BE6ADB4A268B5EC007D6AEADD97992414C040A`) on their physical Android 16 device and confirms that the app opens and remains running without stopping.
