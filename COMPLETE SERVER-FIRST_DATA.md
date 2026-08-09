# SAFA — COMPLETE SERVER-FIRST DATA ARCHITECTURE MIGRATION & MULTI-ACCOUNT AUTHORIZATION FIX

You must now perform a complete forensic code audit and implementation for the SAFA repository.

Repository:

https://github.com/masarax/safa.git

The current Android application launches successfully, but the application's business data is still effectively operating from the local Room/SQLCipher database.

This is NOT acceptable.

The required architecture is:

```text
Laravel/cPanel MySQL = SINGLE SOURCE OF TRUTH

Android Room/SQLCipher = CACHE / SESSION / SECURITY STORAGE ONLY
```

Do NOT treat Room/SQLCipher as the authoritative business database.

---

# 1. PRIMARY REQUIREMENT — SERVER MUST BE THE SOURCE OF TRUTH

All business entities must be persisted directly to the Laravel production backend:

```text
https://safa.masarax.com
```

The production MySQL database on cPanel must become authoritative for:

* Users
* Accounts
* Customers
* Suppliers / Vendors
* Supplier Deposits / Buy
* Wallet Ledger
* Wallet Batches
* Remittance Transactions
* Expenses
* Income
* Account balances
* Financial statistics
* Rates
* Permissions
* Account access
* Any other business entity

The Android application must NOT behave as though locally stored Room records are authoritative.

---

# 2. REMOVE THE CURRENT LOCAL-FIRST BUSINESS DATA ARCHITECTURE

Audit all of:

* AppRepository
* all DAO classes
* Models.kt
* SafaViewModel
* SyncManager
* TokenManager
* RetrofitClient
* ApiSecurityInterceptor
* all customer flows
* supplier flows
* transaction flows
* wallet flows
* expense/income flows
* dashboard/statistics flows
* account flows
* user flows

Find every place where the application:

```text
UI → Room → UI
```

without requiring a successful server operation.

Replace that behavior with:

```text
UI
 ↓
ViewModel
 ↓
Repository
 ↓
Laravel API
 ↓
MySQL
 ↓
API response
 ↓
ViewModel
 ↓
UI
```

---

# 3. CREATE A CLEAR DATA OWNERSHIP RULE

Implement this architectural rule throughout the project:

```text
SERVER DATA:
Laravel/MySQL is authoritative.

LOCAL DATA:
Room/SQLCipher may only be used for:

- authentication/session persistence
- encrypted credentials/tokens
- device security information
- optional read cache
- temporary offline queue ONLY if explicitly required
- UI performance cache

Room must NEVER silently become the permanent source of business truth.
```

If offline queueing remains implemented, it must be clearly separated from authoritative data.

Every queued mutation must eventually receive a definitive server response.

---

# 4. CRUD OPERATIONS MUST BE SERVER-FIRST

For every business operation:

## Customer

Create:

```text
Android
 ↓
POST /api/customers
 ↓
Laravel validation
 ↓
authorization
 ↓
MySQL INSERT
 ↓
JSON response
 ↓
Android UI
```

Update:

```text
PUT/PATCH /api/customers/{id}
 ↓
MySQL UPDATE
 ↓
response
```

Delete:

```text
DELETE /api/customers/{id}
 ↓
MySQL DELETE
 ↓
response
```

The same pattern must be implemented for:

* suppliers
* transactions
* deposits
* wallets
* expenses
* income
* accounts
* any other business entity.

Do NOT create a local Room record and then pretend the operation succeeded before the server confirms it.

---

# 5. IMPORTANT — DELETE/EXTERNAL SERVER CONSISTENCY

This exact scenario currently fails:

```text
User exists in Room
User is deleted from cPanel MySQL
Android still shows the user
```

This must be fixed.

After authentication and initial application startup, the app must obtain authoritative server state.

If an entity no longer exists on the server:

```text
HTTP 404 / missing entity
        ↓
remove stale local cache
        ↓
remove from UI
```

The application must NEVER display deleted server records merely because they remain in Room.

---

# 6. SERVER DATA REFRESH

Implement a proper server reconciliation mechanism.

At minimum:

```text
LOGIN
 ↓
FETCH CURRENT USER
 ↓
FETCH AUTHORIZED ACCOUNTS
 ↓
FETCH CURRENT ACCOUNT DATA
 ↓
FETCH CUSTOMERS
 ↓
FETCH SUPPLIERS
 ↓
FETCH TRANSACTIONS
 ↓
FETCH WALLET DATA
 ↓
FETCH EXPENSE/INCOME
 ↓
FETCH RATES
 ↓
render UI
```

For normal navigation:

```text
screen opened
 ↓
request authoritative server data
 ↓
render server response
```

Do not blindly trust Room.

---

# 7. MULTI-ACCOUNT SECURITY MODEL

This is critical.

A user has exactly ONE primary identity/login.

Example:

```text
User ID: 25
```

That is the authenticated identity.

The user may have access to:

```text
Account 1 → OWNER
Account 7 → MEMBER
Account 12 → MANAGER
```

But the user must NOT automatically have access to every account in the database.

---

# 8. ACCOUNT ACCESS RULE

Every protected API request must establish:

```text
authenticated_user_id
requested_account_id
```

Then backend authorization must check:

```text
Is authenticated user the owner of requested account?
        ↓
YES → ALLOW

NO
 ↓
Does explicit account permission/access exist?
        ↓
YES → ALLOW

NO
 ↓
403 FORBIDDEN
```

Never use:

```php
Account::first()
Account::all()
User::all()
```

as authorization fallbacks.

Never infer permission merely because the account exists.

---

# 9. BACKEND AUTHORIZATION MUST BE AUTHORITATIVE

Do NOT rely on Android code for account security.

Even if Android hides accounts from the UI, a malicious client can manually call:

```text
GET /api/accounts/other-account-id
```

Therefore Laravel must reject unauthorized requests.

Implement proper backend authorization through middleware/policies/services.

Recommended architecture:

```text
AuthenticatedUser
        ↓
AccountAccessService
        ↓
AccountPolicy / authorization
        ↓
Controller
```

Every business query must be scoped to authorized accounts.

---

# 10. PREVENT IDOR / HORIZONTAL PRIVILEGE ESCALATION

Audit every endpoint for insecure patterns such as:

```php
Customer::find($id)
Supplier::find($id)
Transaction::find($id)
Account::find($id)
User::find($id)
```

without authorization.

Replace with account/user scoped queries.

For example conceptually:

```php
$customer = Customer::query()
    ->where('account_id', $accountId)
    ->whereKey($customerId)
    ->firstOrFail();
```

But only after verifying:

```text
authenticated user → authorized for account
```

Apply this consistently to every entity.

---

# 11. LOGIN ARCHITECTURE

There must be exactly one authenticated primary user identity.

Login response should establish:

```json
{
    "user": {
        "id": 25,
        "name": "...",
        "email": "..."
    },
    "primary_account": {...},
    "authorized_accounts": [...]
}
```

The Android application must NOT download all users and allow the user to select another user identity.

The authenticated user remains fixed.

Account switching is NOT user switching.

It is:

```text
same user
+
different authorized account
```

---

# 12. ACCOUNT SWITCHING

Implement:

```text
Current User
    ↓
Authorized Accounts
    ↓
Select Account
    ↓
Server verifies access
    ↓
Set active_account_id
    ↓
Reload account-scoped data
```

Changing account must NOT change:

```text
authenticated_user_id
```

It only changes:

```text
active_account_id
```

Every subsequent API request must carry the active account context.

Example:

```http
X-SAFA-ACCOUNT-ID: 7
```

or another secure server-supported mechanism.

Do not trust this header by itself.

Laravel must verify it against the authenticated user's permissions.

---

# 13. USER DELETION / INVALID SESSION

If the authenticated user has been deleted or disabled from cPanel:

```text
API request
 ↓
401 / 403
 ↓
clear authenticated session
 ↓
clear sensitive local cache
 ↓
show login screen
```

The application must NOT continue operating from stale Room data.

This is extremely important.

---

# 14. ACCOUNT DELETION / ACCESS REVOCATION

If account access is removed from the backend:

```text
server
 ↓
permission removed
 ↓
Android requests data
 ↓
403
 ↓
remove account from authorized account list
 ↓
if active account:
    switch to another authorized account
    OR
    require user to select an authorized account
```

Never allow stale local authorization to keep access alive.

---

# 15. API CONTRACT AUDIT

Audit all existing routes.

Determine exactly which endpoints currently exist.

Create a complete matrix:

| Entity      | GET | POST | PUT/PATCH | DELETE | Auth | Account Scope |
| ----------- | --- | ---- | --------- | ------ | ---- | ------------- |
| User        |     |      |           |        |      |               |
| Account     |     |      |           |        |      |               |
| Customer    |     |      |           |        |      |               |
| Supplier    |     |      |           |        |      |               |
| Deposit     |     |      |           |        |      |               |
| Transaction |     |      |           |        |      |               |
| Wallet      |     |      |           |        |      |               |
| Expense     |     |      |           |        |      |               |
| Income      |     |      |           |        |      |               |
| Rates       |     |      |           |        |      |               |

Do not invent endpoints blindly.

Inspect the actual Laravel routes/controllers/models/migrations first.

If an endpoint is missing, implement it properly.

---

# 16. SYNC MANAGER REFACTOR

The current SyncManager was designed around:

```text
Room → sync → server
```

Do not simply patch the existing sync system.

Determine whether it is still necessary.

Preferred architecture:

```text
ONLINE:

UI → API → Server → Response → UI

OFFLINE (only if explicitly supported):

UI → encrypted pending queue
          ↓
       connection restored
          ↓
       API → Server
          ↓
       success
          ↓
       remove queue item
```

Never:

```text
Room = permanent master database
```

---

# 17. NO FAKE SUCCESS

This is mandatory.

If:

```text
POST /customers
```

fails:

```text
401
403
422
500
timeout
network error
```

the UI must NOT display:

```text
Customer created successfully
```

The local database must NOT mark it as permanently created.

Show an appropriate failure state.

---

# 18. SERVER RESPONSE MUST DETERMINE SUCCESS

For every mutation:

```text
HTTP 2xx + valid response
        ↓
SUCCESS

anything else
        ↓
FAILURE
```

Only after successful server response may local cache be updated.

---

# 19. CACHE INVALIDATION

If Room remains enabled, implement explicit cache semantics.

Example:

```text
Server response:
customer updated

        ↓

update Room cache
        ↓

UI observes cache
```

But never:

```text
Room changed
 ↓
assume server changed
```

The direction must be:

```text
SERVER → CACHE → UI
```

not:

```text
CACHE → UI → assumed truth
```

---

# 20. INITIAL APP LOAD

On startup after authentication:

```text
1. Validate stored authentication token with server.
2. Fetch current authenticated user.
3. Fetch authorized accounts.
4. Validate active account.
5. Fetch current account data.
6. Refresh all required business entities.
7. Populate/update local cache.
8. Render dashboard.
```

If server authentication fails:

```text
DO NOT render stale authenticated business data.
```

---

# 21. REMOVE ALL GLOBAL DATA LEAKS

Search the complete repository for:

```text
User.all()
User::all()
Account::all()
Customer::all()
Supplier::all()
Transaction::all()
Account::first()
User::first()
Account::firstOrCreate()
```

and similar unscoped queries.

Every result returned to Android must be:

```text
authenticated-user scoped
+
authorized-account scoped
```

unless the endpoint is intentionally public.

---

# 22. BACKEND DATABASE VERIFICATION

After implementation, create integration tests that prove:

### User isolation

User A cannot access:

```text
User B
User B's accounts
User B's customers
User B's suppliers
User B's transactions
```

### Account permission

User A:

```text
Account A → allowed
Account B → denied
```

After granting permission:

```text
Account B → allowed
```

After revoking permission:

```text
Account B → denied
```

### Server deletion

Delete customer directly from MySQL/backend.

Android refresh must no longer show it.

### Server creation

Create customer through Android.

Verify actual cPanel MySQL row exists.

### Server update

Update customer through Android.

Verify MySQL changed.

### Server delete

Delete customer through Android.

Verify MySQL row is deleted/soft-deleted according to existing application design.

Repeat for suppliers and transactions.

---

# 23. IMPORTANT — DO NOT DESTROY EXISTING DATA

Do NOT:

* wipe production MySQL
* reset database
* truncate tables
* remove existing users
* remove existing accounts
* generate fake production records
* change production credentials
* expose API secrets in source code

Preserve all existing production data.

---

# 24. SECURITY — API CREDENTIALS

The previous implementation reportedly introduced production API keys into Android TokenManager defaults.

Do NOT permanently embed sensitive production secrets inside the APK.

An APK can be reverse engineered.

Design the authentication/security model so that the client uses an authenticated user/session/token and the server remains authoritative.

If the current HMAC mechanism is required for request integrity, keep it only if its security model is valid, but do not treat an APK-embedded secret as a true server secret.

Review this architecture before finalizing.

---

# 25. REAL-TIME REQUIREMENT

The user wants the app to work directly against the backend.

Implement server-first data access.

For "real-time" behavior, first implement reliable immediate server synchronization.

If true push real-time updates are required and the current infrastructure supports it, evaluate:

```text
Laravel broadcasting / WebSockets
```

or an appropriate mechanism.

Do NOT claim "real-time" merely because Room is updating locally.

---

# 26. OFFLINE MODE

Do NOT silently retain the current offline-first behavior.

The requested priority is:

```text
ONLINE SERVER-FIRST
```

If offline support is retained:

```text
offline = explicitly indicated
```

and the UI must clearly distinguish:

```text
SERVER-SYNCED
PENDING
OFFLINE
SYNC FAILED
```

Never make offline data appear identical to confirmed server data.

---

# 27. REQUIRED FORENSIC REPORT

After implementation, generate:

```text
SERVER_FIRST_ARCHITECTURE_AUDIT.md
MULTI_ACCOUNT_AUTHORIZATION_AUDIT.md
API_DATA_FLOW_AUDIT.md
FINAL_GO_LIVE_AUDIT.md
```

The report must contain:

1. Current architecture before changes.
2. Root causes.
3. Files changed.
4. Every endpoint audited.
5. Authentication flow.
6. Account authorization flow.
7. Entity ownership/scoping.
8. Local cache behavior.
9. Server-first data flow.
10. Error handling.
11. Security findings.
12. Test results.
13. Production verification results.
14. Remaining blockers.

---

# 28. REQUIRED TESTS

Do not only run unit tests.

Add/run:

```text
Laravel feature tests
API authorization tests
Account permission tests
CRUD API tests
Android repository/API tests
```

Minimum scenarios:

```text
Login
Current user
Authorized accounts
Unauthorized account
Account switch
Customer create
Customer read
Customer update
Customer delete
Supplier create
Supplier read
Supplier update
Supplier delete
Transaction create
Transaction read
Transaction update
Transaction delete
User deletion
Account access revocation
Server-side deletion reconciliation
401 handling
403 handling
422 handling
500 handling
network failure
```

---

# 29. FINAL ACCEPTANCE CRITERIA

The task is NOT complete merely because:

```text
33/33 Laravel tests pass
27/27 Android tests pass
APK builds
```

It is complete only when all of these are true:

```text
[ ] Android login uses server authentication
[ ] One login ID represents exactly one authenticated user
[ ] User cannot access another user
[ ] Account switching only works for explicitly authorized accounts
[ ] Backend enforces account authorization
[ ] Customer data is stored in production MySQL
[ ] Supplier data is stored in production MySQL
[ ] Transaction data is stored in production MySQL
[ ] Wallet data is stored in production MySQL
[ ] Expense/income data is stored in production MySQL
[ ] Server deletion is reflected in Android
[ ] Server update is reflected in Android
[ ] Android mutations are confirmed by server before success
[ ] Room is not business-data source of truth
[ ] No fake local success state
[ ] 401/403 force proper authorization handling
[ ] Existing production data is preserved
[ ] API security is not dependent on hardcoded APK secrets
[ ] Automated authorization tests pass
[ ] Physical device API verification passes
[ ] cPanel MySQL verification confirms actual records
```

---

# 30. IMPORTANT IMPLEMENTATION RULE

Do not make another superficial patch.

Do not simply add another `syncAll()` call.

Do not simply change API keys.

Do not simply refresh Room.

First trace the complete real data path:

```text
Login
→ Authentication
→ Current User
→ Account Authorization
→ Account Selection
→ Customer CRUD
→ Supplier CRUD
→ Transaction CRUD
→ Server Response
→ Local Cache
→ UI
```

Find exactly where the application is currently bypassing the Laravel backend and reading/writing Room as the authoritative source.

Then refactor the architecture.

At the end, provide a forensic report with:

```text
ROOT CAUSE
FILES CHANGED
API ENDPOINTS
AUTHORIZATION MODEL
DATA FLOW
TEST RESULTS
PRODUCTION MYSQL VERIFICATION
PHYSICAL DEVICE VERIFICATION
REMAINING BLOCKERS
```

Do not declare:

```text
READY FOR GO LIVE
```

unless actual Android → Laravel → cPanel MySQL operations have been physically verified.
