# SAFA Backend

The `backend/` directory contains the Laravel API and private web layer for **SAFA**, the Android-first, account-scoped financial management system.

## Production

- Website/service host: `https://safa.masarax.com`
- API base: `https://safa.masarax.com/api`
- Unauthenticated health endpoint: `GET /api/auth/health`
- Browser root `/` is intentionally private and returns `404 {"status":"not_found"}`.
- Production secrets belong in the server environment only. Do not commit `.env` or API secrets.

## Main responsibilities

- Mobile authentication with mobile number + PIN
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
- Protected installation/update/database maintenance controls

## Runtime

Production uses MySQL. Automated CI uses an isolated SQLite database.

Required production environment is documented in [`backend/.env.example`](.env.example). The real production `.env` must be created and maintained outside Git.

## Local setup

```bash
cd backend
composer install
cp .env.example .env
php artisan key:generate
php artisan migrate
```

Configure the database and server-only secrets in `.env` before running the application.

## Testing

For a deterministic local test environment:

```bash
cp .env.testing .env
php artisan migrate:fresh --force
php artisan test
```

The CI workflow uses the same repository-provided `.env.testing` fixture. A production `.env` is never required in CI.

## Useful commands

```bash
php artisan migrate
php artisan migrate:fresh --force
php artisan test
php artisan safa:provision-admin
```

Use `migrate:fresh` only against a disposable development/test database.

## Security rules

1. Never commit passwords, PINs, JWT secrets, refresh tokens, private API secrets or production `.env` files.
2. The Android API key is a client identifier; the private API secret remains server-side.
3. Every business operation must resolve an explicit account context.
4. Account sharing must be checked against the requested account, not merely the user's identity.
5. Financial relationships must remain account-scoped.
6. Money values must use decimal-safe persistence and validation rather than binary floating-point as the authoritative representation.
7. Every security, authorization or synchronization regression should have a corresponding automated test.

## Deployment

Production deployment is performed manually through GitHub Actions. The deployment workflow runs the same deterministic Laravel test environment before production Composer installation and cPanel synchronization when the test gate is enabled.

Deployment is blocked when mandatory tests fail. After synchronization, the workflow checks:

```text
GET https://safa.masarax.com/api/auth/health
```

and requires the response to report `status=ok` and `service=SAFA API`.

The cPanel document root must expose only the intended Laravel public entry point (`backend/public` or the equivalent hosting layout). Application source, `.env`, tests and private runtime files must not be web-accessible.

## Architecture

```text
Android / other trusted client
          |
          | HTTPS JSON API
          v
Laravel 13 API
          |
          +-- Authentication/session/device security
          +-- Account authorization/sharing
          +-- Financial domain validation
          +-- Sync/reconciliation/idempotency
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
