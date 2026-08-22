# Incremental sync cursor protocol

## Scope

`GET /api/v1/sync/down` is the canonical Android download contract. Clients send an account-scoped cursor and receive at most 250 change snapshots per response.

The seven synchronized entities are:

- `customers`
- `suppliers`
- `wallet_ledgers`
- `supplier_deposits`
- `wallet_batches`
- `transactions`
- `expenses_incomes`

## Cursor semantics

The server stores an append-only `sync_changes` feed. Its monotonically increasing `id` is the cursor high-water source. Every entry contains the account, entity, server record ID, operation and authoritative record snapshot, including `sync_version` and `deleted_at`.

A client starts with `cursor=0`. A successful response contains:

- `protocol: cursor-v1`
- `cursor`: the requested cursor
- `next_cursor`: the last change included in this chunk, or the requested cursor when idle
- `high_water`: the newest currently visible change ID for this account/permission context
- `has_more`: whether another bounded chunk is immediately available
- the same seven entity arrays used by the existing Android merge layer

Cursor IDs may contain gaps because the database sequence is shared across accounts. They remain strictly monotonic for every account and must be treated as opaque positions, not row counts.

## Android durability rule

Android must process one chunk at a time:

1. Read the persisted cursor for the active account.
2. Request one bounded chunk.
3. Validate that the returned account and cursor match the active request.
4. Merge every row through the existing `sync_version` and pending-mutation conflict guard.
5. Persist the new cursor only after all rows in the chunk have been durably written.
6. Request another chunk only when `has_more` is true.

If the process stops after row persistence but before cursor persistence, the same chunk is replayed. Replay is safe because server snapshots carry authoritative `sync_version` values and the local merge boundary ignores equal/older versions and protects pending local mutations.

Cursor persistence is isolated by account. A cursor must never be reused for another account.

## Server mutation capture

All seven syncable Eloquent models are observed. Direct server/web writes that do not explicitly manage `sync_version` advance the version automatically and append a change snapshot. The mobile `SyncReconciliationService` continues to own its existing idempotent mutation and stale-version logic; because it explicitly advances `sync_version`, the observer does not increment it a second time.

Soft deletes append a tombstone snapshot with a newer `sync_version` and non-null `deleted_at`.

## Bootstrap and memory bounds

Existing rows are backfilled into `sync_changes` in 500-row migration chunks. New installations therefore bootstrap through the same cursor protocol instead of a separate full-account response.

The server caps a cursor response at 250 changes. Android persists each response before requesting the next and no longer accumulates all pages in a single in-memory `SyncDownResponse`. Network-side memory is therefore bounded by the configured chunk size rather than total account history.

## Tombstone retention

`sync_changes` is intentionally append-only in `cursor-v1`; automatic compaction is disabled. This preserves delete history for devices that may remain offline for an unbounded period and prevents a removed record from being resurrected by a stale client.

A future compaction mechanism must introduce an explicit protocol epoch/reset checkpoint or an acknowledged safe horizon before deleting change history. It must never silently delete tombstones while a valid client cursor can still reference an earlier position.

## Compatibility endpoint

`GET /api/sync/down` remains temporarily available for installed legacy clients, but it no longer performs unbounded full-table reads. It uses the bounded legacy page response with a maximum page size of 250 and returns:

- `protocol: legacy-page-v1`
- `Deprecation: true`
- `Sunset: Tue, 01 Dec 2026 00:00:00 GMT`
- a `Link` header pointing to `/api/v1/sync/down`

The legacy route must not be extended with new sync features. New clients use `cursor-v1` exclusively.

## Operational invariants

- Cursor values never regress.
- `has_more=true` requires `next_cursor > cursor`.
- Account context cannot change during a download loop.
- Permission filtering remains enforced before change snapshots are returned.
- Existing pending local mutations and stale-version conflict handling remain authoritative.
- An unchanged account returns bounded metadata with empty entity arrays rather than historical business rows.
