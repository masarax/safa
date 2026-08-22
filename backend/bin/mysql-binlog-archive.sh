#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

fail() {
  printf 'SAFA binlog archive failed: %s\n' "$*" >&2
  exit 1
}

for command_name in mysql mysqlbinlog openssl sha256sum php; do
  command -v "$command_name" >/dev/null 2>&1 || fail "required command is unavailable: $command_name"
done

: "${SAFA_MYSQL_DEFAULTS_FILE:?Set SAFA_MYSQL_DEFAULTS_FILE to a protected MySQL option file outside the web root.}"
: "${SAFA_BACKUP_DESTINATION:?Set SAFA_BACKUP_DESTINATION to the mounted/synchronized off-host backup destination.}"
: "${SAFA_BACKUP_ENCRYPTION_KEY_FILE:?Set SAFA_BACKUP_ENCRYPTION_KEY_FILE to a protected key file outside the web root.}"
: "${SAFA_BINLOG_BACKUP_STATUS_FILE:?Set SAFA_BINLOG_BACKUP_STATUS_FILE to storage/app/dr/latest-binlog.json.}"
: "${SAFA_BACKUP_OFFHOST_ACK:?Set SAFA_BACKUP_OFFHOST_ACK=I_UNDERSTAND_THIS_MUST_BE_OFF_HOST after verifying the destination failure domain.}"

[[ "$SAFA_BACKUP_OFFHOST_ACK" == 'I_UNDERSTAND_THIS_MUST_BE_OFF_HOST' ]] || fail 'off-host destination acknowledgement is missing'
[[ -f "$SAFA_MYSQL_DEFAULTS_FILE" && -r "$SAFA_MYSQL_DEFAULTS_FILE" ]] || fail 'MySQL defaults file is not readable'
[[ -f "$SAFA_BACKUP_ENCRYPTION_KEY_FILE" && -r "$SAFA_BACKUP_ENCRYPTION_KEY_FILE" ]] || fail 'backup encryption key file is not readable'

retention_days="${SAFA_BINLOG_RETENTION_DAYS:-35}"
[[ "$retention_days" =~ ^[0-9]+$ ]] && (( retention_days >= 7 )) || fail 'binlog retention must be at least 7 days'

mkdir -p "$SAFA_BACKUP_DESTINATION/binlog" "$(dirname "$SAFA_BINLOG_BACKUP_STATUS_FILE")"
tmp_dir="$(mktemp -d "${TMPDIR:-/tmp}/safa-binlog.XXXXXX")"
status_tmp="${SAFA_BINLOG_BACKUP_STATUS_FILE}.tmp.$$"
cleanup() {
  rm -rf "$tmp_dir"
  rm -f "$status_tmp"
}
trap cleanup EXIT

mapfile -t logs < <(mysql --defaults-extra-file="$SAFA_MYSQL_DEFAULTS_FILE" --batch --skip-column-names -e 'SHOW BINARY LOGS' | awk '{print $1}')
(( ${#logs[@]} > 0 )) || fail 'MySQL binary logging is disabled or no binary logs are available'

latest_log=''
latest_artifact=''
latest_sha=''
for log_name in "${logs[@]}"; do
  [[ "$log_name" =~ ^[A-Za-z0-9._-]+$ ]] || fail "unsafe binary-log name returned by MySQL: $log_name"

  rm -f "$tmp_dir/$log_name"
  mysqlbinlog \
    --defaults-extra-file="$SAFA_MYSQL_DEFAULTS_FILE" \
    --read-from-remote-server \
    --raw \
    --result-file="$tmp_dir/" \
    "$log_name"

  raw_log="$tmp_dir/$log_name"
  [[ -s "$raw_log" ]] || fail "mysqlbinlog returned an empty archive for $log_name"

  artifact="$SAFA_BACKUP_DESTINATION/binlog/${log_name}.enc"
  encrypted_tmp="${artifact}.tmp.$$"
  openssl enc -aes-256-cbc -salt -pbkdf2 -iter 200000 \
    -pass "file:$SAFA_BACKUP_ENCRYPTION_KEY_FILE" -in "$raw_log" -out "$encrypted_tmp"
  mv "$encrypted_tmp" "$artifact"

  artifact_sha="$(sha256sum "$artifact" | awk '{print $1}')"
  printf '%s  %s\n' "$artifact_sha" "$(basename "$artifact")" > "${artifact}.sha256"
  (
    cd "$(dirname "$artifact")"
    sha256sum -c "$(basename "${artifact}.sha256")" >/dev/null
  ) || fail "encrypted binlog checksum verification failed for $log_name"

  latest_log="$log_name"
  latest_artifact="$artifact"
  latest_sha="$artifact_sha"
done

stamp="$(date -u +%Y%m%dT%H%M%SZ)"
epoch="$(date -u +%s)"
php -r '
$payload = [
    "schema" => 1,
    "kind" => "mysql_binlog",
    "completed_at_epoch" => (int) $argv[1],
    "completed_at_utc" => $argv[2],
    "latest_log_file" => $argv[3],
    "artifact" => $argv[4],
    "sha256" => $argv[5],
];
file_put_contents($argv[6], json_encode($payload, JSON_UNESCAPED_SLASHES | JSON_PRETTY_PRINT) . PHP_EOL, LOCK_EX);
' "$epoch" "$stamp" "$latest_log" "$latest_artifact" "$latest_sha" "$status_tmp"
mv "$status_tmp" "$SAFA_BINLOG_BACKUP_STATUS_FILE"

# Keep enough encrypted log history to cover every retained full backup.
find "$SAFA_BACKUP_DESTINATION/binlog" -type f \
  \( -name '*.enc' -o -name '*.enc.sha256' \) \
  -mtime "+$retention_days" -delete

printf 'SAFA binlog archive verified through %s\n' "$latest_log"
