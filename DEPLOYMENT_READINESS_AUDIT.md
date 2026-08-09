এখন আর নতুন feature বা unnecessary refactor করবে না। SAFA-কে সত্যিকারের production deployment-এর জন্য প্রস্তুত করার উদ্দেশ্যে **FINAL GO-LIVE / DEPLOYMENT READINESS AUDIT** চালাও।

আগের `final_pre_deployment_verification.md` রিপোর্টকে accepted baseline হিসেবে ধরো, কিন্তু রিপোর্টের PASS/PRODUCTION READY দাবিগুলো blind trust করবে না। Live codebase, configuration, deployment flow এবং executable behavior থেকে যতটা সম্ভব আবার verify করবে।

### 1. Production Environment Audit

বিশেষভাবে যাচাই করো:

* `APP_ENV=production`
* `APP_DEBUG=false`
* `APP_KEY` properly configured
* `DB_UPDATE_SECRET` strong এবং production-safe
* `INITIAL_SUPERADMIN_PIN`
* `INITIAL_SUPERADMIN_MOBILE`
* `INITIAL_SUPERADMIN_EMAIL`
* API/security secrets
* কোনো production secret source code / tracked file / migration / seeder / log-এ leak হচ্ছে কিনা
* `.env`, `.env.production`, backup files বা generated config accidentally Git-tracked কিনা
* Laravel config/cache production-safe কিনা
* `storage:link`
* writable directories
* required PHP extensions
* HTTPS enforcement

### 2. Database Deployment Safety

Production-এর existing database ধরে audit করো:

* migrations নতুন server/database-এ cleanly execute করবে কিনা
* existing legacy cPanel database-এ update করলে কোনো table/column/data loss হবে কিনা
* `autoHealExistingSchema()` এবং migration system একে অপরের সাথে conflict করে কিনা
* duplicate table/column creation-এর সম্ভাবনা আছে কিনা
* migration failure হলে rollback/data preservation behavior কী
* production deployment-এর আগে backup বাধ্যতামূলক কিনা
* destructive SQL/query আছে কিনা

কোনো destructive operation থাকলে exact file + line + risk উল্লেখ করবে।

### 3. Authentication / Authorization Deep Audit

সব critical API এবং web/admin route verify করো:

* SuperAdmin
* Manager
* Staff
* account isolation
* horizontal privilege escalation / IDOR
* unauthorized transaction access
* unauthorized customer/supplier/ledger access
* JWT/token validation
* API security key/HMAC validation
* expired/invalid token behavior
* logout/session invalidation
* password/PIN security
* rate limiting যেখানে প্রয়োজন

বিশেষভাবে চেষ্টা করো একজন Account A-এর authenticated user দিয়ে Account B-এর data access করা সম্ভব কিনা।

### 4. Financial Integrity Audit

Production-এর জন্য সবচেয়ে গুরুত্বপূর্ণ অংশ হিসেবে আবার verify করো:

* customer transaction calculation
* supplier calculation
* profit calculation
* partial payment
* due/paid/partial state
* wallet balance
* wallet batch FIFO
* deposit
* withdrawal
* expense
* ledger balance
* duplicate sync
* retry after network failure
* concurrent sync
* failed transaction rollback
* double deduction / double credit
* negative/invalid amount
* decimal/rounding behavior

যেখানে সম্ভব adversarial test লিখে execute করো।

### 5. Offline Sync Deep Audit

বিশেষভাবে test করো:

* duplicate local_id
* retry after timeout
* same record uploaded twice
* concurrent updates
* LWW behavior
* future timestamp
* past timestamp
* invalid timestamp
* deleted record sync
* restore/update after soft delete
* partial sync failure
* transaction rollback
* server/client state divergence

Device clock protection শুধু future timestamp clamp করছে—এতে কোনো legitimate offline transaction-এর timestamp বা conflict resolution ভুলভাবে পরিবর্তিত হচ্ছে কিনা সেটাও verify করো।

### 6. File Upload / RCE / Path Traversal Audit

সব upload endpoint খুঁজে বের করো, শুধু logo upload নয়।

প্রতিটি endpoint-এর জন্য verify করো:

* PHP/PHTML/PHAR upload rejection
* executable file rejection
* MIME spoofing
* extension spoofing
* double extension
* path traversal
* filename injection
* SVG-related XSS risk
* oversized file
* malformed base64
* unauthorized upload
* uploaded file executable হয়ে server-side code execute করতে পারে কিনা

যদি অন্য কোনো unrestricted upload পাওয়া যায়, সেটা P0/P1 হিসেবে fix করো এবং regression test যোগ করো।

### 7. Installer / Update Endpoint Audit

`/install/update-view`, `/update-db` এবং সংশ্লিষ্ট সব deployment/update route আবার verify করো:

* authentication
* authorization
* CSRF
* secret validation
* token entropy
* single-use behavior
* replay protection
* GET/POST restriction
* session spoofing
* brute-force possibility
* error leakage
* production-এ installer accidentally exposed থাকার risk

যদি production deployment-এর পর installer/update endpoint public রাখা নিরাপদ না হয়, তাহলে secure disable/lock strategy implement করো।

### 8. Android Release APK Audit

Release APK-এর জন্য verify করো:

* `assembleRelease`
* R8 enabled
* `debuggable=false`
* HTTPS only
* cleartext disabled
* no debug logging of sensitive information
* no hardcoded production credentials/secrets
* no debug/test server URL
* no test account credentials
* signing configuration
* APK SHA-256
* release APK launches successfully
* critical login/API/sync flow does not crash in release build

যদি possible হয় APK-এর static inspection করে hardcoded secrets এবং debug endpoints search করো।

### 9. Production Deployment Procedure

একটি exact, safe deployment sequence তৈরি করো:

1. backup
2. maintenance/availability strategy
3. upload/deploy code
4. install dependencies
5. environment configuration
6. Laravel cache/config optimization
7. database migration/update
8. storage link
9. permissions
10. queue/cron requirements
11. health check
12. API smoke test
13. Android production API connectivity test
14. rollback procedure

কোনো command বা step uncertain হলে সেটা অনুমান করবে না—exact project configuration দেখে determine করবে।

### 10. Rollback Plan

Production deployment ব্যর্থ হলে exact rollback procedure লিখবে:

* code rollback
* database rollback
* backup restore
* cache clear/rebuild
* storage considerations
* Android API compatibility considerations

বিশেষ করে database migration rollback সম্ভব না হলে সেটা clearly document করবে।

### 11. Final Smoke Test

Deployment-এর আগে production-equivalent environment-এ minimum smoke test চালাও:

* login
* logout
* dashboard
* customer create/update
* supplier create/update
* transaction create
* payment
* wallet/deposit
* expense
* ledger
* sync offline → online
* duplicate sync
* logout/login again
* role restriction
* account isolation

### 12. Final Report

শেষে `FINAL_GO_LIVE_AUDIT.md` তৈরি করো।

Report-এ অবশ্যই থাকবে:

* audit date
* commit SHA
* environment
* exact commands executed
* exact test counts
* vulnerabilities found
* vulnerabilities fixed
* unresolved risks
* deployment prerequisites
* rollback procedure
* release APK path
* APK SHA-256
* final smoke-test results

সবচেয়ে গুরুত্বপূর্ণ:

**“PRODUCTION READY” লিখবে শুধুমাত্র যদি executable evidence দিয়ে সেটা justify করা যায়।**

যদি কোনো কিছু verify করা সম্ভব না হয়, `UNVERIFIED` লিখবে। অনুমান করে PASS দেবে না।

যদি কোনো P0/P1 issue পাওয়া যায়:

1. root cause fix করো
2. regression test লেখো
3. সব tests আবার চালাও
4. release APK আবার build করো
5. নতুন SHA-256 দাও
6. final report update করো

**এই audit-এর সময় নতুন feature যোগ করবে না। Existing behavior অপ্রয়োজনে পরিবর্তন করবে না। Production safety এবং correctness-ই একমাত্র লক্ষ্য।**

শেষে আমাকে পরিষ্কারভাবে তিনটির একটিই verdict দেবে:

* `GO LIVE`
* `GO LIVE WITH CONDITIONS`
* `BLOCKED`

এবং verdict-এর প্রতিটি claim-এর পাশে executable evidence/reference দেবে।
