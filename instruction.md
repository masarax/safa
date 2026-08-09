The Android app now launches successfully, but there is a critical functional/data synchronization problem.

IMPORTANT:
Do NOT make speculative changes to the Android startup/crash architecture anymore. Startup is now working.

The current problem is:

- Customer data is being stored only in the local Android Room/SQLCipher database.
- Supplier/Vendor data is being stored only locally.
- Income/Buy/Transaction data is being stored only locally.
- The corresponding records are NOT appearing in the production cPanel/Laravel database.
- Therefore Android local DB and the production backend database are currently not synchronized correctly.

Perform a FULL END-TO-END DATA SYNCHRONIZATION FORENSIC AUDIT.

Architecture that MUST be verified:

Android App
    ↓
Repository / DAO
    ↓
API Service / Retrofit
    ↓
HTTP Request
    ↓
https://safa.masarax.com/api/
    ↓
Laravel Controller / API Route
    ↓
Laravel Service / Model
    ↓
Production MySQL Database on cPanel
    ↓
Response
    ↓
Android local Room/SQLCipher DB synchronization

DO NOT assume that WorkManager or TokenManager working means synchronization is working.

==================================================
1. AUDIT ALL DATA ENTITIES
==================================================

Identify every business entity that should synchronize with the backend, especially:

- User
- Customer
- Supplier / Vendor
- Income
- Buy
- Transaction
- Payment
- Due / Partial / Paid status
- Rates
- Any other business/accounting records currently stored locally

For EACH entity, document:

Android Entity/Model
→ DAO
→ Repository
→ API interface/service
→ HTTP method
→ API endpoint
→ Laravel route
→ Laravel controller
→ Laravel service (if applicable)
→ Laravel model
→ Production database table
→ Response mapping
→ Local database update

Provide the exact file/class/function names for each layer.

==================================================
2. DETERMINE WHETHER WRITES ARE LOCAL-ONLY
==================================================

Inspect every create/update/delete operation.

For example:

createCustomer()
updateCustomer()
deleteCustomer()
createSupplier()
updateSupplier()
createTransaction()
createIncome()
recordPayment()
etc.

Determine whether each operation does:

A. Local Room DB only
B. API only
C. Local DB + API
D. Queue locally and synchronize later

Report the exact current behavior.

If a repository method only calls a DAO and never calls the API, identify it explicitly.

==================================================
3. AUDIT API ENDPOINTS
==================================================

Inspect the complete Android API layer.

Verify:

- Retrofit/Base URL
- Authentication headers
- Bearer token/session handling
- API security interceptor
- Request serialization
- JSON field names
- Request body structure
- HTTP methods
- Response models
- Error handling
- HTTP status handling
- 401/403 handling
- 419 handling if applicable
- Network connectivity handling
- Timeout configuration

For every business API endpoint, provide:

METHOD
ENDPOINT
REQUEST BODY
AUTHENTICATION
EXPECTED RESPONSE
ACTUAL ANDROID CALLER

Do not invent endpoints. Use the actual code.

==================================================
4. AUDIT LARAVEL API
==================================================

Inspect:

routes/api.php
Controllers
Form Requests
Services
Models
Policies/permissions
API Resources
Database migrations
Relevant middleware

Verify that the production API actually has endpoints for:

- customers
- suppliers/vendors
- transactions
- income
- buy
- payments
- rates
- any other synchronized entity

For each endpoint verify:

- route exists
- authentication works
- validation accepts Android payload
- controller actually writes to database
- model/table is correct
- required foreign keys are correct
- transaction/database commit happens
- response is returned correctly

==================================================
5. VERIFY PRODUCTION DATABASE
==================================================

Do NOT assume the Laravel code is connected to the correct production database.

Verify the production configuration/environment used by:

https://safa.masarax.com

Confirm:

DB_CONNECTION
DB_HOST
DB_PORT
DB_DATABASE
DB_USERNAME
DB_PASSWORD

Do NOT expose the actual password or secrets in the report.

Confirm that the API is writing to the intended cPanel production MySQL database.

Check actual production table names and record counts if access is available.

==================================================
6. TRACE ONE COMPLETE RECORD
==================================================

Use ONE test customer and ONE test transaction.

Trace them from beginning to end:

Android UI
→ ViewModel
→ Repository
→ DAO
→ API call
→ HTTP request
→ Laravel route
→ Controller
→ Model
→ MySQL
→ API response
→ Android local DB

For the test request, capture:

- endpoint
- HTTP method
- HTTP status
- request payload (without secrets)
- response payload
- Laravel log result
- database insertion/update result

The goal is to identify the exact layer where the data stops.

==================================================
7. IMPORTANT: DO NOT HIDE NETWORK FAILURES
==================================================

Search for code that catches exceptions such as:

try {
    apiCall()
} catch (...) {
    // ignore
}

or returns success despite an API failure.

Any such behavior must be identified.

A failed server synchronization MUST NOT silently look like a successful local save.

The UI should be able to distinguish:

SYNCED
PENDING
FAILED

If the current application has no such state, report that rather than inventing one.

==================================================
8. WORKMANAGER / OFFLINE-FIRST AUDIT
==================================================

Inspect AutoSyncWorker and all synchronization workers.

Determine:

- What tables are synchronized?
- What records are uploaded?
- How are pending records identified?
- What happens after successful upload?
- What happens after failure?
- Is retry implemented?
- Is exponential/backoff retry implemented?
- Are duplicate records prevented?
- Is there an idempotency/client UUID?
- Does the worker actually call the API?
- Does it upload Customer/Supplier/Transaction records or only rates/config?

IMPORTANT:

Do not assume AutoSyncWorker synchronizes business data simply because it exists.

Show the actual implementation and list exactly what it synchronizes.

==================================================
9. BIDIRECTIONAL SYNC
==================================================

Determine whether the architecture currently supports:

Android → Server

and

Server → Android

For each entity classify:

LOCAL ONLY
SERVER ONLY
ANDROID → SERVER
SERVER → ANDROID
BIDIRECTIONAL

If synchronization is incomplete, identify exactly what is missing.

==================================================
10. MULTI-DEVICE DATA CONSISTENCY
==================================================

The final system must allow:

Device A creates Customer
        ↓
Server
        ↓
Device B can retrieve Customer

and:

Device A creates Transaction
        ↓
Server
        ↓
Device B retrieves the Transaction

Verify whether the current architecture actually supports this.

==================================================
11. DO NOT DESTROY LOCAL DATA
==================================================

During synchronization fixes:

- Do not delete the existing Room database.
- Do not clear customer/supplier/transaction records.
- Do not replace the local database with a new empty database.
- Do not delete SQLCipher encryption/passphrase logic.
- Preserve existing local data.

If migration is required, implement a safe migration.

==================================================
12. FINAL REPORT FORMAT
==================================================

Produce a report with:

A. ROOT CAUSE
Exactly why business data is currently remaining local.

B. DATA FLOW DIAGRAM
Android → API → Laravel → MySQL → Android

C. ENTITY SYNC MATRIX

| Entity | Local DB | API Endpoint | Laravel Route | Server DB | Upload | Download | Status |

D. BROKEN COMPONENTS
Exact files/classes/functions responsible.

E. REQUIRED FIXES
Only fixes supported by code evidence.

F. IMPLEMENTATION
Implement the necessary synchronization fixes.

G. TESTS
Create/run tests for:

1. Customer create → server
2. Supplier create → server
3. Income create → server
4. Buy create → server
5. Transaction create → server
6. Payment/update → server
7. Server → Android synchronization
8. Failed network request
9. Retry
10. Duplicate prevention

H. PHYSICAL/PRODUCTION VERIFICATION

After implementation, perform a real test against:

https://safa.masarax.com/api/

and verify that a test record created from the Android app actually appears in the production MySQL database.

IMPORTANT FINAL RULE:

Do NOT mark the application as production-ready merely because:
- Android builds successfully
- unit tests pass
- app launches
- Room works
- SQLCipher works
- WorkManager initializes

The application is NOT functionally ready until business records created in Android are demonstrably persisted in the production Laravel/MySQL database and can be retrieved back into the Android app.

Do not stop at an audit summary. Implement the necessary fixes after identifying the actual broken data path.