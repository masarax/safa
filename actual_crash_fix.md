I have now pushed the latest repository state to GitHub.

Repository:
https://github.com/masarax/safa.git

Please inspect the CURRENT `main` branch directly from GitHub before making any further changes.

The Android app STILL crashes/stops immediately after launch on my physical Android 16 device (API 36). This has happened repeatedly after your previous fixes.

Do NOT declare GO LIVE, do NOT mark the issue as resolved, and do NOT rely on unit-test results alone. The app is still not launching on the real device.

I need you to perform a real startup-crash investigation and fix the actual root cause.

## Critical facts

Production website/API base URL:

https://safa.masarax.com

The Android application must use this same hosted Laravel application as its API/backend.

The current TokenManager already contains:

`https://safa.masarax.com/api/`

Do not invent another API host or localhost address.

---

# 1. FIRST: Investigate the actual crash

Before changing architecture again, obtain the REAL Android startup crash.

Use the connected physical Android 16 device and run the equivalent of:

```bash
adb devices
adb shell getprop ro.build.version.sdk
adb shell getconf PAGE_SIZE
adb logcat -c
adb shell am force-stop com.safa.account
adb shell monkey -p com.safa.account 1
adb logcat -d -v threadtime
```

Filter the log for:

* `FATAL EXCEPTION`
* `AndroidRuntime`
* `Fatal signal`
* `SIGSEGV`
* `SIGABRT`
* `UnsatisfiedLinkError`
* `dlopen`
* `SQLite`
* `SQLCipher`
* `KeyStore`
* `EncryptedSharedPreferences`
* `MasterKey`
* `Room`
* `MainActivity`
* `SecurityException`

If the process is dying through a native crash, collect the tombstone/native crash information as well.

DO NOT guess the root cause.

I want the exact first fatal exception / native crash stack that occurs during application startup.

---

# 2. IMPORTANT: Inspect the current startup path

The current `MainActivity` calls:

```kotlin
val database = AppDatabase.getDatabase(applicationContext, lifecycleScope)
```

before `setContent()`.

Therefore the app can crash before Compose UI is even displayed.

Trace the COMPLETE startup dependency chain:

```text
MainActivity
 -> AppDatabase
 -> KeyStoreHelper
 -> SQLCipher native library
 -> Room
 -> repository initialization
 -> TokenManager
 -> AutoSyncWorker
```

Identify exactly which component kills the process.

Do not simply add more try/catch blocks around everything.

---

# 3. CRITICAL SQLCipher PROBLEM

The current repository uses:

```kotlin
implementation("net.zetetic:android-database-sqlcipher:4.5.4")
```

and:

```kotlin
net.sqlcipher.database.SQLiteDatabase.loadLibs(...)
```

This is the legacy `android-database-sqlcipher` package.

According to SQLCipher's own documentation, the legacy Android SQLCipher library is deprecated and the modern replacement is `sqlcipher-android`.

Android 16 / 16 KB page-size compatibility is a critical requirement for this application.

Therefore:

### Investigate and, if confirmed appropriate, migrate the application from:

```text
net.zetetic:android-database-sqlcipher
```

to the supported:

```text
net.zetetic:sqlcipher-android
```

Do not blindly change dependencies. First inspect the exact current Room integration and migration implications.

The migration must preserve:

* existing Room database
* existing encrypted database passphrase
* existing local user data
* all Room migrations
* all DAOs
* all repository behavior
* offline-first behavior
* encryption

There must be NO unencrypted SQLite fallback.

---

# 4. REMOVE THE CURRENT UNSAFE SQLCipher FALLBACK

The current AppDatabase implementation catches SQLCipher native loading failure and then allows Room to continue without `SupportFactory`.

That is NOT an acceptable production solution.

Do NOT do this:

```kotlin
catch (...) {
    factory = null
}
```

followed by opening the encrypted database using normal SQLite.

The database must remain encrypted.

If the required SQLCipher native library cannot load, the application must fail in a controlled and diagnosable way rather than silently switching to unencrypted SQLite.

---

# 5. 16 KB PAGE SIZE VERIFICATION

Check the actual physical device:

```bash
adb shell getconf PAGE_SIZE
```

If it returns:

```text
16384
```

then this is a 16 KB page-size environment.

Then inspect the generated APK:

```bash
zipalign -c -P 16 -v 4 app-debug.apk
```

Also inspect:

```text
lib/arm64-v8a/*.so
lib/armeabi-v7a/*.so
lib/x86_64/*.so
```

and verify ELF LOAD segment alignment.

Do not claim 16 KB compatibility merely because `compileSdk = 36`.

Android's official guidance requires native libraries to actually be compatible/aligned for 16 KB devices.

---

# 6. Test the EXACT APK that is failing

Build:

```bash
./gradlew clean
./gradlew assembleDebug
./gradlew assembleRelease
```

Then install the newly generated APK on the physical Android 16 device.

Do not test only unit tests.

Perform:

```text
uninstall previous version
install fresh debug APK
launch
observe startup
capture logcat
```

Then also test:

```text
install release APK
launch
capture logcat
```

If uninstalling makes it work but updating over an existing installation crashes, test both scenarios separately because that would indicate a persisted KeyStore/database migration problem.

---

# 7. KeyStore investigation

The current repository contains both:

* `KeyStoreHelper.kt`
* `DeviceSecurityHelper.kt`

Do not assume only `KeyStoreHelper` matters.

`DeviceSecurityHelper.kt` still uses:

```kotlin
MasterKey.Builder(context)
    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
    .build()
```

and `EncryptedSharedPreferences`.

Audit ALL Android 16 startup-time KeyStore usage.

The final implementation must:

* preserve the existing DB passphrase
* never delete an existing encryption key
* never generate a new DB passphrase when an existing encrypted database exists
* never silently lose local data
* work after process restart
* work after app update
* work after reinstall according to the defined data-retention model
* avoid incompatible KeyStore initialization
* have deterministic recovery only when it is cryptographically safe

Do not create a second random database key and then claim "data-loss protection".

---

# 8. Database encryption requirement

The database MUST remain encrypted.

There must be no:

```text
unencrypted SQLite fallback
```

and no silent destructive database recreation.

The current:

```kotlin
fallbackToDestructiveMigrationOnDowngrade()
```

must also be reviewed carefully against the production data-loss policy.

Do not destroy an existing user's financial/accounting database merely because a migration or SQLCipher initialization fails.

---

# 9. API/backend verification

The production backend is:

```text
https://safa.masarax.com
```

Verify the actual API endpoints used by the Android app against the Laravel routes/controllers in:

```text
backend/routes/
backend/app/Http/Controllers/
```

Verify:

```text
/api/v1/remote-config
authentication endpoints
login
token/session endpoints
sync endpoints
logo/config endpoints
```

Do not assume endpoint paths.

Compare the Retrofit interfaces with the actual Laravel routes.

However, remember:

THE CURRENT CRASH HAPPENS IMMEDIATELY ON APP LAUNCH.

Therefore do not waste the investigation on API networking until the startup stack is proven to reach the networking layer.

---

# 10. Logo requirement — IMPORTANT

Do NOT use a logo that you designed yourself.

The canonical Android launcher/app logo MUST be the EXACT logo currently displayed on the website Welcome page:

```text
backend/resources/views/welcome.blade.php
```

The Welcome page currently displays:

```html
<img src="{{ asset('safa-logo.png') }}" ...>
```

That exact `safa-logo.png` artwork is the required brand artwork.

I do NOT want a newly recreated shield, redesigned vector, recolored logo, or an agent-generated approximation.

Required branding architecture:

```text
Website Welcome Page logo
        ↓
canonical SAFA logo asset
        ↓
favicon
        ↓
Android launcher icon
        ↓
Android round launcher icon
        ↓
Android in-app logo/header
```

All must represent the SAME artwork.

If `favicon.svg` is visually different from the actual Welcome Page `safa-logo.png`, do NOT treat `favicon.svg` as the canonical source simply because it is easier to convert.

Use the actual Welcome Page logo as the source of truth.

If necessary, generate the required Android mipmap assets from the exact `safa-logo.png` without redesigning the artwork.

---

# 11. Do NOT fake verification

Previous reports repeatedly said:

```text
GO LIVE
```

and:

```text
Cold Launch: PASS
```

while the application still crashes on my physical Android 16 phone.

Therefore this time:

DO NOT write:

```text
GO LIVE
```

unless the exact newly built APK has actually been launched successfully on the physical Android 16 device.

If physical device execution is unavailable to you, explicitly state:

```text
PHYSICAL DEVICE VERIFICATION NOT PERFORMED
```

and do not claim that startup works.

Automated tests passing does NOT prove Android startup works.

---

# 12. Required final verification

Before declaring the issue fixed, verify ALL of these:

### Physical Android 16

```text
Fresh install → Launch → stays open
```

```text
Restart app → Launch → stays open
```

```text
Offline launch → stays open
```

```text
Login → works
```

```text
API connection → works against https://safa.masarax.com
```

```text
Encrypted database → opens successfully
```

```text
Create/read/update local data → works
```

```text
Sync → works
```

```text
App update → existing local encrypted database remains readable
```

### APK

Verify:

```text
Debug APK startup
Release APK startup
16 KB native library alignment
```

### Branding

Verify:

```text
Welcome page logo
favicon
Android launcher icon
Android round icon
in-app logo
```

all use the exact same existing SAFA Welcome Page artwork.

---

# 13. Deliverables

After actually fixing the root cause, provide:

1. Exact root cause of the crash.
2. Exact fatal exception/native crash evidence.
3. Files changed.
4. Why each change was necessary.
5. SQLCipher/Room migration details.
6. KeyStore/data-preservation details.
7. API endpoint verification against `https://safa.masarax.com`.
8. 16 KB verification result.
9. Debug APK build result.
10. Release APK build result.
11. Physical Android 16 launch result.
12. Fresh-install result.
13. Update-install result.
14. Database encryption verification.
15. Logo verification.
16. Final commit SHA.

Most importantly:

DO NOT stop at "tests passed".

The acceptance criterion is simple:

**I install the newly generated APK on my Android 16 phone and the app must NOT immediately stop.**

Until that is demonstrated with actual evidence, the issue is NOT FIXED and the project is NOT GO LIVE.
