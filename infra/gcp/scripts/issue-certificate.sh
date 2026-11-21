#!/usr/bin/env bash
set +x
set -Eeuo pipefail

PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
export PATH

ROOT="${GOLE_TRUSTED_RELEASE_ROOT:-}"
DOMAIN="${DOMAIN:-}"
EMAIL="${EMAIL:-}"
GCP_PROJECT_ID="${GCP_PROJECT_ID:-}"
PRODUCTION_PROJECT_ID="project-72a52bf1-06aa-4519-b2c"
GTS_ACME_SERVER="https://dv.acme-v02.api.pki.goog/directory"
GTS_ACCOUNT_DIR="/etc/letsencrypt/accounts/dv.acme-v02.api.pki.goog/directory"
NGINX_IMAGE="nginx:1.29-alpine@sha256:5616878291a2eed594aee8db4dade5878cf7edcb475e59193904b198d9b830de"
TRANSACTION_DIR="/var/lib/gole/certificate"
TRANSACTION_FILE="$TRANSACTION_DIR/nginx.transaction"
BACKUP_DIR="/var/backups/gole-certificate"
BACKUP_FILE="$BACKUP_DIR/nginx.conf.before-https"
CURRENT_NGINX_CONFIG="/etc/gole/nginx.conf"
EAB_DIR="/run/gole-certificate"
ROLLOUT_LOCK_FILE="/run/lock/gole-production-rollout.lock"

COMPOSE=(docker compose --env-file /etc/gole/infra.env --env-file /etc/gole/gole.env)
if [ -e /etc/gole/discord.env ] || [ -L /etc/gole/discord.env ]; then
  COMPOSE+=(--env-file /etc/gole/discord.env)
fi
COMPOSE+=(-f "$ROOT/infra/gcp/docker-compose.yml")

eab_file=""
rendered=""
headers_file=""
nginx_transaction_active=0
restoration_failed=0
poweroff_requested=0

request_poweroff() {
  if [ "$poweroff_requested" -eq 0 ]; then
    poweroff_requested=1
    systemctl poweroff --no-block >/dev/null 2>&1 || true
    echo "certificate Nginx recovery failed; VM poweroff requested" >&2
  fi
}

cleanup() {
  local cleanup_exit_code=$?
  trap - EXIT INT TERM
  set +e

  [ -z "$eab_file" ] || rm -f -- "$eab_file"
  [ -z "$rendered" ] || rm -f -- "$rendered"
  [ -z "$headers_file" ] || rm -f -- "$headers_file"

  if [ "$cleanup_exit_code" -ne 0 ] &&
    [ "$nginx_transaction_active" -eq 1 ] &&
    [ "$restoration_failed" -eq 0 ]; then
    if ! restore_nginx_transaction; then
      restoration_failed=1
      request_poweroff
      cleanup_exit_code=1
    fi
  fi

  exit "$cleanup_exit_code"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

die() {
  echo "$*" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || die "required command not found: $1"
}

fsync_file() {
  sync -f "$1"
}

fsync_directory() {
  sync -f "$1"
}

file_sha256() {
  sha256sum "$1" | awk '{print $1}'
}

validate_root_file() {
  local path="$1"
  local mode="$2"
  [ -f "$path" ] && [ ! -L "$path" ] &&
    [ "$(stat -c '%U:%G:%a' "$path")" = "root:root:$mode" ]
}

validate_root_directory() {
  local path="$1"
  local mode="$2"
  [ -d "$path" ] && [ ! -L "$path" ] &&
    [ "$(stat -c '%U:%G:%a' "$path")" = "root:root:$mode" ]
}

ensure_private_directory() {
  local path="$1"
  if [ -e "$path" ] || [ -L "$path" ]; then
    validate_root_directory "$path" 700 ||
      die "certificate transaction directory metadata is invalid"
  else
    install -d -m 0700 -o root -g root "$path"
    fsync_directory "$(dirname "$path")"
  fi
}

atomic_install_file() {
  local source="$1"
  local target="$2"
  local mode="$3"
  local target_directory candidate
  target_directory="$(dirname "$target")"
  candidate="$(mktemp "$target_directory/.certificate-install.XXXXXX")" || return 1
  if ! install -m "$mode" -o root -g root "$source" "$candidate" ||
    ! fsync_file "$candidate" ||
    ! mv -f -- "$candidate" "$target" ||
    ! fsync_file "$target" ||
    ! fsync_directory "$target_directory"; then
    rm -f -- "$candidate"
    return 1
  fi
}

write_nginx_transaction() {
  local state="$1"
  local backup_sha256="$2"
  local candidate_sha256="$3"
  local transaction_candidate
  [[ "$state" =~ ^(prepared|installed|verified|restoring|restored)$ ]] || return 1
  [[ "$backup_sha256" =~ ^[0-9a-f]{64}$ ]] || return 1
  [[ "$candidate_sha256" =~ ^[0-9a-f]{64}$ ]] || return 1

  transaction_candidate="$(mktemp "$TRANSACTION_DIR/.nginx.transaction.XXXXXX")" || return 1
  if ! printf '%s\n' \
    'version=1' \
    "state=$state" \
    "backup_path=$BACKUP_FILE" \
    "backup_sha256=$backup_sha256" \
    "candidate_sha256=$candidate_sha256" \
    "release_sha=${ROOT##*/}" >"$transaction_candidate" ||
    ! chown root:root "$transaction_candidate" ||
    ! chmod 0600 "$transaction_candidate" ||
    ! fsync_file "$transaction_candidate" ||
    ! mv -f -- "$transaction_candidate" "$TRANSACTION_FILE" ||
    ! fsync_file "$TRANSACTION_FILE" ||
    ! fsync_directory "$TRANSACTION_DIR"; then
    rm -f -- "$transaction_candidate"
    return 1
  fi
}

read_nginx_transaction() {
  local -a transaction_lines=()
  local current_sha256
  TX_STATE=""
  TX_BACKUP_SHA256=""
  TX_CANDIDATE_SHA256=""
  TX_RELEASE_SHA=""

  validate_root_file "$TRANSACTION_FILE" 600 || return 1
  mapfile -t transaction_lines <"$TRANSACTION_FILE" || return 1
  [ "${#transaction_lines[@]}" -eq 6 ] || return 1
  [ "${transaction_lines[0]}" = 'version=1' ] || return 1
  [[ "${transaction_lines[1]}" =~ ^state=(prepared|installed|verified|restoring|restored)$ ]] || return 1
  [ "${transaction_lines[2]}" = "backup_path=$BACKUP_FILE" ] || return 1
  [[ "${transaction_lines[3]}" =~ ^backup_sha256=([0-9a-f]{64})$ ]] || return 1
  TX_BACKUP_SHA256="${BASH_REMATCH[1]}"
  [[ "${transaction_lines[4]}" =~ ^candidate_sha256=([0-9a-f]{64})$ ]] || return 1
  TX_CANDIDATE_SHA256="${BASH_REMATCH[1]}"
  [[ "${transaction_lines[5]}" =~ ^release_sha=([0-9a-f]{40})$ ]] || return 1
  TX_RELEASE_SHA="${BASH_REMATCH[1]}"
  TX_STATE="${transaction_lines[1]#state=}"

  [ "$TX_RELEASE_SHA" = "${ROOT##*/}" ] || return 1
  validate_root_file "$BACKUP_FILE" 600 || return 1
  [ "$(file_sha256 "$BACKUP_FILE")" = "$TX_BACKUP_SHA256" ] || return 1
  validate_root_file "$CURRENT_NGINX_CONFIG" 644 || return 1
  current_sha256="$(file_sha256 "$CURRENT_NGINX_CONFIG")" || return 1
  case "$TX_STATE:$current_sha256" in
    "prepared:$TX_BACKUP_SHA256" | "prepared:$TX_CANDIDATE_SHA256" | \
      "installed:$TX_CANDIDATE_SHA256" | "verified:$TX_CANDIDATE_SHA256" | \
      "restoring:$TX_CANDIDATE_SHA256" | "restoring:$TX_BACKUP_SHA256" | \
      "restored:$TX_BACKUP_SHA256") ;;
    *) return 1 ;;
  esac
}

remove_transaction_journal() {
  rm -f -- "$TRANSACTION_FILE" || return 1
  # Before unlinking, the journal is always in a terminal state whose replay is
  # safe. If the directory fsync itself fails, either the terminal journal or
  # its absence survives a crash, and both outcomes are valid.
  fsync_directory "$TRANSACTION_DIR" || true
}

remove_backup_best_effort() {
  rm -f -- "$BACKUP_FILE" || return 0
  fsync_directory "$BACKUP_DIR" || true
}

restore_nginx_transaction() {
  read_nginx_transaction || return 1
  if [ "$TX_STATE" != restoring ] && [ "$TX_STATE" != restored ]; then
    write_nginx_transaction restoring "$TX_BACKUP_SHA256" \
      "$TX_CANDIDATE_SHA256" || return 1
    TX_STATE=restoring
  fi
  if [ "$TX_STATE" != restored ]; then
    atomic_install_file "$BACKUP_FILE" "$CURRENT_NGINX_CONFIG" 0644 || return 1
    write_nginx_transaction restored "$TX_BACKUP_SHA256" \
      "$TX_CANDIDATE_SHA256" || return 1
  fi
  "${COMPOSE[@]}" up -d --no-build --no-deps --force-recreate --wait nginx || return 1
  "${COMPOSE[@]}" exec -T nginx nginx -t || return 1
  remove_transaction_journal || return 1
  nginx_transaction_active=0
  remove_backup_best_effort
}

recover_stale_nginx_transaction() {
  if [ -e "$TRANSACTION_FILE" ] || [ -L "$TRANSACTION_FILE" ]; then
    nginx_transaction_active=1
    if ! restore_nginx_transaction; then
      restoration_failed=1
      request_poweroff
      die "could not recover the interrupted certificate Nginx transaction"
    fi
  elif [ -e "$BACKUP_FILE" ] || [ -L "$BACKUP_FILE" ]; then
    # A crash before the prepared journal fsync, or after the commit journal
    # unlink, can leave only the backup. In both windows the active config is
    # authoritative and the private orphan can be discarded.
    validate_root_file "$BACKUP_FILE" 600 ||
      die "orphaned certificate Nginx backup metadata is invalid"
    remove_backup_best_effort
  fi
}

begin_nginx_transaction() {
  local candidate="$1"
  local backup_candidate backup_sha256 candidate_sha256
  [ ! -e "$TRANSACTION_FILE" ] && [ ! -L "$TRANSACTION_FILE" ] || return 1
  [ ! -e "$BACKUP_FILE" ] && [ ! -L "$BACKUP_FILE" ] || return 1

  backup_candidate="$(mktemp "$BACKUP_DIR/.nginx.conf.XXXXXX")" || return 1
  if ! install -m 0600 -o root -g root "$CURRENT_NGINX_CONFIG" "$backup_candidate" ||
    ! fsync_file "$backup_candidate" ||
    ! mv -f -- "$backup_candidate" "$BACKUP_FILE" ||
    ! fsync_file "$BACKUP_FILE" ||
    ! fsync_directory "$BACKUP_DIR"; then
    rm -f -- "$backup_candidate"
    return 1
  fi

  backup_sha256="$(file_sha256 "$BACKUP_FILE")" || return 1
  candidate_sha256="$(file_sha256 "$candidate")" || return 1
  write_nginx_transaction prepared "$backup_sha256" "$candidate_sha256" || return 1
  nginx_transaction_active=1
  atomic_install_file "$candidate" "$CURRENT_NGINX_CONFIG" 0644 || return 1
  write_nginx_transaction installed "$backup_sha256" "$candidate_sha256" || return 1
}

commit_nginx_transaction() {
  read_nginx_transaction || return 1
  [ "$TX_STATE" = installed ] || return 1
  write_nginx_transaction verified "$TX_BACKUP_SHA256" \
    "$TX_CANDIDATE_SHA256" || return 1
  remove_transaction_journal || return 1
  nginx_transaction_active=0
  remove_backup_best_effort
}

clean_stale_eab_files() {
  local stale_eab
  shopt -s nullglob
  for stale_eab in "$EAB_DIR"/gts-eab.*; do
    validate_root_file "$stale_eab" 600 ||
      die "stale Google Public CA EAB file metadata is invalid"
    rm -f -- "$stale_eab"
  done
  shopt -u nullglob
}

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

verify_apex_tls_and_hsts() {
  local status
  headers_file="$(umask 077; mktemp "$EAB_DIR/apex-headers.XXXXXX")"
  status="$(curl --fail --silent --show-error --max-time 15 \
    --resolve "$DOMAIN:443:127.0.0.1" \
    --dump-header "$headers_file" --output /dev/null --write-out '%{http_code}' \
    "https://$DOMAIN/")"
  [ "$status" = 200 ] || die "production apex is not directly healthy over TLS"
  grep -Eiq '^strict-transport-security:[[:space:]]*max-age=31536000([;[:space:]]|$)' \
    "$headers_file" ||
    die "production apex does not return HSTS over TLS"
  rm -f -- "$headers_file"
  headers_file=""
}

verify_single_www_redirect() {
  local scheme="$1"
  local port response
  case "$scheme" in
    http) port=80 ;;
    https) port=443 ;;
    *) return 1 ;;
  esac
  response="$(curl --silent --show-error --max-time 15 \
    --resolve "www.$DOMAIN:$port:127.0.0.1" \
    --output /dev/null --write-out $'%{http_code}\t%{redirect_url}' \
    "$scheme://www.$DOMAIN/")"
  [ "$response" = $'301\thttps://gole.co.kr/' ] ||
    die "www.$DOMAIN does not return the single canonical 301 redirect over $scheme"
}

[ "$(id -u)" -eq 0 ] || die "certificate issuance must run as root"
[ "${GOLE_ROLLOUT_LOCK_HELD:-0}" = 1 ] ||
  die "certificate issuance requires the production rollout lock"
for required_command in awk docker find flock grep install mktemp readlink sha256sum \
  stat sync systemctl; do
  require_command "$required_command"
done
validate_root_file "$ROLLOUT_LOCK_FILE" 660 ||
  die "certificate issuance does not own the production rollout lock"
[ "$(readlink -f "/proc/$$/fd/8" 2>/dev/null || true)" = "$ROLLOUT_LOCK_FILE" ] ||
  die "certificate issuance does not own the production rollout lock"
flock -n 8 || die "certificate issuance does not own the production rollout lock"
[ "$DOMAIN" = gole.co.kr ] || die "certificate domain is not the production apex"
[ "$EMAIL" = coldingcontact@gmail.com ] || die "certificate contact is not the production account"
[ "$GCP_PROJECT_ID" = "$PRODUCTION_PROJECT_ID" ] ||
  die "certificate project is not the production project"
[[ "$ROOT" =~ ^/var/lib/gole/releases/[0-9a-f]{40}$ ]] ||
  die "trusted release root is invalid"
[ -d "$ROOT" ] && [ ! -L "$ROOT" ] &&
  [ "$(stat -c '%U:%G' "$ROOT")" = root:root ] ||
  die "trusted release root metadata is invalid"
[ -f "$ROOT/.gole-source-sha" ] && [ ! -L "$ROOT/.gole-source-sha" ] &&
  [ "$(cat "$ROOT/.gole-source-sha")" = "${ROOT##*/}" ] ||
  die "trusted release marker is invalid"
if find "$ROOT" -xdev \( -type l -o -perm /0022 -o ! -user root -o ! -group root \) \
  -print -quit | grep -q .; then
  die "trusted release contains an unsafe object"
fi
[ -f "$ROOT/infra/gcp/docker-compose.yml" ] &&
  [ -f "$ROOT/infra/gcp/nginx-https.conf.template" ] ||
  die "trusted release is missing certificate configuration"
validate_root_file "$CURRENT_NGINX_CONFIG" 644 ||
  die "current Nginx configuration is missing or invalid"

ensure_private_directory "$TRANSACTION_DIR"
ensure_private_directory "$BACKUP_DIR"
recover_stale_nginx_transaction

for required_command in curl gcloud openssl sed; do
  require_command "$required_command"
done
ensure_private_directory "$EAB_DIR"
clean_stale_eab_files

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

"${COMPOSE[@]}" up -d --no-build --no-deps --force-recreate --wait nginx

if ! gts_account_exists; then
  eab_file="$(umask 077; mktemp "$EAB_DIR/gts-eab.XXXXXX")"
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
  chmod 0600 "$eab_file"

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

certificate_root="/var/lib/docker/volumes/gole_letsencrypt/_data/live/$DOMAIN"
[ -s "$certificate_root/fullchain.pem" ] && [ -s "$certificate_root/privkey.pem" ] ||
  die "issued certificate lineage is incomplete"
openssl x509 -checkend 86400 -noout -in "$certificate_root/fullchain.pem" >/dev/null ||
  die "issued certificate expires too soon"
openssl x509 -checkhost "$DOMAIN" -noout -in "$certificate_root/fullchain.pem" >/dev/null ||
  die "issued certificate does not contain the apex"
openssl x509 -checkhost "www.$DOMAIN" -noout -in "$certificate_root/fullchain.pem" >/dev/null ||
  die "issued certificate does not contain www"

rendered="$(umask 077; mktemp /etc/gole/.nginx.certificate.XXXXXX)"
sed "s/__DOMAIN__/${DOMAIN//\//\\/}/g" \
  "$ROOT/infra/gcp/nginx-https.conf.template" >"$rendered"
chmod 0600 "$rendered"
docker run --rm --network none \
  --add-host backend:127.0.0.1 \
  --add-host frontend:127.0.0.1 \
  --volume "$rendered:/etc/nginx/conf.d/default.conf:ro" \
  --volume gole_letsencrypt:/etc/letsencrypt:ro \
  "$NGINX_IMAGE" nginx -t >/dev/null

begin_nginx_transaction "$rendered" || die "could not begin the certificate Nginx transaction"
# install(1)은 파일 inode를 교체한다. bind mount가 새 파일과 인증서를 보도록 컨테이너를 재생성한다.
"${COMPOSE[@]}" up -d --no-build --no-deps --force-recreate --wait nginx
"${COMPOSE[@]}" exec -T nginx nginx -t
verify_apex_tls_and_hsts
verify_single_www_redirect http
verify_single_www_redirect https
commit_nginx_transaction || die "could not commit the certificate Nginx transaction"
