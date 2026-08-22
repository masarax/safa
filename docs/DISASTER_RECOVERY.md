# SAFA Disaster Recovery Runbook

This runbook is the production recovery contract for the SAFA Laravel/MySQL service. It contains no provider credentials or backup contents. Secrets, MySQL credentials and encryption keys must live outside the repository and outside the web root.

## Recovery objectives and ownership

SAFA stores financial/business records. The production objectives are:

- **RPO: 15 minutes.** At most 15 minutes of committed database/file changes may be lost in a site-level disaster.
- **RTO: 4 hours.** A clean recovery environment must be restored, verified and ready for controlled cutover within four hours of declaring a disaster.
- **Owner:** the production operator/on-call engineer owns backup health and the restore procedure. A second authorized operator acts as incident commander for destructive/cutover approval.
- **Drill cadence:** the repository runs an isolated MySQL restore/PITR drill weekly and for DR changes. Production operators must perform a provider/off-host restore drill at least quarterly and retain its evidence for 12 months.

## Authoritative production topology

The backend is deployed by `.github/workflows/deploy.yml` to the cPanel-hosted Laravel runtime. The deployed `.env` selects the authoritative MySQL instance. The database is the source of truth for accounts, users, customers, suppliers, wallet state, transactions, system settings and sync metadata.

The only durable application file data currently created at runtime is account branding under `public/storage/logos`. Deployment deliberately excludes that directory, so releases do not overwrite uploaded logos. Cache, sessions, rendered views and application logs are operational/transient and are not recovery sources.

The authoritative backup mechanism is therefore:

1. a nightly encrypted logical MySQL full backup produced by `backend/bin/mysql-full-backup.sh`;
2. encrypted MySQL binary-log snapshots every five minutes produced by `backend/bin/mysql-binlog-archive.sh`;
3. encrypted `public/storage/logos` snapshots every five minutes produced by `backend/bin/logo-assets-backup.sh`;
4. independent checksum/freshness monitoring every five minutes with `backend/bin/verify-backup-freshness.sh`;
5. all encrypted artifacts stored outside the cPanel/application failure domain through an operator-provisioned off-host mount or storage gateway.

If the hosting provider offers native managed snapshots/PITR, those are defense in depth. They do not replace this contract unless the operator has documented an equivalent or stronger RPO/RTO, off-host retention, checksum/restore evidence and external monitoring.

## Retention and encryption

- Full database backups: nightly, retained **35 days**.
- Binary logs: archived every **5 minutes**, retained **35 days** so every retained full backup has replay coverage.
- Logo snapshots: checked every **5 minutes**, retained **35 days** when content changes; unchanged snapshots are reused and their freshness heartbeat is renewed.
- Restore-drill evidence: GitHub Actions retains CI evidence for 90 days; production quarterly drill evidence must be retained for 12 months outside Git.
- Artifacts are encrypted with AES-256-CBC + PBKDF2 using OpenSSL. The encryption key file must be readable only by the backup operator account (`chmod 600`) and must not be stored in Git, the public web tree, the backup directory itself, or the same credential store as the off-host backup account.
- Every encrypted artifact is SHA-256 verified before its success heartbeat is written.

The off-host destination must be a separate failure domain: a mounted/synchronized object-storage gateway, backup host, or provider backup target that does not disappear when the primary cPanel account/filesystem is lost. `SAFA_BACKUP_OFFHOST_ACK=I_UNDERSTAND_THIS_MUST_BE_OFF_HOST` is an explicit operator assertion after that topology is verified.

## One-time production setup

Create a protected MySQL client option file outside the web root, for example `$HOME/.safa-mysql-backup.cnf`:

```ini
[client]
host=MYSQL_HOST
port=3306
user=BACKUP_USER
password=BACKUP_PASSWORD
ssl-mode=REQUIRED
```

Grant the backup principal only the privileges required for consistent logical backup and remote binary-log reading. Do not reuse the application account if the provider supports a dedicated backup principal.

Create a random high-entropy encryption key outside the web root and set mode `600`. Create a protected shell environment file, also mode `600`, with deployment-specific paths. The following values are examples of variable names only; do not commit their values:

```bash
export SAFA_APP_ROOT=/absolute/path/to/deployed/backend
export SAFA_DB_NAME=production_database_name
export SAFA_MYSQL_DEFAULTS_FILE=/protected/path/.safa-mysql-backup.cnf
export SAFA_BACKUP_ENCRYPTION_KEY_FILE=/protected/path/.safa-backup.key
export SAFA_BACKUP_DESTINATION=/mounted/off-host/safa
export SAFA_BACKUP_OFFHOST_ACK=I_UNDERSTAND_THIS_MUST_BE_OFF_HOST
export SAFA_FULL_BACKUP_STATUS_FILE="$SAFA_APP_ROOT/storage/app/dr/latest-full.json"
export SAFA_BINLOG_BACKUP_STATUS_FILE="$SAFA_APP_ROOT/storage/app/dr/latest-binlog.json"
export SAFA_ASSET_BACKUP_STATUS_FILE="$SAFA_APP_ROOT/storage/app/dr/latest-assets.json"
export SAFA_LOGO_SOURCE_DIR="$SAFA_APP_ROOT/public/storage/logos"
export SAFA_BACKUP_ALERT_WEBHOOK=
```

The MySQL server must have binary logging enabled and retained long enough for the five-minute archive worker to collect each log. Verify `SHOW BINARY LOGS` succeeds with the backup principal before enabling the schedule.

## cPanel cron schedule

Use cPanel Cron Jobs (or the provider scheduler) with a protected environment file. Replace `/protected/path/.safa-dr.env` only on the host, never in Git.

```cron
10 2 * * * . /protected/path/.safa-dr.env && cd "$SAFA_APP_ROOT" && bash bin/mysql-full-backup.sh
*/5 * * * * . /protected/path/.safa-dr.env && cd "$SAFA_APP_ROOT" && bash bin/mysql-binlog-archive.sh
*/5 * * * * . /protected/path/.safa-dr.env && cd "$SAFA_APP_ROOT" && bash bin/logo-assets-backup.sh
2-59/5 * * * * . /protected/path/.safa-dr.env && cd "$SAFA_APP_ROOT" && bash bin/verify-backup-freshness.sh
```

The monitor exits non-zero on missing, stale or checksum-invalid artifacts. Configure cPanel cron failure mail and/or `SAFA_BACKUP_ALERT_WEBHOOK` to a monitored external destination. The public `GET /api/auth/backup-health` endpoint exposes only freshness booleans/ages and can be polled by an external uptime monitor. It never exposes credentials, backup paths or contents.

After the first verified full, binlog and logo jobs, set these production Laravel variables and reload configuration:

```dotenv
SAFA_BACKUP_STATUS_REQUIRED=true
SAFA_FULL_BACKUP_STATUS_FILE=/absolute/path/to/storage/app/dr/latest-full.json
SAFA_BINLOG_BACKUP_STATUS_FILE=/absolute/path/to/storage/app/dr/latest-binlog.json
SAFA_ASSET_BACKUP_STATUS_FILE=/absolute/path/to/storage/app/dr/latest-assets.json
SAFA_FULL_BACKUP_MAX_AGE_SECONDS=93600
SAFA_BINLOG_BACKUP_MAX_AGE_SECONDS=900
SAFA_ASSET_BACKUP_MAX_AGE_SECONDS=900
```

An external monitor must alert on HTTP 503 or any false check from `/api/auth/backup-health`. The 26-hour full-backup threshold allows a small scheduling window while still detecting a missed nightly run. Binlog/logo status becomes degraded after 15 minutes, matching the RPO.

## Full restore and point-in-time recovery

Never restore directly over production. Recovery is always built in an isolated MySQL instance/database first.

1. Declare the incident and appoint an incident commander. Stop or isolate application writes if the source may still be serving traffic.
2. Record the desired recovery point in the MySQL server timezone. For operator mistakes, choose the final safe instant/position immediately before the bad transaction. For infrastructure loss, choose the newest verified archived point.
3. Provision a clean recovery MySQL instance with the same major version and required SQL mode. Restore application code at the intended release SHA separately.
4. Copy the chosen encrypted full backup, its manifest/checksum, required encrypted binlogs and the encryption key into the protected recovery environment. Never copy them into the web root.
5. Set recovery-only variables. The source and recovery database names must differ:

```bash
export SAFA_MYSQL_DEFAULTS_FILE=/protected/recovery/mysql.cnf
export SAFA_BACKUP_ENCRYPTION_KEY_FILE=/protected/recovery/backup.key
export SAFA_RESTORE_FULL_MANIFEST=/off-host/safa/full/CHOSEN.sql.gz.enc.manifest.json
export SAFA_BINLOG_ARCHIVE_DIR=/off-host/safa/binlog
export SAFA_SOURCE_DB_NAME=production_database_name
export SAFA_RECOVERY_DB_NAME=safa_recovery
export SAFA_PITR_STOP_DATETIME='YYYY-MM-DD HH:MM:SS'
export SAFA_DR_CONFIRM=RESTORE_TO_EMPTY_RECOVERY_DATABASE
bash bin/mysql-restore-pitr.sh
```

For exact position recovery, use `SAFA_PITR_STOP_LOG_FILE` and `SAFA_PITR_STOP_LOG_POS` instead of `SAFA_PITR_STOP_DATETIME`. The restore helper verifies the encrypted full backup checksum, refuses a source-named/non-empty target, restores the full snapshot, then replays retained logs from the full-backup coordinate with `mysqlbinlog --rewrite-db` into the recovery database.

6. Restore the newest verified logo snapshot whose completion is at or after the chosen DB point. Logo filenames are immutable, so a later snapshot is safe: extra unreferenced files do not change financial state, while every DB-referenced logo must be present.

```bash
export SAFA_RESTORE_ASSET_ARTIFACT=/off-host/safa/assets/CHOSEN.tar.gz.enc
export SAFA_RESTORE_ASSET_SHA256=SHA256_FROM_STATUS_OR_CHECKSUM
export SAFA_BACKUP_ENCRYPTION_KEY_FILE=/protected/recovery/backup.key
export SAFA_RECOVERY_LOGO_DIR=/absolute/recovery/public/storage/logos
export SAFA_DR_CONFIRM=RESTORE_TO_EMPTY_RECOVERY_DIRECTORY
bash bin/logo-assets-restore.sh
```

7. Run the integrity checklist below. Do not cut over because the restore command merely exited zero.
8. Record duration, chosen coordinates, source full-backup checksum, verification results and operator approvals. No customer data, credentials, PINs or tokens belong in the drill record.
9. Cut over only after incident-commander approval. Rotate any credentials implicated in the incident, then re-enable backup monitoring against the new primary.

## Recovery integrity checklist

The clean recovery environment must verify at minimum:

- an account row and its owner exist;
- expected customer and supplier counts/spot checks are present and account-scoped;
- wallet ledgers/batches reconcile and no remaining balance exceeds initial balance;
- transactions retain their customer/supplier/wallet foreign-key relationships and exact fixed-point amounts;
- account `system_settings` rows and uniqueness are present;
- required migrations and runtime schema health pass;
- `public/storage/logos` contains every local logo path referenced by `system_settings`;
- login/auth sessions may be deliberately revoked during incident response, but authoritative users/roles must remain intact.

The repository workflow `.github/workflows/dr-restore-drill.yml` performs a clean MySQL full-backup restore, replays binary logs to a chosen pre-corruption position, verifies account/customer/supplier/wallet/transaction/settings integrity and records `recovery_duration_seconds`. A green workflow is required for DR changes; the quarterly production drill additionally proves provider credentials, off-host storage, encryption key access and cPanel scheduling.

## Destructive-action safeguards

- Never use `migrate:fresh`, `DROP DATABASE`, `TRUNCATE` or the restore helper against production.
- The restore helper requires a different source/target database name, an empty target, and the exact `SAFA_DR_CONFIRM` token.
- The asset restore helper requires an empty recovery directory and a different explicit confirmation token.
- Two authorized operators must approve production cutover after integrity checks.
- Preserve the failed/compromised source read-only until incident analysis and regulatory/business retention requirements are satisfied.
- Before schema releases or other risky production changes, verify a fresh full backup, current binlog/logo heartbeats and a green external backup-health monitor. Migration safety is not a substitute for recoverability.
