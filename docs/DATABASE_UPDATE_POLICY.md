# SAFA database installation and live-update policy

This is the production contract for first-time database initialization and all later schema/data updates.

## First installation

1. Configure the server database connection through the deployment environment.
2. Run forward migrations against the empty database:
   `php artisan migrate --force`
3. Run the production-safe database seeder interactively:
   `php artisan db:seed`
4. When no active SuperAdmin exists, the seeder prompts on the server console for the first SuperAdmin name, mobile number, email and matching 6-digit PIN.
5. No first-admin identity or credential is read from `.env`, committed source code, a public installer, a maintenance key, or a web bootstrap endpoint.
6. Programmatic/non-interactive seeding never invents a default administrator.

`migrate:fresh`, `migrate:refresh`, reset, rollback and wipe commands are rebuild/destructive tools and are not part of the production installation or update procedure for an existing SAFA database.

## Ongoing live updates

- Application files may be deployed while the existing database remains intact.
- Pending migrations are the source of truth for whether a database update is required.
- Login remains reachable while an update is pending.
- Normal browser application traffic is directed to `/update` while pending migrations exist; API clients receive machine-readable `503 update_required` responses.
- `/update` is authenticated. Only an activated SuperAdmin receives the execution control.
- `Update Database` acquires the application update lock, validates every pending migration against the production migration safety contract, runs forward `migrate --force`, runs the approved idempotent `ReleaseDataUpdateSeeder`, clears application caches, verifies no migrations remain, and records the operation in application logs.
- Installer, guest migration/seed and maintenance-key write paths remain permanently retired.

## Non-destructive migration contract

Normal one-click production updates use an **expand / backfill / compatibility** model:

- Prefer adding tables, nullable/defaulted columns and new indexes/constraints without removing existing data.
- Backfill or normalize existing rows with explicit bounded/idempotent update logic.
- Validate existing values before a type/precision change; abort before schema mutation when values do not satisfy the new contract.
- Keep deployed application code compatible with the pre-update schema long enough for SuperAdmin login and `/update` to remain usable.
- Do not place table drops, column drops, table/column renames, truncation, row deletion, raw destructive DDL, or equivalent cleanup in a migration `up()` method used by the live updater.
- Rollback-only `down()` methods are outside the one-click live-update path and may reverse additive changes for development/recovery tooling.
- Destructive cleanup requires a separately reviewed maintenance plan and must never be silently included in the normal `/update` button.

## Enforcement

`App\Support\ProductionMigrationSafety` inspects only each pending migration's `up()` body. It blocks known destructive schema/data operations before Laravel's migration command is invoked. CI applies the same analyzer to every committed production migration and includes representative destructive/rollback regression cases.

This guard supplements migration review; it does not make arbitrary SQL safe and it is not a substitute for operational database backups.
