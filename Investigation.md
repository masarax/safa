# SAFA — P0 Android 16 Crash, Security, API, SQLCipher & Branding Root-Cause Investigation

You must STOP declaring the project ready for production based only on unit tests or source-code inspection.

The application is STILL crashing immediately after launch on my physical Android 16 device. I have personally installed the generated APKs and verified the behavior.

This is therefore a **P0 BLOCKER**.

I have now reviewed the current GitHub repository directly:

Repository:

https://github.com/masarax/safa.git

Current branch:

`main`

Do not assume that previous audit reports are correct. Treat the current GitHub `main` source as the only authoritative codebase.

---

## 1. FIRST PRIORITY — FIND THE REAL ANDROID 16 STARTUP CRASH

The application currently initializes the database BEFORE `setContent()` inside `MainActivity.onCreate()`.

The current startup path is effectively:

```text
MainActivity.onCreate()
    ↓
AppDatabase.getDatabase()
    ↓
KeyStoreHelper
    ↓
SQLiteDatabase.loadLibs()
    ↓
SQLCipher native library
    ↓
Room database initialization
    ↓
setContent()
    ↓
First screen
```

Therefore any KeyStore, SQLCipher, native library, Room, migration, or database initialization failure can kill the application before the first Compose screen appears.

Do NOT make another speculative fix.

### Mandatory investigation

Inspect and verify:

* `MainActivity.kt`
* `AppDatabase.kt`
* `KeyStoreHelper.kt`
* `DeviceSecurityHelper.kt`
* `AndroidManifest.xml`
* `Application` initialization
* all startup initializers
* WorkManager initialization
* Retrofit / OkHttp initialization
* SQLCipher
* Room
* AndroidX Security Crypto
* Tink
* Compose initialization
* network security config
* FileProvider
* ProGuard/R8
* release signing
* ABI/native libraries
* 16 KB page-size compatibility
* every dependency initialized during startup

If ADB is available, obtain the ACTUAL crash:

```bash
adb logcat -c
adb shell am force-stop com.safa.account
adb shell monkey -p com.safa.account 1
adb logcat -d
```

Then identify the exact:

```text
FATAL EXCEPTION
AndroidRuntime
Caused by
Fatal signal
SIGSEGV
SIGABRT
UnsatisfiedLinkError
SecurityException
SQLiteException
RoomException
```

Do not write a root cause in the report unless it is supported by actual runtime evidence or a deterministic code/dependency failure.

---

# 2. CRITICAL SQLCIPHER PROBLEM

The current repository still uses:

```text
net.zetetic:android-database-sqlcipher:4.5.4
```

This is the legacy/deprecated SQLCipher Android package.

The application targets:

```text
Target SDK 36
Compile SDK 36
Android 16
```

The current `AppDatabase.kt` directly calls:

```kotlin
SQLiteDatabase.loadLibs(context)
```

and uses:

```kotlin
SupportFactory(passphrase)
```

This must be treated as a PRIMARY crash suspect.

Do not hide this with:

```kotlin
try {
    ...
} catch (...) {
    // fallback to normal SQLite
}
```

That is NOT acceptable because SAFA is a financial application and must never silently fall back to an unencrypted database.

### Required action

Migrate the application from:

```text
android-database-sqlcipher
```

to the currently supported:

```text
sqlcipher-android
```

using the official SQLCipher migration guidance.

Use the appropriate current supported version and compatible AndroidX SQLite version.

Update:

```text
Gradle dependencies
imports
native library loading
Room SupportOpenHelperFactory
ProGuard/R8 rules
database initialization
tests
```

The migration must preserve existing encrypted database compatibility where possible.

Do NOT delete existing local database data.

Do NOT generate a new encryption passphrase on every installation/update.

Do NOT add an unencrypted SQLite fallback.

Official migration reference:

https://www.zetetic.net/sqlcipher/sqlcipher-for-android-migration/

Official SQLCipher Android repository:

https://github.com/sqlcipher/sqlcipher-android

---

# 3. 16 KB PAGE-SIZE COMPATIBILITY MUST BE VERIFIED

The app targets Android 16 / API 36 and contains native SQLCipher code.

Therefore inspect the final APK itself.

Do NOT simply state:

```text
16 KB compatible
```

because Gradle compilation succeeded.

Actually inspect the APK's native libraries.

Verify:

```bash
adb shell getconf PAGE_SIZE
```

when testing on a 16 KB environment.

Also verify APK alignment:

```bash
zipalign -c -P 16 -v 4 app-debug.apk
zipalign -c -P 16 -v 4 app-release.apk
```

Inspect every `.so` under:

```text
lib/arm64-v8a/
lib/armeabi-v7a/
lib/x86_64/
lib/x86/
```

and verify ELF LOAD segment alignment.

If the SQLCipher native library is not 16 KB compatible, fix the dependency rather than adding a Kotlin exception handler.

---

# 4. DO NOT REMOVE DATABASE ENCRYPTION

The previous implementation attempted to use:

```text
SQLCipher → fallback → normal SQLite
```

This is forbidden.

SAFA stores financial/accounting information.

The final architecture MUST be:

```text
Room
 ↓
SQLCipher
 ↓
Encrypted database
```

If SQLCipher initialization fails:

```text
FAIL CLOSED
```

It must NOT silently create/open an unencrypted database.

---

# 5. KEYSTORE / PASSPHRASE ARCHITECTURE

Audit `KeyStoreHelper.kt` again.

Current implementation contains:

```text
EncryptedSharedPreferences
        ↓
fallback
safa_secure_passphrase_store
        ↓
normal SharedPreferences
```

This fallback must NOT be described as equivalent to hardware-backed secure storage.

Requirements:

1. Never delete an existing database encryption key during app update.
2. Never regenerate the database passphrase when an existing encrypted database exists.
3. Never silently replace an existing database with a new database.
4. Never use an unencrypted database.
5. Handle Android Keystore invalidation deterministically.
6. Preserve existing encrypted database access whenever cryptographically possible.
7. If recovery is impossible, fail clearly instead of destroying data.
8. Do not log the database passphrase.
9. Do not include the passphrase in crash reports.
10. Do not expose the passphrase through API responses.

Also audit `DeviceSecurityHelper.kt` separately.

Do not confuse:

```text
device UUID
database encryption key
API secret
JWT token
fingerprint
```

They are different security primitives and must remain separate.

---

# 6. API SECURITY — CURRENT ARCHITECTURE MUST BE AUDITED

Production API base URL:

```text
https://safa.masarax.com
```

The Android application must use this backend.

The backend currently protects several endpoints using:

```text
X-SAFA-API-KEY
X-SAFA-SIGNATURE
X-SAFA-TIMESTAMP
X-SAFA-NONCE
```

The server calculates an HMAC-SHA256 signature using the API secret.

Audit the complete Android request path.

Find the actual Retrofit/OkHttp construction and prove that:

```text
API URL
API key
HMAC signature
timestamp
nonce
access token
device token
fingerprint token
```

are correctly handled.

Do not assume `TokenManager` is enough.

The current TokenManager has empty default values for:

```text
DEFAULT_API_KEY
DEFAULT_API_SECRET
```

This must be resolved architecturally.

---

# 7. VERY IMPORTANT — NEVER EMBED THE PRODUCTION API SECRET IN THE APK

The production `.env` contains private credentials.

DO NOT:

* print them
* quote them
* commit them
* put them in `FINAL_GO_LIVE_AUDIT.md`
* put them in source code
* put them in `BuildConfig`
* put them in resources
* put them in APK assets
* put them in logs
* put them in GitHub issues
* put them in test reports
* put them in screenshots

The `.env` must remain private and server-side only.

The Android APK must NOT contain the production API secret.

If the current API architecture requires a shared HMAC secret inside the APK, redesign the authentication architecture so the server does not rely on a recoverable long-term secret embedded in a public/distributable APK.

Do not solve this by simply hardcoding the `.env` values into Kotlin.

---

# 8. AUTHENTICATION FLOW MUST BE TESTED AGAINST THE REAL SERVER

Verify the real production flow:

```text
Android
  ↓
https://safa.masarax.com/api/auth/login
  ↓
mobile + PIN
  ↓
server authentication
  ↓
access token
refresh token
device token
session token
fingerprint token
  ↓
secure local persistence
  ↓
authenticated API requests
```

Verify actual HTTP responses.

Do not mock the production API.

Check:

* HTTP status
* response JSON
* token parsing
* token persistence
* device binding
* fingerprint handling
* refresh
* logout
* expired token
* invalid token
* offline mode
* reconnect
* sync retry

---

# 9. API ROUTE CONSISTENCY AUDIT

Inspect every Android `ApiService` endpoint against Laravel `routes/api.php`.

Do not assume endpoint existence.

For every Android endpoint produce a table:

```text
Android method
HTTP method
Android path
Laravel route
Middleware
Authentication required
Expected response
Actual response
Status
```

Pay special attention to:

```text
/auth/*
/sync/*
/config/*
/upload/*
/version/*
/graphql
/customers
/suppliers
/deposits
/transactions
```

If Android calls an endpoint that Laravel does not expose, fix the contract.

If Laravel exposes an endpoint that Android incorrectly assumes is public, fix the client.

---

# 10. API SIGNATURE CANONICALIZATION

The backend currently signs a payload based on:

```text
METHOD + PATH + TIMESTAMP + NONCE + BODY
```

The Android implementation must produce exactly the same bytes.

Verify:

```text
HTTP method
request path
timestamp
nonce
raw body
HMAC algorithm
hex/base64 encoding
header names
clock tolerance
nonce uniqueness
```

Test with an actual request against:

```text
https://safa.masarax.com
```

Do not mark this PASS using unit tests alone.

---

# 11. WEBSITE LOGO / ANDROID ICON — PREVIOUS IMPLEMENTATIONS ARE WRONG

This requirement has been repeated multiple times.

The Android app icon must be the ACTUAL SAFA logo currently displayed on the website welcome page.

It must NOT be:

* a newly designed shield
* a recreated vector
* a guessed logo
* a different color
* a different composition
* an agent-created replacement

The canonical chain MUST be:

```text
CURRENT WEBSITE WELCOME PAGE LOGO
              ↓
CANONICAL ORIGINAL SAFA LOGO ASSET
              ↓
ANDROID LAUNCHER ICON
              ↓
ANDROID APP BRANDING
```

Identify the exact asset currently used by:

```text
backend/resources/views/welcome.blade.php
```

and use that exact asset as the source.

Do not modify the website logo to make it easier.

The website logo must remain unchanged.

---

# 12. ANDROID ICON IMPLEMENTATION

Do not simply claim that `ic_launcher_foreground.xml` is "1:1" if it is actually a manually recreated vector.

If the original logo is a PNG, use the actual PNG artwork.

Prepare proper Android launcher resources:

```text
mipmap-mdpi
mipmap-hdpi
mipmap-xhdpi
mipmap-xxhdpi
mipmap-xxxhdpi
mipmap-anydpi-v26
```

Use adaptive icon resources where appropriate, but do not distort or redesign the original artwork.

If adaptive-icon masking changes the visual identity, adjust the safe-zone composition while preserving the actual logo artwork.

`AndroidManifest.xml` must point to the canonical launcher icon.

Verify:

```text
android:icon
android:roundIcon
```

Do not use a different placeholder icon.

Also keep notification icons separate from launcher icons.

---

# 13. FAVICON ROUTES ARE CURRENTLY SUSPICIOUS

The current `web.php` implementation maps:

```text
/favicon.svg
```

to a PNG file.

That is not a valid SVG asset contract.

Fix favicon handling properly:

```text
/favicon.ico → actual ICO or compatible favicon response
/favicon.png → actual PNG
/favicon.svg → actual SVG
```

Do not return PNG bytes with `image/svg+xml`.

The favicon and welcome-page logo should derive from the same canonical branding asset where technically appropriate.

---

# 14. MAINACTIVITY STARTUP ARCHITECTURE

Do not initialize expensive/failure-prone components before the first UI unnecessarily.

Current:

```text
onCreate()
 ↓
database initialization
 ↓
repository
 ↓
TokenManager
 ↓
worker
 ↓
setContent()
```

Refactor startup so that:

```text
Application
 ↓
safe minimal initialization
 ↓
MainActivity
 ↓
first UI
 ↓
controlled initialization
```

However, do NOT simply move database initialization to a coroutine and hide crashes.

Initialization errors must be observable and recoverable.

The application must display a proper error state if initialization fails rather than immediately disappearing.

---

# 15. ADD A REAL STARTUP DIAGNOSTIC

Create a startup initialization layer that records safe diagnostic stages:

```text
START
KEYSTORE_INIT
DATABASE_PASSPHRASE_READY
SQLCIPHER_NATIVE_READY
ROOM_READY
TOKEN_MANAGER_READY
API_CLIENT_READY
WORK_MANAGER_READY
COMPOSE_READY
FIRST_SCREEN_READY
```

Never log:

```text
API secret
database passphrase
PIN
password
JWT
refresh token
```

If startup fails, log only:

```text
stage
exception class
safe exception message
stack trace
app version
Android version
SDK
ABI
page size
```

This is necessary so the next crash can be diagnosed instead of guessed.

---

# 16. RELEASE BUILD MUST BE TESTED SEPARATELY

Verify both:

```text
Debug APK
Release APK
```

Release must be tested with:

```text
R8 enabled
minification enabled
ProGuard rules enabled
```

Audit:

* Moshi
* Retrofit
* Room
* SQLCipher
* AndroidX Security
* Tink
* reflection
* serialization
* WorkManager

Do not add:

```proguard
-keep ** { *; }
```

as a blanket workaround.

Add only required keep rules.

---

# 17. CLEAN BUILD

After fixing the real root cause:

```bash
.\gradlew clean
.\gradlew test --continue
.\gradlew assembleDebug
.\gradlew assembleRelease
```

Then inspect the generated APKs.

Do not reuse old APKs.

Record:

```text
versionCode
versionName
APK size
SHA-256
native libraries
ABI
16 KB alignment
```

---

# 18. MANDATORY RUNTIME ACCEPTANCE TEST

The final build must be tested as:

### Debug

```text
uninstall
install
launch
```

### Release

```text
uninstall
install
launch
```

Then verify:

```text
✓ App opens
✓ App does not immediately stop
✓ First screen renders
✓ Actual SAFA logo is shown
✓ Launcher icon is actual website SAFA logo
✓ Offline launch works
✓ Online launch works
✓ Login screen works
✓ Real production API connection works
✓ Database opens
✓ SQLCipher encryption remains enabled
✓ Process restart works
✓ Device reboot works
✓ Reinstall behavior is understood
✓ App update does not destroy encrypted local data
✓ API authentication works
✓ Sync works
✓ Logout works
```

---

# 19. NO FALSE GO-LIVE VERDICT

This is mandatory.

If physical Android 16 runtime testing is unavailable, report:

```text
UNVERIFIED — Android 16 runtime unavailable
```

Do NOT write:

```text
GO LIVE
```

Do NOT write:

```text
PASS
```

for runtime behavior that was not actually tested.

The current status is:

```text
BLOCKED — NOT READY FOR GO LIVE
```

until the Android 16 startup crash is actually resolved and verified.

---

# 20. FINAL REPORT REQUIREMENTS

Update:

```text
FINAL_GO_LIVE_AUDIT.md
```

with:

### Android 16 / API 36 Runtime Compatibility Verification

Include:

```text
Actual crash symptom
Actual crash evidence
Root cause
Changed files
Dependency changes
SQLCipher migration
KeyStore behavior
API security verification
16 KB native library verification
APK verification
Debug runtime result
Release runtime result
Logo verification
Production API verification
Remaining risks
Final verdict
```

Do not copy previous audit claims unless they have been re-verified against the current `main` branch.

---

## FINAL RULE

Do not make another speculative Android 16 fix.

Do not repeatedly change KeyStore code without evidence.

Do not repeatedly recreate the logo.

Do not declare GO LIVE because `33/33` backend tests and `27/27` Android unit tests pass.

The actual acceptance criterion is simple:

```text
INSTALL APK
    ↓
TAP SAFA ICON
    ↓
APP STAYS OPEN
    ↓
FIRST SCREEN RENDERS
    ↓
REAL API WORKS
    ↓
DATABASE WORKS
    ↓
LOGIN WORKS
```

Until this works on Android 16, SAFA is **NOT READY FOR GO LIVE**.

### Security instruction

The production `.env` is private and must remain private.

Never expose, print, commit, quote, reproduce, or embed any `.env` credential, API key, API secret, APP_KEY, database credential, or other secret in GitHub, APK, logs, audit reports, screenshots, tests, or agent responses.
