# SAFA synchronization protocol

## Production contract

SAFA uses the Android encrypted local store as the device source of truth and a durable outbox for local writes. Server reconciliation has two read phases:

1. **Bootstrap** — `GET /api/v1/sync/down?page=N&per_page=100` returns bounded pages. The first page establishes `snapshot_cursor`; the client reuses that cursor for every subsequent bootstrap page and persists each page before requesting the next.
2. **Incremental reconciliation** — `GET /api/v1/sync/changes?cursor=C&limit=100` returns only account changes whose monotonic journal cursor is greater than `C`.

The Android client stores its cursor in the same account-owned SQLite metadata namespace as the local cache. Account switch, logout/account-state destruction and explicit cache binding reset the cursor together with account data.

## Crash and network safety

The client applies every downloaded chunk using the existing `sync_version` and pending-mutation conflict guard. The cursor is persisted only after every row in that chunk has been written to the encrypted local store. If the process terminates before the cursor write, the same chunk is downloaded again and merged idempotently.

An unchanged account therefore returns only bounded protocol metadata. A single committed server mutation produces a single journal entry (or a small number of harmless duplicate event entries) rather than forcing a historical full-account download.

## Deletes and long-offline devices

All normal business deletes use soft deletion, and the change journal records the delete event. Delta responses load rows with trashed records so a device receives the tombstone.

`php artisan safa:prune-sync-changes --days=90` compacts old journal history. Before deleting history, it records a per-account `floor_cursor`. If a client later requests a cursor below that floor, the server returns `reset_required`; the client performs a fresh bounded bootstrap. This prevents a device that was offline beyond journal retention from silently missing old deletions.

Laravel schedules the compaction command daily at 03:15. Production hosts must run Laravel's scheduler (`php artisan schedule:run` every minute or the equivalent supported scheduler integration).

## Legacy compatibility

`GET /api/sync/down` is deprecated. It remains available for older installations only while every readable entity contains at most 500 rows. Larger accounts receive HTTP 426 `upgrade_required` with deprecation/sunset headers instead of causing an unbounded in-memory response.

Sunset target: **31 December 2026**. New clients must use `/api/v1/sync/down` for bootstrap and `/api/v1/sync/changes` afterward.

## Operational checks

For a release that changes synchronization:

- run full Laravel tests on SQLite and strict MySQL;
- run Android unit, lint, release build and emulator/instrumentation smoke tests;
- verify an unchanged cursor returns zero business changes;
- verify create/update/delete each advance the cursor and reconcile only affected rows;
- interrupt a multi-page bootstrap/delta run and confirm retry resumes without loss or duplication;
- verify a cursor older than `sync_change_floors.floor_cursor` triggers a safe bootstrap reset;
- keep outbox conflict/idempotency tests green.

Do not manually advance a device cursor, delete `sync_change_floors`, or purge soft-deleted business rows independently of a reviewed retention/migration plan.
