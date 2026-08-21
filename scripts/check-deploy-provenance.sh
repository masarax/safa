#!/usr/bin/env bash
set -euo pipefail

workflow=".github/workflows/deploy.yml"

fail() {
  echo "deploy provenance check failed: $*" >&2
  exit 1
}

[ -f "$workflow" ] || fail "$workflow is missing"

grep -Fq 'workflow_run:' "$workflow" || fail 'deployment must be triggered by backend CI workflow_run'
if grep -Fq 'workflow_dispatch:' "$workflow"; then
  fail 'manual production deployment is disabled until it can prove exact-SHA CI eligibility'
fi

grep -Fq 'github.event.workflow_run.conclusion == '\''success'\''' "$workflow" || fail 'successful upstream CI conclusion is required'
grep -Fq 'github.event.workflow_run.event == '\''push'\''' "$workflow" || fail 'deployment must be authorized only by a push CI run'
grep -Fq 'github.event.workflow_run.head_repository.full_name == github.repository' "$workflow" || fail 'source repository identity must be verified'
grep -Fq 'github.event.workflow_run.head_branch == github.event.repository.default_branch' "$workflow" || fail 'source branch must be the repository default branch'
grep -Fq 'ref: ${{ github.event.workflow_run.head_sha }}' "$workflow" || fail 'checkout must use workflow_run.head_sha'
grep -Fq 'TESTED_SHA: ${{ github.event.workflow_run.head_sha }}' "$workflow" || fail 'tested SHA must be carried into provenance verification'
grep -Fq 'CURRENT_DEFAULT_SHA="$(git rev-parse "refs/remotes/origin/$DEFAULT_BRANCH")"' "$workflow" || fail 'current default branch head must be resolved explicitly'
grep -Fq 'test "$CURRENT_DEFAULT_SHA" = "$TESTED_SHA"' "$workflow" || fail 'stale successful CI runs must be rejected before build'
grep -Fq 'test "$CURRENT_DEFAULT_SHA" = "$DEPLOY_SHA"' "$workflow" || fail 'stale deployment must be rechecked immediately before upload'
grep -Fq 'printf '\''{"commit":"%s"}\n'\'' "$DEPLOY_SHA" > backend/bootstrap/safa-build.json' "$workflow" || fail 'build identity must be stamped from the tested deployment SHA'

if grep -Eq '^[[:space:]]+ref:[[:space:]]+main[[:space:]]*$' "$workflow"; then
  fail 'deployment must never checkout moving main directly'
fi

echo 'Deployment provenance contract is valid.'
