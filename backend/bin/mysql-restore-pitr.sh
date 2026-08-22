#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

fail() {
  printf 'SAFA recovery failed: %s\n' "$*" >&2
  exit 1
}

for command_name in mysql mysqlbinlog openssl gzip sha256sum php; do
  command -v "$command_name" >/dev/null 2>&1 || fail "required command is unavailable: $command_name"
done

: "${SAFA_MYSQL_DEFAULTS_FILE:?Set SAFA_MYSQL_DEFAULTS_FILE to recovery-environment credentials.}"
: "${SAFA_RESTORE_FULL_MANIFEST:?Set SAFA_RESTORE_FULL_MANIFEST to one verified full-backup manifest.}"
: "${SAFA_BACKUP_ENCRYPTION_KEY_FILE:?Set SAFA_BACKUP_ENCRYPTION_KEY_FILE to the protected recovery key.}"
: "${SAFA_BINLOG_ARCHIVE_DIR:?Set SAFA_BINLOG_ARCHIVE_DIR to the retained encrypted binlog directory.}"
: "${SAFA_SOURCE_DB_NAME:?Set SAFA_SOURCE_DB_NAME to the production database name recorded in the backup.}"
: "${SAFA_RECOVERY_DB_NAME:?Set SAFA_RECOVERY_DB_NAME to an EMPTY, non-production recovery database.}"
: "${SAFA_DR_CONFIRM:?Set SAFA_DR_CONFIRM=RESTORE_TO_EMPTY_RECOVERY_DATABASE after the incident commander verifies the target.}"

[[ "$SAFA_DR_CONFIRM" == 'RESTORE_TO_EMPTY_RECOVERY_DATABASE' ]] || fail 'destructive recovery confirmation token is incorrect'
[[ "$SAFA_SOURCE_DB_NAME" =~ ^[A-Za-z0-9_]+$ ]] || fail 'source database name contains unsupported characters'
[[ "$SAFA_RECOVERY_DB_NAME" =~ ^[A-Za-z0-9_]+$ ]] || fail 'recovery database name contains unsupported characters'
[[ "$SAFA_SOURCE_DB_NAME" != "$SAFA_RECOVERY_DB_NAME" ]] || fail 'refusing to restore into the authoritative source database name'
[[ -r "$SAFA_MYSQL_DEFAULTS_FILE" ]] || fail 'MySQL defaults file is not readable'
[[ -r "$SAFA_BACKUP_ENCRYPTION_KEY_FILE" ]] || fail 'backup encryption key file is not readable'
[[ -r "$SAFA_RESTORE_FULL_MANIFEST" ]] || fail 'full-backup manifest is not readable'
[[ -d "$SAFA_BINLOG_ARCHIVE_DIR" ]] || fail 'binlog archive directory is unavailable'

manifest_values="$(php -r '
try {
    $m = json_decode(file_get_contents($argv[1]), true, 32, JSON_THROW_ON_ERROR);
    foreach (["artifact", "sha256", "source_log_file", "source_log_pos", "database"] as $key) {
        if (!array_key_exists($key, $m)) exit(2);
    }
    if (!preg_match("/^[a-f0-9]{64}$/", strtolower((string) $m["sha256"]))) exit(2);
    echo base64_encode((string) $m["artifact"]), "\t", strtolower((string) $m["sha256"]), "\t", (string) $m["source_log_file"], "\t", (int) $m["source_log_pos"], "\t", (string) $m["database"];
} catch (Throwable) {
    exit(2);
}
' "$SAFA_RESTORE_FULL_MANIFEST")" || fail 'full-backup manifest is malformed'

IFS=$'\t' read -r artifact_b64 expected_sha source_log_file source_log_pos manifest_database <<< "$manifest_values"
artifact="$(printf '%s' "$artifact_b64" | base64 --decode)" || fail 'backup artifact path could not be decoded'
[[ "$manifest_database" == "$SAFA_SOURCE_DB_NAME" ]] || fail 'manifest database does not match SAFA_SOURCE_DB_NAME'
[[ "$source_log_file" =~ ^[A-Za-z0-9._-]+$ && "$source_log_pos" =~ ^[0-9]+$ ]] || fail 'manifest binary-log coordinate is invalid'
[[ -r "$artifact" && -s "$artifact" ]] || fail 'encrypted full backup artifact is missing'
[[ "$(sha256sum "$artifact" | awk '{print $1}')" == "$expected_sha" ]] || fail 'encrypted full backup checksum does not match the manifest'

# The target may exist, but it must contain no tables. This fail-closed check is
# what prevents the recovery helper from becoming a production wipe command.
mysql --defaults-extra-file="$SAFA_MYSQL_DEFAULTS_FILE" -e \
  "CREATE DATABASE IF NOT EXISTS \`$SAFA_RECOVERY_DB_NAME\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
table_count="$(mysql --defaults-extra-file="$SAFA_MYSQL_DEFAULTS_FILE" --batch --skip-column-names -e \
  "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$SAFA_RECOVERY_DB_NAME'")"
[[ "$table_count" == '0' ]] || fail 'recovery database is not empty'

tmp_dir="$(mktemp -d "${TMPDIR:-/tmp}/safa-restore.XXXXXX")"
cleanup() { rm -rf "$tmp_dir"; }
trap cleanup EXIT

full_gzip="$tmp_dir/full.sql.gz"
full_sql="$tmp_dir/full.sql"
openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 \
  -pass "file:$SAFA_BACKUP_ENCRYPTION_KEY_FILE" -in "$artifact" -out "$full_gzip"
gzip -dc "$full_gzip" > "$full_sql"
[[ -s "$full_sql" ]] || fail 'decrypted full backup is empty'
mysql --defaults-extra-file="$SAFA_MYSQL_DEFAULTS_FILE" "$SAFA_RECOVERY_DB_NAME" < "$full_sql"

mapfile -t encrypted_logs < <(find "$SAFA_BINLOG_ARCHIVE_DIR" -maxdepth 1 -type f -name '*.enc' -printf '%f\n' | sort)
(( ${#encrypted_logs[@]} > 0 )) || fail 'no encrypted binlog files are available for PITR'

started=false
stop_seen=false
for encrypted_name in "${encrypted_logs[@]}"; do
  log_name="${encrypted_name%.enc}"
  [[ "$log_name" =~ ^[A-Za-z0-9._-]+$ ]] || fail "unsafe archived binlog filename: $log_name"

  if [[ "$started" == false ]]; then
    [[ "$log_name" == "$source_log_file" ]] || continue
    started=true
  fi

  encrypted_path="$SAFA_BINLOG_ARCHIVE_DIR/$encrypted_name"
  checksum_path="${encrypted_path}.sha256"
  [[ -r "$checksum_path" ]] || fail "missing checksum for archived binlog $log_name"
  (
    cd "$SAFA_BINLOG_ARCHIVE_DIR"
    sha256sum -c "$(basename "$checksum_path")" >/dev/null
  ) || fail "checksum failed for archived binlog $log_name"

  raw_log="$tmp_dir/$log_name"
  openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 \
    -pass "file:$SAFA_BACKUP_ENCRYPTION_KEY_FILE" -in "$encrypted_path" -out "$raw_log"

  mysqlbinlog_args=(
    --database="$SAFA_SOURCE_DB_NAME"
    --rewrite-db="$SAFA_SOURCE_DB_NAME->$SAFA_RECOVERY_DB_NAME"
  )
  if [[ "$log_name" == "$source_log_file" ]]; then
    mysqlbinlog_args+=(--start-position="$source_log_pos")
  fi

  if [[ -n "${SAFA_PITR_STOP_DATETIME:-}" ]]; then
    mysqlbinlog_args+=(--stop-datetime="$SAFA_PITR_STOP_DATETIME")
  elif [[ -n "${SAFA_PITR_STOP_LOG_FILE:-}" || -n "${SAFA_PITR_STOP_LOG_POS:-}" ]]; then
    : "${SAFA_PITR_STOP_LOG_FILE:?Both SAFA_PITR_STOP_LOG_FILE and SAFA_PITR_STOP_LOG_POS are required for position recovery.}"
    : "${SAFA_PITR_STOP_LOG_POS:?Both SAFA_PITR_STOP_LOG_FILE and SAFA_PITR_STOP_LOG_POS are required for position recovery.}"
    [[ "$SAFA_PITR_STOP_LOG_FILE" =~ ^[A-Za-z0-9._-]+$ && "$SAFA_PITR_STOP_LOG_POS" =~ ^[0-9]+$ ]] || fail 'PITR stop coordinate is invalid'
    if [[ "$log_name" == "$SAFA_PITR_STOP_LOG_FILE" ]]; then
      mysqlbinlog_args+=(--stop-position="$SAFA_PITR_STOP_LOG_POS")
      stop_seen=true
    fi
  fi

  mysqlbinlog "${mysqlbinlog_args[@]}" "$raw_log" | \
    mysql --defaults-extra-file="$SAFA_MYSQL_DEFAULTS_FILE" "$SAFA_RECOVERY_DB_NAME"

  rm -f "$raw_log"

  if [[ "$stop_seen" == true ]]; then
    break
  fi
done

[[ "$started" == true ]] || fail "full backup starts at $source_log_file but that log is absent from the archive"
if [[ -n "${SAFA_PITR_STOP_LOG_FILE:-}" && "$stop_seen" != true ]]; then
  fail "requested PITR stop log $SAFA_PITR_STOP_LOG_FILE is absent from the archive"
fi

printf 'SAFA recovery database %s restored from full backup and retained binlogs.\n' "$SAFA_RECOVERY_DB_NAME"
printf 'Verify business integrity and obtain incident-commander approval before any production cutover.\n'
