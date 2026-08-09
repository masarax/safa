তোমার সর্বশেষ `FINAL_GO_LIVE_AUDIT.md`-এর `GO LIVE` verdict এখন HOLD করো।

আমি বাস্তবে Android 16 / Target SDK 36 device-এ **Debug APK এবং Release APK দুটোই install করে পরীক্ষা করেছি**। দুটো application launch করার সাথে সাথেই **App stops / crashes** করছে।

অতএব এটি এখন **P0 Production Blocker**। যতক্ষণ Android app Android 16 device-এ successfully launch না করে, SAFA-কে `GO LIVE` বলা যাবে না।

## P0 — Android 16 Startup Crash

### আমার observed behavior

* Android version: Android 16
* Target SDK: 36
* Debug APK: install হয়, কিন্তু launch-এর সাথে সাথে crash/stop
* Release APK: install হয়, কিন্তু launch-এর সাথে সাথে crash/stop
* Crash occurs before normal app usage

### তোমার কাজ

এখন কোনো অনুমান করবে না এবং শুধু source code inspection করে PASS দেবে না।

### 1. প্রথমে actual crash root cause বের করো

Debug এবং Release দুটোই আলাদাভাবে reproduce করো এবং Android runtime crash log সংগ্রহ করো।

বিশেষভাবে ব্যবহার করো:

```bash
adb logcat
```

এবং প্রয়োজন অনুযায়ী:

```bash
adb logcat -c
adb shell am force-stop <package>
adb shell monkey -p <package> 1
adb logcat -d
```

Crash-এর `FATAL EXCEPTION`, `AndroidRuntime`, `Caused by`, native crash এবং relevant stack trace identify করো।

যদি physical Android 16 device available না থাকে, Android 16 API 36 emulator/device configuration ব্যবহার করে reproduce করার চেষ্টা করো।

### 2. Debug এবং Release দুটো APK-তেই reproduce করো

শুধু Debug বা শুধু Release fix করবে না।

Verify:

* Debug launch
* Release launch
* cold start
* fresh install
* reinstall/update install
* app process restart
* offline launch
* online launch

### 3. Root cause অনুযায়ী proper fix করো

Android 16 / API 36 compatibility issue হলে root cause fix করবে।

বিশেষভাবে inspect করো:

* Application class
* startup initialization
* AndroidManifest
* exported activities/services/receivers
* MainActivity
* Compose initialization
* dependency initialization
* Room
* SQLCipher
* Retrofit/OkHttp
* AndroidX Security Crypto
* Tink
* WorkManager
* notification initialization
* splash screen
* network security config
* file provider
* storage/file access
* runtime permissions
* edge-to-edge/API 36 behavior
* ProGuard/R8 release-only issues
* release signing/configuration
* native libraries / ABI compatibility
* any initialization code that runs before the first screen

যদি কোনো third-party dependency Android 16-এর সাথে incompatible হয়, exact dependency এবং version identify করে compatible version বা safe configuration ব্যবহার করো।

### 4. Release-only crash অবশ্যই পরীক্ষা করো

কারণ আগে R8/ProGuard নিয়ে issue ছিল।

তাই verify করো:

* R8 enabled release build
* minification
* resource shrinking
* ProGuard keep rules
* reflection-based classes
* Moshi/Retrofit
* Room entities
* SQLCipher
* Tink
* AndroidX Security Crypto

Release APK launch crash হলে `adb logcat` দিয়ে exact missing/obfuscated class identify করবে এবং প্রয়োজন ছাড়া broad `-keep **` ব্যবহার করবে না।

### 5. Crash fix-এর regression test তৈরি করো

যেখানে automated unit/instrumentation test সম্ভব, test যোগ করো।

কমপক্ষে নিশ্চিত করো:

```text
Fresh Install
    ↓
Launch
    ↓
Application initialization
    ↓
MainActivity
    ↓
First Screen
```

কোনো crash ছাড়া complete হয়।

### 6. Website SAFA Logo → Android App Icon

আরেকটি গুরুত্বপূর্ণ requirement:

**Website-এ বর্তমানে যে SAFA logo ব্যবহার হচ্ছে, সেটিই Android application-এর launcher/app icon হবে।**

কোনো আলাদা placeholder/default Android icon ব্যবহার করবে না।

কাজগুলো:

* বর্তমান website SAFA logo-এর actual source asset identify করো।
* যদি logo dynamic/customizable হয়, production/default SAFA logo-এর canonical source asset নির্ধারণ করো।
* Android launcher icon-এর জন্য প্রয়োজনীয় density/adaptive icon assets generate/prepare করো।
* `mipmap-anydpi-v26` / appropriate density resources ব্যবহার করো যেখানে প্রয়োজন।
* `AndroidManifest.xml`-এর `android:icon` এবং প্রয়োজন হলে `android:roundIcon` সঠিক SAFA logo resource-এ point করাও।
* Android 16 launcher-এ icon correctly render হচ্ছে কিনা verify করো।
* icon-এর aspect ratio, transparent background, safe zone এবং adaptive icon mask ঠিক রাখো।
* notification icon-এর জন্য launcher icon সরাসরি ব্যবহার করবে না; প্রয়োজন হলে আলাদা monochrome notification icon রাখবে।
* existing website logo পরিবর্তন করবে না।

### 7. Logo consistency

একই canonical SAFA branding যেন থাকে:

```text
Website Logo
      ↓
Canonical SAFA Logo Asset
      ↓
Android Launcher Icon
      ↓
Android App Branding
```

Website-এর logo এবং Android launcher icon visually consistent হতে হবে।

### 8. APK rebuild

Crash fix এবং icon fix-এর পরে:

```bash
.\gradlew clean
.\gradlew test --continue
.\gradlew assembleDebug
.\gradlew assembleRelease
```

সব test আবার চালাবে।

### 9. Mandatory runtime verification

Final APK তৈরি হওয়ার পরে actual install/launch test করবে।

Debug:

```text
uninstall
install
launch
```

Release:

```text
uninstall
install
launch
```

তারপর:

* app opens successfully
* no immediate crash
* first screen renders
* launcher icon is SAFA logo
* app survives process restart
* app opens offline
* app opens online
* login screen works
* API initialization does not crash

যদি ADB/device available থাকে, actual runtime evidence সংগ্রহ করো।

### 10. IMPORTANT — Fake PASS নিষিদ্ধ

এই audit-এ কোনো কিছু শুধু source code দেখে `PASS` বলবে না যদি runtime verification প্রয়োজন হয়।

যদি Android 16 physical device/emulator-এ test করা সম্ভব না হয়, স্পষ্টভাবে:

`UNVERIFIED — Android 16 runtime unavailable`

লিখবে।

কিন্তু crash-এর root cause যদি logcat/source evidence দিয়ে নির্ণয় করা যায়, fix করে available environment-এ maximum verification করবে।

### 11. Update final report

`FINAL_GO_LIVE_AUDIT.md` update করো এবং নতুন section যোগ করো:

```text
Android 16 / API 36 Runtime Compatibility Verification
```

এখানে থাকবে:

* original crash symptom
* actual crash stack trace/root cause
* root cause fix
* changed files
* Debug APK result
* Release APK result
* Android 16 launch result
* launcher icon verification
* tests executed
* final APK SHA-256

### 12. Final verdict

এই fix-এর পরে verdict শুধুমাত্র নিচের একটি হবে:

`GO LIVE`
`GO LIVE WITH CONDITIONS`
`BLOCKED`

**Android 16-এ Debug এবং Release APK launch successfully verified না হওয়া পর্যন্ত `GO LIVE` দেবে না।**

এখন priority order:

**P0-1: Android 16 startup crash root cause → fix → runtime verify**

**P0-2: Website SAFA logo → Android launcher/app icon**

**P0-3: Full regression test → fresh Debug + Release APK**

**P0-4: Final GO-LIVE verdict**

কোনো নতুন feature যোগ করবে না এবং existing financial/sync behavior অপ্রয়োজনীয়ভাবে পরিবর্তন করবে না।
