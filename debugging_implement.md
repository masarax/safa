STOP making another generic audit report. The app still crashes immediately on launch on physical Android devices, and the actual root cause has NOT been proven.

You must now perform a CRASH-FIRST FORENSIC INVESTIGATION against the CURRENT GitHub `main` HEAD of:

https://github.com/masarax/safa.git

Do NOT assume Android 16 is the root cause. The user has confirmed that the application stops immediately on launch, so investigate Android 7+ / API 24 through Android 16 compatibility and all startup paths.

IMPORTANT SECURITY RULE:
The user's production `.env` is PRIVATE. NEVER print, commit, expose, copy, log, echo, or include any `.env` values, API keys, API secrets, APP_KEY, database credentials, passwords, or signing secrets in any report, source file, log, commit, response, or artifact. You may inspect configuration structure locally if necessary, but redact all secret values completely.

## PHASE 1 — PROVE THE ACTUAL CRASH

Do NOT claim the crash is fixed without an actual crash stack trace.

Audit and test the complete startup chain:

AndroidManifest
→ Application/provider initialization
→ theme/resource initialization
→ MainActivity.onCreate()
→ enableEdgeToEdge()
→ Compose initialization
→ AppDatabase
→ SQLCipher native loading
→ KeyStore
→ EncryptedSharedPreferences
→ AppRepository
→ TokenManager
→ DeviceSecurityHelper
→ SafaViewModelFactory
→ WorkManager
→ first Compose screen

Search the ENTIRE repository for:

* Application subclasses
* AndroidX Startup providers
* ContentProviders
* WorkManager initialization
* Firebase/third-party SDK initialization
* static/object initialization
* companion-object initialization
* native library loading
* SQLCipher
* Room
* KeyStore
* EncryptedSharedPreferences
* MasterKey
* Build.getSerial / Build.SERIAL
* enableEdgeToEdge
* Material themes
* reflection
* JNI
* ProGuard/R8 rules
* minSdk/targetSdk/compileSdk compatibility problems

Do not rely only on the previously created reports.

## PHASE 2 — FIX THE DIAGNOSTIC BOUNDARY

The current MainActivity calls:

enableEdgeToEdge()

BEFORE the try/catch startup boundary.

Therefore a crash inside enableEdgeToEdge(), Activity/theme/window initialization, or related framework code can bypass the current diagnostic UI.

Move the diagnostic boundary to the earliest safe point possible and determine whether the crash happens BEFORE setContent().

Do not simply catch Throwable and declare success.

## PHASE 3 — ISOLATE THE CRASH

Create temporary diagnostic/minimal startup instrumentation/build if necessary.

Test startup in progressively isolated modes:

TEST A:
MainActivity → setContent() only.
No Room, SQLCipher, KeyStore, TokenManager, WorkManager, repository, or ViewModel.

TEST B:
MainActivity + Compose/theme only.

TEST C:
Add KeyStore.

TEST D:
Add SQLCipher/Room.

TEST E:
Add Repository/TokenManager.

TEST F:
Add WorkManager.

TEST G:
Full application.

The purpose is to identify the EXACT component that causes the process to terminate.

If the process dies without a Kotlin exception, inspect native crash output:

* AndroidRuntime
* libc
* linker
* DEBUG
* FATAL EXCEPTION
* SIGSEGV
* SIGABRT
* UnsatisfiedLinkError
* dlopen
* linker64
* native library relocation
* page-size/16KB issues

## PHASE 4 — SQLCIPHER / NATIVE ABI

Do not assume that having `libsqlcipher.so` for four ABIs proves the native library is compatible.

Verify:

* actual SQLCipher version
* Android database SQLCipher dependency version
* NDK compatibility
* ABI packaging
* ELF architecture
* ELF alignment
* 4KB/16KB page-size compatibility
* native dependencies
* whether `SQLiteDatabase.loadLibs()` is safe on supported API levels
* whether the specific SQLCipher release is compatible with current Android/AGP/NDK
* whether the library causes process-level native crash

If SQLCipher is the crash source, choose a modern supported solution compatible with the project's security requirements instead of merely wrapping `loadLibs()` in try/catch.

Do NOT reintroduce an unencrypted SQLite fallback.

## PHASE 5 — KEYSTORE

Audit the current KeyStore architecture on Android API 24–36.

Pay particular attention to the fact that both:

* KeyStoreHelper
* DeviceSecurityHelper

construct/use MasterKey and EncryptedSharedPreferences.

Verify:

* MasterKey creation
* alias lifecycle
* AES-GCM parameters
* EncryptedSharedPreferences compatibility
* API-level compatibility
* Android Keystore behavior
* app reinstall/update behavior
* first-install behavior
* corrupted/invalid keystore state
* whether the same alias/spec is being used incorrectly
* whether fallback logic can generate a NEW database passphrase and thereby make an existing encrypted DB unreadable

Do not claim "zero data loss" unless the implementation actually guarantees passphrase continuity.

## PHASE 6 — API / SECURITY CONTRACT

Audit the complete Android ↔ Laravel API contract.

Production base URL:

https://safa.masarax.com

Verify the actual Android URL, API paths, HTTP client, headers, authentication middleware, CSRF assumptions, Sanctum/session/token behavior, API key handling, API secret handling, device token handling, fingerprint handling, SSL/TLS behavior, and login request.

IMPORTANT:
The Android TokenManager currently contains empty default values for API key/API secret.

Determine EXACTLY how production API authentication is supposed to work.

Trace:

Android request
→ headers
→ Laravel route
→ middleware
→ API authentication
→ controller
→ response

Do not put production secrets into the Android APK or source code.

Do not expose secrets in the report.

Also determine whether API authentication failure can happen before UI rendering. If not, explicitly state that it cannot explain the immediate startup crash.

## PHASE 7 — BACKEND HEALTH

Verify that:

https://safa.masarax.com

is reachable and that the required API routes exist.

Test only safe/non-secret endpoints.

Determine:

* HTTPS certificate validity
* API availability
* expected HTTP status
* route availability
* response structure
* authentication requirements

Do not send or expose production secrets.

## PHASE 8 — ANDROID LOGO — EXACT ORIGINAL ASSET

The current Android launcher still does NOT show the user's original SAFA logo.

The user explicitly requires the ORIGINAL `safa-logo.png` from the repository/backend, NOT an AI-generated/recreated logo.

Audit ALL launcher resources, not just drawable:

* AndroidManifest icon
* android:icon
* android:roundIcon
* mipmap-anydpi-v26
* mipmap-anydpi
* mipmap-hdpi
* mipmap-mdpi
* mipmap-xhdpi
* mipmap-xxhdpi
* mipmap-xxxhdpi
* ic_launcher
* ic_launcher_round
* adaptive foreground
* adaptive background
* legacy launcher icons

Find and remove/replace every old generated SAFA icon.

Use the exact original `backend/public/safa-logo.png` as the canonical source asset.

Do NOT redraw, regenerate, vectorize, reinterpret, recolor, or approximate the logo.

The launcher must display the original artwork faithfully.

Also audit the in-app logo separately from the launcher icon.

## PHASE 9 — TEST MATRIX

The project declares minSdk 24.

Therefore do not only test Android 16.

Verify compatibility for:
API 24+
API 26+
API 28+
API 29+
API 30+
API 33+
API 34+
API 35+
API 36

If a physical device is unavailable, use every available emulator/runtime in the environment and clearly state exactly which runtime was used.

Do NOT write "fully supported" merely because the code compiles.

## PHASE 10 — NO FALSE VERDICTS

Do NOT produce another report saying:

"33/33 tests passed"
"27/27 tests passed"
"APK generated"
"Physical device unavailable"

unless the actual crash cause has been identified.

Unit tests do NOT prove Android application startup.

A successful APK build does NOT prove runtime startup.

Presence of native libraries does NOT prove native compatibility.

A try/catch around initialization does NOT prove process-level native crashes are handled.

## REQUIRED FINAL OUTPUT

Create:

ROOT_CAUSE_INVESTIGATION.md

It must contain:

1. Exact crash location
2. Exact exception/error/native signal
3. Exact stack trace or native crash evidence
4. Why the previous fixes did not solve it
5. Root cause classification:

   * Kotlin/Java exception
   * Android framework/theme
   * KeyStore
   * SQLCipher/Room
   * native linker/JNI
   * WorkManager/provider
   * API/authentication
   * resource/launcher
   * R8/ProGuard
   * other
6. Minimal reproduction/isolation result
7. Exact code fix
8. Security implications
9. API contract verification
10. Original logo verification
11. Test results
12. Remaining blockers

MOST IMPORTANT:

Do not stop at "physical device unavailable".

If the environment cannot access a physical device, reproduce the startup path using emulator/API runtimes and static/native analysis, and identify the most probable root cause with concrete evidence.

Do not make another speculative fix.

First identify the exact failure, then fix it, then rebuild, then test again.

Only after the actual startup crash is resolved should the project move toward GO LIVE.
