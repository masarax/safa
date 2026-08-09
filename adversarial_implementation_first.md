# SAFA — Phase 5: Current-Main Adversarial Product Audit, Real UI/UX Refactor & Production Flow Verification

## IMPORTANT — READ FIRST

This task MUST audit the CURRENT `main` branch source code, not previous audit reports.

Repository:
`masarax/safa`

Current branch:
`main`

Do NOT assume that Phase 2, Phase 3, or Phase 4 reports are correct.

Previous reports contain PASS claims that are contradicted by the current source. Therefore this phase is an adversarial verification phase.

The objective is NOT to generate another "PASS" report.

The objective is:

1. inspect the actual current source;
2. identify remaining problems;
3. fix the problems in the source code;
4. add regression tests that would fail before the fix;
5. run the tests;
6. verify the actual user flows;
7. only then produce the final report.

Do not mark something PASS merely because a component/test exists.

---

# PART 1 — CURRENT BRANCH BASELINE

First inspect the complete current `main` branch.

Record:

* current commit SHA;
* Android source structure;
* all Compose screens;
* all reusable UI components;
* all dialogs;
* all bottom sheets;
* all popup/menu implementations;
* all themes/colors/typography;
* all localization maps;
* all Blade views;
* all installer routes/controllers/middleware;
* all migrations;
* all API authentication/security code;
* all existing tests.

Create:

`phase5_current_source_inventory.md`

Do not modify code during the inventory step until the actual architecture is understood.

---

# PART 2 — CRITICAL CPanel / DATABASE UPDATE FLOW

This is P0 and MUST be tested as a real user flow.

Current source already shows a possible contradiction:

`welcome.blade.php` displays a database update button when migrations are pending.

However:

`install_update.blade.php` POSTs to `/install/update-process`

while:

`InstallerController::updateProcess()` requires either:

* `DB_UPDATE_SECRET`, or
* authenticated authorization.

The public update form does not currently provide the secret.

Therefore verify this exact flow.

## Test A — Existing cPanel DB + New Migration

Simulate:

1. Existing production database.
2. Previous migrations already executed.
3. New migration exists.
4. User visits `/`.
5. `/` detects pending migration.
6. Update UI appears.
7. User clicks update.
8. Migration actually executes.
9. Existing data remains intact.
10. User is redirected to the normal application/welcome page.
11. Refreshing `/` no longer shows the update screen.

This must be tested as an actual HTTP feature flow.

Do NOT merely test `getPendingMigrations()`.

## Test B — Update Button Authorization

Determine the correct production architecture.

The public update page must NOT expose `DB_UPDATE_SECRET` to the browser.

Do NOT solve this by hardcoding the secret into Blade/JavaScript.

Instead implement a secure update authorization design.

Preferred behavior:

* `/install/update` is accessible when pending migrations exist;
* update execution requires proper server-side authorization;
* if the update page is intended to be public, create a safe server-side update session/token flow;
* if admin authentication is required, enforce real administrator authentication;
* never authorize merely because `session()->has('user_id')`.

## Test C — Session Spoofing

Explicitly test:

```text
session user_id exists
but no valid authenticated administrator exists
```

Expected:

```text
403 Forbidden
```

The mere existence of a session variable must never authorize production migrations.

## Test D — Secret Fail Closed

Test:

* missing `DB_UPDATE_SECRET`;
* wrong key;
* empty key;
* malformed key;
* correct key.

Expected:

```text
missing secret -> 403
wrong key -> 403
empty key -> 403
correct key -> success
```

## Test E — GET Protection

Verify:

```text
GET /update-db -> 405
```

and no migration occurs.

## Test F — Migration Idempotency

Run update twice.

Expected:

* first update executes pending migration;
* second update does nothing;
* no duplicate migration rows;
* no duplicate tables;
* no duplicate columns;
* no data loss.

---

# PART 3 — AUTO-HEALING SCHEMA CONTRACT MUST MATCH REAL MIGRATIONS

Do NOT trust the manually written schema map.

Compare:

```text
backend/database/migrations/*.php
```

against:

```text
InstallerController::autoHealExistingSchema()
```

Build an automated contract test that verifies every migration's required table/column contract against the actual migration source.

Important:

The contract must not falsely classify an incomplete table as a completed migration.

Test at minimum:

1. missing table;
2. missing column;
3. partially existing table;
4. imported legacy database;
5. fully existing schema;
6. migration already registered;
7. migration not registered;
8. multiple pending migrations;
9. existing data preservation.

The test must prove:

```text
incomplete schema -> migration remains pending
complete schema -> only then migration may be auto-registered
```

Do not use a weak test that merely checks whether the mapping array contains a string.

---

# PART 4 — REAL LOGO / BRANDING VERIFICATION

There are currently multiple branding paths.

Audit:

* launcher icon;
* login screen;
* top app bar;
* settings;
* dashboard;
* splash/startup;
* bundled drawable logo;
* server-provided logo;
* fallback logo;
* website logo;
* favicon;
* Apple touch icon.

## Critical finding to verify

`HundiTopAppBar` currently has a fallback path where `customAppLogo` can be displayed as text.

The application should use the actual bundled SAFA logo image when no remote logo URI is available.

Required fallback hierarchy:

```text
Remote logo URL
      ↓
valid remote image
      ↓
Bundled SAFA logo drawable
      ↓
safe minimal branded fallback
```

Do NOT use:

```text
👑
SAFA text
generic Android icon
generic lock icon
```

as the primary app logo.

## Login screen

The login screen must display the actual SAFA branded logo, not merely a generic lock icon as the primary visual identity.

The lock/security icon may be secondary.

Add a regression test proving the actual branded drawable/resource is referenced.

---

# PART 5 — COMPLETE LOCALIZATION / COPY AUDIT

The previous Phase 4 claim that there are no bilingual compound strings is contradicted by current source.

Examples already found:

```text
EN | বাংলা
```

in `LoginScreen.kt`.

Also:

```text
রিয়াল প্রদান (ডিপোজিট)
রিয়াল গ্রহণ (উত্তোলন)
```

in `DashboardScreen.kt`.

Also:

```text
Safe Area / ফেইফ এরিয়া
```

in `CalculatorDialog.kt`.

These must be fixed.

## Requirement

When language = BN:

```text
ONLY Bengali UI copy
```

When language = EN:

```text
ONLY English UI copy
```

No:

```text
Bangla (English)
English (Bangla)
English | বাংলা
বাংলা | English
```

inside user-facing labels.

Exceptions are allowed only for unavoidable technical identifiers such as:

* SAR
* BDT
* PIN
* API
* OTP
* PDF
* Excel
* etc.

## Full static audit

Scan all:

```text
*.kt
*.xml
*.blade.php
*.js
```

for user-facing strings.

Find:

* bilingual parentheses;
* bilingual separators;
* hardcoded English in Bengali UI;
* hardcoded Bengali in English UI;
* inconsistent capitalization;
* overly verbose labels;
* raw exception text;
* developer terminology;
* unnecessary descriptions.

Create a structured copy inventory.

---

# PART 6 — NATIVE ANDROID UI/UX REFACTOR

This is NOT a test-only task.

The current UI has many screen-specific implementations.

Audit and refactor:

```text
Login
Dashboard
Customers
Customer Profile
Add Customer
Suppliers
Supplier Profile
Add Supplier
Transactions
Wallet
Expenses
Settings
Reports
Calculator
all dialogs
all bottom sheets
all confirmation dialogs
all popup menus
```

## Required design principles

The application should feel like a professional native Android financial/remittance application.

Use one coherent design language.

Standardize:

* spacing;
* screen padding;
* top app bars;
* section headers;
* cards;
* input fields;
* primary buttons;
* secondary buttons;
* destructive actions;
* status chips;
* empty states;
* loading states;
* error states;
* confirmation dialogs;
* bottom sheets;
* dropdowns;
* menus;
* navigation;
* typography;
* icon sizing;
* touch targets;
* corner radius;
* borders;
* elevation;
* dark mode.

Do NOT simply wrap existing components in `AppCard`.

Actually refactor screens to use the design system.

---

# PART 7 — DIALOG / MODAL SYSTEM

Search the entire Android project for:

```text
AlertDialog(
Dialog(
ModalBottomSheet(
DropdownMenu(
Popup(
```

Create an inventory.

Every modal must have a documented reason for being different.

Standardize:

### Confirm dialog

* same width behavior;
* same padding;
* same title hierarchy;
* same body hierarchy;
* same buttons;
* same corner radius;
* same dismissal behavior.

### Destructive dialog

* same base structure;
* destructive semantic color;
* clear primary action;
* safe cancel;
* no accidental destructive execution.

### Bottom sheet

Use a unified bottom-sheet design token system.

### Calculator

The calculator may remain a specialized bottom-sheet interaction, but it must still follow the global:

* typography;
* spacing;
* dark mode;
* colors;
* accessibility;
* touch targets;
* localization;
* sheet behavior.

Do not leave it as an isolated iOS-looking white keyboard design that visually conflicts with the rest of the Android app.

---

# PART 8 — DESIGN SYSTEM MUST BE REAL, NOT DECORATIVE

Audit `DesignSystemComponents.kt`.

The current components include:

* `AppCard`
* `AppStatusChip`
* `AppMetricCard`
* `AppSectionHeader`
* `AppPrimaryButton`
* `AppOutlinedButton`
* `AppTextField`
* `SafaConfirmDialog`
* `SafaDestructiveDialog`

Verify that actual screens USE these components.

Create a usage matrix:

| Component | Screens using it | Screens bypassing it |
| --------- | ---------------- | -------------------- |

Any repeated screen-specific implementation should either:

1. migrate to the shared component, or
2. be justified as a specialized component.

---

# PART 9 — HARD-CODED COLOR AUDIT

Scan all Compose screens for:

```text
Color(0x...
```

Create an inventory.

Move reusable colors into the theme/design tokens.

Avoid situations where one screen uses:

```text
#D7A84B
```

while another screen independently defines unrelated colors for the same semantic role.

Define semantic tokens such as:

```text
primary
onPrimary
surface
surfaceVariant
border
success
warning
error
info
financialPositive
financialNegative
```

Support both light and dark themes.

---

# PART 10 — TYPOGRAPHY AUDIT

Remove excessive manual:

```text
fontSize = ...
fontWeight = ...
```

when Material typography tokens are appropriate.

Create a coherent hierarchy:

```text
Display
Screen title
Section title
Card title
Body
Supporting text
Label
Caption
Numeric financial value
```

Financial numbers should be visually prioritized without making the UI oversized.

---

# PART 11 — NAVIGATION UX

Audit:

* forward navigation;
* back navigation;
* Android system back;
* nested screens;
* dialogs;
* keyboard visibility;
* bottom navigation;
* state preservation;
* scroll position.

Test:

```text
Dashboard
 -> Customer
 -> Customer Profile
 -> Back
 -> Dashboard

Dashboard
 -> Add Customer
 -> Save
 -> Customer list
 -> Back

Dashboard
 -> Transaction
 -> Save
 -> Back
```

No screen should unexpectedly reset or navigate to the wrong destination.

---

# PART 12 — LOADING / EMPTY / ERROR STATES

Every major screen must have:

### Loading

Use consistent skeleton/progress presentation.

### Empty

Explain what is empty and provide a useful CTA.

Example:

```text
No customers yet
Add your first customer
```

not a large paragraph.

### Error

Show user-friendly localized copy.

Never display:

```text
IOException
SocketTimeoutException
RuntimeException
NullPointerException
```

directly to users.

---

# PART 13 — OFFLINE-FIRST UX

Verify every create/update/delete flow:

```text
offline
↓
local Room save
↓
pending state
↓
visible sync status
↓
network returns
↓
background sync
↓
server acknowledgement
↓
local record becomes synced
```

Verify:

* failed sync;
* retry count;
* manual retry;
* max retry;
* permanent rejection;
* pending update;
* pending delete.

Add UI tests for each state.

---

# PART 14 — ACCESSIBILITY

Audit:

* minimum touch target 48dp;
* icon content descriptions;
* contrast;
* text scaling;
* keyboard navigation where applicable;
* focus behavior;
* dialogs;
* bottom navigation;
* buttons;
* fields.

Do not claim accessibility PASS merely because a button is 48dp.

Test actual semantics.

---

# PART 15 — WEBSITE / WELCOME PAGE

Audit:

```text
/
 /install
 /install/update
 /install/success
 /update-db
 /safa-logo.png
 /favicon.svg
```

Verify:

* logo actually loads;
* favicon actually loads;
* correct MIME types;
* no broken image;
* no duplicate logo;
* no bilingual compound text;
* mobile responsive;
* update state clearly visible;
* normal state clearly visible;
* pending migration state clearly visible.

---

# PART 16 — WELCOME PAGE DATABASE UPDATE UX

This is especially important.

When a new cPanel database migration is added:

```text
Existing DB
+
new migration
=
user visits /
```

Expected:

```text
SAFA logo
Database update required
N pending migrations
Update Database button
```

After successful update:

```text
Database up to date
Normal SAFA welcome/application state
```

The button MUST actually work.

Write an end-to-end Laravel test that submits the same form the browser submits.

Do not call the controller directly.

---

# PART 17 — SECURITY REGRESSION

Verify:

```text
GET /update-db -> 405
POST /update-db without secret -> 403
POST /update-db wrong secret -> 403
POST /update-db correct secret -> success
POST /install/update-process without authorization -> 403
POST /install/update-process with fake session user_id -> 403
POST /install/update-process with valid administrator authorization -> success
```

No migration may execute on failed authorization.

---

# PART 18 — TESTS MUST TEST BEHAVIOR, NOT FILE CONTENT

Avoid weak tests such as:

```text
assert file contains "SafaConfirmDialog"
assert file contains "favicon.svg"
```

These are insufficient.

Prefer:

* HTTP feature tests;
* database state assertions;
* migration state assertions;
* Compose UI tests;
* screenshot tests where appropriate;
* navigation tests;
* state transition tests;
* localization tests;
* accessibility semantics tests.

Every bug found must have a regression test that would fail before the fix.

---

# PART 19 — MANDATORY UI SCREEN TEST MATRIX

Create automated tests for:

| Screen       | Light | Dark | BN | EN | Loading | Empty | Error |
| ------------ | ----: | ---: | -: | -: | ------: | ----: | ----: |
| Login        |     ✓ |    ✓ |  ✓ |  ✓ |       ✓ |     — |     ✓ |
| Dashboard    |     ✓ |    ✓ |  ✓ |  ✓ |       ✓ |     ✓ |     ✓ |
| Customers    |     ✓ |    ✓ |  ✓ |  ✓ |       ✓ |     ✓ |     ✓ |
| Suppliers    |     ✓ |    ✓ |  ✓ |  ✓ |       ✓ |     ✓ |     ✓ |
| Transactions |     ✓ |    ✓ |  ✓ |  ✓ |       ✓ |     ✓ |     ✓ |
| Wallet       |     ✓ |    ✓ |  ✓ |  ✓ |       ✓ |     ✓ |     ✓ |
| Expenses     |     ✓ |    ✓ |  ✓ |  ✓ |       ✓ |     ✓ |     ✓ |
| Settings     |     ✓ |    ✓ |  ✓ |  ✓ |       ✓ |     — |     ✓ |
| Reports      |     ✓ |    ✓ |  ✓ |  ✓ |       ✓ |     ✓ |     ✓ |

At minimum, critical UI states must be Compose-tested.

---

# PART 20 — NO PLACEHOLDER DATA IN PRODUCTION UI

Audit for hardcoded fake/sample records.

Current Dashboard source contains fallback placeholder customers such as:

```text
রানা ভাই
হাসেম ভাই
Fahim Rana
নাজমুল চাচা
```

This must be investigated.

Production application UI must never silently display fake financial/customer data when the database is empty.

If an empty database exists:

```text
Show empty state.
```

Do NOT manufacture customers for visual purposes.

Add regression test:

```text
empty Room DB -> zero fake customers displayed
```

---

# PART 21 — NO MAGIC BUSINESS VALUES

Audit financial calculations.

Current Dashboard source contains logic such as:

```text
SAR * 32.5
```

Verify whether this is a hardcoded business rule.

Rates must come from the actual configured rate/domain source.

No hardcoded production financial rate should exist unless explicitly defined as a documented constant/business rule.

Add tests for:

* customer rate;
* supplier rate;
* currency conversion;
* BDT;
* SAR;
* profit;
* expense;
* income.

---

# PART 22 — RAW / UNKNOWN DATA

Audit fallback strings such as:

```text
Unknown Customer
Unknown Supplier
```

These must be localized and preferably resolved through actual relationship/state handling.

Do not leak developer-oriented fallback copy into the final UI.

---

# PART 23 — REMOTE LOGO

Verify:

```text
remote logo configured
remote logo loads
remote logo invalid
remote logo unavailable
network unavailable
```

Expected fallback:

```text
bundled SAFA logo
```

not:

```text
text "SAFA"
emoji
blank circle
generic icon
```

---

# PART 24 — FINAL BUILD / TEST COMMANDS

Run all applicable tests.

Laravel:

```bash
cd backend
php artisan test
```

Android:

```bash
.\gradlew.bat test
```

Also run:

```bash
.\gradlew.bat compileDebugKotlin
```

If lint is configured:

```bash
.\gradlew.bat lint
```

If screenshot/UI tests exist, execute them too.

Do not report "ALL TESTS PASS" unless the commands actually completed successfully.

---

# PART 25 — FINAL ACCEPTANCE CRITERIA

Phase 5 is NOT PASS unless ALL of the following are true:

### Branding

* [ ] actual SAFA logo appears on Android login;
* [ ] actual SAFA logo appears in top app bar;
* [ ] launcher uses SAFA logo;
* [ ] website logo loads;
* [ ] favicon loads;
* [ ] no emoji logo fallback;
* [ ] no generic Android logo.

### Localization

* [ ] BN = Bengali only;
* [ ] EN = English only;
* [ ] no `EN | বাংলা`;
* [ ] no `Bangla (English)`;
* [ ] no `English (Bangla)`;
* [ ] no bilingual compound labels;
* [ ] no bilingual technical prose.

### UI/UX

* [ ] common design system actually used;
* [ ] dialogs consistent;
* [ ] bottom sheets consistent;
* [ ] buttons consistent;
* [ ] inputs consistent;
* [ ] cards consistent;
* [ ] spacing consistent;
* [ ] typography consistent;
* [ ] light mode polished;
* [ ] dark mode polished;
* [ ] empty states polished;
* [ ] error states polished;
* [ ] loading states polished.

### Database Update

* [ ] fresh install works;
* [ ] existing DB works;
* [ ] pending migration detected;
* [ ] update screen appears;
* [ ] update button actually executes migration;
* [ ] unauthorized execution blocked;
* [ ] fake session authorization blocked;
* [ ] valid authorization works;
* [ ] existing data preserved;
* [ ] second execution is idempotent;
* [ ] update screen disappears after successful migration.

### Production Safety

* [ ] no hardcoded API secrets;
* [ ] no hardcoded DB update secret;
* [ ] no fake production data;
* [ ] no hardcoded financial conversion rate;
* [ ] no raw exception messages;
* [ ] no unsafe migration endpoint;
* [ ] no authorization bypass.

### Sync

* [ ] offline create;
* [ ] offline update;
* [ ] offline delete;
* [ ] background sync;
* [ ] retry;
* [ ] max retry;
* [ ] permanent failure;
* [ ] manual retry;
* [ ] correct sync status.

---

# PART 26 — DELIVERABLES

Create:

```text
phase5_current_source_inventory.md
phase5_security_audit.md
phase5_cpanel_migration_e2e.md
phase5_branding_audit.md
phase5_localization_copy_audit.md
phase5_native_ui_ux_audit.md
phase5_design_system_usage.md
phase5_offline_sync_ui_audit.md
phase5_test_results.md
phase5_final_acceptance_report.md
```

The final report MUST contain:

1. Actual findings.
2. Exact files changed.
3. Root cause.
4. Fix.
5. Regression test.
6. Test command.
7. Actual test result.
8. Remaining issues, if any.

Do NOT write "PASS" for an item merely because a source file or test exists.

A requirement is PASS only after actual behavioral verification.

---

# FINAL RULE

This phase is an adversarial production-readiness audit.

Do not optimize for producing a PASS report.

Optimize for discovering what is still wrong.

If something is wrong, FIX IT.

If it cannot safely be fixed, report it honestly.

If a previous Phase 2/3/4 report conflicts with current source code, trust the CURRENT SOURCE CODE and document the discrepancy.

Do not stop after security fixes.

The final objective is a genuinely polished, professional, native-feeling Android application plus a reliable cPanel/Laravel installation and database-update lifecycle.
