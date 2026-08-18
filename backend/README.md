# SAFA Backend

The `backend/` directory contains the Laravel API and private web layer for **SAFA**, the Android-first, account-scoped financial management system.

## Production

- Website/service host: `https://safa.masarax.com`
- API base: `https://safa.masarax.com/api`
- Unauthenticated health endpoint: `GET /api/auth/health`
- Browser root `/` redirects guests to the secure login page and authenticated users to the application.
- Production secrets belong in the server environment only. Do not commit `.env`, credentials, signing material or private API secrets.

## Main responsibilities

- Mobile/email authentication with PIN/password
- JWT access tokens plus refresh/session/device/fingerprint controls
- Account ownership and account-sharing authorization
- Customer and supplier management
- Remittance/transaction management
- Wallet ledgers and batches
- Supplier deposits
- Expense/income records
- Offline-first Android synchronization
- Sync reconciliation and idempotent mutation handling
- Audit and security controls
- Authenticated SuperAdmin database-update lifecycle

## Runtime

Production uses MySQL. Automated backend CI uses an isolated SQLite database.

Required production environment values are documented in [`backend/.env.example`](.env.example). The real production `.env` must be created and maintained outside Git. First-SuperAdmin identity or credentials are **not** configuration values and must never be stored in `.env`.

## Local setup

```bash
cd backend
composer install
cp .env.example .env
php artisan key:generate
php artisan migrate
php artisan db:seed
```

Configure the database and server-only secrets in `.env` before running the application.

## First production SuperAdmin

First-user provisioning is deliberately separated from ongoing application/database updates.

For a new production database:

```bash
php artisan migrate --force
php artisan db:seed
```

When no active SuperAdmin exists and `db:seed` is run from an interactive server console, SAFA prompts for the first SuperAdmin's name, mobile number, email address and matching 6-digit PIN. The credential is hashed before persistence.

The first SuperAdmin is **not** created from:

- `.env` identity/PIN variables;
- a default or hard-coded password;
- `/index` or `/install`;
- a maintenance/setup key;
- FTP deployment;
- a public HTTP database endpoint.

Non-interactive/programmatic seeding never invents a default administrator. If an inactive SuperAdmin already exists, the seeder fails closed instead of silently creating a second privileged identity.

`migrate:fresh`, `migrate:refresh`, reset, rollback and wipe commands are not production recovery/update procedures for an existing database because they can destroy or reverse persisted data.

## Live database updates

Production file deployment and production database updates are separate responsibilities.

After application files are deployed, pending Laravel migrations indicate that a database update is required. Login remains reachable so an existing activated SuperAdmin can authenticate. While migrations remain pending:

- normal browser application traffic is directed to `/update`;
- API clients receive a machine-readable `503 update_required` response;
- guests and lower roles cannot execute the update.

The authenticated SuperAdmin uses **Update Database** on `/update`. The server then:

1. acquires the SAFA database-update lock;
2. inspects pending migration `up()` methods against the non-destructive production migration policy;
3. runs forward `migrate --force` only when the pending migrations pass that guard;
4. runs the approved idempotent release-data updater;
5. clears application caches;
6. verifies no migrations remain pending;
7. records the update result in application logs.

The normal one-click update path does not expose `migrate:fresh`, rollback, reset, wipe, table/column drops, truncation or other destructive maintenance operations.

See:

- [`../docs/DATABASE_UPDATE_POLICY.md`](../docs/DATABASE_UPDATE_POLICY.md)
- [`../docs/PRODUCTION_ARCHITECTURE.md`](../docs/PRODUCTION_ARCHITECTURE.md)

## Testing

For a deterministic disposable local/test database:

```bash
cp .env.testing .env
php artisan migrate:fresh --force
php artisan test
```

`migrate:fresh` is acceptable here only because the database is disposable. Production data must use the forward-update policy above.

## Useful commands

```bash
php artisan migrate
php artisan db:seed
php artisan test
```

For an existing production database, use the authenticated SuperAdmin `/update` workflow rather than manually coupling schema changes to deployment.

## Security rules

1. Never commit passwords, PINs, JWT secrets, refresh tokens, private API secrets or production `.env` files.
2. The Android API key is a client identifier; the private API secret remains server-side.
3. Every business operation must resolve an explicit account context.
4. Account sharing must be checked against the requested account, not merely the user's identity.
5. Financial relationships must remain account-scoped.
6. Money values must use decimal-safe persistence and validation rather than binary floating-point as the authoritative representation.
7. First privileged identity creation must remain server-console-only and collision-safe.
8. Live database updates must remain activated-SuperAdmin-only, locked, audited and non-destructive.
9. Every security, authorization, synchronization or database-update regression should have a corresponding automated test.

## Deployment

Production backend deployment is manual through GitHub Actions workflow **Deploy Laravel Backend to cPanel**.

The deployment workflow is intentionally file-only:

1. checks out `main`;
2. prepares production Composer dependencies;
3. stamps the exact deployed Git commit;
4. synchronizes `backend/` to cPanel over FTP.

It preserves server-owned/runtime state including `.env`, sessions, logs, cache state and uploaded logos. It does **not** run production migrations, seeders, cache mutations or HTTP-triggered database maintenance.

If the deployed release includes a pending database migration, the web application exposes the authenticated SuperAdmin `/update` lifecycle described above. Destructive migration authoring is blocked by the production migration safety contract before the normal one-click updater invokes Laravel migration execution.

The cPanel document root must expose only the intended Laravel public entry point (`backend/public` or the equivalent hosting layout). Application source, `.env`, tests and private runtime files must not be web-accessible.

## Architecture

```text
Android / browser / trusted client
          |
          | HTTPS
          v
Laravel 13
          |
          +-- Authentication/session/device security
          +-- Account authorization/sharing
          +-- Financial domain validation
          +-- Sync/reconciliation/idempotency
          +-- SuperAdmin-only database update lifecycle
          +-- Audit/security middleware
          |
          v
       MySQL
```

The Android application currently uses a custom `SQLiteOpenHelper` local-first store with encrypted local payloads, durable outbox state, retry metadata, server versions and mutation metadata. It is **not documented as Room or SQLCipher** because the current implementation does not use those technologies.

## Current status

This project is under active development. Documentation describes the current implementation only. Planned features or future migrations must not be presented as production functionality.

## License

SAFA is private and proprietary financial software. All rights reserved.
