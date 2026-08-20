#!/usr/bin/env bash
set -euo pipefail

status=0
while IFS= read -r -d '' file; do
  while IFS=: read -r line_no line; do
    [[ -n "$line_no" ]] || continue
    ref="${line#*@}"
    ref="${ref%%[[:space:]#]*}"
    if [[ ! "$ref" =~ ^[0-9a-fA-F]{40}$ ]]; then
      echo "Mutable GitHub Action ref: ${file}:${line_no}: ${line}" >&2
      status=1
    fi
  done < <(grep -nE '^[[:space:]]*uses:[[:space:]]+[^[:space:]#]+@[^[:space:]#]+' "$file" || true)
done < <(find .github/workflows -type f \( -name '*.yml' -o -name '*.yaml' \) -print0 | sort -z)

if (( status != 0 )); then
  echo 'Every third-party GitHub Action must be pinned to a full immutable 40-character commit SHA.' >&2
  exit "$status"
fi

echo 'All workflow action references are pinned to immutable SHAs.'
