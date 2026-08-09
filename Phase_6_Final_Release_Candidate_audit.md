Phase 6/Final Release Candidate audit দেখে আমি চাই তুমি এখন আর কোনো নতুন feature বা unnecessary refactor করবে না।

এখন একটি FINAL PRE-DEPLOYMENT / PRODUCTION GO-LIVE VERIFICATION করো।

বর্তমান HEAD:
e02c4cc2c861a5195a91bc67aa103af1ab662b81

আগের audit-এর PASS রিপোর্টকে ধরে নিয়ে শুধু report-এর ওপর নির্ভর করবে না। Live source/config/build artifact দেখে যেখানে সম্ভব empirically verify করবে।

এই final verification-এর লক্ষ্য হলো:
“Production Ready” কথাটি বাস্তবে deployment-এর জন্য নিরাপদ কি না নিশ্চিত করা।

নিচের বিষয়গুলো একে একে verify করো:

1. SECRET / CREDENTIAL SAFETY
- Repository/Git history/current tracked files-এ কোনো real password, PIN, API secret, DB password, private key, signing secret বা production credential accidentally committed আছে কি না scan করো।
- INITIAL_SUPERADMIN_PIN
- INITIAL_SUPERADMIN_MOBILE
- INITIAL_SUPERADMIN_EMAIL
- DB_UPDATE_SECRET
- API secrets
- Android signing configuration
এসবের কোনো unsafe fallback আছে কি না verify করো।
- `.env` এবং `.env.example`-এর পার্থক্য verify করো।
- Production `.env` accidentally tracked হচ্ছে কি না verify করো।
- যদি কোনো real credential পাওয়া যায়, value report-এ expose করবে না; শুধু file/location ও severity বলবে।

2. DATABASE / MIGRATION SAFETY
- Fresh database-এ full migration কাজ করে কি না verify করো।
- Existing legacy cPanel-style database-এ auto-healing কাজ করে কি না verify করো।
- Partial schema হলে migration incorrectly marked as completed হচ্ছে কি না verify করো।
- Existing financial/customer/wallet data preservation verify করো।
- Migration rollback/failed migration হলে data corruption risk আছে কি না inspect করো।
- `migrations` table registration logic সত্যিই safe কি না source-level verify করো।

3. FINANCIAL DATA INTEGRITY
বিশেষভাবে verify করো:
- Customer rate
- Supplier rate
- Profit
- SAR/BDT conversion
- Wallet batch FIFO
- Supplier deposit
- Expenses/incomes
- Partial/complete transaction states
- Offline sync
- Duplicate retry
- Failed sync rollback

একই operation duplicate হলে balance/profit যেন double-count না হয়।
Concurrent requests বা retry হলে wallet balance negative বা inconsistent হতে পারে কি না inspect/test করো।

4. OFFLINE SYNC ADVERSARIAL TEST
কমপক্ষে এই cases verify করো:
- offline create → reconnect → sync
- same local_id submitted twice
- same transaction submitted concurrently
- update vs update conflict
- delete vs update conflict
- deleted_at propagation
- failed sync মাঝপথে rollback
- retry after server timeout

বিশেষভাবে দেখো `(account_id, local_id)` uniqueness এবং transaction atomicity বাস্তবে যথেষ্ট কি না।

5. AUTHENTICATION / AUTHORIZATION
Verify:
- normal user
- operator/admin
- superadmin
- invalid session
- expired token/session
- device binding
- wrong device
- invalid refresh/access token
- logout
- privilege escalation
- direct API endpoint access

কোনো API শুধু frontend UI hide করে protected আছে এমন false security যেন না থাকে।

6. INSTALLER / UPDATE ENDPOINT
Verify:
- `/install`
- `/install/update-view`
- `/install/update-process`
- `/update-db`

সব endpoint-এর intended access control, HTTP method restriction, CSRF/security token, replay protection এবং production exposure verify করো।

বিশেষভাবে check করো:
- installer production environment-এ unnecessarily accessible কি না
- update endpoint public internet থেকে abuse করা সম্ভব কি না
- DB_UPDATE_SECRET leak হলে blast radius কী
- successful update-এর পরে token/session invalidation হচ্ছে কি না

7. FILE UPLOAD SECURITY
RemoteConfigController-এর logo upload আবার inspect করো।

Verify:
- `.php`
- `.phtml`
- `.phar`
- `.php.jpg`
- MIME spoofing
- extension spoofing
- SVG containing script
- base64 malicious payload
- oversized file
- path traversal
- arbitrary filename

এসব দিয়ে executable file বা XSS/RCE risk আছে কি না পরীক্ষা করো।

শুধু extension whitelist দেখে PASS বলবে না।

8. API SECURITY
সব গুরুত্বপূর্ণ API endpoint inspect করে verify করো:
- authentication
- authorization
- account ownership
- user/account sharing permission
- mass assignment
- IDOR
- arbitrary account_id manipulation
- arbitrary user_id manipulation
- rate/amount tampering
- replay attacks

একজন authenticated user অন্য account-এর financial data access/update করতে পারে কি না বিশেষভাবে পরীক্ষা করো।

9. ANDROID RELEASE BUILD
Release APK-এর জন্য verify করো:
- assembleRelease
- R8/minification
- signing configuration
- debuggable=false
- cleartext traffic disabled যেখানে প্রয়োজন
- API base URL production-safe
- no localhost/127.0.0.1/dev endpoint
- no hardcoded secret/token/password
- no debug logging of financial/auth data
- backup/security configuration
- exported activities/services/receivers
- network security configuration

APK-এর final manifest এবং release configuration inspect করো।

10. PRODUCTION CONFIGURATION
Production deployment-এর জন্য একটি exact checklist তৈরি করো:
- PHP version
- required PHP extensions
- Laravel environment
- APP_ENV
- APP_DEBUG
- APP_KEY
- database
- cache/session
- queue যদি ব্যবহৃত হয়
- storage permissions
- public/storage link
- HTTPS
- CORS
- CSRF
- cron/scheduler যদি প্রয়োজন হয়
- Android API URL
- database backup
- migration procedure
- rollback procedure

11. BACKUP / DISASTER RECOVERY
Verify whether production deployment has a safe:
- database backup
- pre-migration backup
- rollback/recovery procedure

বিশেষ করে financial system হওয়ায় deployment-এর আগে database backup ছাড়া migration run করা উচিত কি না evaluate করো।

12. TEST COVERAGE GAP
বর্তমান:
- Laravel: 32/32
- Android: 27/27

PASS count দেখে সন্তুষ্ট হবে না।

Identify করো:
- কোন critical production behavior এখনো automated test দ্বারা covered নয়।
- যদি critical gap থাকে, minimal necessary tests যোগ করো।
- unnecessary tests/features যোগ করবে না।

13. DEVICE CLOCK / LWW RISK
আগের report-এ 2099 device-clock manipulation risk উল্লেখ আছে।

এটা production risk হিসেবে evaluate করো:
- বর্তমান implementation কি malicious future timestamp reject করতে পারে?
- server timestamp authority ব্যবহার করা সম্ভব কি না?
- future timestamp-এর reasonable maximum drift enforce করা উচিত কি না?

যদি fix করা প্রয়োজন হয়, minimal safe fix implement করো এবং test যোগ করো।

14. FINAL RELEASE ARTIFACT
সব verification শেষে:
- git status
- final HEAD SHA
- Laravel tests
- Android tests
- release APK build
- APK size
- APK SHA-256 checksum

record করো।

যদি কোনো source change করো:
- কেন change করলে
- exact files
- security/business reason
- tests
সব report করো।

IMPORTANT:
- কোনো নতুন feature implement করবে না।
- UI redesign করবে না।
- architecture unnecessarily change করবে না।
- existing working behavior ভাঙবে না।
- “PASS” শুধু report পড়ে বলবে না; source/test/runtime evidence দিয়ে বলবে।
- কোনো issue পাওয়া গেলে আগে severity নির্ধারণ করবে:
  P0 = production blocker
  P1 = must fix before production
  P2 = non-blocking
  P3 = improvement
- P0/P1 থাকলে “PRODUCTION READY” বলবে না।
- P2/P3 থাকলে production blocker নয় বলে clearly explain করবে।

সবশেষে একটি নতুন report তৈরি করো:

final_pre_deployment_verification.md

Report structure:

1. Executive Summary
2. Repository & Build Baseline
3. Secret/Credential Audit
4. Database & Migration Verification
5. Financial Integrity Verification
6. Offline Sync Adversarial Verification
7. Authentication & Authorization Verification
8. Installer/Update Security
9. File Upload Security
10. API Security / IDOR Verification
11. Android Release Security
12. Production Configuration Checklist
13. Backup & Recovery Assessment
14. Test Coverage Gaps
15. Device Clock / LWW Assessment
16. Findings by Severity (P0/P1/P2/P3)
17. Exact Changes Made During This Verification
18. Final Test Results
19. Final Release Artifact SHA-256
20. FINAL GO-LIVE VERDICT

Final verdict must be exactly one of:

- PRODUCTION READY — GO LIVE
- PRODUCTION READY WITH NON-BLOCKING RISKS
- NOT PRODUCTION READY — FIX REQUIRED

Do not declare GO LIVE until all P0/P1 issues are resolved and verified.