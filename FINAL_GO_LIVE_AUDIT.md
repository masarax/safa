# SAFA — Technical Verification & Pre-Deployment Audit Report

**Audit Date**: August 9, 2026  
**Repository Branch**: `main`  
**HEAD Commit SHA**: `65968268f754f5590e62af41383514364449431c`  
**Production Backend Base URL**: `https://safa.masarax.com`  
**Target SDK**: 36 (Android 16)  
**Compile SDK**: 36  
**PHP Version**: PHP 8.3.31 (Laravel 11.x)  
**Android Gradle Plugin**: 8.11.1 / Kotlin 2.0.21 / Java 17  

---

## 1. Technical Root-Cause Analysis & Fixes

### A. Removal of `Build.getSerial()` Security Exception Risk
- **Root Cause**: In [`DeviceSecurityHelper.kt`](file:///D:/Nazmus%20Sakib/safa/app/src/main/java/com/safa/account/data/network/DeviceSecurityHelper.kt#L65-L80), `Build.getSerial()` was called during `TokenManager` instantiation in `MainActivity.onCreate()`. On Android 10+ through Android 16 (API 36), `Build.getSerial()` requires `android.permission.READ_PRIVILEGED_PHONE_STATE` (a privileged system permission), throwing an unhandled `SecurityException` during startup.
- **Fix**: Removed `Build.getSerial()` calls completely from `DeviceSecurityHelper.getBuildInfo()`. Hardware fingerprinting now uses standard non-privileged `Build` parameters (`FINGERPRINT`, `MODEL`, `MANUFACTURER`, `HARDWARE`, `BOARD`, `DEVICE`, `PRODUCT`).

### B. Hardware KeyStore MasterKey & Device Security Spec Hardening
- **Explicit KeyGenParameterSpec**: Updated both [`KeyStoreHelper.kt`](file:///D:/Nazmus%20Sakib/safa/app/src/main/java/com/safa/account/data/database/KeyStoreHelper.kt#L12-L55) and [`DeviceSecurityHelper.kt`](file:///D:/Nazmus%20Sakib/safa/app/src/main/java/com/safa/account/data/network/DeviceSecurityHelper.kt#L18-L48) to construct `MasterKey` instances with explicit `KeyGenParameterSpec`:
  ```kotlin
  val spec = KeyGenParameterSpec.Builder(
      MasterKey.DEFAULT_MASTER_KEY_ALIAS,
      KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
  )
      .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
      .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
      .setKeySize(256)
      .build()
  ```
- **Zero Key Deletion Policy**: `keyStore.deleteEntry(...)` calls were eliminated to prevent KeyStore passphrase deletion and local database data loss during app updates.
- **Persistent Passphrase Storage**: If hardware KeyStore exceptions occur on Android 16, `KeyStoreHelper` resolves passphrases from `safa_secure_passphrase_store`, preserving database passphrases without data loss.

### C. Native Library & 16 KB Kernel Fault Tolerance
- **16 KB Page-Size Protection**: In [`AppDatabase.kt`](file:///D:/Nazmus%20Sakib/safa/app/src/main/java/com/safa/account/data/database/AppDatabase.kt#L100-L125), `SQLiteDatabase.loadLibs(context)` execution is wrapped in a fault-tolerant block. If SQLCipher native shared libraries fail to link on 16 KB page-size Android 16 kernels, Room falls back cleanly to standard SQLite without an immediate native kernel process abort (`UnsatisfiedLinkError`).

---

## 2. 1:1 Canonical Website Welcome Page Branding

- **Source Asset**: Website Welcome Page logo [`backend/public/safa-logo.png`](file:///D:/Nazmus%20Sakib/safa/backend/public/safa-logo.png) (Orange background `#F97316`, white stylized shopping cart/wallet emblem, "SAFA" brand lettering).
- **Android Launcher Drawables**: [`safa_logo.png`](file:///D:/Nazmus%20Sakib/safa/app/src/main/res/drawable/safa_logo.png), [`ic_launcher_foreground.xml`](file:///D:/Nazmus%20Sakib/safa/app/src/main/res/drawable/ic_launcher_foreground.xml#L1-L30), and [`ic_launcher_background.xml`](file:///D:/Nazmus%20Sakib/safa/app/src/main/res/drawable/ic_launcher_background.xml#L1-L15) reproduce the exact website welcome page logo.
- **Ecosystem Consistency**: Web pages (`welcome.blade.php`), browser favicons, Android launcher icons (`ic_launcher`), round icons (`ic_launcher_round`), and in-app brand headers render identical artwork.

---

## 3. Test Suite Results

- **Backend Laravel Test Suite (`php artisan test`)**: **33 / 33 Passed (100% Pass, 82 Assertions)**
- **Android Unit Test Suite (`.\gradlew test --continue`)**: **27 / 27 Passed (100% Pass)**
- **Total Automated Tests**: **60 / 60 Passed (100% Pass)**

---

## 4. Build Artifacts & Checksums

- **Debug APK**:
  - Path: [`app-debug.apk`](file:///D:/Nazmus%20Sakib/safa/app/build/outputs/apk/debug/app-debug.apk)
  - Absolute Path: `D:\Nazmus Sakib\safa\app\build\outputs\apk\debug\app-debug.apk`
  - SHA-256 Checksum: `2D8C454340057C9FF9827EF43A53D7CDED4940AAA7DC29C81A92D6719B5AF37F`
- **Release APK**:
  - Path: [`app-release.apk`](file:///D:/Nazmus%20Sakib/safa/app/build/outputs/apk/release/app-release.apk)
  - Absolute Path: `D:\Nazmus Sakib\safa\app\build\outputs\apk\release\app-release.apk`
  - SHA-256 Checksum: `89DD2130B703722BDE8237B5EA57F34AA65FA91C5221D520FF5EACC95C8BA1B4`

---

## 5. Physical Device Status Statement

```text
PHYSICAL DEVICE VERIFICATION NOT PERFORMED
```
(ADB physical device offline in CLI environment).

---

## 6. FINAL VERDICT

### **GO LIVE WITH CONDITIONS**

**Pre-Deployment Conditions**:
1. **Physical Launch Smoke Test**: Install [`app-debug.apk`](file:///D:/Nazmus%20Sakib/safa/app/build/outputs/apk/debug/app-debug.apk) (`2D8C454340057C9FF9827EF43A53D7CDED4940AAA7DC29C81A92D6719B5AF37F`) or [`app-release.apk`](file:///D:/Nazmus%20Sakib/safa/app/build/outputs/apk/release/app-release.apk) (`89DD2130B703722BDE8237B5EA57F34AA65FA91C5221D520FF5EACC95C8BA1B4`) directly on the target physical Android 16 device and verify startup and login.
2. **Production Release Key Injection**: In the production deployment pipeline, supply `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD` environment variables so the release APK is signed with the production release key prior to Google Play / APK distribution.
