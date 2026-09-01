#!/usr/bin/env bash
set +x
set -Eeuo pipefail

cd "$(dirname "$0")/../../.."
ROOT="$(pwd)"
DOMAIN="${DOMAIN:-gole.co.kr}"
EMAIL="${EMAIL:-coldingcontact@gmail.com}"
GCP_PROJECT_ID="${GCP_PROJECT_ID:-}"
GTS_ACME_SERVER="https://dv.acme-v02.api.pki.goog/directory"
GTS_ACCOUNT_DIR="/etc/letsencrypt/accounts/dv.acme-v02.api.pki.goog/directory"
COMPOSE=(docker compose --env-file /etc/gole/infra.env --env-file /etc/gole/gole.env -f "$ROOT/infra/gcp/docker-compose.yml")

eab_file=""
rendered=""

cleanup() {
  local cleanup_exit_code=$?
  trap - EXIT
  if [ -n "$eab_file" ]; then
    rm -f -- "$eab_file"
  fi
  if [ -n "$rendered" ]; then
    rm -f -- "$rendered"
  fi
  exit "$cleanup_exit_code"
}
trap cleanup EXIT

die() {
  echo "$*" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || die "required command not found: $1"
}

if [[ ! "$DOMAIN" =~ ^[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?(\.[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?)+$ ]]; then
  die "DOMAIN must be an apex DNS name such as gole.co.kr"
fi
if [[ "$EMAIL" != *@* || "$EMAIL" =~ [[:space:]] ]]; then
  die "EMAIL must be a valid contact address"
fi

require_command docker
require_command mktemp

gts_account_exists() {
  local account_exit_code=0
  # shellcheck disable=SC2016 # $1/$account는 certbot 컨테이너의 sh에서 확장한다.
  if "${COMPOSE[@]}" --profile certificate run --rm --no-deps -T \
    --entrypoint /bin/sh certbot -c \
    'for account in "$1"/*; do [ -d "$account" ] && exit 0; done; exit 42' \
    sh "$GTS_ACCOUNT_DIR" >/dev/null 2>&1; then
    return 0
  else
    account_exit_code=$?
  fi

  if [ "$account_exit_code" -eq 42 ]; then
    return 1
  fi
  die "could not inspect the Certbot account volume"
}

renewal_state="missing"
renewal_status=0
# shellcheck disable=SC2016 # $1/$2는 certbot 컨테이너의 sh에서 확장한다.
if "${COMPOSE[@]}" --profile certificate run --rm --no-deps -T \
  --entrypoint /bin/sh certbot -c \
  'if [ ! -f "$1" ]; then exit 42; fi; grep -Fqx "server = $2" "$1" && exit 0; exit 43' \
  sh "/etc/letsencrypt/renewal/$DOMAIN.conf" "$GTS_ACME_SERVER" >/dev/null 2>&1; then
  renewal_state="gts"
else
  renewal_status=$?
  case "$renewal_status" in
    42) renewal_state="missing" ;;
    43) renewal_state="different-ca" ;;
    *) die "could not inspect the existing Certbot renewal configuration" ;;
  esac
fi

if ! gts_account_exists; then
  require_command gcloud

  if [ -z "$GCP_PROJECT_ID" ]; then
    GCP_PROJECT_ID="$(gcloud config get-value project 2>/dev/null || true)"
  fi
  if [ -z "$GCP_PROJECT_ID" ] || [ "$GCP_PROJECT_ID" = "(unset)" ]; then
    die "set GCP_PROJECT_ID or configure a default gcloud project"
  fi

  gcloud services enable publicca.googleapis.com \
    --project "$GCP_PROJECT_ID" \
    --quiet

  eab_file="$(umask 077; mktemp "${TMPDIR:-/tmp}/gole-gts-eab.XXXXXX")"
  chmod 0600 "$eab_file"
  if ! CLOUDSDK_CORE_LOG_HTTP=false gcloud publicca external-account-keys create \
    --project "$GCP_PROJECT_ID" \
    --verbosity=error \
    --format='value(keyId,b64MacKey)' >"$eab_file" 2>/dev/null; then
    die "failed to create the one-time Google Public CA EAB key"
  fi

  eab_kid=""
  eab_hmac_key=""
  if ! IFS=$'\t' read -r eab_kid eab_hmac_key <"$eab_file"; then
    die "Google Public CA returned an unreadable EAB key"
  fi
  if [ -z "$eab_kid" ] || [ -z "$eab_hmac_key" ]; then
    die "Google Public CA returned an incomplete EAB key"
  fi

  {
    printf 'server = %s\n' "$GTS_ACME_SERVER"
    printf 'eab-kid = %s\n' "$eab_kid"
    printf 'eab-hmac-key = %s\n' "$eab_hmac_key"
  } >"$eab_file"
  unset eab_kid eab_hmac_key

  if ! "${COMPOSE[@]}" --profile certificate run --rm --no-deps -T \
    --volume "$eab_file:/run/secrets/gts-eab.ini:ro" \
    certbot register \
    --config /run/secrets/gts-eab.ini \
    --non-interactive --agree-tos --email "$EMAIL" --quiet \
    >/dev/null 2>&1; then
    die "failed to register the Certbot account with Google Public CA"
  fi

  rm -f -- "$eab_file"
  eab_file=""
fi

certbot_args=(
  certonly
  --server "$GTS_ACME_SERVER"
  --webroot -w /var/www/certbot
  --cert-name "$DOMAIN"
  -d "$DOMAIN" -d "www.$DOMAIN"
  --non-interactive --agree-tos --email "$EMAIL"
)
if [ "$renewal_state" = "different-ca" ]; then
  certbot_args+=(--force-renewal)
fi

"${COMPOSE[@]}" --profile certificate run --rm --no-deps -T certbot "${certbot_args[@]}"

rendered="$(mktemp)"
sed "s/__DOMAIN__/${DOMAIN//\//\\/}/g" "$ROOT/infra/gcp/nginx-https.conf.template" >"$rendered"
sudo install -m 0644 "$rendered" /etc/gole/nginx.conf
# install(1)은 파일 inode를 교체한다. bind mount가 새 파일과 인증서를 보도록 컨테이너를 재생성한다.
"${COMPOSE[@]}" up -d --no-deps --force-recreate nginx
"${COMPOSE[@]}" exec -T nginx nginx -t
