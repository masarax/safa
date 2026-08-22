#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

fail() {
  printf 'SAFA logo recovery failed: %s\n' "$*" >&2
  exit 1
}

for command_name in tar openssl sha256sum; do
  command -v "$command_name" >/dev/null 2>&1 || fail "required command is unavailable: $command_name"
done

: "${SAFA_RESTORE_ASSET_ARTIFACT:?Set SAFA_RESTORE_ASSET_ARTIFACT to a verified encrypted logo snapshot.}"
: "${SAFA_RESTORE_ASSET_SHA256:?Set SAFA_RESTORE_ASSET_SHA256 from the matching status/checksum record.}"
: "${SAFA_BACKUP_ENCRYPTION_KEY_FILE:?Set SAFA_BACKUP_ENCRYPTION_KEY_FILE to the protected recovery key.}"
: "${SAFA_RECOVERY_LOGO_DIR:?Set SAFA_RECOVERY_LOGO_DIR to an empty recovery public/storage/logos directory.}"
: "${SAFA_DR_CONFIRM:?Set SAFA_DR_CONFIRM=RESTORE_TO_EMPTY_RECOVERY_DIRECTORY after verifying the target.}"

[[ "$SAFA_DR_CONFIRM" == 'RESTORE_TO_EMPTY_RECOVERY_DIRECTORY' ]] || fail 'destructive asset recovery confirmation token is incorrect'
[[ "$SAFA_RESTORE_ASSET_SHA256" =~ ^[a-f0-9]{64}$ ]] || fail 'asset checksum format is invalid'
[[ -r "$SAFA_RESTORE_ASSET_ARTIFACT" && -s "$SAFA_RESTORE_ASSET_ARTIFACT" ]] || fail 'encrypted asset artifact is missing'
[[ -r "$SAFA_BACKUP_ENCRYPTION_KEY_FILE" ]] || fail 'backup encryption key file is not readable'
[[ "$(sha256sum "$SAFA_RESTORE_ASSET_ARTIFACT" | awk '{print $1}')" == "$SAFA_RESTORE_ASSET_SHA256" ]] || fail 'encrypted asset checksum does not match'

mkdir -p "$SAFA_RECOVERY_LOGO_DIR"
if find "$SAFA_RECOVERY_LOGO_DIR" -mindepth 1 -print -quit | grep -q .; then
  fail 'recovery logo directory is not empty'
fi

tmp_tar="$(mktemp "${TMPDIR:-/tmp}/safa-logos.XXXXXX.tar.gz")"
trap 'rm -f "$tmp_tar"' EXIT
openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 \
  -pass "file:$SAFA_BACKUP_ENCRYPTION_KEY_FILE" \
  -in "$SAFA_RESTORE_ASSET_ARTIFACT" -out "$tmp_tar"

tar -tzf "$tmp_tar" | while IFS= read -r entry; do
  case "$entry" in
    /*|../*|*/../*|*'/..') fail "unsafe path in asset archive: $entry" ;;
  esac
done

tar -C "$SAFA_RECOVERY_LOGO_DIR" -xzf "$tmp_tar" --no-same-owner --no-same-permissions
printf 'SAFA logo assets restored into recovery directory %s.\n' "$SAFA_RECOVERY_LOGO_DIR"
