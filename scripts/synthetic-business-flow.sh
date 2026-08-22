#!/usr/bin/env bash
set -euo pipefail

: "${SAFA_BASE_URL:=https://safa.masarax.com}"
: "${SAFA_SYNTHETIC_MOBILE:?SAFA_SYNTHETIC_MOBILE is required}"
: "${SAFA_SYNTHETIC_PIN:?SAFA_SYNTHETIC_PIN is required}"
: "${SAFA_MOBILE_API_KEY:?SAFA_MOBILE_API_KEY is required}"
: "${SAFA_OPS_METRICS_KEY:?SAFA_OPS_METRICS_KEY is required}"

base="${SAFA_BASE_URL%/}"
device="safa-synthetic-probe"
fingerprint="safa-synthetic-probe-v1"
login_payload="$(jq -nc --arg mobile "$SAFA_SYNTHETIC_MOBILE" --arg pin "$SAFA_SYNTHETIC_PIN" --arg device "$device" --arg fingerprint "$fingerprint" '{mobile:$mobile,pin:$pin,device_uuid:$device,fingerprint_hash:$fingerprint}')"

login="$(curl --fail-with-body --silent --show-error \
  --connect-timeout 10 --max-time 30 \
  -H 'Accept: application/json' -H 'Content-Type: application/json' \
  -H "X-SAFA-API-KEY: $SAFA_MOBILE_API_KEY" \
  -d "$login_payload" "$base/api/auth/login")"

access="$(jq -er '.access_token // .tokens.access_token' <<<"$login")"
session="$(jq -er '.session_token // .tokens.session_token' <<<"$login")"
refresh="$(jq -er '.refresh_token // .tokens.refresh_token' <<<"$login")"

auth_headers=(
  -H 'Accept: application/json'
  -H "X-SAFA-API-KEY: $SAFA_MOBILE_API_KEY"
  -H "Authorization: Bearer $access"
  -H "X-SAFA-SESSION-TOKEN: $session"
  -H "X-SAFA-DEVICE-TOKEN: $device"
  -H "X-SAFA-FINGERPRINT-TOKEN: $fingerprint"
)

# The persistence probe writes and reads a real Customer inside one database
# transaction, then always rolls back. It exercises database/model persistence
# without leaving synthetic financial/business rows or sync tombstones behind.
curl --fail-with-body --silent --show-error \
  --connect-timeout 10 --max-time 30 \
  -X POST -H 'Accept: application/json' \
  -H "X-SAFA-OPS-KEY: $SAFA_OPS_METRICS_KEY" \
  "$base/api/ops/synthetic-persistence" \
  | jq -e '.status == "ok" and .check == "synthetic_persistence"' >/dev/null

# Close the synthetic authenticated session. Logout failure is also a failed
# synthetic because it signals an authentication lifecycle regression.
curl --fail-with-body --silent --show-error \
  --connect-timeout 10 --max-time 30 \
  -X POST "${auth_headers[@]}" \
  -H "X-SAFA-REFRESH-TOKEN: $refresh" \
  "$base/api/auth/logout" >/dev/null

printf 'SAFA synthetic authentication + rollback persistence probe passed.\n'
