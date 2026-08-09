# STOP GUESSING — PERFORM A REAL STARTUP CRASH INVESTIGATION ON THE CURRENT GITHUB HEAD

I have now reviewed the CURRENT `main` branch of:

https://github.com/masarax/safa.git

I also inspected the current source code directly from GitHub.

The Android app STILL immediately stops/crashes on my physical Android 16 / API 36 phone.

Your previous reports repeatedly claimed that the problem was fixed, but the application still does not launch.

Therefore, this is now a strict debugging task.

## IMPORTANT: DO NOT DECLARE GO LIVE

Do NOT write:

```text
GO LIVE
```

Do NOT write:

```text
Cold Launch: PASS
```

Do NOT claim that Android startup works.

Do NOT consider the issue fixed because:

```text
33/33 Laravel tests passed
27/27 Android tests passed
APK assembled successfully
```

Those tests do NOT prove that the application starts successfully on my Android 16 phone.

The acceptance criterion is:

> I install the newly generated APK on my Android 16 phone and the app remains open instead of immediately stopping.

Until that is proven, the application is NOT fixed.

---

# 1. I reviewed the CURRENT GitHub HEAD

The latest GitHub `main` commit is currently:

```text
3d3c6a66fddbff50ff0632f6e6b5848f1579d51f
```

Your previous audit reports still reference:

```text
65968268f754f5590e62af41383514364449431c
```

That is NOT the current HEAD anymore.

Therefore:

1. Re-read the CURRENT `main` branch.
2. Re-audit the actual current source.
3. Do not base the investigation on an old audit report.
4. Every final report must reference the actual final commit SHA.

---

# 2. FIRST PRIORITY: GET THE REAL CRASH LOG

Before changing another Kotlin file, obtain the actual crash from the physical Android 16 device.

Run:

```bash
adb devices
adb shell getprop ro.build.version.sdk
adb shell getprop ro.build.version.release
adb shell getconf PAGE_SIZE

adb logcat -c

adb shell am force-stop com.safa.account
adb shell monkey -p com.safa.account 1

adb logcat -d -v threadtime
```

Then specifically extract:

```text
FATAL EXCEPTION
AndroidRuntime
Fatal signal
SIGSEGV
SIGABRT
SIGBUS
UnsatisfiedLinkError
dlopen
SQLite
SQLCipher
KeyStore
EncryptedSharedPreferences
MasterKey
Room
MainActivity
SecurityException
```

Also run:

```bash
adb logcat -b crash -d -v threadtime
```

If this is a native crash, obtain the native crash/tombstone evidence.

I need the FIRST fatal exception or native crash stack.

Do not give me a theoretical root cause.

Show me the actual evidence.

---

# 3. VERY IMPORTANT: THE CURRENT DATABASE CODE STILL HAS AN UNSAFE FALLBACK

I inspected the current:

```text
app/src/main/java/com/safa/account/data/database/AppDatabase.kt
```

The current code effectively does this:

```kotlin
val factory = try {
    SQLiteDatabase.loadLibs(context)
    SupportFactory(passphrase)
} catch (t: Throwable) {
    Log.e(...)
    null
}
```

and then:

```kotlin
if (factory != null) {
    builder.openHelperFactory(factory)
}
```

This means:

```text
SQLCipher fails
        ↓
factory = null
        ↓
Room continues
        ↓
normal SQLite may be opened
```

This is NOT acceptable.

The application requires encrypted local financial/accounting data.

There must NEVER be:

```text
SQLCipher failure → unencrypted SQLite
```

Remove this unsafe fallback.

If SQLCipher cannot initialize, the application must fail in a controlled, diagnosable way rather than silently opening an unencrypted database.

---

# 4. THE CURRENT SQLCIPHER DEPENDENCY IS STILL LEGACY

I inspected:

```text
app/build.gradle.kts
```

The current dependency is:

```kotlin
implementation("net.zetetic:android-database-sqlcipher:4.5.4")
```

and the code uses:

```kotlin
net.sqlcipher.database.SQLiteDatabase
net.sqlcipher.database.SupportFactory
```

This is the legacy SQLCipher Android integration.

Android 16 / API 36 and 16 KB page-size compatibility are critical requirements.

Investigate whether this legacy integration is the actual native startup-crash source.

If the current SQLCipher package is incompatible with the physical Android 16 environment, migrate to the currently supported SQLCipher Android integration.

However:

DO NOT blindly replace the dependency.

First inspect:

```text
Room version
Room SupportSQLiteOpenHelper integration
SQLCipher API compatibility
native .so libraries
ABI packaging
16 KB ELF alignment
existing encrypted database format
database passphrase handling
Room migrations
```

Then perform the migration safely.

---

# 5. 16 KB PAGE-SIZE MUST BE VERIFIED, NOT ASSUMED

Run on the actual device:

```bash
adb shell getconf PAGE_SIZE
```

If the result is:

```text
16384
```

then this is a 16 KB page-size environment.

Then inspect the generated APK:

```bash
zipalign -c -P 16 -v 4 app-debug.apk
```

and inspect:

```text
lib/arm64-v8a/*.so
lib/armeabi-v7a/*.so
lib/x86_64/*.so
```

Check ELF LOAD segment alignment.

Do not claim Android 16 / 16 KB compatibility simply because:

```text
compileSdk = 36
targetSdk = 36
```

Those values alone do NOT prove native 16 KB compatibility.

---

# 6. TRACE THE COMPLETE REAL STARTUP PATH

The current `MainActivity` initializes the database BEFORE Compose:

```kotlin
val database = AppDatabase.getDatabase(
    applicationContext,
    lifecycleScope
)
```

This happens before:

```kotlin
setContent { ... }
```

Therefore the startup path must be traced exactly:

```text
MainActivity.onCreate()
        ↓
AppDatabase.getDatabase()
        ↓
KeyStoreHelper.getOrGenerateDbPassphrase()
        ↓
SQLCipher loadLibs()
        ↓
SupportFactory()
        ↓
Room.databaseBuilder()
        ↓
database.build()
        ↓
AppRepository()
        ↓
TokenManager()
        ↓
AutoSyncWorker.schedulePeriodicSync()
        ↓
setContent()
```

Determine exactly where the process dies.

Do NOT simply wrap the entire startup in:

```kotlin
try/catch
```

and ignore the underlying failure.

---

# 7. TEMPORARILY ISOLATE STARTUP COMPONENTS TO FIND THE REAL CRASH

If physical logcat does not immediately identify the cause, perform controlled binary isolation.

For example, create a temporary diagnostic build where:

### Test A

Do not initialize:

```text
AppDatabase
```

Launch the application.

If the app launches, the crash is inside the database startup path.

### Test B

Restore database but do not initialize:

```text
TokenManager
```

### Test C

Restore TokenManager but disable:

```text
AutoSyncWorker
```

### Test D

Restore all components.

This will identify the exact component responsible.

Do not permanently remove functionality.

This is only for root-cause isolation.

---

# 8. KEYSTORE MUST BE AUDITED AS A WHOLE

There are currently TWO separate security helpers:

```text
KeyStoreHelper.kt
DeviceSecurityHelper.kt
```

Both use:

```text
MasterKey
EncryptedSharedPreferences
```

Do not assume the previous KeyStore fix solved everything.

Audit every startup-time KeyStore operation.

Specifically verify:

```text
MasterKey creation
EncryptedSharedPreferences initialization
existing key alias handling
existing database passphrase retrieval
device UUID retrieval
hardware fingerprint generation
app-signature retrieval
```

The implementation must guarantee:

```text
existing encrypted DB
        ↓
same passphrase
        ↓
same encryption key
        ↓
database remains readable
```

Never generate a new random DB passphrase when an encrypted database already exists.

Never delete an existing encryption key.

Never silently destroy local financial data.

---

# 9. IMPORTANT: THE CURRENT FALLBACK STORAGE IS NOT A REAL HARDWARE-SECURE SOLUTION

The current `KeyStoreHelper.kt` falls back to:

```text
safa_secure_passphrase_store
```

using ordinary:

```kotlin
context.getSharedPreferences(...)
```

That may preserve a passphrase, but it changes the security model substantially.

Do not describe this as equivalent to hardware-backed secure storage.

Audit whether this fallback is actually necessary.

The final architecture should preserve:

1. encryption
2. passphrase persistence
3. Android 16 compatibility
4. update compatibility
5. no silent data loss

without weakening the application's security model unnecessarily.

---

# 10. REVIEW DATABASE MIGRATION SAFETY

Current database version is:

```text
5
```

and the project contains:

```text
MIGRATION_3_4
MIGRATION_4_5
```

The current code also contains:

```kotlin
fallbackToDestructiveMigrationOnDowngrade()
```

Review this carefully.

This is a financial/accounting application.

Do NOT allow a startup/migration problem to silently destroy a user's local database.

The final database strategy must be explicit about:

```text
fresh install
existing version 3
existing version 4
existing version 5
app update
downgrade
SQLCipher initialization failure
corrupted database
wrong passphrase
```

---

# 11. DO NOT SPEND TIME ON API UNTIL STARTUP IS PROVEN

The correct production backend is:

```text
https://safa.masarax.com
```

The current TokenManager already contains:

```text
https://safa.masarax.com/api/
```

Therefore do NOT replace it with:

```text
localhost
10.0.2.2
10.0.2.2:8000
```

The Android app must use:

```text
https://safa.masarax.com
```

as the production backend.

However, the app currently crashes immediately on launch.

Therefore first prove whether the crash occurs BEFORE networking.

Only after the application successfully reaches the UI should you verify:

```text
/api/v1/remote-config
authentication
login
token/session
sync
logo/config
```

against the Laravel backend.

---

# 12. VERIFY RETROFIT ROUTES AGAINST LARAVEL

After startup is fixed, compare:

```text
Android Retrofit interfaces
```

against:

```text
backend/routes/api.php
backend/routes/web.php
backend/app/Http/Controllers/
```

Verify the exact paths.

Do not assume that an endpoint exists merely because it is mentioned in an audit report.

---

# 13. LOGO REQUIREMENT — USE THE ACTUAL WELCOME PAGE LOGO

Do NOT design a new logo.

Do NOT recreate the logo from `favicon.svg`.

Do NOT use an agent-created shield.

The canonical source is the exact logo currently displayed on:

```text
backend/resources/views/welcome.blade.php
```

which uses:

```html
<img src="{{ asset('safa-logo.png') }}" ...>
```

The exact existing:

```text
backend/public/safa-logo.png
```

must be the source artwork.

Required relationship:

```text
backend/public/safa-logo.png
            ↓
Website Welcome Page
            ↓
favicon
            ↓
Android launcher icon
            ↓
Android round launcher icon
            ↓
Android in-app logo/header
```

All must represent the same actual artwork.

If adaptive icon resources are necessary, generate them from the exact existing PNG.

Do NOT redraw or reinterpret the artwork.

---

# 14. IMPORTANT: YOUR CURRENT REPORT IS NOT ACCEPTABLE

Your latest report says:

```text
PHYSICAL DEVICE VERIFICATION NOT PERFORMED
```

and then still says:

```text
GO LIVE WITH CONDITIONS
```

That is not appropriate while the user is explicitly reporting that the app crashes immediately on a physical Android 16 device.

The final verdict must remain:

```text
NOT READY / BLOCKED
```

until the actual APK launches successfully.

---

# 15. REQUIRED TEST MATRIX

After fixing the actual root cause, perform:

## Physical Android 16

```text
Fresh install
→ Launch
→ App remains open
```

```text
Restart app
→ Launch
→ App remains open
```

```text
Offline
→ Launch
→ App remains open
```

```text
Login
→ Successful
```

```text
API
→ https://safa.masarax.com
```

```text
Encrypted database
→ Opens successfully
```

```text
Create local record
→ Works
```

```text
Read local record
→ Works
```

```text
Update local record
→ Works
```

```text
Sync
→ Works
```

```text
App update
→ Existing encrypted database remains readable
```

## APK

Verify:

```text
Debug APK
Release APK
16 KB native library alignment
arm64-v8a native libraries
```

## Branding

Verify:

```text
Website Welcome Page
favicon
launcher icon
round launcher icon
in-app logo
```

all use the exact existing:

```text
backend/public/safa-logo.png
```

artwork.

---

# 16. REQUIRED FINAL REPORT

When the issue is genuinely fixed, report:

1. Exact root cause.
2. Actual fatal exception/native crash stack.
3. Evidence from physical Android 16 device.
4. Files changed.
5. Exact reason for every change.
6. SQLCipher version before/after.
7. Room integration before/after.
8. Database migration strategy.
9. KeyStore/data-preservation strategy.
10. API endpoint verification.
11. 16 KB page-size result.
12. Native library alignment result.
13. Debug APK result.
14. Release APK result.
15. Fresh-install result.
16. Restart result.
17. Offline result.
18. Update-install result.
19. Database encryption verification.
20. Logo verification.
21. Final commit SHA.

Do NOT fabricate physical-device results.

If physical device execution is unavailable, explicitly say:

```text
PHYSICAL DEVICE VERIFICATION NOT PERFORMED
```

and keep the release status:

```text
BLOCKED — NOT READY FOR GO LIVE
```

---

# FINAL ACCEPTANCE CRITERION

The project is NOT considered fixed until this exact condition is satisfied:

```text
Install newly generated APK on my Android 16 / API 36 phone
        ↓
Tap SAFA
        ↓
Application opens
        ↓
Application remains running
        ↓
No immediate crash / stop
```

Only after this succeeds should you proceed to login, API, sync, encryption, and final production verification.

Until then:

# DO NOT DECLARE GO LIVE.
