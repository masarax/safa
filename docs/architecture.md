# SAFA Production Architecture

## Primary contract

The Android application communicates with Laravel through the versioned REST contract at `/api/v1/*`. The existing `/api/*` routes remain as a backward-compatible migration surface; new Android code must not add unversioned endpoints.

```text
Compose UI
   -> Repository
      -> Encrypted LocalFirstStore (durable local cache + outbox)
      -> Retrofit/OkHttp (/api/v1)
      -> WorkManager (sync/retry)
      -> DataStore (non-secret metadata)
      -> Android Keystore-backed token protection
   -> Laravel API / relational database
```

## Security boundary

Laravel remains authoritative for authentication, authorization, account isolation, validation, server IDs, revisions, conflict resolution and sync reconciliation. DataStore is never a secret vault; access/refresh/session/device credentials remain protected by the existing Keystore-backed encrypted storage.

## Local persistence

`AppRepository` uses `LocalFirstStore` as the single production Android business-data persistence implementation. The store is backed by SQLite through `SQLiteOpenHelper`; business payloads are encrypted before persistence and the same durable database tracks mutation outbox state, retry metadata and server revisions.

There is no parallel Room/SQLCipher production store. Authentication/account switching is a lifecycle boundary: account-scoped cached records and queued mutations must not leak into another authenticated account.

## Synchronization

Offline mutations remain idempotent and are persisted through the durable outbox. WorkManager owns background execution and retry scheduling. Collection synchronization is bounded and page/cursor based. Sync/reconciliation may carry explicit tombstones and revision metadata, while ordinary UI collection reads expose active records only.

## API evolution

`/api/v1` is the compatibility boundary for Android. The server keeps the existing unversioned routes during migration so deployed clients do not break. Versioning is implemented as a server-side compatibility bridge to the existing Laravel controllers, preserving the same authorization and business rules.

## Technology roles

- `LocalFirstStore` / SQLiteOpenHelper: encrypted durable local business cache, server revisions and mutation outbox.
- Retrofit + OkHttp + Moshi: versioned HTTP transport and JSON DTO contract.
- WorkManager: unique, idempotent background synchronization and bounded retry.
- DataStore: lightweight non-secret application/session metadata.
- Android Keystore-backed storage: sensitive tokens, credentials and keys.
- Laravel + relational database: business authority and server reconciliation.

See `docs/PRODUCTION_ARCHITECTURE.md` for the detailed production contract and migration rules.
