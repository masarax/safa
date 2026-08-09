# SAFA — FORENSIC STARTUP CRASH ISOLATION — DO NOT GUESS

The SAFA Android application STILL crashes/stops immediately when the user taps the app icon on physical Android devices.

Do NOT make another speculative fix and do NOT declare a root cause based on assumptions.

You must now perform a true forensic startup isolation of the repository at the current HEAD.

## CRITICAL FACTS

The current repository is:

`https://github.com/masarax/safa`

Current HEAD:

`9b6994720b53d01f73899e5d8bbebfeeac4c2bc8`

The application crashes immediately on physical Android hardware.

The previous reports repeatedly claimed that startup crashes were fixed, but the application still stops.

Therefore:

**DO NOT TRUST PREVIOUS AUDIT REPORTS AS PROOF.**
Inspect the actual source code and prove every conclusion.

---

# 1. FIRST: IDENTIFY EVERY POSSIBLE PRE-UI CRASH POINT

Trace the complete Android startup lifecycle from:

Application process creation
→ AndroidManifest
→ Application initialization
→ AndroidX Startup/providers
→ FileProvider
→ Activity instantiation
→ Activity.attach
→ Activity.onCreate
→ super.onCreate
→ theme/window initialization
→ enableEdgeToEdge
→ database initialization
→ SQLCipher native loading
→ KeyStore
→ SharedPreferences
→ Repository
→ TokenManager
→ ViewModel creation
→ Compose setContent
→ first composable.

Do NOT assume MainActivity.onCreate() is the earliest possible crash boundary.

Explicitly inspect:

* AndroidManifest.xml
* Application class, if any
* all ContentProviders
* AndroidX Startup providers
* FileProvider
* theme resources
* styles/themes
* values-v*/themes.xml
* drawable/mipmap resources
* networkSecurityConfig
* MainActivity
* every static/object initialization used by MainActivity
* AppDatabase
* KeyStoreHelper
* DeviceSecurityHelper
* TokenManager
* SafaViewModelFactory
* SafaViewModel
* AutoSyncWorker
* all classes referenced during startup.

---

# 2. DO NOT USE A SINGLE LARGE try/catch

The current code claims to have a startup error boundary, but this is insufficient.

Instrument startup with EXACT sequential checkpoints.

For example:

STARTUP_000_PROCESS
STARTUP_010_ACTIVITY_CREATED
STARTUP_020_AFTER_SUPER_ON_CREATE
STARTUP_030_BEFORE_EDGE_TO_EDGE
STARTUP_040_AFTER_EDGE_TO_EDGE
STARTUP_050_BEFORE_KEYSTORE
STARTUP_060_AFTER_KEYSTORE
STARTUP_070_BEFORE_SQLCIPHER
STARTUP_080_AFTER_SQLCIPHER
STARTUP_090_BEFORE_ROOM
STARTUP_100_AFTER_ROOM
STARTUP_110_BEFORE_REPOSITORY
STARTUP_120_AFTER_REPOSITORY
STARTUP_130_BEFORE_TOKEN_MANAGER
STARTUP_140_AFTER_TOKEN_MANAGER
STARTUP_150_BEFORE_VIEWMODEL_FACTORY
STARTUP_160_AFTER_VIEWMODEL_FACTORY
STARTUP_170_BEFORE_WORK_MANAGER
STARTUP_180_AFTER_WORK_MANAGER
STARTUP_190_BEFORE_SET_CONTENT
STARTUP_200_SET_CONTENT_STARTED
STARTUP_210_FIRST_COMPOSE_FRAME

Every checkpoint must be logged independently.

The purpose is to identify the EXACT last successful stage.

---

# 3. IMPORTANT: enableEdgeToEdge MUST NOT BE OUTSIDE THE DIAGNOSTIC BOUNDARY

Current source was inspected and currently contains:

super.onCreate(savedInstanceState)

enableEdgeToEdge()

var initError: Throwable? = null

try {
...
}

Therefore enableEdgeToEdge is still outside the try/catch boundary.

Fix the architecture so that the startup instrumentation can determine whether:

* super.onCreate()
* theme initialization
* enableEdgeToEdge()

is responsible.

Do not claim it is protected unless the actual code proves it.

---

# 4. CREATE A MINIMAL BOOT MODE

This is mandatory.

Temporarily create a diagnostic startup path where MainActivity does ONLY:

super.onCreate(savedInstanceState)

setContent {
Text("SAFA BOOT OK")
}

No:

* SQLCipher
* Room
* KeyStore
* SharedPreferences
* TokenManager
* Repository
* WorkManager
* ViewModel
* network
* API
* biometric
* Coil
* custom logo loading
* custom application logic.

The purpose is to determine whether the application can launch at all.

If this minimal boot still crashes, the problem is NOT Room, SQLCipher, API, KeyStore, or TokenManager.

Then isolate:

PHASE A:
super.onCreate + setContent only

PHASE B:

* enableEdgeToEdge

PHASE C:

* KeyStore

PHASE D:

* SQLCipher native load

PHASE E:

* Room

PHASE F:

* Repository

PHASE G:

* TokenManager

PHASE H:

* ViewModel

PHASE I:

* WorkManager

PHASE J:
full application.

Each phase must produce a separate build/test result.

DO NOT jump directly from full application to another speculative patch.

---

# 5. SQLCIPHER MUST BE TREATED AS A HIGH-RISK STARTUP DEPENDENCY

Current dependency:

net.zetetic:android-database-sqlcipher:4.5.4

Current code directly calls:

SQLiteDatabase.loadLibs(context)

before Room.

Investigate:

* exact SQLCipher version
* Android API compatibility
* NDK/ABI compatibility
* arm64-v8a
* armeabi-v7a
* x86
* x86_64
* 16 KB page-size compatibility
* native linker requirements
* whether libsqlcipher.so can actually load on the target Android versions
* whether loading the library causes process-level native termination rather than a catchable Java exception.

Do NOT say "the .so exists inside the APK, therefore SQLCipher is fine."

Presence of a .so file does NOT prove successful runtime loading.

---

# 6. KEYSTORE MUST BE ISOLATED

Completely remove KeyStore initialization from the first diagnostic build.

Then test KeyStore independently.

Inspect:

* MasterKey
* KeyGenParameterSpec
* Android Keystore provider
* AES/GCM configuration
* EncryptedSharedPreferences
* aliases
* existing encrypted preferences from previous installations
* behavior after uninstall/reinstall
* behavior after app update
* behavior after Android OS update.

Determine whether an existing installation with old encryption metadata can crash during startup.

---

# 7. SHARED PREFERENCES MUST ALSO BE ISOLATED

TokenManager currently initializes:

context.getSharedPreferences("safa_secure_prefs", Context.MODE_PRIVATE)

Do not assume this is harmless.

Test:

* clean install
* existing installation
* upgrade over previous APK
* corrupted preference state
* encryption migration state.

---

# 8. TEST BOTH DEBUG AND RELEASE SEPARATELY

Do not use only unit tests.

Build:

* debug APK
* release APK

Then inspect:

* manifest
* resources
* native libraries
* ABI splits
* R8 output
* ProGuard
* signing
* application ID
* launch activity
* themes.

Release must not be considered verified merely because it compiles.

---

# 9. VERY IMPORTANT: LOGCAT / CRASH FORENSICS

If physical ADB is unavailable, DO NOT claim the crash has been diagnosed.

Instead create a diagnostic APK whose sole purpose is collecting startup evidence.

The diagnostic build must make the startup stage visible in logcat.

Provide exact commands the user can run:

adb devices
adb logcat -c
adb logcat -v threadtime

Then:

adb install -r app-debug.apk
adb shell am force-stop com.safa.account
adb shell monkey -p com.safa.account 1

Capture:

* FATAL EXCEPTION
* AndroidRuntime
* libc
* DEBUG
* linker
* SQLite
* SQLCipher
* SecurityException
* Resources$NotFoundException
* InflateException
* NoSuchMethodError
* UnsatisfiedLinkError
* ExceptionInInitializerError
* ClassNotFoundException
* VerifyError
* IllegalStateException
* ActivityTaskManager
* WindowManager.

If the crash is native, Java try/catch is NOT sufficient.

---

# 10. DO NOT CLAIM "FULLY SUPPORTED ANDROID 7-16"

The previous report claims Android 7-16 support without physical runtime verification.

That statement is NOT acceptable.

Use:

UNVERIFIED

until actual runtime testing proves compatibility.

---

# 11. API / SECURITY AUDIT

Inspect the entire API security chain, but DO NOT expose secrets in reports.

Audit:

* production API base URL
* API key loading
* API secret loading
* Gradle Secrets Plugin
* BuildConfig
* resources
* strings.xml
* generated BuildConfig
* APK assets
* decompiled APK
* Retrofit
* OkHttp
* interceptors
* authentication headers
* refresh token flow
* device token
* fingerprint token
* TLS
* cleartext traffic
* networkSecurityConfig
* certificate validation
* server-side API authentication
* 401/403 handling
* startup network dependencies.

Confirm that API credentials are NOT required for initial UI startup.

The app must be able to display the login screen even if the server is completely offline.

DO NOT print, commit, log, or report actual secret values.

---

# 12. .ENV SECURITY

The repository must NEVER expose production secrets.

Do not print actual values from:

.env
.env.example
local.properties
keystore files
Gradle secrets
BuildConfig
APK resources.

If a secret has already been exposed in any report, immediately flag it as compromised and recommend rotation.

Do not copy the secret into any new file or commit.

---

# 13. BRANDING — FIX THE ACTUAL ORIGINAL LOGO

The previous report incorrectly claims:

"1:1 pixel copies of authentic brand asset safa-logo.png"

This is NOT true for the adaptive Android launcher.

Actual current `ic_launcher_foreground.xml` contains manually-created vector paths rather than the original PNG artwork.

Therefore stop claiming that the launcher uses the original logo.

The requirement is:

**Use the actual canonical `backend/public/safa-logo.png` asset itself.**

Do NOT redraw it.

Do NOT approximate it.

Do NOT manually recreate it as vector paths.

Do NOT generate a new logo.

The Android launcher icon must visually use the exact original SAFA logo artwork.

Inspect Android adaptive icon behavior carefully.

If Android requires an adaptive icon, create the correct adaptive-icon composition using the original artwork while preserving the actual logo appearance.

Also inspect:

* mipmap-anydpi-v26
* mipmap-anydpi
* mipmap-mdpi
* mipmap-hdpi
* mipmap-xhdpi
* mipmap-xxhdpi
* mipmap-xxxhdpi
* ic_launcher
* ic_launcher_round
* monochrome icon.

The launcher icon displayed on Android must be the actual SAFA logo, not an agent-generated recreation.

---

# 14. DO NOT CHANGE BUSINESS LOGIC DURING CRASH ISOLATION

Do NOT refactor unrelated:

* dashboard
* customer
* supplier
* transaction
* wallet
* expense
* reports
* synchronization
* localization
* UI logic.

Only modify what is necessary to isolate and fix the startup crash.

---

# 15. REQUIRED FINAL REPORT

Create:

FORENSIC_STARTUP_ROOT_CAUSE.md

It must contain:

1. Exact confirmed root cause.
2. Exact file and line.
3. Why it crashes.
4. Why previous fixes did not solve it.
5. Exact startup checkpoint where failure occurs.
6. Whether crash is Java/Kotlin, resource/theme, native linker, SQLCipher, KeyStore, framework, or something else.
7. Evidence.
8. Minimal reproduction/isolation result.
9. Exact fix applied.
10. Security impact.
11. API impact.
12. Branding verification.
13. Debug APK verification.
14. Release APK verification.
15. Physical device status.

If the exact physical crash cannot be proven without logcat, explicitly say:

ROOT CAUSE NOT YET PROVEN

Do NOT invent a root cause.

---

# 16. STOP CONDITIONS

Do NOT mark:

READY FOR GO LIVE

until:

* minimal boot works
* full startup works
* debug APK launches
* release APK launches
* no immediate process termination
* SQLCipher verified at runtime
* KeyStore verified at runtime
* API login verified
* original SAFA logo visually verified
* clean install verified
* upgrade install verified.

Until then:

BLOCKED — ROOT CAUSE NOT PROVEN / NOT READY FOR GO LIVE

This is a forensic investigation, not a documentation exercise.

Do not spend the next iteration merely rewriting audit markdown files.

First isolate the actual crash.
Then fix it.
Then prove the fix.
