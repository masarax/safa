# SAFA Production Architecture

## Primary contract

The Android application communicates with Laravel through the versioned REST contract at `/api/v1/*`. The existing `/api/*` routes remain as a backward-compatible migration surface; new Android code must not add unversioned endpoints.

```text
Compose UI
   -> Repository
      -> Room (offline domain source of truth)
      -> Retrofit/OkHttp (/api/v1)
      -> WorkManager (sync/retry)
      -> DataStore (non-secret metadata)
      -> Android Keystore-backed token protection
   -> Laravel API / relational database
```

## Security boundary

Laravel remains authoritative for authentication, authorization, account isolation, validation, server IDs, revisions, conflict resolution and sync reconciliation. DataStore is never a secret vault; access/refresh/session/device credentials remain protected by the existing Keystore-backed encrypted storage.

## Synchronization

Offline mutations remain idempotent and are persisted through the durable outbox. WorkManager owns background execution and retry scheduling. Collection synchronization must be bounded and page/cursor based; clients must process one page at a time rather than loading an unbounded dataset into memory.

## API evolution

`/api/v1` is the compatibility boundary for Android. The server keeps the existing unversioned routes during migration so deployed clients do not break. Versioning is implemented as a server-side compatibility bridge to the existing Laravel controllers, preserving the same authorization and business rules.

## Technology roles

- Room: local domain data and offline reads/writes.
- Retrofit + OkHttp: HTTP transport and API error/status preservation.
- WorkManager: unique, idempotent background synchronization.
- DataStore: lightweight non-secret application/session metadata.
- Android Keystore-backed storage: sensitive tokens, credentials and keys.
- Laravel + relational database: business authority and server reconciliation.
