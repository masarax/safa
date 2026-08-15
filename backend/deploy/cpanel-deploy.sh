#!/bin/bash
set -Eeuo pipefail
umask 027

fail() {
  printf 'SAFA deployment failed: %s\n' "$1" >&2
  exit 1
}

REPOSITORY_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd -P)"
cd "$REPOSITORY_ROOT"

DEPLOY_SHA="$(git rev-parse HEAD 2>/dev/null || true)"
[[ "$DEPLOY_SHA" =~ ^[a-f0-9]{40}$ ]] || fail 'Unable to resolve the checked-out Git commit.'

HOME_DIR="${HOME:-}"
[[ -n "$HOME_DIR" && -d "$HOME_DIR" ]] || fail 'The cPanel account home directory is unavailable.'
HOME_DIR="$(cd "$HOME_DIR" && pwd -P)"

DOMAIN_FILE="$HOME_DIR/.safa-production-domain"
RUN_ONCE_REQUEST_FILE="$HOME_DIR/.safa-run-once.request"
[[ -f "$DOMAIN_FILE" ]] || fail 'The production-domain deployment marker is missing.'
PRODUCTION_DOMAIN="$(tr -d '\r\n[:space:]' < "$DOMAIN_FILE")"
[[ "$PRODUCTION_DOMAIN" =~ ^[A-Za-z0-9.-]+$ && "$PRODUCTION_DOMAIN" == *.* ]] || fail 'The production-domain deployment marker is invalid.'

PHP_BIN=''
for candidate in /opt/cpanel/ea-php83/root/usr/bin/php "$(command -v php 2>/dev/null || true)"; do
  if [[ -n "$candidate" && -x "$candidate" ]]; then
    version_id="$($candidate -r 'echo PHP_VERSION_ID;' 2>/dev/null || true)"
    if [[ "$version_id" =~ ^[0-9]+$ ]] && (( version_id >= 80300 )); then
      PHP_BIN="$candidate"
      break
    fi
  fi
done
[[ -n "$PHP_BIN" ]] || fail 'PHP 8.3 or newer is required on the cPanel account.'

UAPI_BIN="$(command -v uapi 2>/dev/null || true)"
if [[ -z "$UAPI_BIN" && -x /usr/local/cpanel/bin/uapi ]]; then
  UAPI_BIN=/usr/local/cpanel/bin/uapi
fi
[[ -n "$UAPI_BIN" && -x "$UAPI_BIN" ]] || fail 'The cPanel UAPI command is unavailable.'

DOMAIN_JSON="$($UAPI_BIN --output=json DomainInfo single_domain_data domain="$PRODUCTION_DOMAIN" 2>/dev/null)" || fail 'Could not resolve the production domain through cPanel.'
DOCUMENT_ROOT="$(DOMAIN_JSON="$DOMAIN_JSON" "$PHP_BIN" -r '
$d=json_decode((string)getenv("DOMAIN_JSON"), true);
if (!is_array($d) || (int)($d["result"]["status"] ?? 0) !== 1) exit(2);
$data=$d["result"]["data"] ?? [];
$root=is_array($data) ? ($data["documentroot"] ?? $data["document_root"] ?? null) : null;
if (!is_string($root) || $root === "" || $root[0] !== "/") exit(3);
echo $root;
' 2>/dev/null)" || fail 'cPanel did not return a valid document root for the production domain.'
[[ -d "$DOCUMENT_ROOT" ]] || fail 'The production document root does not exist.'
DOCUMENT_ROOT="$(cd "$DOCUMENT_ROOT" && pwd -P)"

if [[ "$(basename "$DOCUMENT_ROOT")" == 'public' ]]; then
  PROJECT_ROOT="$(cd "$DOCUMENT_ROOT/.." && pwd -P)"
else
  PROJECT_ROOT="$DOCUMENT_ROOT"
fi

case "$PROJECT_ROOT" in
  "$HOME_DIR"/*) ;;
  *) fail 'The resolved project root is outside the cPanel account home directory.' ;;
esac
[[ "$PROJECT_ROOT" != "$HOME_DIR" && "$PROJECT_ROOT" != '/' && "$PROJECT_ROOT" != "$REPOSITORY_ROOT" ]] || fail 'The resolved project root is unsafe for deployment.'
mkdir -p "$PROJECT_ROOT"

COMPOSER_BIN="$(command -v composer 2>/dev/null || true)"
if [[ -z "$COMPOSER_BIN" && -x /opt/cpanel/composer/bin/composer ]]; then
  COMPOSER_BIN=/opt/cpanel/composer/bin/composer
fi
[[ -n "$COMPOSER_BIN" && -x "$COMPOSER_BIN" ]] || fail 'Composer is required on the cPanel account.'

RELEASES_ROOT="$HOME_DIR/.safa-releases"
RELEASE_ROOT="$RELEASES_ROOT/$DEPLOY_SHA"
STAGING_ROOT="$RELEASE_ROOT/backend"
NEW_MANIFEST="$RELEASE_ROOT/manifest.txt"
OLD_MANIFEST="$PROJECT_ROOT/.safa-deployed-files"
mkdir -p "$RELEASES_ROOT"
rm -rf "$RELEASE_ROOT"
mkdir -p "$STAGING_ROOT"

cleanup() {
  rm -rf "$RELEASE_ROOT" 2>/dev/null || true
}
trap cleanup EXIT

# Stage only repository-owned application files. Production credentials and
# runtime/user data are never copied out of, overwritten in, or deleted from
# the live project root.
tar -C "$REPOSITORY_ROOT/backend" \
  --exclude='./.env' \
  --exclude='./.env.*' \
  --exclude='./database/testing.sqlite' \
  --exclude='./deploy' \
  --exclude='./node_modules' \
  --exclude='./storage' \
  --exclude='./public/storage' \
  --exclude='./tests' \
  -cf - . | tar -C "$STAGING_ROOT" -xf -

[[ -f "$STAGING_ROOT/composer.json" && -f "$STAGING_ROOT/composer.lock" ]] || fail 'The staged Laravel release is incomplete.'

# Composer package discovery may need the production application settings, but
# the credentials file remains outside the release manifest and is never
# published from staging.
if [[ -f "$PROJECT_ROOT/.env" ]]; then
  cp -p "$PROJECT_ROOT/.env" "$STAGING_ROOT/.env"
fi

(
  cd "$STAGING_ROOT"
  "$PHP_BIN" "$COMPOSER_BIN" install \
    --no-dev \
    --no-interaction \
    --prefer-dist \
    --optimize-autoloader \
    --no-progress
)
rm -f "$STAGING_ROOT/.env"

mkdir -p "$STAGING_ROOT/bootstrap"
printf '{"commit":"%s"}\n' "$DEPLOY_SHA" > "$STAGING_ROOT/bootstrap/safa-build.json"

(
  cd "$STAGING_ROOT"
  find . \( -type f -o -type l \) -print \
    | sed 's#^\./##' \
    | grep -Ev '^(\.env($|\.)|storage($|/)|public/storage($|/)|\.safa-deployed-files$)' \
    | LC_ALL=C sort -u > "$NEW_MANIFEST"
)
[[ -s "$NEW_MANIFEST" ]] || fail 'The deployment manifest is empty.'

# Remove only stale files that were installed by an earlier SAFA deployment.
# Untracked production/runtime files are deliberately left untouched.
if [[ -f "$OLD_MANIFEST" ]]; then
  LC_ALL=C sort -u "$OLD_MANIFEST" > "$RELEASE_ROOT/old-manifest.txt"
  comm -23 "$RELEASE_ROOT/old-manifest.txt" "$NEW_MANIFEST" | while IFS= read -r relative; do
    [[ -n "$relative" ]] || continue
    case "$relative" in
      /*|*'..'*|.env|.env.*|storage/*|public/storage/*) fail 'Unsafe stale-file entry detected in deployment manifest.' ;;
    esac
    rm -f -- "$PROJECT_ROOT/$relative"
  done
fi

# Copy the staged release without touching .env, runtime storage, uploaded
# assets, or any other production-only files.
tar -C "$STAGING_ROOT" \
  --exclude='./.env' \
  --exclude='./.env.*' \
  --exclude='./storage' \
  --exclude='./public/storage' \
  -cf - . | tar -C "$PROJECT_ROOT" -xf -
cp "$NEW_MANIFEST" "$OLD_MANIFEST"

for directory in \
  "$PROJECT_ROOT/storage/framework/cache/data" \
  "$PROJECT_ROOT/storage/framework/sessions" \
  "$PROJECT_ROOT/storage/framework/views" \
  "$PROJECT_ROOT/storage/logs" \
  "$PROJECT_ROOT/public/storage/logos"; do
  mkdir -p "$directory"
done
chmod -R u+rwX "$PROJECT_ROOT/storage" "$PROJECT_ROOT/bootstrap/cache" 2>/dev/null || true

[[ -f "$PROJECT_ROOT/.env" ]] || fail 'Production .env is missing. Deployment will not create credentials automatically.'
[[ -f "$PROJECT_ROOT/artisan" && -f "$PROJECT_ROOT/vendor/autoload.php" ]] || fail 'The published Laravel runtime is incomplete.'

ARTISAN=("$PHP_BIN" "$PROJECT_ROOT/artisan")
APP_WAS_DOWN=0
if [[ -f "$PROJECT_ROOT/storage/framework/down" ]]; then
  APP_WAS_DOWN=1
else
  "${ARTISAN[@]}" down --retry=15 --refresh=30 >/dev/null 2>&1 || true
fi

restore_application() {
  if (( APP_WAS_DOWN == 0 )); then
    "${ARTISAN[@]}" up >/dev/null 2>&1 || true
  fi
}
trap 'restore_application; cleanup' EXIT

"${ARTISAN[@]}" migrate --force --no-interaction
"${ARTISAN[@]}" db:seed --force --no-interaction
"${ARTISAN[@]}" optimize:clear
"${ARTISAN[@]}" config:cache
"${ARTISAN[@]}" view:cache
printf '%s\n' "$(date -u +'%Y-%m-%dT%H:%M:%SZ')" > "$PROJECT_ROOT/storage/installed"

RUN_ONCE_REQUEST='0'
if [[ -f "$RUN_ONCE_REQUEST_FILE" ]]; then
  RUN_ONCE_REQUEST="$(tr -d '\r\n[:space:]' < "$RUN_ONCE_REQUEST_FILE")"
fi
rm -f "$RUN_ONCE_REQUEST_FILE"

RUNNER_SOURCE="$REPOSITORY_ROOT/backend/deploy/run-once.php"
RUNNER_ROOT_COPY="$PROJECT_ROOT/run-once.php"
RUNNER_PUBLIC_COPY="$PROJECT_ROOT/public/run-once.php"
RUNNER_LOCK="$PROJECT_ROOT/storage/run-once.lock"
if [[ "$RUN_ONCE_REQUEST" == '1' && ! -f "$RUNNER_LOCK" ]]; then
  [[ -f "$RUNNER_SOURCE" ]] || fail 'The one-time setup runner source is missing.'
  cp "$RUNNER_SOURCE" "$RUNNER_ROOT_COPY"
  mkdir -p "$PROJECT_ROOT/public"
  cp "$RUNNER_SOURCE" "$RUNNER_PUBLIC_COPY"
  chmod 0644 "$RUNNER_ROOT_COPY" "$RUNNER_PUBLIC_COPY"
else
  # A successful historical run permanently wins over any future request.
  # This preserves the one-time runner's self-deletion guarantee.
  rm -f "$RUNNER_ROOT_COPY" "$RUNNER_PUBLIC_COPY"
fi

restore_application
trap cleanup EXIT

# Keep only a small number of transient build directories. The live project is
# not a symlink to staging, so cleanup cannot remove the production release.
find "$RELEASES_ROOT" -mindepth 1 -maxdepth 1 -type d -mtime +2 -exec rm -rf {} + 2>/dev/null || true

printf 'SAFA deployment completed for commit %s\n' "$DEPLOY_SHA"
