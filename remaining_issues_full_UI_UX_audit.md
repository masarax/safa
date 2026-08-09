# SAFA — Phase 4 Current-Main-Branch Deep Audit, Production Safety & Full Native UI/UX Refinement

Repository:

`masarax/safa`

IMPORTANT:

The repository has just been updated on the `main` branch.

You MUST audit the CURRENT `main` branch only.

Do NOT rely on older commits, previous audit reports, previous screenshots, previous assumptions, or previously reported failures.

The current source already contains several Phase 2/3 fixes. Do NOT revert or duplicate fixes that are already correct.

Your task is to inspect the CURRENT source, identify remaining real problems, implement only necessary fixes, and verify them with meaningful tests.

The objective is NOT to generate another optimistic PASS report.

Use this workflow:

```text
CURRENT SOURCE
↓
AUDIT
↓
IDENTIFY REAL FINDINGS
↓
WRITE REGRESSION TESTS
↓
RUN TESTS
↓
FIX ROOT CAUSE
↓
RUN TESTS AGAIN
↓
FULL BUILD / TEST / LINT
↓
PRODUCTION-SAFETY VERIFICATION
↓
FINAL REPORT
```

Never mark something PASS merely because a component/function exists.

---

# PART 1 — CURRENT BASELINE VERIFICATION

First inspect the current `main` branch and establish a baseline.

Verify the actual current versions of:

### Android

* TokenManager.kt
* ApiService.kt
* SyncManager.kt
* AutoSyncWorker.kt
* AppRepository.kt
* AppDaos.kt
* AppDatabase.kt
* Models.kt
* HundiViewModel.kt
* MainActivity.kt
* DesignSystemComponents.kt
* Theme files
* every screen under `ui/screens`

### Laravel

* routes/web.php
* routes/api.php
* InstallerController.php
* RemoteConfigController.php
* CheckInstalled.php
* EnsureNotInstalled.php
* all migrations
* all Blade views
* config files
* tests

Do not assume the previous Phase 3 report is correct.

---

# PART 2 — CRITICAL FINDING ALREADY IDENTIFIED IN CURRENT SOURCE

There is one issue that MUST be investigated first.

## `/install/update-process`

Current route is:

```php
Route::post('/install/update-process', [InstallerController::class, 'updateProcess']);
```

Current `updateProcess()` appears to execute:

```php
Artisan::call('migrate', ['--force' => true]);
```

without the same explicit authorization mechanism used by `/update-db`.

This is a production database mutation endpoint.

## Required behavior

Unauthorized request:

```text
POST /install/update-process
```

must NOT execute migration.

It must return:

```text
403
```

or another secure authorization failure.

Valid authorized request must be able to perform the migration.

### Required tests

Write tests that actually execute HTTP requests.

Test:

1. POST without authorization → 403.
2. POST with wrong authorization → 403.
3. POST with valid authorization → authorized path.
4. Unauthorized request must not invoke migration.
5. No database mutation occurs before authorization.
6. Secret is not exposed in HTML or JavaScript.

Do not write a superficial source-string test.

---

# PART 3 — `/update-db` SECURITY HARDENING

Current `/update-db` already contains authorization logic.

Do NOT remove it.

However, audit the current implementation.

Current source uses:

```php
env('DB_UPDATE_SECRET', 'safa_secure_update_key_2026')
```

This default fallback is unacceptable for production security.

Required:

* No hardcoded production migration secret.
* Secret must come from environment/configuration.
* Missing secret configuration should fail closed.
* Do not silently fall back to a known public secret.
* GET must NOT be allowed to mutate production database.
* Prefer POST-only for migration.
* Validate authorization before any migration-related work.
* Do not expose secret in response.
* Add appropriate rate limiting if architecture allows.

Required tests:

```text
GET /update-db → 405 or 403
POST without secret → 403
POST wrong secret → 403
POST valid secret → authorized
missing DB_UPDATE_SECRET → fail closed
```

Do not break legitimate update functionality.

---

# PART 4 — INSTALLER / CPANEL DATABASE UPDATE FLOW

This is a core requirement of SAFA.

The system must correctly support:

## Scenario A — Completely new cPanel database

```text
Empty database
↓
Install page
↓
Connect database
↓
Run all migrations
↓
Create application schema
↓
Create installed lock
↓
Welcome page
```

Verify this path.

---

## Scenario B — Existing SAFA database + new migration

```text
Existing database
+
new migration file
↓
application detects pending migration
↓
database update screen
↓
authorized update
↓
migration executes
↓
data preserved
↓
welcome page
```

Verify this path.

---

## Scenario C — Existing schema but migration missing from migrations table

The system should determine whether the schema really satisfies the migration contract before marking that migration as already executed.

Do not blindly mark migrations complete.

---

## Scenario D — Missing column

Example:

```text
customers exists
required new column missing
```

Expected:

```text
migration remains pending
↓
migration executes
↓
column added
↓
existing data preserved
```

---

## Scenario E — Partial database

Some tables exist.

Some tables do not.

Some columns exist.

Some columns do not.

Expected:

* no false-positive migration completion
* missing schema detected
* migration remains executable
* existing data preserved
* no destructive table recreation

---

# PART 5 — MIGRATION CONTRACT AUDIT

Current `autoHealExistingSchema()` already contains migration mappings.

Do NOT assume those mappings are correct.

Compare every mapping against the actual migration source files.

Inspect:

```text
backend/database/migrations/
```

For every migration:

* exact filename
* exact tables
* exact required columns
* foreign keys
* indexes where relevant
* nullable/non-nullable requirements where relevant
* migration ordering

The contract must never claim a migration is complete when the actual schema is incomplete.

Especially verify:

```text
2026_01_01_000000_create_safa_tables
2026_01_02_000000_expand_hundi_and_wallet_tables
2026_01_03_000000_add_deleted_at_to_sync_tables
2026_01_04_000000_create_device_bindings_and_tokens_tables
2026_01_05_000000_create_superadmin_and_rbac_tables
2026_01_06_000000_create_account_shares_table
2026_01_07_000000_create_system_settings_table
```

Do not hardcode assumptions that differ from the migration files.

---

# PART 6 — TOKEN / API SECURITY

Current `TokenManager.kt` already has empty defaults for API key and secret.

Do NOT reintroduce static credentials.

Verify the entire Android project for:

```text
safa_key_
safa_sec_
DEFAULT_API_KEY
DEFAULT_API_SECRET
OBFUSCATED_API_KEY
OBFUSCATED_API_SECRET
SAFA_API_KEY
SAFA_API_SECRET
```

The Android application must not contain production API credentials.

Verify:

* source
* resources
* BuildConfig
* assets
* JSON
* XML
* generated APK if possible

The client should rely on the intended server authentication/session mechanism.

---

# PART 7 — LOGO / FAVICON / BRANDING

Verify current branding implementation.

Required:

### Android

* launcher icon
* adaptive icon
* foreground
* background
* monochrome icon if used

No generic Android robot.

No emoji.

No unrelated logo.

### Web

Verify:

```text
backend/public/safa-logo.png
backend/public/favicon.svg
```

But do not merely check that files exist.

Actually verify:

```text
GET /safa-logo.png
GET /favicon.svg
```

Expected:

* HTTP 200
* correct Content-Type
* non-empty response
* valid image/SVG content

Check:

* welcome
* install
* install success
* install update

All must use the canonical SAFA branding.

---

# PART 8 — REMOTE CONFIGURATION

Audit:

`RemoteConfigController.php`

and Android remote configuration consumption.

Verify:

```text
app_name
app_logo_url
app_version
local_currency
foreign_currency
feature flags
```

Test:

### Valid config

Server values appear correctly.

### Empty logo

Bundled SAFA logo appears.

### Broken logo URL

Bundled logo appears.

### Network failure

Application remains usable.

### Malformed response

Application remains usable.

### Missing fields

Safe defaults are applied.

No emoji fallback.

No application crash.

---

# PART 9 — FULL LOCALIZATION AUDIT

The original requirement was:

NO unnecessary:

```text
বাংলা (English)
English (বাংলা)
Bangla / English
```

Current language system must be audited across the actual user interface.

Search the entire repository.

Inspect:

* Dashboard
* Customer
* Supplier
* Transaction
* Wallet
* Expense/Income
* Settings
* Login
* dialogs
* empty states
* error states
* installer
* update screen
* success screen
* sync status
* buttons
* navigation
* form labels
* helper text

When language = BN:

```text
Bengali only
```

When language = EN:

```text
English only
```

Do not accept a translation-map-only test.

Tests should inspect actual user-facing strings and UI state.

Also remove unnecessarily long text.

For example:

Bad:

```text
ডাটাবেস আপডেট প্রয়োজন (Database Update Required)
```

Good BN:

```text
ডাটাবেস আপডেট প্রয়োজন
```

Good EN:

```text
Database update required
```

---

# PART 10 — NATIVE ANDROID UI/UX — DEEP AUDIT

This is the most important UI requirement.

The current design-system components exist.

That is NOT enough.

You must verify that actual screens USE the design system consistently.

Audit every screen:

```text
Login
Dashboard
Customers
Suppliers
Transactions
Wallet
Expenses / Income
Rates
Settings
Profile
Search
Filters
Forms
Details
Sync
```

For each screen inspect:

### App bar

* consistent
* proper hierarchy
* native Android behavior
* back navigation
* title
* actions

### Cards

Use the same card language.

Avoid random:

* corner radius
* border
* padding
* elevation
* colors

### Buttons

Standardize:

* primary CTA
* secondary CTA
* destructive CTA
* icon buttons

Minimum touch target approximately 48dp.

### Text fields

Standardize:

* label
* placeholder
* error
* focus
* keyboard
* supporting text

### Spacing

Use consistent spacing tokens.

### Typography

Use a clear hierarchy:

```text
Display
Headline
Title
Body
Label
Caption
```

Avoid arbitrary font sizes everywhere.

### Colors

Use centralized theme/design tokens.

No arbitrary screen-specific colors unless semantically justified.

### Shapes

Use consistent corner radius.

---

# PART 11 — MODAL / DIALOG AUDIT

Search all Android source for:

```text
AlertDialog
Dialog
BasicAlertDialog
ModalBottomSheet
```

Build an inventory.

Determine:

* which dialogs exist
* where they are used
* whether they are confirm/destructive/info/input dialogs

Every normal confirmation should use:

```text
SafaConfirmDialog
```

Every destructive confirmation should use:

```text
SafaDestructiveDialog
```

unless there is a documented UX reason.

Standardize:

* radius
* title
* body
* button order
* CTA hierarchy
* destructive color
* dismiss behavior
* accessibility
* minimum touch target

Do NOT only test that the component exists.

Test usage across actual screens.

---

# PART 12 — NATIVE BOTTOM SHEETS / FORMS

Where appropriate, prefer native-feeling:

* bottom sheets
* full-screen forms
* date pickers
* dropdowns
* segmented controls
* filters

Avoid browser-like UI patterns.

The app should feel like a polished Android financial application, not a web page wrapped in Android.

---

# PART 13 — LOADING / EMPTY / ERROR / SUCCESS STATES

Every major screen must have professional states.

### Loading

Use appropriate skeleton/progress UI.

### Empty

Explain what is empty and provide a useful CTA.

### Error

Human-readable message + retry action.

### Success

Use concise confirmation.

### Sync

Clearly distinguish:

```text
Pending
Syncing
Synced
Failed
Requires review
```

Do not show raw Java/Kotlin exceptions to users.

---

# PART 14 — TEXT DENSITY / COPY QUALITY

Audit all UI text.

Remove unnecessary paragraphs.

Buttons should describe actions:

Good:

```text
Save
Add customer
Send
Retry
Delete
```

Not:

```text
Click here to save this customer information
```

Dialogs should be concise.

Cards should not contain paragraphs unless necessary.

Settings should use:

```text
Title
short supporting description
```

not long explanatory blocks.

---

# PART 15 — DASHBOARD UX

The dashboard is the first impression.

Audit:

* balance hierarchy
* key metrics
* transaction summary
* quick actions
* recent activity
* sync status
* visual hierarchy
* whitespace
* card density

It should look like a professional financial app.

Do not overcrowd the screen.

Do not make every metric a large card.

Use information hierarchy.

---

# PART 16 — FINANCIAL DATA UX

Because SAFA is a remittance/accounting application, verify:

* SAR values
* BDT values
* rates
* profit
* due
* paid
* partial
* wallet balance

Use consistent number formatting.

Use clear currency labels.

Avoid ambiguous values.

Use semantic colors carefully:

* positive
* negative
* warning
* pending
* error

Do not rely only on color to communicate status.

---

# PART 17 — ACCESSIBILITY

Verify:

* touch targets >= approximately 48dp
* content descriptions for meaningful icons
* no important information conveyed only through color
* readable contrast
* scalable text where appropriate
* screen-reader meaningful labels
* keyboard/focus behavior
* dialog accessibility

---

# PART 18 — OFFLINE-FIRST UX

Verify actual flow:

```text
offline
↓
create customer
↓
immediately visible
↓
PENDING
↓
network restored
↓
sync
↓
server ID assigned
↓
SYNCED
```

Repeat for transactions and related entities.

Verify:

* failed sync
* retryable errors
* permanent errors
* max retry
* manual retry
* duplicate upload
* foreign-key resolution

---

# PART 19 — SYNC RETRY HARDENING REGRESSION

The existing retry hardening must remain intact.

Verify:

```text
retryCount
lastSyncAttemptAt
SYNC_FAILED
manual retry
PENDING_CREATE
PENDING_UPDATE
PENDING_DELETE
WorkManager backoff
```

Do not regress existing sync functionality.

---

# PART 20 — DATABASE / INSTALLER UI

When a new cPanel database or a database with pending migration is detected:

The user must see a professional database update screen instead of being sent directly to the generic welcome page.

Expected:

```text
Application
↓
Detect pending migrations
↓
Database update screen
↓
Show concise migration status
↓
User authorizes update
↓
Migration
↓
Success
↓
Welcome
```

If database is already current:

```text
Welcome page
```

No unnecessary update screen.

---

# PART 21 — WEB UI PROFESSIONALISM

Audit:

* welcome page
* install page
* install success
* update page

Requirements:

* consistent SAFA branding
* responsive layout
* concise copy
* proper logo
* proper favicon
* professional spacing
* clear CTA
* accessible controls
* no unnecessary bilingual text
* no raw exception messages
* no debug UI

---

# PART 22 — SECURITY / DEBUG AUDIT

Search entire repository for:

```text
dd(
dump(
var_dump(
print_r(
TODO
FIXME
debug
secret
password
token
api_secret
api_key
```

Inspect every match.

Do not automatically delete legitimate references.

Remove:

* debug output
* temporary credentials
* test credentials
* production secrets
* accidental sensitive logs

Ensure:

```text
APP_DEBUG=false
```

is correct for production.

---

# PART 23 — TEST QUALITY AUDIT

Review previous Phase 2/3 tests.

Identify tests that only prove:

```text
source contains X
file exists
function exists
```

Those are insufficient for critical behavior.

For P0/P1 behavior, tests should exercise actual behavior.

Examples:

```text
HTTP request → response
database state → migration behavior
UI state → rendered content
retry state → DAO result
remote config → fallback behavior
```

Do not delete useful existing tests.

Improve weak tests where necessary.

---

# PART 24 — BUILD / TEST / LINT

Run:

```text
.\gradlew.bat clean
.\gradlew.bat compileDebugKotlin
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lint
```

Laravel:

```text
php artisan test
```

Also run any project-specific test suites.

Report exact:

```text
tests
passed
failed
skipped
warnings
```

Do not write "all tests passed" unless actual command output proves it.

---

# PART 25 — REQUIRED DELIVERABLES

Generate:

```text
phase4_current_branch_audit.md
phase4_security_verification.md
phase4_installer_cpanel_verification.md
phase4_native_ui_ux_audit.md
phase4_localization_copy_audit.md
phase4_test_results.md
```

For every actual finding:

```text
Finding ID
Severity
Current file
Current behavior
Expected behavior
Reproduction
Root cause
Fix
Regression test
Final verification
Status
```

Allowed:

```text
PASS
FAIL
PARTIAL
NOT VERIFIED
```

Never use PASS when something was not actually tested.

---

# FINAL ACCEPTANCE CRITERIA

Do NOT report overall production readiness unless all critical criteria are genuinely verified.

### Security

* `/update-db` secure
* `/install/update-process` secure
* no GET database mutation
* no hardcoded migration secret
* no production API secret in APK
* no secret leakage

### Installer

* fresh database
* existing database
* pending migration
* partial schema
* missing column
* migration contract
* data preservation

### Branding

* Android launcher
* web logo
* favicon
* production HTTP asset verification

### Localization

* BN only Bengali
* EN only English
* no unnecessary bilingual strings
* concise copy

### Android UI/UX

* native-feeling
* consistent cards
* consistent buttons
* consistent inputs
* consistent dialogs
* consistent bottom sheets
* consistent typography
* consistent spacing
* consistent navigation
* professional loading/error/empty states

### Sync

* offline save
* sync
* retry
* max retry
* manual retry
* duplicate protection
* FK resolution

### Verification

```text
Android compile: PASS
Android tests: PASS
Android lint: PASS
Laravel tests: PASS
P0: PASS
P1: PASS
```

If any criterion is not actually verified, report it honestly.

IMPORTANT:

Do not revert already-correct Phase 2/3 changes.

Do not invent failures based on old commits.

Do not fix unrelated architecture.

Do not weaken security.

Do not create tests merely to make the report PASS.

The current `main` branch is the only source of truth.
