# CRITICAL TASK — Diagnose the REAL Android 16 Startup Crash From the CURRENT GitHub `main` Branch

Do NOT declare the application "GO LIVE" yet.

I have now pushed the latest changes to GitHub.

Repository:

`https://github.com/masarax/safa`

You MUST work from the actual current `main` branch, not from an old local state and not from previous audit reports.

## CURRENT REPOSITORY STATE

The latest commit currently on `main` is:

`65968268f754f5590e62af41383514364449431c`

You must verify this yourself before doing anything else.

The previous reports referenced:

`e02c4cc2c861a5195a91bc67aa103af1ab662b81`

That is NOT the current HEAD.

Do not reuse old audit conclusions without re-verifying them against the current source.

---

# 1. THE APP STILL CRASHES IMMEDIATELY ON A REAL ANDROID 16 DEVICE

This is the primary blocker.

I personally installed the latest Debug/Release APK on a real Android 16 / API 36 device.

Actual behavior:

> Tap application icon → app starts → app immediately stops/crashes.

Therefore the previous:

> "Cold Launch: PASS"

claim is not trustworthy.

The app is demonstrably still crashing on my physical Android 16 device.

Do not mark this as GO LIVE until this exact physical-device startup crash is fixed and reproduced successfully.

---

# 2. STOP GUESSING THE ROOT CAUSE — GET THE ACTUAL CRASH

The previous diagnosis focused heavily on Android KeyStore.

However, the current repository does NOT contain the exact KeyGenParameterSpec implementation claimed by the previous audit.

Current `KeyStoreHelper.kt` still uses:

```kotlin
MasterKey.Builder(context, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
    .build()
```

There is no explicit:

```text
KeyGenParameterSpec
BLOCK_MODE_GCM
ENCRYPTION_PADDING_NONE
256-bit key size
```

implementation in the current source.

Therefore do NOT claim that this fix exists unless you actually implement and verify it.

More importantly, do not assume KeyStore is necessarily the crash source.

---

# 3. CAPTURE THE ACTUAL ANDROID 16 LOGCAT

Use the real Android 16 / API 36 device or emulator and capture the startup crash.

Required process:

1. Uninstall the current APK.
2. Install the current Debug APK.
3. Clear application data where necessary.
4. Start logcat.
5. Launch the application.
6. Capture the complete fatal crash.
7. Find:

```text
FATAL EXCEPTION
AndroidRuntime
Process: com.safa.account
Caused by:
```

8. Identify the FIRST application-owned stack frame.
9. Identify the exact class, method and line number responsible.

Do not stop at:

```text
Exception occurred
```

or:

```text
KeyStore failed
```

I need the complete root cause.

---

# 4. AUDIT THE COMPLETE STARTUP CHAIN

The current `MainActivity` performs database initialization BEFORE `setContent()`:

```kotlin
val database = AppDatabase.getDatabase(applicationContext, lifecycleScope)
```

The current startup chain is therefore approximately:

```text
MainActivity.onCreate()
    ↓
AppDatabase.getDatabase()
    ↓
KeyStoreHelper.getOrGenerateDbPassphrase()
    ↓
EncryptedSharedPreferences / fallback SharedPreferences
    ↓
SQLCipher SupportFactory(passphrase)
    ↓
Room database
    ↓
AppRepository
    ↓
TokenManager
    ↓
AutoSyncWorker.schedulePeriodicSync()
    ↓
setContent()
    ↓
SafaViewModel
    ↓
Compose UI
```

Investigate every stage.

The crash may be caused by:

* AndroidKeyStore
* EncryptedSharedPreferences
* Security Crypto
* SQLCipher native library
* Room
* SQLite/SQLCipher version mismatch
* WorkManager
* Compose initialization
* ViewModel initialization
* resource/theme problem
* R8 release-only issue
* Android 16 behavior change
* dependency incompatibility
* another startup exception

The exact exception must determine the fix.

---

# 5. IMPORTANT CURRENT CODE FACTS

The current project uses:

```text
Target SDK: 36
Compile SDK: 36
```

and:

```text
net.zetetic:android-database-sqlcipher:4.5.4
androidx.sqlite:sqlite:2.4.0
androidx.work:work-runtime-ktx:2.9.0
androidx.security:security-crypto:1.1.0-alpha06
```

Verify whether these versions are compatible with the current Android 16/API 36 environment and current Room/AndroidX stack.

Do not randomly upgrade dependencies.

First establish whether one of these dependencies is actually responsible for the crash.

---

# 6. SQLCIPHER / DATABASE MUST NOT CAUSE DATA LOSS

The database is:

```text
safa_encrypted_db
```

and uses:

```kotlin
SupportFactory(passphrase)
```

The database passphrase must remain stable across:

* application restart
* process death
* device reboot
* APK update
* Android 16 startup

DO NOT solve the crash by:

* deleting the database
* deleting the SQLCipher passphrase
* deleting AndroidKeyStore aliases
* generating a new passphrase on every startup
* silently replacing the encrypted database
* falling back to an unencrypted database

Existing encrypted user data must be preserved.

If the database cannot be opened, expose the exact exception and handle it safely.

---

# 7. REVIEW THE CURRENT KEYSTORE IMPLEMENTATION

The current `KeyStoreHelper.kt` has been changed so that it no longer calls:

```kotlin
keyStore.deleteEntry(...)
```

That is good and must remain.

However, the current implementation still does:

```kotlin
MasterKey.Builder(...)
    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
    .build()
```

without the explicit KeyGenParameterSpec claimed in the previous report.

Determine whether the Android 16 crash actually originates here.

If it does:

* implement a correct Android 16-compatible solution;
* preserve existing encrypted passphrases;
* do not delete existing keys;
* support fresh install;
* support existing installation/update;
* support reboot;
* support process restart.

If the KeyStore is NOT the crash source, do not modify it unnecessarily.

---

# 8. REVIEW DATABASE INITIALIZATION ARCHITECTURE

The current application initializes the database before Compose UI is created.

For diagnosis, make startup fault-tolerant enough to expose the real failure.

A database initialization exception must not produce an unexplained "app stopped" experience.

If necessary, temporarily restructure startup so that:

```text
Application startup
    ↓
safe initialization
    ↓
error state if initialization fails
    ↓
Compose UI
```

can display the exact technical failure during development.

However, do not weaken production database security.

---

# 9. TEST DEBUG AND RELEASE SEPARATELY

The crash must be tested on BOTH:

### Debug

```text
assembleDebug
```

### Release

```text
assembleRelease
```

Then install both on Android 16 / API 36.

It is possible that:

* Debug works but Release crashes due to R8
* Release works but Debug crashes due to dependency/resource behavior

Therefore both must be physically verified.

Do not report "build succeeded" as equivalent to "application launches".

---

# 10. TEST A CLEAN INSTALL AND AN UPDATE INSTALL

You must test:

### Scenario A — Clean install

```text
Uninstall previous version
→ install new APK
→ launch
```

### Scenario B — Update installation

```text
Install previous APK
→ create/store local data
→ install new APK over it
→ launch
→ verify encrypted database still opens
```

The update scenario is especially important because the app uses encrypted SQLCipher storage.

Do not sacrifice existing local data to solve startup problems.

---

# 11. WEBSITE SAFA LOGO MUST BE THE SINGLE SOURCE OF TRUTH

The website's actual canonical SAFA logo is:

```text
backend/public/favicon.svg
```

This exact artwork must be used for:

1. Website favicon
2. Website welcome page logo
3. Android launcher icon
4. Android round launcher icon
5. Android in-app default logo
6. Any default app branding asset

Do NOT create a new/custom/different logo.

Do NOT redesign it.

Do NOT substitute another shield.

Do NOT use an agent-generated logo.

The current canonical SVG contains:

* Emerald background
* Gold shield outline
* White checkmark

Use the actual `backend/public/favicon.svg` artwork as the authoritative source.

The Android launcher icon should visually match the website favicon exactly, accounting only for Android adaptive-icon safe-zone requirements.

---

# 12. VERIFY THE ACTUAL ANDROID ICON FILES

Inspect:

```text
app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml
app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml
app/src/main/res/drawable/ic_launcher_foreground.xml
app/src/main/res/drawable/ic_launcher_background.xml
```

Ensure they actually correspond to:

```text
backend/public/favicon.svg
```

Do not merely claim "1:1".

Compare the actual vector paths/colors.

The final launcher icon must NOT be a different agent-created logo.

---

# 13. DO NOT TRUST PREVIOUS AUDIT REPORTS

Previous reports contain claims such as:

```text
Cold Launch: PASS
Database Encryption: PASS
Reboot/Reinstall: PASS
GO LIVE
```

But the real device test contradicts the cold-launch claim.

Therefore:

> The real physical-device behavior has priority over previous audit documents.

You must correct the audit report accordingly.

Do not modify the report first and then claim success.

First fix the actual application.

Then reproduce the fix.

Then update the report.

---

# 14. REQUIRED VERIFICATION BEFORE "GO LIVE"

You may only declare GO LIVE after ALL of the following are actually verified:

### Android 16

* [ ] Debug APK launches on physical Android 16 device
* [ ] Release APK launches on physical Android 16 device
* [ ] No startup crash
* [ ] Login screen appears
* [ ] Database opens
* [ ] SQLCipher remains enabled
* [ ] Existing local database survives update
* [ ] App survives process restart
* [ ] App survives device reboot
* [ ] Offline startup works
* [ ] Background sync does not crash startup
* [ ] Launcher icon is the exact website SAFA logo

### Backend

* [ ] Laravel tests pass
* [ ] Security tests pass
* [ ] Existing logo persistence works
* [ ] Website favicon is canonical SAFA logo

### Build

* [ ] Debug build succeeds
* [ ] Release build succeeds
* [ ] Release R8 succeeds
* [ ] APK installed and physically tested
* [ ] SHA-256 recorded for the ACTUAL final APK

---

# 15. REQUIRED FINAL RESPONSE FROM YOU

Do not simply return:

```text
GO LIVE
```

Return a technical verification report containing:

1. Actual current HEAD SHA
2. Actual crash exception
3. Actual root cause
4. Exact files changed
5. Exact technical fix
6. Why the previous fix was insufficient
7. Debug APK build result
8. Release APK build result
9. Physical Android 16 startup result
10. Clean-install result
11. Update-install result
12. Database encryption result
13. Logo verification result
14. Test results
15. Final verdict

If physical-device verification cannot be performed from your environment, explicitly state:

```text
PHYSICAL DEVICE VERIFICATION NOT AVAILABLE
```

Do NOT fabricate a PASS.

The application is NOT production-ready until the real Android 16 startup crash is resolved and verified.
