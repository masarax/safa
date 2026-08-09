STOP GUESSING. Do NOT make another speculative Android 16-only fix.

I have reviewed the current `main` branch of `https://github.com/masarax/safa.git`, and the application is still stopping/crashing on normal Android devices, not only Android 16.

Your previous audits repeatedly focused on Android 16, `Build.getSerial()`, KeyStore, SQLCipher, and physical-device verification, but the actual application still does not stay running. Therefore, this time you must perform a COMPLETE STARTUP CRASH INVESTIGATION from the repository itself.

IMPORTANT:

* Do NOT declare the app fixed.
* Do NOT write another `FINAL_GO_LIVE_AUDIT.md` claiming success.
* Do NOT assume Android 16 is the cause.
* Do NOT remove security/encryption just to make the app start.
* Do NOT use an unencrypted database fallback.
* Do NOT expose, print, commit, log, or reproduce any `.env` secrets, API keys, API secrets, APP_KEY, database password, signing credentials, or other sensitive values. Treat `.env` as PRIVATE and SECRET.
* Do NOT ask me to provide secrets.
* Never put secret values into Logcat, audit files, GitHub commits, source code, screenshots, or diagnostic output.

## 1. FIRST: PROVE WHERE THE APP DIES

Investigate the complete Android process startup lifecycle, not only `MainActivity.onCreate()`.

Trace:

1. Application process creation
2. AndroidManifest initialization
3. ContentProviders / automatically initialized AndroidX components
4. WorkManager initialization
5. MainActivity class loading
6. MainActivity.onCreate()
7. `enableEdgeToEdge()`
8. `AppDatabase.getDatabase()`
9. `KeyStoreHelper`
10. `SQLiteDatabase.loadLibs()`
11. SQLCipher native library loading
12. Room database construction
13. `AppRepository`
14. `TokenManager`
15. `DeviceSecurityHelper`
16. KeyStore / EncryptedSharedPreferences
17. `SafaViewModelFactory`
18. ViewModel creation
19. `setContent()`
20. Compose theme/UI initialization
21. first screen rendering
22. Retrofit/HTTP/API initialization
23. WorkManager periodic sync
24. any startup coroutine/thread/background worker.

Do not assume the crash is inside MainActivity.

## 2. INVESTIGATE NATIVE CRASH POSSIBILITY

The current `AppDatabase.kt` directly executes:

`SQLiteDatabase.loadLibs(context.applicationContext)`

before Room is built.

This is especially important because native `.so` loading failures, ABI mismatch, linker errors, SIGSEGV, SIGABRT, `UnsatisfiedLinkError`, `NoSuchMethodError`, and native SQLCipher crashes are not necessarily recoverable with Kotlin `try/catch`.

Determine:

* exact SQLCipher dependency and version
* AndroidX SQLite dependency versions
* Room version
* NDK/native ABI requirements
* whether APK contains:
  `arm64-v8a`
  `armeabi-v7a`
  `x86`
  `x86_64`
* whether all required `.so` libraries are actually packaged
* whether debug and release APKs contain identical native libraries
* whether minSdk/targetSdk/compileSdk combinations are valid
* whether packagingOptions/jniLibs configuration is correct
* whether R8/minification changes release behavior
* whether the crash happens before Kotlin code can catch it.

Do NOT simply add a fallback to normal SQLite.

The database must remain encrypted.

## 3. INVESTIGATE ANDROID VERSION COMPATIBILITY

Do a matrix-style audit for:

Android 8/9
Android 10
Android 11
Android 12
Android 13
Android 14
Android 15
Android 16

Find every API call that can cause runtime failure on older Android versions.

Search the entire Android source for:

* Build.getSerial
* hidden/system APIs
* API-level-specific APIs
* unsupported permissions
* biometric APIs
* edge-to-edge APIs
* notification APIs
* foreground service APIs
* exact alarm APIs
* storage APIs
* package/signature APIs
* WebView APIs
* FileProvider issues
* Activity/Context assumptions
* Android 12 exported requirements
* Android 13 notification behavior
* Android 14/15/16 restrictions.

Do not focus only on Build.getSerial.

## 4. INVESTIGATE AndroidManifest.xml

Audit every manifest component.

Current manifest includes:

* MainActivity
* FileProvider
* networkSecurityConfig
* INTERNET
* ACCESS_NETWORK_STATE
* USE_BIOMETRIC

Determine whether any provider/component is initialized before MainActivity and can crash the process.

Also inspect:

* file_paths.xml
* backup_rules.xml
* data_extraction_rules.xml
* network_security_config.xml
* themes.xml
* styles
* launcher resources
* mipmap resources.

If WorkManager is auto-initialized through AndroidX Startup, investigate that separately.

## 5. INVESTIGATE WORKMANAGER

Do not merely catch:

`AutoSyncWorker.schedulePeriodicSync(...)`

because WorkManager may initialize before MainActivity.

Inspect:

* Worker implementation
* Worker constructor
* dependency injection
* CoroutineWorker/Worker type
* network constraints
* database access
* TokenManager usage
* API calls
* retry logic
* initialization provider
* startup initializer configuration.

Determine whether WorkManager can crash the process before MainActivity.

## 6. INVESTIGATE TOKENMANAGER / SECURITY

The current TokenManager contains:

`DEFAULT_URL = "https://safa.masarax.com/api/"`

That must be verified against the actual Laravel routes.

Also investigate why:

`DEFAULT_API_KEY = ""`
`DEFAULT_API_SECRET = ""`

exist and where API credentials are expected to come from.

Trace the complete authentication flow:

Android
→ API client
→ Laravel middleware
→ API key authentication
→ login endpoint
→ token creation
→ refresh
→ device token
→ fingerprint token
→ session token.

Do NOT put actual API secrets into the Android source code.

Determine whether the app can start completely OFFLINE without crashing.

Startup must NOT depend on successful API connectivity.

An unreachable server, SSL issue, DNS failure, HTTP 401/403, API key failure, timeout, or Laravel error must NOT terminate the Android process.

## 7. VERIFY PRODUCTION BACKEND CONTRACT

Production backend:

`https://safa.masarax.com`

Audit the repository's actual API routes and compare them with every Android API endpoint.

Find:

* exact login endpoint
* base URL construction
* `/api/` prefix
* HTTP methods
* headers
* API key headers
* authentication headers
* CSRF assumptions
* Sanctum/session/token assumptions
* JSON response structures
* SSL configuration
* timeout configuration.

Use only public/non-secret endpoint information in the report.

Do not output any API secret values.

## 8. INVESTIGATE THE DATABASE FROM ZERO

Audit:

* Room version
* SQLCipher version
* database version
* migrations 1→2, 2→3, 3→4, 4→5
* entity schema
* DAO initialization
* passphrase generation
* KeyStore access
* encrypted SharedPreferences
* database file creation
* first-install behavior
* existing-install/update behavior
* corrupted database behavior.

Important:

The current code executes SQLCipher native loading during startup.

Determine whether this is the actual universal crash.

Also determine whether:

`KeyStoreHelper.getOrGenerateDbPassphrase(context)`

can throw on ordinary devices.

Test first install and upgrade scenarios separately.

## 9. INVESTIGATE KEYSTORE CORRECTLY

Do not keep saying "Android 16 KeyStore issue" without evidence.

Determine whether the explicit:

`KeyGenParameterSpec`

is actually compatible with the AndroidX Security Crypto version used by this project.

Check:

* MasterKey.Builder
* KeyGenParameterSpec
* AES256_GCM
* AES256_SIV
* EncryptedSharedPreferences
* Android Keystore provider
* alias reuse
* first installation
* app upgrade
* uninstall/reinstall
* OS upgrade.

Also verify whether two different components are trying to manage the same MasterKey alias in incompatible ways.

## 10. INVESTIGATE THE ACTUAL APP LOGO

The logo problem is STILL NOT FIXED.

Do not simply say:

"copied safa-logo.png"

The actual Android launcher icon and the in-app logo must be verified visually and technically against the canonical SAFA logo.

Find the canonical source asset in the repository.

Then audit:

* launcher foreground
* launcher background
* adaptive icon
* round icon
* mipmap-anydpi-v26
* mipmap resources
* drawable resources
* in-app header logo
* login screen logo
* splash/startup logo
* settings logo
* remotely loaded logo.

Do not redesign the logo.

The canonical SAFA logo must be used exactly as the source artwork.

If adaptive icon masking prevents a 1:1 appearance, construct the adaptive icon correctly while preserving the actual logo artwork.

Do not invent a different SVG.

## 11. INVESTIGATE RESOURCE / THEME STARTUP CRASH

Inspect:

* themes.xml
* colors.xml
* dimens
* strings
* launcher XML
* vector XML
* adaptive icons
* Compose theme
* fonts
* drawable references.

Check for:

* missing resources
* invalid vector paths
* unsupported attributes
* API-specific drawable resources
* malformed adaptive icon
* resource linking errors
* runtime resource crashes.

## 12. RELEASE VS DEBUG

Audit BOTH:

* debug APK
* release APK

Determine:

* minification
* R8
* ProGuard
* resource shrinking
* signing
* ABI packaging
* native libraries
* manifest merging
* applicationId
* build variants.

A debug build working while release crashes must be detected.

A release build working while debug crashes must also be detected.

## 13. DO NOT ACCEPT UNIT TESTS AS PROOF OF STARTUP

33/33 Laravel tests and 27/27 Android unit tests do NOT prove that the APK launches.

Add actual startup-oriented tests where possible.

At minimum create:

* Application startup test
* MainActivity launch test
* database initialization test
* KeyStore initialization test
* TokenManager initialization test
* WorkManager initialization test
* offline startup test
* API unavailable startup test.

If instrumentation tests cannot run in the current environment, explicitly say so.

Do not claim they passed.

## 14. MOST IMPORTANT: BUILD A CRASH DIAGNOSTIC APK

Before attempting another "fix", produce a diagnostic build whose ONLY purpose is to identify the crash.

It must record safe startup stages such as:

PROCESS_START
APPLICATION_CREATE
PROVIDER_INITIALIZATION
MAIN_ACTIVITY_CREATE
DATABASE_START
KEYSTORE_START
SQLCIPHER_LOAD_START
SQLCIPHER_LOAD_SUCCESS
ROOM_BUILD_SUCCESS
TOKEN_MANAGER_START
TOKEN_MANAGER_SUCCESS
VIEWMODEL_CREATE
COMPOSE_START
WORKMANAGER_START

BUT:

NEVER log:

* API keys
* API secrets
* access tokens
* refresh tokens
* passwords
* APP_KEY
* DB credentials
* signing credentials
* passphrases.

If the crash is a native crash, provide a way to identify the native crash from Logcat / tombstone.

## 15. VERY IMPORTANT: PHYSICAL DEVICE TEST MUST NOT BE THE ONLY PLAN

I understand that you cannot physically operate my phone.

But you must still determine everything that can be determined from the repository and build.

If ADB is unavailable, state exactly:

"Repository/build investigation completed; runtime crash remains unverified."

Do NOT convert that into:

"Android 16 is probably the cause."

## 16. DO NOT CHANGE UNRELATED THINGS

Do not keep changing:

* logo
* favicon
* audit documents
* API configuration
* database security

unless the investigation proves they are related.

Do not create dozens of new audit markdown files.

One investigation report is enough.

## 17. REQUIRED FINAL REPORT

Create/update:

`ROOT_CAUSE_INVESTIGATION.md`

It must contain:

### A. Confirmed Facts

Only facts proven from source/build.

### B. Suspected Causes

Clearly separated from confirmed causes.

### C. Ruled-Out Causes

Anything investigated and proven not to be responsible.

### D. Startup Dependency Graph

Show exactly what initializes before what.

### E. Crash Boundary

Identify the earliest possible crash point.

### F. Native Library Audit

Complete SQLCipher/ABI/native `.so` analysis.

### G. Android Compatibility Audit

Android 8→16.

### H. Backend/API Contract Audit

Android ↔ Laravel.

### I. Security Audit

KeyStore, encrypted DB, token storage, API authentication.

### J. Branding Audit

Canonical SAFA logo → launcher → in-app UI.

### K. Required Fixes

Only fixes supported by evidence.

### L. Verification Status

Clearly state what was actually executed and what could not be executed.

The final status MUST NOT say GO LIVE unless the application has actually been demonstrated to launch and remain running.

## 18. IMPORTANT SECURITY RULE

The `.env` file and all credentials/secrets I provided are PRIVATE.

Do not:

* print them
* echo them
* copy them into source code
* put them into audit files
* put them into GitHub
* put them into logs
* mention their actual values in the response.

Only refer to them by variable name when necessary.

## 19. FINAL INSTRUCTION

Do not give me another generic audit summary.

Find the actual reason why this Android application stops immediately.

Start from the earliest possible process initialization and trace the entire dependency chain until the first crash boundary.

If the evidence points to SQLCipher/native libraries, prove it.
If it points to KeyStore, prove it.
If it points to WorkManager, prove it.
If it points to API/network initialization, prove it.
If it points to Compose/resources, prove it.
If it points to the launcher/manifest/provider, prove it.
If it points to something else, identify it.

Only after identifying the evidence-based root cause should you modify the code.

And after fixing it:

1. build debug APK,
2. build release APK,
3. run all available tests,
4. run startup/instrumentation tests if possible,
5. verify APK contents/ABIs/native libraries,
6. verify backend endpoint contract,
7. verify logo resources,
8. commit everything to GitHub,
9. provide the exact commit SHA,
10. provide exact APK hashes,
11. provide a concise ROOT_CAUSE_INVESTIGATION.md.

DO NOT GUESS. FIND THE ROOT CAUSE FIRST.
