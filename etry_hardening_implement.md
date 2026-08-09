I reviewed the complete real_e2e_production_connectivity_verification.md.

The core production synchronization is now verified successfully:

* Android → production API
* Customer → production MySQL
* Server acknowledgement → Android Room
* Transaction → production MySQL
* local customer ID → server customer ID FK resolution
* duplicate upload protection
* rejection handling
* timestamp compatibility

So the core sync problem is considered resolved.

However, I do NOT accept Test F as PASS.

Your own report confirms that:

```text
AutoSyncWorker
WHERE syncStatus != 1
```

causes `SYNC_FAILED (4)` records to be retried every 15 minutes indefinitely.

The proposed retryCount solution is currently only proposed, not implemented.

## Implement ONLY this hardening now

Do not start any new architectural phase.

### 1. Add retry metadata

Add to all 7 syncable Room entities:

```text
retryCount: Int = 0
lastSyncAttemptAt: Long? = null
```

Use a safe Room migration.

Do NOT use destructive migration.

### 2. Update pending queries

Do not simply use:

```sql
WHERE syncStatus != 1
```

Instead distinguish retryable and permanently failed records.

Temporary/network failures should remain automatically retryable.

Permanent validation/business failures should become:

```text
SYNC_FAILED
```

and should NOT be retried forever.

### 3. Implement retry classification

At minimum distinguish:

#### Retryable

* network timeout
* connection failure
* HTTP 408
* HTTP 429
* HTTP 500
* HTTP 502
* HTTP 503
* HTTP 504

#### Non-retryable

* validation errors
* missing required fields
* invalid foreign key
* account/permission rejection
* malformed business data

Do not blindly classify every HTTP error as retryable.

### 4. Retry limit

For retryable failures:

```text
retryCount += 1
```

Use a maximum retry count of 5.

After the maximum:

```text
SYNC_FAILED
```

and mark it as requiring user review.

### 5. Reset retry state on success

When a record is successfully synchronized:

```text
syncStatus = SYNCED
serverId = actual server ID
syncError = null
retryCount = 0
lastSyncAttemptAt = current time
```

### 6. User retry

A permanently failed record must have a clear way to be manually retried.

Manual retry should reset:

```text
retryCount = 0
syncStatus = PENDING_CREATE/PENDING_UPDATE/PENDING_DELETE
syncError = null
```

according to the record's actual operation.

Do not accidentally convert an update/delete into PENDING_CREATE.

### 7. Backoff

Do not retry a failed network request every few seconds.

Use reasonable exponential/backoff scheduling, while respecting WorkManager constraints.

### 8. Tests

Add tests for:

1. Successful sync resets retryCount.
2. Network failure increments retryCount.
3. Retryable HTTP 500 retries.
4. Validation error does NOT automatically retry forever.
5. retryCount >= 5 stops automatic retry.
6. Manual retry resets retry state.
7. PENDING_UPDATE remains PENDING_UPDATE after manual retry.
8. PENDING_DELETE remains PENDING_DELETE after manual retry.

### 9. Migration verification

Verify:

```text
Existing local records
        ↓
preserved
        ↓
retryCount = 0
lastSyncAttemptAt = null
```

No existing business data may be deleted.

### 10. Final verification

Run:

* Android compilation
* Room migration verification
* Android tests
* Laravel PHPUnit tests

Then generate:

```text
sync_retry_hardening_report.md
```

The report must clearly state:

```text
A. Retry metadata: PASS/FAIL
B. Retryable error handling: PASS/FAIL
C. Permanent error handling: PASS/FAIL
D. Maximum retry limit: PASS/FAIL
E. Manual retry: PASS/FAIL
F. Backoff: PASS/FAIL
G. Migration safety: PASS/FAIL
H. Android build: PASS/FAIL
I. Tests: PASS/FAIL
```

Do NOT make any unrelated changes.

Do NOT start another phase after this.

Wait for my review of the hardening report.
