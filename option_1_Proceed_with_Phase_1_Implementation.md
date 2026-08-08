Proceed with Phase 1 implementation, BUT before applying the Room v3 → v4 migration, fix one critical migration-safety issue identified in your verification report.

Your proposed migration currently uses:

```sql
syncStatus INTEGER NOT NULL DEFAULT 1
```

where `1 = SYNCED`.

This is NOT safe for existing local records because some existing Room records may have never been uploaded to the server.

Therefore:

1. Do NOT blindly mark all existing records as `SYNCED`.

2. Determine the safest legacy-record migration strategy from the actual existing code and current data model.

3. Any existing local record that has no confirmed server acknowledgement / serverId must remain eligible for synchronization.

4. The migration must preserve all existing local financial/business records.

5. After migration, the state must logically satisfy:

```text
confirmed server record
    → SYNCED
    → valid serverId

not confirmed on server
    → PENDING_CREATE
    → serverId = 0/null
```

Use the project's actual existing semantics to determine how a previously synced record can be distinguished from a never-synced record. If the current schema cannot reliably distinguish them, do NOT invent a false SYNCED state. Instead, design the safest backward-compatible approach and explain it before implementation.

Also verify that:

```text
serverId = 0/null
```

can never be interpreted as a valid server primary key.

After implementing Phase 1:

* update all 7 Room entities
* implement the safe v3 → v4 migration
* remove fallbackToDestructiveMigration()
* add the required DAO sync-state queries
* ensure existing local data is preserved
* compile the Android project
* run relevant tests
* verify the generated Room schema

Do NOT proceed to Backend SyncController changes yet.

Phase 1 must be completed and verified independently first.

At the end, provide:

1. Files changed
2. Exact migration logic
3. Sync state definitions
4. DAO changes
5. Existing-data preservation strategy
6. Build/test results
7. Any remaining risks

Only after Phase 1 is verified should we proceed to Phase 2.
