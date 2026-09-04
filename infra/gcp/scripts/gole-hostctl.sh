#!/usr/bin/env bash
set -Eeuo pipefail

APP_ENV_FILE="/etc/gole/gole.env"
APP_ROOT="/app"
ADOPTION_BACKUP_DIR="/var/backups/gole-adoption"
ADOPTION_TRANSACTION_FILE="/etc/gole/gole.adoption.transaction"
BACKUP_DIR="/var/backups/gole-env"
DEPLOY_IDENTITY_FILE="/etc/gole/deploy-user"
DEPLOYED_SHA_FILE="/etc/gole/deployed.sha"
DEPLOYMENT_TRANSACTION_FILE="/etc/gole/deployment.transaction"
DISCORD_ENV_FILE="/etc/gole/discord.env"
RELEASE_ROOT="/var/lib/gole/releases"
ROOT_GIT_REPOSITORY="/var/lib/gole/repository.git"
GITHUB_RELEASE_VERIFIER="/usr/local/libexec/gole/verify-github-release.py"
FIXED_REPOSITORY_URL="https://github.com/GoLe-by-Colding/GoLe.git"
HOST_BOOTSTRAP_MARKER="/etc/gole/host-bootstrap.complete"
INITIAL_DEPLOY_FILE="/etc/gole/initial-deploy.pending"
ENV_VERSION_FILE="/etc/gole/gole.env.version"
ENV_TRANSACTION_FILE="/etc/gole/gole.env.transaction"
INFRA_ENV_FILE="/etc/gole/infra.env"
IMAGE_BACKUP_DIR="/var/backups/gole-images"
LOGICAL_BACKUP_HELPER="/usr/local/sbin/gole-backup-data"
MINIO_RECOVERY_MARKER="/var/backups/gole-data/MINIO_UNFREEZE_REQUIRED"
NGINX_BACKUP_DIR="/var/backups/gole-nginx"
NGINX_CONFIG_FILE="/etc/gole/nginx.conf"
NGINX_TRANSACTION_FILE="/etc/gole/nginx.conf.transaction"
NGINX_VALIDATION_IMAGE="nginx:1.29-alpine@sha256:5616878291a2eed594aee8db4dade5878cf7edcb475e59193904b198d9b830de"
BROKER_CONFIG_FILE="/etc/gole/cloud-broker.conf"
METADATA_FIREWALL="/usr/local/sbin/gole-metadata-firewall"
METADATA_MIGRATION_MARKER="/etc/gole/metadata-migration.pending"
PRODUCTION_SECRET_NAME="gole-production-env"
PRODUCTION_COMPOSE_FILE="$APP_ROOT/infra/gcp/docker-compose.yml"
PRODUCTION_COMPOSE_VALIDATOR="/usr/local/libexec/gole/validate-production-compose.py"
PRODUCTION_ENV_VALIDATOR="/usr/local/libexec/gole/validate-production-env.py"
CERTIFICATE_ISSUER="/usr/local/libexec/gole/issue-certificate.sh"

die() {
  echo "$*" >&2
  exit 1
}

# A function-local variable is no longer in scope when Bash runs an EXIT trap.
# Keep temporary paths in a process-global registry instead; this also avoids
# helper functions overwriting the transaction rollback EXIT handler.
GOLE_TEMP_FILES=()
GOLE_TEMP_DIRS=()
METADATA_RATCHET_STARTED=0
INITIAL_RESET_STARTED=0
declare -A SNAPSHOT_IMAGE_IDS=()
declare -A DATA_UPGRADE_CHANGES=()
declare -A DATA_UPGRADE_IMAGE_IDS=()
STRICT_DATA_UPGRADE_CHANGED=0

register_temp_file() { GOLE_TEMP_FILES+=("$1"); }
register_temp_dir() { GOLE_TEMP_DIRS+=("$1"); }
forget_temp_file() {
  local item target="$1"
  local -a kept=()
  for item in "${GOLE_TEMP_FILES[@]}"; do
    [ "$item" = "$target" ] || kept+=("$item")
  done
  GOLE_TEMP_FILES=("${kept[@]}")
}
forget_temp_dir() {
  local item target="$1"
  local -a kept=()
  for item in "${GOLE_TEMP_DIRS[@]}"; do
    [ "$item" = "$target" ] || kept+=("$item")
  done
  GOLE_TEMP_DIRS=("${kept[@]}")
}
cleanup_registered_temporaries() {
  local item
  for item in "${GOLE_TEMP_FILES[@]}"; do rm -f -- "$item"; done
  for item in "${GOLE_TEMP_DIRS[@]}"; do rm -rf -- "$item"; done
  GOLE_TEMP_FILES=()
  GOLE_TEMP_DIRS=()
}
default_exit_cleanup() {
  local status=$?
  trap - EXIT
  set +e
  cleanup_registered_temporaries
  if [ "$status" -ne 0 ] && [ "$METADATA_RATCHET_STARTED" -eq 1 ]; then
    systemctl poweroff --no-block || true
    echo "metadata isolation ratchet failed after closing began; VM powered off" >&2
  elif [ "$status" -ne 0 ] && [ "$INITIAL_RESET_STARTED" -eq 1 ]; then
    systemctl poweroff --no-block || true
    echo "initial deployment reset failed after cleanup began; VM powered off" >&2
  fi
  exit "$status"
}
trap default_exit_cleanup EXIT

if [ "$(id -u)" -ne 0 ]; then
  die "gole-hostctl must run through sudo"
fi
if [ ! -r "$DEPLOY_IDENTITY_FILE" ]; then
  die "missing deploy identity configuration"
fi

IFS=: read -r DEPLOY_USER DEPLOY_GROUP < "$DEPLOY_IDENTITY_FILE"
if [[ ! "$DEPLOY_USER" =~ ^[a-z_][a-z0-9_-]{0,31}$ ]] ||
  [[ ! "$DEPLOY_GROUP" =~ ^[a-z_][a-z0-9_-]{0,31}$ ]]; then
  die "invalid deploy identity configuration"
fi
if [ "${SUDO_USER:-root}" != "$DEPLOY_USER" ] && [ "${SUDO_USER:-root}" != "root" ]; then
  die "caller is not the configured deploy user"
fi

install -d -m 0755 -o root -g root /run/lock
exec 9>/run/lock/gole-hostctl.lock
flock -x 9

require_argument_count() {
  local expected="$1"
  shift
  if [ "$#" -ne "$expected" ]; then
    die "invalid argument count"
  fi
}

atomic_install() {
  local source="$1"
  local target="$2"
  local mode="$3"
  local group="$4"
  local staged
  staged="$(mktemp "$(dirname "$target")/.$(basename "$target").XXXXXX")"
  if ! install -m "$mode" -o root -g "$group" "$source" "$staged"; then
    rm -f -- "$staged"
    die "could not stage host state"
  fi
  if ! mv -f -- "$staged" "$target"; then
    rm -f -- "$staged"
    die "could not atomically activate host state"
  fi
}

sync_host_state() {
  local path="$1"
  sync -f "$path"
  sync -f "$(dirname "$path")"
}

remove_host_state() {
  local path="$1"
  rm -f -- "$path"
  sync -f "$(dirname "$path")"
}

validate_discord_environment() {
  local path="${1:-$DISCORD_ENV_FILE}" key line value line_count=0 size
  local -A seen=() values=()
  local -a expected_keys=(
    GOLE_DISCORD_ALERTS_ENABLED
    DISCORD_DEPLOY_WEBHOOK_URL
    DISCORD_OPERATIONS_WEBHOOK_URL
    DISCORD_ACCOUNT_WEBHOOK_URL
    DISCORD_PAYMENT_WEBHOOK_URL
    DISCORD_SUPPORT_WEBHOOK_URL
    DISCORD_SUPPRESS_NOTIFICATIONS
  )
  if [ ! -f "$path" ] || [ -L "$path" ] ||
    [ "$(stat -c '%U:%G:%a' "$path")" != "root:root:600" ]; then
    die "Discord environment file is missing or invalid"
  fi
  size="$(stat -c '%s' "$path")"
  if [ "$size" -le 0 ] || [ "$size" -gt 16384 ]; then
    die "Discord environment file size is invalid"
  fi
  while IFS= read -r line || [ -n "$line" ]; do
    line_count=$((line_count + 1))
    [[ "$line" != *$'\r'* ]] || die "Discord environment contains invalid line endings"
    [[ "$line" =~ ^([A-Z][A-Z0-9_]*)=(.*)$ ]] ||
      die "Discord environment contains invalid syntax"
    key="${BASH_REMATCH[1]}"
    value="${BASH_REMATCH[2]}"
    case "$key" in
      GOLE_DISCORD_ALERTS_ENABLED | DISCORD_DEPLOY_WEBHOOK_URL | \
        DISCORD_OPERATIONS_WEBHOOK_URL | DISCORD_ACCOUNT_WEBHOOK_URL | \
        DISCORD_PAYMENT_WEBHOOK_URL | DISCORD_SUPPORT_WEBHOOK_URL | \
        DISCORD_SUPPRESS_NOTIFICATIONS) ;;
      *) die "Discord environment contains an unknown key" ;;
    esac
    [ "${seen[$key]:-0}" -eq 0 ] || die "Discord environment contains a duplicate key"
    seen[$key]=1
    values[$key]="$value"
  done < "$path"
  [ "$line_count" -eq "${#expected_keys[@]}" ] ||
    die "Discord environment key count is invalid"
  for key in "${expected_keys[@]}"; do
    [ "${seen[$key]:-0}" -eq 1 ] || die "Discord environment is incomplete"
  done
  [ "${values[GOLE_DISCORD_ALERTS_ENABLED]}" = true ] ||
    die "Discord alerts must be enabled in production"
  [[ "${values[DISCORD_SUPPRESS_NOTIFICATIONS]}" =~ ^(true|false)$ ]] ||
    die "Discord notification suppression flag is invalid"
  for key in \
    DISCORD_DEPLOY_WEBHOOK_URL \
    DISCORD_OPERATIONS_WEBHOOK_URL \
    DISCORD_ACCOUNT_WEBHOOK_URL \
    DISCORD_PAYMENT_WEBHOOK_URL \
    DISCORD_SUPPORT_WEBHOOK_URL; do
    value="${values[$key]}"
    [[ "$value" =~ ^https://(discord\.com|discordapp\.com)/api/webhooks/[0-9]{1,32}/[A-Za-z0-9._-]{20,256}$ ]] ||
      die "Discord webhook URL is invalid"
  done
}

install_discord_environment_from_stdin() {
  local candidate size
  # The runner cannot choose a path or pass a value in argv. The rollout lock
  # is acquired by root for this short atomic operation, so an overlay cannot
  # change halfway through adoption, Secret Sync, deploy, or rollback.
  exec 8>>/run/lock/gole-production-rollout.lock
  flock -n 8 || die "another production rollout is active"
  candidate="$(mktemp /etc/gole/.discord.env.request.XXXXXX)"
  register_temp_file "$candidate"
  chmod 0600 "$candidate"
  head -c 16385 > "$candidate"
  size="$(stat -c '%s' "$candidate")"
  [ "$size" -le 16384 ] || die "Discord environment request is too large"
  validate_discord_environment "$candidate"
  atomic_install "$candidate" "$DISCORD_ENV_FILE" 0600 root
  rm -f -- "$candidate"
  forget_temp_file "$candidate"
}

container_environment_value() {
  local container="$1" key="$2" raw value
  raw="$(docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' "$container")" ||
    die "could not inspect the existing Discord environment"
  [ "${#raw}" -le 262144 ] || die "existing container environment is too large"
  value="$(printf '%s\n' "$raw" | awk -v prefix="$key=" '
    index($0, prefix) == 1 { count += 1; value = substr($0, length(prefix) + 1) }
    END { if (count != 1) exit 1; print value }
  ')" || die "existing container Discord environment is incomplete"
  printf '%s\n' "$value"
}

ensure_legacy_discord_environment() {
  local account deploy operations payment project_label service_label suppress support
  local budget_operations candidate
  if [ -e "$DISCORD_ENV_FILE" ] || [ -L "$DISCORD_ENV_FILE" ]; then
    validate_discord_environment
    return
  fi
  project_label="$(docker inspect --format '{{index .Config.Labels "com.docker.compose.project"}}' gole-backend)" ||
    die "could not verify the legacy backend owner"
  service_label="$(docker inspect --format '{{index .Config.Labels "com.docker.compose.service"}}' gole-backend)" ||
    die "could not verify the legacy backend service"
  [ "$project_label:$service_label" = "gole:backend" ] ||
    die "legacy backend Compose identity is invalid"
  project_label="$(docker inspect --format '{{index .Config.Labels "com.docker.compose.project"}}' gole-budget-relay)" ||
    die "could not verify the legacy budget relay owner"
  service_label="$(docker inspect --format '{{index .Config.Labels "com.docker.compose.service"}}' gole-budget-relay)" ||
    die "could not verify the legacy budget relay service"
  [ "$project_label:$service_label" = "gole:budget-relay" ] ||
    die "legacy budget relay Compose identity is invalid"

  operations="$(container_environment_value gole-backend DISCORD_OPERATIONS_WEBHOOK_URL)"
  account="$(container_environment_value gole-backend DISCORD_ACCOUNT_WEBHOOK_URL)"
  payment="$(container_environment_value gole-backend DISCORD_PAYMENT_WEBHOOK_URL)"
  if ! support="$(container_environment_value gole-backend DISCORD_SUPPORT_WEBHOOK_URL 2>/dev/null)" ||
    [ -z "$support" ]; then
    # The legacy release predates the support-specific route. Keep support in
    # the same trusted GoLe operations room. A support-specific GitHub Secret,
    # when later configured, takes precedence during the next atomic refresh.
    support="$operations"
  fi
  if ! deploy="$(container_environment_value gole-backend DISCORD_DEPLOY_WEBHOOK_URL 2>/dev/null)" ||
    [ -z "$deploy" ]; then
    deploy="$operations"
  fi
  if ! suppress="$(container_environment_value gole-backend DISCORD_SUPPRESS_NOTIFICATIONS 2>/dev/null)" ||
    [[ ! "$suppress" =~ ^(true|false)$ ]]; then
    suppress=false
  fi
  budget_operations="$(container_environment_value gole-budget-relay DISCORD_OPERATIONS_WEBHOOK_URL)"
  [ "$budget_operations" = "$operations" ] ||
    die "legacy Discord operations routes do not match"

  candidate="$(mktemp /etc/gole/.discord.env.legacy.XXXXXX)"
  register_temp_file "$candidate"
  chmod 0600 "$candidate"
  printf '%s\n' \
    'GOLE_DISCORD_ALERTS_ENABLED=true' \
    "DISCORD_DEPLOY_WEBHOOK_URL=$deploy" \
    "DISCORD_OPERATIONS_WEBHOOK_URL=$operations" \
    "DISCORD_ACCOUNT_WEBHOOK_URL=$account" \
    "DISCORD_PAYMENT_WEBHOOK_URL=$payment" \
    "DISCORD_SUPPORT_WEBHOOK_URL=$support" \
    "DISCORD_SUPPRESS_NOTIFICATIONS=$suppress" > "$candidate"
  validate_discord_environment "$candidate"
  atomic_install "$candidate" "$DISCORD_ENV_FILE" 0600 root
  rm -f -- "$candidate"
  forget_temp_file "$candidate"
}

read_env_version() {
  local version
  if [ ! -e "$ENV_VERSION_FILE" ] && [ ! -L "$ENV_VERSION_FILE" ]; then
    echo 0
    return
  fi
  if [ ! -f "$ENV_VERSION_FILE" ] || [ -L "$ENV_VERSION_FILE" ] ||
    [ "$(stat -c '%U:%G:%a' "$ENV_VERSION_FILE")" != "root:root:644" ]; then
    die "environment version marker metadata is invalid"
  fi
  version="$(cat "$ENV_VERSION_FILE")"
  [[ "$version" =~ ^[1-9][0-9]{0,11}$ ]] || die "environment version marker is invalid"
  printf '%s\n' "$version"
}

assert_initial_environment_empty() {
  local container path
  for path in \
    "$APP_ENV_FILE" \
    "$ENV_VERSION_FILE" \
    "$DEPLOYED_SHA_FILE" \
    "$INITIAL_DEPLOY_FILE" \
    "$ENV_TRANSACTION_FILE" \
    "$ADOPTION_TRANSACTION_FILE" \
    "$NGINX_TRANSACTION_FILE"; do
    if [ -e "$path" ] || [ -L "$path" ]; then
      die "initial environment bootstrap requires an empty host state"
    fi
  done
  for container in \
    gole-mongo gole-redis gole-minio gole-support-agent gole-backend \
    gole-frontend gole-budget-relay gole-nginx; do
    if docker inspect "$container" >/dev/null 2>&1; then
      die "initial environment bootstrap found an existing production container"
    fi
  done
}

validate_bootstrap_environment_candidate() {
  local source="$1" version="$2" source_mode="${3:-runner}" staged_candidate
  assert_initial_environment_empty
  if [ "$source_mode" = root ]; then
    source="$(validate_root_secret_candidate "$source")"
  elif [ "$source_mode" = runner ]; then
    source="$(validate_candidate "$source")"
  else
    die "invalid environment candidate trust mode"
  fi
  [[ "$version" =~ ^[1-9][0-9]{0,11}$ ]] || die "invalid secret version"
  staged_candidate="$(mktemp /etc/gole/.bootstrap.validation.XXXXXX)"
  register_temp_file "$staged_candidate"
  install -m 0600 -o root -g root "$source" "$staged_candidate"
  validate_production_environment "$staged_candidate"
  validate_production_compose "$staged_candidate"
  rm -f -- "$staged_candidate"
  forget_temp_file "$staged_candidate"
}

bootstrap_environment() {
  local source="$1"
  local version="$2"
  local source_mode="${3:-runner}"
  local candidate_sha256 environment_stage initial_deploy_stage version_stage

  assert_initial_environment_empty
  if [ "$source_mode" = root ]; then
    source="$(validate_root_secret_candidate "$source")"
  elif [ "$source_mode" = runner ]; then
    source="$(validate_candidate "$source")"
  else
    die "invalid environment candidate trust mode"
  fi
  [[ "$version" =~ ^[1-9][0-9]{0,11}$ ]] || die "invalid secret version"

  environment_stage="$(mktemp /etc/gole/.gole.env.bootstrap.XXXXXX)"
  version_stage="$(mktemp /etc/gole/.gole.env.version.bootstrap.XXXXXX)"
  initial_deploy_stage="$(mktemp /etc/gole/.initial-deploy.bootstrap.XXXXXX)"
  register_temp_file "$environment_stage"
  register_temp_file "$version_stage"
  register_temp_file "$initial_deploy_stage"
  install -m 0600 -o root -g root "$source" "$environment_stage"
  validate_production_environment "$environment_stage"
  validate_production_compose "$environment_stage"
  candidate_sha256="$(sha256sum "$environment_stage" | cut -d' ' -f1)"
  printf '%s\n' "$version" > "$version_stage"
  chown root:root "$version_stage"
  chmod 0644 "$version_stage"
  printf 'version=%s\nenv_sha256=%s\n' "$version" "$candidate_sha256" > "$initial_deploy_stage"
  chown root:root "$initial_deploy_stage"
  chmod 0600 "$initial_deploy_stage"

  # Hard-link activation is atomic and refuses to replace a file that appeared
  # after the preflight. Keep the staged inode until both links succeed so a
  # version-marker failure can remove only the environment installed here.
  if ! ln "$environment_stage" "$APP_ENV_FILE"; then
    die "could not atomically bootstrap the environment file"
  fi
  sync_host_state "$APP_ENV_FILE"
  if ! ln "$version_stage" "$ENV_VERSION_FILE"; then
    if [ "$(stat -c '%d:%i' "$environment_stage")" = "$(stat -c '%d:%i' "$APP_ENV_FILE")" ]; then
      rm -f -- "$APP_ENV_FILE"
    fi
    die "could not atomically bootstrap the environment version marker"
  fi
  sync_host_state "$ENV_VERSION_FILE"
  if ! ln "$initial_deploy_stage" "$INITIAL_DEPLOY_FILE"; then
    if [ "$(stat -c '%d:%i' "$version_stage")" = "$(stat -c '%d:%i' "$ENV_VERSION_FILE")" ]; then
      rm -f -- "$ENV_VERSION_FILE"
    fi
    if [ "$(stat -c '%d:%i' "$environment_stage")" = "$(stat -c '%d:%i' "$APP_ENV_FILE")" ]; then
      rm -f -- "$APP_ENV_FILE"
    fi
    die "could not atomically create the initial deployment marker"
  fi
  sync_host_state "$INITIAL_DEPLOY_FILE"
  rm -f -- "$environment_stage" "$version_stage" "$initial_deploy_stage"
  forget_temp_file "$environment_stage"
  forget_temp_file "$version_stage"
  forget_temp_file "$initial_deploy_stage"
}

install_cost_guard_watchdog() {
  local installed_file

  # The runner owns /app and must never be able to promote repository content
  # into a root executable or unit. A reviewed bootstrap installs these files;
  # this operation can only activate those immutable root-owned copies.
  for installed_file in \
    /usr/local/sbin/gole-cost-guard-watchdog \
    /etc/systemd/system/gole-cost-guard-watchdog.service \
    /etc/systemd/system/gole-cost-guard-watchdog.timer; do
    if [ ! -f "$installed_file" ] || [ -L "$installed_file" ]; then
      die "installed watchdog policy is missing or invalid"
    fi
  done
  [ "$(stat -c '%U:%G:%a' /usr/local/sbin/gole-cost-guard-watchdog)" = "root:root:755" ] ||
    die "installed watchdog command permissions are invalid"
  for installed_file in \
    /etc/systemd/system/gole-cost-guard-watchdog.service \
    /etc/systemd/system/gole-cost-guard-watchdog.timer; do
    [ "$(stat -c '%U:%G:%a' "$installed_file")" = "root:root:644" ] ||
      die "installed watchdog unit permissions are invalid"
  done
  systemctl daemon-reload
  systemctl enable --now gole-cost-guard-watchdog.timer
}

read_deployed_sha() {
  local sha
  if [ ! -f "$DEPLOYED_SHA_FILE" ] || [ -L "$DEPLOYED_SHA_FILE" ] ||
    [ "$(stat -c '%U:%G:%a' "$DEPLOYED_SHA_FILE")" != "root:root:644" ]; then
    die "last-known-good deployment marker is missing or invalid"
  fi
  sha="$(cat "$DEPLOYED_SHA_FILE")"
  [[ "$sha" =~ ^[0-9a-f]{40}$ ]] || die "last-known-good deployment SHA is invalid"
  printf '%s\n' "$sha"
}

write_deployed_sha_exact() {
  local candidate requested_sha="$1"
  [[ "$requested_sha" =~ ^[0-9a-f]{40}$ ]] || die "invalid deployment SHA marker"
  candidate="$(mktemp)"
  register_temp_file "$candidate"
  printf '%s\n' "$requested_sha" > "$candidate"
  atomic_install "$candidate" "$DEPLOYED_SHA_FILE" 0644 root
  sync_host_state "$DEPLOYED_SHA_FILE"
  rm -f -- "$candidate"
  forget_temp_file "$candidate"
}

validate_initial_deployment() {
  local key value marker_version="" marker_sha256=""
  local seen_version=0 seen_hash=0

  if [ -e "$DEPLOYED_SHA_FILE" ] || [ -L "$DEPLOYED_SHA_FILE" ]; then
    die "a successful deployment marker already exists"
  fi
  if [ -e "$ENV_TRANSACTION_FILE" ] || [ -L "$ENV_TRANSACTION_FILE" ]; then
    die "an environment transaction is active"
  fi
  if [ -e "$ADOPTION_TRANSACTION_FILE" ] || [ -L "$ADOPTION_TRANSACTION_FILE" ]; then
    die "an existing-deployment adoption transaction is active"
  fi
  if [ ! -f "$INITIAL_DEPLOY_FILE" ] || [ -L "$INITIAL_DEPLOY_FILE" ] ||
    [ "$(stat -c '%U:%G:%a' "$INITIAL_DEPLOY_FILE")" != "root:root:600" ]; then
    die "initial deployment authorization marker is missing or invalid"
  fi

  while IFS='=' read -r key value || [ -n "${key}${value}" ]; do
    case "$key" in
      version)
        [ "$seen_version" -eq 0 ] || die "initial deployment marker has duplicate version"
        marker_version="$value"
        seen_version=1
        ;;
      env_sha256)
        [ "$seen_hash" -eq 0 ] || die "initial deployment marker has duplicate hash"
        marker_sha256="$value"
        seen_hash=1
        ;;
      *) die "initial deployment marker contains an unknown field" ;;
    esac
  done < "$INITIAL_DEPLOY_FILE"
  [ "$seen_version$seen_hash" = "11" ] || die "initial deployment marker is incomplete"
  [[ "$marker_version" =~ ^[1-9][0-9]{0,11}$ ]] ||
    die "initial deployment marker version is invalid"
  [[ "$marker_sha256" =~ ^[0-9a-f]{64}$ ]] ||
    die "initial deployment marker hash is invalid"
  [ "$(read_env_version)" = "$marker_version" ] ||
    die "initial deployment environment version does not match"
  validate_current_environment
  [ "$(sha256sum "$APP_ENV_FILE" | cut -d' ' -f1)" = "$marker_sha256" ] ||
    die "initial deployment environment hash does not match"
}

validate_backup_path() {
  local path="$1"
  if [[ ! "$path" =~ ^/var/backups/gole-env/gole\.env\.[0-9]{8}T[0-9]{6}Z\.v[0-9]+\.[0-9a-fA-F-]{36}$ ]]; then
    die "invalid environment backup path"
  fi
  if [ ! -f "$path" ] || [ -L "$path" ]; then
    die "environment backup does not exist"
  fi
  [ "$(stat -c '%U:%G:%a' "$path")" = "root:root:600" ] ||
    die "environment backup metadata is invalid"
}

validate_candidate() {
  local path="$1"
  local resolved owner size
  if [ ! -f "$path" ] || [ -L "$path" ]; then
    die "environment candidate must be a regular file"
  fi
  resolved="$(readlink -f -- "$path")"
  [[ "$resolved" =~ ^/tmp/gole-env\.[A-Za-z0-9]+$ ]] || die "environment candidate path is invalid"
  owner="$(stat -c '%U' "$resolved")"
  [ "$owner" = "$DEPLOY_USER" ] || die "environment candidate ownership is invalid"
  size="$(stat -c '%s' "$resolved")"
  if [ "$size" -le 0 ] || [ "$size" -gt 131072 ]; then
    die "environment candidate size is invalid"
  fi
  printf '%s\n' "$resolved"
}

validate_root_secret_candidate() {
  local path="$1" resolved size
  if [ ! -f "$path" ] || [ -L "$path" ]; then
    die "root secret candidate must be a regular file"
  fi
  resolved="$(readlink -f -- "$path")"
  [[ "$resolved" =~ ^/etc/gole/\.secret\.[A-Za-z0-9]+$ ]] ||
    die "root secret candidate path is invalid"
  [ "$(stat -c '%U:%G:%a' "$resolved")" = "root:root:600" ] ||
    die "root secret candidate metadata is invalid"
  size="$(stat -c '%s' "$resolved")"
  if [ "$size" -le 0 ] || [ "$size" -gt 131072 ]; then
    die "root secret candidate size is invalid"
  fi
  printf '%s\n' "$resolved"
}

fetch_root_secret_candidate() {
  local project_id version="$1"
  [[ "$version" =~ ^[1-9][0-9]{0,11}$ ]] || die "invalid secret version"
  if [ ! -f "$BROKER_CONFIG_FILE" ] || [ -L "$BROKER_CONFIG_FILE" ] ||
    [ "$(stat -c '%U:%G:%a' "$BROKER_CONFIG_FILE")" != "root:root:600" ]; then
    die "cloud broker configuration is missing or invalid"
  fi
  [ "$(grep -Ec '^PROJECT_ID=' "$BROKER_CONFIG_FILE")" -eq 1 ] ||
    die "cloud broker project configuration is invalid"
  project_id="$(awk -F= '$1 == "PROJECT_ID" { print substr($0, index($0, "=") + 1) }' "$BROKER_CONFIG_FILE")"
  [[ "$project_id" =~ ^[a-z][a-z0-9-]{4,28}[a-z0-9]$ ]] ||
    die "cloud broker project configuration is invalid"
  [ -x /usr/bin/gcloud ] || die "root Secret Manager client is unavailable"
  ROOT_SECRET_CANDIDATE="$(mktemp /etc/gole/.secret.XXXXXXXX)"
  register_temp_file "$ROOT_SECRET_CANDIDATE"
  chown root:root "$ROOT_SECRET_CANDIDATE"
  chmod 0600 "$ROOT_SECRET_CANDIDATE"
  if ! env -i HOME=/root PATH=/usr/bin:/bin CLOUDSDK_CORE_DISABLE_PROMPTS=1 \
    /usr/bin/gcloud secrets versions access "$version" \
      --secret="$PRODUCTION_SECRET_NAME" \
      --project="$project_id" \
      --out-file="$ROOT_SECRET_CANDIDATE" \
      --quiet >/dev/null 2>&1; then
    rm -f -- "$ROOT_SECRET_CANDIDATE"
    forget_temp_file "$ROOT_SECRET_CANDIDATE"
    ROOT_SECRET_CANDIDATE=""
    die "could not fetch the fixed production secret version"
  fi
  validate_root_secret_candidate "$ROOT_SECRET_CANDIDATE" >/dev/null
}

validate_current_environment() {
  local size
  if [ ! -f "$APP_ENV_FILE" ] || [ -L "$APP_ENV_FILE" ] ||
    [ "$(stat -c '%U:%G:%a' "$APP_ENV_FILE")" != "root:root:600" ]; then
    die "current environment file is missing or invalid"
  fi
  size="$(stat -c '%s' "$APP_ENV_FILE")"
  if [ "$size" -le 0 ] || [ "$size" -gt 131072 ]; then
    die "current environment file size is invalid"
  fi
}

validate_production_environment() {
  local path="$1"
  if [ ! -x "$PRODUCTION_ENV_VALIDATOR" ] || [ -L "$PRODUCTION_ENV_VALIDATOR" ] ||
    [ "$(stat -c '%U:%G:%a' "$PRODUCTION_ENV_VALIDATOR")" != "root:root:755" ]; then
    die "production environment validator is missing or invalid"
  fi
  env -i HOME=/root PATH=/usr/bin:/bin PYTHONNOUSERSITE=1 \
    "$PRODUCTION_ENV_VALIDATOR" "$path" >/dev/null
}

validate_infra_environment() {
  local size
  if [ ! -f "$INFRA_ENV_FILE" ] || [ -L "$INFRA_ENV_FILE" ] ||
    [ "$(stat -c '%U:%G:%a' "$INFRA_ENV_FILE")" != "root:root:600" ]; then
    die "infrastructure environment file is missing or invalid"
  fi
  size="$(stat -c '%s' "$INFRA_ENV_FILE")"
  if [ "$size" -le 0 ] || [ "$size" -gt 131072 ]; then
    die "infrastructure environment file size is invalid"
  fi
}

validate_production_compose_validator() {
  if [ ! -x "$PRODUCTION_COMPOSE_VALIDATOR" ] || [ -L "$PRODUCTION_COMPOSE_VALIDATOR" ] ||
    [ "$(stat -c '%U:%G:%a' "$PRODUCTION_COMPOSE_VALIDATOR")" != "root:root:755" ]; then
    die "production Compose validator is missing or invalid"
  fi
}

production_compose() {
  local environment_file="$1"
  local -a environment_arguments=(
    --env-file "$INFRA_ENV_FILE"
    --env-file "$environment_file"
  )
  shift
  # Never forward the runner's environment into a root Docker process. All
  # interpolation comes from root-owned 0600 files and is then checked against
  # the rendered Compose allowlist before any build or container mutation.
  if [ -e "$DISCORD_ENV_FILE" ] || [ -L "$DISCORD_ENV_FILE" ]; then
    validate_discord_environment
    environment_arguments+=(--env-file "$DISCORD_ENV_FILE")
  elif [ "${GOLE_ALLOW_MISSING_DISCORD_OVERLAY:-0}" != 1 ]; then
    die "Discord environment overlay is required"
  fi
  env -i \
    HOME=/root \
    PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin \
    GOLE_APP_ENV_FILE="$environment_file" \
    GOLE_INFRA_ENV_FILE="$INFRA_ENV_FILE" \
    docker compose \
    "${environment_arguments[@]}" \
    -f "$PRODUCTION_COMPOSE_FILE" "$@"
}

validate_production_compose() {
  local environment_file="$1" mode="${2:-strict}" release_root
  local allow_missing_discord=0
  local validator_arguments=()
  validate_infra_environment
  validate_production_compose_validator
  if [ ! -f "$PRODUCTION_COMPOSE_FILE" ] || [ -L "$PRODUCTION_COMPOSE_FILE" ]; then
    die "production Compose file is missing or invalid"
  fi
  if [ "$mode" = "legacy-adoption" ]; then
    validator_arguments+=(--allow-legacy-adoption)
  elif [ "$mode" = "strict-lkg" ]; then
    validator_arguments+=(--allow-lkg-image-pins)
  elif [ "$mode" != "strict" ]; then
    die "invalid production Compose validation mode"
  fi
  if [[ "$PRODUCTION_COMPOSE_FILE" =~ ^(/var/lib/gole/releases/[0-9a-f]{40})/infra/gcp/docker-compose\.yml$ ]]; then
    release_root="${BASH_REMATCH[1]}"
    validator_arguments+=(--release-root "$release_root")
  fi
  if [ -e "$DISCORD_ENV_FILE" ] || [ -L "$DISCORD_ENV_FILE" ]; then
    validate_discord_environment
  else
    # Empty-host env validation and the pre-overlay read-only legacy check are
    # the only callers that can render with blank Discord interpolation. Any
    # Compose mutation is guarded separately and requires the root overlay.
    allow_missing_discord=1
    validator_arguments+=(--allow-missing-discord-overlay)
  fi
  local compose_validation_arguments=(config --format json)
  if [ "$mode" = strict ] || [ "$mode" = strict-lkg ]; then
    # Validate the normally dormant certificate profile too; otherwise a
    # reviewed main commit could hide a Docker-socket or host-path mount in the
    # root-owned renewal path while the base model still passed.
    compose_validation_arguments=(--profile certificate config --format json)
  fi
  if ! GOLE_ALLOW_MISSING_DISCORD_OVERLAY="$allow_missing_discord" \
    production_compose "$environment_file" "${compose_validation_arguments[@]}" 2>/dev/null |
    env -i HOME=/root PATH=/usr/bin:/bin PYTHONNOUSERSITE=1 \
      "$PRODUCTION_COMPOSE_VALIDATOR" "${validator_arguments[@]}" >/dev/null; then
    die "production Compose privilege policy validation failed"
  fi
}

validate_clean_checkout_sha() {
  local actual_sha requested_sha="$1"
  [[ "$requested_sha" =~ ^[0-9a-f]{40}$ ]] || die "invalid deployment SHA"
  if [ -L "$APP_ROOT" ] || [ ! -d "$APP_ROOT/.git" ] ||
    [ -n "$(runuser -u "$DEPLOY_USER" -- git -C "$APP_ROOT" status --porcelain=v1 --untracked-files=all)" ]; then
    die "production checkout is missing or not clean"
  fi
  actual_sha="$(runuser -u "$DEPLOY_USER" -- git -C "$APP_ROOT" rev-parse --verify HEAD)"
  [ "$actual_sha" = "$requested_sha" ] || die "deployment SHA does not match the clean checkout"
}

release_path() {
  [[ "$1" =~ ^[0-9a-f]{40}$ ]] || die "invalid release SHA"
  printf '%s/%s\n' "$RELEASE_ROOT" "$1"
}

validate_release() {
  local release requested_sha="$1"
  release="$(release_path "$requested_sha")"
  [ -d "$release" ] && [ ! -L "$release" ] || die "immutable release is missing"
  [ -f "$release/.gole-source-sha" ] && [ ! -L "$release/.gole-source-sha" ] ||
    die "immutable release marker is missing"
  [ "$(cat "$release/.gole-source-sha")" = "$requested_sha" ] ||
    die "immutable release marker does not match"
  [ "$(stat -c '%U:%G' "$release")" = "root:root" ] ||
    die "immutable release ownership is invalid"
  if find "$release" -xdev \( -type l -o -perm /0022 -o ! -user root -o ! -group root \) \
    -print -quit | grep -q .; then
    die "immutable release contains a symlink or writable object"
  fi
}

create_release() {
  local release requested_sha="$1" provenance="${2:-false}" staging remote_main
  # This function is entered through sudo.  Never let the caller's GIT_*
  # variables, HOME, PATH, system configuration, aliases or replace refs
  # influence the root trust repository.  The repository itself is root-only,
  # and every Git invocation below crosses this same sanitized boundary.
  local -a trusted_git=(
    env -i
    HOME=/root
    PATH=/usr/bin:/bin
    GIT_CONFIG_NOSYSTEM=1
    GIT_CONFIG_GLOBAL=/dev/null
    git
  )
  install -d -m 0700 -o root -g root "$RELEASE_ROOT"
  release="$(release_path "$requested_sha")"
  if [ -e "$release" ] || [ -L "$release" ]; then
    validate_release "$requested_sha"
    if [ "$provenance" = true ]; then
      remote_main="$("${trusted_git[@]}" ls-remote "$FIXED_REPOSITORY_URL" refs/heads/main |
        awk 'NR == 1 {print $1}')"
      [ "$remote_main" = "$requested_sha" ] || die "candidate is not current origin/main"
      [ -x "$GITHUB_RELEASE_VERIFIER" ] && [ ! -L "$GITHUB_RELEASE_VERIFIER" ] &&
        [ "$(stat -c '%U:%G:%a' "$GITHUB_RELEASE_VERIFIER")" = "root:root:755" ] ||
        die "GitHub release verifier is missing or invalid"
      env -i HOME=/root PATH=/usr/bin:/bin PYTHONNOUSERSITE=1 \
        "$GITHUB_RELEASE_VERIFIER" "$requested_sha" >/dev/null
    elif [ "$provenance" = historical-main ]; then
      [ -x "$GITHUB_RELEASE_VERIFIER" ] && [ ! -L "$GITHUB_RELEASE_VERIFIER" ] &&
        [ "$(stat -c '%U:%G:%a' "$GITHUB_RELEASE_VERIFIER")" = "root:root:755" ] ||
        die "GitHub release verifier is missing or invalid"
      env -i HOME=/root PATH=/usr/bin:/bin PYTHONNOUSERSITE=1 \
        "$GITHUB_RELEASE_VERIFIER" --historical-main "$requested_sha" >/dev/null
    elif [ "$provenance" != false ]; then
      die "invalid release provenance mode"
    fi
    return
  fi
  if [ ! -d "$ROOT_GIT_REPOSITORY" ]; then
    [ ! -e "$ROOT_GIT_REPOSITORY" ] && [ ! -L "$ROOT_GIT_REPOSITORY" ] ||
      die "root Git repository path is unsafe"
    "${trusted_git[@]}" init --bare "$ROOT_GIT_REPOSITORY" >/dev/null
    "${trusted_git[@]}" --git-dir="$ROOT_GIT_REPOSITORY" remote add origin "$FIXED_REPOSITORY_URL"
    chown -R root:root "$ROOT_GIT_REPOSITORY"
    chmod -R go-rwx "$ROOT_GIT_REPOSITORY"
  fi
  [ ! -L "$ROOT_GIT_REPOSITORY" ] &&
    [ "$(stat -c '%U:%G:%a' "$ROOT_GIT_REPOSITORY")" = "root:root:700" ] ||
    die "root Git repository metadata is invalid"
  [ "$("${trusted_git[@]}" --git-dir="$ROOT_GIT_REPOSITORY" remote get-url origin)" = "$FIXED_REPOSITORY_URL" ] ||
    die "root Git origin is invalid"
  if [ "$provenance" = true ]; then
    remote_main="$("${trusted_git[@]}" ls-remote "$FIXED_REPOSITORY_URL" refs/heads/main |
      awk 'NR == 1 {print $1}')"
    [ "$remote_main" = "$requested_sha" ] || die "candidate is not current origin/main"
    [ -x "$GITHUB_RELEASE_VERIFIER" ] && [ ! -L "$GITHUB_RELEASE_VERIFIER" ] &&
      [ "$(stat -c '%U:%G:%a' "$GITHUB_RELEASE_VERIFIER")" = "root:root:755" ] ||
      die "GitHub release verifier is missing or invalid"
    env -i HOME=/root PATH=/usr/bin:/bin PYTHONNOUSERSITE=1 \
      "$GITHUB_RELEASE_VERIFIER" "$requested_sha" >/dev/null
  elif [ "$provenance" = historical-main ]; then
    [ -x "$GITHUB_RELEASE_VERIFIER" ] && [ ! -L "$GITHUB_RELEASE_VERIFIER" ] &&
      [ "$(stat -c '%U:%G:%a' "$GITHUB_RELEASE_VERIFIER")" = "root:root:755" ] ||
      die "GitHub release verifier is missing or invalid"
    env -i HOME=/root PATH=/usr/bin:/bin PYTHONNOUSERSITE=1 \
      "$GITHUB_RELEASE_VERIFIER" --historical-main "$requested_sha" >/dev/null
  elif [ "$provenance" != false ]; then
    die "invalid release provenance mode"
  fi
  "${trusted_git[@]}" --git-dir="$ROOT_GIT_REPOSITORY" fetch --no-tags --force origin \
    "$requested_sha:refs/gole/candidate" >/dev/null 2>&1
  [ "$("${trusted_git[@]}" --git-dir="$ROOT_GIT_REPOSITORY" rev-parse refs/gole/candidate)" = "$requested_sha" ] ||
    die "root Git fetch did not resolve the requested commit"
  staging="$(mktemp -d "$RELEASE_ROOT/.release.XXXXXX")"
  register_temp_dir "$staging"
  "${trusted_git[@]}" --no-replace-objects --git-dir="$ROOT_GIT_REPOSITORY" \
    archive --format=tar "$requested_sha" |
    tar -x --no-same-owner --no-same-permissions -C "$staging"
  if find "$staging" -xdev -type l -print -quit | grep -q .; then
    die "release archive contains symbolic links"
  fi
  printf '%s\n' "$requested_sha" > "$staging/.gole-source-sha"
  chown -R root:root "$staging"
  chmod -R go-w "$staging"
  mv -- "$staging" "$release"
  forget_temp_dir "$staging"
  validate_release "$requested_sha"
}

select_release() {
  local requested_sha="$1" release
  validate_release "$requested_sha"
  release="$(release_path "$requested_sha")"
  PRODUCTION_COMPOSE_FILE="$release/infra/gcp/docker-compose.yml"
}

write_deployment_transaction() {
  local state="$1" target="$2" request_id="$3" new_sha="$4" previous_sha="$5" candidate
  candidate="$(mktemp)"
  printf '%s\n' \
    "state=$state" \
    "target=$target" \
    "request_id=$request_id" \
    "new_sha=$new_sha" \
    "previous_sha=$previous_sha" > "$candidate"
  atomic_install "$candidate" "$DEPLOYMENT_TRANSACTION_FILE" 0600 root
  rm -f -- "$candidate"
  sync_host_state "$DEPLOYMENT_TRANSACTION_FILE"
}

read_deployment_transaction() {
  local key value seen_state=0 seen_target=0 seen_request=0 seen_new=0 seen_previous=0
  [ -f "$DEPLOYMENT_TRANSACTION_FILE" ] && [ ! -L "$DEPLOYMENT_TRANSACTION_FILE" ] &&
    [ "$(stat -c '%U:%G:%a' "$DEPLOYMENT_TRANSACTION_FILE")" = "root:root:600" ] ||
    die "deployment transaction is missing or invalid"
  DEPLOY_TX_STATE="" DEPLOY_TX_TARGET="" DEPLOY_TX_REQUEST_ID="" DEPLOY_TX_NEW_SHA="" DEPLOY_TX_PREVIOUS_SHA=""
  while IFS='=' read -r key value || [ -n "${key}${value}" ]; do
    case "$key" in
      state) [ "$seen_state" -eq 0 ] || die "duplicate deployment state"; DEPLOY_TX_STATE="$value"; seen_state=1 ;;
      target) [ "$seen_target" -eq 0 ] || die "duplicate deployment target"; DEPLOY_TX_TARGET="$value"; seen_target=1 ;;
      request_id) [ "$seen_request" -eq 0 ] || die "duplicate deployment request"; DEPLOY_TX_REQUEST_ID="$value"; seen_request=1 ;;
      new_sha) [ "$seen_new" -eq 0 ] || die "duplicate deployment SHA"; DEPLOY_TX_NEW_SHA="$value"; seen_new=1 ;;
      previous_sha) [ "$seen_previous" -eq 0 ] || die "duplicate previous SHA"; DEPLOY_TX_PREVIOUS_SHA="$value"; seen_previous=1 ;;
      *) die "unknown deployment transaction field" ;;
    esac
  done < "$DEPLOYMENT_TRANSACTION_FILE"
  [ "$seen_state$seen_target$seen_request$seen_new$seen_previous" = "11111" ] ||
    die "deployment transaction is incomplete"
  [[ "$DEPLOY_TX_STATE" =~ ^(prepared|snapshotted|built|nginx-installed|mutation-armed|mutated|refreshed|budget-updated|verified|marker-recorded|initial-http-verified|runtime-verified|metadata-ratchet-armed|metadata-ratchet-verified|initial-reset-armed|cleanup-pending|rollback-restored)$ ]] ||
    die "deployment transaction state is invalid"
  validate_deployment_target "$DEPLOY_TX_TARGET"
  validate_request_id "$DEPLOY_TX_REQUEST_ID"
  [[ "$DEPLOY_TX_NEW_SHA" =~ ^[0-9a-f]{40}$ ]] || die "deployment SHA is invalid"
  [[ "$DEPLOY_TX_PREVIOUS_SHA" =~ ^(0|[0-9a-f]{40})$ ]] || die "previous deployment SHA is invalid"
}

require_deployment_transaction() {
  local request_id="$1" expected_state="$2"
  read_deployment_transaction
  [ "$DEPLOY_TX_REQUEST_ID" = "$request_id" ] || die "deployment request does not match"
  [[ ",$expected_state," == *",$DEPLOY_TX_STATE,"* ]] ||
    die "deployment operation is out of order"
}

begin_deployment_transaction() {
  local target="$1" new_sha="$2" previous_sha="$3" request_id="$4" marker_sha
  validate_deployment_target "$target"
  validate_request_id "$request_id"
  [[ "$new_sha" =~ ^[0-9a-f]{40}$ ]] || die "invalid deployment SHA"
  [[ "$previous_sha" =~ ^(0|[0-9a-f]{40})$ ]] || die "invalid previous deployment SHA"
  [ ! -e "$DEPLOYMENT_TRANSACTION_FILE" ] && [ ! -L "$DEPLOYMENT_TRANSACTION_FILE" ] ||
    die "a deployment transaction is already active"
  if [ "$target" != all ] && { [ -e "$METADATA_MIGRATION_MARKER" ] ||
    [ -L "$METADATA_MIGRATION_MARKER" ]; }; then
    die "metadata migration pending requires a full deployment"
  fi
  if [ "$previous_sha" = "0" ]; then
    validate_initial_deployment
  else
    marker_sha="$(read_deployed_sha)"
    [ "$marker_sha" = "$previous_sha" ] || die "previous deployment SHA is not the LKG marker"
    create_release "$previous_sha" false
    if [ "$target" = all ]; then
      select_release "$previous_sha"
      verify_seller_identity_launch_preflight
    fi
  fi
  create_release "$new_sha" true
  write_deployment_transaction prepared "$target" "$request_id" "$new_sha" "$previous_sha"
}

advance_deployment_transaction() {
  local request_id="$1" expected="$2" next="$3"
  require_deployment_transaction "$request_id" "$expected"
  write_deployment_transaction "$next" "$DEPLOY_TX_TARGET" "$DEPLOY_TX_REQUEST_ID" \
    "$DEPLOY_TX_NEW_SHA" "$DEPLOY_TX_PREVIOUS_SHA"
}

validate_existing_deployment_runtime() {
  local container enforce_policy="$2" health_state requested_sha="$1" version

  [[ "$requested_sha" =~ ^[0-9a-f]{40}$ ]] || die "invalid deployment SHA"
  validate_current_environment
  version="$(read_env_version)"
  [ "$version" -gt 0 ] || die "an existing environment version marker is required"
  if [ "$enforce_policy" = "true" ]; then
    validate_production_environment "$APP_ENV_FILE"
  fi

  validate_clean_checkout_sha "$requested_sha"
  validate_production_compose "$APP_ENV_FILE" legacy-adoption

  for container in gole-backend gole-frontend gole-budget-relay; do
    health_state="$(docker inspect --format \
      '{{.State.Status}}:{{if .State.Health}}{{.State.Health.Status}}{{else}}missing{{end}}' \
      "$container" 2>/dev/null || true)"
    [ "$health_state" = "running:healthy" ] || die "required production container is not healthy"
  done
  curl -fsS http://127.0.0.1:8080/actuator/health/readiness >/dev/null ||
    die "backend readiness check failed"
  curl -fsS http://127.0.0.1:3000/icon.svg >/dev/null || die "frontend readiness check failed"
  systemctl is-active --quiet gole-cost-guard-watchdog.timer ||
    die "cost guard watchdog timer is not active"
}

assert_existing_adoption_markers_empty() {
  if [ -e "$DEPLOYED_SHA_FILE" ] || [ -L "$DEPLOYED_SHA_FILE" ]; then
    die "a successful deployment marker already exists"
  fi
  if [ -e "$INITIAL_DEPLOY_FILE" ] || [ -L "$INITIAL_DEPLOY_FILE" ]; then
    die "an initial deployment authorization marker already exists"
  fi
  if [ -e "$ENV_TRANSACTION_FILE" ] || [ -L "$ENV_TRANSACTION_FILE" ]; then
    die "an environment transaction is active"
  fi
  if [ -e "$NGINX_TRANSACTION_FILE" ] || [ -L "$NGINX_TRANSACTION_FILE" ]; then
    die "an Nginx configuration transaction is active"
  fi
}

validate_adoption_backup_path() {
  local path="$1"
  [[ "$path" =~ ^/var/backups/gole-adoption/gole\.env\.[0-9a-fA-F-]{36}$ ]] ||
    die "invalid adoption backup path"
  if [ ! -f "$path" ] || [ -L "$path" ] ||
    [ "$(stat -c '%U:%G:%a' "$path")" != "root:root:600" ]; then
    die "adoption backup is missing or invalid"
  fi
}

adoption_backup_path() {
  local request_id="$1"
  validate_request_id "$request_id"
  printf '%s/gole.env.%s\n' "$ADOPTION_BACKUP_DIR" "$request_id"
}

cleanup_adoption_backup_artifact() {
  local backup_file request_id="$1"
  validate_request_id "$request_id"
  backup_file="$(adoption_backup_path "$request_id")"
  if [ -e "$backup_file" ] || [ -L "$backup_file" ]; then
    validate_adoption_backup_path "$backup_file"
    remove_host_state "$backup_file"
  else
    sync -f "$ADOPTION_BACKUP_DIR"
  fi
}

write_adoption_transaction() {
  local backup_file="$5" candidate_sha256="$6" previous_version="$2"
  local request_id="$4" requested_version="$3" state="$1" adoption_sha="$7"
  local transaction_candidate
  transaction_candidate="$(mktemp)"
  printf '%s\n' \
    "state=$state" \
    "previous_version=$previous_version" \
    "requested_version=$requested_version" \
    "request_id=$request_id" \
    "backup_file=$backup_file" \
    "candidate_sha256=$candidate_sha256" \
    "adoption_sha=$adoption_sha" > "$transaction_candidate"
  atomic_install "$transaction_candidate" "$ADOPTION_TRANSACTION_FILE" 0600 root
  rm -f -- "$transaction_candidate"
  sync_host_state "$ADOPTION_TRANSACTION_FILE"
}

read_adoption_transaction() {
  local key value
  local seen_state=0 seen_previous=0 seen_requested=0 seen_request=0 seen_backup=0 seen_hash=0 seen_sha=0
  if [ ! -e "$ADOPTION_TRANSACTION_FILE" ] && [ ! -L "$ADOPTION_TRANSACTION_FILE" ]; then
    return 1
  fi
  if [ ! -f "$ADOPTION_TRANSACTION_FILE" ] || [ -L "$ADOPTION_TRANSACTION_FILE" ] ||
    [ "$(stat -c '%U:%G:%a' "$ADOPTION_TRANSACTION_FILE")" != "root:root:600" ]; then
    die "adoption transaction metadata is invalid"
  fi
  ADOPT_STATE=""
  ADOPT_PREVIOUS_VERSION=""
  ADOPT_REQUESTED_VERSION=""
  ADOPT_REQUEST_ID=""
  ADOPT_BACKUP_FILE=""
  ADOPT_CANDIDATE_SHA256=""
  ADOPT_DEPLOYMENT_SHA=""
  while IFS='=' read -r key value || [ -n "${key}${value}" ]; do
    case "$key" in
      state)
        [ "$seen_state" -eq 0 ] || die "adoption transaction has duplicate state"
        ADOPT_STATE="$value"
        seen_state=1
        ;;
      previous_version)
        [ "$seen_previous" -eq 0 ] || die "adoption transaction has duplicate previous version"
        ADOPT_PREVIOUS_VERSION="$value"
        seen_previous=1
        ;;
      requested_version)
        [ "$seen_requested" -eq 0 ] || die "adoption transaction has duplicate requested version"
        ADOPT_REQUESTED_VERSION="$value"
        seen_requested=1
        ;;
      request_id)
        [ "$seen_request" -eq 0 ] || die "adoption transaction has duplicate request id"
        ADOPT_REQUEST_ID="$value"
        seen_request=1
        ;;
      backup_file)
        [ "$seen_backup" -eq 0 ] || die "adoption transaction has duplicate backup path"
        ADOPT_BACKUP_FILE="$value"
        seen_backup=1
        ;;
      candidate_sha256)
        [ "$seen_hash" -eq 0 ] || die "adoption transaction has duplicate candidate hash"
        ADOPT_CANDIDATE_SHA256="$value"
        seen_hash=1
        ;;
      adoption_sha)
        [ "$seen_sha" -eq 0 ] || die "adoption transaction has duplicate deployment SHA"
        ADOPT_DEPLOYMENT_SHA="$value"
        seen_sha=1
        ;;
      *) die "adoption transaction contains an unknown field" ;;
    esac
  done < "$ADOPTION_TRANSACTION_FILE"
  [ "$seen_state$seen_previous$seen_requested$seen_request$seen_backup$seen_hash$seen_sha" = \
    "1111111" ] || die "adoption transaction is incomplete"
  [[ "$ADOPT_STATE" =~ ^(prepared|snapshotted|installed|ready|committed|adopted|rollback-restored)$ ]] ||
    die "adoption transaction state is invalid"
  [[ "$ADOPT_PREVIOUS_VERSION" =~ ^[1-9][0-9]{0,11}$ ]] ||
    die "adoption transaction previous version is invalid"
  [[ "$ADOPT_REQUESTED_VERSION" =~ ^[1-9][0-9]{0,11}$ ]] ||
    die "adoption transaction requested version is invalid"
  [ "$ADOPT_REQUESTED_VERSION" -gt "$ADOPT_PREVIOUS_VERSION" ] ||
    die "adoption transaction version order is invalid"
  validate_request_id "$ADOPT_REQUEST_ID"
  validate_adoption_backup_path "$ADOPT_BACKUP_FILE"
  [[ "$ADOPT_CANDIDATE_SHA256" =~ ^[0-9a-f]{64}$ ]] ||
    die "adoption transaction hash is invalid"
  [[ "$ADOPT_DEPLOYMENT_SHA" =~ ^[0-9a-f]{40}$ ]] ||
    die "adoption transaction deployment SHA is invalid"
}

require_matching_adoption_transaction() {
  local request_id="$1"
  read_adoption_transaction || die "adoption transaction is missing"
  [ "$ADOPT_REQUEST_ID" = "$request_id" ] || die "adoption transaction request does not match"
}

require_exact_adoption_invocation() {
  local adoption_sha="$1" requested_version="$2" request_id="$3"
  [ "$ADOPT_DEPLOYMENT_SHA" = "$adoption_sha" ] &&
    [ "$ADOPT_REQUESTED_VERSION" = "$requested_version" ] &&
    [ "$ADOPT_REQUEST_ID" = "$request_id" ] ||
    die "adoption recovery invocation does not exactly match the active transaction"
}

begin_adoption_transaction() {
  local adoption_sha="$4" backup_file candidate candidate_sha256 previous_version
  local request_id="$3" requested_version="$2" source="$1" staged_candidate
  local source_mode="${5:-runner}"

  if [ -e "$ADOPTION_TRANSACTION_FILE" ] || [ -L "$ADOPTION_TRANSACTION_FILE" ]; then
    die "an adoption transaction is already active"
  fi
  assert_existing_adoption_markers_empty
  validate_request_id "$request_id"
  [[ "$adoption_sha" =~ ^[0-9a-f]{40}$ ]] || die "invalid adoption deployment SHA"
  [[ "$requested_version" =~ ^[1-9][0-9]{0,11}$ ]] || die "invalid secret version"
  # The one-time live adoption intentionally records the exact already-running
  # LKG, which can be behind current main once this migration code lands.  It
  # must still be an ancestor of current main with a successful main-push CI.
  create_release "$adoption_sha" historical-main
  select_release "$adoption_sha"
  validate_existing_deployment_runtime "$adoption_sha" false
  previous_version="$(read_env_version)"
  [ "$requested_version" -gt "$previous_version" ] ||
    die "adoption secret version must advance the marker"
  if [ "$source_mode" = root ]; then
    source="$(validate_root_secret_candidate "$source")"
  elif [ "$source_mode" = runner ]; then
    source="$(validate_candidate "$source")"
  else
    die "invalid environment candidate trust mode"
  fi

  staged_candidate="$(mktemp /etc/gole/.adoption.candidate.XXXXXX)"
  register_temp_file "$staged_candidate"
  install -m 0600 -o root -g root "$source" "$staged_candidate"
  validate_production_environment "$staged_candidate"
  validate_production_compose "$staged_candidate" legacy-adoption
  # Recheck the old deployment immediately before the first root mutation.
  validate_existing_deployment_runtime "$adoption_sha" false

  install -d -m 0700 -o root -g root "$ADOPTION_BACKUP_DIR"
  # A terminal adoption deliberately removes its journal before best-effort
  # artifact cleanup. Reclaim only the exact, validated request-scoped orphan
  # so the documented identical retry remains idempotent after a power loss.
  cleanup_adoption_backend_image_artifacts "$request_id"
  cleanup_adoption_backup_artifact "$request_id"
  backup_file="$(adoption_backup_path "$request_id")"
  install -m 0600 -o root -g root "$APP_ENV_FILE" "$backup_file"
  sync_host_state "$backup_file"
  candidate_sha256="$(sha256sum "$staged_candidate" | cut -d' ' -f1)"
  write_adoption_transaction prepared "$previous_version" "$requested_version" \
    "$request_id" "$backup_file" "$candidate_sha256" "$adoption_sha"
  snapshot_adoption_backend_image "$request_id"
  write_adoption_transaction snapshotted "$previous_version" "$requested_version" \
    "$request_id" "$backup_file" "$candidate_sha256" "$adoption_sha"
  atomic_install "$staged_candidate" "$APP_ENV_FILE" 0600 root
  sync_host_state "$APP_ENV_FILE"
  write_adoption_transaction installed "$previous_version" "$requested_version" \
    "$request_id" "$backup_file" "$candidate_sha256" "$adoption_sha"
  rm -f -- "$staged_candidate"
  forget_temp_file "$staged_candidate"
}

mark_adoption_transaction_ready() {
  local request_id="$1"
  require_matching_adoption_transaction "$request_id"
  [ "$ADOPT_STATE" = "installed" ] || die "adoption transaction is not installed"
  validate_current_environment
  [ "$(sha256sum "$APP_ENV_FILE" | cut -d' ' -f1)" = "$ADOPT_CANDIDATE_SHA256" ] ||
    die "adoption environment changed during rollout"
  validate_existing_deployment_runtime "$ADOPT_DEPLOYMENT_SHA" true
  write_adoption_transaction ready "$ADOPT_PREVIOUS_VERSION" "$ADOPT_REQUESTED_VERSION" \
    "$ADOPT_REQUEST_ID" "$ADOPT_BACKUP_FILE" "$ADOPT_CANDIDATE_SHA256" "$ADOPT_DEPLOYMENT_SHA"
}

commit_adoption_transaction() {
  local current_version request_id="$1"
  require_matching_adoption_transaction "$request_id"
  [ "$ADOPT_STATE" = "ready" ] || die "adoption transaction is not ready"
  validate_existing_deployment_runtime "$ADOPT_DEPLOYMENT_SHA" true
  [ "$(sha256sum "$APP_ENV_FILE" | cut -d' ' -f1)" = "$ADOPT_CANDIDATE_SHA256" ] ||
    die "adoption environment changed before commit"
  current_version="$(read_env_version)"
  [ "$current_version" = "$ADOPT_PREVIOUS_VERSION" ] ||
    die "environment version changed before adoption commit"
  write_env_version_exact "$ADOPT_REQUESTED_VERSION"
  write_adoption_transaction committed "$ADOPT_PREVIOUS_VERSION" "$ADOPT_REQUESTED_VERSION" \
    "$ADOPT_REQUEST_ID" "$ADOPT_BACKUP_FILE" "$ADOPT_CANDIDATE_SHA256" "$ADOPT_DEPLOYMENT_SHA"

  write_deployed_sha_exact "$ADOPT_DEPLOYMENT_SHA"
  write_adoption_transaction adopted "$ADOPT_PREVIOUS_VERSION" "$ADOPT_REQUESTED_VERSION" \
    "$ADOPT_REQUEST_ID" "$ADOPT_BACKUP_FILE" "$ADOPT_CANDIDATE_SHA256" "$ADOPT_DEPLOYMENT_SHA"
}

finalize_adoption_transaction() {
  local request_id="$1"
  require_matching_adoption_transaction "$request_id"
  [ "$ADOPT_STATE" = "adopted" ] || die "adoption transaction is not adopted"
  validate_current_environment
  [ "$(sha256sum "$APP_ENV_FILE" | cut -d' ' -f1)" = "$ADOPT_CANDIDATE_SHA256" ] ||
    die "adopted environment hash is invalid"
  [ "$(read_env_version)" = "$ADOPT_REQUESTED_VERSION" ] ||
    die "adopted environment version is invalid"
  [ "$(read_deployed_sha)" = "$ADOPT_DEPLOYMENT_SHA" ] ||
    die "adopted deployment SHA is invalid"
  verify_adoption_backend_image_runtime "$request_id"
  remove_host_state "$ADOPTION_TRANSACTION_FILE"
  cleanup_adoption_backend_image_artifacts "$request_id"
  cleanup_adoption_backup_artifact "$request_id"
}

restore_adoption_transaction() {
  local current_deployed_sha
  validate_adoption_backup_path "$ADOPT_BACKUP_FILE"
  if [ -e "$DEPLOYED_SHA_FILE" ] || [ -L "$DEPLOYED_SHA_FILE" ]; then
    current_deployed_sha="$(read_deployed_sha)"
    [ "$current_deployed_sha" = "$ADOPT_DEPLOYMENT_SHA" ] ||
      die "deployment marker changed outside the adoption transaction"
    remove_host_state "$DEPLOYED_SHA_FILE"
  fi
  atomic_install "$ADOPT_BACKUP_FILE" "$APP_ENV_FILE" 0600 root
  sync_host_state "$APP_ENV_FILE"
  write_env_version_exact "$ADOPT_PREVIOUS_VERSION"
  write_adoption_transaction rollback-restored "$ADOPT_PREVIOUS_VERSION" \
    "$ADOPT_REQUESTED_VERSION" "$ADOPT_REQUEST_ID" "$ADOPT_BACKUP_FILE" \
    "$ADOPT_CANDIDATE_SHA256" "$ADOPT_DEPLOYMENT_SHA"
}

abort_adoption_transaction() {
  local request_id="$1"
  require_matching_adoption_transaction "$request_id"
  if [ "$ADOPT_STATE" = prepared ]; then
    ensure_adoption_backend_image_snapshot "$request_id"
  fi
  if [ "$ADOPT_STATE" != "rollback-restored" ]; then
    restore_adoption_transaction
  fi
}

recover_adoption_transaction() {
  local current_deployed_sha current_version
  if ! read_adoption_transaction; then
    echo "NONE"
    return
  fi
  if [ "$ADOPT_STATE" = "committed" ] || [ "$ADOPT_STATE" = "adopted" ]; then
    current_deployed_sha="$(read_deployed_sha 2>/dev/null || true)"
    current_version="$(read_env_version 2>/dev/null || true)"
    if [ "$current_deployed_sha" = "$ADOPT_DEPLOYMENT_SHA" ] &&
      [ "$current_version" = "$ADOPT_REQUESTED_VERSION" ] &&
      [ -f "$APP_ENV_FILE" ] && [ ! -L "$APP_ENV_FILE" ] &&
      [ "$(sha256sum "$APP_ENV_FILE" 2>/dev/null | cut -d' ' -f1)" = \
        "$ADOPT_CANDIDATE_SHA256" ]; then
      verify_adoption_backend_image_runtime "$ADOPT_REQUEST_ID"
      remove_host_state "$ADOPTION_TRANSACTION_FILE"
      cleanup_adoption_backend_image_artifacts "$ADOPT_REQUEST_ID"
      cleanup_adoption_backup_artifact "$ADOPT_REQUEST_ID"
      echo "COMMITTED"
      return
    fi
  fi
  if [ "$ADOPT_STATE" = prepared ]; then
    ensure_adoption_backend_image_snapshot "$ADOPT_REQUEST_ID"
  fi
  restore_adoption_transaction
  echo "RECOVERY_REQUIRED:$ADOPT_REQUEST_ID"
}

finish_adoption_recovery() {
  local request_id="$1"
  require_matching_adoption_transaction "$request_id"
  [ "$ADOPT_STATE" = "rollback-restored" ] || die "adoption recovery is not ready to finish"
  validate_current_environment
  [ "$(sha256sum "$APP_ENV_FILE" | cut -d' ' -f1)" = \
    "$(sha256sum "$ADOPT_BACKUP_FILE" | cut -d' ' -f1)" ] ||
    die "recovered adoption environment hash is invalid"
  [ "$(read_env_version)" = "$ADOPT_PREVIOUS_VERSION" ] ||
    die "recovered adoption environment version is invalid"
  if [ -e "$DEPLOYED_SHA_FILE" ] || [ -L "$DEPLOYED_SHA_FILE" ]; then
    die "deployment marker remains after adoption rollback"
  fi
  verify_adoption_backend_image_runtime "$request_id"
  remove_host_state "$ADOPTION_TRANSACTION_FILE"
  cleanup_adoption_backend_image_artifacts "$request_id"
  cleanup_adoption_backup_artifact "$request_id"
}

validate_deployment_target() {
  case "$1" in
    all | backend | frontend) ;;
    *) die "invalid deployment target" ;;
  esac
}

deployment_image_services() {
  local mode="${2:-strict}" target="$1"
  case "$target:$mode" in
    all:initial | all:legacy-adoption)
      printf '%s\n' \
        mongo mongo-init redis minio minio-init support-agent backend frontend nginx budget-relay
      ;;
    all:strict)
      # Data containers are immutable rollback provenance even when an ordinary
      # application deploy leaves them running. Capturing IDs/tags is harmless;
      # recreation is gated separately by an exact pinned-reference change.
      printf '%s\n' \
        mongo mongo-init redis minio minio-init support-agent backend frontend nginx budget-relay
      ;;
    backend:strict)
      printf '%s\n' support-agent backend nginx
      ;;
    frontend:strict) printf '%s\n' frontend nginx ;;
    *) die "invalid deployment target" ;;
  esac
}

deployment_long_running_service() {
  case "$1" in
    mongo | redis | minio | support-agent | backend | frontend | nginx | budget-relay) return 0 ;;
    mongo-init | minio-init) return 1 ;;
    *) die "invalid deployment image service" ;;
  esac
}

deployment_container_name() {
  case "$1" in
    mongo) printf 'gole-mongo\n' ;;
    redis) printf 'gole-redis\n' ;;
    minio) printf 'gole-minio\n' ;;
    support-agent) printf 'gole-support-agent\n' ;;
    backend) printf 'gole-backend\n' ;;
    frontend) printf 'gole-frontend\n' ;;
    nginx) printf 'gole-nginx\n' ;;
    budget-relay) printf 'gole-budget-relay\n' ;;
    mongo-init | minio-init) return 1 ;;
    *) die "invalid deployment image service" ;;
  esac
}

deployment_rollback_image() {
  local request_id="$2" service="$1"
  validate_request_id "$request_id"
  case "$service" in
    mongo | mongo-init | redis | minio | minio-init | support-agent | backend | frontend | nginx | budget-relay) ;;
    *) die "invalid rollback image service" ;;
  esac
  printf 'gole/rollback-%s:%s\n' "$service" "${request_id//-/}"
}

deployment_image_marker() {
  local compact_request_id request_id="$1"
  validate_request_id "$request_id"
  compact_request_id="${request_id//-/}"
  printf '%s/images.%s\n' "$IMAGE_BACKUP_DIR" "$compact_request_id"
}

deployment_image_snapshot_mode() {
  local previous_sha="$1"
  if [ "$previous_sha" = 0 ]; then
    printf 'initial\n'
  elif read_metadata_migration_marker; then
    [ "$METADATA_MIGRATION_STATE" = pending ] ||
      die "cannot snapshot images while metadata isolation is ratcheting"
    printf 'legacy-adoption\n'
  else
    printf 'strict\n'
  fi
}

deployment_snapshot_image_required() {
  local mode="$1" service="$2"
  case "$mode:$service" in
    initial:*) return 1 ;;
    legacy-adoption:support-agent) return 1 ;;
    legacy-adoption:* | strict:*) return 0 ;;
    *) die "invalid deployment image snapshot requirement" ;;
  esac
}

render_deployment_compose_model() {
  local destination="$1"
  production_compose "$APP_ENV_FILE" config --format json > "$destination"
  [ -s "$destination" ] && [ "$(stat -c '%s' "$destination")" -le 4194304 ] ||
    die "rendered deployment Compose model is invalid"
}

compose_model_has_service() {
  local model="$1" service="$2"
  python3 - "$model" "$service" <<'PY'
import json
import sys

with open(sys.argv[1], "rb") as source:
    model = json.load(source)
services = model.get("services")
raise SystemExit(0 if isinstance(services, dict) and sys.argv[2] in services else 1)
PY
}

compose_model_service_image() {
  local model="$1" service="$2"
  python3 - "$model" "$service" <<'PY'
import json
import sys

with open(sys.argv[1], "rb") as source:
    model = json.load(source)
image = model.get("services", {}).get(sys.argv[2], {}).get("image")
if not isinstance(image, str) or not image or len(image) > 512 or any(c.isspace() for c in image):
    raise SystemExit(1)
print(image)
PY
}

verify_compose_container_identity() {
  local container_id="$1" service="$2" labels
  [[ "$container_id" =~ ^[0-9a-f]{12,64}$ || "$container_id" =~ ^gole-[a-z-]+$ ]] ||
    die "invalid Compose container identity"
  labels="$(docker inspect --format \
    '{{index .Config.Labels "com.docker.compose.project"}}|{{index .Config.Labels "com.docker.compose.service"}}' \
    "$container_id")" || die "could not inspect Compose container ownership"
  [ "$labels" = "gole|$service" ] || die "Compose container ownership is invalid: $service"
}

resolve_snapshot_service_image() {
  local container_id container_ids image_id image_ref model="$1" mode="$2" service="$3" state
  if ! compose_model_has_service "$model" "$service"; then
    [ "$mode:$service" = legacy-adoption:support-agent ] ||
      die "required deployment service is absent: $service"
    printf 'absent\n'
    return
  fi
  image_ref="$(compose_model_service_image "$model" "$service")" ||
    die "deployment service image is missing: $service"
  container_ids="$(production_compose "$APP_ENV_FILE" ps -a -q "$service")" ||
    die "could not resolve deployment service container: $service"
  if [ -n "$container_ids" ]; then
    [ "$(wc -l <<<"$container_ids")" -eq 1 ] ||
      die "deployment service has multiple containers: $service"
    container_id="$container_ids"
    verify_compose_container_identity "$container_id" "$service"
    if deployment_long_running_service "$service"; then
      state="$(docker inspect --format \
        '{{.State.Status}}:{{if .State.Health}}{{.State.Health.Status}}{{else}}missing{{end}}' \
        "$container_id")" || die "could not inspect deployment service health: $service"
      case "$service:$mode:$state" in
        nginx:legacy-adoption:running:missing | *:*:running:healthy) ;;
        *) die "deployment service is not a healthy LKG: $service" ;;
      esac
    else
      state="$(docker inspect --format '{{.State.Status}}:{{.State.ExitCode}}' "$container_id")" ||
        die "could not inspect initializer state: $service"
      [ "$state" = exited:0 ] || die "historical initializer did not complete successfully: $service"
    fi
    image_id="$(docker inspect --format '{{.Image}}' "$container_id")" ||
      die "could not inspect deployment service image: $service"
  else
    if deployment_long_running_service "$service"; then
      die "required running deployment service is missing: $service"
    fi
    # A mutable legacy initializer tag is not evidence of which image last
    # prepared the live data. The exited Compose container is the provenance;
    # if it was manually removed, require operator recovery instead of guessing.
    die "historical initializer container is missing: $service"
  fi
  [[ "$image_id" =~ ^sha256:[0-9a-f]{64}$ ]] ||
    die "deployment service image identity is invalid: $service"
  printf '%s\n' "$image_id"
}

snapshot_deployment_images() {
  local count=0 image_id image_ref marker marker_candidate mode model request_id="$2" rollback_image
  local service target="$1"
  local -A compose_ref_ids=()
  local -a image_manifest=()
  validate_deployment_target "$target"
  require_deployment_transaction "$request_id" prepared
  [ "$DEPLOY_TX_TARGET" = "$target" ] || die "deployment image target does not match transaction"
  marker="$(deployment_image_marker "$request_id")"
  install -d -m 0700 -o root -g root "$IMAGE_BACKUP_DIR"
  if [ -e "$marker" ] || [ -L "$marker" ]; then
    die "deployment image snapshot already exists"
  fi
  mode="$(deployment_image_snapshot_mode "$DEPLOY_TX_PREVIOUS_SHA")"
  if [ "$mode" = initial ]; then
    model=""
  else
    select_release "$DEPLOY_TX_PREVIOUS_SHA"
    validate_current_environment
    if [ "$mode" = strict ]; then
      validate_production_compose "$APP_ENV_FILE" strict-lkg
    else
      validate_production_compose "$APP_ENV_FILE" "$mode"
    fi
    model="$(mktemp)"
    register_temp_file "$model"
    chmod 0600 "$model"
    render_deployment_compose_model "$model"
  fi
  while IFS= read -r service; do
    if [ "$mode" = initial ]; then
      image_id=absent
    else
      image_id="$(resolve_snapshot_service_image "$model" "$mode" "$service")"
    fi
    if [ "$image_id" != absent ]; then
      image_ref="$(compose_model_service_image "$model" "$service")" ||
        die "deployment service image is missing: $service"
      if [ -n "${compose_ref_ids[$image_ref]+present}" ] &&
        [ "${compose_ref_ids[$image_ref]}" != "$image_id" ]; then
        die "one Compose image reference resolves to conflicting LKG images: $image_ref"
      fi
      compose_ref_ids["$image_ref"]="$image_id"
      rollback_image="$(deployment_rollback_image "$service" "$request_id")"
      docker image tag "$image_id" "$rollback_image"
      [ "$(docker image inspect --format '{{.Id}}' "$rollback_image" 2>/dev/null || true)" = "$image_id" ] ||
        die "rollback image tag could not be verified: $service"
      count=$((count + 1))
      image_manifest+=("image.$service=$image_id")
    else
      deployment_snapshot_image_required "$mode" "$service" &&
        die "required deployment image is missing: $service"
      image_manifest+=("image.$service=absent")
    fi
  done < <(deployment_image_services "$target" "$mode")
  marker_candidate="$(mktemp)"
  register_temp_file "$marker_candidate"
  printf 'target=%s\nrequest_id=%s\nmode=%s\nimage_count=%s\n' \
    "$target" "$request_id" "$mode" "$count" > "$marker_candidate"
  printf '%s\n' "${image_manifest[@]}" >> "$marker_candidate"
  atomic_install "$marker_candidate" "$marker" 0600 root
  sync_host_state "$marker"
  rm -f -- "$marker_candidate"
  forget_temp_file "$marker_candidate"
  if [ -n "$model" ]; then
    rm -f -- "$model"
    forget_temp_file "$model"
  fi
  advance_deployment_transaction "$request_id" prepared snapshotted
}

read_deployment_image_marker() {
  local expected_fields=0 key marker="$1" present_count=0 service value
  local seen_count=0 seen_images=0 seen_mode=0 seen_request=0 seen_target=0
  if [ ! -f "$marker" ] || [ -L "$marker" ] ||
    [ "$(stat -c '%U:%G:%a' "$marker")" != "root:root:600" ]; then
    die "deployment image snapshot marker is missing or invalid"
  fi
  SNAPSHOT_TARGET=""
  SNAPSHOT_REQUEST_ID=""
  SNAPSHOT_MODE=""
  SNAPSHOT_IMAGE_COUNT=""
  SNAPSHOT_IMAGE_IDS=()
  while IFS='=' read -r key value || [ -n "${key}${value}" ]; do
    case "$key" in
      target)
        [ "$seen_target" -eq 0 ] || die "duplicate image snapshot target"
        SNAPSHOT_TARGET="$value"
        seen_target=1
        ;;
      request_id)
        [ "$seen_request" -eq 0 ] || die "duplicate image snapshot request"
        SNAPSHOT_REQUEST_ID="$value"
        seen_request=1
        ;;
      mode)
        [ "$seen_mode" -eq 0 ] || die "duplicate image snapshot mode"
        SNAPSHOT_MODE="$value"
        seen_mode=1
        ;;
      image_count)
        [ "$seen_count" -eq 0 ] || die "duplicate image snapshot count"
        SNAPSHOT_IMAGE_COUNT="$value"
        seen_count=1
        ;;
      image.mongo|image.mongo-init|image.redis|image.minio|image.minio-init|image.support-agent|image.backend|image.frontend|image.nginx|image.budget-relay)
        service="${key#image.}"
        [ -z "${SNAPSHOT_IMAGE_IDS[$service]+present}" ] ||
          die "duplicate image snapshot identity"
        [[ "$value" = absent || "$value" =~ ^sha256:[0-9a-f]{64}$ ]] ||
          die "invalid image snapshot identity"
        SNAPSHOT_IMAGE_IDS["$service"]="$value"
        seen_images=$((seen_images + 1))
        ;;
      *) die "unknown deployment image snapshot field" ;;
    esac
  done < "$marker"
  [ "$seen_target$seen_request$seen_mode$seen_count" = "1111" ] ||
    die "incomplete image snapshot marker"
  validate_deployment_target "$SNAPSHOT_TARGET"
  validate_request_id "$SNAPSHOT_REQUEST_ID"
  [[ "$SNAPSHOT_MODE" =~ ^(initial|legacy-adoption|strict)$ ]] ||
    die "invalid image snapshot mode"
  [[ "$SNAPSHOT_IMAGE_COUNT" =~ ^([0-9]|10)$ ]] || die "invalid image snapshot count"
  while IFS= read -r service; do
    expected_fields=$((expected_fields + 1))
    [ -n "${SNAPSHOT_IMAGE_IDS[$service]+present}" ] ||
      die "image snapshot manifest is incomplete"
    value="${SNAPSHOT_IMAGE_IDS[$service]}"
    if [ "$value" != absent ]; then
      [ "$SNAPSHOT_MODE" != initial ] ||
        die "initial deployment snapshot unexpectedly contains an image"
      present_count=$((present_count + 1))
    elif deployment_snapshot_image_required "$SNAPSHOT_MODE" "$service"; then
      die "required image snapshot identity is absent: $service"
    fi
  done < <(deployment_image_services "$SNAPSHOT_TARGET" "$SNAPSHOT_MODE")
  [ "$seen_images" -eq "$expected_fields" ] || die "image snapshot manifest has extra identities"
  [ "$present_count" -eq "$SNAPSHOT_IMAGE_COUNT" ] ||
    die "image snapshot count does not match its manifest"
}

require_deployment_image_snapshot() {
  local marker request_id="$2" target="$1"
  if [ -z "${DEPLOY_TX_PREVIOUS_SHA+x}" ]; then
    require_deployment_transaction "$request_id" \
      snapshotted,built,nginx-installed,mutation-armed,mutated,refreshed,budget-updated,verified,marker-recorded,initial-http-verified,runtime-verified,metadata-ratchet-armed,metadata-ratchet-verified,initial-reset-armed,rollback-restored,cleanup-pending
  fi
  marker="$(deployment_image_marker "$request_id")"
  read_deployment_image_marker "$marker"
  if [ "$SNAPSHOT_TARGET" != "$target" ] || [ "$SNAPSHOT_REQUEST_ID" != "$request_id" ]; then
    die "deployment image snapshot does not match"
  fi
  if [ "$DEPLOY_TX_PREVIOUS_SHA" = 0 ]; then
    [ "$SNAPSHOT_MODE" = initial ] || die "initial deployment snapshot mode does not match"
  else
    [ "$SNAPSHOT_MODE" != initial ] || die "rollback snapshot mode does not match"
  fi
  SNAPSHOT_MARKER="$marker"
}

restore_deployment_images() {
  local expected_id request_id="$2" restored=0 rollback_id rollback_image service target="$1"
  require_deployment_image_snapshot "$target" "$request_id"
  [ "$SNAPSHOT_IMAGE_COUNT" -gt 0 ] || die "deployment image snapshot is empty"
  while IFS= read -r service; do
    expected_id="${SNAPSHOT_IMAGE_IDS[$service]}"
    [ "$expected_id" != absent ] || continue
    rollback_image="$(deployment_rollback_image "$service" "$request_id")"
    rollback_id="$(docker image inspect --format '{{.Id}}' "$rollback_image" 2>/dev/null || true)"
    [ "$rollback_id" = "$expected_id" ] ||
      die "rollback image identity changed or is missing: $service"
    restored=$((restored + 1))
  done < <(deployment_image_services "$target" "$SNAPSHOT_MODE")
  [ "$restored" -eq "$SNAPSHOT_IMAGE_COUNT" ] || die "deployment image snapshot is incomplete"
}

restore_canonical_compose_image_refs() {
  local expected_id image_ref model="$1" request_id="$3" rollback_image service target="$2"
  local -A restored_refs=()
  while IFS= read -r service; do
    expected_id="${SNAPSHOT_IMAGE_IDS[$service]}"
    [ "$expected_id" != absent ] || continue
    compose_model_has_service "$model" "$service" ||
      die "rollback Compose service disappeared: $service"
    image_ref="$(compose_model_service_image "$model" "$service")" ||
      die "rollback Compose image disappeared: $service"
    if [ -n "${restored_refs[$image_ref]+present}" ] &&
      [ "${restored_refs[$image_ref]}" != "$expected_id" ]; then
      die "rollback image reference has conflicting identities: $image_ref"
    fi
    restored_refs["$image_ref"]="$expected_id"
    if [[ "$image_ref" == *@sha256:* ]]; then
      [ "$(docker image inspect --format '{{.Id}}' "$image_ref" 2>/dev/null || true)" = "$expected_id" ] ||
        die "immutable rollback image reference changed: $service"
    else
      rollback_image="$(deployment_rollback_image "$service" "$request_id")"
      docker image tag "$rollback_image" "$image_ref"
      [ "$(docker image inspect --format '{{.Id}}' "$image_ref" 2>/dev/null || true)" = "$expected_id" ] ||
        die "canonical rollback image activation failed: $service"
    fi
  done < <(deployment_image_services "$target" "$SNAPSHOT_MODE")
  for image_ref in "${!restored_refs[@]}"; do
    [ "$(docker image inspect --format '{{.Id}}' "$image_ref" 2>/dev/null || true)" = \
      "${restored_refs[$image_ref]}" ] || die "canonical rollback image reference drifted"
  done
}

write_deployment_image_override() {
  local destination="$3" expected_id request_id="$2" rollback_id rollback_image
  local service target="$1"
  require_deployment_image_snapshot "$target" "$request_id"
  : > "$destination"
  chmod 0600 "$destination"
  printf 'services:\n' >> "$destination"
  while IFS= read -r service; do
    expected_id="${SNAPSHOT_IMAGE_IDS[$service]}"
    [ "$expected_id" != absent ] || continue
    rollback_image="$(deployment_rollback_image "$service" "$request_id")"
    rollback_id="$(docker image inspect --format '{{.Id}}' "$rollback_image" 2>/dev/null || true)"
    [ "$rollback_id" = "$expected_id" ] ||
      die "rollback image identity changed before Compose restore: $service"
    printf '  %s:\n    image: %s\n' "$service" "$rollback_image" >> "$destination"
  done < <(deployment_image_services "$target" "$SNAPSHOT_MODE")
  sync -f "$destination"
}

production_compose_with_override() {
  local environment_file="$1" override_file="$2"
  shift 2
  [ -f "$override_file" ] && [ ! -L "$override_file" ] &&
    [ "$(stat -c '%U:%G:%a' "$override_file")" = root:root:600 ] ||
    die "rollback Compose image override is invalid"
  local -a environment_arguments=(
    --env-file "$INFRA_ENV_FILE"
    --env-file "$environment_file"
  )
  if [ -e "$DISCORD_ENV_FILE" ] || [ -L "$DISCORD_ENV_FILE" ]; then
    validate_discord_environment
    environment_arguments+=(--env-file "$DISCORD_ENV_FILE")
  else
    die "Discord environment overlay is required"
  fi
  env -i \
    HOME=/root \
    PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin \
    GOLE_APP_ENV_FILE="$environment_file" \
    GOLE_INFRA_ENV_FILE="$INFRA_ENV_FILE" \
    docker compose \
    "${environment_arguments[@]}" \
    -f "$PRODUCTION_COMPOSE_FILE" -f "$override_file" "$@"
}

run_compose_services_exactly() {
  local compose_mode="$1" override_file="${2:-}" service
  shift 2
  local -a command=(production_compose "$APP_ENV_FILE")
  if [ "$compose_mode" = rollback ]; then
    command=(production_compose_with_override "$APP_ENV_FILE" "$override_file")
  elif [ "$compose_mode" != candidate ]; then
    die "invalid exact Compose rollout mode"
  fi
  for service in "$@"; do
    "${command[@]}" up -d --no-build --no-deps --force-recreate --wait "$service"
  done
}

run_compose_initializers_exactly() {
  local compose_mode="$1" container_id container_ids override_file="${2:-}" service state
  shift 2
  local -a command=(production_compose "$APP_ENV_FILE")
  if [ "$compose_mode" = rollback ]; then
    command=(production_compose_with_override "$APP_ENV_FILE" "$override_file")
  elif [ "$compose_mode" != candidate ]; then
    die "invalid exact Compose initializer mode"
  fi
  for service in "$@"; do
    # Keep the successful Compose service container as an immutable provenance
    # record. `compose run --rm` erased that evidence, so the next strict deploy
    # could not prove which initializer prepared the live volume.
    "${command[@]}" up --no-build --no-deps --force-recreate \
      --abort-on-container-exit --exit-code-from "$service" "$service" >/dev/null
    container_ids="$("${command[@]}" ps -a -q "$service")" ||
      die "could not resolve completed initializer: $service"
    [ -n "$container_ids" ] && [ "$(wc -l <<<"$container_ids")" -eq 1 ] ||
      die "initializer provenance container is missing: $service"
    container_id="$container_ids"
    verify_compose_container_identity "$container_id" "$service"
    state="$(docker inspect --format '{{.State.Status}}:{{.State.ExitCode}}' "$container_id")" ||
      die "could not inspect completed initializer: $service"
    [ "$state" = exited:0 ] || die "initializer did not complete successfully: $service"
  done
}

data_upgrade_services() {
  printf '%s\n' mongo mongo-init redis minio minio-init
}

data_upgrade_marker() {
  local request_id="$1"
  validate_request_id "$request_id"
  printf '%s/data-upgrade.%s\n' "$IMAGE_BACKUP_DIR" "${request_id//-/}"
}

validate_logical_backup_path() {
  local backup_path="$1" complete
  [[ "$backup_path" =~ ^/var/backups/gole-data/20[0-9]{6}T[0-9]{6}Z$ ]] ||
    die "deployment logical backup path is invalid"
  [ -d "$backup_path" ] && [ ! -L "$backup_path" ] &&
    [ "$(stat -c '%U:%G:%a' "$backup_path")" = root:root:700 ] ||
    die "deployment logical backup directory is invalid"
  complete="$backup_path/COMPLETE"
  [ -f "$complete" ] && [ ! -L "$complete" ] &&
    [ "$(stat -c '%U:%G:%a' "$complete")" = root:root:600 ] ||
    die "deployment logical backup completion marker is invalid"
  for artifact in SHA256SUMS mongo.archive.gz minio.tar.gz redis.tar.gz; do
    [ -f "$backup_path/$artifact" ] && [ ! -L "$backup_path/$artifact" ] &&
      [ "$(stat -c '%U:%G:%a' "$backup_path/$artifact")" = root:root:600 ] ||
      die "deployment logical backup artifact is invalid"
  done
  (
    cd "$backup_path"
    sha256sum --check --strict --status SHA256SUMS &&
      [ -s mongo.archive.gz ] &&
      [ -s minio.tar.gz ] &&
    [ -s redis.tar.gz ]
  ) || die "deployment logical backup checksum validation failed"
}

write_data_upgrade_marker() {
  local backup_path="$1" request_id="$2" marker candidate service
  marker="$(data_upgrade_marker "$request_id")"
  [ ! -e "$marker" ] && [ ! -L "$marker" ] || die "data upgrade marker already exists"
  candidate="$(mktemp)"
  register_temp_file "$candidate"
  printf 'state=backup-ready\nrequest_id=%s\nbackup_path=%s\n' \
    "$request_id" "$backup_path" > "$candidate"
  while IFS= read -r service; do
    printf 'change.%s=%s\ncandidate.%s=%s\n' \
      "$service" "${DATA_UPGRADE_CHANGES[$service]}" \
      "$service" "${DATA_UPGRADE_IMAGE_IDS[$service]}" >> "$candidate"
  done < <(data_upgrade_services)
  atomic_install "$candidate" "$marker" 0600 root
  sync_host_state "$marker"
  rm -f -- "$candidate"
  forget_temp_file "$candidate"
}

arm_data_upgrade_mutation() {
  local candidate marker request_id="$1" service
  require_data_upgrade_marker "$request_id"
  [ "$DATA_UPGRADE_STATE" = backup-ready ] ||
    die "data upgrade mutation is not ready to arm"
  marker="$(data_upgrade_marker "$request_id")"
  candidate="$(mktemp)"
  register_temp_file "$candidate"
  printf 'state=mutation-armed\nrequest_id=%s\nbackup_path=%s\n' \
    "$request_id" "$DATA_UPGRADE_BACKUP_PATH" > "$candidate"
  while IFS= read -r service; do
    printf 'change.%s=%s\ncandidate.%s=%s\n' \
      "$service" "${DATA_UPGRADE_CHANGES[$service]}" \
      "$service" "${DATA_UPGRADE_IMAGE_IDS[$service]}" >> "$candidate"
  done < <(data_upgrade_services)
  atomic_install "$candidate" "$marker" 0600 root
  sync_host_state "$marker"
  rm -f -- "$candidate"
  forget_temp_file "$candidate"
  require_data_upgrade_marker "$request_id"
  [ "$DATA_UPGRADE_STATE" = mutation-armed ] ||
    die "data upgrade mutation marker was not armed"
}

read_data_upgrade_marker() {
  local marker="$1" key value service seen_backup=0 seen_request=0 seen_state=0
  local seen_changes=0 seen_candidates=0
  [ -f "$marker" ] && [ ! -L "$marker" ] &&
    [ "$(stat -c '%U:%G:%a' "$marker")" = root:root:600 ] ||
    die "data upgrade marker is missing or invalid"
  DATA_UPGRADE_REQUEST_ID=""
  DATA_UPGRADE_BACKUP_PATH=""
  DATA_UPGRADE_STATE=""
  DATA_UPGRADE_CHANGES=()
  DATA_UPGRADE_IMAGE_IDS=()
  while IFS='=' read -r key value || [ -n "${key}${value}" ]; do
    case "$key" in
      state)
        [ "$seen_state" -eq 0 ] || die "duplicate data upgrade state"
        [[ "$value" =~ ^(backup-ready|mutation-armed)$ ]] ||
          die "invalid data upgrade state"
        DATA_UPGRADE_STATE="$value"
        seen_state=1
        ;;
      request_id)
        [ "$seen_request" -eq 0 ] || die "duplicate data upgrade request"
        DATA_UPGRADE_REQUEST_ID="$value"
        seen_request=1
        ;;
      backup_path)
        [ "$seen_backup" -eq 0 ] || die "duplicate data upgrade backup"
        DATA_UPGRADE_BACKUP_PATH="$value"
        seen_backup=1
        ;;
      change.mongo|change.mongo-init|change.redis|change.minio|change.minio-init)
        service="${key#change.}"
        [ -z "${DATA_UPGRADE_CHANGES[$service]+present}" ] ||
          die "duplicate data upgrade change flag"
        [[ "$value" =~ ^(true|false)$ ]] || die "invalid data upgrade change flag"
        DATA_UPGRADE_CHANGES["$service"]="$value"
        seen_changes=$((seen_changes + 1))
        ;;
      candidate.mongo|candidate.mongo-init|candidate.redis|candidate.minio|candidate.minio-init)
        service="${key#candidate.}"
        [ -z "${DATA_UPGRADE_IMAGE_IDS[$service]+present}" ] ||
          die "duplicate data upgrade candidate image"
        [[ "$value" =~ ^sha256:[0-9a-f]{64}$ ]] ||
          die "invalid data upgrade candidate image"
        DATA_UPGRADE_IMAGE_IDS["$service"]="$value"
        seen_candidates=$((seen_candidates + 1))
        ;;
      *) die "unknown data upgrade marker field" ;;
    esac
  done < "$marker"
  [ "$seen_state$seen_request$seen_backup" = 111 ] && [ "$seen_changes" -eq 5 ] &&
    [ "$seen_candidates" -eq 5 ] || die "data upgrade marker is incomplete"
  validate_request_id "$DATA_UPGRADE_REQUEST_ID"
  validate_logical_backup_path "$DATA_UPGRADE_BACKUP_PATH"
}

require_data_upgrade_marker() {
  local marker request_id="$1"
  marker="$(data_upgrade_marker "$request_id")"
  read_data_upgrade_marker "$marker"
  [ "$DATA_UPGRADE_REQUEST_ID" = "$request_id" ] || die "data upgrade request does not match"
}

cleanup_data_upgrade_marker() {
  local marker request_id="$1"
  marker="$(data_upgrade_marker "$request_id")"
  if [ -e "$marker" ] || [ -L "$marker" ]; then
    require_data_upgrade_marker "$request_id"
    remove_host_state "$marker"
  fi
}

prepare_strict_data_upgrade() {
  local changed=0 image_id marker new_model new_ref old_model old_ref
  local request_id="$1" service
  local -a changed_services=()
  require_deployment_transaction "$request_id" nginx-installed
  require_deployment_image_snapshot all "$request_id"
  [ "$SNAPSHOT_MODE" = strict ] || die "strict data upgrade requested outside strict mode"
  marker="$(data_upgrade_marker "$request_id")"
  [ ! -e "$marker" ] && [ ! -L "$marker" ] || die "data upgrade marker already exists"
  old_model="$(mktemp)"
  new_model="$(mktemp)"
  register_temp_file "$old_model"
  register_temp_file "$new_model"
  chmod 0600 "$old_model" "$new_model"
  select_release "$DEPLOY_TX_PREVIOUS_SHA"
  render_deployment_compose_model "$old_model"
  select_release "$DEPLOY_TX_NEW_SHA"
  render_deployment_compose_model "$new_model"
  DATA_UPGRADE_CHANGES=()
  DATA_UPGRADE_IMAGE_IDS=()
  STRICT_DATA_UPGRADE_CHANGED=0
  while IFS= read -r service; do
    old_ref="$(compose_model_service_image "$old_model" "$service")" ||
      die "previous data image is missing: $service"
    new_ref="$(compose_model_service_image "$new_model" "$service")" ||
      die "candidate data image is missing: $service"
    [[ "$new_ref" =~ ^[^[:space:]@]+@sha256:[0-9a-f]{64}$ ]] ||
      die "candidate data image is not digest pinned: $service"
    if [ "$old_ref" != "$new_ref" ]; then
      DATA_UPGRADE_CHANGES["$service"]=true
      changed_services+=("$service")
      changed=1
    else
      DATA_UPGRADE_CHANGES["$service"]=false
    fi
  done < <(data_upgrade_services)
  if [ "$changed" -eq 0 ]; then
    rm -f -- "$old_model" "$new_model"
    forget_temp_file "$old_model"
    forget_temp_file "$new_model"
    return 0
  fi
  # Resolve every candidate before closing the write path. A slow or failed
  # registry pull must not lengthen the data recovery-point window.
  production_compose "$APP_ENV_FILE" pull --quiet "${changed_services[@]}"
  while IFS= read -r service; do
    new_ref="$(compose_model_service_image "$new_model" "$service")" ||
      die "candidate data image is missing after pull: $service"
    image_id="$(docker image inspect --format '{{.Id}}' "$new_ref" 2>/dev/null || true)"
    [[ "$image_id" =~ ^sha256:[0-9a-f]{64}$ ]] ||
      die "candidate data image identity is unavailable: $service"
    DATA_UPGRADE_IMAGE_IDS["$service"]="$image_id"
  done < <(data_upgrade_services)
  STRICT_DATA_UPGRADE_CHANGED=1
  rm -f -- "$old_model" "$new_model"
  forget_temp_file "$old_model"
  forget_temp_file "$new_model"
}

capture_strict_data_upgrade_backup() {
  local backup_output backup_path backup_status=0 request_id="$1"
  [ "$STRICT_DATA_UPGRADE_CHANGED" -eq 1 ] ||
    die "strict data upgrade backup was requested without a changed image"
  require_deployment_transaction "$request_id" mutation-armed
  [ -x "$LOGICAL_BACKUP_HELPER" ] && [ ! -L "$LOGICAL_BACKUP_HELPER" ] ||
    die "logical backup helper is missing or invalid"

  # The transaction is durable before this stop. Therefore a crash anywhere
  # from quiesce through backup can only recover by restarting the exact LKG.
  quiesce_public_runtime
  if backup_output="$("$LOGICAL_BACKUP_HELPER")"; then
    :
  else
    backup_status=$?
    if [ "$backup_status" -eq 78 ] ||
      [ -e "$MINIO_RECOVERY_MARKER" ] || [ -L "$MINIO_RECOVERY_MARKER" ]; then
      systemctl poweroff --no-block || true
      die "MinIO unfreeze is uncertain; deployment retained and VM powered off"
    fi
    die "pre-upgrade logical backup failed"
  fi
  [ "$(wc -l <<<"$backup_output")" -eq 1 ] || die "logical backup returned an invalid path"
  backup_path="$backup_output"
  validate_logical_backup_path "$backup_path"
  write_data_upgrade_marker "$backup_path" "$request_id"
}

data_upgrade_required() {
  local marker
  marker="$(data_upgrade_marker "$1")"
  if [ -e "$marker" ] || [ -L "$marker" ]; then
    require_data_upgrade_marker "$1"
    return 0
  fi
  return 1
}

run_strict_data_upgrade() {
  local request_id="$1"
  require_data_upgrade_marker "$request_id"
  [ "$DATA_UPGRADE_STATE" = backup-ready ] ||
    die "strict data upgrade backup is not ready"
  require_public_runtime_quiesced
  # From this durable boundary onward, merely restoring old images is unsafe:
  # an initializer or storage engine can have changed on-disk semantics. Any
  # failure retains the exact logical backup and requires explicit root restore.
  arm_data_upgrade_mutation "$request_id"
  [ "${DATA_UPGRADE_CHANGES[mongo]}" = true ] &&
    run_compose_services_exactly candidate "" mongo
  [ "${DATA_UPGRADE_CHANGES[redis]}" = true ] &&
    run_compose_services_exactly candidate "" redis
  [ "${DATA_UPGRADE_CHANGES[minio]}" = true ] &&
    run_compose_services_exactly candidate "" minio
  if [ "${DATA_UPGRADE_CHANGES[mongo]}" = true ] ||
    [ "${DATA_UPGRADE_CHANGES[mongo-init]}" = true ]; then
    run_compose_initializers_exactly candidate "" mongo-init
  fi
  if [ "${DATA_UPGRADE_CHANGES[minio]}" = true ] ||
    [ "${DATA_UPGRADE_CHANGES[minio-init]}" = true ]; then
    run_compose_initializers_exactly candidate "" minio-init
  fi
  verify_data_upgrade_candidate_runtime "$request_id"
  # Do not reopen background or public writes until every changed data image
  # and initializer has passed the immutable candidate checks above.
  require_public_runtime_quiesced
}

verify_data_upgrade_candidate_runtime() {
  local actual_id container container_ids expected_id request_id="$1" service state
  require_data_upgrade_marker "$request_id"
  while IFS= read -r service; do
    expected_id="${DATA_UPGRADE_IMAGE_IDS[$service]}"
    if deployment_long_running_service "$service"; then
      container="$(deployment_container_name "$service")"
      verify_compose_container_identity "$container" "$service"
      actual_id="$(docker inspect --format '{{.Image}}' "$container")" ||
        die "could not inspect upgraded data service: $service"
      [ "$actual_id" = "$expected_id" ] || die "upgraded data image does not match: $service"
      state="$(docker inspect --format \
        '{{.State.Status}}:{{if .State.Health}}{{.State.Health.Status}}{{else}}missing{{end}}' \
        "$container")" || die "could not inspect upgraded data health: $service"
      [ "$state" = running:healthy ] || die "upgraded data service is not healthy: $service"
    else
      container_ids="$(production_compose "$APP_ENV_FILE" ps -a -q "$service")" ||
        die "could not resolve upgraded initializer: $service"
      [ -n "$container_ids" ] && [ "$(wc -l <<<"$container_ids")" -eq 1 ] ||
        die "upgraded initializer provenance is missing: $service"
      container="$container_ids"
      verify_compose_container_identity "$container" "$service"
      actual_id="$(docker inspect --format '{{.Image}}' "$container")" ||
        die "could not inspect upgraded initializer: $service"
      [ "$actual_id" = "$expected_id" ] || die "upgraded initializer image does not match: $service"
      state="$(docker inspect --format '{{.State.Status}}:{{.State.ExitCode}}' "$container")" ||
        die "could not inspect upgraded initializer state: $service"
      [ "$state" = exited:0 ] || die "upgraded initializer did not succeed: $service"
    fi
  done < <(data_upgrade_services)
}

quiesce_public_runtime() {
  local container labels service state
  # Nginx closes the public write path first. Backend is stopped before any
  # data-plane replacement so no request or background worker can span the
  # legacy and strict Mongo/Redis/MinIO instances.
  for service in nginx frontend backend support-agent; do
    container="$(deployment_container_name "$service")"
    if ! docker inspect "$container" >/dev/null 2>&1; then
      [ "$service" = support-agent ] || continue
      continue
    fi
    labels="$(docker inspect --format \
      '{{index .Config.Labels "com.docker.compose.project"}}|{{index .Config.Labels "com.docker.compose.service"}}' \
      "$container")" || die "could not verify service before quiescing: $service"
    [ "$labels" = "gole|$service" ] || die "unexpected container blocks quiescing: $service"
    docker stop --time 30 "$container" >/dev/null
    state="$(docker inspect --format '{{.State.Running}}' "$container")" ||
      die "could not verify quiesced service: $service"
    [ "$state" = false ] || die "service remained active during data-plane migration: $service"
  done
}

require_public_runtime_quiesced() {
  local container labels service state
  for service in nginx frontend backend support-agent; do
    container="$(deployment_container_name "$service")"
    if ! docker inspect "$container" >/dev/null 2>&1; then
      continue
    fi
    labels="$(docker inspect --format \
      '{{index .Config.Labels "com.docker.compose.project"}}|{{index .Config.Labels "com.docker.compose.service"}}' \
      "$container")" || die "could not verify quiesced service ownership: $service"
    [ "$labels" = "gole|$service" ] || die "unexpected container blocks quiesce proof: $service"
    state="$(docker inspect --format '{{.State.Running}}' "$container")" ||
      die "could not inspect quiesced service: $service"
    [ "$state" = false ] || die "write-capable service resumed during data-plane migration: $service"
  done
}

verify_restored_deployment_images() {
  local container container_ids expected_id image_id labels request_id="$2" rollback_id rollback_image
  local service state target="$1"
  require_deployment_image_snapshot "$target" "$request_id"
  while IFS= read -r service; do
    expected_id="${SNAPSHOT_IMAGE_IDS[$service]}"
    if [ "$expected_id" = absent ]; then
      if container="$(deployment_container_name "$service" 2>/dev/null)" &&
        docker inspect "$container" >/dev/null 2>&1; then
        die "rollback retained a service that was absent from the LKG: $service"
      fi
      continue
    fi
    rollback_image="$(deployment_rollback_image "$service" "$request_id")"
    rollback_id="$(docker image inspect --format '{{.Id}}' "$rollback_image" 2>/dev/null || true)"
    [ "$rollback_id" = "$expected_id" ] || die "rollback image tag drifted: $service"
    if deployment_long_running_service "$service"; then
      container="$(deployment_container_name "$service")"
      verify_compose_container_identity "$container" "$service"
      image_id="$(docker inspect --format '{{.Image}}' "$container")" ||
        die "could not inspect restored service: $service"
      [ "$image_id" = "$expected_id" ] || die "restored service image does not match: $service"
      state="$(docker inspect --format \
        '{{.State.Status}}:{{if .State.Health}}{{.State.Health.Status}}{{else}}missing{{end}}' \
        "$container")" || die "could not inspect restored service health: $service"
      case "$service:$SNAPSHOT_MODE:$state" in
        nginx:legacy-adoption:running:missing | *:*:running:healthy) ;;
        *) die "restored service is not healthy: $service" ;;
      esac
    else
      container_ids="$(production_compose "$APP_ENV_FILE" ps -a -q "$service")" ||
        die "could not resolve restored initializer: $service"
      [ -n "$container_ids" ] && [ "$(wc -l <<<"$container_ids")" -eq 1 ] ||
        die "restored initializer provenance is missing: $service"
      container="$container_ids"
      verify_compose_container_identity "$container" "$service"
      image_id="$(docker inspect --format '{{.Image}}' "$container")" ||
        die "could not inspect restored initializer: $service"
      [ "$image_id" = "$expected_id" ] ||
        die "restored initializer image does not match: $service"
      state="$(docker inspect --format '{{.State.Status}}:{{.State.ExitCode}}' "$container")" ||
        die "could not inspect restored initializer state: $service"
      [ "$state" = exited:0 ] || die "restored initializer did not succeed: $service"
    fi
  done < <(deployment_image_services "$target" "$SNAPSHOT_MODE")
}

cleanup_deployment_images() {
  local image_mode marker marker_present=0 request_id="$2" rollback_image service target="$1"
  validate_deployment_target "$target"
  validate_request_id "$request_id"
  marker="$(deployment_image_marker "$request_id")"
  if [ -e "$marker" ] || [ -L "$marker" ]; then
    require_deployment_image_snapshot "$target" "$request_id"
    marker_present=1
    image_mode="$SNAPSHOT_MODE"
  else
    [ "${DEPLOY_TX_TARGET:-}" = "$target" ] &&
      [ "${DEPLOY_TX_REQUEST_ID:-}" = "$request_id" ] ||
      die "deployment image cleanup does not match transaction"
    case "${DEPLOY_TX_STATE:-}" in
      cleanup-pending | rollback-restored) ;;
      *) die "deployment image snapshot marker is missing before terminal cleanup" ;;
    esac
    if [ "$target" = all ]; then
      image_mode=legacy-adoption
    else
      image_mode=strict
    fi
  fi
  while IFS= read -r service; do
    rollback_image="$(deployment_rollback_image "$service" "$request_id")"
    docker image rm "$rollback_image" >/dev/null 2>&1 || true
  done < <(deployment_image_services "$target" "$image_mode")
  cleanup_data_upgrade_marker "$request_id"
  if [ "$marker_present" -eq 1 ]; then
    remove_host_state "$SNAPSHOT_MARKER"
  else
    sync -f "$IMAGE_BACKUP_DIR"
  fi
}

cleanup_deployment_images_command() {
  local request_id="$2" target="$1"
  require_deployment_transaction "$request_id" cleanup-pending,rollback-restored
  [ "$DEPLOY_TX_TARGET" = "$target" ] || die "deployment image cleanup target does not match"
  cleanup_deployment_images "$target" "$request_id"
}

cleanup_uncommitted_deployment_image_tags() {
  local mode request_id="$2" rollback_image service target="$1"
  validate_deployment_target "$target"
  validate_request_id "$request_id"
  mode="$(deployment_image_snapshot_mode "$DEPLOY_TX_PREVIOUS_SHA")"
  while IFS= read -r service; do
    rollback_image="$(deployment_rollback_image "$service" "$request_id")"
    docker image rm "$rollback_image" >/dev/null 2>&1 || true
  done < <(deployment_image_services "$target" "$mode")
}

verify_pre_snapshot_lkg_runtime() {
  local available container expected_sha="$1" mode service state
  if [ "$expected_sha" = 0 ]; then
    validate_initial_deployment
    return
  fi
  [ "$(read_deployed_sha)" = "$expected_sha" ] ||
    die "pre-snapshot recovery LKG marker changed"
  select_release "$expected_sha"
  validate_current_environment
  mode="$(deployment_image_snapshot_mode "$expected_sha")"
  if [ "$mode" = strict ]; then
    validate_production_compose "$APP_ENV_FILE" strict-lkg
  else
    validate_production_compose "$APP_ENV_FILE" "$mode"
  fi
  available="$(production_compose "$APP_ENV_FILE" config --services)"
  for service in backend frontend budget-relay nginx; do
    grep -qx "$service" <<<"$available" ||
      die "pre-snapshot recovery release misses a required service"
  done
  if [ "$mode" = strict ]; then
    grep -qx support-agent <<<"$available" ||
      die "strict pre-snapshot recovery release misses support-agent"
    verify_metadata_full_policy
    verify_broker_native_budget_relay
  else
    verify_metadata_pending_policy
  fi
  for container in gole-backend gole-frontend gole-budget-relay gole-nginx; do
    state="$(docker inspect --format \
      '{{.State.Status}}:{{if .State.Health}}{{.State.Health.Status}}{{else}}missing{{end}}' \
      "$container" 2>/dev/null || true)"
    [ "$state" = running:healthy ] || die "pre-snapshot LKG container is not healthy"
  done
  if [ "$mode" = strict ]; then
    state="$(docker inspect --format \
      '{{.State.Status}}:{{if .State.Health}}{{.State.Health.Status}}{{else}}missing{{end}}' \
      gole-support-agent 2>/dev/null || true)"
    [ "$state" = running:healthy ] || die "pre-snapshot support agent is not healthy"
  fi
  verify_public_transport_runtime
  systemctl is-active --quiet gole-cost-guard-watchdog.timer ||
    die "cost guard watchdog timer is not active"
}

recover_pre_snapshot_deployment() {
  local marker
  [ "$DEPLOY_TX_STATE" = prepared ] || die "pre-snapshot recovery state changed"
  verify_pre_snapshot_lkg_runtime "$DEPLOY_TX_PREVIOUS_SHA"
  marker="$(deployment_image_marker "$DEPLOY_TX_REQUEST_ID")"
  if [ -e "$marker" ] || [ -L "$marker" ]; then
    require_deployment_image_snapshot "$DEPLOY_TX_TARGET" "$DEPLOY_TX_REQUEST_ID"
    cleanup_deployment_images "$DEPLOY_TX_TARGET" "$DEPLOY_TX_REQUEST_ID"
  else
    cleanup_uncommitted_deployment_image_tags "$DEPLOY_TX_TARGET" "$DEPLOY_TX_REQUEST_ID"
  fi
  remove_host_state "$DEPLOYMENT_TRANSACTION_FILE"
}

validate_current_production_model() {
  validate_discord_environment
  validate_current_environment
  validate_production_environment "$APP_ENV_FILE"
  validate_production_compose "$APP_ENV_FILE"
}

build_deployment_images() {
  local requested_sha="$2" request_id="$3" target="$1"
  local services=()
  validate_deployment_target "$target"
  require_deployment_transaction "$request_id" snapshotted
  [ "$DEPLOY_TX_TARGET" = "$target" ] && [ "$DEPLOY_TX_NEW_SHA" = "$requested_sha" ] ||
    die "deployment build does not match transaction"
  select_release "$requested_sha"
  validate_current_production_model
  case "$target" in
    all) services=(support-agent backend frontend budget-relay) ;;
    backend) services=(support-agent backend) ;;
    frontend) services=(frontend) ;;
  esac
  production_compose "$APP_ENV_FILE" build "${services[@]}"
  advance_deployment_transaction "$request_id" snapshotted built
}

run_deployment_compose_phase() {
  local phase="$1" requested_sha="$2" request_id="$3"
  require_deployment_transaction "$request_id" \
    built,nginx-installed,mutation-armed,mutated,refreshed,budget-updated
  [ "$DEPLOY_TX_NEW_SHA" = "$requested_sha" ] || die "deployment phase SHA does not match"
  select_release "$requested_sha"
  validate_current_production_model
  case "$phase" in
    rollout-all-apps)
      [ "$DEPLOY_TX_TARGET" = all ] && [ "$DEPLOY_TX_STATE" = nginx-installed ] ||
        die "all-services rollout is out of order"
      require_matching_nginx_transaction "$request_id"
      [ "$NGINX_TXN_STATE" = installed ] && [ "$NGINX_TXN_DEPLOY_SHA" = "$requested_sha" ] ||
        die "all-services rollout Nginx transaction is not installed"
      # Do not let Compose recursively choose dependencies. The first legacy
      # cutover changes both network topology and pinned data-plane images, so
      # every affected service is replaced in a fixed order after its exact
      # previous image was journaled.
      require_deployment_image_snapshot all "$request_id"
      if [ "$SNAPSHOT_MODE" = strict ]; then
        # Compare the previous and candidate digest-pinned data images before
        # crossing the durable mutation boundary. Candidate pulls and identity
        # resolution finish while the LKG still accepts writes. An ordinary
        # deploy performs no data pull, backup, stop or recreate when every
        # reference is equal.
        prepare_strict_data_upgrade "$request_id"
      fi
      advance_deployment_transaction "$request_id" nginx-installed mutation-armed
      if [ "$SNAPSHOT_MODE" = strict ] && [ "$STRICT_DATA_UPGRADE_CHANGED" -eq 1 ]; then
        # The RPO begins only after all write-capable services are stopped, and
        # that stop is recoverable because mutation-armed is already durable.
        capture_strict_data_upgrade_backup "$request_id"
      fi
      if [ "$SNAPSHOT_MODE" = legacy-adoption ]; then
        quiesce_public_runtime
        run_compose_services_exactly candidate "" mongo redis minio
        run_compose_initializers_exactly candidate "" mongo-init minio-init
      elif [ "$SNAPSHOT_MODE" = initial ]; then
        run_compose_services_exactly candidate "" mongo redis minio
        run_compose_initializers_exactly candidate "" mongo-init minio-init
      elif data_upgrade_required "$request_id"; then
        run_strict_data_upgrade "$request_id"
      fi
      run_compose_services_exactly candidate "" support-agent backend frontend
      advance_deployment_transaction "$request_id" mutation-armed mutated
      ;;
    rollout-backend)
      [ "$DEPLOY_TX_TARGET" = backend ] && [ "$DEPLOY_TX_STATE" = built ] ||
        die "backend rollout is out of order"
      advance_deployment_transaction "$request_id" built mutation-armed
      run_compose_services_exactly candidate "" support-agent backend
      advance_deployment_transaction "$request_id" mutation-armed mutated
      ;;
    rollout-frontend)
      [ "$DEPLOY_TX_TARGET" = frontend ] && [ "$DEPLOY_TX_STATE" = built ] ||
        die "frontend rollout is out of order"
      advance_deployment_transaction "$request_id" built mutation-armed
      run_compose_services_exactly candidate "" frontend
      advance_deployment_transaction "$request_id" mutation-armed mutated
      ;;
    rollout-budget)
      [ "$DEPLOY_TX_TARGET" = all ] && [ "$DEPLOY_TX_STATE" = refreshed ] ||
        die "budget rollout is out of order"
      production_compose "$APP_ENV_FILE" up -d --no-build --no-deps --force-recreate --wait budget-relay
      advance_deployment_transaction "$request_id" refreshed budget-updated
      ;;
    refresh-nginx)
      [ "$DEPLOY_TX_STATE" = mutated ] || die "Nginx refresh is out of order"
      production_compose "$APP_ENV_FILE" up -d --no-deps --force-recreate --wait nginx
      production_compose "$APP_ENV_FILE" exec -T nginx nginx -t >/dev/null
      advance_deployment_transaction "$request_id" mutated refreshed
      ;;
    *) die "invalid deployment Compose phase" ;;
  esac
}

show_deployment_status() {
  local requested_sha="$1" request_id="$2"
  require_deployment_transaction "$request_id" prepared,snapshotted,built,nginx-installed,mutation-armed,mutated,refreshed,budget-updated,verified,marker-recorded,initial-http-verified,runtime-verified
  [ "$DEPLOY_TX_NEW_SHA" = "$requested_sha" ] || die "status SHA does not match"
  select_release "$requested_sha"
  validate_current_production_model
  production_compose "$APP_ENV_FILE" ps
}

verify_budget_relay_health() {
  [ "$(docker inspect --format '{{.State.Status}}:{{if .State.Health}}{{.State.Health.Status}}{{else}}missing{{end}}' \
    gole-budget-relay 2>/dev/null || true)" = "running:healthy" ] ||
    die "budget relay is not healthy"
}

read_host_bootstrap_sha() {
  local line
  if [ ! -f "$HOST_BOOTSTRAP_MARKER" ] || [ -L "$HOST_BOOTSTRAP_MARKER" ] ||
    [ "$(stat -c '%U:%G:%a' "$HOST_BOOTSTRAP_MARKER")" != "root:root:644" ]; then
    die "host bootstrap completion marker is missing or invalid"
  fi
  [ "$(wc -l < "$HOST_BOOTSTRAP_MARKER")" -eq 1 ] &&
    [ "$(tail -c 1 "$HOST_BOOTSTRAP_MARKER" | wc -l)" -eq 1 ] ||
    die "host bootstrap completion marker is malformed"
  line="$(cat "$HOST_BOOTSTRAP_MARKER")"
  [[ "$line" =~ ^bootstrap_source_sha=([0-9a-f]{40})$ ]] ||
    die "host bootstrap completion SHA is invalid"
  printf '%s\n' "${BASH_REMATCH[1]}"
}

read_cloud_broker_project_id() {
  local project_id
  if [ ! -f "$BROKER_CONFIG_FILE" ] || [ -L "$BROKER_CONFIG_FILE" ] ||
    [ "$(stat -c '%U:%G:%a' "$BROKER_CONFIG_FILE")" != "root:root:600" ]; then
    die "cloud broker configuration is missing or invalid"
  fi
  [ "$(grep -Ec '^PROJECT_ID=' "$BROKER_CONFIG_FILE")" -eq 1 ] ||
    die "cloud broker project configuration is invalid"
  project_id="$(awk -F= '$1 == "PROJECT_ID" { print substr($0, index($0, "=") + 1) }' \
    "$BROKER_CONFIG_FILE")"
  [[ "$project_id" =~ ^[a-z][a-z0-9-]{4,28}[a-z0-9]$ ]] ||
    die "cloud broker project configuration is invalid"
  printf '%s\n' "$project_id"
}

issue_certificate() {
  local caller="${SUDO_USER:-root}" project_id release release_sha transaction
  if [ ! -f /run/lock/gole-production-rollout.lock ] ||
    [ -L /run/lock/gole-production-rollout.lock ] ||
    [ "$(stat -c '%U:%G:%a' /run/lock/gole-production-rollout.lock)" != "root:${DEPLOY_GROUP}:660" ]; then
    die "production rollout lock is missing or invalid"
  fi
  exec 8>>/run/lock/gole-production-rollout.lock
  flock -n 8 || die "another production rollout is active"

  if [ -e "$DEPLOYMENT_TRANSACTION_FILE" ] || [ -L "$DEPLOYMENT_TRANSACTION_FILE" ]; then
    read_deployment_transaction
    [ "$DEPLOY_TX_STATE" = initial-http-verified ] &&
      [ "$DEPLOY_TX_TARGET" = all ] && [ "$DEPLOY_TX_PREVIOUS_SHA" = 0 ] &&
      [ "$(read_deployed_sha)" = "$DEPLOY_TX_NEW_SHA" ] ||
      die "an active production transaction blocks certificate issuance"
    release_sha="$DEPLOY_TX_NEW_SHA"
  else
    [ "$caller" = root ] ||
      die "certificate issuance by the deploy user requires an initial TLS transaction"
    if [ -e "$DEPLOYED_SHA_FILE" ] || [ -L "$DEPLOYED_SHA_FILE" ]; then
      release_sha="$(read_deployed_sha)"
    else
      release_sha="$(read_host_bootstrap_sha)"
    fi
  fi
  for transaction in \
    "$ENV_TRANSACTION_FILE" "$ADOPTION_TRANSACTION_FILE" "$NGINX_TRANSACTION_FILE" \
    "$METADATA_MIGRATION_MARKER"; do
    [ ! -e "$transaction" ] && [ ! -L "$transaction" ] ||
      die "an active production transaction blocks certificate issuance"
  done
  [ ! -e "$INITIAL_DEPLOY_FILE" ] && [ ! -L "$INITIAL_DEPLOY_FILE" ] ||
    die "initial deployment is not complete"
  project_id="$(read_cloud_broker_project_id)"
  create_release "$release_sha" historical-main
  select_release "$release_sha"
  validate_current_production_model
  release="$(release_path "$release_sha")"
  if [ ! -x "$CERTIFICATE_ISSUER" ] || [ -L "$CERTIFICATE_ISSUER" ] ||
    [ "$(stat -c '%U:%G:%a' "$CERTIFICATE_ISSUER")" != "root:root:755" ]; then
    die "certificate issuer is missing or invalid"
  fi
  env -i \
    HOME=/root \
    PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin \
    GOLE_ROLLOUT_LOCK_HELD=1 \
    GOLE_TRUSTED_RELEASE_ROOT="$release" \
    DOMAIN=gole.co.kr \
    EMAIL=coldingcontact@gmail.com \
    GCP_PROJECT_ID="$project_id" \
    "$CERTIFICATE_ISSUER"
}

renew_certificate() {
  local deployed_sha
  if [ "${SUDO_USER:-root}" != "root" ]; then
    die "certificate renewal is reserved for the root-owned systemd timer"
  fi
  if [ ! -f /run/lock/gole-production-rollout.lock ] ||
    [ -L /run/lock/gole-production-rollout.lock ] ||
    [ "$(stat -c '%U:%G:%a' /run/lock/gole-production-rollout.lock)" != "root:${DEPLOY_GROUP}:660" ]; then
    die "production rollout lock is missing or invalid"
  fi
  exec 8>>/run/lock/gole-production-rollout.lock
  flock -n 8 || die "another production rollout is active"
  deployed_sha="$(read_deployed_sha)"
  select_release "$deployed_sha"
  validate_current_production_model
  production_compose "$APP_ENV_FILE" --profile certificate run --rm --no-deps -T certbot renew --quiet
  production_compose "$APP_ENV_FILE" exec -T nginx nginx -t >/dev/null
  production_compose "$APP_ENV_FILE" exec -T nginx nginx -s reload >/dev/null
}

verify_public_transport_runtime() {
  local apex_headers canonical_path http_redirect https_redirect state
  state="$(docker inspect --format \
    '{{.State.Status}}:{{if .State.Health}}{{.State.Health.Status}}{{else}}missing{{end}}' \
    gole-nginx 2>/dev/null || true)"
  [ "$state" = "running:healthy" ] || [ "$state" = "running:missing" ] ||
    die "Nginx is not running"
  docker exec gole-nginx nginx -t >/dev/null 2>&1 || die "Nginx runtime validation failed"
  curl -fsS --max-time 15 http://127.0.0.1:8080/actuator/health/readiness >/dev/null ||
    die "backend readiness check failed"
  curl -fsS --max-time 15 http://127.0.0.1:3000/icon.svg >/dev/null ||
    die "frontend readiness check failed"
  canonical_path="/__gole-canonical-check?source=runtime"
  http_redirect="$(curl -sS --max-time 15 --resolve www.gole.co.kr:80:127.0.0.1 \
    --output /dev/null --write-out '%{http_code}|%{redirect_url}' \
    "http://www.gole.co.kr${canonical_path}")" || die "HTTP canonical redirect check failed"
  [ "$http_redirect" = "301|https://gole.co.kr${canonical_path}" ] ||
    die "HTTP www does not redirect directly to the canonical apex"
  https_redirect="$(curl -sS --max-time 15 --resolve www.gole.co.kr:443:127.0.0.1 \
    --output /dev/null --write-out '%{http_code}|%{redirect_url}' \
    "https://www.gole.co.kr${canonical_path}")" || die "HTTPS canonical redirect check failed"
  [ "$https_redirect" = "301|https://gole.co.kr${canonical_path}" ] ||
    die "HTTPS www does not redirect directly to the canonical apex"
  apex_headers="$(curl -fsSI --max-time 15 --resolve gole.co.kr:443:127.0.0.1 \
    https://gole.co.kr/)" || die "HTTPS apex response check failed"
  grep -Eiq '^strict-transport-security:[[:space:]]*max-age=31536000([;[:space:]]|$)' \
    <<<"$apex_headers" || die "HTTPS apex response is missing HSTS"
}

verify_legacy_adopted_transport_runtime() {
  local apex_headers expected_config http_apex http_www https_apex https_www www_headers
  local legacy_path state template

  # Adoption deliberately leaves the already-serving Nginx LKG untouched.
  # Bind that temporary exception to the exact reviewed historical template;
  # accepting transport behavior alone could bless an unrelated root config.
  validate_nginx_config
  [ "$(stat -c '%h' "$NGINX_CONFIG_FILE")" -eq 1 ] ||
    die "legacy Nginx configuration has multiple hard links"
  template="$(dirname "$PRODUCTION_COMPOSE_FILE")/nginx-https.conf.template"
  [ -f "$template" ] && [ ! -L "$template" ] ||
    die "reviewed legacy Nginx template is missing"
  expected_config="$(mktemp)"
  register_temp_file "$expected_config"
  sed 's/__DOMAIN__/gole.co.kr/g' "$template" > "$expected_config"
  cmp -s "$expected_config" "$NGINX_CONFIG_FILE" ||
    die "legacy Nginx configuration does not match the reviewed release"
  rm -f -- "$expected_config"
  forget_temp_file "$expected_config"

  state="$(docker inspect --format \
    '{{.State.Status}}:{{if .State.Health}}{{.State.Health.Status}}{{else}}missing{{end}}' \
    gole-nginx 2>/dev/null || true)"
  [ "$state" = "running:healthy" ] || [ "$state" = "running:missing" ] ||
    die "Nginx is not running"
  docker exec gole-nginx nginx -t >/dev/null 2>&1 || die "Nginx runtime validation failed"

  legacy_path="/__gole-legacy-transport-check?source=adoption"
  http_apex="$(curl -sS --max-time 15 --resolve gole.co.kr:80:127.0.0.1 \
    --output /dev/null --write-out '%{http_code}|%{redirect_url}' \
    "http://gole.co.kr${legacy_path}")" || die "legacy HTTP apex redirect check failed"
  [ "$http_apex" = "301|https://gole.co.kr${legacy_path}" ] ||
    die "legacy HTTP apex redirect changed"
  http_www="$(curl -sS --max-time 15 --resolve www.gole.co.kr:80:127.0.0.1 \
    --output /dev/null --write-out '%{http_code}|%{redirect_url}' \
    "http://www.gole.co.kr${legacy_path}")" || die "legacy HTTP www redirect check failed"
  [ "$http_www" = "301|https://www.gole.co.kr${legacy_path}" ] ||
    die "legacy HTTP www redirect changed"
  https_apex="$(curl -sS --max-time 15 --resolve gole.co.kr:443:127.0.0.1 \
    --output /dev/null --write-out '%{http_code}|%{redirect_url}' \
    https://gole.co.kr/)" || die "legacy HTTPS apex check failed"
  [ "$https_apex" = "200|" ] || die "legacy HTTPS apex response changed"
  https_www="$(curl -sS --max-time 15 --resolve www.gole.co.kr:443:127.0.0.1 \
    --output /dev/null --write-out '%{http_code}|%{redirect_url}' \
    https://www.gole.co.kr/)" || die "legacy HTTPS www check failed"
  [ "$https_www" = "200|" ] || die "legacy HTTPS www response changed"
  apex_headers="$(curl -fsSI --max-time 15 --resolve gole.co.kr:443:127.0.0.1 \
    https://gole.co.kr/)" || die "legacy HTTPS apex header check failed"
  grep -Eiq '^strict-transport-security:[[:space:]]*max-age=31536000([;[:space:]]|$)' \
    <<<"$apex_headers" || die "legacy HTTPS apex response is missing HSTS"
  www_headers="$(curl -fsSI --max-time 15 --resolve www.gole.co.kr:443:127.0.0.1 \
    https://www.gole.co.kr/)" || die "legacy HTTPS www header check failed"
  grep -Eiq '^strict-transport-security:[[:space:]]*max-age=31536000([;[:space:]]|$)' \
    <<<"$www_headers" || die "legacy HTTPS www response is missing HSTS"
}

expected_runtime_resource_limits() {
  case "$1" in
    mongo) printf '1000000000|1879048192\n' ;;
    mongo-init) printf '250000000|268435456\n' ;;
    redis) printf '500000000|402653184\n' ;;
    minio) printf '750000000|805306368\n' ;;
    minio-init) printf '250000000|134217728\n' ;;
    support-agent) printf '250000000|201326592\n' ;;
    backend) printf '1500000000|2147483648\n' ;;
    budget-relay) printf '250000000|134217728\n' ;;
    frontend) printf '750000000|671088640\n' ;;
    nginx) printf '500000000|201326592\n' ;;
    *) die "strict runtime resource contract is missing: $1" ;;
  esac
}

verify_strict_live_compose_runtime() {
  local actual_image actual_mounts actual_networks actual_ports container container_ids
  local actual_resources expected_image expected_mounts expected_networks expected_ports
  local expected_resources image_ref logging model restart_policy security service state
  model="$(mktemp)"
  register_temp_file "$model"
  chmod 0600 "$model"
  render_deployment_compose_model "$model"
  for service in mongo redis minio support-agent backend frontend nginx budget-relay; do
    container="$(deployment_container_name "$service")"
    verify_compose_container_identity "$container" "$service"
    image_ref="$(compose_model_service_image "$model" "$service")" ||
      die "strict runtime image is missing: $service"
    expected_image="$(docker image inspect --format '{{.Id}}' "$image_ref" 2>/dev/null || true)"
    actual_image="$(docker inspect --format '{{.Image}}' "$container" 2>/dev/null || true)"
    [[ "$expected_image" =~ ^sha256:[0-9a-f]{64}$ ]] && [ "$actual_image" = "$expected_image" ] ||
      die "strict runtime image identity changed: $service"
    actual_networks="$(docker inspect --format \
      '{{range $name, $_ := .NetworkSettings.Networks}}{{println $name}}{{end}}' \
      "$container" | LC_ALL=C sort | paste -sd, -)" ||
      die "strict runtime networks could not be inspected: $service"
    case "$service" in
      backend) expected_networks=gole_agent,gole_data,gole_edge ;;
      mongo | redis | minio) expected_networks=gole_data ;;
      support-agent) expected_networks=gole_agent ;;
      frontend | nginx | budget-relay) expected_networks=gole_edge ;;
    esac
    [ "$actual_networks" = "$expected_networks" ] ||
      die "strict runtime network boundary changed: $service"
    actual_ports="$(docker inspect --format '{{json .HostConfig.PortBindings}}' "$container" |
      python3 -c 'import json,sys; print(json.dumps(json.load(sys.stdin) or {}, separators=(",", ":"), sort_keys=True))')" ||
      die "strict runtime ports could not be inspected: $service"
    case "$service" in
      backend) expected_ports='{"8080/tcp":[{"HostIp":"127.0.0.1","HostPort":"8080"}]}' ;;
      frontend) expected_ports='{"3000/tcp":[{"HostIp":"127.0.0.1","HostPort":"3000"}]}' ;;
      nginx) expected_ports='{"443/tcp":[{"HostIp":"","HostPort":"443"}],"80/tcp":[{"HostIp":"","HostPort":"80"}]}' ;;
      *) expected_ports='{}' ;;
    esac
    [ "$actual_ports" = "$expected_ports" ] || die "strict runtime published ports changed: $service"
    if [ "$service" = mongo ] || [ "$service" = redis ] || [ "$service" = minio ]; then
      actual_mounts="$(docker inspect --format '{{json .Mounts}}' "$container" |
        python3 -c 'import json,sys
m=json.load(sys.stdin) or []
rows=[]
for x in m:
    if not isinstance(x,dict): raise SystemExit(1)
    rows.append("%s|%s|%s|%s" % (x.get("Type",""),x.get("Name",""),x.get("Destination",""),str(bool(x.get("RW"))).lower()))
print(",".join(sorted(rows)))')" || die "strict runtime mounts could not be inspected: $service"
      case "$service" in
        mongo) expected_mounts='volume|gole_mongo-data|/data/db|true' ;;
        redis) expected_mounts='volume|gole_redis-data|/data|true' ;;
        minio) expected_mounts='volume|gole_minio-data|/data|true' ;;
      esac
      [ "$actual_mounts" = "$expected_mounts" ] ||
        die "strict runtime persistent volume changed: $service"
    fi
    logging="$(docker inspect --format \
      '{{.HostConfig.LogConfig.Type}}|{{index .HostConfig.LogConfig.Config "max-size"}}|{{index .HostConfig.LogConfig.Config "max-file"}}' \
      "$container")" || die "strict runtime logging could not be inspected: $service"
    [ "$logging" = 'local|10m|3' ] || die "strict runtime log rotation changed: $service"
    security="$(docker inspect --format '{{json .HostConfig.SecurityOpt}}' "$container" |
      python3 -c 'import json,sys; print("true" if "no-new-privileges:true" in (json.load(sys.stdin) or []) else "false")')" ||
      die "strict runtime security option changed: $service"
    [ "$security" = true ] || die "strict runtime security option changed: $service"
    restart_policy="$(docker inspect --format '{{.HostConfig.RestartPolicy.Name}}' "$container")" ||
      die "strict runtime restart policy could not be inspected: $service"
    [ "$restart_policy" = unless-stopped ] || die "strict runtime restart policy changed: $service"
    actual_resources="$(docker inspect --format \
      '{{.HostConfig.NanoCpus}}|{{.HostConfig.Memory}}' "$container")" ||
      die "strict runtime resource limits could not be inspected: $service"
    expected_resources="$(expected_runtime_resource_limits "$service")"
    [ "$actual_resources" = "$expected_resources" ] ||
      die "strict runtime resource limits changed: $service"
  done
  for service in mongo-init minio-init; do
    container_ids="$(production_compose "$APP_ENV_FILE" ps -a -q "$service")" ||
      die "strict initializer could not be resolved: $service"
    [ -n "$container_ids" ] && [ "$(wc -l <<<"$container_ids")" -eq 1 ] ||
      die "strict initializer provenance is missing: $service"
    container="$container_ids"
    verify_compose_container_identity "$container" "$service"
    image_ref="$(compose_model_service_image "$model" "$service")" ||
      die "strict initializer image is missing: $service"
    expected_image="$(docker image inspect --format '{{.Id}}' "$image_ref" 2>/dev/null || true)"
    actual_image="$(docker inspect --format '{{.Image}}' "$container" 2>/dev/null || true)"
    [[ "$expected_image" =~ ^sha256:[0-9a-f]{64}$ ]] && [ "$actual_image" = "$expected_image" ] ||
      die "strict initializer image identity changed: $service"
    state="$(docker inspect --format '{{.State.Status}}:{{.State.ExitCode}}' "$container")" ||
      die "strict initializer state could not be inspected: $service"
    [ "$state" = exited:0 ] || die "strict initializer did not complete successfully: $service"
    actual_resources="$(docker inspect --format \
      '{{.HostConfig.NanoCpus}}|{{.HostConfig.Memory}}' "$container")" ||
      die "strict initializer resource limits could not be inspected: $service"
    expected_resources="$(expected_runtime_resource_limits "$service")"
    [ "$actual_resources" = "$expected_resources" ] ||
      die "strict initializer resource limits changed: $service"
  done
  rm -f -- "$model"
  forget_temp_file "$model"
}

verify_deployment_runtime_components_base() {
  local container expected_sha="$1" state
  select_release "$expected_sha"
  validate_current_production_model
  for container in gole-backend gole-frontend gole-budget-relay gole-support-agent gole-nginx; do
    state="$(docker inspect --format \
      '{{.State.Status}}:{{if .State.Health}}{{.State.Health.Status}}{{else}}missing{{end}}' \
      "$container" 2>/dev/null || true)"
    [ "$state" = "running:healthy" ] || die "required production container is not healthy"
  done
  verify_strict_live_compose_runtime
  systemctl is-active --quiet gole-cost-guard-watchdog.timer ||
    die "cost guard watchdog timer is not active"
}

verify_initial_http_transport_runtime() {
  local canonical_path http_redirect state
  state="$(docker inspect --format \
    '{{.State.Status}}:{{if .State.Health}}{{.State.Health.Status}}{{else}}missing{{end}}' \
    gole-nginx 2>/dev/null || true)"
  [ "$state" = running:healthy ] || die "initial HTTP Nginx is not healthy"
  docker exec gole-nginx nginx -t >/dev/null 2>&1 ||
    die "initial HTTP Nginx validation failed"
  curl -fsS --max-time 15 --resolve gole.co.kr:80:127.0.0.1 \
    http://gole.co.kr/actuator/health/readiness >/dev/null ||
    die "initial HTTP apex readiness failed"
  curl -fsS --max-time 15 --resolve gole.co.kr:80:127.0.0.1 \
    http://gole.co.kr/icon.svg >/dev/null || die "initial HTTP apex frontend failed"
  canonical_path="/__gole-canonical-check?source=initial"
  http_redirect="$(curl -sS --max-time 15 --resolve www.gole.co.kr:80:127.0.0.1 \
    --output /dev/null --write-out '%{http_code}|%{redirect_url}' \
    "http://www.gole.co.kr${canonical_path}")" ||
    die "initial HTTP canonical redirect check failed"
  [ "$http_redirect" = "301|https://gole.co.kr${canonical_path}" ] ||
    die "initial HTTP www does not redirect directly to the canonical apex"
}

verify_deployment_runtime_components() {
  local expected_sha="$1"
  verify_deployment_runtime_components_base "$expected_sha"
  verify_public_transport_runtime
}

verify_initial_deployment_runtime_components() {
  local expected_sha="$1"
  verify_deployment_runtime_components_base "$expected_sha"
  verify_initial_http_transport_runtime
}

verify_adopted_deployment_runtime() {
  local age expected_sha="$1" now
  [ "${SUDO_USER:-root}" = root ] ||
    die "legacy deployment runtime verification is root-only"
  read_metadata_migration_marker || die "legacy metadata migration is not pending"
  [ "$METADATA_MIGRATION_STATE" = pending ] &&
    [ "$METADATA_MIGRATION_LEGACY_SHA" = "$expected_sha" ] ||
    die "legacy metadata migration SHA does not match"
  [ "$(read_deployed_sha)" = "$expected_sha" ] ||
    die "adopted deployment marker does not match"
  verify_metadata_pending_policy
  select_release "$expected_sha"
  validate_existing_deployment_runtime "$expected_sha" true
  verify_legacy_adopted_transport_runtime
  systemctl is-active --quiet gole-cloud-broker.service ||
    die "root cloud broker is not active"
  [ -f /run/gole-cloud-broker/policy-heartbeat ] &&
    [ ! -L /run/gole-cloud-broker/policy-heartbeat ] &&
    [ "$(stat -c '%U:%G:%a' /run/gole-cloud-broker/policy-heartbeat)" = "root:golecloud:600" ] ||
    die "root cloud broker policy heartbeat is invalid"
  now="$(date +%s)"
  age=$((now - $(stat -c %Y /run/gole-cloud-broker/policy-heartbeat)))
  [ "$age" -ge 0 ] && [ "$age" -le 30 ] ||
    die "root cloud broker policy heartbeat is stale"
}

verify_seller_identity_launch_preflight() {
  local count query
  # Output is deliberately reduced to one integer inside MongoDB. Neither an
  # account id, seller id, phone number nor verification timestamp crosses the
  # root helper boundary or reaches Actions logs.
  query='const r=db.getSiblingDB("gole").listings.aggregate([
    {$match:{status:"ACTIVE"}},
    {$lookup:{from:"accounts",localField:"sellerId",foreignField:"_id",as:"account"}},
    {$match:{$or:[
      {"account.0":{$exists:false}},
      {"account.0.phoneNumber":{$exists:false}},
      {"account.0.phoneNumber":null},
      {"account.0.phoneNumber":""},
      {"account.0.phoneVerifiedAt":{$exists:false}},
      {"account.0.phoneVerifiedAt":null}
    ]}},
    {$count:"count"}
  ]).toArray(); print(r.length===0?0:r[0].count);'
  count="$(production_compose "$APP_ENV_FILE" exec -T mongo \
    mongosh --quiet --norc --eval "$query")" ||
    die "seller identity launch preflight could not query MongoDB"
  count="${count//$'\r'/}"
  [[ "$count" =~ ^[0-9]+$ ]] || die "seller identity launch preflight returned invalid output"
  [ "$count" -eq 0 ] ||
    die "active listings with incomplete verified seller identity remain: $count"
}

verify_full_deployment_runtime() {
  local expected_sha="$1" request_id="$2"
  require_deployment_transaction "$request_id" marker-recorded
  [ "$DEPLOY_TX_NEW_SHA" = "$expected_sha" ] || die "runtime verification SHA does not match"
  [ "$(read_deployed_sha)" = "$expected_sha" ] || die "deployed SHA marker does not match"
  verify_deployment_runtime_components "$expected_sha"
  advance_deployment_transaction "$request_id" marker-recorded runtime-verified
}

verify_initial_http_commit() {
  local expected_sha="$1" request_id="$2"
  require_deployment_transaction "$request_id" marker-recorded
  [ "$DEPLOY_TX_TARGET" = all ] && [ "$DEPLOY_TX_PREVIOUS_SHA" = 0 ] &&
    [ "$DEPLOY_TX_NEW_SHA" = "$expected_sha" ] ||
    die "initial HTTP commit transaction does not match"
  [ "$(read_deployed_sha)" = "$expected_sha" ] ||
    die "initial HTTP commit marker does not match"
  [ ! -e "$INITIAL_DEPLOY_FILE" ] && [ ! -L "$INITIAL_DEPLOY_FILE" ] ||
    die "initial HTTP commit authorization marker remains"
  require_matching_nginx_transaction "$request_id"
  [ "$NGINX_TXN_STATE" = committed ] && [ "$NGINX_TXN_DEPLOY_SHA" = "$expected_sha" ] ||
    die "initial HTTP committed Nginx journal does not match"
  verify_initial_deployment_runtime_components "$expected_sha"
  verify_seller_identity_launch_preflight
  advance_deployment_transaction "$request_id" marker-recorded initial-http-verified
}

complete_initial_tls_commit() {
  local request_id="$1"
  require_deployment_transaction "$request_id" initial-http-verified
  [ "$DEPLOY_TX_TARGET" = all ] && [ "$DEPLOY_TX_PREVIOUS_SHA" = 0 ] ||
    die "initial TLS completion transaction does not match"
  [ "$(read_deployed_sha)" = "$DEPLOY_TX_NEW_SHA" ] ||
    die "initial TLS completion marker does not match"
  [ ! -e "$INITIAL_DEPLOY_FILE" ] && [ ! -L "$INITIAL_DEPLOY_FILE" ] ||
    die "initial TLS completion authorization marker remains"
  [ ! -e "$NGINX_TRANSACTION_FILE" ] && [ ! -L "$NGINX_TRANSACTION_FILE" ] ||
    die "deployment Nginx transaction remains before certificate activation"
  verify_deployment_runtime_components "$DEPLOY_TX_NEW_SHA"
  verify_seller_identity_launch_preflight
  advance_deployment_transaction "$request_id" initial-http-verified runtime-verified
  finalize_deployment_transaction "$request_id"
}

adoption_image_marker() {
  local request_id="$1"
  validate_request_id "$request_id"
  printf '%s/backend-image.%s\n' "$ADOPTION_BACKUP_DIR" "${request_id//-/}"
}

adoption_rollback_image() {
  local request_id="$1"
  validate_request_id "$request_id"
  printf 'gole/adoption-backend:%s\n' "${request_id//-/}"
}

snapshot_adoption_backend_image() {
  local candidate image_id labels marker request_id="$1" rollback_image
  validate_request_id "$request_id"
  marker="$(adoption_image_marker "$request_id")"
  [ ! -e "$marker" ] && [ ! -L "$marker" ] || die "adoption image snapshot already exists"
  labels="$(docker inspect --format \
    '{{index .Config.Labels "com.docker.compose.project"}}|{{index .Config.Labels "com.docker.compose.service"}}' \
    gole-backend)" || die "could not verify adopted backend ownership"
  [ "$labels" = gole\|backend ] || die "adopted backend ownership is invalid"
  image_id="$(docker inspect --format '{{.Image}}' gole-backend)" ||
    die "could not inspect adopted backend image"
  [[ "$image_id" =~ ^sha256:[0-9a-f]{64}$ ]] || die "adopted backend image ID is invalid"
  rollback_image="$(adoption_rollback_image "$request_id")"
  docker image tag "$image_id" "$rollback_image"
  [ "$(docker image inspect --format '{{.Id}}' "$rollback_image" 2>/dev/null || true)" = "$image_id" ] ||
    die "adopted backend rollback image could not be verified"
  candidate="$(mktemp)"
  register_temp_file "$candidate"
  printf 'request_id=%s\nimage.backend=%s\n' "$request_id" "$image_id" > "$candidate"
  atomic_install "$candidate" "$marker" 0600 root
  sync_host_state "$marker"
  rm -f -- "$candidate"
  forget_temp_file "$candidate"
}

read_adoption_backend_image() {
  local key marker request_id="$1" seen_image=0 seen_request=0 value
  marker="$(adoption_image_marker "$request_id")"
  [ -f "$marker" ] && [ ! -L "$marker" ] &&
    [ "$(stat -c '%U:%G:%a' "$marker")" = root:root:600 ] ||
    die "adoption image snapshot is missing or invalid"
  ADOPTION_BACKEND_IMAGE_ID=""
  while IFS='=' read -r key value || [ -n "${key}${value}" ]; do
    case "$key" in
      request_id)
        [ "$seen_request" -eq 0 ] || die "duplicate adoption image request"
        [ "$value" = "$request_id" ] || die "adoption image request does not match"
        seen_request=1
        ;;
      image.backend)
        [ "$seen_image" -eq 0 ] || die "duplicate adoption backend image"
        [[ "$value" =~ ^sha256:[0-9a-f]{64}$ ]] || die "adoption backend image ID is invalid"
        ADOPTION_BACKEND_IMAGE_ID="$value"
        seen_image=1
        ;;
      *) die "unknown adoption image snapshot field" ;;
    esac
  done < "$marker"
  [ "$seen_request$seen_image" = 11 ] || die "adoption image snapshot is incomplete"
}

ensure_adoption_backend_image_snapshot() {
  local marker request_id="$1"
  marker="$(adoption_image_marker "$request_id")"
  if [ ! -e "$marker" ] && [ ! -L "$marker" ]; then
    # A prepared transaction cannot have changed the backend yet. Rebuilding
    # its snapshot makes the abort path deterministic after an interrupted tag.
    [ "$ADOPT_STATE" = prepared ] || die "installed adoption is missing its image snapshot"
    docker image rm "$(adoption_rollback_image "$request_id")" >/dev/null 2>&1 || true
    snapshot_adoption_backend_image "$request_id"
  fi
  read_adoption_backend_image "$request_id"
}

verify_adoption_backend_image_runtime() {
  local current_id labels request_id="$1" state
  validate_request_id "$request_id"
  [ "${ADOPT_REQUEST_ID:-}" = "$request_id" ] ||
    die "adoption backend verification does not match transaction"
  read_adoption_backend_image "$request_id"
  labels="$(docker inspect --format \
    '{{index .Config.Labels "com.docker.compose.project"}}|{{index .Config.Labels "com.docker.compose.service"}}' \
    gole-backend)" || die "could not verify adopted backend ownership"
  [ "$labels" = gole\|backend ] || die "adopted backend ownership is invalid"
  current_id="$(docker inspect --format '{{.Image}}' gole-backend)" ||
    die "could not verify adopted backend image"
  [ "$current_id" = "$ADOPTION_BACKEND_IMAGE_ID" ] ||
    die "adopted backend does not run its snapshotted image"
  state="$(docker inspect --format \
    '{{.State.Status}}:{{if .State.Health}}{{.State.Health.Status}}{{else}}missing{{end}}' \
    gole-backend)" || die "could not verify adopted backend health"
  [ "$state" = running:healthy ] || die "adopted backend is not healthy"
}

cleanup_adoption_backend_image_artifacts() {
  local marker request_id="$1"
  validate_request_id "$request_id"
  marker="$(adoption_image_marker "$request_id")"
  docker image rm "$(adoption_rollback_image "$request_id")" >/dev/null 2>&1 || true
  if [ -e "$marker" ] || [ -L "$marker" ]; then
    remove_host_state "$marker"
  else
    sync -f "$ADOPTION_BACKUP_DIR"
  fi
}

restart_adoption_services() {
  local available current_id override request_id="$1" rollback_id rollback_image service
  require_matching_adoption_transaction "$request_id"
  case "$ADOPT_STATE" in
    installed | rollback-restored | prepared) ;;
    *) die "adoption services cannot restart in the current transaction state" ;;
  esac
  validate_clean_checkout_sha "$ADOPT_DEPLOYMENT_SHA"
  validate_current_environment
  validate_production_compose "$APP_ENV_FILE" legacy-adoption
  ensure_adoption_backend_image_snapshot "$request_id"
  rollback_image="$(adoption_rollback_image "$request_id")"
  rollback_id="$(docker image inspect --format '{{.Id}}' "$rollback_image" 2>/dev/null || true)"
  [ "$rollback_id" = "$ADOPTION_BACKEND_IMAGE_ID" ] ||
    die "adoption backend rollback image changed"
  # Keep the canonical local tag on the adopted LKG as well. Later Secret Sync
  # must not resurrect a drifted candidate after this one-time migration.
  docker image tag "$rollback_image" gole/backend:local
  [ "$(docker image inspect --format '{{.Id}}' gole/backend:local 2>/dev/null || true)" = \
    "$ADOPTION_BACKEND_IMAGE_ID" ] || die "adoption backend image activation failed"
  available="$(production_compose "$APP_ENV_FILE" config --services)"
  for service in backend frontend budget-relay nginx; do
    grep -qx "$service" <<<"$available" || die "legacy Compose is missing a required service"
  done
  override="$(mktemp)"
  register_temp_file "$override"
  chmod 0600 "$override"
  printf 'services:\n  backend:\n    image: %s\n' "$rollback_image" > "$override"
  sync -f "$override"
  production_compose_with_override "$APP_ENV_FILE" "$override" \
    up -d --no-build --no-deps --force-recreate --wait backend
  current_id="$(docker inspect --format '{{.Image}}' gole-backend)" ||
    die "could not verify restarted adoption backend"
  [ "$current_id" = "$ADOPTION_BACKEND_IMAGE_ID" ] ||
    die "restarted adoption backend image does not match"
  rm -f -- "$override"
  forget_temp_file "$override"
  production_compose "$APP_ENV_FILE" exec -T nginx nginx -t >/dev/null
  production_compose "$APP_ENV_FILE" exec -T nginx nginx -s reload >/dev/null
}

verify_adoption_services() {
  local available backend_image request_id="$1" state
  require_matching_adoption_transaction "$request_id"
  case "$ADOPT_STATE" in
    installed | rollback-restored) ;;
    *) die "adoption services cannot be verified in the current transaction state" ;;
  esac
  validate_existing_deployment_runtime "$ADOPT_DEPLOYMENT_SHA" false
  ensure_adoption_backend_image_snapshot "$request_id"
  backend_image="$(docker inspect --format '{{.Image}}' gole-backend)" ||
    die "could not verify adopted backend image"
  [ "$backend_image" = "$ADOPTION_BACKEND_IMAGE_ID" ] ||
    die "adopted backend image changed during environment migration"
  available="$(production_compose "$APP_ENV_FILE" config --services)"
  if grep -qx support-agent <<<"$available"; then
    state="$(docker inspect --format '{{.State.Status}}:{{if .State.Health}}{{.State.Health.Status}}{{else}}missing{{end}}' \
      gole-support-agent 2>/dev/null || true)"
    [ "$state" = "running:healthy" ] || [ "$state" = "running:missing" ] ||
      die "support agent is not running"
  fi
  state="$(docker inspect --format '{{.State.Status}}:{{if .State.Health}}{{.State.Health.Status}}{{else}}missing{{end}}' \
    gole-nginx 2>/dev/null || true)"
  [ "$state" = "running:healthy" ] || [ "$state" = "running:missing" ] ||
    die "Nginx is not running"
  docker exec gole-nginx nginx -t >/dev/null 2>&1 || die "Nginx runtime validation failed"
}

validate_nginx_config() {
  local nginx_size
  if [ ! -f "$NGINX_CONFIG_FILE" ] || [ -L "$NGINX_CONFIG_FILE" ] ||
    [ "$(stat -c '%U:%G:%a' "$NGINX_CONFIG_FILE")" != "root:root:644" ]; then
    die "current Nginx configuration is missing or invalid"
  fi
  nginx_size="$(stat -c '%s' "$NGINX_CONFIG_FILE")"
  if [ "$nginx_size" -le 0 ] || [ "$nginx_size" -gt 131072 ]; then
    die "current Nginx configuration size is invalid"
  fi
}

nginx_candidate_path() {
  local compact_request_id request_id="$1"
  validate_request_id "$request_id"
  compact_request_id="${request_id//-/}"
  printf '/tmp/gole-nginx.%s\n' "$compact_request_id"
}

validate_nginx_candidate() {
  local candidate mode owner request_id="$1" size
  candidate="$(nginx_candidate_path "$request_id")"
  if [ ! -f "$candidate" ] || [ -L "$candidate" ]; then
    die "Nginx candidate is missing or invalid"
  fi
  owner="$(stat -c '%U' "$candidate")"
  mode="$(stat -c '%a' "$candidate")"
  size="$(stat -c '%s' "$candidate")"
  [ "$owner" = "$DEPLOY_USER" ] || die "Nginx candidate ownership is invalid"
  [ "$mode" = "600" ] || die "Nginx candidate permissions are invalid"
  if [ "$size" -le 0 ] || [ "$size" -gt 131072 ]; then
    die "Nginx candidate size is invalid"
  fi
  printf '%s\n' "$candidate"
}

validate_nginx_backup_path() {
  local path="$1"
  [[ "$path" =~ ^/var/backups/gole-nginx/nginx\.conf\.[0-9a-fA-F-]{36}$ ]] ||
    die "invalid Nginx backup path"
  if [ ! -f "$path" ] || [ -L "$path" ] ||
    [ "$(stat -c '%U:%G:%a' "$path")" != "root:root:600" ]; then
    die "Nginx backup is missing or invalid"
  fi
}

write_nginx_transaction() {
  local backup_file="$3" candidate_sha256="$4" deploy_sha="$5" request_id="$2" state="$1"
  local transaction_candidate
  transaction_candidate="$(mktemp)"
  printf '%s\n' \
    "state=$state" \
    "request_id=$request_id" \
    "backup_file=$backup_file" \
    "candidate_sha256=$candidate_sha256" \
    "deploy_sha=$deploy_sha" > "$transaction_candidate"
  atomic_install "$transaction_candidate" "$NGINX_TRANSACTION_FILE" 0600 root
  rm -f -- "$transaction_candidate"
  sync_host_state "$NGINX_TRANSACTION_FILE"
}

read_nginx_transaction() {
  local key value seen_backup=0 seen_deploy=0 seen_hash=0 seen_request=0 seen_state=0
  if [ ! -e "$NGINX_TRANSACTION_FILE" ] && [ ! -L "$NGINX_TRANSACTION_FILE" ]; then
    return 1
  fi
  if [ ! -f "$NGINX_TRANSACTION_FILE" ] || [ -L "$NGINX_TRANSACTION_FILE" ] ||
    [ "$(stat -c '%U:%G:%a' "$NGINX_TRANSACTION_FILE")" != "root:root:600" ]; then
    die "Nginx transaction metadata is invalid"
  fi
  NGINX_TXN_STATE=""
  NGINX_TXN_REQUEST_ID=""
  NGINX_TXN_BACKUP_FILE=""
  NGINX_TXN_CANDIDATE_SHA256=""
  NGINX_TXN_DEPLOY_SHA=""
  while IFS='=' read -r key value || [ -n "${key}${value}" ]; do
    case "$key" in
      state)
        [ "$seen_state" -eq 0 ] || die "Nginx transaction has duplicate state"
        NGINX_TXN_STATE="$value"
        seen_state=1
        ;;
      request_id)
        [ "$seen_request" -eq 0 ] || die "Nginx transaction has duplicate request id"
        NGINX_TXN_REQUEST_ID="$value"
        seen_request=1
        ;;
      backup_file)
        [ "$seen_backup" -eq 0 ] || die "Nginx transaction has duplicate backup path"
        NGINX_TXN_BACKUP_FILE="$value"
        seen_backup=1
        ;;
      candidate_sha256)
        [ "$seen_hash" -eq 0 ] || die "Nginx transaction has duplicate candidate hash"
        NGINX_TXN_CANDIDATE_SHA256="$value"
        seen_hash=1
        ;;
      deploy_sha)
        [ "$seen_deploy" -eq 0 ] || die "Nginx transaction has duplicate deployment SHA"
        NGINX_TXN_DEPLOY_SHA="$value"
        seen_deploy=1
        ;;
      *) die "Nginx transaction contains an unknown field" ;;
    esac
  done < "$NGINX_TRANSACTION_FILE"
  [ "$seen_state$seen_request$seen_backup$seen_hash$seen_deploy" = "11111" ] ||
    die "Nginx transaction is incomplete"
  [[ "$NGINX_TXN_STATE" =~ ^(prepared|installed|committed|rollback-restored)$ ]] ||
    die "Nginx transaction state is invalid"
  validate_request_id "$NGINX_TXN_REQUEST_ID"
  validate_nginx_backup_path "$NGINX_TXN_BACKUP_FILE"
  [[ "$NGINX_TXN_CANDIDATE_SHA256" =~ ^[0-9a-f]{64}$ ]] ||
    die "Nginx transaction hash is invalid"
  [[ "$NGINX_TXN_DEPLOY_SHA" =~ ^[0-9a-f]{40}$ ]] ||
    die "Nginx transaction deployment SHA is invalid"
}

begin_nginx_transaction() {
  local backup_file candidate_sha256 deploy_sha="$2" request_id="$1" staged_candidate template
  [[ "$deploy_sha" =~ ^[0-9a-f]{40}$ ]] || die "invalid deployment SHA"
  require_deployment_transaction "$request_id" built
  [ "$DEPLOY_TX_TARGET" = all ] && [ "$DEPLOY_TX_NEW_SHA" = "$deploy_sha" ] ||
    die "Nginx transaction does not match deployment"
  select_release "$deploy_sha"
  if [ -e "$NGINX_TRANSACTION_FILE" ] || [ -L "$NGINX_TRANSACTION_FILE" ]; then
    die "an Nginx transaction is already active"
  fi
  validate_nginx_config
  template="$(release_path "$deploy_sha")/infra/gcp/nginx-http.conf.template"
  if grep -Eq '^[[:space:]]*listen[[:space:]]+443([[:space:]]|;)' "$NGINX_CONFIG_FILE"; then
    template="$(release_path "$deploy_sha")/infra/gcp/nginx-https.conf.template"
  fi
  [ -f "$template" ] && [ ! -L "$template" ] || die "reviewed Nginx template is missing"
  staged_candidate="$(mktemp /etc/gole/.nginx.candidate.XXXXXX)"
  register_temp_file "$staged_candidate"
  sed 's/__DOMAIN__/gole.co.kr/g' "$template" > "$staged_candidate"
  chown root:root "$staged_candidate"
  chmod 0600 "$staged_candidate"
  docker run --rm \
    --network none \
    --add-host backend:127.0.0.1 \
    --add-host frontend:127.0.0.1 \
    --volume "$staged_candidate:/etc/nginx/conf.d/default.conf:ro" \
    --volume gole_letsencrypt:/etc/letsencrypt:ro \
    "$NGINX_VALIDATION_IMAGE" nginx -t >/dev/null
  install -d -m 0700 -o root -g root "$NGINX_BACKUP_DIR"
  backup_file="$NGINX_BACKUP_DIR/nginx.conf.$request_id"
  if [ -e "$backup_file" ] || [ -L "$backup_file" ]; then
    die "Nginx transaction backup already exists"
  fi
  install -m 0600 -o root -g root "$NGINX_CONFIG_FILE" "$backup_file"
  sync_host_state "$backup_file"
  candidate_sha256="$(sha256sum "$staged_candidate" | cut -d' ' -f1)"
  write_nginx_transaction prepared "$request_id" "$backup_file" "$candidate_sha256" "$deploy_sha"
  atomic_install "$staged_candidate" "$NGINX_CONFIG_FILE" 0644 root
  sync_host_state "$NGINX_CONFIG_FILE"
  write_nginx_transaction installed "$request_id" "$backup_file" "$candidate_sha256" "$deploy_sha"
  advance_deployment_transaction "$request_id" built nginx-installed
  rm -f -- "$staged_candidate"
  forget_temp_file "$staged_candidate"
}

require_matching_nginx_transaction() {
  local request_id="$1"
  read_nginx_transaction || die "Nginx transaction is missing"
  [ "$NGINX_TXN_REQUEST_ID" = "$request_id" ] || die "Nginx transaction request does not match"
}

restore_nginx_transaction() {
  validate_nginx_backup_path "$NGINX_TXN_BACKUP_FILE"
  atomic_install "$NGINX_TXN_BACKUP_FILE" "$NGINX_CONFIG_FILE" 0644 root
  sync_host_state "$NGINX_CONFIG_FILE"
  write_nginx_transaction rollback-restored "$NGINX_TXN_REQUEST_ID" \
    "$NGINX_TXN_BACKUP_FILE" "$NGINX_TXN_CANDIDATE_SHA256" "$NGINX_TXN_DEPLOY_SHA"
}

abort_nginx_transaction() {
  local request_id="$1"
  require_matching_nginx_transaction "$request_id"
  if [ "$NGINX_TXN_STATE" != "rollback-restored" ]; then
    restore_nginx_transaction
  fi
}

recover_nginx_transaction() {
  local current_deployed_sha
  if ! read_nginx_transaction; then
    echo "NONE"
    return
  fi
  if [ "$NGINX_TXN_STATE" = "committed" ]; then
    if [ -f "$NGINX_CONFIG_FILE" ] && [ ! -L "$NGINX_CONFIG_FILE" ] &&
      [ "$(stat -c '%U:%G:%a' "$NGINX_CONFIG_FILE")" = "root:root:644" ] &&
      current_deployed_sha="$(read_deployed_sha 2>/dev/null)" &&
      [ "$current_deployed_sha" = "$NGINX_TXN_DEPLOY_SHA" ] &&
      [ "$(sha256sum "$NGINX_CONFIG_FILE" | cut -d' ' -f1)" = \
        "$NGINX_TXN_CANDIDATE_SHA256" ]; then
      remove_host_state "$NGINX_TRANSACTION_FILE"
      echo "COMMITTED"
      return
    fi
  fi
  if [ "$NGINX_TXN_STATE" != "rollback-restored" ]; then
    restore_nginx_transaction
  fi
  echo "RECOVERY_REQUIRED:$NGINX_TXN_REQUEST_ID"
}

finish_nginx_recovery() {
  local request_id="$1"
  require_matching_nginx_transaction "$request_id"
  [ "$NGINX_TXN_STATE" = "rollback-restored" ] || die "Nginx recovery is not ready to finish"
  validate_nginx_config
  [ "$(sha256sum "$NGINX_CONFIG_FILE" | cut -d' ' -f1)" = \
    "$(sha256sum "$NGINX_TXN_BACKUP_FILE" | cut -d' ' -f1)" ] ||
    die "recovered Nginx configuration hash is invalid"
  remove_host_state "$NGINX_TRANSACTION_FILE"
}

commit_nginx_transaction() {
  local request_id="$1"
  require_matching_nginx_transaction "$request_id"
  require_deployment_transaction "$request_id" verified
  [ "$NGINX_TXN_STATE" = "installed" ] || die "Nginx transaction is not installed"
  validate_nginx_config
  [ "$(sha256sum "$NGINX_CONFIG_FILE" | cut -d' ' -f1)" = "$NGINX_TXN_CANDIDATE_SHA256" ] ||
    die "Nginx configuration changed before commit"
  write_nginx_transaction committed "$NGINX_TXN_REQUEST_ID" \
    "$NGINX_TXN_BACKUP_FILE" "$NGINX_TXN_CANDIDATE_SHA256" "$NGINX_TXN_DEPLOY_SHA"
}

finalize_nginx_transaction() {
  local current_deployed_sha request_id="$1"
  require_matching_nginx_transaction "$request_id"
  require_deployment_transaction "$request_id" runtime-verified,initial-http-verified
  if [ "$DEPLOY_TX_STATE" = initial-http-verified ] &&
    { [ "$DEPLOY_TX_TARGET" != all ] || [ "$DEPLOY_TX_PREVIOUS_SHA" != 0 ]; }; then
    die "only the initial HTTP commit may finalize Nginx before TLS"
  fi
  [ "$NGINX_TXN_STATE" = "committed" ] || die "Nginx transaction is not committed"
  validate_nginx_config
  [ "$(sha256sum "$NGINX_CONFIG_FILE" | cut -d' ' -f1)" = "$NGINX_TXN_CANDIDATE_SHA256" ] ||
    die "committed Nginx configuration hash is invalid"
  current_deployed_sha="$(read_deployed_sha)"
  [ "$current_deployed_sha" = "$NGINX_TXN_DEPLOY_SHA" ] ||
    die "deployment marker does not match the Nginx transaction"
  remove_host_state "$NGINX_TRANSACTION_FILE"
}

verify_candidate_deployment_runtime() {
  local expected_sha="$1" request_id="$2" expected_state
  require_deployment_transaction "$request_id" refreshed,budget-updated
  [ "$DEPLOY_TX_NEW_SHA" = "$expected_sha" ] || die "candidate verification SHA does not match"
  if [ "$DEPLOY_TX_TARGET" = all ]; then
    expected_state=budget-updated
  else
    expected_state=refreshed
  fi
  [ "$DEPLOY_TX_STATE" = "$expected_state" ] || die "candidate verification is out of order"
  if [ "$DEPLOY_TX_PREVIOUS_SHA" = 0 ]; then
    verify_initial_deployment_runtime_components "$expected_sha"
  else
    verify_deployment_runtime_components "$expected_sha"
  fi
  if [ "$DEPLOY_TX_TARGET" = all ]; then
    verify_seller_identity_launch_preflight
  fi
  advance_deployment_transaction "$request_id" "$expected_state" verified
}

record_deployment_sha() {
  local requested_sha="$1" request_id="$2" initial_deployment=false
  require_deployment_transaction "$request_id" verified
  [ "$DEPLOY_TX_NEW_SHA" = "$requested_sha" ] || die "deployment marker SHA does not match"
  if [ "$DEPLOY_TX_TARGET" = all ]; then
    require_matching_nginx_transaction "$request_id"
    [ "$NGINX_TXN_STATE" = committed ] || die "Nginx transaction is not committed"
  fi
  if [ -e "$INITIAL_DEPLOY_FILE" ] || [ -L "$INITIAL_DEPLOY_FILE" ]; then
    validate_initial_deployment
    initial_deployment=true
  elif [ "$(read_deployed_sha)" != "$DEPLOY_TX_PREVIOUS_SHA" ]; then
    die "last-known-good marker changed during deployment"
  fi
  write_deployed_sha_exact "$requested_sha"
  if [ "$initial_deployment" = true ] && ! rm -f -- "$INITIAL_DEPLOY_FILE"; then
    rm -f -- "$DEPLOYED_SHA_FILE"
    die "could not retire the initial deployment marker"
  fi
  sync -f /etc/gole
  advance_deployment_transaction "$request_id" verified marker-recorded
}

read_metadata_migration_marker() {
  local key value seen_legacy=0 seen_state=0
  if [ ! -e "$METADATA_MIGRATION_MARKER" ] && [ ! -L "$METADATA_MIGRATION_MARKER" ]; then
    return 1
  fi
  if [ ! -f "$METADATA_MIGRATION_MARKER" ] || [ -L "$METADATA_MIGRATION_MARKER" ] ||
    [ "$(stat -c '%U:%G:%a' "$METADATA_MIGRATION_MARKER")" != "root:root:644" ]; then
    die "metadata migration marker is invalid"
  fi
  METADATA_MIGRATION_STATE=""
  METADATA_MIGRATION_LEGACY_SHA=""
  while IFS='=' read -r key value || [ -n "${key}${value}" ]; do
    case "$key" in
      state)
        [ "$seen_state" -eq 0 ] || die "metadata migration marker has duplicate state"
        METADATA_MIGRATION_STATE="$value"
        seen_state=1
        ;;
      legacy_sha)
        [ "$seen_legacy" -eq 0 ] || die "metadata migration marker has duplicate legacy SHA"
        METADATA_MIGRATION_LEGACY_SHA="$value"
        seen_legacy=1
        ;;
      *) die "metadata migration marker contains an unknown field" ;;
    esac
  done < "$METADATA_MIGRATION_MARKER"
  [ "$seen_state$seen_legacy" = "11" ] || die "metadata migration marker is incomplete"
  [[ "$METADATA_MIGRATION_STATE" =~ ^(pending|ratcheting)$ ]] ||
    die "metadata migration marker state is invalid"
  [[ "$METADATA_MIGRATION_LEGACY_SHA" =~ ^[0-9a-f]{40}$ ]] ||
    die "metadata migration legacy SHA is invalid"
}

write_metadata_migration_marker() {
  local candidate legacy_sha="$1" state="$2"
  [[ "$legacy_sha" =~ ^[0-9a-f]{40}$ ]] || die "invalid metadata migration legacy SHA"
  [[ "$state" =~ ^(pending|ratcheting)$ ]] || die "invalid metadata migration state"
  candidate="$(mktemp)"
  register_temp_file "$candidate"
  printf 'state=%s\nlegacy_sha=%s\n' "$state" "$legacy_sha" > "$candidate"
  atomic_install "$candidate" "$METADATA_MIGRATION_MARKER" 0644 root
  sync_host_state "$METADATA_MIGRATION_MARKER"
  rm -f -- "$candidate"
  forget_temp_file "$candidate"
}

verify_metadata_pending_policy() {
  ! iptables -w -t raw -C PREROUTING -j GOLE_METADATA_INPUT >/dev/null 2>&1 ||
    die "metadata container isolation was applied before the ratchet"
  iptables -w -C OUTPUT -d 169.254.169.254/32 -j GOLE_METADATA_OUTPUT >/dev/null 2>&1 &&
    iptables -w -C GOLE_METADATA_OUTPUT -m owner --uid-owner 0 -j RETURN >/dev/null 2>&1 &&
    iptables -w -C GOLE_METADATA_OUTPUT -j REJECT >/dev/null 2>&1 ||
    die "metadata host isolation is incomplete"
  if command -v ip6tables >/dev/null 2>&1; then
    ! ip6tables -w -t raw -C PREROUTING -j GOLE_METADATA_INPUT >/dev/null 2>&1 ||
      die "metadata IPv6 container isolation was applied before the ratchet"
    ip6tables -w -C OUTPUT -d fd20:ce::254/128 -j GOLE_METADATA_OUTPUT >/dev/null 2>&1 ||
      die "metadata IPv6 host isolation is incomplete"
  fi
}

verify_metadata_full_policy() {
  iptables -w -t raw -C PREROUTING -j GOLE_METADATA_INPUT >/dev/null 2>&1 &&
    iptables -w -t raw -C GOLE_METADATA_INPUT -d 169.254.169.254/32 -j DROP >/dev/null 2>&1 &&
    iptables -w -C OUTPUT -d 169.254.169.254/32 -j GOLE_METADATA_OUTPUT >/dev/null 2>&1 &&
    iptables -w -C GOLE_METADATA_OUTPUT -m owner --uid-owner 0 -j RETURN >/dev/null 2>&1 &&
    iptables -w -C GOLE_METADATA_OUTPUT -j REJECT >/dev/null 2>&1 ||
    die "metadata IPv4 isolation ratchet is incomplete"
  if command -v ip6tables >/dev/null 2>&1; then
    ip6tables -w -t raw -C PREROUTING -j GOLE_METADATA_INPUT >/dev/null 2>&1 &&
      ip6tables -w -t raw -C GOLE_METADATA_INPUT -d fd20:ce::254/128 -j DROP >/dev/null 2>&1 &&
      ip6tables -w -C OUTPUT -d fd20:ce::254/128 -j GOLE_METADATA_OUTPUT >/dev/null 2>&1 ||
      die "metadata IPv6 isolation ratchet is incomplete"
  fi
}

verify_broker_native_budget_relay() {
  local age broker_socket heartbeat mount now socket_value state
  systemctl is-active --quiet gole-metadata-firewall.service ||
    die "metadata firewall service is not active"
  systemctl is-active --quiet gole-cloud-broker.service ||
    die "root cloud broker is not active"
  broker_socket="/run/gole-cloud-broker/broker.sock"
  [ -S "$broker_socket" ] && [ ! -L "$broker_socket" ] &&
    [ "$(stat -c '%U:%G:%a' "$broker_socket")" = "root:golecloud:660" ] ||
    die "root cloud broker socket is invalid"
  heartbeat="/run/gole-cloud-broker/policy-heartbeat"
  [ -f "$heartbeat" ] && [ ! -L "$heartbeat" ] &&
    [ "$(stat -c '%U:%G:%a' "$heartbeat")" = "root:golecloud:600" ] ||
    die "root cloud broker policy heartbeat is invalid"
  now="$(date +%s)"
  age=$((now - $(stat -c %Y "$heartbeat")))
  [ "$age" -ge 0 ] && [ "$age" -le 30 ] ||
    die "root cloud broker policy heartbeat is stale"
  state="$(docker inspect --format '{{.State.Status}}:{{if .State.Health}}{{.State.Health.Status}}{{else}}missing{{end}}' \
    gole-budget-relay 2>/dev/null || true)"
  [ "$state" = "running:healthy" ] || die "broker-native budget relay is not healthy"
  [ "$(docker inspect --format '{{.Config.Image}}' gole-budget-relay 2>/dev/null || true)" = \
    "gole/budget-relay:local" ] || die "budget relay image identity is invalid"
  socket_value="$(container_environment_value gole-budget-relay GOLE_CLOUD_BROKER_SOCKET)"
  [ "$socket_value" = "$broker_socket" ] || die "budget relay does not use the root cloud broker"
  mount="$(docker inspect --format \
    '{{range .Mounts}}{{if eq .Destination "/run/gole-cloud-broker"}}{{.Type}}|{{.Source}}|{{.Destination}}|{{.RW}}{{println}}{{end}}{{end}}' \
    gole-budget-relay 2>/dev/null || true)"
  [ "$mount" = "bind|/run/gole-cloud-broker|/run/gole-cloud-broker|false" ] ||
    die "budget relay broker directory mount is invalid"
}

verify_metadata_denial_runtime() {
  if runuser -u "$DEPLOY_USER" -- curl -fsS --max-time 2 \
    -H 'Metadata-Flavor: Google' \
    http://169.254.169.254/computeMetadata/v1/instance/service-accounts/default/token \
    >/dev/null 2>&1; then
    die "deploy runner can reach the metadata token endpoint"
  fi
  if docker exec gole-budget-relay python -c \
    'import urllib.request; urllib.request.urlopen(urllib.request.Request("http://169.254.169.254/computeMetadata/v1/instance/service-accounts/default/token", headers={"Metadata-Flavor":"Google"}), timeout=2).read()' \
    >/dev/null 2>&1; then
    die "budget relay container can reach the metadata token endpoint"
  fi
}

verify_broker_policy_heartbeat_advanced() {
  local after before broker_after broker_before attempt
  broker_before="$(stat -c %y /run/gole-cloud-broker/policy-heartbeat 2>/dev/null || true)"
  [ -n "$broker_before" ] || die "root broker policy heartbeat is missing"
  before="$(docker exec gole-budget-relay python -c \
    'import os; print(os.stat("/tmp/gole-cost-guard-heartbeat").st_mtime_ns)' \
    2>/dev/null || true)"
  [[ "$before" =~ ^[0-9]+$ ]] || die "budget relay heartbeat is missing"
  for attempt in $(seq 1 35); do
    sleep 1
    after="$(docker exec gole-budget-relay python -c \
      'import os; print(os.stat("/tmp/gole-cost-guard-heartbeat").st_mtime_ns)' \
      2>/dev/null || true)"
    broker_after="$(stat -c %y /run/gole-cloud-broker/policy-heartbeat 2>/dev/null || true)"
    if [[ "$after" =~ ^[0-9]+$ ]] && [ "$after" -gt "$before" ] &&
      [ -n "$broker_after" ] && [ "$broker_after" != "$broker_before" ]; then
      return 0
    fi
  done
  die "budget relay heartbeat did not advance after metadata isolation"
}

prepare_metadata_migration_ratchet() {
  local expected_sha="$1"
  read_metadata_migration_marker || return 1
  [ "$METADATA_MIGRATION_STATE" = pending ] ||
    die "metadata migration ratchet is already in progress"
  [ "$METADATA_MIGRATION_LEGACY_SHA" != "$expected_sha" ] ||
    die "legacy deployment cannot finalize metadata isolation"
  [ "$(read_deployed_sha)" = "$expected_sha" ] ||
    die "metadata ratchet SHA is not the current LKG"
  verify_metadata_pending_policy
  verify_broker_native_budget_relay
  verify_deployment_runtime_components "$expected_sha"
}

arm_metadata_migration_ratchet() {
  local expected_sha="$1" request_id="$2"
  require_deployment_transaction "$request_id" runtime-verified
  [ "$DEPLOY_TX_TARGET" = all ] && [ "$DEPLOY_TX_NEW_SHA" = "$expected_sha" ] ||
    die "metadata ratchet deployment identity changed before arming"
  read_metadata_migration_marker || die "metadata migration marker disappeared before arming"
  [ "$METADATA_MIGRATION_STATE" = pending ] ||
    die "metadata migration is not pending before arming"
  METADATA_RATCHET_STARTED=1
  # The marker is the reboot-time fail-closed journal and must reach disk before
  # the deployment transaction advances. A crash from this point makes the
  # firewall full and prevents the runner from starting.
  write_metadata_migration_marker "$METADATA_MIGRATION_LEGACY_SHA" ratcheting
  advance_deployment_transaction "$request_id" runtime-verified metadata-ratchet-armed
}

complete_metadata_migration_ratchet() {
  local expected_sha="$1" request_id="$2"
  require_deployment_transaction "$request_id" metadata-ratchet-armed
  [ "$DEPLOY_TX_TARGET" = all ] && [ "$DEPLOY_TX_NEW_SHA" = "$expected_sha" ] ||
    die "metadata ratchet deployment identity changed"
  if read_metadata_migration_marker; then
    [ "$METADATA_MIGRATION_LEGACY_SHA" != "$expected_sha" ] ||
      die "legacy deployment cannot complete metadata isolation"
    if [ "$METADATA_MIGRATION_STATE" = pending ]; then
      verify_metadata_pending_policy
      verify_broker_native_budget_relay
      verify_deployment_runtime_components "$expected_sha"
      METADATA_RATCHET_STARTED=1
      write_metadata_migration_marker "$METADATA_MIGRATION_LEGACY_SHA" ratcheting
    else
      METADATA_RATCHET_STARTED=1
    fi
  else
    # Absence is reachable only after the one-way rule and marker removal. The
    # durable deployment state still requires full verification before cleanup.
    METADATA_RATCHET_STARTED=1
  fi
  "$METADATA_FIREWALL" --full
  verify_metadata_full_policy
  verify_metadata_denial_runtime
  verify_broker_native_budget_relay
  verify_broker_policy_heartbeat_advanced
  verify_deployment_runtime_components "$expected_sha"
  # Keep the fail-closed marker until the durable deployment journal records
  # that every post-ratchet check passed. A crash before that write leaves the
  # runner gated and recovery can only continue forward.
  advance_deployment_transaction "$request_id" metadata-ratchet-armed metadata-ratchet-verified
  if [ -e "$METADATA_MIGRATION_MARKER" ] || [ -L "$METADATA_MIGRATION_MARKER" ]; then
    rm -f -- "$METADATA_MIGRATION_MARKER"
    sync -f /etc/gole
  fi
  METADATA_RATCHET_STARTED=0
}

finalize_deployment_transaction() {
  local request_id="$1"
  require_deployment_transaction "$request_id" runtime-verified
  if [ "$DEPLOY_TX_TARGET" = all ] && { [ -e "$NGINX_TRANSACTION_FILE" ] || [ -L "$NGINX_TRANSACTION_FILE" ]; }; then
    die "Nginx transaction must be finalized first"
  fi
  if [ "$DEPLOY_TX_TARGET" = all ] && prepare_metadata_migration_ratchet "$DEPLOY_TX_NEW_SHA"; then
    arm_metadata_migration_ratchet "$DEPLOY_TX_NEW_SHA" "$request_id"
    complete_metadata_migration_ratchet "$DEPLOY_TX_NEW_SHA" "$request_id"
  fi
  require_deployment_transaction "$request_id" runtime-verified,metadata-ratchet-verified
  [ "$(read_deployed_sha)" = "$DEPLOY_TX_NEW_SHA" ] ||
    die "completed deployment marker changed before cleanup"
  [ ! -e "$METADATA_MIGRATION_MARKER" ] && [ ! -L "$METADATA_MIGRATION_MARKER" ] ||
    die "metadata migration marker remains before cleanup"
  verify_metadata_full_policy
  verify_metadata_denial_runtime
  verify_broker_native_budget_relay
  verify_broker_policy_heartbeat_advanced
  verify_deployment_runtime_components "$DEPLOY_TX_NEW_SHA"
  require_deployment_image_snapshot "$DEPLOY_TX_TARGET" "$request_id"
  advance_deployment_transaction "$request_id" \
    runtime-verified,metadata-ratchet-verified cleanup-pending
  cleanup_deployment_images "$DEPLOY_TX_TARGET" "$request_id"
  remove_host_state "$DEPLOYMENT_TRANSACTION_FILE"
}

finalize_partial_deployment_transaction() {
  local request_id="$1"
  require_deployment_transaction "$request_id" verified
  [ "$DEPLOY_TX_TARGET" != all ] || die "full deployment requires marker verification"
  [ "$(read_deployed_sha)" = "$DEPLOY_TX_NEW_SHA" ] ||
    die "partial deployment may only rebuild the current LKG SHA"
  [ ! -e "$METADATA_MIGRATION_MARKER" ] && [ ! -L "$METADATA_MIGRATION_MARKER" ] ||
    die "partial deployment cannot finalize during metadata migration"
  verify_metadata_full_policy
  verify_metadata_denial_runtime
  verify_broker_native_budget_relay
  verify_broker_policy_heartbeat_advanced
  verify_deployment_runtime_components "$DEPLOY_TX_NEW_SHA"
  require_deployment_image_snapshot "$DEPLOY_TX_TARGET" "$request_id"
  advance_deployment_transaction "$request_id" verified cleanup-pending
  cleanup_deployment_images "$DEPLOY_TX_TARGET" "$request_id"
  remove_host_state "$DEPLOYMENT_TRANSACTION_FILE"
}

recover_completed_deployment_cleanup() {
  [ "$DEPLOY_TX_STATE" = cleanup-pending ] || die "deployment cleanup recovery state changed"
  [ "$(read_deployed_sha)" = "$DEPLOY_TX_NEW_SHA" ] ||
    die "cleanup-pending deployment is not the current LKG"
  [ ! -e "$METADATA_MIGRATION_MARKER" ] && [ ! -L "$METADATA_MIGRATION_MARKER" ] ||
    die "cleanup-pending deployment still has a metadata migration marker"
  verify_metadata_full_policy
  verify_metadata_denial_runtime
  verify_broker_native_budget_relay
  verify_broker_policy_heartbeat_advanced
  verify_deployment_runtime_components "$DEPLOY_TX_NEW_SHA"
  cleanup_deployment_images "$DEPLOY_TX_TARGET" "$DEPLOY_TX_REQUEST_ID"
  remove_host_state "$DEPLOYMENT_TRANSACTION_FILE"
}

verify_runtime_verified_commit_window() {
  [ "$DEPLOY_TX_STATE" = runtime-verified ] && [ "$DEPLOY_TX_TARGET" = all ] ||
    die "deployment commit-window state is invalid"
  [ ! -e "$NGINX_TRANSACTION_FILE" ] && [ ! -L "$NGINX_TRANSACTION_FILE" ] ||
    die "deployment commit-window still has an Nginx transaction"
  [ ! -e "$METADATA_MIGRATION_MARKER" ] && [ ! -L "$METADATA_MIGRATION_MARKER" ] ||
    die "deployment commit-window still has a metadata migration marker"
  [ "$(read_deployed_sha)" = "$DEPLOY_TX_NEW_SHA" ] ||
    die "deployment commit-window LKG marker does not match"
  verify_metadata_full_policy
  verify_metadata_denial_runtime
  verify_broker_native_budget_relay
  verify_broker_policy_heartbeat_advanced
  verify_deployment_runtime_components "$DEPLOY_TX_NEW_SHA"
}

recover_restored_deployment_cleanup() {
  [ "$DEPLOY_TX_STATE" = rollback-restored ] || die "rollback cleanup recovery state changed"
  verify_pre_snapshot_lkg_runtime "$DEPLOY_TX_PREVIOUS_SHA"
  cleanup_deployment_images "$DEPLOY_TX_TARGET" "$DEPLOY_TX_REQUEST_ID"
  remove_host_state "$DEPLOYMENT_TRANSACTION_FILE"
}

recover_initial_deployment_commit_window() {
  [ "$DEPLOY_TX_TARGET" = all ] && [ "$DEPLOY_TX_PREVIOUS_SHA" = 0 ] ||
    die "initial deployment commit-window identity changed"
  [[ "$DEPLOY_TX_STATE" =~ ^(marker-recorded|initial-http-verified|runtime-verified)$ ]] ||
    die "initial deployment commit-window state is invalid"
  [ ! -e "$INITIAL_DEPLOY_FILE" ] && [ ! -L "$INITIAL_DEPLOY_FILE" ] ||
    die "initial deployment authorization was not retired"
  [ "$(read_deployed_sha)" = "$DEPLOY_TX_NEW_SHA" ] ||
    die "initial deployment marker does not match its transaction"
  [ ! -e "$METADATA_MIGRATION_MARKER" ] && [ ! -L "$METADATA_MIGRATION_MARKER" ] ||
    die "initial deployment commit window has unexpected metadata migration state"
  case "$DEPLOY_TX_STATE" in
    marker-recorded)
      require_matching_nginx_transaction "$DEPLOY_TX_REQUEST_ID"
      [ "$NGINX_TXN_STATE" = committed ] &&
        [ "$NGINX_TXN_DEPLOY_SHA" = "$DEPLOY_TX_NEW_SHA" ] ||
        die "initial deployment committed Nginx journal does not match"
      verify_initial_http_commit "$DEPLOY_TX_NEW_SHA" "$DEPLOY_TX_REQUEST_ID"
      finalize_nginx_transaction "$DEPLOY_TX_REQUEST_ID"
      echo INITIAL_TLS_REQUIRED
      ;;
    initial-http-verified)
      [ ! -e "$NGINX_TRANSACTION_FILE" ] && [ ! -L "$NGINX_TRANSACTION_FILE" ] ||
        die "initial HTTP commit still has a deployment Nginx journal"
      verify_initial_deployment_runtime_components "$DEPLOY_TX_NEW_SHA"
      echo INITIAL_TLS_REQUIRED
      ;;
    runtime-verified)
      verify_deployment_runtime_components "$DEPLOY_TX_NEW_SHA"
      if [ -e "$NGINX_TRANSACTION_FILE" ] || [ -L "$NGINX_TRANSACTION_FILE" ]; then
        require_matching_nginx_transaction "$DEPLOY_TX_REQUEST_ID"
        [ "$NGINX_TXN_STATE" = committed ] &&
          [ "$NGINX_TXN_DEPLOY_SHA" = "$DEPLOY_TX_NEW_SHA" ] ||
          die "initial deployment committed Nginx journal does not match"
        finalize_nginx_transaction "$DEPLOY_TX_REQUEST_ID"
      fi
      finalize_deployment_transaction "$DEPLOY_TX_REQUEST_ID"
      echo RECOVERED
      ;;
  esac
}

initial_reset_container_name() {
  case "$1" in
    mongo) printf 'gole-mongo\n' ;;
    mongo-init) printf 'gole-mongo-init-1\n' ;;
    redis) printf 'gole-redis\n' ;;
    minio) printf 'gole-minio\n' ;;
    minio-init) printf 'gole-minio-init-1\n' ;;
    support-agent) printf 'gole-support-agent\n' ;;
    backend) printf 'gole-backend\n' ;;
    frontend) printf 'gole-frontend\n' ;;
    nginx) printf 'gole-nginx\n' ;;
    budget-relay) printf 'gole-budget-relay\n' ;;
    *) die "unexpected Compose service blocks initial reset" ;;
  esac
}

validate_initial_reset_project_resources() {
  local container_id labels name network_id network_identity service
  local -A seen_containers=() seen_networks=()

  # Compose down acts on a project label, so prove that every object within
  # that project is one of the reviewed production objects before mutation.
  while IFS= read -r container_id; do
    [ -n "$container_id" ] || continue
    labels="$(docker inspect --format \
      '{{index .Config.Labels "com.docker.compose.project"}}|{{index .Config.Labels "com.docker.compose.service"}}|{{.Name}}' \
      "$container_id")" || die "could not inspect initial deployment container"
    IFS='|' read -r project service name <<<"$labels"
    [ "$project" = gole ] || die "initial reset container project changed"
    name="${name#/}"
    [ "$name" = "$(initial_reset_container_name "$service")" ] ||
      die "unexpected Compose container blocks initial reset: $service"
    [ -z "${seen_containers[$service]+present}" ] ||
      die "duplicate Compose service blocks initial reset: $service"
    seen_containers["$service"]=1
  done < <(docker ps -a --filter label=com.docker.compose.project=gole --format '{{.ID}}')

  for service in \
    mongo mongo-init redis minio minio-init support-agent backend frontend nginx budget-relay; do
    name="$(initial_reset_container_name "$service")"
    if docker inspect "$name" >/dev/null 2>&1; then
      labels="$(docker inspect --format \
        '{{index .Config.Labels "com.docker.compose.project"}}|{{index .Config.Labels "com.docker.compose.service"}}' \
        "$name")" || die "could not inspect named production container"
      [ "$labels" = "gole|$service" ] ||
        die "named production container ownership is invalid: $name"
    fi
  done

  while IFS= read -r network_id; do
    [ -n "$network_id" ] || continue
    network_identity="$(docker network inspect --format \
      '{{.Name}}|{{index .Labels "com.docker.compose.project"}}|{{index .Labels "com.docker.compose.network"}}' \
      "$network_id")" || die "could not inspect initial deployment network"
    IFS='|' read -r name project service <<<"$network_identity"
    [ "$project" = gole ] || die "initial reset network project changed"
    case "$name|$service" in
      gole_edge\|edge | gole_data\|data | gole_agent\|agent) ;;
      *) die "unexpected Compose network blocks initial reset: $name" ;;
    esac
    [ -z "${seen_networks[$name]+present}" ] ||
      die "duplicate Compose network blocks initial reset: $name"
    seen_networks["$name"]=1
  done < <(docker network ls --filter label=com.docker.compose.project=gole --format '{{.ID}}')

  for name in gole_edge gole_data gole_agent; do
    if docker network inspect "$name" >/dev/null 2>&1; then
      network_identity="$(docker network inspect --format \
        '{{index .Labels "com.docker.compose.project"}}|{{index .Labels "com.docker.compose.network"}}' \
        "$name")" || die "could not verify named production network"
      case "$name|$network_identity" in
        gole_edge\|gole\|edge | gole_data\|gole\|data | gole_agent\|gole\|agent) ;;
        *) die "named production network ownership is invalid: $name" ;;
      esac
    fi
  done
}

initial_reset_volume_state() {
  local identity volume
  for volume in \
    gole_mongo-data gole_redis-data gole_minio-data gole_certbot-webroot \
    gole_letsencrypt gole_budget-relay-state; do
    if identity="$(docker volume inspect --format '{{.Name}}|{{.Driver}}' "$volume" 2>/dev/null)"; then
      [ "$identity" = "$volume|local" ] ||
        die "production volume identity is invalid: $volume"
      printf '%s=present\n' "$volume"
    else
      printf '%s=absent\n' "$volume"
    fi
  done
}

verify_initial_reset_project_absent() {
  local name service
  [ -z "$(docker ps -a --filter label=com.docker.compose.project=gole --format '{{.ID}}')" ] ||
    die "Compose project containers remain after initial reset"
  [ -z "$(docker network ls --filter label=com.docker.compose.project=gole --format '{{.ID}}')" ] ||
    die "Compose project networks remain after initial reset"
  for service in \
    mongo mongo-init redis minio minio-init support-agent backend frontend nginx budget-relay; do
    name="$(initial_reset_container_name "$service")"
    ! docker inspect "$name" >/dev/null 2>&1 ||
      die "named production container remains after initial reset: $name"
  done
  for name in gole_edge gole_data gole_agent; do
    ! docker network inspect "$name" >/dev/null 2>&1 ||
      die "named production network remains after initial reset: $name"
  done
}

remove_initial_reset_local_images() {
  local image
  for image in \
    gole/support-agent:local gole/backend:local gole/frontend:local gole/budget-relay:local; do
    docker image rm "$image" >/dev/null 2>&1 || true
    ! docker image inspect "$image" >/dev/null 2>&1 ||
      die "candidate local image remains after initial reset: $image"
  done
}

reset_initial_deployment_failure() {
  local request_id state_before volume_state
  [ "${SUDO_USER:-root}" = root ] || die "initial deployment reset is root-only"
  [ -f /run/lock/gole-production-rollout.lock ] &&
    [ ! -L /run/lock/gole-production-rollout.lock ] &&
    [ "$(stat -c '%U:%G:%a' /run/lock/gole-production-rollout.lock)" = "root:${DEPLOY_GROUP}:660" ] ||
    die "production rollout lock metadata is invalid"
  exec 8>>/run/lock/gole-production-rollout.lock
  flock -n 8 || die "another production rollout is active"

  read_deployment_transaction
  [ "$DEPLOY_TX_TARGET" = all ] && [ "$DEPLOY_TX_PREVIOUS_SHA" = 0 ] ||
    die "only a failed initial full deployment can be reset"
  case "$DEPLOY_TX_STATE" in
    mutation-armed | mutated | refreshed | budget-updated | verified | initial-reset-armed | rollback-restored) ;;
    *) die "initial deployment is not in an explicitly resettable state" ;;
  esac
  validate_initial_deployment
  [ ! -e "$METADATA_MIGRATION_MARKER" ] && [ ! -L "$METADATA_MIGRATION_MARKER" ] ||
    die "metadata migration state blocks initial reset"
  require_deployment_image_snapshot all "$DEPLOY_TX_REQUEST_ID"
  [ "$SNAPSHOT_MODE" = initial ] && [ "$SNAPSHOT_IMAGE_COUNT" -eq 0 ] ||
    die "initial deployment image journal is invalid"
  create_release "$DEPLOY_TX_NEW_SHA" historical-main
  select_release "$DEPLOY_TX_NEW_SHA"
  validate_current_production_model
  request_id="$DEPLOY_TX_REQUEST_ID"
  state_before="$DEPLOY_TX_STATE"

  if [ "$state_before" != initial-reset-armed ] && [ "$state_before" != rollback-restored ]; then
    require_matching_nginx_transaction "$request_id"
    [ "$NGINX_TXN_DEPLOY_SHA" = "$DEPLOY_TX_NEW_SHA" ] ||
      die "initial reset Nginx deployment SHA changed"
    write_deployment_transaction initial-reset-armed "$DEPLOY_TX_TARGET" \
      "$request_id" "$DEPLOY_TX_NEW_SHA" "$DEPLOY_TX_PREVIOUS_SHA"
    state_before=initial-reset-armed
  fi

  INITIAL_RESET_STARTED=1
  if [ "$state_before" = initial-reset-armed ]; then
    if [ -e "$NGINX_TRANSACTION_FILE" ] || [ -L "$NGINX_TRANSACTION_FILE" ]; then
      require_matching_nginx_transaction "$request_id"
      [ "$NGINX_TXN_DEPLOY_SHA" = "$DEPLOY_TX_NEW_SHA" ] ||
        die "initial reset Nginx deployment SHA changed"
      if [ "$NGINX_TXN_STATE" != rollback-restored ]; then
        restore_nginx_transaction
      fi
      finish_nginx_recovery "$request_id"
    fi

    validate_initial_reset_project_resources
    volume_state="$(initial_reset_volume_state)"
    # Deliberately omit both -v and --remove-orphans. Unknown project objects
    # were rejected above, and named persistent volumes must survive exactly.
    production_compose "$APP_ENV_FILE" down --timeout 30
    verify_initial_reset_project_absent
    [ "$(initial_reset_volume_state)" = "$volume_state" ] ||
      die "production volume presence changed during initial reset"
    remove_initial_reset_local_images
    write_deployment_transaction rollback-restored "$DEPLOY_TX_TARGET" \
      "$request_id" "$DEPLOY_TX_NEW_SHA" "$DEPLOY_TX_PREVIOUS_SHA"
  else
    [ ! -e "$NGINX_TRANSACTION_FILE" ] && [ ! -L "$NGINX_TRANSACTION_FILE" ] ||
      die "Nginx transaction remains after initial reset"
    verify_initial_reset_project_absent
    remove_initial_reset_local_images
  fi

  read_deployment_transaction
  [ "$DEPLOY_TX_STATE" = rollback-restored ] ||
    die "initial deployment reset did not reach terminal cleanup"
  cleanup_deployment_images all "$request_id"
  remove_host_state "$DEPLOYMENT_TRANSACTION_FILE"
  INITIAL_RESET_STARTED=0
}

rollback_unmutated_deployment_transaction() {
  local model="" request_id="$1" validation_mode=strict
  require_deployment_transaction "$request_id" snapshotted,built,nginx-installed
  require_deployment_image_snapshot "$DEPLOY_TX_TARGET" "$request_id"

  if [ "$DEPLOY_TX_PREVIOUS_SHA" != 0 ]; then
    select_release "$DEPLOY_TX_PREVIOUS_SHA"
    validate_current_environment
    if [ "$SNAPSHOT_MODE" = strict ]; then
      validation_mode=strict-lkg
    else
      validation_mode="$SNAPSHOT_MODE"
    fi
    validate_production_compose "$APP_ENV_FILE" "$validation_mode"
    model="$(mktemp)"
    register_temp_file "$model"
    chmod 0600 "$model"
    render_deployment_compose_model "$model"
    # A build may have moved mutable local tags, but no running service may be
    # recreated until mutation-armed is durable. Restore only those tags here.
    restore_canonical_compose_image_refs "$model" "$DEPLOY_TX_TARGET" "$request_id"
  fi

  if [ "$DEPLOY_TX_STATE" = nginx-installed ] &&
    [ ! -e "$NGINX_TRANSACTION_FILE" ] && [ ! -L "$NGINX_TRANSACTION_FILE" ]; then
    die "unmutated deployment lost its installed Nginx transaction"
  fi
  if [ -e "$NGINX_TRANSACTION_FILE" ] || [ -L "$NGINX_TRANSACTION_FILE" ]; then
    require_matching_nginx_transaction "$request_id"
    case "$NGINX_TXN_STATE" in
      prepared | installed) restore_nginx_transaction ;;
      rollback-restored) ;;
      *) die "unmutated deployment has an invalid Nginx transaction state" ;;
    esac
  fi

  verify_restored_deployment_images "$DEPLOY_TX_TARGET" "$request_id"
  verify_pre_snapshot_lkg_runtime "$DEPLOY_TX_PREVIOUS_SHA"
  if [ "$DEPLOY_TX_PREVIOUS_SHA" != 0 ]; then
    write_deployed_sha_exact "$DEPLOY_TX_PREVIOUS_SHA"
  fi
  if [ -e "$NGINX_TRANSACTION_FILE" ] || [ -L "$NGINX_TRANSACTION_FILE" ]; then
    finish_nginx_recovery "$request_id"
  fi
  if [ -n "$model" ]; then
    rm -f -- "$model"
    forget_temp_file "$model"
  fi
  advance_deployment_transaction "$request_id" \
    snapshotted,built,nginx-installed rollback-restored
  cleanup_deployment_images "$DEPLOY_TX_TARGET" "$request_id"
  remove_host_state "$DEPLOYMENT_TRANSACTION_FILE"
}

recover_deployment_transaction() {
  if [ ! -e "$DEPLOYMENT_TRANSACTION_FILE" ] && [ ! -L "$DEPLOYMENT_TRANSACTION_FILE" ]; then
    if [ -e "$NGINX_TRANSACTION_FILE" ] || [ -L "$NGINX_TRANSACTION_FILE" ]; then
      die "orphaned Nginx transaction requires manual root recovery"
    fi
    if read_metadata_migration_marker && [ "$METADATA_MIGRATION_STATE" = ratcheting ]; then
      systemctl poweroff --no-block || true
      die "orphaned metadata ratchet requires manual recovery; VM powered off"
    fi
    echo NONE
    return
  fi
  read_deployment_transaction
  if [ "$DEPLOY_TX_STATE" = cleanup-pending ]; then
    recover_completed_deployment_cleanup
    echo RECOVERED
    return
  fi
  if [ "$DEPLOY_TX_STATE" = rollback-restored ]; then
    recover_restored_deployment_cleanup
    echo RECOVERED
    return
  fi
  if [ "$DEPLOY_TX_STATE" = prepared ]; then
    recover_pre_snapshot_deployment
    echo RECOVERED
    return
  fi
  if [ "$DEPLOY_TX_PREVIOUS_SHA" = 0 ] && [ "$DEPLOY_TX_TARGET" = all ] &&
    [[ "$DEPLOY_TX_STATE" =~ ^(marker-recorded|initial-http-verified|runtime-verified)$ ]]; then
    recover_initial_deployment_commit_window
    return
  fi
  if [ "$DEPLOY_TX_STATE" = runtime-verified ] &&
    read_metadata_migration_marker && [ "$METADATA_MIGRATION_STATE" = ratcheting ]; then
    METADATA_RATCHET_STARTED=1
    advance_deployment_transaction "$DEPLOY_TX_REQUEST_ID" runtime-verified metadata-ratchet-armed
    read_deployment_transaction
  fi
  if [ "$DEPLOY_TX_STATE" = metadata-ratchet-armed ]; then
    complete_metadata_migration_ratchet "$DEPLOY_TX_NEW_SHA" "$DEPLOY_TX_REQUEST_ID"
    read_deployment_transaction
  fi
  if [ "$DEPLOY_TX_STATE" = runtime-verified ] &&
    [ "$DEPLOY_TX_TARGET" = all ] &&
    [ ! -e "$NGINX_TRANSACTION_FILE" ] && [ ! -L "$NGINX_TRANSACTION_FILE" ] &&
    [ ! -e "$METADATA_MIGRATION_MARKER" ] && [ ! -L "$METADATA_MIGRATION_MARKER" ] &&
    [ "$(read_deployed_sha 2>/dev/null || true)" = "$DEPLOY_TX_NEW_SHA" ] &&
    (verify_runtime_verified_commit_window); then
    require_deployment_image_snapshot "$DEPLOY_TX_TARGET" "$DEPLOY_TX_REQUEST_ID"
    advance_deployment_transaction "$DEPLOY_TX_REQUEST_ID" runtime-verified cleanup-pending
    cleanup_deployment_images "$DEPLOY_TX_TARGET" "$DEPLOY_TX_REQUEST_ID"
    remove_host_state "$DEPLOYMENT_TRANSACTION_FILE"
    echo RECOVERED
    return
  fi
  if [ "$DEPLOY_TX_STATE" = metadata-ratchet-verified ]; then
    METADATA_RATCHET_STARTED=1
    verify_metadata_full_policy
    verify_metadata_denial_runtime
    verify_broker_native_budget_relay
    verify_broker_policy_heartbeat_advanced
    verify_deployment_runtime_components "$DEPLOY_TX_NEW_SHA"
    if [ -e "$METADATA_MIGRATION_MARKER" ] || [ -L "$METADATA_MIGRATION_MARKER" ]; then
      if ! (read_metadata_migration_marker); then
        systemctl poweroff --no-block || true
        die "invalid metadata ratchet marker during recovery; VM powered off"
      fi
      read_metadata_migration_marker
      [ "$METADATA_MIGRATION_STATE" = ratcheting ] &&
        [ "$METADATA_MIGRATION_LEGACY_SHA" != "$DEPLOY_TX_NEW_SHA" ] || {
          systemctl poweroff --no-block || true
          die "metadata ratchet marker does not match recovery; VM powered off"
        }
      rm -f -- "$METADATA_MIGRATION_MARKER"
      sync -f /etc/gole
    fi
    require_deployment_image_snapshot "$DEPLOY_TX_TARGET" "$DEPLOY_TX_REQUEST_ID"
    advance_deployment_transaction "$DEPLOY_TX_REQUEST_ID" \
      metadata-ratchet-verified cleanup-pending
    cleanup_deployment_images "$DEPLOY_TX_TARGET" "$DEPLOY_TX_REQUEST_ID"
    remove_host_state "$DEPLOYMENT_TRANSACTION_FILE"
    METADATA_RATCHET_STARTED=0
  else
    rollback_deployment_transaction "$DEPLOY_TX_REQUEST_ID"
  fi
  echo RECOVERED
}

rollback_deployment_transaction() {
  local available marker model override request_id="$1" service validation_mode=strict-lkg
  local -a app_services=()
  require_deployment_transaction "$request_id" prepared,snapshotted,built,nginx-installed,mutation-armed,mutated,refreshed,budget-updated,verified,marker-recorded,runtime-verified
  # Once the durable marker says ratcheting, rollback is forbidden even if a
  # crash happened before the deployment transaction advanced. Reopening the
  # legacy metadata-dependent relay would break the one-way security boundary.
  if [ -e "$METADATA_MIGRATION_MARKER" ] || [ -L "$METADATA_MIGRATION_MARKER" ]; then
    if ! (read_metadata_migration_marker); then
      "$METADATA_FIREWALL" --full >/dev/null 2>&1 || true
      systemctl poweroff --no-block || true
      die "invalid metadata migration marker blocks rollback; VM powered off"
    fi
    read_metadata_migration_marker
    if [ "$METADATA_MIGRATION_STATE" = ratcheting ]; then
      METADATA_RATCHET_STARTED=1
      "$METADATA_FIREWALL" --full >/dev/null 2>&1 || true
      systemctl poweroff --no-block || true
      die "metadata isolation ratchet blocks rollback; VM powered off"
    fi
    validation_mode=legacy-adoption
  fi
  if [ "$DEPLOY_TX_STATE" = prepared ]; then
    recover_pre_snapshot_deployment
    return
  fi
  case "$DEPLOY_TX_STATE" in
    snapshotted | built | nginx-installed)
      rollback_unmutated_deployment_transaction "$request_id"
      return
      ;;
  esac
  if [ "$DEPLOY_TX_PREVIOUS_SHA" = 0 ]; then
    # A first deployment has no known-good application or image. Leaving an
    # unknown partial service online is less safe than stopping the VM. Return
    # failure so deploy.sh cannot announce a nonexistent LKG rollback; the
    # command wrapper performs the single fail-closed poweroff request.
    die "initial deployment has no LKG; transaction retained for explicit root reset"
  fi
  if [ -e "$NGINX_TRANSACTION_FILE" ] || [ -L "$NGINX_TRANSACTION_FILE" ]; then
    require_matching_nginx_transaction "$request_id"
    if [ "$NGINX_TXN_STATE" != rollback-restored ]; then
      restore_nginx_transaction
    fi
  fi
  require_deployment_image_snapshot "$DEPLOY_TX_TARGET" "$request_id"
  if [ "$SNAPSHOT_MODE" = strict ] &&
    { [ -e "$MINIO_RECOVERY_MARKER" ] || [ -L "$MINIO_RECOVERY_MARKER" ]; }; then
    (quiesce_public_runtime && require_public_runtime_quiesced) || true
    systemctl poweroff --no-block || true
    die "MinIO unfreeze recovery is unresolved; rollback cannot reopen writes"
  fi
  if [ "$SNAPSHOT_MODE" = strict ]; then
    marker="$(data_upgrade_marker "$request_id")"
    if [ -e "$marker" ] || [ -L "$marker" ]; then
      if ! (quiesce_public_runtime && require_public_runtime_quiesced); then
        systemctl poweroff --no-block || true
        die "data-plane rollback could not close writes; VM powered off"
      fi
      require_data_upgrade_marker "$request_id"
      if [ "$DATA_UPGRADE_STATE" = mutation-armed ]; then
        systemctl poweroff --no-block || true
        die "mutated data plane requires explicit logical restore; backup retained and VM powered off"
      fi
    fi
  fi
  restore_deployment_images "$DEPLOY_TX_TARGET" "$request_id"
  select_release "$DEPLOY_TX_PREVIOUS_SHA"
  validate_current_environment
  validate_production_compose "$APP_ENV_FILE" "$validation_mode"
  model="$(mktemp)"
  override="$(mktemp)"
  register_temp_file "$model"
  register_temp_file "$override"
  chmod 0600 "$model" "$override"
  render_deployment_compose_model "$model"
  restore_canonical_compose_image_refs "$model" "$DEPLOY_TX_TARGET" "$request_id"
  write_deployment_image_override "$DEPLOY_TX_TARGET" "$request_id" "$override"
  available="$(production_compose "$APP_ENV_FILE" config --services)"
  for service in backend frontend budget-relay nginx; do
    grep -qx "$service" <<<"$available" || die "rollback release misses a required service"
  done
  if [ "$DEPLOY_TX_TARGET" = all ] &&
    { [ "$SNAPSHOT_MODE" = legacy-adoption ] ||
      { [ "$SNAPSHOT_MODE" = strict ] && data_upgrade_required "$request_id"; }; }; then
    quiesce_public_runtime
    for service in mongo mongo-init redis minio minio-init; do
      grep -qx "$service" <<<"$available" || die "rollback release misses a data service"
    done
    if [ "$SNAPSHOT_MODE" = strict ]; then
      require_data_upgrade_marker "$request_id"
      if { [ "${DATA_UPGRADE_CHANGES[mongo]}" = true ] &&
        ! run_compose_services_exactly rollback "$override" mongo; } ||
        { [ "${DATA_UPGRADE_CHANGES[redis]}" = true ] &&
          ! run_compose_services_exactly rollback "$override" redis; } ||
        { [ "${DATA_UPGRADE_CHANGES[minio]}" = true ] &&
          ! run_compose_services_exactly rollback "$override" minio; } ||
        { { [ "${DATA_UPGRADE_CHANGES[mongo]}" = true ] ||
            [ "${DATA_UPGRADE_CHANGES[mongo-init]}" = true ]; } &&
          ! run_compose_initializers_exactly rollback "$override" mongo-init; } ||
        { { [ "${DATA_UPGRADE_CHANGES[minio]}" = true ] ||
            [ "${DATA_UPGRADE_CHANGES[minio-init]}" = true ]; } &&
          ! run_compose_initializers_exactly rollback "$override" minio-init; }; then
        systemctl poweroff --no-block
        die "strict data-plane rollback failed; logical backup retained; VM powered off"
      fi
    elif ! run_compose_services_exactly rollback "$override" mongo redis minio ||
      ! run_compose_initializers_exactly rollback "$override" mongo-init minio-init; then
      systemctl poweroff --no-block
      die "rollback data-plane Compose failed; VM powered off"
    fi
  fi
  case "$DEPLOY_TX_TARGET" in
    all)
      grep -qx support-agent <<<"$available" && app_services+=(support-agent)
      app_services+=(backend frontend budget-relay nginx)
      ;;
    backend)
      grep -qx support-agent <<<"$available" && app_services+=(support-agent)
      app_services+=(backend nginx)
      ;;
    frontend) app_services+=(frontend nginx) ;;
  esac
  if [ "${SNAPSHOT_IMAGE_IDS[support-agent]:-missing}" = absent ] &&
    docker inspect gole-support-agent >/dev/null 2>&1; then
    labels="$(docker inspect --format \
      '{{index .Config.Labels "com.docker.compose.project"}}|{{index .Config.Labels "com.docker.compose.service"}}' \
      gole-support-agent)"
    [ "$labels" = gole\|support-agent ] || die "unexpected support-agent blocks rollback"
    docker rm -f gole-support-agent >/dev/null
  fi
  if ! run_compose_services_exactly rollback "$override" "${app_services[@]}"; then
    systemctl poweroff --no-block
    die "rollback Compose failed; VM powered off"
  fi
  verify_restored_deployment_images "$DEPLOY_TX_TARGET" "$request_id" || {
    systemctl poweroff --no-block
    die "rollback image or health verification failed; VM powered off"
  }
  docker exec gole-nginx nginx -t >/dev/null 2>&1 || {
    systemctl poweroff --no-block
    die "rollback Nginx validation failed; VM powered off"
  }
  curl -fsS --max-time 15 http://127.0.0.1:8080/actuator/health/readiness >/dev/null || {
    systemctl poweroff --no-block
    die "rollback readiness failed; VM powered off"
  }
  write_deployed_sha_exact "$DEPLOY_TX_PREVIOUS_SHA"
  if [ -e "$NGINX_TRANSACTION_FILE" ] || [ -L "$NGINX_TRANSACTION_FILE" ]; then
    finish_nginx_recovery "$request_id"
  fi
  rm -f -- "$model" "$override"
  forget_temp_file "$model"
  forget_temp_file "$override"
  advance_deployment_transaction "$request_id" \
    mutation-armed,mutated,refreshed,budget-updated,verified,marker-recorded,runtime-verified \
    rollback-restored
  cleanup_deployment_images "$DEPLOY_TX_TARGET" "$request_id"
  remove_host_state "$DEPLOYMENT_TRANSACTION_FILE"
}

cost_guard_fail_closed() {
  if systemctl is-active --quiet gole-cost-guard-watchdog.timer; then
    return 0
  fi
  # A legacy relay can stay healthy briefly after the root policy loop dies.
  # Without the watchdog timer, only the complete broker-native path proves
  # that budget enforcement is still running.
  if (verify_broker_native_budget_relay) >/dev/null 2>&1; then
    return 0
  fi
  systemctl poweroff --no-block || true
  die "cost guard protections are unavailable; VM powered off"
}

validate_request_id() {
  [[ "$1" =~ ^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$ ]] ||
    die "invalid request id"
}

create_environment_backup() {
  local version="$1"
  local request_id="$2"
  local backup_file
  validate_current_environment
  install -d -m 0700 -o root -g root "$BACKUP_DIR"
  backup_file="${BACKUP_DIR}/gole.env.$(date -u +%Y%m%dT%H%M%SZ).v${version}.${request_id}"
  install -m 0600 -o root -g root "$APP_ENV_FILE" "$backup_file"
  sync_host_state "$backup_file"
  printf '%s\n' "$backup_file"
}

prune_environment_backups() {
  local backup
  local -a backups=()
  install -d -m 0700 -o root -g root "$BACKUP_DIR"
  while IFS= read -r backup; do
    backups+=("$backup")
  done < <(find "$BACKUP_DIR" -xdev -maxdepth 1 -type f \
    -regextype posix-extended \
    -regex '.*/gole\.env\.[0-9]{8}T[0-9]{6}Z\.v[1-9][0-9]{0,11}\.[0-9a-fA-F-]{36}' \
    -printf '%T@ %p\n' | sort -nr | cut -d' ' -f2-)
  for backup in "${backups[@]:2}"; do
    [ ! -L "$backup" ] && [ "$(stat -c '%U:%G:%a' "$backup")" = root:root:600 ] ||
      return 1
    rm -f -- "$backup" || return 1
  done
}

write_env_version_exact() {
  local version="$1"
  local version_candidate
  if [ "$version" = "0" ]; then
    if [ -e "$ENV_VERSION_FILE" ] || [ -L "$ENV_VERSION_FILE" ]; then
      remove_host_state "$ENV_VERSION_FILE"
    fi
    return
  fi
  [[ "$version" =~ ^[1-9][0-9]{0,11}$ ]] || die "invalid secret version"
  version_candidate="$(mktemp)"
  printf '%s\n' "$version" > "$version_candidate"
  atomic_install "$version_candidate" "$ENV_VERSION_FILE" 0644 root
  sync_host_state "$ENV_VERSION_FILE"
  rm -f -- "$version_candidate"
}

write_transaction() {
  local state="$1"
  local previous_version="$2"
  local requested_version="$3"
  local request_id="$4"
  local backup_file="$5"
  local candidate_sha256="$6"
  local transaction_candidate

  transaction_candidate="$(mktemp)"
  printf '%s\n' \
    "state=$state" \
    "previous_version=$previous_version" \
    "requested_version=$requested_version" \
    "request_id=$request_id" \
    "backup_file=$backup_file" \
    "candidate_sha256=$candidate_sha256" > "$transaction_candidate"
  atomic_install "$transaction_candidate" "$ENV_TRANSACTION_FILE" 0600 root
  rm -f -- "$transaction_candidate"
  sync_host_state "$ENV_TRANSACTION_FILE"
}

read_transaction() {
  local key value
  local seen_state=0 seen_previous=0 seen_requested=0 seen_request=0 seen_backup=0 seen_hash=0
  if [ ! -e "$ENV_TRANSACTION_FILE" ] && [ ! -L "$ENV_TRANSACTION_FILE" ]; then
    return 1
  fi
  if [ ! -f "$ENV_TRANSACTION_FILE" ] || [ -L "$ENV_TRANSACTION_FILE" ] ||
    [ "$(stat -c '%U:%G:%a' "$ENV_TRANSACTION_FILE")" != "root:root:600" ]; then
    die "environment transaction metadata is invalid"
  fi

  TXN_STATE=""
  TXN_PREVIOUS_VERSION=""
  TXN_REQUESTED_VERSION=""
  TXN_REQUEST_ID=""
  TXN_BACKUP_FILE=""
  TXN_CANDIDATE_SHA256=""
  while IFS='=' read -r key value || [ -n "${key}${value}" ]; do
    case "$key" in
      state)
        [ "$seen_state" -eq 0 ] || die "environment transaction has duplicate state"
        TXN_STATE="$value"
        seen_state=1
        ;;
      previous_version)
        [ "$seen_previous" -eq 0 ] || die "environment transaction has duplicate previous version"
        TXN_PREVIOUS_VERSION="$value"
        seen_previous=1
        ;;
      requested_version)
        [ "$seen_requested" -eq 0 ] || die "environment transaction has duplicate requested version"
        TXN_REQUESTED_VERSION="$value"
        seen_requested=1
        ;;
      request_id)
        [ "$seen_request" -eq 0 ] || die "environment transaction has duplicate request id"
        TXN_REQUEST_ID="$value"
        seen_request=1
        ;;
      backup_file)
        [ "$seen_backup" -eq 0 ] || die "environment transaction has duplicate backup path"
        TXN_BACKUP_FILE="$value"
        seen_backup=1
        ;;
      candidate_sha256)
        [ "$seen_hash" -eq 0 ] || die "environment transaction has duplicate candidate hash"
        TXN_CANDIDATE_SHA256="$value"
        seen_hash=1
        ;;
      *) die "environment transaction contains an unknown field" ;;
    esac
  done < "$ENV_TRANSACTION_FILE"

  [ "$seen_state$seen_previous$seen_requested$seen_request$seen_backup$seen_hash" = "111111" ] ||
    die "environment transaction is incomplete"
  [[ "$TXN_STATE" =~ ^(prepared|installed|ready|committed|rollback-restored)$ ]] ||
    die "environment transaction state is invalid"
  [[ "$TXN_PREVIOUS_VERSION" =~ ^(0|[1-9][0-9]{0,11})$ ]] ||
    die "environment transaction previous version is invalid"
  [[ "$TXN_REQUESTED_VERSION" =~ ^[1-9][0-9]{0,11}$ ]] ||
    die "environment transaction requested version is invalid"
  [ "$TXN_REQUESTED_VERSION" -gt "$TXN_PREVIOUS_VERSION" ] ||
    die "environment transaction version order is invalid"
  validate_request_id "$TXN_REQUEST_ID"
  validate_backup_path "$TXN_BACKUP_FILE"
  [[ "$TXN_CANDIDATE_SHA256" =~ ^[0-9a-f]{64}$ ]] ||
    die "environment transaction hash is invalid"
}

require_matching_transaction() {
  local version="$1"
  local request_id="$2"
  read_transaction || die "environment transaction is missing"
  if [ "$TXN_REQUESTED_VERSION" != "$version" ] || [ "$TXN_REQUEST_ID" != "$request_id" ]; then
    die "environment transaction does not match the request"
  fi
}

restore_transaction_environment() {
  local current_version
  atomic_install "$TXN_BACKUP_FILE" "$APP_ENV_FILE" 0600 root
  sync_host_state "$APP_ENV_FILE"
  current_version="$(read_env_version)"
  if [ "$current_version" != "$TXN_PREVIOUS_VERSION" ]; then
    if [ "$current_version" != "$TXN_REQUESTED_VERSION" ]; then
      die "environment version changed outside the active transaction"
    fi
    write_env_version_exact "$TXN_PREVIOUS_VERSION"
  fi
  write_transaction \
    rollback-restored \
    "$TXN_PREVIOUS_VERSION" \
    "$TXN_REQUESTED_VERSION" \
    "$TXN_REQUEST_ID" \
    "$TXN_BACKUP_FILE" \
    "$TXN_CANDIDATE_SHA256"
}

begin_environment_transaction() {
  local source="$1"
  local version="$2"
  local request_id="$3"
  local source_mode="${4:-runner}"
  local previous_version backup_file candidate_sha256 staged_candidate

  if [ -e "$ENV_TRANSACTION_FILE" ] || [ -L "$ENV_TRANSACTION_FILE" ]; then
    die "an environment transaction is already active"
  fi
  if [ -e "$ADOPTION_TRANSACTION_FILE" ] || [ -L "$ADOPTION_TRANSACTION_FILE" ]; then
    die "an existing-deployment adoption transaction is active"
  fi
  if [ "$source_mode" = root ]; then
    source="$(validate_root_secret_candidate "$source")"
  elif [ "$source_mode" = runner ]; then
    source="$(validate_candidate "$source")"
  else
    die "invalid environment candidate trust mode"
  fi
  [[ "$version" =~ ^[1-9][0-9]{0,11}$ ]] || die "invalid secret version"
  validate_request_id "$request_id"
  previous_version="$(read_env_version)"
  [ "$version" -gt "$previous_version" ] || die "new secret version must advance the marker"

  staged_candidate="$(mktemp /etc/gole/.environment.candidate.XXXXXX)"
  register_temp_file "$staged_candidate"
  install -m 0600 -o root -g root "$source" "$staged_candidate"
  validate_production_environment "$staged_candidate"
  validate_production_compose "$staged_candidate"
  backup_file="$(create_environment_backup "$version" "$request_id")"
  candidate_sha256="$(sha256sum "$staged_candidate" | cut -d' ' -f1)"

  write_transaction prepared "$previous_version" "$version" "$request_id" \
    "$backup_file" "$candidate_sha256"
  atomic_install "$staged_candidate" "$APP_ENV_FILE" 0600 root
  sync_host_state "$APP_ENV_FILE"
  write_transaction installed "$previous_version" "$version" "$request_id" \
    "$backup_file" "$candidate_sha256"
  rm -f -- "$staged_candidate"
  forget_temp_file "$staged_candidate"
}

mark_environment_transaction_ready() {
  local version="$1"
  local request_id="$2"
  require_matching_transaction "$version" "$request_id"
  [ "$TXN_STATE" = "installed" ] || die "environment transaction is not installed"
  validate_current_environment
  [ "$(sha256sum "$APP_ENV_FILE" | cut -d' ' -f1)" = "$TXN_CANDIDATE_SHA256" ] ||
    die "environment changed during rollout"
  write_transaction ready "$TXN_PREVIOUS_VERSION" "$TXN_REQUESTED_VERSION" \
    "$TXN_REQUEST_ID" "$TXN_BACKUP_FILE" "$TXN_CANDIDATE_SHA256"
}

commit_environment_transaction() {
  local version="$1"
  local request_id="$2"
  local current_version
  require_matching_transaction "$version" "$request_id"
  [ "$TXN_STATE" = "ready" ] || die "environment transaction is not ready"
  validate_current_environment
  [ "$(sha256sum "$APP_ENV_FILE" | cut -d' ' -f1)" = "$TXN_CANDIDATE_SHA256" ] ||
    die "environment changed before commit"
  current_version="$(read_env_version)"
  if [ "$current_version" != "$TXN_PREVIOUS_VERSION" ] &&
    [ "$current_version" != "$TXN_REQUESTED_VERSION" ]; then
    die "environment version changed before commit"
  fi
  write_env_version_exact "$TXN_REQUESTED_VERSION"
  write_transaction committed "$TXN_PREVIOUS_VERSION" "$TXN_REQUESTED_VERSION" \
    "$TXN_REQUEST_ID" "$TXN_BACKUP_FILE" "$TXN_CANDIDATE_SHA256"
}

finalize_environment_transaction() {
  local version="$1"
  local request_id="$2"
  require_matching_transaction "$version" "$request_id"
  [ "$TXN_STATE" = "committed" ] || die "environment transaction is not committed"
  validate_current_environment
  [ "$(sha256sum "$APP_ENV_FILE" | cut -d' ' -f1)" = "$TXN_CANDIDATE_SHA256" ] ||
    die "committed environment hash is invalid"
  [ "$(read_env_version)" = "$TXN_REQUESTED_VERSION" ] ||
    die "committed environment version is invalid"
  remove_host_state "$ENV_TRANSACTION_FILE"
}

recover_environment_transaction() {
  local current_version current_sha256
  if ! read_transaction; then
    echo "NONE"
    return
  fi

  if [ "$TXN_STATE" = "ready" ] || [ "$TXN_STATE" = "committed" ]; then
    validate_current_environment
    current_version="$(read_env_version)"
    current_sha256="$(sha256sum "$APP_ENV_FILE" | cut -d' ' -f1)"
    if [ "$current_sha256" = "$TXN_CANDIDATE_SHA256" ] &&
      { [ "$current_version" = "$TXN_PREVIOUS_VERSION" ] ||
        [ "$current_version" = "$TXN_REQUESTED_VERSION" ]; }; then
      write_env_version_exact "$TXN_REQUESTED_VERSION"
      remove_host_state "$ENV_TRANSACTION_FILE"
      echo "COMMITTED"
      return
    fi
  fi

  restore_transaction_environment
  echo "RECOVERY_REQUIRED"
}

abort_environment_transaction() {
  local version="$1"
  local request_id="$2"
  require_matching_transaction "$version" "$request_id"
  restore_transaction_environment
}

finish_environment_recovery() {
  read_transaction || die "environment recovery transaction is missing"
  [ "$TXN_STATE" = "rollback-restored" ] || die "environment recovery is not ready to finish"
  validate_current_environment
  [ "$(sha256sum "$APP_ENV_FILE" | cut -d' ' -f1)" = \
    "$(sha256sum "$TXN_BACKUP_FILE" | cut -d' ' -f1)" ] ||
    die "recovered environment hash is invalid"
  [ "$(read_env_version)" = "$TXN_PREVIOUS_VERSION" ] ||
    die "recovered environment version is invalid"
  remove_host_state "$ENV_TRANSACTION_FILE"
}

restart_strict_lkg_services() {
  local attempt backend_ready=0 expected_sha="$1" https_ready=0
  create_release "$expected_sha" false
  select_release "$expected_sha"
  validate_current_production_model
  production_compose "$APP_ENV_FILE" up -d --no-build --no-deps \
    --force-recreate --wait support-agent
  production_compose "$APP_ENV_FILE" up -d --no-build --no-deps \
    --force-recreate --wait backend
  for ((attempt = 1; attempt <= 30; attempt++)); do
    if curl -fsS --max-time 5 \
      http://127.0.0.1:8080/actuator/health/readiness >/dev/null; then
      backend_ready=1
      break
    fi
    sleep 1
  done
  [ "$backend_ready" -eq 1 ] || die "restarted backend did not become ready"
  production_compose "$APP_ENV_FILE" up -d --no-build --no-deps \
    --force-recreate --wait nginx
  production_compose "$APP_ENV_FILE" exec -T nginx nginx -t >/dev/null
  production_compose "$APP_ENV_FILE" exec -T nginx nginx -s reload >/dev/null
  for ((attempt = 1; attempt <= 30; attempt++)); do
    if curl -fsS --max-time 5 --resolve gole.co.kr:443:127.0.0.1 \
      https://gole.co.kr/actuator/health/readiness >/dev/null; then
      https_ready=1
      break
    fi
    sleep 1
  done
  [ "$https_ready" -eq 1 ] || die "restarted HTTPS backend route did not become ready"
  verify_deployment_runtime_components "$expected_sha"
}

recover_environment_services_or_poweroff() {
  local expected_sha="$1"
  if ! restart_strict_lkg_services "$expected_sha"; then
    systemctl poweroff --no-block || true
    die "environment rollback runtime verification failed; VM powered off"
  fi
  finish_environment_recovery
}

secret_sync_exit_cleanup() {
  local failed=0 status=$?
  trap - EXIT INT TERM
  set +e
  rm -f -- "${ROOT_SECRET_CANDIDATE:-}"
  cleanup_registered_temporaries
  if [ "${SECRET_SYNC_TRANSACTION_ACTIVE:-0}" -eq 1 ] &&
    { [ -e "$ENV_TRANSACTION_FILE" ] || [ -L "$ENV_TRANSACTION_FILE" ]; }; then
    abort_environment_transaction "$SECRET_SYNC_VERSION" "$SECRET_SYNC_REQUEST_ID" || failed=1
    if [ "$failed" -ne 0 ] ||
      ! recover_environment_services_or_poweroff "$SECRET_SYNC_LKG_SHA"; then
      systemctl poweroff --no-block || true
      status=1
    fi
  fi
  exit "$status"
}

sync_secret_environment() {
  local requested_version="$1" request_id="$2" current_version lkg_sha recovery_state
  ROOT_SECRET_CANDIDATE=""
  SECRET_SYNC_TRANSACTION_ACTIVE=0
  SECRET_SYNC_VERSION="$requested_version"
  SECRET_SYNC_REQUEST_ID="$request_id"
  SECRET_SYNC_LKG_SHA=""
  trap secret_sync_exit_cleanup EXIT
  trap 'exit 130' INT
  trap 'exit 143' TERM
  validate_request_id "$request_id"
  [[ "$requested_version" =~ ^[1-9][0-9]{0,11}$ ]] || die "invalid secret version"
  [ ! -e "$ADOPTION_TRANSACTION_FILE" ] && [ ! -L "$ADOPTION_TRANSACTION_FILE" ] ||
    die "an adoption transaction is active"
  [ ! -e "$DEPLOYMENT_TRANSACTION_FILE" ] && [ ! -L "$DEPLOYMENT_TRANSACTION_FILE" ] ||
    die "a deployment transaction is active"
  [ ! -e "$NGINX_TRANSACTION_FILE" ] && [ ! -L "$NGINX_TRANSACTION_FILE" ] ||
    die "an Nginx transaction is active"
  lkg_sha="$(read_deployed_sha)"
  SECRET_SYNC_LKG_SHA="$lkg_sha"
  create_release "$lkg_sha" false
  select_release "$lkg_sha"
  validate_discord_environment

  if [ -e "$ENV_TRANSACTION_FILE" ] || [ -L "$ENV_TRANSACTION_FILE" ]; then
    recovery_state="$(recover_environment_transaction)"
    case "$recovery_state" in
      COMMITTED) ;;
      RECOVERY_REQUIRED) recover_environment_services_or_poweroff "$lkg_sha" ;;
      *) die "environment recovery returned an invalid state" ;;
    esac
  fi

  current_version="$(read_env_version)"
  [ "$requested_version" -ge "$current_version" ] ||
    die "older Secret Manager versions are rejected"
  fetch_root_secret_candidate "$requested_version"
  validate_production_environment "$ROOT_SECRET_CANDIDATE"
  validate_production_compose "$ROOT_SECRET_CANDIDATE"
  if [ "$requested_version" -eq "$current_version" ]; then
    cmp -s -- "$ROOT_SECRET_CANDIDATE" "$APP_ENV_FILE" ||
      die "the installed payload differs for the same secret version"
    rm -f -- "$ROOT_SECRET_CANDIDATE"
    forget_temp_file "$ROOT_SECRET_CANDIDATE"
    ROOT_SECRET_CANDIDATE=""
    trap - EXIT INT TERM
    return 0
  fi

  SECRET_SYNC_TRANSACTION_ACTIVE=1
  begin_environment_transaction "$ROOT_SECRET_CANDIDATE" "$requested_version" "$request_id" root
  rm -f -- "$ROOT_SECRET_CANDIDATE"
  forget_temp_file "$ROOT_SECRET_CANDIDATE"
  ROOT_SECRET_CANDIDATE=""
  # The EXIT handler owns rollback from this point until every marker is
  # durable. SIGKILL cannot run a trap; the next invocation recovers the root
  # journal before fetching another payload.

  restart_strict_lkg_services "$lkg_sha"
  mark_environment_transaction_ready "$requested_version" "$request_id"
  commit_environment_transaction "$requested_version" "$request_id"
  finalize_environment_transaction "$requested_version" "$request_id"
  SECRET_SYNC_TRANSACTION_ACTIVE=0
  trap - EXIT INT TERM
  prune_environment_backups ||
    echo "warning: old environment backup pruning failed" >&2
}

bootstrap_secret_environment() {
  local requested_version="$1" requested_sha="$2" mode="$3"
  local ROOT_SECRET_CANDIDATE=""
  [ "${SUDO_USER:-root}" = root ] || die "initial secret bootstrap is root-only"
  [[ "$requested_version" =~ ^[1-9][0-9]{0,11}$ ]] || die "invalid secret version"
  [[ "$requested_sha" =~ ^[0-9a-f]{40}$ ]] || die "invalid deployment SHA"
  case "$mode" in validate | install) ;; *) die "invalid bootstrap mode" ;; esac
  assert_initial_environment_empty
  create_release "$requested_sha" true
  select_release "$requested_sha"
  fetch_root_secret_candidate "$requested_version"
  validate_bootstrap_environment_candidate "$ROOT_SECRET_CANDIDATE" "$requested_version" root
  if [ "$mode" = install ]; then
    bootstrap_environment "$ROOT_SECRET_CANDIDATE" "$requested_version" root
  fi
  rm -f -- "$ROOT_SECRET_CANDIDATE"
  forget_temp_file "$ROOT_SECRET_CANDIDATE"
}

adoption_secret_exit_cleanup() {
  local failed=0 status=$?
  trap - EXIT INT TERM
  set +e
  rm -f -- "${ROOT_SECRET_CANDIDATE:-}"
  cleanup_registered_temporaries
  if [ "${ADOPTION_SECRET_TRANSACTION_ACTIVE:-0}" -eq 1 ] &&
    { [ -e "$ADOPTION_TRANSACTION_FILE" ] || [ -L "$ADOPTION_TRANSACTION_FILE" ]; }; then
    abort_adoption_transaction "$ADOPTION_SECRET_REQUEST_ID" || failed=1
    if [ "$failed" -ne 0 ] ||
      ! restart_adoption_services "$ADOPTION_SECRET_REQUEST_ID" ||
      ! verify_adoption_services "$ADOPTION_SECRET_REQUEST_ID" ||
      ! finish_adoption_recovery "$ADOPTION_SECRET_REQUEST_ID"; then
      systemctl poweroff --no-block || true
      status=1
    fi
  fi
  exit "$status"
}

migrate_and_adopt_secret() {
  local adoption_sha="$1" requested_version="$2" request_id="$3"
  local recovery_state
  ROOT_SECRET_CANDIDATE=""
  [ "${SUDO_USER:-root}" = root ] || die "existing deployment adoption is root-only"
  [[ "$adoption_sha" =~ ^[0-9a-f]{40}$ ]] || die "invalid adoption SHA"
  [[ "$requested_version" =~ ^[1-9][0-9]{0,11}$ ]] || die "invalid secret version"
  validate_request_id "$request_id"
  [ ! -e "$DEPLOYMENT_TRANSACTION_FILE" ] && [ ! -L "$DEPLOYMENT_TRANSACTION_FILE" ] ||
    die "a deployment transaction is active"
  [ ! -e "$ENV_TRANSACTION_FILE" ] && [ ! -L "$ENV_TRANSACTION_FILE" ] ||
    die "an environment transaction is active"
  [ ! -e "$NGINX_TRANSACTION_FILE" ] && [ ! -L "$NGINX_TRANSACTION_FILE" ] ||
    die "an Nginx transaction is active"

  if [ -e "$ADOPTION_TRANSACTION_FILE" ] || [ -L "$ADOPTION_TRANSACTION_FILE" ]; then
    read_adoption_transaction || die "adoption recovery transaction is missing"
    # Recovery is part of the original root operation, not a generic adoption
    # shortcut. Reject stale or mistyped workflow arguments before any release,
    # environment, image, service, or marker mutation occurs.
    require_exact_adoption_invocation "$adoption_sha" "$requested_version" "$request_id"
    validate_discord_environment
    create_release "$ADOPT_DEPLOYMENT_SHA" false
    select_release "$ADOPT_DEPLOYMENT_SHA"
    recovery_state="$(recover_adoption_transaction)"
    case "$recovery_state" in
      COMMITTED) return 0 ;;
      RECOVERY_REQUIRED:*)
        restart_adoption_services "${recovery_state#RECOVERY_REQUIRED:}" || {
          systemctl poweroff --no-block || true
          die "adoption recovery restart failed; VM powered off"
        }
        verify_adoption_services "${recovery_state#RECOVERY_REQUIRED:}" || {
          systemctl poweroff --no-block || true
          die "adoption recovery verification failed; VM powered off"
        }
        finish_adoption_recovery "${recovery_state#RECOVERY_REQUIRED:}"
        ;;
      *) die "adoption recovery returned an invalid state" ;;
    esac
  fi

  create_release "$adoption_sha" historical-main
  select_release "$adoption_sha"
  assert_existing_adoption_markers_empty
  validate_existing_deployment_runtime "$adoption_sha" false
  # The old runner cannot reveal GitHub Secrets to the operator. Preserve only
  # the already-running containers' same-purpose Discord routes after their
  # clean SHA, health and Compose ownership have been proven. No value is
  # written to stdout or a runner-owned path.
  ensure_legacy_discord_environment
  verify_seller_identity_launch_preflight
  [ "$requested_version" -gt "$(read_env_version)" ] ||
    die "adoption secret version must advance the marker"
  ADOPTION_SECRET_TRANSACTION_ACTIVE=0
  ADOPTION_SECRET_REQUEST_ID="$request_id"
  trap adoption_secret_exit_cleanup EXIT
  trap 'exit 130' INT
  trap 'exit 143' TERM
  fetch_root_secret_candidate "$requested_version"
  ADOPTION_SECRET_TRANSACTION_ACTIVE=1
  begin_adoption_transaction "$ROOT_SECRET_CANDIDATE" "$requested_version" \
    "$request_id" "$adoption_sha" root
  rm -f -- "$ROOT_SECRET_CANDIDATE"
  forget_temp_file "$ROOT_SECRET_CANDIDATE"
  ROOT_SECRET_CANDIDATE=""
  restart_adoption_services "$request_id"
  verify_adoption_services "$request_id"
  mark_adoption_transaction_ready "$request_id"
  commit_adoption_transaction "$request_id"
  finalize_adoption_transaction "$request_id"
  ADOPTION_SECRET_TRANSACTION_ACTIVE=0
  trap - EXIT INT TERM
}

hostctl_command="${1:-}"
shift || true
case "$hostctl_command" in
  privilege-probe)
    require_argument_count 0 "$@"
    ;;
  env-bootstrap-check)
    require_argument_count 0 "$@"
    assert_initial_environment_empty
    ;;
  env-bootstrap-validate)
    require_argument_count 2 "$@"
    validate_bootstrap_environment_candidate "$1" "$2"
    ;;
  env-bootstrap)
    require_argument_count 2 "$@"
    bootstrap_environment "$1" "$2"
    ;;
  env-read-version)
    require_argument_count 0 "$@"
    read_env_version
    ;;
  env-candidate-matches-current)
    require_argument_count 1 "$@"
    candidate="$(validate_candidate "$1")"
    validate_current_environment
    cmp -s -- "$candidate" "$APP_ENV_FILE"
    ;;
  env-prune)
    require_argument_count 0 "$@"
    if [ -e "$ENV_TRANSACTION_FILE" ] || [ -L "$ENV_TRANSACTION_FILE" ]; then
      die "cannot prune backups during an environment transaction"
    fi
    install -d -m 0700 -o root -g root "$BACKUP_DIR"
    find "$BACKUP_DIR" -xdev -type f -name 'gole.env.*' -mtime +30 -delete
    ;;
  env-transaction-begin)
    require_argument_count 3 "$@"
    begin_environment_transaction "$1" "$2" "$3"
    ;;
  env-transaction-mark-ready)
    require_argument_count 2 "$@"
    mark_environment_transaction_ready "$1" "$2"
    ;;
  env-transaction-commit)
    require_argument_count 2 "$@"
    commit_environment_transaction "$1" "$2"
    ;;
  env-transaction-finalize)
    require_argument_count 2 "$@"
    finalize_environment_transaction "$1" "$2"
    ;;
  env-transaction-abort)
    require_argument_count 2 "$@"
    abort_environment_transaction "$1" "$2"
    ;;
  env-transaction-recover)
    require_argument_count 0 "$@"
    recover_environment_transaction
    ;;
  env-transaction-finish-recovery)
    require_argument_count 0 "$@"
    finish_environment_recovery
    ;;
  deployment-read-sha)
    require_argument_count 0 "$@"
    read_deployed_sha
    ;;
  deployment-begin)
    require_argument_count 4 "$@"
    begin_deployment_transaction "$1" "$2" "$3" "$4"
    ;;
  deployment-recover)
    require_argument_count 0 "$@"
    if ! (recover_deployment_transaction); then
      systemctl poweroff --no-block || true
      die "deployment recovery failed; VM powered off"
    fi
    ;;
  deployment-reset-initial-failure)
    require_argument_count 0 "$@"
    reset_initial_deployment_failure
    ;;
  deployment-is-uninitialized)
    require_argument_count 0 "$@"
    validate_initial_deployment
    ;;
  deployment-compose-build)
    require_argument_count 3 "$@"
    build_deployment_images "$1" "$2" "$3"
    ;;
  deployment-compose-up)
    require_argument_count 3 "$@"
    run_deployment_compose_phase "$1" "$2" "$3"
    ;;
  deployment-compose-ps)
    require_argument_count 2 "$@"
    show_deployment_status "$1" "$2"
    ;;
  deployment-images-snapshot)
    require_argument_count 2 "$@"
    snapshot_deployment_images "$1" "$2"
    ;;
  deployment-images-restore)
    require_argument_count 2 "$@"
    restore_deployment_images "$1" "$2"
    ;;
  deployment-images-cleanup)
    require_argument_count 2 "$@"
    cleanup_deployment_images_command "$1" "$2"
    ;;
  deployment-budget-healthy)
    require_argument_count 0 "$@"
    verify_budget_relay_health
    ;;
  discord-overlay-install)
    require_argument_count 0 "$@"
    install_discord_environment_from_stdin
    ;;
  discord-overlay-verify)
    require_argument_count 0 "$@"
    validate_discord_environment
    ;;
  secret-sync)
    require_argument_count 2 "$@"
    sync_secret_environment "$1" "$2"
    ;;
  env-bootstrap-secret)
    require_argument_count 3 "$@"
    bootstrap_secret_environment "$1" "$2" "$3"
    ;;
  deployment-migrate-adopt-secret)
    require_argument_count 3 "$@"
    migrate_and_adopt_secret "$1" "$2" "$3"
    ;;
  deployment-verify-candidate-runtime)
    require_argument_count 2 "$@"
    verify_candidate_deployment_runtime "$1" "$2"
    ;;
  deployment-verify-initial-http-commit)
    require_argument_count 2 "$@"
    verify_initial_http_commit "$1" "$2"
    ;;
  deployment-complete-initial-tls)
    require_argument_count 0 "$@"
    read_deployment_transaction
    complete_initial_tls_commit "$DEPLOY_TX_REQUEST_ID"
    ;;
  certificate-renew)
    require_argument_count 0 "$@"
    renew_certificate
    ;;
  certificate-issue)
    require_argument_count 0 "$@"
    issue_certificate
    ;;
  deployment-verify-commit)
    require_argument_count 2 "$@"
    verify_full_deployment_runtime "$1" "$2"
    ;;
  deployment-verify-runtime)
    require_argument_count 1 "$@"
    [ "$(read_deployed_sha)" = "$1" ] || die "deployed SHA marker does not match"
    [ ! -e "$METADATA_MIGRATION_MARKER" ] && [ ! -L "$METADATA_MIGRATION_MARKER" ] ||
      die "metadata migration is not finalized"
    verify_metadata_full_policy
    verify_broker_native_budget_relay
    verify_deployment_runtime_components "$1"
    ;;
  deployment-verify-adopted-runtime)
    require_argument_count 1 "$@"
    verify_adopted_deployment_runtime "$1"
    ;;
  adoption-transaction-begin)
    require_argument_count 4 "$@"
    begin_adoption_transaction "$1" "$2" "$3" "$4"
    ;;
  adoption-transaction-mark-ready)
    require_argument_count 1 "$@"
    mark_adoption_transaction_ready "$1"
    ;;
  adoption-transaction-commit)
    require_argument_count 1 "$@"
    commit_adoption_transaction "$1"
    ;;
  adoption-transaction-finalize)
    require_argument_count 1 "$@"
    finalize_adoption_transaction "$1"
    ;;
  adoption-transaction-abort)
    require_argument_count 1 "$@"
    abort_adoption_transaction "$1"
    ;;
  adoption-transaction-recover)
    require_argument_count 0 "$@"
    recover_adoption_transaction
    ;;
  adoption-transaction-finish-recovery)
    require_argument_count 1 "$@"
    finish_adoption_recovery "$1"
    ;;
  adoption-services-restart)
    require_argument_count 1 "$@"
    restart_adoption_services "$1"
    ;;
  adoption-services-verify)
    require_argument_count 1 "$@"
    verify_adoption_services "$1"
    ;;
  nginx-transaction-begin)
    require_argument_count 2 "$@"
    begin_nginx_transaction "$1" "$2"
    ;;
  nginx-transaction-abort)
    require_argument_count 1 "$@"
    abort_nginx_transaction "$1"
    ;;
  nginx-transaction-recover)
    require_argument_count 0 "$@"
    recover_nginx_transaction
    ;;
  nginx-transaction-finish-recovery)
    require_argument_count 1 "$@"
    finish_nginx_recovery "$1"
    ;;
  nginx-transaction-commit)
    require_argument_count 1 "$@"
    commit_nginx_transaction "$1"
    ;;
  nginx-transaction-finalize)
    require_argument_count 1 "$@"
    finalize_nginx_transaction "$1"
    ;;
  deployment-record-sha)
    require_argument_count 2 "$@"
    record_deployment_sha "$1" "$2"
    ;;
  deployment-finalize)
    require_argument_count 1 "$@"
    finalize_deployment_transaction "$1"
    ;;
  deployment-finalize-partial)
    require_argument_count 1 "$@"
    finalize_partial_deployment_transaction "$1"
    ;;
  deployment-rollback)
    require_argument_count 1 "$@"
    if ! (rollback_deployment_transaction "$1"); then
      systemctl poweroff --no-block || true
      die "deployment rollback failed; VM powered off"
    fi
    ;;
  deployment-fail-closed)
    require_argument_count 1 "$@"
    require_deployment_transaction "$1" prepared,snapshotted,built,nginx-installed,mutation-armed,mutated,refreshed,budget-updated,verified,marker-recorded,initial-http-verified,runtime-verified,metadata-ratchet-armed,metadata-ratchet-verified,initial-reset-armed,cleanup-pending,rollback-restored
    systemctl poweroff --no-block
    ;;
  deployment-fail-closed-initial-tls)
    require_argument_count 0 "$@"
    read_deployment_transaction
    [ "$DEPLOY_TX_STATE" = initial-http-verified ] &&
      [ "$DEPLOY_TX_TARGET" = all ] && [ "$DEPLOY_TX_PREVIOUS_SHA" = 0 ] ||
      die "no initial TLS completion is pending"
    systemctl poweroff --no-block
    ;;
  watchdog-install)
    require_argument_count 0 "$@"
    install_cost_guard_watchdog
    ;;
  watchdog-active)
    require_argument_count 0 "$@"
    systemctl is-active --quiet gole-cost-guard-watchdog.timer
    ;;
  cost-guard-fail-closed)
    require_argument_count 0 "$@"
    cost_guard_fail_closed
    ;;
  *)
    die "unsupported host operation"
    ;;
esac
