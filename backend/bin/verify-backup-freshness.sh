#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

: "${SAFA_FULL_BACKUP_STATUS_FILE:?Set SAFA_FULL_BACKUP_STATUS_FILE.}"
: "${SAFA_BINLOG_BACKUP_STATUS_FILE:?Set SAFA_BINLOG_BACKUP_STATUS_FILE.}"
: "${SAFA_ASSET_BACKUP_STATUS_FILE:?Set SAFA_ASSET_BACKUP_STATUS_FILE.}"

full_max_age="${SAFA_FULL_BACKUP_MAX_AGE_SECONDS:-93600}"
binlog_max_age="${SAFA_BINLOG_BACKUP_MAX_AGE_SECONDS:-900}"
asset_max_age="${SAFA_ASSET_BACKUP_MAX_AGE_SECONDS:-900}"
[[ "$full_max_age" =~ ^[0-9]+$ && "$binlog_max_age" =~ ^[0-9]+$ && "$asset_max_age" =~ ^[0-9]+$ ]] || {
  echo 'Backup freshness limits must be integer seconds.' >&2
  exit 1
}

alert() {
  local reason="$1"
  printf 'SAFA backup monitor degraded: %s\n' "$reason" >&2

  if [[ -n "${SAFA_BACKUP_ALERT_WEBHOOK:-}" ]] && command -v curl >/dev/null 2>&1; then
    # Do not send paths, credentials, database names or backup contents to the
    # monitoring destination. The endpoint receives only a generic state.
    curl --fail --silent --show-error \
      --connect-timeout 5 --max-time 10 \
      -H 'Content-Type: application/json' \
      --data '{"service":"safa-backup","status":"degraded"}' \
      "$SAFA_BACKUP_ALERT_WEBHOOK" >/dev/null || true
  fi
  exit 1
}

check_status() {
  local label="$1"
  local status_file="$2"
  local max_age="$3"

  [[ -f "$status_file" && -r "$status_file" ]] || alert "$label status heartbeat is missing"

  local parsed
  parsed="$(php -r '
try {
    $data = json_decode(file_get_contents($argv[1]), true, 32, JSON_THROW_ON_ERROR);
    $epoch = (int) ($data["completed_at_epoch"] ?? 0);
    $artifact = (string) ($data["artifact"] ?? "");
    $sha = strtolower((string) ($data["sha256"] ?? ""));
    if ($epoch <= 0 || $artifact === "" || !preg_match("/^[a-f0-9]{64}$/", $sha)) exit(2);
    echo $epoch, "\t", base64_encode($artifact), "\t", $sha;
} catch (Throwable) {
    exit(2);
}
' "$status_file")" || alert "$label status heartbeat is invalid"

  local epoch artifact_b64 expected_sha
  IFS=$'\t' read -r epoch artifact_b64 expected_sha <<< "$parsed"
  [[ "$epoch" =~ ^[0-9]+$ ]] || alert "$label completion timestamp is invalid"

  local now age
  now="$(date -u +%s)"
  (( epoch <= now + 300 )) || alert "$label completion timestamp is unexpectedly in the future"
  age=$(( now - epoch ))
  (( age <= max_age )) || alert "$label is stale (${age}s > ${max_age}s)"

  local artifact
  artifact="$(printf '%s' "$artifact_b64" | base64 --decode)" || alert "$label artifact path could not be decoded"
  [[ -f "$artifact" && -r "$artifact" && -s "$artifact" ]] || alert "$label encrypted artifact is missing"

  local actual_sha
  actual_sha="$(sha256sum "$artifact" | awk '{print $1}')"
  [[ "$actual_sha" == "$expected_sha" ]] || alert "$label encrypted artifact checksum does not match"

  printf '%s healthy: age=%ss\n' "$label" "$age"
}

for command_name in php sha256sum base64; do
  command -v "$command_name" >/dev/null 2>&1 || alert "required monitor command is unavailable: $command_name"
done

check_status 'full backup' "$SAFA_FULL_BACKUP_STATUS_FILE" "$full_max_age"
check_status 'binlog archive' "$SAFA_BINLOG_BACKUP_STATUS_FILE" "$binlog_max_age"
check_status 'logo assets' "$SAFA_ASSET_BACKUP_STATUS_FILE" "$asset_max_age"

echo 'SAFA backup freshness and artifact integrity are healthy.'
