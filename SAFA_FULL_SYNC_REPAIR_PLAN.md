# SAFA — Complete Offline-to-Cloud Sync Audit & Repair Specification

> **Document Type:** AI Coding Agent Task Specification
> **Project:** SAFA — Safa & Multi-Currency Account Management System
> **Repository:** `masarax/safa`
> **Primary Goal:** Fix and harden the complete Android → Laravel → Production Database synchronization pipeline.

---

# 1. IMPORTANT — READ THIS FIRST

You are an AI coding agent working on the SAFA repository.

Your job is **not** to make assumptions based on this document alone.

Before modifying any code, you MUST inspect the **entire repository structure and all relevant source files** and reconstruct the actual architecture.

The application is intended to work as:

```text
Android App
    ↓
Local Encrypted Room / SQLCipher Database
    ↓
Offline-first Repository
    ↓
Pending / Dirty / Unsynced Records
    ↓
Sync Engine / WorkManager
    ↓
Retrofit / HTTPS
    ↓
Laravel API
    ↓
Authentication / API Security
    ↓
Sync Controller / Services
    ↓
Eloquent Models
    ↓
Production MySQL/PostgreSQL Database
```

The current reported production problem is:

> Data added from the Android application is often stored only in the local Android database and does not reach the production cPanel database.

The user reports that the **user/authentication-related data works**, but other business entities do not reliably reach the server.

This task is to find the **actual root cause from the codebase**, fix it completely, and make synchronization reliable.

---

# 2. DO NOT TRUST THE CURRENT IMPLEMENTATION

The repository contains an offline-first architecture.

The README describes:

* Android Kotlin
* Jetpack Compose
* Room / SQLCipher
* REST API
* HMAC/API security
* Laravel backend
* Sync Engine
* Bidirectional synchronization

However, documentation may not perfectly represent the current implementation.

Therefore:

```text
CODE > README
ACTUAL RUNTIME FLOW > COMMENTS
ACTUAL API ROUTES > ASSUMPTIONS
ACTUAL DATABASE SCHEMA > MODEL ASSUMPTIONS
```

Never assume that a feature works simply because a class or method exists.

---

# 3. FIRST PHASE — COMPLETE REPOSITORY AUDIT

Before making any modification, inspect the complete repository tree.

At minimum inspect:

```text
app/
backend/
database/
routes/
config/
resources/
tests/
.github/
Gradle configuration
Laravel configuration
```

Also inspect all relevant Kotlin packages.

Find every file related to:

```text
API
Retrofit
OkHttp
Interceptors
Authentication
Token
HMAC
API Key
Repository
DAO
Room
SQLCipher
Database
Entity
Model
Sync
Worker
WorkManager
Coroutine
ViewModel
UseCase
Service
Network
Payload
DTO
Mapper
Preferences
DataStore
SharedPreferences
Local storage
Remote configuration
```

On Laravel side inspect:

```text
routes/api.php
routes/web.php

app/Http/Controllers/
app/Http/Middleware/
app/Models/
app/Services/
app/Repositories/
app/Requests/

database/migrations/
database/seeders/

config/database.php
config/*
.env.example
composer.json
```

Also inspect tests.

---

# 4. CREATE A DATA-FLOW MAP

Before editing code, build a complete map of every business entity.

For every entity answer:

```text
Where is it created in UI?
↓
Which ViewModel handles it?
↓
Which Repository handles it?
↓
Which DAO writes it?
↓
Which Room Entity represents it?
↓
How is it marked as pending/dirty/unsynced?
↓
Which Sync Engine finds it?
↓
Which DTO represents it?
↓
Which payload contains it?
↓
Which Retrofit endpoint receives it?
↓
Which interceptor modifies the request?
↓
Which Laravel middleware receives it?
↓
Which controller handles it?
↓
Which service/model writes it?
↓
Which database table stores it?
```

Do this separately for:

* Users
* Customers
* Suppliers
* Transactions
* Wallets
* Wallet Batches
* Wallet Ledgers
* Supplier Deposits
* Expenses
* Incomes
* Any other entity found in the repository

---

# 5. CRITICAL CURRENT FINDING — API ARCHITECTURE MISMATCH

The Android API layer currently contains direct endpoints similar to:

```text
POST /customers
POST /suppliers
POST /deposits
POST /transactions
```

while the Laravel backend currently contains a central synchronization endpoint:

```text
POST /sync/up
GET  /sync/down
```

The backend `sync/up` endpoint handles multiple collections.

This creates a potential architecture mismatch.

You MUST determine whether the application is intended to use:

### Architecture A — Direct CRUD

```text
Android
 ↓
POST /customers
 ↓
Laravel
 ↓
customers table
```

or:

### Architecture B — Offline-first synchronization

```text
Android
 ↓
Room
 ↓
Sync Queue
 ↓
POST /sync/up
 ↓
Laravel
 ↓
Production DB
```

The project should not accidentally use both architectures inconsistently.

---

# 6. REQUIRED ARCHITECTURE DECISION

For the current application, the preferred architecture should be:

```text
LOCAL-FIRST + CENTRAL SYNC
```

Business operations should first be persisted safely to local Room/SQLCipher.

Then records should become:

```text
PENDING_SYNC
```

or equivalent.

A reliable Sync Engine should upload them.

The server becomes the authoritative cloud copy.

---

# 7. LOCAL DATABASE REQUIREMENTS

Inspect every Room Entity.

Each syncable entity must have enough information to identify and synchronize a record.

At minimum, determine whether each entity has equivalents of:

```text
localId
serverId
accountId
createdAt
updatedAt
deletedAt
syncStatus
syncError
lastSyncedAt
```

If the architecture uses different names, preserve the existing naming convention where practical.

Do NOT blindly add duplicate fields.

---

# 8. REQUIRED SYNC STATUS MODEL

The agent should establish a clear state machine.

Recommended:

```text
LOCAL_ONLY
    ↓
PENDING_CREATE
    ↓
SYNCING
    ↓
SYNCED
```

For updates:

```text
SYNCED
    ↓
PENDING_UPDATE
    ↓
SYNCING
    ↓
SYNCED
```

For deletes:

```text
SYNCED
    ↓
PENDING_DELETE
    ↓
SYNCING
    ↓
DELETED / TOMBSTONE
```

On failure:

```text
SYNCING
    ↓
SYNC_FAILED
    ↓
RETRY
```

Do not silently discard failed records.

---

# 9. NEVER LOSE LOCAL DATA

A failed network request must NOT delete the local record.

Example:

```text
Create Customer
↓
Room insert succeeds
↓
Sync starts
↓
Internet unavailable
↓
Upload fails
↓
Customer remains locally
↓
Status = PENDING_CREATE
↓
Retry later
```

Never do:

```text
upload failed
↓
delete local record
```

---

# 10. SYNC WORKER REQUIREMENTS

Inspect existing WorkManager implementation.

If no reliable Worker exists, implement one.

The Worker must:

1. Check network availability.
2. Read pending records.
3. Build a deterministic sync payload.
4. Upload to server.
5. Validate HTTP response.
6. Validate application-level response.
7. Mark successfully uploaded records as synced.
8. Keep failed records pending.
9. Retry transient errors.
10. Avoid infinite rapid retry loops.
11. Support manual sync.
12. Support automatic background sync.

Use appropriate WorkManager constraints.

---

# 11. DO NOT REPORT SUCCESS TOO EARLY

This is critical.

This is NOT enough:

```kotlin
api.syncUp(payload)
```

The implementation must verify:

```text
HTTP status
+
response body
+
server acknowledgement
```

Only then mark local records as synchronized.

---

# 12. SERVER RESPONSE CONTRACT

Create a clear sync response contract.

Recommended structure:

```json
{
  "success": true,
  "message": "Sync completed",
  "server_time": 1234567890,
  "accepted": {
    "customers": [1, 2],
    "suppliers": [3],
    "transactions": [4]
  },
  "rejected": [],
  "conflicts": []
}
```

The exact implementation can differ, but the response MUST clearly identify:

```text
accepted
rejected
conflicted
```

records.

---

# 13. SYNC FAILURE MUST BE VISIBLE

Never silently swallow exceptions.

Bad:

```kotlin
try {
    sync()
} catch (e: Exception) {
}
```

Good:

```text
Log error
+
Persist sync error
+
Keep record pending
+
Retry
+
Expose meaningful sync state to UI
```

Do not expose sensitive secrets in logs.

---

# 14. RETROFIT BASE URL AUDIT

Inspect:

```text
SAFA_BASE_URL
RetrofitClient
BuildConfig
Secrets Gradle Plugin
.env
.env.example
release configuration
debug configuration
remote configuration
```

The production application MUST NOT accidentally use:

```text
10.0.2.2
localhost
127.0.0.1
192.168.x.x
10.x.x.x
```

unless explicitly intended.

Development:

```text
http://10.0.2.2:8000/api/
```

Production:

```text
https://YOUR-PRODUCTION-DOMAIN/api/
```

Use the actual production URL already configured in the project. Do not invent one.

---

# 15. URL NORMALIZATION

Ensure the final URL behaves correctly:

```text
https://example.com/api/
```

and Retrofit paths:

```text
sync/up
sync/down
```

must produce:

```text
https://example.com/api/sync/up
```

Do not accidentally produce:

```text
https://example.com/sync/up
```

or:

```text
https://example.com/api/api/sync/up
```

---

# 16. API SECURITY AUDIT

Inspect:

```text
ApiSecurityInterceptor
CheckApiSecurityKey
HMAC generation
API key handling
API secret handling
JWT
token manager
refresh token
device binding
```

Verify that Android and Laravel calculate/validate authentication identically.

Test:

```text
valid key
invalid key
expired token
missing token
invalid signature
clock difference
empty header
wrong account
```

Do not log:

```text
API secret
JWT
password
private key
HMAC secret
```

---

# 17. ACCOUNT ISOLATION

The backend sync code uses an account identifier.

Every syncable record must be associated with the correct account.

Verify:

```text
account_id
API key → account
authenticated user → account
local account → server account
```

A sync request from Account A must NEVER write data into Account B.

---

# 18. CRITICAL SERVER-SIDE SYNC AUDIT

Inspect `SyncController`.

For every entity verify:

```text
Validation
↓
Account resolution
↓
local_id validation
↓
Duplicate detection
↓
Conflict resolution
↓
Create/update
↓
Soft delete
↓
Transaction
↓
Response
```

Do not rely only on:

```php
$request->all()
```

without validating nested payloads adequately.

---

# 19. DATABASE TRANSACTION SAFETY

The sync operation should use a database transaction where appropriate.

But avoid making the entire huge payload fail because of one unrelated entity unless that atomicity is intentionally required.

Consider whether sync should be:

```text
all-or-nothing
```

or:

```text
per-entity/per-record acknowledgement
```

For a mobile offline-first system, per-record acknowledgement is generally more resilient.

---

# 20. MODEL AUDIT

Inspect every relevant Eloquent model.

Verify:

```text
$table
$fillable
$guarded
casts
dates
SoftDeletes
relationships
account relationship
local_id
timestamps
```

Especially verify that:

```php
updateOrCreate()
```

is not blocked by mass-assignment configuration.

---

# 21. MIGRATION AUDIT

For every server model, verify the corresponding migration.

Check:

```text
account_id
local_id
foreign keys
nullable fields
timestamps
soft deletes
indexes
unique constraints
data types
decimal precision
```

There should be an appropriate uniqueness strategy such as:

```text
account_id + local_id
```

for offline-created records.

Do not assume global `local_id` uniqueness.

---

# 22. CUSTOMER SYNC

Customer creation must work like:

```text
Android creates local customer
↓
local_id generated
↓
sync status pending
↓
sync payload includes customer
↓
server identifies account
↓
server updateOrCreate(account_id + local_id)
↓
server returns acknowledgement
↓
Android marks customer synced
```

Repeat this for:

```text
Supplier
Transaction
Wallet
Deposit
Expense
Income
Ledger
```

---

# 23. FOREIGN KEY / DEPENDENCY PROBLEM

This is particularly important.

Example:

```text
Customer
    ↓
Transaction
```

If Customer is created locally with:

```text
local_id = 100
```

and Transaction references:

```text
customer_id = 100
```

but server Customer has:

```text
id = 57
```

then the server cannot blindly treat:

```text
customer_id = 100
```

as the server primary key.

The sync architecture must correctly map:

```text
local_id → server_id
```

for related entities.

This is one of the most important areas to audit.

---

# 24. SERVER-ID MAPPING

Every entity that can be referenced by another entity needs a mapping strategy.

Example:

```text
Customer
local_id = 100
server_id = 57
```

Then Transaction should ultimately reference:

```text
customer_server_id = 57
```

or the backend must resolve:

```text
customer account_id + local_id
```

before inserting the transaction.

Do not create invalid foreign-key relationships.

---

# 25. SYNC ORDER

If dependencies exist, synchronize in a safe order.

Example:

```text
1. Customers
2. Suppliers
3. Wallets / Ledgers
4. Deposits
5. Transactions
6. Expenses / Income
```

Use the actual dependency graph discovered from the codebase.

Do not assume the above order is always correct.

---

# 26. BIDIRECTIONAL SYNC

Inspect `/sync/down`.

It must correctly handle:

```text
server-created records
server-updated records
server-deleted records
```

The Android app must merge server changes without destroying newer local changes.

---

# 27. CONFLICT RESOLUTION

The current backend appears to use timestamps.

Audit this carefully.

Potential conflict:

```text
Phone updated at 12:05
Server updated at 12:06
Phone syncs at 12:07
```

The older record must not overwrite the newer record unless that is explicitly intended.

Use a deterministic conflict policy.

Document it.

---

# 28. TIMESTAMP AUDIT

Do not mix:

```text
seconds
milliseconds
Laravel timestamps
Unix timestamps
ISO timestamps
```

without explicit conversion.

For example:

```text
1700000000
```

and:

```text
1700000000000
```

represent completely different scales.

Standardize the protocol.

---

# 29. DELETE SYNCHRONIZATION

Offline deletion must use tombstones/soft deletes where required.

Correct:

```text
Local delete
↓
PENDING_DELETE
↓
Server receives deletion
↓
Server soft-deletes
↓
Acknowledgement
```

Do not immediately hard-delete local records if they are still required for synchronization.

---

# 30. RETRY POLICY

Transient errors:

```text
timeout
connection reset
DNS failure
temporary 5xx
```

should retry.

Permanent errors:

```text
400 validation error
401 authentication failure
403 authorization failure
422 invalid data
```

should not endlessly retry.

Persist the reason.

---

# 31. HTTP STATUS HANDLING

Explicitly handle:

```text
200
201
204
400
401
403
404
409
422
429
500
502
503
504
```

Especially:

```text
401
403
422
429
5xx
```

---

# 32. RATE LIMITING

The current API has throttling.

Verify that WorkManager retry behaviour does not create a request storm.

If server returns:

```text
429
```

respect backoff.

---

# 33. CACHING PROBLEM

Inspect whether Retrofit/OkHttp or local repositories accidentally cache stale data.

Verify:

```text
GET /sync/down
```

is actually reaching the server when required.

---

# 34. DIRECT CRUD ENDPOINTS

The Android API currently contains direct CRUD-style methods.

Determine whether they are actually used.

If they are unused:

```text
Do not leave confusing duplicate architecture unnecessarily.
```

If they are used:

```text
Either implement matching backend routes properly
OR migrate those operations to the central sync architecture.
```

For this project, prefer a consistent offline-first sync architecture unless there is a documented reason to use direct CRUD.

---

# 35. AUTHENTICATION MUST BE SEPARATE FROM BUSINESS SYNC

Do not use the fact that login works as evidence that sync works.

Authentication flow:

```text
Login
↓
Token
↓
User session
```

Business sync flow:

```text
Local data
↓
Sync payload
↓
API authentication
↓
Server persistence
```

These are separate systems.

---

# 36. LOGGING / DEBUG MODE

Implement safe sync diagnostics.

Recommended log structure:

```text
SYNC_START
SYNC_PAYLOAD_COUNTS
SYNC_REQUEST
SYNC_RESPONSE
SYNC_ACCEPTED
SYNC_REJECTED
SYNC_CONFLICT
SYNC_COMPLETE
SYNC_FAILURE
```

Never log secrets or sensitive financial/customer data unnecessarily.

---

# 37. SERVER LOGGING

Laravel should log useful synchronization diagnostics.

For example:

```text
sync request received
account resolved
entity counts
record rejected
validation failure
database exception
sync completed
```

Avoid logging:

```text
API secret
password
JWT
full financial payload
personal information
```

---

# 38. ERROR RESPONSE STANDARDIZATION

Every API error should have predictable structure.

Recommended:

```json
{
  "success": false,
  "message": "Human readable message",
  "code": "SYNC_VALIDATION_ERROR",
  "errors": {}
}
```

The Android client should parse this consistently.

---

# 39. LOCAL DATABASE ERROR HANDLING

Room exceptions must not be silently swallowed.

Inspect:

```text
SQLiteConstraintException
SQLiteException
SQLCipher errors
migration errors
```

Verify database migrations.

---

# 40. ROOM MIGRATION AUDIT

Every schema change must have a proper migration.

Do not rely on:

```text
fallbackToDestructiveMigration()
```

for production financial data unless there is an extremely specific reason.

Data loss is unacceptable.

---

# 41. SQLCIPHER AUDIT

Verify:

```text
database initialization
encryption key
database opening
migration
backup
restore
```

A failed database migration must not silently create a fresh empty database.

---

# 42. WORKMANAGER LIFECYCLE

Verify sync continues when:

```text
app is closed
screen changes
device restarts
network returns
device is charging/not charging
```

Use appropriate WorkManager constraints.

---

# 43. MANUAL SYNC

Provide a reliable manual sync action.

The UI should show:

```text
Syncing...
Synced
Pending
Failed
Last synced at...
```

If records remain pending, the user should be able to understand why.

---

# 44. OFFLINE MODE

When offline:

```text
Create/update/delete
```

must still work locally.

When online again:

```text
automatic sync
```

should happen.

---

# 45. PRODUCTION DATABASE CONFIGURATION

Inspect Laravel:

```text
.env.example
config/database.php
```

Verify production expects:

```text
DB_CONNECTION
DB_HOST
DB_PORT
DB_DATABASE
DB_USERNAME
DB_PASSWORD
```

Do not commit real credentials.

Also verify Laravel cached configuration does not keep stale values.

Production deployment must include appropriate commands such as:

```bash
php artisan config:clear
php artisan config:cache
php artisan route:clear
php artisan route:cache
php artisan migrate --force
```

Only execute deployment commands when appropriate for the environment.

---

# 46. CPANEL DEPLOYMENT AUDIT

Verify:

```text
Document Root
public/
Laravel storage permissions
bootstrap/cache
PHP version
extensions
Composer dependencies
database credentials
HTTPS
API URL
cron jobs
queue worker
```

If WorkManager is used on Android, a Laravel queue worker may not be required for sync itself.

Do not introduce unnecessary server infrastructure.

---

# 47. API HTTPS REQUIREMENT

Production should use HTTPS.

Do not use:

```text
http://
```

for production financial synchronization.

Verify Android network security configuration.

---

# 48. TEST THE COMPLETE PIPELINE

After implementation, create integration tests.

Minimum test:

```text
Create Customer offline
↓
Verify Room record exists
↓
Verify pending state
↓
Run sync
↓
Verify API request
↓
Verify Laravel response
↓
Verify production DB record
↓
Verify local record contains server ID
↓
Verify status = SYNCED
```

---

# 49. TEST UPDATE

```text
Create Customer
↓
Sync
↓
Modify Customer offline
↓
Sync
↓
Verify server updated
```

---

# 50. TEST DELETE

```text
Create
↓
Sync
↓
Delete offline
↓
Sync
↓
Verify server deletion/soft deletion
```

---

# 51. TEST FAILURE

Test:

```text
Internet disabled
```

Expected:

```text
record remains local
status remains pending
```

Then reconnect.

Expected:

```text
automatic retry
↓
server receives data
↓
record becomes synced
```

---

# 52. TEST SERVER FAILURE

Simulate:

```text
500
503
timeout
```

Expected:

```text
no data loss
retry
```

---

# 53. TEST AUTH FAILURE

Simulate:

```text
401
```

Expected:

```text
refresh/re-authentication if supported
OR
show authentication failure
```

Do NOT delete local records.

---

# 54. TEST MULTI-ACCOUNT

Test:

```text
Account A
Customer X

Account B
Customer Y
```

Verify:

```text
A cannot see Y
B cannot see X
```

---

# 55. TEST DUPLICATE SYNC

Send the same payload twice.

Expected:

```text
one logical server record
```

not:

```text
duplicate records
```

This is critical for retry safety.

---

# 56. IDEMPOTENCY

The server must safely accept repeated sync attempts.

Use:

```text
account_id + local_id
```

or another robust idempotency key.

Do not rely on:

```text
timestamp
```

alone.

---

# 57. FINANCIAL DATA SAFETY

SAFA handles financial records.

Therefore:

```text
NO SILENT DATA LOSS
NO SILENT OVERWRITE
NO SILENT SYNC FAILURE
NO DESTRUCTIVE MIGRATION
NO SECRET LOGGING
NO CROSS-ACCOUNT DATA LEAK
```

are mandatory.

---

# 58. DO NOT MAKE UNRELATED CHANGES

While fixing synchronization:

Do NOT unnecessarily redesign:

```text
UI
branding
navigation
business calculations
financial formulas
authentication UX
```

unless the audit proves they directly cause synchronization problems.

Keep changes focused.

---

# 59. BACKWARD COMPATIBILITY

Existing local data must remain usable.

If schema changes are required:

```text
Migration
+
Backward-compatible mapping
+
Existing local records preserved
```

must be implemented.

---

# 60. DO NOT DELETE EXISTING DATA

Never automatically clear:

```text
Room database
server database
production records
```

during the fix.

Never introduce:

```text
DROP TABLE
TRUNCATE
destructive migration
```

for convenience.

---

# 61. CODE QUALITY

Use clear responsibilities:

```text
UI
↓
ViewModel
↓
UseCase
↓
Repository
↓
Local/Remote Data Source
↓
Sync Engine
```

Do not put:

```text
HTTP calls
database writes
business logic
```

all inside a Compose screen.

---

# 62. SYNC REPOSITORY DESIGN

Prefer a central abstraction such as:

```kotlin
interface SyncRepository {
    suspend fun syncUp(): SyncResult
    suspend fun syncDown(): SyncResult
    suspend fun syncNow(): SyncResult
}
```

Use the existing architecture if an equivalent already exists.

Do not duplicate sync logic in every ViewModel.

---

# 63. SYNC RESULT

Use a meaningful result model.

Example:

```kotlin
sealed class SyncResult {
    data class Success(
        val uploaded: Int,
        val downloaded: Int,
        val failed: Int
    ) : SyncResult()

    data class PartialSuccess(
        val uploaded: Int,
        val failed: Int
    ) : SyncResult()

    data class AuthenticationError(
        val message: String
    ) : SyncResult()

    data class NetworkError(
        val message: String
    ) : SyncResult()

    data class ServerError(
        val message: String
    ) : SyncResult()
}
```

Adapt to existing project conventions.

---

# 64. SERVER SYNC SERVICE

If `SyncController` is currently too large, refactor carefully into services.

Possible:

```text
SyncService
CustomerSyncService
SupplierSyncService
TransactionSyncService
WalletSyncService
ExpenseIncomeSyncService
```

Do not refactor solely for style if it increases risk.

---

# 65. PAYLOAD VALIDATION

Nested arrays should be validated.

Example:

```text
transactions.*.local_id
transactions.*.timestamp
transactions.*.type
```

and equivalent fields.

Validation must not reject legitimate zero values.

For example:

```text
amount = 0
```

should not accidentally be treated as missing if zero is valid.

---

# 66. NULL / EMPTY VALUE AUDIT

Be careful with:

```kotlin
empty string
null
0
false
```

and PHP:

```php
empty()
```

For example:

```php
if (empty($id))
```

treats `0` as empty.

Determine whether `0` is ever a valid identifier.

---

# 67. CURRENT SYNC CONTROLLER REVIEW

The current backend uses checks such as:

```php
if (empty($tx['local_id'])) continue;
```

Audit every such condition.

Do not silently skip invalid records.

Instead, preferably collect them into:

```text
rejected[]
```

and return the reason.

Otherwise Android may believe synchronization succeeded while some records were silently ignored.

---

# 68. IMPORTANT — DO NOT HIDE ERRORS

Never do:

```php
if (invalid) continue;
```

without reporting the rejected record.

A financial synchronization system must provide traceability.

---

# 69. SERVER ACKNOWLEDGEMENT

For every successfully synchronized local record, return enough information for Android to update local state.

Example:

```json
{
  "local_id": 123,
  "server_id": 456,
  "status": "synced"
}
```

---

# 70. LOCAL SERVER-ID UPDATE

After server acknowledgement:

```text
local_id = 123
server_id = 456
sync_status = SYNCED
```

must be persisted locally.

This is essential for later:

```text
update
delete
relationship
conflict resolution
```

---

# 71. RELATIONSHIP MAPPING

For example:

```text
Transaction
customerLocalId = 100
```

must be translated appropriately before server persistence.

Never assume:

```text
local ID == server ID
```

---

# 72. SYNC DOWN MERGE

When downloading:

```text
server_id
local_id
updated_at
deleted_at
```

must be handled consistently.

Do not create duplicate local records.

---

# 73. CLOCK SKEW

Do not blindly trust device time.

Mobile device clocks can be wrong.

Prefer server timestamps where possible.

---

# 74. SECURITY

Do not commit:

```text
.env
API secret
production DB password
JWT secret
keystore
signing credentials
private certificates
```

Check Git history as well if secrets may previously have been committed.

---

# 75. AGENT MUST SEARCH FOR HARDCODED URLs

Search entire repository for:

```text
http://
https://
localhost
127.0.0.1
10.0.2.2
192.168.
10.
api/
sync/up
sync/down
```

Classify each occurrence.

---

# 76. AGENT MUST SEARCH FOR SILENT EXCEPTIONS

Search for:

```text
catch
Exception
Throwable
runCatching
try
```

Inspect every sync-related exception handler.

---

# 77. AGENT MUST SEARCH FOR DISABLED SYNC

Search for:

```text
syncEnabled
autoSync
enableSync
disableSync
isOnline
pending
dirty
unsynced
syncStatus
lastSync
WorkManager
PeriodicWorkRequest
OneTimeWorkRequest
```

Determine whether synchronization is accidentally disabled.

---

# 78. AGENT MUST SEARCH FOR LOCAL-ONLY OPERATIONS

Search for:

```text
insert
update
delete
upsert
```

in every repository.

Determine whether each operation:

```text
only writes Room
```

or:

```text
writes Room + queues synchronization
```

Any syncable business operation that only writes Room without marking itself for sync is a likely root cause.

---

# 79. AGENT MUST SEARCH FOR DIRECT API USAGE

Search for:

```text
createCustomer
createSupplier
createTransaction
createDeposit
```

and every Retrofit API method.

Determine which ones are actually called.

---

# 80. AGENT MUST SEARCH FOR DEAD CODE

Identify:

```text
unused SyncService
unused API methods
unused Workers
duplicate repositories
duplicate DTOs
duplicate entity models
```

Do not delete immediately.

First prove they are unused.

---

# 81. FINAL ARCHITECTURE

After repair, the preferred architecture should conceptually become:

```text
                 ┌─────────────────────┐
                 │   Jetpack Compose   │
                 └──────────┬──────────┘
                            ↓
                       ViewModel
                            ↓
                       Repository
                     ↙            ↘
              Local Room       Sync Queue
                  ↓                 ↓
            SQLCipher DB       Sync Worker
                                    ↓
                                Retrofit
                                    ↓
                               HTTPS API
                                    ↓
                              Laravel API
                                    ↓
                             Sync Service
                                    ↓
                              Eloquent ORM
                                    ↓
                           Production Database
```

---

# 82. SUCCESS CRITERIA

The task is NOT complete until all of the following are true:

### Customer

```text
Create offline
→ local
→ pending
→ sync
→ production DB
→ server ID returned
→ local synced
```

### Supplier

Same.

### Transaction

Same.

### Wallet

Same.

### Deposit

Same.

### Expense

Same.

### Income

Same.

### Ledger

Same.

---

# 83. REQUIRED TEST MATRIX

Create tests for:

```text
Online create
Offline create
Online update
Offline update
Online delete
Offline delete
Network failure
Timeout
Server 500
Server 422
Server 401
Server 403
Duplicate retry
Application restart
Device restart
Multiple accounts
Large pending queue
Dependency ordering
Conflict
Server-side deletion
Server-side update
```

---

# 84. LARGE QUEUE TEST

Test:

```text
100+
500+
1000+
```

pending records where practical.

The app must not:

```text
OOM
freeze UI
duplicate records
lose records
```

---

# 85. FINAL VERIFICATION

Before declaring success:

### Android

```text
./gradlew test
```

and relevant Android tests.

### Laravel

```bash
php artisan test
```

Also run:

```bash
php artisan route:list
```

to verify expected API routes.

Check migrations.

---

# 86. PRODUCTION VERIFICATION

After deployment, verify:

```text
GET /api/...
POST /api/sync/up
GET /api/sync/down
```

using the actual production environment.

Do NOT use real customer financial data for initial testing.

Use a controlled test account.

---

# 87. REQUIRED AGENT FINAL REPORT

When finished, report exactly:

## A. Root Cause

Explain the actual root cause(s).

Do not report guesses.

## B. Files Changed

List every modified file.

## C. What Was Fixed

Explain each change.

## D. Database Changes

List migrations/schema changes.

## E. API Changes

List endpoint/request/response changes.

## F. Android Changes

List:

```text
Repository
DAO
Worker
Sync Engine
Retrofit
DTO
ViewModel
```

changes.

## G. Tests

List tests executed and results.

## H. Remaining Risks

List anything that could not be verified.

---

# 88. ABSOLUTE RULES

Never:

```text
❌ delete production data
❌ clear local database automatically
❌ use destructive migration
❌ silently discard sync failures
❌ silently ignore rejected records
❌ hardcode production secrets
❌ log API secrets
❌ assume local ID == server ID
❌ mark record synced before server acknowledgement
❌ claim sync works without testing
❌ remove functionality just because it is difficult
```

Always:

```text
✅ preserve existing data
✅ make synchronization idempotent
✅ maintain account isolation
✅ retain failed records
✅ provide retry
✅ provide server acknowledgement
✅ map local IDs to server IDs
✅ test offline mode
✅ test online mode
✅ test failure scenarios
✅ verify production API
```

---

# 89. MOST IMPORTANT TASK

The ultimate requirement is:

> **When a user creates, updates, or deletes any syncable business record from the Android application, the operation must remain safe offline and must eventually reach the correct production account/database automatically when connectivity is available.**

The system must provide:

```text
Offline-first
+
Reliable synchronization
+
Retry
+
Idempotency
+
Conflict handling
+
Server acknowledgement
+
Local/server ID mapping
+
Account isolation
+
No data loss
```

Do not stop after fixing only the first discovered bug.

Trace the entire pipeline and repair every related synchronization defect discovered during the audit.

---

# 90. AGENT EXECUTION ORDER

Follow this exact order:

```text
PHASE 1
Complete repository inspection

↓

PHASE 2
Generate actual architecture/data-flow map

↓

PHASE 3
Identify every syncable entity

↓

PHASE 4
Trace Android local write path

↓

PHASE 5
Trace Android sync queue

↓

PHASE 6
Trace WorkManager / Sync Worker

↓

PHASE 7
Trace Retrofit/API/security

↓

PHASE 8
Trace Laravel routes/middleware

↓

PHASE 9
Trace SyncController/Services

↓

PHASE 10
Trace Models/migrations/database

↓

PHASE 11
Identify exact root causes

↓

PHASE 12
Design minimal safe fix

↓

PHASE 13
Implement Android fixes

↓

PHASE 14
Implement Laravel fixes

↓

PHASE 15
Implement migrations if necessary

↓

PHASE 16
Implement tests

↓

PHASE 17
Run tests

↓

PHASE 18
Review for regressions

↓

PHASE 19
Production configuration verification

↓

PHASE 20
Final audit report
```

---

# 91. FINAL INSTRUCTION TO THE CODING AGENT

**Do not merely patch the symptom.**

The current symptom is:

```text
Android data exists locally
but does not reliably reach cPanel production database.
```

Find why.

The final implementation must establish a reliable:

```text
LOCAL DATABASE
      ↓
SYNC QUEUE
      ↓
SYNC ENGINE
      ↓
API
      ↓
LARAVEL
      ↓
PRODUCTION DATABASE
```

pipeline.

Every syncable entity must follow the same predictable lifecycle.

If an existing implementation is already correct, preserve it.

If multiple competing implementations exist, consolidate them carefully.

If you discover a deeper architectural problem, fix the architecture rather than adding another workaround.

**Do not declare completion until you can demonstrate, through code inspection and tests, that the complete Android → API → Laravel → production database path works for all syncable entities.**
