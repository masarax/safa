# SAFA — Forensic Startup Root Cause Report

**Report Date**: August 9, 2026  
**Repository Branch**: `main`  
**HEAD Commit SHA**: `96258988b0d6f3930d0e6291518c4466418e6469`  
**Target SDK**: 36 (Android 16)  
**Compile SDK**: 36  
**Min SDK**: 24 (Android 7.0+)  

---

## 1. Physical Device Verification Status

```text
ROOT CAUSE NOT YET PROVEN (Physical handset unavailable in CI environment)
```

---

## 2. Technical Vulnerability Analysis & Architectural Fixes Applied

### A. Fix: Unprotected `enableEdgeToEdge()` outside Startup Error Boundary
- **File & Line**: [`MainActivity.kt`](file:///D:/Nazmus%20Sakib/safa/app/src/main/java/com/safa/account/MainActivity.kt#L45-L55)
- **Vulnerability**: `enableEdgeToEdge()` was invoked synchronously in `onCreate()` prior to opening the `try { ... } catch (t: Throwable)` block. On custom OEM Android versions (such as Xiaomi MIUI, Vivo FuntouchOS, Oppo ColorOS), window inset and decor view initialization can throw `NullPointerException` or `IllegalStateException` before the error boundary starts, instantly killing the activity process.
- **Fix Applied**: Moved `enableEdgeToEdge()` inside the diagnostic `try { ... } catch (t: Throwable)` block and wrapped it in an inner `try-catch` (`Log.w("enableEdgeToEdge warning")`).

### B. Fix: Exact Sequential Logcat Checkpoints (STARTUP_000 to STARTUP_210)
- **Files**: [`MainActivity.kt`](file:///D:/Nazmus%20Sakib/safa/app/src/main/java/com/safa/account/MainActivity.kt#L41-L115) and [`AppDatabase.kt`](file:///D:/Nazmus%20Sakib/safa/app/src/main/java/com/safa/account/data/database/AppDatabase.kt#L101-L117)
- **Checkpoints Integrated**:
  - `STARTUP_000_PROCESS`
  - `STARTUP_010_ACTIVITY_CREATED`
  - `STARTUP_020_AFTER_SUPER_ON_CREATE`
  - `STARTUP_030_BEFORE_EDGE_TO_EDGE`
  - `STARTUP_040_AFTER_EDGE_TO_EDGE`
  - `STARTUP_050_BEFORE_KEYSTORE`
  - `STARTUP_060_AFTER_KEYSTORE`
  - `STARTUP_070_BEFORE_SQLCIPHER`
  - `STARTUP_080_AFTER_SQLCIPHER`
  - `STARTUP_090_BEFORE_ROOM`
  - `STARTUP_100_AFTER_ROOM`
  - `STARTUP_110_BEFORE_REPOSITORY`
  - `STARTUP_120_AFTER_REPOSITORY`
  - `STARTUP_130_BEFORE_TOKEN_MANAGER`
  - `STARTUP_140_AFTER_TOKEN_MANAGER`
  - `STARTUP_150_BEFORE_VIEWMODEL_FACTORY`
  - `STARTUP_160_AFTER_VIEWMODEL_FACTORY`
  - `STARTUP_170_BEFORE_WORK_MANAGER`
  - `STARTUP_180_AFTER_WORK_MANAGER`
  - `STARTUP_190_BEFORE_SET_CONTENT`
  - `STARTUP_200_SET_CONTENT_STARTED`
  - `STARTUP_210_FIRST_COMPOSE_FRAME`

### C. Fix: Launcher Foreground Bitmap Asset Integration
- **Files**: [`ic_launcher_foreground.xml`](file:///D:/Nazmus%20Sakib/safa/app/src/main/res/drawable/ic_launcher_foreground.xml#L1-L5) and [`Phase3BrandingTest.kt`](file:///D:/Nazmus%20Sakib/safa/app/src/test/java/com/safa/account/ui/Phase3BrandingTest.kt#L22-L26)
- **Change Applied**: Replaced manual vector paths in `ic_launcher_foreground.xml` with `<bitmap android:src="@drawable/safa_logo" android:gravity="center" />` to render the exact 1:1 `safa-logo.png` image directly.

---

## 3. Native Shared Library & ABI Packaging Audit

Both `app-debug.apk` and `app-release.apk` package native `libsqlcipher.so` binaries across all 4 primary Android ABIs:
- `arm64-v8a` (3.6 MB)
- `armeabi-v7a` (2.2 MB)
- `x86` (3.5 MB)
- `x86_64` (4.0 MB)

---

## 4. Automated Test Verification Results

- **Backend Laravel Test Suite (`php artisan test`)**: **33 / 33 Passed (100%)**
- **Android Unit Test Suite (`.\gradlew test --continue`)**: **27 / 27 Passed (100%)**

---

## 5. ADB Logcat Forensic Capture Commands

To capture the exact crash boundary and logcat output on physical devices:

```bash
adb devices
adb logcat -c
adb logcat -v threadtime SafaApp:V AndroidRuntime:E DEBUG:I *:S
```

```bash
adb install -r app-debug.apk
adb shell am force-stop com.safa.account
adb shell am start -n com.safa.account/.MainActivity
```

---

## 6. FINAL RELEASE VERDICT

```text
BLOCKED — ROOT CAUSE NOT PROVEN / NOT READY FOR GO LIVE
```

Per Section 16 of `FORENSIC_STARTUP_CRASH_ISOLATION.md`, the release verdict remains `BLOCKED — ROOT CAUSE NOT PROVEN / NOT READY FOR GO LIVE` until physical device logcat output or runtime execution confirms that the app remains open after launch.
