# SAFA Phase 6 — Independent Adversarial Verification, Migration Contract Integrity & Production Gate

You are now working against the **CURRENT pushed `main` branch only**.

Repository:
`masarax/safa`

## CRITICAL BASELINE RULE

Do NOT rely on any previous Phase 4/Phase 5 report as proof.

Do NOT assume that a documented `PASS` means the implementation is actually correct.

Do NOT inspect an old commit or stale local source.

First establish the exact current `main` HEAD and inspect the actual source files in that commit.

The current `main` branch is expected to contain the Phase 5 changes. Treat the current source code as the only implementation truth.

Your job in Phase 6 is **independent adversarial verification**, not confirmation of the previous agent's claims.

If a previous report says PASS but the current source contradicts it, the source wins.

---

# PHASE 6 OBJECTIVE

Perform a deep, adversarial production-readiness audit of the CURRENT SAFA codebase.

The primary goals are:

1. Prove the cPanel migration auto-healing system is actually safe.
2. Prove migration schema contracts are complete and cannot falsely mark incomplete schemas as migrated.
3. Prove `/install/update-process` cannot be abused, replayed, spoofed, or bypassed.
4. Prove `/update-db` is fail-closed and cannot mutate the database without valid authorization.
5. Verify the actual Android UI implementation instead of trusting audit documents.
6. Verify localization isolation across Android and Web.
7. Verify branding/logo/fallback behavior from actual source/assets.
8. Verify dynamic financial/business calculations.
9. Verify offline/sync behavior and retry state transitions.
10. Search for remaining hardcoded values, fake data, placeholder data, mixed-language copy, security weaknesses, and inconsistent UI components.
11. Run real tests after every correction.
12. Only declare production readiness if the actual source and tests justify it.

---

# 1. FIRST: CURRENT SOURCE BASELINE

Before modifying anything:

* Verify current branch.
* Verify current HEAD commit SHA.
* Inspect git status.
* Inspect the actual files currently present.
* Do not use stale assumptions from earlier phases.

Create:

`phase6_current_source_baseline.md`

Include:

* current branch
* current HEAD
* repository state
* relevant source files inspected
* migration files inspected
* Android screens inspected
* backend routes/controllers inspected
* tests inspected

Do not claim anything that was not verified from current source.

---

# 2. P0 — MIGRATION CONTRACT MUST BE TRULY 1:1

This is the most important Phase 6 task.

The current `InstallerController::autoHealExistingSchema()` claims to use exact migration/schema contracts.

Independently verify this claim.

For EVERY migration file in:

`backend/database/migrations/`

extract the actual schema created/modified by the migration.

Then compare it against:

`InstallerController::$migrationSchemaMap`

The contract must represent the actual required schema sufficiently to prevent a false-positive migration registration.

## IMPORTANT

Do NOT merely check a few important columns.

For CREATE TABLE migrations, inspect the COMPLETE structural contract including, where applicable:

* table existence
* required columns
* column names
* primary keys
* important foreign keys
* unique constraints
* indexes where required for application correctness
* nullable/non-nullable requirements where meaningful
* relevant defaults
* relationships between tables
* columns added by subsequent migrations

For ALTER migrations:

* verify every column that migration is responsible for
* verify the migration cannot be auto-registered if any required alteration is missing

## Current known concern

The current source appears to have incomplete contracts.

For example, the migration:

`2026_01_01_000000_create_safa_tables.php`

contains more schema than the current contract map appears to validate.

Do not blindly trust this observation either; verify it directly against the migration source.

If the contract is incomplete, FIX IT.

Do not merely update the documentation.

---

# 3. ADVERSARIAL DATABASE SCENARIOS

Create real automated tests for all of the following.

### Scenario A — Completely valid existing schema

Expected:

* migration may be safely auto-registered
* no duplicate table creation
* no data loss

### Scenario B — Existing table but one required column missing

Expected:

* migration MUST NOT be marked complete
* migration MUST remain pending
* actual migration must execute
* existing data must remain intact

### Scenario C — Existing table with multiple missing columns

Expected:

* migration MUST remain pending
* migration must repair the schema

### Scenario D — Existing table with missing important index/constraint

Expected:

* determine whether auto-healing can safely handle it
* if it cannot safely verify it, DO NOT falsely mark the migration complete

### Scenario E — Partial schema

Some tables exist and others do not.

Expected:

* no false-positive migration registration
* migration executes safely

### Scenario F — Existing data

Populate realistic customer/supplier/transaction/wallet data.

Run update.

Verify:

* row count unchanged
* important values unchanged
* relationships preserved
* migration completes
* no duplicate records

### Scenario G — Run migration twice

Expected:

* first execution succeeds
* second execution is idempotent
* no duplicate table/column/row errors

### Scenario H — Migration failure

Force a migration failure.

Verify:

* application does not falsely report successful completion
* migration state remains recoverable
* authorization token behavior is safe
* database is not left in a falsely completed state

---

# 4. P0 — `/install/update-process` ADVERSARIAL SECURITY

Current implementation uses:

* `safa_update_token`
* `DB_UPDATE_SECRET`
* authenticated admin/superadmin authorization

Verify all three independently.

Create tests for:

### Must return 403

* no token
* wrong token
* empty token
* random token
* fake `session(['user_id' => 999])`
* fake session user without valid authorization
* wrong secret
* empty secret
* malformed request
* missing authorization header
* expired/stale session
* token from another session

### Must succeed only when valid

* valid single-use update token
* valid configured secret
* genuinely authenticated authorized administrator

### Single-use requirement

Use one valid update token.

First request:

* must authorize

Second request using exactly the same token:

* MUST be rejected

### Concurrent/replay testing

Attempt repeated submission / duplicate submission.

Verify the endpoint cannot execute the migration twice because the same token was reused.

### CSRF

Verify normal browser POST remains CSRF protected.

Do not weaken CSRF just to make migration testing easier.

---

# 5. P0 — `/update-db` SECURITY

Verify current route and implementation.

Required behavior:

* GET `/update-db` => 405
* POST without secret => 403
* POST with wrong secret => 403
* POST with empty secret configuration => 403
* POST with valid secret => authorized
* secret must never be returned
* secret must never be logged into response
* no fallback/default production secret
* no static secret in source
* no static secret in Android APK/source

Also verify:

* database migration cannot run before authorization
* `Artisan::call('migrate')` cannot be reached through unauthorized control flow
* exceptions do not leak credentials or sensitive configuration

---

# 6. INSTALLER SECURITY AUDIT

Inspect:

* `/install`
* `/install/process`
* `/install/test-db`
* `/install/update`
* `/install/update-process`
* `/install/success`

Look for:

* credential leakage
* DB password exposure
* API secret exposure
* exception leakage
* CSRF bypass
* session fixation
* authorization bypass
* unsafe file writes
* path traversal
* arbitrary configuration injection
* unsafe environment modification
* unintended repeated installation
* installation lock bypass

Especially verify that installer error messages do not expose raw PDO/database credentials or unnecessary internal exception details in production.

---

# 7. WEB UI / INSTALLER UX AUDIT

Audit actual Blade files, not documentation:

* `install.blade.php`
* `install_update.blade.php`
* `install_success.blade.php`
* `welcome.blade.php`

Verify:

* professional visual hierarchy
* consistent typography
* consistent spacing
* consistent buttons
* consistent branding
* responsive mobile layout
* no awkward emoji-based pseudo-branding
* no mixed-language sentences
* no `English (Bangla)` / `Bangla (English)` compound copy
* clean BN mode
* clean EN mode
* all interactive controls have proper touch targets
* no misleading "100% guarantee" claims unless technically justified
* error states are professional
* loading states are professional
* migration state is clearly communicated

Do not merely remove bilingual text.

Improve the actual UI where needed.

---

# 8. ANDROID UI/UX — REAL SOURCE AUDIT

Inspect every current Compose screen:

* LoginScreen
* DashboardScreen
* CustomerScreen
* SupplierScreen
* TransactionScreen
* WalletScreen
* ExpenseScreen
* SettingsScreen
* CalculatorDialog
* MainActivity
* shared design-system components

Verify actual usage of:

* AppCard
* AppMetricCard
* AppStatusChip
* AppSectionHeader
* AppPrimaryButton
* AppOutlinedButton
* AppTextField
* SafaConfirmDialog
* SafaDestructiveDialog

Do not accept a component as "used" merely because it exists.

Check actual screen composition.

---

# 9. ANDROID UI CONSISTENCY AUDIT

Check:

* typography hierarchy
* spacing scale
* corner radius
* icon sizing
* button height
* touch targets >= 48dp
* text field consistency
* card consistency
* dialog consistency
* snackbar/error presentation
* loading/skeleton presentation
* empty states
* error states
* success states
* dark mode
* light mode
* keyboard interaction
* bottom-sheet behavior
* scrolling
* accessibility labels
* content descriptions
* contrast

Find and correct inconsistent one-off UI implementations.

---

# 10. PLACEHOLDER / FAKE DATA AUDIT

Search the ENTIRE Android source for:

* fake customer names
* fake supplier names
* sample financial values
* fake transaction records
* placeholder SAR/BDT amounts
* magic exchange rates
* demo balances
* hardcoded production-looking records

Do not only search for the names already mentioned in Phase 5.

Search broadly for patterns such as:

* hardcoded arrays
* `listOf(...)`
* sample objects
* fallback records
* default financial values
* fake names
* suspicious numeric constants

If any production screen depends on fake data, remove it.

---

# 11. BUSINESS LOGIC / FINANCIAL CALCULATION AUDIT

Search for hardcoded:

* exchange rates
* conversion multipliers
* SAR/BDT rates
* customer rates
* supplier rates
* profit percentages
* balances
* fees

Every production financial calculation must derive from the correct persisted/system state.

Verify:

* customer rate
* supplier rate
* transaction amount
* BDT amount
* wallet balances
* deposits
* expenses/income
* profit calculations

Add regression tests for realistic financial scenarios.

---

# 12. LOCALIZATION AUDIT

Search ALL Android source and Web UI source for:

* mixed Bengali + English labels
* duplicated language labels
* `EN | বাংলা`
* `Bangla (English)`
* `English (Bangla)`
* Bengali text inside English-only UI
* English text inside Bengali-only UI
* accidental bilingual comments rendered in UI
* raw exception names shown to users

Verify language switching at runtime.

BN mode must present clean Bengali UI.

EN mode must present clean English UI.

Technical identifiers, currencies, product names, and unavoidable proper nouns may remain where appropriate.

---

# 13. BRANDING AUDIT

Verify actual assets:

* launcher icon
* foreground icon
* background icon
* login logo
* top app bar logo
* remote logo
* fallback logo
* web logo
* favicon

Search for:

* Android robot artwork
* crown emoji
* lock icon used as fake branding
* text-only fallback where an actual logo should exist
* broken image fallback
* incorrect asset MIME types
* 404 logo routes

Verify:

`/safa-logo.png`

and

`/favicon.svg`

using actual route tests.

---

# 14. OFFLINE-FIRST / SYNC AUDIT

Inspect actual implementation of:

* Room persistence
* pending state
* sync queue
* WorkManager
* retry count
* exponential backoff
* failed state
* manual retry
* server ID mapping
* duplicate prevention
* foreign-key mapping
* conflict handling

Test:

1. create entity offline
2. verify immediate local visibility
3. reconnect
4. sync
5. verify server mapping
6. verify local state becomes synced
7. force failure
8. verify retry
9. exceed retry limit
10. verify final failed state
11. manual retry
12. verify successful recovery

Do not accept a documentation-only lifecycle diagram.

---

# 15. API / TOKEN SECURITY AUDIT

Inspect:

* TokenManager
* ApiSecurityInterceptor
* authentication/session code
* refresh token handling
* device token
* fingerprint token
* session token
* JWT handling

Search the complete repository for:

* `safa_key_`
* `safa_sec_`
* hardcoded API keys
* hardcoded secrets
* passwords
* private tokens
* fallback production credentials

Verify no production secret is embedded in Android source/resources/build config.

---

# 16. TEST QUALITY AUDIT

Do not only count tests.

Inspect whether the tests actually prove the requirements.

For every security test ask:

> Would this test fail if the vulnerability were reintroduced?

For every migration test ask:

> Would this test fail if one important schema column were silently removed from the contract map?

If the answer is NO, improve the test.

Avoid tests that merely inspect source strings when runtime behavior can be tested.

Source inspection tests may be supplementary, not the primary proof for security-critical behavior.

---

# 17. REQUIRED TEST EXECUTION

After corrections run:

### Laravel

```bash
cd backend
php artisan test
```

### Android

```powershell
.\gradlew.bat test --continue
```

Also run relevant static/search checks where useful.

Do not report "PASS" unless the command actually completed successfully.

Record:

* total tests
* passed
* failed
* skipped
* duration
* relevant test classes
* any warnings

---

# 18. REQUIRED DOCUMENTATION

Create/update:

`phase6_current_source_baseline.md`

`phase6_migration_contract_audit.md`

`phase6_security_adversarial_audit.md`

`phase6_installer_e2e_audit.md`

`phase6_native_ui_ux_audit.md`

`phase6_localization_audit.md`

`phase6_business_logic_audit.md`

`phase6_offline_sync_audit.md`

`phase6_test_results.md`

`phase6_final_production_gate.md`

---

# 19. FINAL PRODUCTION GATE

Do NOT automatically write "100% PASS".

Use one of:

### PASS

Only if all critical requirements are empirically verified.

### PASS WITH NON-BLOCKING FINDINGS

Only if remaining issues are genuinely non-critical and do not compromise production safety/correctness.

### BLOCKED

If any P0/P1 security, migration integrity, data-loss, authentication, financial-calculation, or major UI correctness issue remains.

The final report MUST explicitly list:

* fixed issues
* remaining issues
* severity
* exact source file
* exact reason
* test evidence
* production impact

---

# 20. IMPORTANT CURRENT SOURCE ISSUE TO VERIFY

There is already a specific concern visible in the current `main` source:

`InstallerController::autoHealExistingSchema()` appears to define only a partial schema contract for several migrations.

For example, the actual:

`2026_01_01_000000_create_safa_tables.php`

defines additional columns beyond some of the columns currently represented in the contract map.

Do NOT assume this observation is correct without checking every migration.

But if confirmed:

**FIX THE ROOT CAUSE.**

Do not simply change the report.

Do not simply add a test that accommodates the incomplete behavior.

The actual contract implementation must become correct.

---

# 21. NO DOCUMENTATION-ONLY SUCCESS

A major requirement of this phase:

> Source code > runtime behavior > tests > documentation.

If the documentation says PASS but source is unsafe:

* fix source
* update tests
* update documentation

If a test says PASS but the test does not actually cover the vulnerability:

* improve the test
* rerun it

If the UI audit says PASS but actual screen code is inconsistent:

* fix the UI
* test again

---

# 22. FINAL RESPONSE FORMAT

At the end provide:

1. Current HEAD SHA
2. Files changed
3. Security fixes
4. Migration contract fixes
5. Installer fixes
6. Android UI/UX fixes
7. Localization fixes
8. Business logic fixes
9. Offline/sync fixes
10. Laravel test result
11. Android test result
12. Remaining findings
13. Final production gate: PASS / PASS WITH NON-BLOCKING FINDINGS / BLOCKED

Do not claim production-ready merely because all existing tests pass.

The goal of Phase 6 is to **discover whether the existing tests and implementation are actually strong enough**, then fix anything that is not.
