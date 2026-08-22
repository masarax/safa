#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

fail() {
  printf 'SAFA logo backup failed: %s\n' "$*" >&2
  exit 1
}

for command_name in tar openssl sha256sum php find sort; do
  command -v "$command_name" >/dev/null 2>&1 || fail "required command is unavailable: $command_name"
done

: "${SAFA_LOGO_SOURCE_DIR:?Set SAFA_LOGO_SOURCE_DIR to the deployed public/storage/logos directory.}"
: "${SAFA_BACKUP_DESTINATION:?Set SAFA_BACKUP_DESTINATION to the mounted/synchronized off-host backup destination.}"
: "${SAFA_BACKUP_ENCRYPTION_KEY_FILE:?Set SAFA_BACKUP_ENCRYPTION_KEY_FILE to a protected key file outside the web root.}"
: "${SAFA_ASSET_BACKUP_STATUS_FILE:?Set SAFA_ASSET_BACKUP_STATUS_FILE to storage/app/dr/latest-assets.json.}"
: "${SAFA_BACKUP_OFFHOST_ACK:?Set SAFA_BACKUP_OFFHOST_ACK=I_UNDERSTAND_THIS_MUST_BE_OFF_HOST after verifying the destination failure domain.}"

[[ "$SAFA_BACKUP_OFFHOST_ACK" == 'I_UNDERSTAND_THIS_MUST_BE_OFF_HOST' ]] || fail 'off-host destination acknowledgement is missing'
[[ -f "$SAFA_BACKUP_ENCRYPTION_KEY_FILE" && -r "$SAFA_BACKUP_ENCRYPTION_KEY_FILE" ]] || fail 'backup encryption key file is not readable'

retention_days="${SAFA_ASSET_BACKUP_RETENTION_DAYS:-35}"
[[ "$retention_days" =~ ^[0-9]+$ ]] && (( retention_days >= 7 )) || fail 'asset backup retention must be at least 7 days'

mkdir -p "$SAFA_LOGO_SOURCE_DIR" "$SAFA_BACKUP_DESTINATION/assets" "$(dirname "$SAFA_ASSET_BACKUP_STATUS_FILE")"

# Logo filenames are server-generated and immutable. Hash both relative names and
# bytes so a no-change run can safely reuse the last encrypted snapshot instead
# of creating 288 duplicate archives per day.
content_sha="$(
  while IFS= read -r -d '' relative; do
    printf '%s\0' "$relative"
    sha256sum "$SAFA_LOGO_SOURCE_DIR/$relative"
  done < <(cd "$SAFA_LOGO_SOURCE_DIR" && find . -type f -printf '%P\0' | sort -z)
)"
content_sha="$(printf '%s' "$content_sha" | sha256sum | awk '{print $1}')"

epoch="$(date -u +%s)"
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
status_tmp="${SAFA_ASSET_BACKUP_STATUS_FILE}.tmp.$$"
cleanup() { rm -f "$status_tmp"; }
trap cleanup EXIT

existing=""
if [[ -r "$SAFA_ASSET_BACKUP_STATUS_FILE" ]]; then
  existing="$(php -r '
try {
    $s = json_decode(file_get_contents($argv[1]), true, 32, JSON_THROW_ON_ERROR);
    if (($s["content_sha256"] ?? "") !== $argv[2]) exit(0);
    $artifact = (string) ($s["artifact"] ?? "");
    $sha = strtolower((string) ($s["sha256"] ?? ""));
    if ($artifact !== "" && preg_match("/^[a-f0-9]{64}$/", $sha)) echo base64_encode($artifact), "\t", $sha;
} catch (Throwable) {}
' "$SAFA_ASSET_BACKUP_STATUS_FILE" "$content_sha")"
fi

artifact=''
artifact_sha=''
if [[ -n "$existing" ]]; then
  IFS=$'\t' read -r artifact_b64 artifact_sha <<< "$existing"
  artifact="$(printf '%s' "$artifact_b64" | base64 --decode)"
  if [[ ! -s "$artifact" || "$(sha256sum "$artifact" | awk '{print $1}')" != "$artifact_sha" ]]; then
    artifact=''
    artifact_sha=''
  fi
fi

if [[ -z "$artifact" ]]; then
  artifact="$SAFA_BACKUP_DESTINATION/assets/safa-logos-${stamp}.tar.gz.enc"
  encrypted_tmp="${artifact}.tmp.$$"
  trap 'rm -f "$status_tmp" "${encrypted_tmp:-}"' EXIT
  tar -C "$SAFA_LOGO_SOURCE_DIR" -czf - . | \
    openssl enc -aes-256-cbc -salt -pbkdf2 -iter 200000 \
      -pass "file:$SAFA_BACKUP_ENCRYPTION_KEY_FILE" -out "$encrypted_tmp"
  [[ -s "$encrypted_tmp" ]] || fail 'encrypted logo snapshot is empty'
  mv "$encrypted_tmp" "$artifact"
  artifact_sha="$(sha256sum "$artifact" | awk '{print $1}')"
  printf '%s  %s\n' "$artifact_sha" "$(basename "$artifact")" > "${artifact}.sha256"
else
  # Keep the currently authoritative unchanged snapshot inside retention.
  touch "$artifact" "${artifact}.sha256" 2>/dev/null || true
fi

php -r '
$payload = [
    "schema" => 1,
    "kind" => "logo_assets",
    "completed_at_epoch" => (int) $argv[1],
    "completed_at_utc" => $argv[2],
    "artifact" => $argv[3],
    "sha256" => $argv[4],
    "content_sha256" => $argv[5],
];
file_put_contents($argv[6], json_encode($payload, JSON_UNESCAPED_SLASHES | JSON_PRETTY_PRINT) . PHP_EOL, LOCK_EX);
' "$epoch" "$stamp" "$artifact" "$artifact_sha" "$content_sha" "$status_tmp"
mv "$status_tmp" "$SAFA_ASSET_BACKUP_STATUS_FILE"

find "$SAFA_BACKUP_DESTINATION/assets" -type f \
  \( -name 'safa-logos-*.tar.gz.enc' -o -name 'safa-logos-*.tar.gz.enc.sha256' \) \
  -mtime "+$retention_days" -delete

printf 'SAFA logo asset backup verified: %s\n' "$artifact"
