# SAFA production architecture

This document is the canonical implementation contract for the production Android client and Laravel backend. Code, tests and future changes must not introduce a second competing implementation without an explicit migration plan.

## Android data and offline model

- Laravel/MySQL is authoritative for accepted server state and server revisions.
- Android is local-first and keeps an encrypted durable cache plus mutation outbox in `LocalFirstStore` (`safa_local.db`).
- `AppRepository` reads/writes business records through `LocalFirstStore`; WorkManager replays the durable outbox.
- Pending mutations survive process restart and network loss. A sync item is claimed atomically before upload and stale claimed items are recoverable.
- Authentication/account changes are lifecycle boundaries: account-scoped local data and queued mutations must not cross to another authenticated account.
- Normal UI collection reads exclude soft-deleted records. Sync/reconciliation endpoints may carry tombstones/revision metadata explicitly.

## REST API versioning

The canonical mobile contract is URI-versioned under `/api/v1/...`.

- New Android builds use `/api/v1` for business and sync calls.
- Existing unversioned `/api/...` routes are compatibility routes during migration and must preserve the same authorization/business rules.
- `/api/v1/{path}` may proxy compatibility operations only while both versions have automated parity coverage.
- Breaking request/response changes require a new version rather than silently changing an installed client's contract.
- Authentication, account context and sync payload changes are versioned as part of the same compatibility policy.

A compatibility route may be deprecated only after all supported Android releases have moved to the replacement contract. Removal must be announced in release notes and covered by contract tests before sunset.

## GraphQL compatibility surface

GraphQL is a read-only compatibility API. Business writes use the canonical versioned REST API under `/api/v1`.

- Collection reads default to `limit=100` and are clamped to a maximum of `250` records per root field.
- `offset` defaults to `0`, must be non-negative, and is bounded to `1,000,000`.
- Results use stable ascending `id` ordering.
- Normal reads exclude soft-deleted rows and are always restricted to the authorized active account.
- Invalid pagination returns a field error rather than loading an unbounded collection.
- The custom query document is capped at 32 KiB and a single request may contain at most 20 root fields.
- GraphQL mutations return `410 GRAPHQL_MUTATIONS_DEPRECATED`; clients must migrate writes to `/api/v1`.

For high-volume or rapidly changing mobile synchronization, use the versioned REST/sync pagination contract rather than GraphQL offset pagination.

## JSON serialization

Android REST JSON uses **Moshi** only:

- Retrofit installs `MoshiConverterFactory`.
- API DTOs use `@JsonClass(generateAdapter = true)` and `@Json` names where wire names differ.
- KSP generates Moshi adapters.
- Unknown server fields must be tolerated for forward compatibility.
- DTO defaults are explicit; nullable wire fields stay nullable.
- Do not introduce a second Retrofit JSON converter such as kotlinx.serialization alongside Moshi.
- Financial decimal values must be represented by the canonical money contract rather than relying on binary floating-point equality.

## Account context and authorization

- The active account must always be explicit and server-authorized.
- `X-SAFA-ACCOUNT-ID`, authenticated session/token context and controller/domain authorization must identify the same account.
- Foreign business IDs must be validated against that account before mutation.
- No API resolver may fall back to `Account::first()` or database ordering for an ambiguous user.

## API rate limiting

The named Laravel `api` limiter is the canonical general REST/GraphQL limiter. Identity must not use the public Android API key alone because that key is shared by installations. The bucket identity combines the client key with authenticated user/session/device identity, falling back to the request IP when no stronger identity exists.

Authentication-sensitive routes may use stricter endpoint-specific limits in addition to the general policy.

## CI and release gates

- `backend-ci.yml` runs PHP syntax checks and the complete Laravel test suite on backend changes.
- `android-ci.yml` runs Android unit tests, lint, a minified release build and emulator-backed instrumentation tests.
- Third-party GitHub Actions are pinned to immutable commit SHAs.
- Production deployment is manual and must pass read-only HTTPS smoke verification after file synchronization.
- A production deployment is not considered successful until the live health/private-surface checks pass.

## Dependency hygiene

Every backend/platform dependency must correspond to an implemented feature. Before adding or retaining a platform SDK, verify a production source reference exists and document the feature requiring it. Remove stale dependencies, plugins, ProGuard rules and generated/template files during architecture migrations.
