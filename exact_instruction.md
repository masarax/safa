I want you to STOP treating the previous "PRODUCTION READY / GO LIVE" reports as sufficient. I have now performed a real installation test on an Android 16 device with Target SDK 36, and the application immediately crashes/stops as soon as I open it.

I have also inspected the actual GitHub repository `masarax/safa` directly. You must use the current repository source as the authoritative source and fix the actual root cause, not just make the tests pass.

## 1. CRITICAL: Android 16 startup crash must be fixed

The current app is configured with:

* Target SDK 36
* Compile SDK 36
* Android 16 test device
* Room 2.7.0
* SQLCipher `net.zetetic:android-database-sqlcipher:4.5.4`
* AndroidX Security Crypto `1.1.0-alpha06`

The current startup path in `MainActivity.kt` immediately initializes:

`AppDatabase.getDatabase(applicationContext, lifecycleScope)`

and `AppDatabase` immediately initializes:

`KeyStoreHelper.getOrGenerateDbPassphrase(...)`

then attempts:

`System.loadLibrary("sqlcipher")`

and `SupportFactory(passphrase)`.

The current code catches SQLCipher loading failures and falls back to normal Room SQLite, but this does NOT prove that startup is safe. `KeyStoreHelper` also catches `Throwable` and falls back to ordinary SharedPreferences.

I need you to investigate the REAL crash on Android 16.

### Required debugging process

Do NOT assume the cause.

You must:

1. Install the DEBUG APK on the actual Android 16 device/emulator.
2. Launch the application from a completely cold state.
3. Capture the actual `logcat` crash/FATAL EXCEPTION output.
4. Identify the exact exception, class, native library, initialization block, or Android 16 compatibility issue causing the crash.
5. Fix the root cause.
6. Reinstall the APK after clearing/uninstalling the previous app data.
7. Test a completely fresh first launch.
8. Test a second launch.
9. Test launch after device reboot.
10. Test launch with network unavailable.
11. Test launch after database already contains data.
12. Verify that the app does not crash in any of these cases.

Do not declare success merely because:

`.\gradlew test --continue`

passes.

The real acceptance criterion is:

**The app must physically launch and remain open on Android 16 without crashing.**

Also verify both DEBUG and RELEASE APKs on Android 16.

## 2. Pay special attention to the database/security startup chain

Review these files and the complete dependency chain:

* `app/src/main/java/com/safa/account/MainActivity.kt`
* `app/src/main/java/com/safa/account/data/database/AppDatabase.kt`
* `app/src/main/java/com/safa/account/data/database/KeyStoreHelper.kt`
* `app/build.gradle.kts`
* `gradle/libs.versions.toml`
* `app/src/main/AndroidManifest.xml`

Do not blindly keep the current:

`net.zetetic:android-database-sqlcipher:4.5.4`

if it is incompatible or problematic on Android 16 / current Android tooling.

Determine whether the SQLCipher version, native ABI packaging, Room integration, AndroidX Security Crypto version, or any other startup dependency is responsible.

If SQLCipher is the cause, upgrade to a current Android-16-compatible SQLCipher version and make sure all required native ABIs are packaged correctly.

If AndroidX Security Crypto is involved, replace deprecated/alpha dependencies with a stable supported approach where appropriate.

Do not remove encryption/security just to make the crash disappear.

The database must remain secure and existing database data must remain compatible.

## 3. IMPORTANT: Do not hide startup crashes with broad Throwable fallbacks

The current code uses broad `catch (t: Throwable)` blocks.

Do not simply add more broad catches to hide the actual failure.

A startup crash must be properly fixed.

If a fallback is genuinely required, it must:

* preserve data integrity,
* preserve database security,
* be deterministic,
* be tested,
* and not silently downgrade from encrypted storage to unencrypted storage.

In particular, do NOT introduce an insecure fallback where an encrypted database becomes an ordinary unencrypted SQLite database without an explicit, safe migration/design decision.

## 4. Test database initialization independently

Create/extend automated tests where useful for:

* first database initialization,
* existing encrypted database reopening,
* KeyStore passphrase retrieval,
* Android 16 compatibility,
* SQLCipher initialization,
* Room initialization,
* migration from existing database versions,
* cold-start initialization.

But remember: instrumentation/device testing is required in addition to JVM unit tests.

## 5. CRITICAL: Use the EXISTING SAFA WEBSITE WELCOME-PAGE LOGO

The previous agent created/used its own logo.

I explicitly reject that.

DO NOT create a new logo.

DO NOT redesign the logo.

DO NOT generate another logo.

The logo already used by the SAFA website welcome page is the canonical SAFA brand logo.

Use that exact existing website logo as:

1. Website favicon
2. Android launcher icon
3. Android round launcher icon
4. Android app icon wherever the SAFA brand icon is displayed

The repository currently contains the SAFA favicon asset at:

`backend/public/favicon.svg`

This existing SAFA asset must be treated as the source/reference for the application icon. Do not replace it with a newly designed shield/checkmark/logo.

The Android icon must visually match the actual website welcome-page SAFA logo.

## 6. Proper Android adaptive icon implementation

Do not merely put the logo into `ic_launcher_foreground.xml`.

Build the launcher icon correctly for modern Android:

* adaptive launcher icon
* foreground
* background
* round icon
* legacy fallback where required
* correct safe-zone/mask handling
* no unwanted clipping
* no duplicated background
* no artificial logo redesign

The visual identity must remain exactly the existing SAFA website logo.

If the website logo is SVG, convert/use it appropriately for Android resources while preserving the same artwork.

Do not substitute another icon.

## 7. Website favicon consistency

Verify that the website welcome page, browser favicon, Android launcher icon, and application branding all use the same canonical SAFA logo.

Check for:

* `favicon.svg`
* favicon `<link>` tags
* PNG favicon fallback if needed
* Android launcher resources
* any duplicate/generated logo resources
* any hardcoded alternate logo assets

Remove or stop using the agent-created alternate logo if it is not the actual website SAFA logo.

There must be ONE canonical SAFA brand logo source.

## 8. Verify dynamic logo functionality separately

There are two different concepts that must not be confused:

### A. Brand / launcher icon

The Android APK launcher icon and website favicon must use the canonical SAFA website welcome-page logo.

### B. Runtime custom application logo

If the application supports uploading a custom logo from the admin panel, that dynamic logo may be displayed inside the application UI.

Do NOT dynamically replace the compiled Android launcher icon with a server-uploaded image.

The APK launcher icon is a compiled application resource.

The runtime custom logo is a separate UI feature.

Keep these responsibilities separate.

## 9. Release build verification

After fixing the crash and logo:

Run all relevant verification:

### Backend

`php artisan test`

### Android JVM tests

`.\gradlew test --continue`

### Android resource/build verification

`.\gradlew processDebugResources`

### Debug APK

`.\gradlew assembleDebug`

### Release APK

`.\gradlew assembleRelease`

Then install BOTH APKs on Android 16 and physically launch them.

Do not claim production readiness until both actually launch successfully.

## 10. Required Android 16 smoke-test matrix

For both Debug and Release APKs verify:

* Fresh install → launch
* First launch → remains open
* Login screen → works
* Login → dashboard
* Close app → reopen
* Force stop → reopen
* Device reboot → reopen
* Offline launch → works without crash
* Existing local database → opens without crash
* App update over previous version → opens without crash
* Database migration → works
* Background sync initialization → does not crash startup
* Android 16 system back behavior → works
* App icon displayed correctly
* Recent-apps icon displayed correctly

Capture actual results.

## 11. Do not modify unrelated business logic

This task is primarily:

1. Android 16 startup crash
2. Database/security startup compatibility
3. Correct canonical SAFA logo
4. Favicon/icon consistency
5. Real-device verification

Do not make unnecessary changes to financial logic, sync business rules, authentication, or UI.

## 12. Final report requirements

When finished, provide a truthful report containing:

### Root cause

The exact Android 16 crash exception and why it happened.

### Files changed

Exact file paths and what was changed.

### Database compatibility

Explain whether existing local databases remain readable.

### Logo

Confirm that the launcher icon is derived from the existing SAFA website welcome-page logo, NOT a newly generated logo.

### Test results

Show actual:

* Laravel tests
* Android unit tests
* Debug build
* Release build
* Android 16 Debug installation test
* Android 16 Release installation test

### Crash verification

Explicitly state that the app was launched on Android 16 after installation and did NOT crash.

### APK

Provide:

* Debug APK path
* Release APK path
* Release SHA-256

Do NOT write "PRODUCTION READY", "GO LIVE", or "ALL PASS" unless the actual Android 16 physical launch test has passed.

The previous reports already claimed production readiness, but my real device test disproved that claim. Therefore this task must be treated as a new blocking production issue until the Android 16 crash is actually reproduced, fixed, and verified.

Start by reproducing the Android 16 crash and collecting the real FATAL EXCEPTION/logcat output before making further speculative changes.
