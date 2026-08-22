#!/usr/bin/env bash
set -euo pipefail

: "${GITHUB_SHA:?GITHUB_SHA is required}"
: "${GITHUB_REPOSITORY:?GITHUB_REPOSITORY is required}"
: "${GITHUB_API_URL:=https://api.github.com}"
: "${GITHUB_EVENT_NAME:?GITHUB_EVENT_NAME is required}"
: "${GITHUB_REF_TYPE:?GITHUB_REF_TYPE is required}"
: "${GITHUB_REF_NAME:?GITHUB_REF_NAME is required}"
: "${GITHUB_TOKEN:?GITHUB_TOKEN is required}"

# A production signing job may run only for a commit already merged to main.
# Tags are allowed only when they point to such a commit. A manual dispatch from
# an arbitrary feature branch therefore fails before signing secrets are read.
git fetch --no-tags origin main:refs/remotes/origin/main
if ! git merge-base --is-ancestor "$GITHUB_SHA" refs/remotes/origin/main; then
  echo "Release SHA $GITHUB_SHA is not part of main history" >&2
  exit 1
fi

version_name="$(sed -n 's/^SAFA_VERSION_NAME=//p' gradle.properties | tail -n 1 | tr -d '[:space:]')"
if [[ "$GITHUB_REF_TYPE" == "tag" ]]; then
  expected_tag="v${version_name}"
  if [[ "$GITHUB_REF_NAME" != "$expected_tag" ]]; then
    echo "Release tag $GITHUB_REF_NAME must match Android versionName $expected_tag" >&2
    exit 1
  fi
elif [[ "$GITHUB_EVENT_NAME" != "workflow_dispatch" ]]; then
  echo "Unsupported release event: $GITHUB_EVENT_NAME / $GITHUB_REF_TYPE" >&2
  exit 1
fi

runs_url="${GITHUB_API_URL}/repos/${GITHUB_REPOSITORY}/actions/workflows/android-ci.yml/runs?head_sha=${GITHUB_SHA}&status=success&per_page=100"
runs_json="$(curl --fail --silent --show-error --location \
  --header "Accept: application/vnd.github+json" \
  --header "Authorization: Bearer ${GITHUB_TOKEN}" \
  --header "X-GitHub-Api-Version: 2022-11-28" \
  "$runs_url")"

validated_run_id="$(python3 -c '
import json, sys
payload = json.load(sys.stdin)
runs = payload.get("workflow_runs", [])
eligible = [
    run for run in runs
    if run.get("head_sha") == sys.argv[1]
    and run.get("conclusion") == "success"
    and run.get("name") == "Android Production CI"
]
if not eligible:
    raise SystemExit(1)
eligible.sort(key=lambda run: run.get("run_number", 0), reverse=True)
print(eligible[0]["id"])
' "$GITHUB_SHA" <<<"$runs_json")" || {
  echo "No successful Android Production CI exists for exact release SHA $GITHUB_SHA" >&2
  exit 1
}

jobs_url="${GITHUB_API_URL}/repos/${GITHUB_REPOSITORY}/actions/runs/${validated_run_id}/jobs?per_page=100"
jobs_json="$(curl --fail --silent --show-error --location \
  --header "Accept: application/vnd.github+json" \
  --header "Authorization: Bearer ${GITHUB_TOKEN}" \
  --header "X-GitHub-Api-Version: 2022-11-28" \
  "$jobs_url")"

python3 -c '
import json, sys
payload = json.load(sys.stdin)
required = {
    "Unit, lint and release build",
    "Emulator instrumentation and release smoke",
}
seen = {
    job.get("name")
    for job in payload.get("jobs", [])
    if job.get("conclusion") == "success"
}
missing = sorted(required - seen)
if missing:
    print("Required Android CI jobs are not green: " + ", ".join(missing), file=sys.stderr)
    raise SystemExit(1)
' <<<"$jobs_json"

printf 'validated_android_ci_run=%s\nvalidated_sha=%s\n' "$validated_run_id" "$GITHUB_SHA"
