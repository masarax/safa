# SAFA (সাফা) — Multi-Account, Multi-Currency Financial Management

SAFA is an offline-first financial/account-management system with an Android client and Laravel 13 API backend. The current development architecture is intentionally API-first and account-scoped.

## Production

- Website/service host: `https://safa.masarax.com`
- Canonical mobile API base: `https://safa.masarax.com/api/v1`
- Compatibility API base: `https://safa.masarax.com/api`
- Health endpoint: `GET https://safa.masarax.com/api/auth/health`
- Browser root `/` is intentionally private and returns `404 {"status":"not_found"}`.

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
Laravel 13 Backend
        |
        +-- Account authorization / sharing
        +-- Sync reconciliation / idempotency
        +-- Financial domain validation
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

The Android client stores its authentication credentials using Keystore-backed encrypted storage.

### Database

The supported production configuration is MySQL, as reflected by `backend/.env.example` and the production-oriented migrations. SQLite is used by CI for isolated automated backend tests where supported.

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
php artisan test
```

Provision the first SuperAdmin explicitly rather than embedding credentials in source code:

```bash
php artisan safa:provision-admin
```

### Fresh development database

For a clean disposable development schema:

```bash
php artisan migrate:fresh
```

Only use `migrate:fresh` against a disposable development/test database.

## CI/CD

Production-readiness CI runs automatically for relevant pull requests and pushes to `main`:

- Laravel syntax checks and the complete backend test suite.
- Android unit tests and lint.
- A real minified/resource-shrunk release APK build using an ephemeral CI-only signing key.
- Emulator-backed Android instrumentation tests, including local-first recovery/conflict coverage.
- Test/build reports uploaded for failure diagnosis.

Production deployment itself remains manual. Deployment first runs the mandatory backend gate, then synchronizes the Laravel backend and performs read-only HTTPS smoke verification of the live health endpoint, private installer/root surface and protected critical routes. The health response verifies the PHP runtime, database connection, required migration columns, configured database-backed cache/session tables, writable Laravel runtime directories and exact deployed commit. A deployment is not considered successful if any smoke check fails.

The FTP workflow intentionally cannot execute migrations or cache commands. For a schema-changing release, use the authenticated cPanel terminal to review pending migrations and run `php artisan migrate --force`, then `php artisan optimize:clear` and `php artisan optimize`. Never use `migrate:fresh` in production. If those explicit operator steps have not been completed, the live readiness response stays degraded and the deployment workflow fails; rerun it only after the safe maintenance step is complete.

Third-party GitHub Actions are pinned to immutable commit SHAs. Production signing credentials and deployment credentials remain GitHub secrets and are not exposed to pull-request code.

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
5. Require the release job to pass unit tests, lint, minified/resource-shrunk `assembleRelease`, and `apksigner verify` before accepting the artifact.

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

1. Never add passwords, PINs, JWT secrets, refresh tokens or private API secrets to Git.
2. Never authorize an account using owner identity alone when the requested account is not owned by the current user.
3. Every business table/query must remain account-scoped.
4. Every foreign business relationship must be verified against the active account.
5. Do not use PHP/Android binary floating point as the authoritative representation of money.
6. REST and any supported compatibility API must converge on the same domain validation rules; GraphQL business mutations are deprecated.
7. Add a regression test for every security or synchronization bug that is fixed.
8. Do not introduce an unused backend/platform dependency without an implemented feature and documented production role.

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
