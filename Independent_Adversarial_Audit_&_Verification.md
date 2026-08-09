# SAFA — Phase 3 Independent Adversarial Audit, UI/UX, Branding, Installer & Production Safety Verification

## IMPORTANT — READ THIS FIRST

Do NOT assume that any previous audit, verification report, or "PASS" statement is correct.

The previous Phase 2 verification report contains claims that do not match the current GitHub source code.

You MUST inspect the CURRENT repository state directly and verify every claim against actual source code.

Repository:

`masarax/safa`

Default branch:

`main`

This phase is an **independent verification and correction phase**.

Do NOT mark an item PASS merely because:

* a previous report says PASS;
* a test file exists;
* a method exists;
* compilation succeeds.

A requirement is PASS only when the actual production behavior and source implementation prove it.

---

# 1. Known Mismatches Already Detected

The following issues have already been independently observed and MUST be verified first.

## 1.1 Website Logo

`backend/resources/views/welcome.blade.php` references:

```blade
{{ asset('safa-logo.png') }}
```

However, the latest GitHub commit deleted `safa-logo.png`.

Verify:

* Does `backend/public/safa-logo.png` currently exist?
* Is it tracked by Git?
* Does `/safa-logo.png` return HTTP 200 in production?
* Does the image render correctly?
* Does browser cache/CDN affect it?
* Are install/update/welcome pages all using the same canonical logo asset?

Requirement:

There must be one canonical SAFA logo asset strategy.

Do not depend on a missing binary file.

---

# 2. Android Launcher Icon Audit

Inspect:

```text
app/src/main/AndroidManifest.xml
app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml
app/src/main/res/drawable/ic_launcher_foreground.xml
app/src/main/res/drawable/ic_launcher_background.xml
app/src/main/res/mipmap-*/
```

Verify:

* launcher icon
* round icon
* adaptive icon
* monochrome icon
* Android 8+
* Android older versions
* dark/light launcher behavior
* foreground scaling
* safe zone
* actual SAFA branding

IMPORTANT:

The current `ic_launcher_foreground.xml` must NOT contain the generic Android robot artwork.

Create an automated source-level regression test that fails if generic Android robot launcher artwork is restored.

Also verify the generated APK actually contains the expected launcher resources.

---

# 3. Website Favicon Audit

Verify every relevant Blade page:

```text
welcome.blade.php
install.blade.php
install_success.blade.php
install_update.blade.php
```

Must have:

* favicon
* SVG favicon
* PNG fallback where appropriate
* apple-touch-icon
* correct absolute/asset URLs
* no missing asset references
* no duplicate conflicting favicon declarations

Test the production HTTP endpoints.

Required tests:

```text
GET /
GET /install
GET /install/update
GET /favicon.svg
GET /safa-logo.png
```

Do not assume an asset exists because a Blade template references it.

---

# 4. CRITICAL — Database Update Endpoint Security

Audit ALL database migration endpoints.

Search entire backend for:

```text
Artisan::call('migrate'
migrate --force
update-db
update-process
install/update
```

Known endpoints include:

```text
/update-db
/install/update
/install/update-process
```

Every endpoint capable of modifying the production database MUST have proper authorization.

Requirements:

### Unauthorized request

Must return:

```text
403
```

and MUST NOT execute migrations.

### Authorized request

Must execute only after valid authorization.

Do NOT use a GET endpoint for a destructive/state-changing migration operation unless there is an extremely strong reason.

Prefer:

```text
POST
```

with:

* authentication
* authorization
* CSRF protection where applicable
* server-side secret validation
* rate limiting where appropriate
* audit logging

Do NOT expose a public migration trigger.

Create Laravel feature tests proving:

1. unauthenticated request rejected
2. wrong secret rejected
3. missing secret rejected
4. correct authorization accepted
5. unauthorized request never calls migration
6. migration execution errors are handled safely

---

# 5. Database Update Detection — Full Verification

The system requirement is:

When a cPanel production database already exists and a newer application version contains new migrations:

```text
User opens website
        ↓
Application detects pending migrations
        ↓
Welcome page is NOT shown
        ↓
Database Update screen is shown
        ↓
Pending migration list is displayed
        ↓
User explicitly authorizes update
        ↓
Migration runs
        ↓
Success is shown
        ↓
User returns to normal application
```

Verify this with a realistic test matrix.

## Scenario A — Fresh installation

Empty database:

Expected:

```text
/install
```

Then:

```text
migrations execute
```

Then:

```text
normal application
```

## Scenario B — Existing database, no pending migrations

Expected:

```text
welcome/application page
```

## Scenario C — Existing database + one new migration

Expected:

```text
database update page
```

NOT:

```text
welcome page
```

## Scenario D — Existing table but missing new column

Expected:

```text
migration detects missing column
migration adds column
```

It MUST NOT incorrectly register the migration as completed simply because the table already exists.

## Scenario E — Existing table partially matches schema

Example:

```text
table exists
column A exists
column B missing
column C missing
```

Expected:

```text
missing schema elements detected
safe migration executed
```

## Scenario F — Migration partially fails

Expected:

* no false "success"
* clear error
* database remains recoverable
* migration state remains correct
* retry is possible

---

# 6. CRITICAL — Audit autoHealExistingSchema()

Inspect:

```text
backend/app/Http/Controllers/InstallerController.php
```

Do NOT claim "column-level contract verification" unless actual code verifies columns.

For every migration that is auto-registered as completed, define:

```text
migration
→ expected tables
→ expected columns
→ expected indexes
→ expected foreign keys
```

Never mark a migration complete merely because one primary table exists.

Create automated tests for:

* table exists but column missing
* table and all columns exist
* table partially exists
* migration already registered
* migration not registered
* migration failed
* legacy/imported cPanel database

---

# 7. Hardcoded API Secret Audit

Inspect:

```text
TokenManager.kt
```

Search the entire repository for:

```text
SAFA_API_KEY
SAFA_API_SECRET
safa_key_
safa_sec_
```

The APK must NOT contain a reusable production API secret.

IMPORTANT:

XOR/base64/string splitting/obfuscation is NOT real secret protection.

A secret embedded in an APK is extractable.

Design a secure architecture.

Possible architecture:

```text
Android
   ↓
authenticated login
   ↓
server-issued short-lived credentials/session
   ↓
API request
```

The production server secret must remain server-side.

Create tests/static checks that fail if production secrets are hardcoded into Android source.

---

# 8. Dark Mode Persistence

Inspect:

```text
SafaViewModel.kt
TokenManager.kt
SettingsScreen.kt
theme files
```

Verify:

```text
user enables dark mode
        ↓
preference saved
        ↓
application closes
        ↓
application restarts
        ↓
dark mode remains enabled
```

Current implementation appears to keep dark mode only in:

```kotlin
MutableStateFlow(false)
```

This MUST be verified and fixed if confirmed.

Add unit tests.

---

# 9. Language System Audit

The user explicitly requires professional language UX.

Do NOT use:

```text
Bangla (English)
English (Bangla)
```

everywhere.

Do NOT write unnecessarily duplicated labels such as:

```text
ডাটাবেস আপডেট (Database Update)
```

on every UI element.

Instead implement a real localization system.

Example:

```text
বাংলা:
ডাটাবেস আপডেট

English:
Database Update
```

The UI displays ONE language at a time.

Audit:

```text
app/src/main/java/**/ui/**
backend/resources/views/**
```

Find:

* duplicated bilingual labels
* repeated explanatory text
* unnecessarily long labels
* mixed-language buttons
* mixed-language dialogs
* repeated descriptions
* inconsistent terminology

Create a localization/wording audit report.

---

# 10. Native Android UI/UX Audit

The application must feel like a professional native Android application.

Audit ALL screens:

```text
Login
Lock
Dashboard
Customers
Customer Profile
Customer Add/Edit
Suppliers
Supplier Profile
Supplier Add/Edit
Transactions
Wallet
Expenses
Reports
Settings
Dialogs
Sheets
Confirmations
Empty States
Loading States
Error States
Sync States
```

Do not only inspect the design-system file.

Inspect actual screen implementations.

---

# 11. Modal/Dialog Consistency

Find every:

```text
AlertDialog
Dialog
Modal
BottomSheet
ModalBottomSheet
DatePicker
TimePicker
Dropdown
Popup
```

Create a matrix:

| Component | Screen | Current Style | Desired Standard |
| --------- | ------ | ------------- | ---------------- |

All destructive dialogs must share:

* same title hierarchy
* same spacing
* same button order
* same destructive color
* same corner radius
* same dismiss behavior
* same accessibility semantics

All normal confirmation dialogs must share a unified component.

Do not create multiple visually different confirmation systems.

---

# 12. Design System Audit

Inspect:

```text
DesignSystemComponents.kt
```

IMPORTANT:

Verify every reusable component actually renders all supplied parameters.

For example, inspect `AppPrimaryButton`.

It accepts:

```kotlin
text: String
```

but verify that:

```kotlin
Text(text = text)
```

is actually rendered.

Create regression tests for:

* primary button
* outlined button
* text button
* cards
* status chips
* section headers
* text fields
* dialogs
* loading states
* empty states

A component is not PASS merely because it compiles.

---

# 13. Typography Audit

Remove excessive text.

Use:

```text
short title
short supporting text
clear CTA
```

Avoid paragraph-heavy mobile UI.

Buttons should generally contain:

```text
1–3 words
```

when possible.

Examples:

BAD:

```text
ডাটাবেস আপডেট করুন এবং নতুন স্কিমা পরিবর্তনগুলো সম্পূর্ণ করুন
```

GOOD:

```text
ডাটাবেস আপডেট
```

---

# 14. Spacing & Component Consistency

Create global tokens for:

```text
screen padding
section spacing
card spacing
field spacing
button height
corner radius
icon size
typography
```

Avoid random values across screens such as:

```text
12.dp
13.dp
14.dp
17.dp
19.dp
23.dp
```

unless justified.

Use a coherent spacing scale.

---

# 15. Native Navigation Audit

Verify:

* Android back behavior
* nested screen navigation
* modal dismissal
* keyboard dismissal
* scroll position
* state restoration
* orientation/state changes where relevant
* bottom navigation state
* deep navigation from profile → transaction → back

The app must behave like a native application, not a collection of independent screens.

---

# 16. Loading / Error / Empty State Audit

Every data-driven screen must have:

### Loading

Skeleton/progress state.

### Empty

Short, useful empty state:

```text
কোনো গ্রাহক নেই
```

with a single CTA where appropriate.

### Error

Human-readable error + retry action.

### Sync

Visible but unobtrusive sync state.

Avoid technical messages such as:

```text
IOException
HTTP 500
SocketTimeoutException
```

in the user UI.

---

# 17. Offline-First UX Audit

Because SAFA is local-first:

When offline:

```text
Create record
→ save locally immediately
→ show saved state
→ queue sync
```

The UI must not feel broken when offline.

Display sync state where useful:

```text
সংরক্ষিত
সিঙ্ক হচ্ছে
সিঙ্ক সম্পন্ন
সিঙ্ক ব্যর্থ
```

Do not block normal data entry because the server is temporarily unavailable.

---

# 18. Sync Failure UX

For:

```text
SYNC_FAILED
```

provide a professional recovery UI.

Example:

```text
সিঙ্ক করা যায়নি

কারণ: সার্ভারের সাথে সংযোগ নেই

[আবার চেষ্টা করুন]
```

For permanent validation errors:

```text
ডাটা ঠিক করুন
```

rather than blindly retrying.

---

# 19. Logo / Branding Architecture

There must be a single canonical branding source.

Recommended:

```text
Server:
https://safa.masarax.com/assets/branding/logo

Android:
remote configurable logo
+
safe bundled fallback
```

If remote logo fails:

```text
bundled SAFA logo
```

must be shown.

Never use:

```text
👑
```

as the default production logo.

Audit:

```text
getCustomAppLogo()
getCustomAppLogoUri()
getServerLogoUrl()
fetchRemoteConfig()
uploadAppLogoToServer()
```

Ensure they use one consistent source of truth.

---

# 20. Remote Configuration Audit

Verify:

```text
app_name
app_logo_url
app_version
local_currency
foreign_currency
```

Flow:

```text
Server config
    ↓
API
    ↓
Android
    ↓
validated local cache
    ↓
UI
```

Requirements:

* remote config failure must not break app
* invalid URL must not break UI
* logo HTTP failure must fall back locally
* stale config must remain usable
* app should not repeatedly fetch unnecessarily

---

# 21. Accessibility Audit

Verify:

* content descriptions
* touch target ≥ 48dp
* readable contrast
* scalable text
* screen reader labels
* semantic button roles
* keyboard/input labels
* dialog semantics

Create automated tests where practical.

---

# 22. Security Audit

Search for:

```text
hardcoded secrets
passwords
API secrets
private keys
debug endpoints
migration endpoints
test credentials
localhost
10.0.2.2
HTTP URLs
unsafe WebViews
cleartext traffic
```

Production must use HTTPS.

No production credentials may exist in the APK.

---

# 23. Production API Verification

Do NOT claim "real E2E production PASS" unless actual HTTP requests are genuinely executed and their results are captured.

Verify:

```text
GET/POST authentication
sync/up
sync/down
remote config
logo upload
health check
```

For each test record:

```text
URL
HTTP method
status code
request type
response structure
database effect
Android local effect
```

Never fabricate production test data.

---

# 24. Automated Test Requirements

Create:

## Laravel

```text
Phase3InstallerSecurityTest.php
Phase3SchemaContractTest.php
Phase3BrandingAssetTest.php
Phase3RemoteConfigTest.php
```

## Android

```text
Phase3BrandingTest.kt
Phase3LocalizationTest.kt
Phase3DesignSystemTest.kt
Phase3SettingsPersistenceTest.kt
Phase3SyncUxTest.kt
```

Tests MUST verify behavior, not merely file existence.

---

# 25. Mandatory Test Matrix

Produce this exact matrix:

| Area          | Test                   | Expected      | Actual | Status |
| ------------- | ---------------------- | ------------- | ------ | ------ |
| Logo          | public logo URL        | 200           |        |        |
| Favicon       | favicon URL            | 200           |        |        |
| Launcher      | foreground             | SAFA asset    |        |        |
| Migration     | new DB                 | migrate       |        |        |
| Migration     | old DB + new migration | update screen |        |        |
| Migration     | missing column         | column added  |        |        |
| Migration     | partial schema         | repaired      |        |        |
| Migration     | unauthorized update    | 403           |        |        |
| Security      | APK secret             | absent        |        |        |
| Dark mode     | restart                | persisted     |        |        |
| Language      | BN                     | Bengali only  |        |        |
| Language      | EN                     | English only  |        |        |
| Dialog        | confirm                | unified       |        |        |
| Dialog        | destructive            | unified       |        |        |
| Button        | primary                | text visible  |        |        |
| Offline       | create record          | local save    |        |        |
| Offline       | sync later             | server save   |        |        |
| Remote config | logo                   | displayed     |        |        |
| Remote config | failure                | fallback      |        |        |

---

# 26. No False PASS Rule

This is mandatory.

Never write:

```text
PASS
```

because:

* code exists
* compilation works
* unit test exists
* previous report says PASS

Only mark PASS when the behavior is verified.

Use:

```text
PASS
FAIL
PARTIAL
NOT VERIFIED
BLOCKED
```

If production access is unavailable, write:

```text
NOT VERIFIED
```

Do not invent evidence.

---

# 27. Final Deliverables

Create:

```text
phase3_adversarial_audit_report.md
phase3_ui_ux_audit.md
phase3_installer_security_audit.md
phase3_test_results.md
```

The final report MUST contain:

1. Previous claim
2. Actual source evidence
3. Problem
4. Severity
5. Required fix
6. Automated test
7. Verification result

---

# 28. Implementation Rule

Do NOT perform a giant uncontrolled rewrite.

Work in this order:

```text
1. Audit
2. Reproduce
3. Write failing tests
4. Fix root cause
5. Run tests
6. Re-audit
7. Verify production behavior
8. Report
```

Every fix must have a regression test.

---

# 29. Priority

Fix in this order:

### P0 — Critical

* Public migration endpoints
* Hardcoded production API secret
* Incorrect migration auto-healing
* Missing production logo
* Incorrect production branding assets

### P1 — High

* Dark mode persistence
* Remote logo fallback
* Database update workflow
* Sync error UX
* inconsistent dialogs
* broken reusable components

### P2 — Medium

* excessive bilingual text
* typography
* spacing
* loading states
* empty states
* navigation polish

### P3 — Final Polish

* animations
* micro-interactions
* accessibility refinements
* visual consistency

---

# FINAL INSTRUCTION

Do NOT tell me that the project is complete merely because the existing tests pass.

The goal is:

> **A production-ready SAFA Android + Laravel system with reliable offline synchronization, safe cPanel database migration, correct branding, secure API architecture, consistent native Android UI/UX, concise localization, and independently verified behavior.**

Start with the audit and failing tests.

Do not skip directly to implementation.

At the end, report every FAIL and PARTIAL item explicitly.
