#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

fail() {
  printf 'SAFA full backup failed: %s\n' "$*" >&2
  exit 1
}

for command_name in mysqldump openssl gzip sha256sum php; do
  command -v "$command_name" >/dev/null 2>&1 || fail "required command is unavailable: $command_name"
done

: "${SAFA_MYSQL_DEFAULTS_FILE:?Set SAFA_MYSQL_DEFAULTS_FILE to a protected MySQL option file outside the web root.}"
: "${SAFA_DB_NAME:?Set SAFA_DB_NAME to the authoritative production database name.}"
: "${SAFA_BACKUP_DESTINATION:?Set SAFA_BACKUP_DESTINATION to the mounted/synchronized off-host backup destination.}"
: "${SAFA_BACKUP_ENCRYPTION_KEY_FILE:?Set SAFA_BACKUP_ENCRYPTION_KEY_FILE to a protected key file outside the web root.}"
: "${SAFA_FULL_BACKUP_STATUS_FILE:?Set SAFA_FULL_BACKUP_STATUS_FILE to storage/app/dr/latest-full.json.}"
: "${SAFA_BACKUP_OFFHOST_ACK:?Set SAFA_BACKUP_OFFHOST_ACK=I_UNDERSTAND_THIS_MUST_BE_OFF_HOST after verifying the destination failure domain.}"

[[ "$SAFA_BACKUP_OFFHOST_ACK" == 'I_UNDERSTAND_THIS_MUST_BE_OFF_HOST' ]] || fail 'off-host destination acknowledgement is missing'
[[ "$SAFA_DB_NAME" =~ ^[A-Za-z0-9_]+$ ]] || fail 'SAFA_DB_NAME contains unsupported characters'
[[ -f "$SAFA_MYSQL_DEFAULTS_FILE" && -r "$SAFA_MYSQL_DEFAULTS_FILE" ]] || fail 'MySQL defaults file is not readable'
[[ -f "$SAFA_BACKUP_ENCRYPTION_KEY_FILE" && -r "$SAFA_BACKUP_ENCRYPTION_KEY_FILE" ]] || fail 'backup encryption key file is not readable'

retention_days="${SAFA_FULL_BACKUP_RETENTION_DAYS:-35}"
[[ "$retention_days" =~ ^[0-9]+$ ]] && (( retention_days >= 7 )) || fail 'full backup retention must be at least 7 days'

mkdir -p "$SAFA_BACKUP_DESTINATION/full" "$(dirname "$SAFA_FULL_BACKUP_STATUS_FILE")"

stamp="$(date -u +%Y%m%dT%H%M%SZ)"
epoch="$(date -u +%s)"
base="safa-${SAFA_DB_NAME}-${stamp}"
artifact="$SAFA_BACKUP_DESTINATION/full/${base}.sql.gz.enc"
manifest="${artifact}.manifest.json"
checksum_file="${artifact}.sha256"
plain_dump="$(mktemp "${TMPDIR:-/tmp}/safa-full-${stamp}.XXXXXX.sql")"
encrypted_tmp="${artifact}.tmp.$$"
status_tmp="${SAFA_FULL_BACKUP_STATUS_FILE}.tmp.$$"

cleanup() {
  rm -f "$plain_dump" "$encrypted_tmp" "$status_tmp"
}
trap cleanup EXIT

source_data_option='--master-data=2'
if mysqldump --help 2>/dev/null | grep -q -- '--source-data'; then
  source_data_option='--source-data=2'
fi

gtid_option=()
if mysqldump --help 2>/dev/null | grep -q -- '--set-gtid-purged'; then
  gtid_option=(--set-gtid-purged=OFF)
fi

mysqldump \
  --defaults-extra-file="$SAFA_MYSQL_DEFAULTS_FILE" \
  --single-transaction \
  --quick \
  --routines \
  --triggers \
  --events \
  --hex-blob \
  --default-character-set=utf8mb4 \
  "$source_data_option" \
  "${gtid_option[@]}" \
  "$SAFA_DB_NAME" > "$plain_dump"

[[ -s "$plain_dump" ]] || fail 'mysqldump produced an empty backup'

coordinate_line="$(grep -m1 -E 'CHANGE (REPLICATION SOURCE|MASTER) TO' "$plain_dump" || true)"
source_log_file="$(printf '%s\n' "$coordinate_line" | sed -E "s/.*(SOURCE_LOG_FILE|MASTER_LOG_FILE)='([^']+)'.*/\2/" || true)"
source_log_pos="$(printf '%s\n' "$coordinate_line" | sed -nE 's/.*(SOURCE_LOG_POS|MASTER_LOG_POS)=([0-9]+).*/\2/p' || true)"
[[ -n "$source_log_file" && "$source_log_pos" =~ ^[0-9]+$ ]] || fail 'full backup did not contain a binary-log recovery coordinate'

gzip -c "$plain_dump" | openssl enc -aes-256-cbc -salt -pbkdf2 -iter 200000 \
  -pass "file:$SAFA_BACKUP_ENCRYPTION_KEY_FILE" -out "$encrypted_tmp"
[[ -s "$encrypted_tmp" ]] || fail 'encrypted backup artifact is empty'
mv "$encrypted_tmp" "$artifact"

artifact_sha="$(sha256sum "$artifact" | awk '{print $1}')"
printf '%s  %s\n' "$artifact_sha" "$(basename "$artifact")" > "$checksum_file"
(
  cd "$(dirname "$artifact")"
  sha256sum -c "$(basename "$checksum_file")" >/dev/null
) || fail 'encrypted backup checksum verification failed'

php -r '
$payload = [
    "schema" => 1,
    "kind" => "mysql_full",
    "completed_at_epoch" => (int) $argv[1],
    "completed_at_utc" => $argv[2],
    "database" => $argv[3],
    "artifact" => $argv[4],
    "sha256" => $argv[5],
    "source_log_file" => $argv[6],
    "source_log_pos" => (int) $argv[7],
];
file_put_contents($argv[8], json_encode($payload, JSON_UNESCAPED_SLASHES | JSON_PRETTY_PRINT) . PHP_EOL, LOCK_EX);
' "$epoch" "$stamp" "$SAFA_DB_NAME" "$artifact" "$artifact_sha" "$source_log_file" "$source_log_pos" "$manifest"

cp "$manifest" "$status_tmp"
mv "$status_tmp" "$SAFA_FULL_BACKUP_STATUS_FILE"

# Retention is applied only after a new verified backup and heartbeat exist.
find "$SAFA_BACKUP_DESTINATION/full" -type f \
  \( -name 'safa-*.sql.gz.enc' -o -name 'safa-*.sql.gz.enc.sha256' -o -name 'safa-*.sql.gz.enc.manifest.json' \) \
  -mtime "+$retention_days" -delete

printf 'SAFA full backup verified: %s (binlog %s:%s)\n' "$artifact" "$source_log_file" "$source_log_pos"
