SAFA — FINAL RELEASE CANDIDATE / PRODUCTION GATE VERIFICATION

Do NOT start a new feature phase.
Do NOT redesign the application.
Do NOT make unnecessary UI or architectural changes.

The objective of this task is ONLY:

1. Independently verify the current repository at HEAD.
2. Find any remaining production-blocking issue.
3. Fix only confirmed issues at the root cause.
4. Re-run all relevant tests after every fix.
5. Perform a final release-candidate verification.
6. Do NOT declare production-ready based only on previous Phase 5/6 reports.
7. Treat all previous reports as claims that must be independently verified.

IMPORTANT:
The previous Phase 6 report claimed:

- Laravel: 31/31 tests passed
- Android: 27/27 tests passed
- Migration contract: 10/10 complete
- Update token replay protection
- Session spoofing protection
- Offline sync
- Financial calculation
- UI/localization
- Production Gate: APPROVED

You must now independently verify these claims against the actual current source code and actual executable build/test results.

==================================================
A. REPOSITORY BASELINE
==================================================

1. Confirm:
   - current branch
   - current HEAD SHA
   - working tree status
   - uncommitted changes
   - actual Laravel version
   - actual PHP version
   - Android/Gradle configuration

2. Do NOT trust the reported SHA or previous reports.
3. Record the actual values.

==================================================
B. PRODUCTION CREDENTIAL & SECRET AUDIT — CRITICAL
==================================================

Perform a complete repository-wide audit for:

- hardcoded passwords
- default PINs
- API secrets
- API keys
- DB credentials
- encryption keys
- JWT secrets
- session secrets
- signing credentials
- production tokens
- test credentials accidentally used by production seeders
- credentials exposed in documentation

Pay special attention to the previously documented:

Mobile: 0536308965
PIN: 123456

Determine whether this is:
1. documentation-only,
2. test-only,
3. development-only,
4. or an actual production/default seeded credential.

If 123456 is actually a default production SuperAdmin credential, this MUST be treated as a production security issue.

Fix it safely without breaking the installation flow.

Production must NOT depend on a publicly documented static administrator PIN.

Do not expose any real secret in your final report.
Redact secrets.

==================================================
C. RELEASE APK VERIFICATION — CRITICAL
==================================================

Do not stop at:

.\gradlew test

Build the actual release artifact.

Run the appropriate release build, for example:

.\gradlew assembleRelease

or the repository's actual configured release task.

Verify:

- release APK builds successfully
- no compilation errors
- no resource errors
- signing configuration
- release manifest
- application ID
- versionName
- versionCode
- minSdk / targetSdk
- INTERNET permission
- network configuration
- API base URL configuration
- release-specific environment configuration
- R8/minification if enabled
- resource shrinking if enabled
- Room database initialization
- WorkManager initialization

If a release APK is generated, inspect it as a release artifact rather than assuming the debug/test configuration represents production.

If possible, install/run the release APK and verify the critical startup/login path.

==================================================
D. REAL BACKEND PRODUCTION CONFIGURATION AUDIT
==================================================

Audit the Laravel backend for production deployment safety:

- APP_ENV
- APP_DEBUG
- APP_KEY
- DB configuration
- SESSION configuration
- CACHE configuration
- QUEUE configuration
- filesystem/storage configuration
- CORS
- HTTPS assumptions
- CSRF
- authentication
- authorization
- rate limiting
- error handling
- logging
- sensitive error disclosure

Ensure:

APP_DEBUG must NOT be enabled in production.

Do not print secrets in reports.

==================================================
E. INSTALLER / CPANEL RELEASE VERIFICATION
==================================================

Independently verify the installer flow:

1. Fresh installation
2. Existing legacy database
3. Existing complete schema
4. Existing partial schema
5. Missing columns
6. Pending migrations
7. No pending migrations
8. Repeated update
9. Existing production data preservation
10. Migration failure handling
11. Invalid DB credentials
12. Cache clearing
13. storage permissions
14. .env creation/update

Verify the actual code in:

InstallerController.php
routes/web.php
install.blade.php
install_update.blade.php
install_success.blade.php

Do not rely only on previous Phase 6 tests.

==================================================
F. MIGRATION CONTRACT ADVERSARIAL TEST
==================================================

Re-check ALL 10 migrations against the actual migration files.

Do not manually assume the contract map is correct.

Programmatically or systematically compare:

Migration file
        VS
autoHealExistingSchema() contract map

Verify:

- every table
- every required column
- renamed columns
- nullable requirements where relevant
- important indexes/unique constraints where relevant
- foreign keys where relevant
- data types where relevant

The goal is to ensure auto-healing NEVER falsely marks a migration as completed when a required schema element is missing.

Also test:

- complete schema
- one missing column
- multiple missing columns
- partial table
- existing data
- repeated execution
- broken DB connection

==================================================
G. INSTALLER SECURITY ADVERSARIAL TEST
==================================================

Re-test:

1. GET /update-db
   Expected: 405

2. POST /update-db without secret
   Expected: 403

3. POST /update-db with wrong secret
   Expected: 403

4. POST /update-db with correct secret
   Expected: authorized

5. /install/update-process without authorization
   Expected: 403

6. fake session(['user_id' => 999])
   Expected: 403

7. valid update token
   Expected: success

8. replay same update token
   Expected: 403

9. malformed token
   Expected: 403

10. expired/invalid session
   Expected: 403

11. privilege escalation attempts
   Expected: rejected

Check for timing-safe secret comparison where appropriate.

==================================================
H. FINANCIAL BUSINESS LOGIC ADVERSARIAL TEST
==================================================

Do not test only normal examples.

Verify:

SAR × customer rate
SAR × supplier rate
profit calculation
wallet balance
supplier balance
customer balance

Test edge cases:

- 0 SAR
- 0 BDT
- decimal SAR
- 4-decimal exchange rate
- very large values
- customer rate = supplier rate
- customer rate < supplier rate
- negative/invalid amounts
- partial payment
- full payment
- overpayment
- insufficient wallet balance

==================================================
I. WALLET FIFO + ATOMICITY TEST — CRITICAL
==================================================

Verify FIFO depletion with:

1 batch
2 batches
3+ batches
exact batch boundary
amount smaller than batch
amount equal to batch
amount greater than first batch
amount greater than multiple batches
insufficient total balance

Most importantly verify database atomicity:

Transaction starts
→ wallet deduction
→ another operation fails
→ entire operation rolls back

There must be no situation where:

transaction fails BUT wallet money is deducted

or:

wallet deduction fails BUT transaction remains recorded

Use database transactions/rollback verification where appropriate.

==================================================
J. OFFLINE SYNC ADVERSARIAL TEST
==================================================

Verify:

offline create
offline update
offline delete
retry
duplicate retry
network interruption
server timeout
partial response
server duplicate request
local_id mapping
account_id isolation
deleted_at propagation
LWW conflict

Test clock-skew scenarios.

Example:

Device A timestamp = 1000
Device B timestamp = 900

and reverse.

Determine whether client timestamps can incorrectly overwrite newer financial data because of device clock manipulation.

Do not blindly accept LWW for financial records if the implementation creates a correctness problem.

If the existing architecture intentionally uses LWW, document the limitation clearly.

==================================================
K. AUTHENTICATION & AUTHORIZATION AUDIT
==================================================

Audit:

- login
- PIN verification
- session creation
- token creation
- refresh token
- device binding
- device UUID
- fingerprint hash
- logout
- session invalidation
- unauthorized API calls
- role checks
- SuperAdmin access
- operator access
- account sharing permissions

Test horizontal privilege escalation:

User A must NOT access User B's:

- customers
- suppliers
- transactions
- wallet
- expenses
- reports
- account data

Test vertical privilege escalation:

Operator must NOT gain SuperAdmin functionality.

==================================================
L. API SECURITY AUDIT
==================================================

Inspect all API routes.

Look for:

- missing authentication middleware
- missing authorization
- IDOR
- account_id manipulation
- local_id collisions
- mass assignment
- unsafe request validation
- sensitive fields exposed
- debug responses
- SQL injection risks
- unsafe file upload
- unrestricted logo upload
- path traversal
- arbitrary file access

Do not perform destructive attacks against external systems.
Use local/test environment only.

==================================================
M. UI / LOCALIZATION FINAL CHECK
==================================================

Verify:

- no fake customer data
- no hardcoded exchange rate
- no compound bilingual labels
- Bengali mode = Bengali UI
- English mode = English UI
- SAFA branding
- dark/light theme
- empty states
- loading states
- error states
- offline states
- sync failure states

Do not claim AAA contrast unless an actual contrast measurement/verifiable test exists.

==================================================
N. ANDROID RELEASE BEHAVIOR
==================================================

Verify critical user flows:

Launch
→ Login
→ Dashboard
→ Customer
→ Supplier
→ Transaction
→ Wallet
→ Expense
→ Settings
→ Logout

Verify:

- online mode
- offline mode
- sync
- retry
- app restart
- session persistence
- database persistence

Pay special attention to destructive actions and financial mutations.

==================================================
O. TEST SUITE
==================================================

Run the complete existing test suites:

Laravel:
php artisan test

Android:
.\gradlew test --continue

Then run the actual release build.

If new tests are required to prove a discovered issue, add them.

Do NOT create fake tests whose only purpose is to make the report pass.

Every new test must test an actual production requirement or discovered vulnerability.

==================================================
P. FINAL VERDICT RULES
==================================================

You are NOT allowed to say:

"APPROVED FOR PRODUCTION"

just because all existing tests pass.

Use one of these exact verdicts:

1. PRODUCTION READY
   Only if all critical release gates pass.

2. CONDITIONALLY READY
   If only documented non-critical issues remain.

3. NOT READY FOR PRODUCTION
   If any security, financial integrity, authentication, migration/data-loss, or release-build blocker remains.

==================================================
Q. REQUIRED FINAL REPORT
==================================================

Create:

final_release_candidate_audit.md

Include:

1. Actual HEAD SHA
2. Working tree status
3. Environment versions
4. Security findings
5. Credential audit
6. Installer audit
7. Migration audit
8. Financial logic audit
9. Wallet FIFO audit
10. Atomicity/rollback audit
11. Offline sync audit
12. Authentication/authorization audit
13. API security audit
14. UI/localization audit
15. Release APK build result
16. Laravel test result
17. Android test result
18. Any newly added tests
19. Any fixes made
20. Remaining risks
21. Exact final verdict

For every finding use:

STATUS:
PASS / FAIL / WARNING / NOT VERIFIED

EVIDENCE:
Exact file/test/build evidence.

Do not make unsupported claims.

==================================================
FINAL INSTRUCTION
==================================================

This is the FINAL RELEASE CANDIDATE verification.

Do not expand project scope.

Do not add new features.

Do not rewrite working architecture without evidence.

Find real remaining problems, fix confirmed blockers, test the fixes, build the release artifact, and only then provide the final production verdict.

If everything genuinely passes, state:

FINAL VERDICT: PRODUCTION READY

If anything critical remains, state:

FINAL VERDICT: NOT READY FOR PRODUCTION

Do not hide or downgrade critical findings merely to obtain a PASS.