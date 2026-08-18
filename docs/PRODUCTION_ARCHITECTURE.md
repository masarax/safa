# SAFA production architecture

This document is the canonical implementation contract for the production Android client and Laravel backend. Code, tests and future changes must not introduce a second competing implementation without an explicit migration plan.

## Android data and offline model

- Laravel/MySQL is authoritative for accepted server state and server revisions.
- Android is local-first and keeps an encrypted durable cache plus mutation outbox in `LocalFirstStore` (`safa_local.db`).
- `AppRepository` reads/writes business records through `LocalFirstStore`; WorkManager replays the durable outbox.
- Pending mutations survive process restart and network loss. A sync item is claimed atomically before upload and stale claimed items are recoverable.
- The local database is intentionally bound to **one active business account namespace at a time**. The binding is stored in durable metadata.
- Switching from account A to account B is blocked while A has pending/processing/failed mutations. A clean switch atomically clears A's business cache, outbox and server-revision state before B is bound.
- Background/foreground sync requires a server-authorized active account; no accountless outbox upload is allowed.
- Authentication/account changes are lifecycle boundaries: account-scoped local data and queued mutations must not cross to another authenticated account.
- Normal UI collection reads exclude soft-deleted records. Sync/reconciliation endpoints may carry tombstones/revision metadata explicitly.
- Server snapshots are revision-checked again at the repository persistence boundary. Older snapshots, equal-version incompatible replays and all snapshots that conflict with a pending local mutation/tombstone are ignored.
- Server timestamps use one canonical parser. Zone-less Laravel timestamps are interpreted as UTC; invalid timestamps never fall back to a fabricated local `now` value.

## Monetary precision contract

SAFA financial persistence and network synchronization use an exact fixed-scale decimal contract aligned with MySQL schema precision:

- SAR monetary amounts: `DECIMAL(15,2)` semantics, **13 integer digits + scale 2**.
- BDT monetary amounts: `DECIMAL(15,2)` semantics, **13 integer digits + scale 2**.
- Exchange rates: `DECIMAL(10,4)` semantics, **6 integer digits + scale 4**.
- Rounding at a required scale boundary: **HALF_UP**.
- Android business calculations use `MoneyMath` (`BigDecimal`) and outgoing REST/sync JSON is canonicalized to fixed-scale decimal strings before transmission.
- Android domain models, ViewModel summaries, calculator results, wallet operations and report calculations use `BigDecimal`; no monetary or rate domain field is a `Float`/`Double` compatibility projection.
- A legacy JSON numeric token is accepted only at the ingestion boundary and is immediately converted to a fixed-scale decimal. Canonical REST/sync output always uses strings such as `"10.00"` and `"32.1235"`.
- Laravel sync input is canonicalized by `MoneyDecimal` without PHP float coercion. Eloquent decimal casts keep database values as fixed-scale decimal strings in API/sync output.
- Mutation identity is computed after decimal canonicalization so numerically equivalent representations converge deterministically.
- Principal amounts, BDT disbursements, balances and rates are non-negative. `sar_collected` alone may be signed to represent returning a customer's advance balance.
- The encrypted Android local store persists canonical decimal strings, including daily operating rates; process restart, outbox replay and repeated sync cannot introduce a binary-float drift.
- Database schema precision is the final storage boundary; values outside supported precision or negative values for non-negative business fields are rejected with validation errors.

## REST API versioning

The canonical and only supported business/mobile contract is URI-versioned REST under `/api/v1/...`.

- New Android builds use `/api/v1` for business and sync calls.
- Existing unversioned `/api/...` routes are compatibility routes during migration and must preserve the same authorization/business rules.
- `/api/v1/{path}` may proxy compatibility operations only while both versions have automated parity coverage.
- Breaking request/response changes require a new version rather than silently changing an installed client's contract.
- Authentication, account context and sync payload changes are versioned as part of the same compatibility policy.

A compatibility route may be deprecated only after all supported Android releases have moved to the replacement contract. Removal must be announced in release notes and covered by contract tests before sunset.

## GraphQL retirement

GraphQL is not a supported SAFA business API. The legacy `/api/graphql` endpoint exists only as a migration signal and returns `410 GRAPHQL_DEPRECATED` with `/api/v1` as the replacement.

- GraphQL performs no business reads.
- GraphQL performs no business mutations.
- No financial calculation, validation, account-scoping or deletion rule is implemented independently in GraphQL.
- Android has no supported GraphQL product flow; new integrations must use versioned REST.
- Removing the endpoint entirely is safe after legacy integrations have completed migration.

This retirement keeps one canonical implementation for account authorization, validation, pagination, deletion semantics, financial rules and synchronization.

## JSON serialization

Android REST JSON uses **Moshi** only:

- Retrofit installs `MoshiConverterFactory`.
- API DTOs use `@JsonClass(generateAdapter = true)` and `@Json` names where wire names differ.
- KSP generates Moshi adapters.
- Unknown server fields must be tolerated for forward compatibility.
- DTO defaults are explicit; nullable wire fields stay nullable.
- Do not introduce a second Retrofit JSON converter such as kotlinx.serialization alongside Moshi.
- Financial decimal values are normalized by the canonical money contract before transmission; binary floating-point equality is never a financial data contract.

## Account context and authorization

- The active account must always be explicit and server-authorized.
- Authenticated users with multiple authorized accounts must explicitly choose one before business sync/data presentation proceeds.
- `X-SAFA-ACCOUNT-ID`, encrypted Android active-account state, authenticated session/token context and controller/domain authorization must identify the same account.
- Foreign business IDs must be validated against that account before mutation.
- No API resolver may fall back to `Account::first()` or database ordering for an ambiguous user.

## Destructive operation confirmation

- User confirmation occurs at the application/domain layer before a destructive request is sent or queued.
- OkHttp interceptors are non-interactive and never block a dispatcher thread waiting for UI.
- An unconfirmed direct `DELETE` is rejected locally before network execution.
- A confirmed offline delete is represented by the durable outbox operation itself and can be replayed by WorkManager after process death without Activity/UI context.
- Server-side confirmation/authorization remains mandatory; client confirmation never weakens account authorization.

## API rate limiting

The named Laravel `api` limiter is the canonical general protected API limiter. Identity must not use the public Android API key alone because that key is shared by installations. The bucket identity combines the client key with authenticated user/session/device identity, falling back to the request IP when no stronger identity exists.

Authentication-sensitive routes may use stricter endpoint-specific limits in addition to the general policy.

## CI and release gates

- `backend-ci.yml` runs PHP syntax checks and the complete Laravel test suite on backend changes.
- `android-ci.yml` runs Android unit tests, lint, a minified release build, emulator-backed instrumentation tests and a minified release runtime launch smoke test.
- Android CI rejects a release `versionCode` that is not greater than the tracked last-published version code.
- Third-party GitHub Actions are pinned to immutable commit SHAs with human-readable version comments.
- Production backend deployment is manual and **file-only**: `deploy.yml` prepares production Composer dependencies, stamps the exact checked-out `main` commit, and synchronizes `backend/` over FTP while preserving production runtime state such as `.env`, sessions, logs, cache state and uploaded logos.
- The FTP workflow does not run migrations, seeders, cache rebuild commands or database writes. Runtime verification is an operational step outside the file-synchronization workflow.
- Pending migrations are the source of truth for database-update-required state. Login remains reachable so an administrator can authenticate; normal browser application traffic is directed to `/update`, while API clients receive a machine-readable `503 update_required` response.
- Only an authenticated, activated **SuperAdmin** can execute `/update`. The one-click update acquires the application update lock, validates pending migration `up()` methods against the non-destructive production migration policy, runs forward `migrate --force`, applies the approved idempotent release-data updater, clears application caches, verifies that no migrations remain, and records the operation in application logs.
- First SuperAdmin provisioning is a separate first-install responsibility and is CLI-only through the interactive production seeder; it is never performed by FTP deployment, `.env` identity credentials, a public installer, or the `/update` flow.
- Production migration `up()` methods must follow the additive/expand-backfill compatibility rules in `docs/DATABASE_UPDATE_POLICY.md`. Destructive cleanup is never part of the normal one-click live database update path.

## Dependency hygiene

Every backend/platform dependency must correspond to an implemented feature. Before adding or retaining a platform SDK, verify a production source reference exists and document the feature requiring it. Remove stale dependencies, plugins, ProGuard rules and generated/template files during architecture migrations.
