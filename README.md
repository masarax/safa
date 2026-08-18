# SAFA (সাফা) — Multi-Account, Multi-Currency Financial Management

SAFA is an offline-first financial/account-management system with an Android client and Laravel 13 backend. The production architecture is API-first, account-scoped and designed to keep financial data authoritative on the server while Android remains usable offline.

## Production

- Website/service host: `https://safa.masarax.com`
- Canonical mobile API base: `https://safa.masarax.com/api/v1`
- Compatibility API base: `https://safa.masarax.com/api`
- Health endpoint: `GET https://safa.masarax.com/api/auth/health`
- Browser root `/` sends guests to the secure login page and authenticated users to the web application.

## Current Architecture

```text
Android (Kotlin / Jetpack Compose)
        |
        +-- Encrypted LocalFirstStore (SQLiteOpenHelper + durable outbox)
        +-- WorkManager reconciliation/retry
        |
        | HTTPS versioned REST API + Moshi
        | JWT + device/session/fingerprint verification
        v
Laravel 13 Backend / Web App
        |
        +-- Account authorization / sharing
        +-- Sync reconciliation / idempotency
        +-- Financial domain validation
        +-- SuperAdmin-only database update lifecycle
        +-- Audit/security middleware
        |
        v
MySQL
```

### Android local storage

The production Android implementation uses a custom `SQLiteOpenHelper` local-first store (`LocalFirstStore`). Payloads are encrypted before being written to the local database and the store contains a durable outbox, retry state, server versions and mutation metadata.

It is **not** SQLCipher/Room. Documentation, dependencies and shrink rules must not describe Room/SQLCipher as part of the production persistence architecture unless a future migration explicitly reintroduces them.

### Authentication

The backend uses a custom JWT-based access token plus refresh/session/device/fingerprint security model. Auth-session token values are encrypted at rest and indexed using SHA-256 hashes for lookup.

The browser application uses protected Laravel sessions and the same server-side user/role/account authorization model. The Android client stores its authentication credentials using Keystore-backed encrypted storage.

### Database

The supported production configuration is MySQL, as reflected by `backend/.env.example` and the production migrations. SQLite is used by CI for isolated automated backend tests where supported.

First privileged-user provisioning and later database upgrades are intentionally separate:

- **First SuperAdmin:** interactive server-console `php artisan db:seed` after forward migrations. No first-admin identity/PIN exists in `.env`, source code, a public installer or a second provisioning command.
- **Later updates:** pending migrations are handled through authenticated activated-SuperAdmin `/update`, with a runtime non-destructive migration guard, update lock, approved idempotent release-data updater, cache clearing and post-update verification.

See [`docs/DATABASE_UPDATE_POLICY.md`](docs/DATABASE_UPDATE_POLICY.md) and [`docs/PRODUCTION_ARCHITECTURE.md`](docs/PRODUCTION_ARCHITECTURE.md).

## Core Modules

- Multi-account financial data isolation
- Customer management
- Supplier management
- Remittance/transaction management
- Wallet ledgers and batches
- Supplier deposits
- Expenses/incomes
- Offline-first Android synchronization
- Idempotent sync mutations and conflict reconciliation
- Account sharing with account-specific permission overrides
- JWT/device/session/fingerprint authentication
- Audit/security middleware
- SuperAdmin-only live database update lifecycle

## Security Model

Every authenticated business request should resolve an explicit account context:

```text
Authenticated User
      |
      v
Requested Account
      |
      +--> Owner? -------- yes --> Authorized
      |
      +--> Exact Share? --- yes --> Authorized with effective permissions
      |
      +--> otherwise --------------> 403
```

A share for one account never authorizes another account owned by the same user.

Financial values are persisted using MySQL `DECIMAL` columns. Android financial calculations must follow the repository's canonical exact-money contract and must not rely on binary floating-point equality.

## Local Development

### Prerequisites

- Android Studio
- JDK 17
- PHP 8.3+
- Composer 2.x
- MySQL 8+ for normal backend development

### Android

From the repository root:

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew connectedDebugAndroidTest
./gradlew assembleRelease
```

Do not put private API secrets in the APK. The mobile API key is a public client identifier; server secrets remain server-side.

### Laravel

```bash
cd backend
composer install
cp .env.example .env
php artisan key:generate
php artisan migrate
php artisan db:seed
php artisan test
```

When `db:seed` is run interactively and no active SuperAdmin exists, the first SuperAdmin is created by the canonical interactive seeder after validating name, mobile, email and matching 6-digit PIN input. Non-interactive seeding never invents a privileged user.

### Fresh development database

For a clean **disposable** development schema:

```bash
php artisan migrate:fresh --seed
```

`migrate:fresh`, refresh, reset, rollback and wipe are not production update/recovery procedures for an existing SAFA database.

## CI/CD

Production-readiness CI runs automatically for relevant pull requests and pushes to `main`:

- Laravel syntax checks and the complete backend test suite.
- Android unit tests and lint.
- A minified/resource-shrunk release build.
- Emulator-backed Android instrumentation/runtime tests where configured by the Android production CI workflow.

Production backend deployment is a separate manual workflow: **Deploy Laravel Backend to cPanel**. It intentionally performs file deployment only:

1. checks out `main`;
2. prepares production Composer dependencies;
3. stamps the exact deployed Git commit;
4. synchronizes `backend/` to cPanel over FTP while preserving production-owned runtime state.

The FTP workflow does **not** run migrations, seeders, cache mutations or HTTP-triggered database maintenance.

For a schema-changing release, pending migrations keep login available but place normal browser application traffic in the update-required flow. API clients receive machine-readable `503 update_required`. An authenticated activated SuperAdmin opens `/update` and uses **Update Database**. Before Laravel migration execution, SAFA validates pending migration `up()` methods against the production non-destructive migration policy. The updater then runs forward migrations, the approved idempotent release-data updater, cache clearing and a final pending-migration verification under an application lock.

Third-party GitHub Actions should remain pinned according to the repository's CI security policy. Production signing credentials and deployment credentials remain GitHub secrets and are not exposed to pull-request code.

### Signed Android APK release

Production APK publication uses `.github/workflows/release-apk.yml` (`Build Signed Android APK`). The workflow can be started manually with `workflow_dispatch` or by pushing an intentional release tag matching `v*`. A production-signed APK must only be produced from the exact commit intended for release.

Configure these repository GitHub Actions secrets before triggering the workflow:

- `ANDROID_KEYSTORE_BASE64`: base64-encoded production Android keystore bytes.
- `ANDROID_STORE_PASSWORD`: keystore store password.
- `ANDROID_KEY_ALIAS`: alias of the production signing key in the keystore.
- `ANDROID_KEY_PASSWORD`: password for that signing key.

Never commit the keystore, decoded signing material, passwords, aliases containing sensitive context, or copied secret values to the repository. The workflow fails closed when any required signing secret is missing, decodes the keystore only into the runner's temporary directory with restricted permissions, validates the requested alias, and removes the decoded keystore in an `always()` cleanup step.

Before each release:

1. Update `SAFA_VERSION_CODE` and `SAFA_VERSION_NAME` in `gradle.properties` through the normal issue/branch/PR/CI process. `SAFA_VERSION_CODE` must be greater than the integer recorded in `release/android-last-published-version-code.txt`.
2. Confirm the intended release commit is green in the normal Android CI.
3. Confirm all four production signing secrets above are configured in GitHub Actions without printing or copying their values into logs or issue/PR text.
4. Run **Actions → Build Signed Android APK → Run workflow** against the intended release ref, or push an intentional `v*` release tag that points to that exact commit.
5. Require the release job to pass its test/lint/release-build and `apksigner verify` gates before accepting the artifact.

A successful run uploads one artifact named `safa-signed-apk-<run-id>` for 30 days. It contains:

- the signed release APK;
- `mapping.txt` for the minified release;
- `SHA256SUMS.txt` for the APK;
- `build-identity.txt` containing the exact Git commit SHA and Git ref used by the run.

Before publishing or distributing the APK, download the artifact and verify all of the following:

1. `build-identity.txt` contains the intended full 40-character commit SHA and expected release ref.
2. Put the APK and `SHA256SUMS.txt` in the same directory and run `sha256sum -c SHA256SUMS.txt`; it must report success.
3. Run `apksigner verify --verbose <apk-file>` using Android SDK Build Tools; verification must succeed.
4. Confirm the APK version identity matches the reviewed `SAFA_VERSION_CODE` / `SAFA_VERSION_NAME` for that release.

After the APK is actually published, update `release/android-last-published-version-code.txt` to the published `SAFA_VERSION_CODE` through the normal issue/branch/PR/CI process. Do not advance that baseline for an unpublished or failed release.

## Important Development Rules

1. Never add passwords, PINs, JWT secrets, refresh tokens, first-admin credentials or private API secrets to Git.
2. First SuperAdmin provisioning has one supported source of truth: the interactive production seeder.
3. Never authorize an account using owner identity alone when the requested account is not owned by the current user.
4. Every business table/query must remain account-scoped.
5. Every foreign business relationship must be verified against the active account.
6. Do not use PHP/Android binary floating point as the authoritative representation of money.
7. REST and any supported compatibility API must converge on the same domain validation rules; GraphQL business mutations are deprecated.
8. Production migration `up()` methods must follow the non-destructive expand/backfill compatibility contract; destructive cleanup never belongs in the normal `/update` path.
9. Add a regression test for every security, synchronization or database-update bug that is fixed.
10. Do not introduce an unused backend/platform dependency without an implemented feature and documented production role.

## Tests

Backend:

```bash
cd backend
php artisan test
```

Android:

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew connectedDebugAndroidTest
./gradlew assembleRelease
```

## License

Private & Proprietary Financial Software. All rights reserved.
