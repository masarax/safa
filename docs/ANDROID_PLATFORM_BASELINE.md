# Android 2026 stable platform baseline

Issue: #240

This baseline is intentionally limited to stable production Android/Jetpack/toolchain releases. Alpha, beta and RC artifacts are excluded.

## Adopted baseline

- Android Gradle Plugin: 9.3.1
- Gradle wrapper: 9.7.0
- Kotlin Compose plugin: 2.4.10
- KSP: 2.3.11
- compileSdk: API 37
- targetSdk: API 36 (release-policy target remains unchanged by this build-platform migration)
- Compose BOM: 2026.08.00 / Compose 1.12 stable family
- AndroidX Core KTX: 1.19.0
- Activity Compose: 1.13.0
- Lifecycle runtime/runtime-compose/viewmodel-compose: 2.11.0
- Navigation Compose: 2.9.8
- DataStore Preferences: 1.2.1
- WorkManager: 2.11.2
- CameraX camera2/core/lifecycle/view: 1.6.1
- Android test core/runner: 1.7.0
- Kotlin coroutines core/android/test: 1.11.0
- Play Services Location: 21.4.0
- Roborazzi: 1.72.0
- desugar_jdk_libs: 2.1.5

Compose 1.12 requires compileSdk 37 and an AGP 9.1.2+ baseline, so AGP/Gradle/Kotlin/Compose/compileSdk move together rather than as independent dependency bumps.

## Deliberately separate compatibility changes

Retrofit 3, OkHttp 5/logging-interceptor 5 and Mockito-Kotlin 6 are major-version migrations rather than requirements of the Android platform baseline. They remain separate compatibility reviews and must not be silently mixed into this platform change.

AndroidX Security Crypto is also excluded from this platform PR because credential-vault migration #241 owns that sensitive upgrade path.

## Verification gate

The branch is mergeable only after Android Production CI proves:

- debug unit tests and lint;
- debug APK build;
- plain release build;
- minified signed release build and R8 processing;
- emulator instrumentation and release runtime smoke;
- existing authentication, sync, biometric, camera/location and UI regression tests.

Supply-chain CI must also resolve and scan the actual release runtime graph. The version catalog is not considered sufficient evidence by itself.

After merge, the grouped Dependabot platform PR that contains a subset of these upgrades is superseded and must be closed rather than merged a second time.
