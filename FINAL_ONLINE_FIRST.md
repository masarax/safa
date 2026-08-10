# SAFA — FINAL ONLINE-FIRST / OFFLINE-QUEUE ARCHITECTURE & USER AUTHORIZATION FIX

এবার আর শুধু audit/report তৈরি করবে না। Repository-এর actual code flow ধরে implementation করতে হবে।

বর্তমান implementation-এ Room/SQLCipher এখনো business data flow-এর প্রধান অংশ হিসেবে ব্যবহৃত হচ্ছে। `SyncManager.syncAll()` প্রথমে `repository.getPendingTransactions()`, `getPendingCustomers()`, `getPendingSuppliers()` ইত্যাদি local Room থেকে সংগ্রহ করে তারপর `/api/sync/up`-এ পাঠাচ্ছে। এটি আমার চাওয়া architecture নয়।

আমার final requirement:

> **INTERNET AVAILABLE = SERVER IS THE SOURCE OF TRUTH AND ALL ONLINE CRUD OPERATIONS HAPPEN DIRECTLY ON SERVER.**
>
> **NO INTERNET = LOCAL ROOM/SQLCIPHER IS USED ONLY AS OFFLINE CACHE + OUTBOX QUEUE.**
>
> **WHEN INTERNET RETURNS = ALL OFFLINE QUEUED MUTATIONS ARE AUTOMATICALLY SENT TO SERVER, SERVER CONFIRMS THEM, THEN LOCAL CACHE IS RECONCILED WITH SERVER.**

---

# 1. FINAL DATA ARCHITECTURE

Architecture হবে:

```text
                         ┌─────────────────────┐
                         │ Laravel / MySQL     │
                         │ Production Server   │
                         │ SINGLE SOURCE       │
                         │ OF TRUTH            │
                         └──────────┬──────────┘
                                    │
                              HTTPS / API
                                    │
                                    ▼
┌───────────────────────────────────────────────────────────────┐
│                    Android Application                        │
│                                                               │
│  Compose UI                                                   │
│       │                                                       │
│       ▼                                                       │
│  SafaViewModel                                                │
│       │                                                       │
│       ▼                                                       │
│  Repository / Data Gateway                                    │
│       │                                                       │
│       ├──────── INTERNET AVAILABLE ────────► SERVER API       │
│       │                                      │                │
│       │                                      ▼                │
│       │                                MySQL mutation         │
│       │                                      │                │
│       │                                      ▼                │
│       │                                Server response        │
│       │                                      │                │
│       │                                      ▼                │
│       │                                Update Room cache      │
│       │                                                       │
│       └──────── INTERNET UNAVAILABLE ─────► Room Cache        │
│                                              + Outbox Queue   │
│                                                               │
└───────────────────────────────────────────────────────────────┘
```

Room/SQLCipher কখনোই online অবস্থায় authoritative business database হবে না।

---

# 2. ONLINE CRUD MUST BE DIRECT SERVER CRUD

Customer, Supplier, Transaction, Supplier Deposit, Wallet, Expense/Income এবং অন্যান্য business data-এর জন্য:

### Online CREATE

```text
User presses Save
        ↓
Check Internet
        ↓
ONLINE
        ↓
POST Laravel API
        ↓
Server validates
        ↓
MySQL INSERT
        ↓
Server returns created record + server_id
        ↓
Update local Room cache
        ↓
UI shows server-confirmed record
```

Local Room-এ আগে save করে তারপর server sync করার current pattern ব্যবহার করা যাবে না।

### Online UPDATE

```text
UI Edit
 ↓
PUT/PATCH server
 ↓
MySQL UPDATE
 ↓
Success response
 ↓
Update local cache
```

### Online DELETE

```text
UI Delete
 ↓
DELETE server
 ↓
MySQL delete/soft-delete
 ↓
Success response
 ↓
Remove/update Room cache
```

### গুরুত্বপূর্ণ

Online অবস্থায় যদি server operation fail করে:

```text
DO NOT show success.
DO NOT mark local record as successfully saved.
DO NOT silently keep stale local data as authoritative.
```

User-কে actual server error দেখাতে হবে।

---

# 3. OFFLINE MODE

Internet না থাকলে:

### CREATE

```text
User creates Customer
 ↓
No Internet
 ↓
Save locally
 ↓
syncStatus = PENDING_CREATE
 ↓
outbox operation created
 ↓
UI can show "Offline / Pending Sync"
```

একই নিয়ম:

* Customer
* Supplier
* Transaction
* Supplier Deposit
* Wallet Ledger
* Wallet Batch
* Expense
* Income
* অন্যান্য business entities

এর জন্য প্রযোজ্য হবে।

### UPDATE

Offline update হলে:

```text
Room cache update
+
outbox PENDING_UPDATE
```

### DELETE

Offline delete হলে:

```text
Room cache marked deleted
+
outbox PENDING_DELETE
```

কিন্তু user-কে এমন impression দেওয়া যাবে না যে server-এ delete হয়ে গেছে।

UI-তে pending state পরিষ্কারভাবে রাখতে হবে।

---

# 4. INTERNET RETURN করলে AUTOMATIC SYNC

Connectivity ফিরে এলে:

```text
Network Available
       ↓
Authenticate / refresh token
       ↓
Process Outbox
       ↓
CREATE / UPDATE / DELETE
       ↓
Server confirms
       ↓
Mark operation SYNCED
       ↓
Refresh authoritative server data
       ↓
Reconcile Room cache
```

WorkManager ব্যবহার করে reliable retry করতে হবে।

Retry হবে:

```text
network failure
408
429
500
502
503
504
```

কিন্তু:

```text
401 / 403
```

হলে blind retry করা যাবে না।

401 হলে token refresh / re-login flow।

403 হলে authorization failure হিসেবে operation pending/error state-এ রাখতে হবে এবং user-কে জানাতে হবে।

400/422 validation failure হলে permanently failed operation হিসেবে mark করতে হবে এবং server-এর validation error দেখাতে হবে।

---

# 5. SYNC OUTBOX ARCHITECTURE

শুধু entity-এর `syncStatus` দিয়ে architecture চালাবে না।

একটি proper Outbox table/entity তৈরি করো:

```text
sync_outbox
------------
id
user_id
account_id
entity_type
entity_local_id
entity_server_id
operation
payload_json
status
retry_count
last_error
created_at
updated_at
```

Operation:

```text
CREATE
UPDATE
DELETE
```

Status:

```text
PENDING
PROCESSING
SYNCED
FAILED
```

প্রতিটি offline mutation একটি durable outbox operation তৈরি করবে।

App বন্ধ হলেও operation হারাবে না।

Phone restart হলেও operation হারাবে না।

Internet ফিরে এলে WorkManager automatically process করবে।

---

# 6. SERVER-FIRST READ FLOW

Internet available থাকলে list/detail screen-এর data server থেকে load হবে।

উদাহরণ:

```text
Open Customers
 ↓
Network available?
 ↓ YES
GET /api/customers
 ↓
Laravel
 ↓
MySQL
 ↓
JSON
 ↓
Replace/reconcile local cache
 ↓
UI
```

Room observer দিয়ে পুরনো data সরাসরি UI-এর authoritative source হিসেবে দেখানো যাবে না।

Offline হলে:

```text
Open Customers
 ↓
No Internet
 ↓
Room cache
 ↓
UI
```

---

# 7. SERVER DELETE MUST REMOVE LOCAL DATA

এটি খুব গুরুত্বপূর্ণ।

যদি cPanel MySQL-এ Customer delete করা হয়:

```text
Server:
Customer 25 = deleted
```

পরবর্তীতে Android online হলে:

```text
GET /api/customers
 ↓
Customer 25 নেই
 ↓
Remove Customer 25 from local Room
```

অর্থাৎ:

> Server-এ নেই কিন্তু local Room-এ আছে — এমন stale business record থাকতে পারবে না যখন device online reconciliation সম্পন্ন হয়েছে।

Soft-delete হলে `deleted_at` অনুযায়ী local record remove/archive করতে হবে।

একই নিয়ম Supplier, Transaction, Expense, Wallet ইত্যাদির ক্ষেত্রেও।

---

# 8. LOGIN MUST BE SERVER-AUTHENTICATED

Login-এর ক্ষেত্রে:

### Internet available

```text
Mobile/PIN
 ↓
Laravel /api/auth/login
 ↓
MySQL users table
 ↓
Validate credentials
 ↓
Return JWT/session/tokens/user
 ↓
Android stores session securely
```

কোনো locally cached user list দিয়ে নতুন login authenticate করা যাবে না।

cPanel database থেকে user delete করলে:

```text
Next online authentication/session validation
 ↓
401
 ↓
Clear invalid session
 ↓
Return to login
```

অর্থাৎ server-এ user নেই অথচ Android local database-এ পুরনো user আছে বলে তাকে valid user হিসেবে দেখানো যাবে না।

### Offline login

Offline mode-এ শুধুমাত্র previously authenticated/current user-এর locally secured session unlock করা যেতে পারে, যদি existing product security model এটি allow করে।

কিন্তু:

> নতুন user login / unknown user login offline থেকে করা যাবে না।

---

# 9. ONE USER = ONE PRIMARY IDENTITY

এটি কঠোরভাবে enforce করতে হবে।

একজন authenticated user-এর:

```text
user_id = ONE PRIMARY IDENTITY
```

Account switch করলে:

```text
user_id stays SAME
account_id changes ONLY if authorized
```

উদাহরণ:

```text
User 25
 ├── Own Account 7
 ├── Shared Account 12
 └── Shared Account 19
```

User 25 login করলে সে:

```text
User 25 identity
```

হিসেবেই থাকবে।

সে User 26 / User 27 / অন্য কোনো user identity-তে ঢুকতে পারবে না।

Account sharing এবং user switching এক জিনিস নয়।

---

# 10. ACCOUNT ACCESS AUTHORIZATION

Account switch endpoint:

```text
POST /api/auth/switch-account
```

Server অবশ্যই verify করবে:

```text
current authenticated user
        │
        ├── owns target account?
        │        YES → allow
        │
        └── explicit UserAccountShare exists?
                 YES → allow
                 NO → 403
```

কখনো:

```php
Account::all()
User::all()
User::first()
```

জাতীয় fallback দিয়ে access অনুমতি দেওয়া যাবে না।

---

# 11. EVERY BUSINESS API MUST BE USER + ACCOUNT SCOPED

প্রতিটি request-এর authoritative context:

```text
JWT user_id
JWT/current account_id
```

Backend query অবশ্যই:

```php
->where('account_id', $authorizedAccountId)
```

এবং user/account authorization verify করার পর হবে।

Client থেকে পাঠানো arbitrary:

```text
X-SAFA-ACCOUNT-ID
```

blindly trust করা যাবে না।

Header-এর account ID শুধু requested context হবে; backend server-side authorization দিয়ে verify করবে।

---

# 12. ADMIN / SUPERADMIN USER MANAGEMENT FIX

বর্তমানে Admin/SuperAdmin user account edit/delete করতে পারছে না।

এটি আলাদা করে complete fix করতে হবে।

বর্তমান `api.php`-তে routes আছে:

```text
GET    /api/auth/operators
POST   /api/auth/operators
PUT    /api/auth/operators/{id}
DELETE /api/auth/operators/{id}
```

কিন্তু route থাকা মানেই functionality সম্পূর্ণ নয়।

Actual `AuthJWTController` implementation এবং Android API/UI flow সম্পূর্ণ audit করো।

---

# 13. USER MANAGEMENT REQUIREMENTS

SuperAdmin:

```text
View users
Create user
Edit user
Change role
Change permissions
Activate/deactivate user
Reset PIN/password
Delete user
Revoke sessions
Revoke devices
```

Admin:

```text
Only permissions explicitly granted by RBAC
```

Staff:

```text
No user-management access unless explicitly granted
```

---

# 14. DELETE USER SAFETY

SuperAdmin নিজেকে delete করতে পারবে না।

অন্য SuperAdmin delete করার permission আলাদা explicit rule ছাড়া দেওয়া যাবে না।

Delete হলে server-side:

```text
users
auth_sessions
device_bindings
user_account_shares
operator_accounts
```

এর সম্পর্কগুলো properly revoke/remove করতে হবে।

Business records automatically delete করা যাবে না শুধু user delete হওয়ার কারণে।

Business data account ownership অনুযায়ী থাকবে।

---

# 15. USER EDIT MUST BE SERVER-FIRST

Admin/SuperAdmin edit:

```text
UI
 ↓
PUT /api/auth/operators/{id}
 ↓
Backend authorization
 ↓
Validation
 ↓
MySQL users UPDATE
 ↓
Return updated user
 ↓
Update Android cache
```

Local-only edit করা যাবে না।

---

# 16. USER DELETE MUST BE SERVER-FIRST

```text
UI Delete
 ↓
DELETE /api/auth/operators/{id}
 ↓
Backend authorization
 ↓
MySQL transaction
 ↓
Revoke sessions/devices
 ↓
Delete/deactivate user
 ↓
200/204
 ↓
Android removes cached user
```

Server failure হলে local user delete as successful দেখানো যাবে না।

---

# 17. USER MANAGEMENT API TESTS

Mandatory tests লিখতে হবে:

```text
SuperAdmin can list operators
SuperAdmin can create operator
SuperAdmin can update operator
SuperAdmin can change permissions
SuperAdmin can deactivate operator
SuperAdmin can delete operator

Unauthorized staff cannot update operator
Unauthorized staff cannot delete operator

User cannot modify another user identity
User cannot impersonate another user

Deleted user cannot login
Deactivated user cannot login
Revoked session cannot access API
```

---

# 18. ONLINE/OFFLINE ACCEPTANCE TESTS

এই test cases অবশ্যই automated এবং physical device-এ verify করতে হবে।

### TEST A — Online Customer Create

```text
Internet ON
Create Customer
```

Expected:

```text
Immediately POST /api/customers
MySQL customer created
Server ID returned
Room updated from server response
```

Room-only success গ্রহণযোগ্য নয়।

---

### TEST B — Online Customer Delete

```text
Internet ON
Delete Customer
```

Expected:

```text
DELETE server
MySQL record deleted/soft-deleted
Local cache updated
```

---

### TEST C — Server-side Delete

cPanel থেকে customer delete:

```text
Customer X deleted in MySQL
```

Then Android:

```text
Internet ON
Refresh
```

Expected:

```text
Customer X disappears
```

---

### TEST D — Offline Create

```text
Internet OFF
Create Customer
```

Expected:

```text
Room record exists
PENDING_CREATE
Outbox exists
Server record does NOT yet exist
```

UI should indicate pending/offline state.

---

### TEST E — Internet Returns

```text
Internet ON
```

Expected:

```text
Outbox automatically uploads
MySQL record created
server_id returned
Outbox SYNCED
Room reconciled
```

---

### TEST F — Offline Update

```text
Internet OFF
Edit Customer
```

Expected:

```text
Local cache updated
PENDING_UPDATE
Outbox created
```

Then internet ON:

```text
PUT server
MySQL updated
outbox synced
```

---

### TEST G — Offline Delete

```text
Internet OFF
Delete Customer
```

Expected:

```text
Local hidden/marked deleted
PENDING_DELETE
```

Internet ON:

```text
DELETE server
confirmed
local cache permanently reconciled
```

---

### TEST H — User Deleted From cPanel

```text
Delete user directly from MySQL
```

Android currently logged in:

```text
next authenticated API request
 ↓
401
 ↓
session invalidated
 ↓
local user cannot continue as authenticated
 ↓
login screen
```

---

# 19. REMOVE FALSE "PASSED" CLAIMS

Do NOT mark the system:

```text
SERVER-FIRST = PASSED
PHYSICAL DEVICE API VERIFICATION = PASSED
CPANEL MYSQL VERIFICATION = PASSED
```

unless the actual physical device has performed the test and the actual production MySQL database has been verified.

Automated tests alone cannot prove production API connectivity.

---

# 20. REQUIRED PRODUCTION LOGGING

For debugging, temporarily add safe diagnostic logs:

```text
ONLINE_REQUEST
OFFLINE_QUEUE
SERVER_RESPONSE
OUTBOX_ENQUEUED
OUTBOX_PROCESSING
OUTBOX_SUCCESS
OUTBOX_FAILED
SERVER_RECONCILIATION
AUTH_SUCCESS
AUTH_401
AUTH_403
ACCOUNT_SWITCH_SUCCESS
ACCOUNT_SWITCH_DENIED
```

NEVER log:

```text
PIN
password
JWT
refresh token
API secret
HMAC secret
full sensitive personal data
```

---

# 21. REQUIRED FINAL ARCHITECTURE

Final system must behave exactly like:

```text
                  INTERNET AVAILABLE
                         │
                         ▼
                ┌─────────────────┐
                │ Laravel API     │
                │ + MySQL         │
                │ AUTHORITATIVE   │
                └────────┬────────┘
                         │
                         ▼
                    Android UI
                         │
                         ▼
                  Room Cache only


                  INTERNET OFF
                         │
                         ▼
                    Android UI
                         │
                         ▼
                  Room Cache
                         │
                         ▼
                    Outbox Queue
                         │
                         │
               Internet Returns
                         │
                         ▼
                  Laravel API
                         │
                         ▼
                    MySQL
                         │
                         ▼
                Server Confirmation
                         │
                         ▼
                  Room Reconcile
```

---

# 22. IMPLEMENTATION RULE

Do not simply modify the audit markdown files.

Actually inspect and modify:

```text
SafaViewModel.kt
AppRepository.kt
SyncManager.kt
ApiService.kt
TokenManager.kt
RetrofitClient.kt
network/interceptors
Room DAOs
Room entities
WorkManager
AuthJWTController.php
SyncController.php
CustomerController.php
SupplierController.php
TransactionController.php
User/operator management APIs
routes/api.php
RBAC/permission logic
Android user-management UI
```

প্রয়োজনে নতুন:

```text
SyncOutbox
ConnectivityMonitor
ServerFirstRepository/DataGateway
UserManagementController
UserManagementService
```

তৈরি করো।

---

# 23. FINAL SUCCESS CONDITION

Implementation complete বলা যাবে শুধুমাত্র যখন:

1. Internet ON অবস্থায় Customer create করলে সরাসরি cPanel MySQL-এ record তৈরি হয়।
2. Internet ON অবস্থায় Customer update/delete সরাসরি server-এ হয়।
3. Internet ON অবস্থায় Customer/Supplier/Transaction list server থেকে আসে।
4. cPanel থেকে record delete করলে Android online refresh-এর পরে record আর দেখায় না।
5. Internet OFF অবস্থায় mutation local outbox-এ queue হয়।
6. Internet ফিরে এলে queue automatically server-এ যায়।
7. Server confirmation ছাড়া online mutation success দেখায় না।
8. Login online হলে অবশ্যই Laravel/MySQL থেকে authenticate হয়।
9. Server থেকে user delete/deactivate করলে Android তাকে valid authenticated user হিসেবে রাখতে পারে না।
10. One user = one primary user_id।
11. Account switching শুধুমাত্র explicit ownership/share permission থাকলে সম্ভব।
12. Unauthorized user অন্য user/account access করতে পারে না।
13. SuperAdmin/Admin permission অনুযায়ী user edit/delete করতে পারে।
14. User edit/delete server-first।
15. 401/403 properly handled।
16. Automated tests pass।
17. Physical device online CRUD test pass।
18. Physical device offline → online sync test pass।
19. Production cPanel MySQL actual records verified।
20. কোন audit file-এ evidence ছাড়া "PASSED" লেখা যাবে না।

সব implementation শেষে:

```text
git diff
git status
php artisan test
.\gradlew test
.\gradlew clean assembleDebug
```

চালিয়ে actual changed files, tests, APK SHA-256 এবং production verification evidence report করো।

**শুধু report লিখে থামবে না। Actual code fix করতে হবে।**
