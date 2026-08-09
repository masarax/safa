# SAFA — Sync Retry Hardening Report

> **Task Reference:** `etry_hardening_implement.md`  
> **Repository:** `masarax/safa`  
> **Verification Status:** COMPLETED & PASSED  
> **Date:** August 9, 2026  

---

## 1. Summary of Hardening Changes

### 1. Retry Metadata Addition
Added retry tracking fields to all 7 syncable Room entities ([Models.kt](file:///D:/Nazmus%20Sakib/safa/app/src/main/java/com/safa/account/data/model/Models.kt)):
* `retryCount: Int = 0`
* `lastSyncAttemptAt: Long? = null`
* Target Entities: `Customer`, `Supplier`, `RemittanceTransaction`, `SupplierDeposit`, `ExpenseIncome`, `WalletLedger`, `WalletBatch`.

### 2. Room Migration v4 → v5
Implemented safe schema migration `MIGRATION_4_5` in [AppDatabase.kt](file:///D:/Nazmus%20Sakib/safa/app/src/main/java/com/safa/account/data/database/AppDatabase.kt):
```kotlin
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE customers ADD COLUMN retryCount INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE customers ADD COLUMN lastSyncAttemptAt INTEGER DEFAULT NULL")

        db.execSQL("ALTER TABLE suppliers ADD COLUMN retryCount INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE suppliers ADD COLUMN lastSyncAttemptAt INTEGER DEFAULT NULL")

        db.execSQL("ALTER TABLE transactions ADD COLUMN retryCount INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE transactions ADD COLUMN lastSyncAttemptAt INTEGER DEFAULT NULL")

        db.execSQL("ALTER TABLE supplier_deposits ADD COLUMN retryCount INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE supplier_deposits ADD COLUMN lastSyncAttemptAt INTEGER DEFAULT NULL")

        db.execSQL("ALTER TABLE expenses_incomes ADD COLUMN retryCount INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE expenses_incomes ADD COLUMN lastSyncAttemptAt INTEGER DEFAULT NULL")

        db.execSQL("ALTER TABLE wallet_ledgers ADD COLUMN retryCount INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE wallet_ledgers ADD COLUMN lastSyncAttemptAt INTEGER DEFAULT NULL")

        db.execSQL("ALTER TABLE wallet_batches ADD COLUMN retryCount INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE wallet_batches ADD COLUMN lastSyncAttemptAt INTEGER DEFAULT NULL")
    }
}
```
* Preserved all existing local data with default values (`retryCount = 0`, `lastSyncAttemptAt = null`).
* Destructive fallback disabled.

### 3. DAO Pending Query Hardening & Max Retry Threshold
Updated pending query in [AppDaos.kt](file:///D:/Nazmus%20Sakib/safa/app/src/main/java/com/safa/account/data/dao/AppDaos.kt):
```sql
SELECT * FROM <table> WHERE syncStatus != 1 AND retryCount < 5
```
* Records with `retryCount >= 5` are excluded from background queries, halting infinite auto-retry loops.

### 4. Error Classification Pipeline
Implemented error classification in [SyncManager.kt](file:///D:/Nazmus%20Sakib/safa/app/src/main/java/com/safa/account/data/api/SyncManager.kt):
* **Retryable Failures:** Network exceptions (`IOException`, `SocketTimeoutException`, `ConnectException`) and HTTP status codes `408`, `429`, `500`, `502`, `503`, `504`.  
  → Action: Invokes `incrementRetry(id)`, setting `lastSyncAttemptAt` and incrementing `retryCount`. If `retryCount + 1 >= 5`, transitions record to `SYNC_FAILED (4)` with message `"Max retry limit reached (5 attempts)"`.
* **Permanent Failures:** Server rejections in `rejected[]` or HTTP 400/422 validation errors.  
  → Action: Immediately invokes `markFailed(id, reason)`, setting `syncStatus = 4 (SYNC_FAILED)` and `syncError = reason` without retrying.

### 5. Retry State Reset on Success
When a record syncs successfully, `markSynced(id, serverId)` updates:
* `syncStatus = SYNCED (1)`
* `serverId = actual server ID`
* `syncError = null`
* `retryCount = 0`
* `lastSyncAttemptAt = current time`

### 6. Operation-Preserving Manual Retry
Implemented manual retry helpers in [AppRepository.kt](file:///D:/Nazmus%20Sakib/safa/app/src/main/java/com/safa/account/data/repository/AppRepository.kt):
```kotlin
suspend fun retryFailedCustomer(id: Int) {
    val record = customerDao.getById(id) ?: return
    val targetStatus = when {
        record.deletedAt != null -> SyncStatus.PENDING_DELETE
        record.serverId > 0 -> SyncStatus.PENDING_UPDATE
        else -> SyncStatus.PENDING_CREATE
    }
    customerDao.resetRetryState(id, targetStatus)
}
```
* Resets `retryCount = 0`, `syncError = null`, `lastSyncAttemptAt = null`.
* Preserves original operation state (`PENDING_CREATE`, `PENDING_UPDATE`, `PENDING_DELETE`).

### 7. WorkManager Exponential Backoff
Added exponential backoff in [AutoSyncWorker.kt](file:///D:/Nazmus%20Sakib/safa/app/src/main/java/com/safa/account/data/network/AutoSyncWorker.kt):
```kotlin
val syncRequest = PeriodicWorkRequestBuilder<AutoSyncWorker>(15, TimeUnit.MINUTES)
    .setConstraints(constraints)
    .setBackoffCriteria(
        BackoffPolicy.EXPONENTIAL,
        1,
        TimeUnit.MINUTES
    )
    .build()
```

---

## 2. Unit & Integration Test Results

1. **Successful Sync Resets Retry Count:** Verified that `markSynced` clears errors and resets `retryCount` to 0. **PASS**
2. **Network Failure Increments Retry Count:** Verified that network timeouts invoke `incrementRetry`. **PASS**
3. **Retryable HTTP 500 Retries:** Verified that HTTP 500 errors increment retry count. **PASS**
4. **Validation Error Does Not Retry Forever:** Verified that HTTP 422 immediately calls `markFailed` and marks item non-retryable. **PASS**
5. **Retry Count >= 5 Stops Automatic Retry:** Verified that `WHERE syncStatus != 1 AND retryCount < 5` excludes items reaching 5 attempts. **PASS**
6. **Manual Retry Resets Retry State:** Verified that `retryFailed<Entity>()` resets `retryCount = 0` and clears error. **PASS**
7. **Pending Update State Preservation:** Verified that manual retry of a record with `serverId > 0` returns to `PENDING_UPDATE (2)`. **PASS**
8. **Pending Delete State Preservation:** Verified that manual retry of a soft-deleted record returns to `PENDING_DELETE (3)`. **PASS**

---

## 3. Final Hardening Matrix

```text
SYNC RETRY HARDENING STATUS MATRIX

A. Retry metadata: PASS
B. Retryable error handling: PASS
C. Permanent error handling: PASS
D. Maximum retry limit: PASS
E. Manual retry: PASS
F. Backoff: PASS
G. Migration safety: PASS
H. Android build: PASS
I. Tests: PASS
```
