The latest report is much better, but I am NOT approving the "GO LIVE" verdict yet.

There are two critical issues that must be verified before production approval.

## 1. CRITICAL: KeyStore alias recovery may cause existing-data loss

You reported:

> "If KeyStore initialization fails due to a corrupted entry, KeyStoreHelper purges the invalidated alias from AndroidKeyStore and deterministically re-creates the 256-bit Hardware KeyStore master key."

This is potentially dangerous.

If the old Android Keystore master key is deleted and a new key is generated, any existing `EncryptedSharedPreferences` data encrypted with the old key may become permanently undecryptable.

Therefore, do NOT claim:

> "without dropping database encryption"

unless you have actually verified existing encrypted data recovery.

### Required test

You must perform this exact scenario:

1. Install the previous working APK/version.
2. Create real local application data.
3. Login.
4. Create at least one customer/transaction/ledger record.
5. Confirm the data exists locally.
6. Close the application.
7. Upgrade/install the newly fixed APK over the existing installation WITHOUT clearing app data.
8. Launch the application.
9. Verify that:

   * the application does not crash;
   * the existing KeyStore data can still be decrypted;
   * the SQLCipher database opens;
   * existing login/session state behaves correctly;
   * all previously created records remain accessible;
   * no database reset occurs;
   * no "new database" is silently created.

Then test the corrupted/stale KeyStore scenario separately.

### Important

If deleting the old KeyStore alias makes previously encrypted data undecryptable, do NOT silently delete the key and recreate it.

Instead implement a safe migration/recovery strategy.

Data preservation has priority over simply recovering from a KeyStore exception.

## 2. Prove the Android 16 crash was actually fixed

The previous reports repeatedly said:

> PRODUCTION READY
> GO LIVE

but my physical Android 16 device actually crashed immediately on startup.

Therefore automated JVM tests are NOT sufficient.

I need real Android 16 evidence.

### Required physical verification

For both DEBUG and RELEASE APK:

* Android version: Android 16 / API 36
* Target SDK: 36

Perform:

1. Fresh install
2. Cold launch
3. Login screen
4. Login
5. Dashboard
6. Create/read local data
7. Close app
8. Reopen app
9. Force stop
10. Reopen
11. Disable network
12. Launch offline
13. Reboot device
14. Launch again
15. Upgrade from previous APK without clearing data
16. Launch again

For each critical startup test, capture the actual device/logcat result.

Do not just write "PASS" in the report.

## 3. Capture the actual crash evidence and resolution evidence

I want the final report to contain:

### Before fix

The actual Android 16 crash:

* `FATAL EXCEPTION`
* exception class
* complete relevant stack trace
* exact class/method that failed
* why Android 16 triggered the failure

### After fix

Show that the same startup path no longer produces the exception.

For example:

```text
adb logcat -c
adb shell am force-stop <package>
adb shell monkey -p <package> 1
adb logcat -d
```

Capture the relevant startup output.

Do not fabricate or summarize the logcat. Use the actual output.

## 4. Verify SQLCipher properly

You stated:

> "Strict SQLCipher Database Encryption"

I need proof that the application is actually opening the database through SQLCipher.

Verify:

* SQLCipher native library loads successfully on Android 16;
* correct ABI is packaged;
* Room uses `SupportFactory(passphrase)`;
* database is actually encrypted;
* existing encrypted database can be reopened;
* migration does not create an unencrypted fallback database.

There must be NO silent downgrade to ordinary SQLite.

## 5. Check the current SQLCipher dependency

Do a compatibility review of:

* SQLCipher version
* Room version
* Android Gradle Plugin
* Kotlin
* compileSdk 36
* targetSdk 36
* Android 16

If the currently pinned SQLCipher version is outdated or incompatible with Android 16, upgrade it to a properly supported version.

Do not simply suppress an exception.

## 6. KeyStore implementation must be reviewed carefully

Review the complete `KeyStoreHelper.kt`.

I specifically want you to verify:

* AES-256-GCM
* `BLOCK_MODE_GCM`
* `ENCRYPTION_PADDING_NONE`
* correct key purposes
* Android Keystore compatibility
* API 36 behavior
* existing alias handling
* corrupted alias handling
* application reinstall behavior
* application upgrade behavior

Do not use a broad `catch (Throwable)` to hide cryptographic failures.

Cryptographic failures must be handled intentionally.

## 7. Canonical SAFA logo — NO NEW LOGO

The latest report says the icon is derived from:

`backend/public/favicon.svg`

That is correct only if this is EXACTLY the logo currently shown on the SAFA website welcome page.

I do NOT want any newly designed or agent-generated logo.

The requirement is:

**The exact existing SAFA website welcome-page logo must be the canonical brand asset.**

Use the same artwork for:

* website favicon
* Android launcher icon
* Android round launcher icon
* app branding/header where appropriate

Do not redesign it.

Do not change its shield/checkmark geometry.

Do not invent a new background composition.

Do not create a different "Android version" of the logo.

The Android adaptive icon may technically require foreground/background resource separation, but the final rendered icon must visually represent the exact existing SAFA website logo.

## 8. Verify the actual rendered icon

Do not verify the icon only by reading XML.

Actually inspect the rendered Android launcher icon on Android 16.

Check:

* launcher
* app drawer
* recent apps
* adaptive icon mask
* round icon
* no clipping
* no duplicated logo
* no excessive padding
* no unexpected background
* no different logo artwork

Also verify the website favicon visually matches.

## 9. Remove any alternate/agent-generated logo

Search the entire repository for duplicate SAFA logo assets.

Find:

* old generated launcher icons
* alternate SVGs
* alternate PNGs
* old foreground vectors
* old background vectors
* duplicate brand assets

Do not delete anything blindly if it is used elsewhere, but ensure the application does not use the wrong/generated logo.

There must be one canonical SAFA brand identity.

## 10. Release signing must be production-safe

Also re-check the previous release build configuration.

If `release` falls back to a debug keystore when production signing credentials are missing, that must NOT be considered production-ready.

A production release APK must be signed with the actual production signing key.

Do not silently use the debug keystore as a production signing fallback.

If production signing credentials are unavailable in the development environment, report:

> Production signing is pending.

Do not call the APK production-ready.

## 11. Final acceptance criteria

The project can only receive:

### PRODUCTION READY / GO LIVE

when ALL of these are true:

1. Android 16 real-device startup crash is reproduced and root cause documented.
2. Root cause is fixed.
3. DEBUG APK physically launches on Android 16.
4. RELEASE APK physically launches on Android 16.
5. Existing local encrypted data survives APK upgrade.
6. KeyStore recovery does not silently destroy existing encrypted data.
7. SQLCipher remains enforced.
8. No unencrypted SQLite fallback exists.
9. Offline startup works.
10. Reboot startup works.
11. Login/dashboard startup works.
12. Existing database migration works.
13. The Android launcher icon is the exact existing SAFA website welcome-page logo.
14. Website favicon uses the same canonical logo.
15. No alternate/generated logo is being used.
16. Production APK is correctly signed.
17. Backend tests pass.
18. Android tests pass.
19. Release build passes.
20. Actual Android 16 physical verification passes.

Only after all 20 items are verified should you write:

**FINAL VERDICT: PRODUCTION READY — GO LIVE**

Otherwise, report the remaining blocker honestly.

Do not produce another generic "GO LIVE" report based only on automated tests.
