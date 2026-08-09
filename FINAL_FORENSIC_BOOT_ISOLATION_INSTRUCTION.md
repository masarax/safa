# SAFA — FINAL FORENSIC BOOT ISOLATION INSTRUCTION

## DO NOT GUESS. DO NOT MAKE ANOTHER SPECULATIVE FIX.

The SAFA Android app still immediately stops/crashes when launched on physical Android devices.

I have reviewed the current repository and the previous forensic reports.

The previous iterations repeatedly modified:

* SQLCipher
* KeyStore
* Build.getSerial()
* enableEdgeToEdge()
* themes
* launcher icons
* ProGuard/R8
* startup try/catch
* logging checkpoints

but the application STILL stops immediately.

Therefore, **STOP PATCHING INDIVIDUAL SUSPECTS.**

We now need to isolate the crash scientifically.

---

## 1. CRITICAL REQUIREMENT — MINIMAL BOOT APK

The next build MUST be a true minimal boot build.

Temporarily modify the Android application so that the launcher activity performs ONLY:

```kotlin
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(
            TextView(this).apply {
                text = "SAFA BOOT OK"
                textSize = 24f
            }
        )
    }
}
```

Use the simplest possible Android Activity.

For this diagnostic build:

### REMOVE FROM STARTUP COMPLETELY

* SQLCipher
* Room
* AppDatabase
* KeyStore
* EncryptedSharedPreferences
* SharedPreferences
* TokenManager
* DeviceSecurityHelper
* AppRepository
* SafaViewModel
* SafaViewModelFactory
* WorkManager
* AutoSyncWorker
* Retrofit
* OkHttp
* Moshi
* Coil
* biometric
* custom logo loading
* custom application logic
* API initialization
* network initialization
* all custom startup services

Do NOT merely wrap them in try/catch.

They must NOT execute.

---

# 2. EVEN MORE IMPORTANT — ISOLATE PRE-ACTIVITY CRASHES

If the minimal Activity still crashes, the problem is NOT:

* Room
* SQLCipher
* KeyStore
* API
* TokenManager
* ViewModel

Then inspect everything that executes BEFORE MainActivity.onCreate():

```text
Android process creation
↓
Manifest parsing
↓
Application creation
↓
ContentProviders
↓
AndroidX Startup providers
↓
FileProvider
↓
Activity class loading
↓
Activity instantiation
↓
Theme inflation
↓
Window creation
↓
Activity.onCreate()
```

Do NOT assume MainActivity.onCreate() is the earliest crash boundary.

---

# 3. TEMPORARILY REMOVE FileProvider

The current manifest contains:

```xml
<provider
    android:name="androidx.core.content.FileProvider"
    ...
/>
```

For the minimal diagnostic APK, temporarily remove the FileProvider completely.

Then test.

If minimal boot works after removing FileProvider, investigate FileProvider initialization/configuration.

If it still crashes, continue.

---

# 4. USE A PLAIN ANDROID THEME

For the minimal diagnostic APK, do NOT use the application's Compose/Material theme.

Use a minimal platform theme such as:

```xml
<style name="Theme.SafaDiagnostic"
    parent="@android:style/Theme.Material.Light.NoActionBar">
</style>
```

Assign this theme directly to MainActivity.

No:

* Material3
* Compose theme
* custom colors
* custom window attributes
* edge-to-edge
* status bar customization
* navigation bar customization

The purpose is to determine whether the application can launch at the Android framework level.

---

# 5. TEST MATRIX

Create separate diagnostic builds/phases.

Do NOT jump directly to the full application.

### PHASE A — Absolute Minimal

```text
Activity
+
super.onCreate()
+
plain TextView
```

Nothing else.

Expected:

```text
SAFA BOOT OK
```

### PHASE B

Add:

```text
Compose setContent
```

Nothing else.

### PHASE C

Add:

```text
enableEdgeToEdge()
```

### PHASE D

Add:

```text
KeyStore
```

### PHASE E

Add:

```text
SQLCipher native loading
```

### PHASE F

Add:

```text
Room
```

### PHASE G

Add:

```text
Repository
```

### PHASE H

Add:

```text
TokenManager
```

### PHASE I

Add:

```text
ViewModel
```

### PHASE J

Add:

```text
WorkManager
```

### PHASE K

Restore the complete application.

Every phase MUST produce a separate APK and a separate result.

---

# 6. CRITICAL SQLCIPHER INVESTIGATION

The current dependency is:

```text
net.zetetic:android-database-sqlcipher:4.5.4
```

and the application directly calls:

```kotlin
SQLiteDatabase.loadLibs(context)
```

Do NOT claim:

> "libsqlcipher.so exists, therefore SQLCipher is fine."

That is NOT proof.

Investigate runtime compatibility including:

* Android API 24+
* Android 10+
* Android 12+
* Android 13+
* Android 14+
* Android 15+
* Android 16
* arm64-v8a
* armeabi-v7a
* x86
* x86_64
* native linker
* 16 KB memory/page-size compatibility
* JNI loading
* UnsatisfiedLinkError
* native SIGSEGV
* native abort
* linker errors

If SQLCipher is the cause, prove it with logcat.

---

# 7. CRITICAL KEYSTORE INVESTIGATION

The current KeyStore implementation contains a fallback:

```kotlin
context.getSharedPreferences(
    "safa_secure_passphrase_store",
    Context.MODE_PRIVATE
)
```

This is NOT encrypted storage.

Therefore previous reports claiming:

> "Strict SQLCipher encryption"

while simultaneously using an unencrypted SharedPreferences fallback are technically misleading.

Do NOT call this a secure encrypted fallback.

Investigate:

* MasterKey creation
* AndroidKeyStore
* alias
* KeyGenParameterSpec
* AES256-GCM
* EncryptedSharedPreferences
* existing installation
* upgrade installation
* uninstall/reinstall
* corrupted encryption metadata

But only after minimal boot succeeds.

---

# 8. CRITICAL SECURITY ISSUE — API CREDENTIALS

Audit the entire API security chain:

```text
.env
↓
Gradle Secrets Plugin
↓
BuildConfig/resources
↓
APK
↓
TokenManager
↓
Retrofit
↓
OkHttp
↓
API headers
↓
Laravel middleware
```

DO NOT print or expose any secret values.

DO NOT put secrets into any audit markdown.

DO NOT commit secrets.

The application MUST be capable of displaying the login screen while the API server is completely unavailable.

Therefore API connectivity MUST NOT be a prerequisite for initial UI launch.

---

# 9. CURRENT TokenManager MUST NOT BE PART OF BOOT TEST

The current TokenManager constructor itself is simple:

```kotlin
context.getSharedPreferences(...)
```

but do not assume it is harmless.

It must be isolated in its own phase.

First prove:

```text
Activity launches
```

Then:

```text
KeyStore works
```

Then:

```text
SQLCipher works
```

Then:

```text
TokenManager works
```

---

# 10. CURRENT MainActivity ERROR BOUNDARY IS NOT SUFFICIENT

Do NOT report:

> "The entire startup is protected by try/catch."

It is not.

The following are outside the current catch boundary:

* process creation
* manifest/provider initialization
* activity instantiation
* theme inflation
* `super.onCreate()`
* class verification/linking
* native process crashes
* composition crashes after `setContent()`

Therefore the diagnostic architecture must reflect the actual Android lifecycle.

---

# 11. LOGCAT REQUIREMENT

Physical runtime evidence is mandatory.

Provide these commands:

```bash
adb devices
adb logcat -c
adb logcat -v threadtime
```

Then:

```bash
adb install -r app-debug.apk
adb shell am force-stop com.safa.account
adb shell monkey -p com.safa.account 1
```

Capture the complete output around the launch.

Specifically search for:

```text
FATAL EXCEPTION
AndroidRuntime
libc
DEBUG
linker
SQLite
SQLCipher
SecurityException
Resources$NotFoundException
InflateException
NoSuchMethodError
UnsatisfiedLinkError
ExceptionInInitializerError
ClassNotFoundException
VerifyError
IllegalStateException
ActivityTaskManager
WindowManager
SIGSEGV
SIGABRT
```

If it is a native crash, Java/Kotlin try/catch cannot prove or prevent it.

---

# 12. APK MUST BE TESTED IN TWO INSTALL STATES

Test:

### Clean install

```text
uninstall
↓
install
↓
launch
```

### Upgrade install

```text
old APK
↓
install new APK over it
↓
launch
```

This is especially important because KeyStore/EncryptedSharedPreferences/SQLCipher metadata may differ between versions.

---

# 13. BRANDING — CURRENT CLAIM IS STILL NOT ACCEPTABLE

The current adaptive icon is:

```xml
<adaptive-icon>
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```

and:

```xml
<bitmap
    android:src="@drawable/safa_logo"
    android:gravity="center" />
```

The background is separately generated as:

```text
#F97316
```

This means the adaptive icon is NOT simply the original `safa-logo.png` file.

The requirement is:

**The Android launcher must visually display the actual canonical SAFA logo artwork from `backend/public/safa-logo.png`.**

Do not:

* redraw it
* approximate it
* generate a new logo
* recreate it using vector paths

Use the actual source asset.

Inspect ALL launcher variants:

```text
mipmap-anydpi-v26
mipmap-anydpi
mipmap-mdpi
mipmap-hdpi
mipmap-xhdpi
mipmap-xxhdpi
mipmap-xxxhdpi
ic_launcher
ic_launcher_round
monochrome
```

Verify the icon visually from the generated APK.

---

# 14. DO NOT MODIFY BUSINESS LOGIC

During this investigation do NOT change:

* Dashboard
* Customers
* Suppliers
* Transactions
* Wallet
* Expenses
* Reports
* Localization
* Synchronization
* Business rules
* UI behavior unrelated to startup

Only startup/crash isolation code may change.

---

# 15. REQUIRED OUTPUT

Do NOT give me another generic audit report.

I require:

### A. Phase results

```text
PHASE A — PASS/FAIL
PHASE B — PASS/FAIL
PHASE C — PASS/FAIL
...
```

### B. Exact failure phase

Example:

```text
FAILURE PHASE: E
LAST SUCCESSFUL PHASE: D
```

### C. Exact evidence

Include the relevant logcat exception/error.

### D. Exact root cause

Only if proven.

Otherwise:

```text
ROOT CAUSE NOT YET PROVEN
```

### E. Exact source location

```text
file:
line:
function:
```

### F. Why previous fixes failed

Explain specifically why the previous SQLCipher/KeyStore/theme/edge-to-edge changes did not resolve the crash.

### G. Minimal boot APK

The minimal APK MUST be built and its SHA-256 recorded.

### H. Full debug APK

Build only after minimal boot succeeds.

### I. Full release APK

Build only after debug startup succeeds.

---

# 16. ABSOLUTE STOP CONDITION

Do NOT say:

```text
READY FOR GO LIVE
```

Do NOT say:

```text
ANDROID 7-16 FULLY SUPPORTED
```

Do NOT say:

```text
ROOT CAUSE FIXED
```

unless physical runtime evidence proves it.

Until then:

```text
BLOCKED — ROOT CAUSE NOT PROVEN / NOT READY FOR GO LIVE
```

---

# FINAL REQUIREMENT

The next iteration must NOT primarily modify markdown audit reports.

The next iteration must produce:

1. minimal boot APK
2. phase-by-phase isolation
3. physical logcat evidence
4. exact crash boundary
5. proven root cause
6. minimal targeted fix
7. debug verification
8. release verification
9. actual original SAFA logo verification

**Do not guess. Do not patch randomly. Isolate first. Fix second. Prove third.**
