# SAFA — Phase 2 Complete UI/UX, Branding, Web Installer & Production Schema Verification

## Objective

Perform a complete production-grade audit and repair of the SAFA Android application and Laravel web installer/update system.

This phase is NOT limited to visual changes.

The agent must verify:

1. Android launcher/app branding
2. Website favicon
3. Website logo loading
4. Welcome page
5. First installation flow
6. Existing cPanel database installation flow
7. New migration detection
8. Existing/partial migration detection
9. Database update screen
10. Database migration safety
11. Android UI consistency
12. Modal/dialog consistency
13. Typography consistency
14. Language consistency
15. Dark mode consistency
16. App logo/name remote configuration
17. Native Android UX quality
18. Accessibility
19. Loading/error/empty states
20. Security issues discovered during this audit

Do NOT redesign business logic or synchronization architecture unless a reproducible problem is found.

---

# PART A — BRANDING & LOGO

## A1. Android Launcher Icon

Current issue:

`AndroidManifest.xml` uses:

```xml
android:icon="@mipmap/ic_launcher"
android:roundIcon="@mipmap/ic_launcher_round"
```

but the current launcher foreground/background are default Android assets.

Replace the default Android robot/green launcher artwork with the actual SAFA brand identity.

Requirements:

* Proper SAFA logo
* Adaptive icon
* Foreground safe zone
* Background
* Monochrome icon
* Legacy launcher compatibility
* Round icon compatibility
* Android 8+ adaptive icon
* Android older versions
* Correct density resources
* No Android robot branding

Acceptance tests:

1. Build debug APK.
2. Install on Android emulator/device.
3. Launcher icon must display SAFA branding.
4. Long-press launcher icon must still show correct icon.
5. App info screen must show correct SAFA icon.
6. Recent-apps/app switcher must not display a generic Android icon.
7. Adaptive icon must not crop the SAFA mark.

---

# A2. Website Favicon

Current issue:

`backend/public/favicon.ico` is empty and the Blade pages do not properly declare a favicon.

Implement:

```html
<link rel="icon" type="image/png" href="...">
<link rel="apple-touch-icon" href="...">
```

Use the actual SAFA logo asset.

Acceptance tests:

* `/favicon.ico` must not be empty.
* Logo asset must return HTTP 200.
* Correct Content-Type.
* Browser tab displays SAFA favicon.
* Mobile browser bookmark/add-to-home-screen uses correct icon.
* No broken favicon request.
* No mixed-content request.
* Cache-busting/versioning must be handled when logo changes.

---

# A3. Website Logo Loading

The current welcome page references:

```blade
{{ asset('safa-logo.png') }}
```

Do not assume this is sufficient.

Verify the complete production path:

```text
Browser
 ↓
/safa-logo.png
 ↓
HTTP 200
 ↓
image/png
 ↓
valid image bytes
 ↓
browser rendering
```

Test:

* clean browser
* incognito
* mobile browser
* HTTPS
* cPanel deployment
* cache cleared
* stale browser cache

If the asset is missing or invalid, fail the test.

---

# PART B — DATABASE INSTALLATION & UPDATE SYSTEM

## B1. Fresh Installation

Test with an empty MySQL database.

Expected:

```text
GET /
 ↓
installation detection
 ↓
/install
 ↓
requirements check
 ↓
database connection test
 ↓
installation
 ↓
migrations
 ↓
installed lock
 ↓
success page
 ↓
/
 ↓
welcome page
```

Verify that no data loss occurs.

---

# B2. Existing cPanel Database

This is critical.

Create/use a database containing existing SAFA data.

Deploy a newer application version containing new migrations.

Expected:

```text
Existing DB
+
New application code
+
New migration files
        ↓
GET /
        ↓
Detect pending migration
        ↓
Show Database Update screen
        ↓
DO NOT show normal Welcome page
```

The update screen must display:

* update required
* number of pending migrations
* migration names
* safe migration warning
* update button

---

# B3. Schema-Level Migration Verification

DO NOT determine migration completion only by checking whether a table exists.

Example:

```text
Migration requires:
users
columns A, B, C, D

Production DB:
users exists
A exists
B exists
C missing
D exists
```

The system must detect that the schema is incomplete.

Acceptance:

* Missing table → detected
* Missing column → detected
* Missing index → detected where required
* Missing foreign key → detected where required
* Incomplete migration → detected
* Migration marked as executed but schema incomplete → detected/repaired safely

---

# B4. Existing Table Auto-Healing Safety

Review:

```php
InstallerController::autoHealExistingSchema()
```

Do not blindly mark a migration as executed simply because one table exists.

Before auto-registering a migration as completed, verify the complete schema contract of that migration.

Never silently mark a partially migrated database as fully migrated.

---

# B5. Update Process Safety

Test:

```text
Existing production DB
 ↓
pending migration
 ↓
update screen
 ↓
click update
 ↓
migration
 ↓
verification
 ↓
welcome page
```

Verify:

* existing users remain
* customers remain
* suppliers remain
* transactions remain
* financial data remains
* no destructive migration
* no accidental DROP TABLE
* no duplicate table creation
* no duplicate migration execution

---

# B6. Failure Recovery

Simulate:

* DB permission denied
* insufficient privilege
* migration SQL failure
* connection timeout
* partially applied migration

Expected:

* meaningful error
* application does not falsely report success
* database remains recoverable
* retry possible
* no silent failure

---

# B7. Remove/Protect Unsafe GET Migration Endpoint

Audit:

```text
GET /update-db
```

Do not leave an unauthenticated public GET endpoint capable of running:

```php
Artisan::call('migrate', ['--force' => true])
```

Preferred:

```text
Authenticated/admin action
+
POST
+
CSRF
+
explicit confirmation
+
migration verification
```

If the endpoint is retained for emergency compatibility, protect it with strong authorization.

---

# PART C — LANGUAGE SYSTEM

## C1. No Duplicate Bilingual Labels

The application must NOT display unnecessary:

```text
বাংলা (English)
English (বাংলা)
```

or:

```text
বাংলা Text (English Text)
```

everywhere.

Rules:

### Bengali mode

Show Bengali only.

Example:

```text
গ্রাহক
লেনদেন
সংরক্ষণ
বাতিল
মুছে ফেলুন
```

### English mode

Show English only.

Example:

```text
Customer
Transaction
Save
Cancel
Delete
```

Only keep English technical identifiers where genuinely necessary.

---

# C2. Centralized Translation System

Audit all Compose screens.

Do not allow random inline strings everywhere.

Create/use a centralized translation resource:

```text
TranslationKey
BN
EN
```

Every user-facing label should use the centralized translation system.

Acceptance:

* no duplicate translations
* no mixed-language UI
* no untranslated placeholder
* no accidental English inside Bengali mode
* no accidental Bengali inside English mode

---

# PART D — ANDROID DESIGN SYSTEM

## D1. One Global Design System

Create a single SAFA design system containing:

* colors
* typography
* spacing
* shapes
* elevation
* borders
* icon sizes
* button heights
* input heights
* dialog dimensions
* navigation dimensions
* status colors

Do not hardcode random colors inside individual screens.

Use:

```text
SAFA Design Tokens
```

instead of:

```kotlin
Color(0xFFD7A84B)
Color(0xFF...)
```

repeated throughout screens.

---

# D2. Top App Bar

The current top bar uses a separate hardcoded gold palette.

Replace with the unified SAFA theme.

Requirements:

* consistent height
* consistent title typography
* consistent logo size
* consistent action icon size
* proper touch target
* proper dark/light mode
* no excessive text
* no unnecessary operator text when space is constrained

---

# D3. Bottom Navigation

Make the bottom navigation feel like a modern native Android application.

Requirements:

* Material 3 compliant
* consistent icon size
* clear selected state
* clear unselected state
* proper safe-area handling
* no unnecessary borders
* no oversized labels
* smooth state transition
* keyboard-aware behavior

---

# PART E — MODAL & DIALOG SYSTEM

## E1. Audit ALL dialogs

Find every:

```text
AlertDialog
Dialog
Modal
BottomSheet
ModalBottomSheet
DatePicker
TimePicker
Confirmation popup
Delete confirmation
Logout confirmation
Error popup
Success popup
```

Create one reusable SAFA dialog system.

All dialogs must share:

* corner radius
* title style
* body style
* button hierarchy
* spacing
* icon treatment
* colors
* dark mode
* animation
* dismiss behavior

---

# E2. Confirmation Dialogs

Standardize:

### Destructive

```text
Delete Customer?
This action cannot be undone.

Cancel        Delete
```

### Normal

```text
Save changes?
Your changes will be saved.

Cancel        Save
```

No page should invent a different modal style.

---

# PART F — NATIVE APP UX

Audit every screen:

* Login
* Dashboard
* Customers
* Customer profile
* Add customer
* Suppliers
* Supplier profile
* Add supplier
* Transactions
* Wallet
* Expenses
* Add expense
* Reports
* Settings
* Lock screen

For each screen verify:

```text
Loading
Success
Empty
Error
Offline
Retry
Saving
Saved
Disabled
Permission denied
Network unavailable
```

Every state must have professional UI.

---

# PART G — FORM UX

All forms must use consistent:

* input height
* label
* placeholder
* supporting text
* error text
* required indicator
* keyboard type
* focus state
* validation
* save button
* loading state

Do not duplicate confusing labels.

Avoid:

```text
Name
Name (Customer Name)
```

Use one clear label.

---

# PART H — DARK MODE

Current dark mode is held only in ViewModel state.

Implement persistent theme preference.

Expected:

```text
User selects Dark
 ↓
App restarts
 ↓
Dark remains
```

Also test:

```text
Light
Dark
System
```

if supported.

Every component must remain readable in both themes.

---

# PART I — REMOTE APP CONFIGURATION

Verify:

```text
Server app name
Server logo
Server version
Currency
```

flow:

```text
API
 ↓
Remote config
 ↓
TokenManager/cache
 ↓
ViewModel
 ↓
Compose
```

Test:

1. Change app name on server.
2. Open/restart Android app.
3. Verify new name.
4. Change logo on server.
5. Verify new logo.
6. Disconnect internet.
7. Verify cached configuration still works.
8. Reconnect.
9. Verify latest configuration arrives.

Do not use emoji as the primary production logo fallback.

Replace:

```text
👑
```

with the actual bundled SAFA logo.

---

# PART J — ACCESSIBILITY

Audit:

* minimum 48dp touch targets
* content descriptions
* contrast
* font scaling
* TalkBack
* keyboard navigation where applicable
* focus order
* truncation
* long text handling

No critical action should be smaller than the recommended Android touch target.

---

# PART K — SECURITY FINDING

Audit `TokenManager.kt`.

Current code contains default API credentials in Android source.

Do NOT leave production API secret credentials hardcoded in APK-accessible source.

Design a safer authentication/security mechanism.

Do not break existing authentication or sync.

Create a separate security remediation plan if immediate architectural change is unsafe.

---

# PART L — AUTOMATED TEST REQUIREMENTS

The agent must create tests for at least:

## Web

1. Fresh installation
2. Existing database detection
3. Pending migration detection
4. Missing column detection
5. Partial migration detection
6. Migration execution
7. Migration failure
8. Data preservation
9. Logo HTTP availability
10. Favicon availability
11. Favicon HTML declaration
12. Welcome/update routing

## Android

1. Launcher resource configuration
2. Logo fallback
3. Remote logo loading
4. Remote app-name loading
5. Language switching
6. No mixed-language labels in translated UI
7. Dialog consistency where testable
8. Theme persistence
9. Dark-mode rendering
10. Form validation
11. Loading/error/empty states
12. Accessibility semantics
13. Remote config offline fallback

---

# PART M — REQUIRED FINAL VERIFICATION

After implementation run:

```text
Android:
./gradlew.bat compileDebugKotlin
./gradlew.bat testDebugUnitTest
./gradlew.bat lint

Backend:
php artisan test
php artisan migrate:status
```

Also perform real production verification where safe.

Final report must contain:

```text
UI/UX AUDIT
Branding: PASS/FAIL
Android icon: PASS/FAIL
Website logo: PASS/FAIL
Favicon: PASS/FAIL
Language system: PASS/FAIL
Design system: PASS/FAIL
Dialogs: PASS/FAIL
Dark mode: PASS/FAIL
Remote config: PASS/FAIL
Accessibility: PASS/FAIL

INSTALLER
Fresh install: PASS/FAIL
Existing DB: PASS/FAIL
Pending migration: PASS/FAIL
Partial migration: PASS/FAIL
Migration safety: PASS/FAIL
Failure recovery: PASS/FAIL

SECURITY
Hardcoded credentials: PASS/FAIL
Unsafe migration endpoint: PASS/FAIL

TESTS
Android compile: PASS/FAIL
Android unit tests: PASS/FAIL
Android lint: PASS/FAIL
Laravel tests: PASS/FAIL

Every FAIL must include:
- exact file
- exact root cause
- reproduction steps
- fix
- test proving the fix
```

## IMPORTANT

Do not claim PASS merely because code compiles.

Do not claim production verification without actually executing the relevant production test.

Do not replace a missing test with a code inspection.

Do not silently skip a failed test.

Do not redesign the synchronization engine in this phase unless a reproducible sync regression is found.

The goal is a **professional, native-feeling, consistent SAFA application and a safe production installer/update system**, not merely a visually different application.
