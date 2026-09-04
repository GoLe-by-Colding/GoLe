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
INITIAL_DEPLOY_FILE="/etc/gole/initial-deploy.pending"
ENV_VERSION_FILE="/etc/gole/gole.env.version"
ENV_TRANSACTION_FILE="/etc/gole/gole.env.transaction"
INFRA_ENV_FILE="/etc/gole/infra.env"
IMAGE_BACKUP_DIR="/var/backups/gole-images"
NGINX_BACKUP_DIR="/var/backups/gole-nginx"
NGINX_CONFIG_FILE="/etc/gole/nginx.conf"
NGINX_TRANSACTION_FILE="/etc/gole/nginx.conf.transaction"
NGINX_VALIDATION_IMAGE="nginx:1.29-alpine@sha256:5616878291a2eed594aee8db4dade5878cf7edcb475e59193904b198d9b830de"
BROKER_CONFIG_FILE="/etc/gole/cloud-broker.conf"
PRODUCTION_SECRET_NAME="gole-production-env"
PRODUCTION_COMPOSE_FILE="$APP_ROOT/infra/gcp/docker-compose.yml"
PRODUCTION_COMPOSE_VALIDATOR="/usr/local/libexec/gole/validate-production-compose.py"
PRODUCTION_ENV_VALIDATOR="/usr/local/libexec/gole/validate-production-env.py"

die() {
  echo "$*" >&2
  exit 1
}

# A function-local variable is no longer in scope when Bash runs an EXIT trap.
# Keep temporary paths in a process-global registry instead; this also avoids
# helper functions overwriting the transaction rollback EXIT handler.
GOLE_TEMP_FILES=()
GOLE_TEMP_DIRS=()

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
  if ! ln "$version_stage" "$ENV_VERSION_FILE"; then
    if [ "$(stat -c '%d:%i' "$environment_stage")" = "$(stat -c '%d:%i' "$APP_ENV_FILE")" ]; then
      rm -f -- "$APP_ENV_FILE"
    fi
    die "could not atomically bootstrap the environment version marker"
  fi
  if ! ln "$initial_deploy_stage" "$INITIAL_DEPLOY_FILE"; then
    if [ "$(stat -c '%d:%i' "$version_stage")" = "$(stat -c '%d:%i' "$ENV_VERSION_FILE")" ]; then
      rm -f -- "$ENV_VERSION_FILE"
    fi
    if [ "$(stat -c '%d:%i' "$environment_stage")" = "$(stat -c '%d:%i' "$APP_ENV_FILE")" ]; then
      rm -f -- "$APP_ENV_FILE"
    fi
    die "could not atomically create the initial deployment marker"
  fi
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

  while IFS='=' read -r key value; do
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
  if [ "$mode" = strict ]; then
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
}

read_deployment_transaction() {
  local key value seen_state=0 seen_target=0 seen_request=0 seen_new=0 seen_previous=0
  [ -f "$DEPLOYMENT_TRANSACTION_FILE" ] && [ ! -L "$DEPLOYMENT_TRANSACTION_FILE" ] &&
    [ "$(stat -c '%U:%G:%a' "$DEPLOYMENT_TRANSACTION_FILE")" = "root:root:600" ] ||
    die "deployment transaction is missing or invalid"
  DEPLOY_TX_STATE="" DEPLOY_TX_TARGET="" DEPLOY_TX_REQUEST_ID="" DEPLOY_TX_NEW_SHA="" DEPLOY_TX_PREVIOUS_SHA=""
  while IFS='=' read -r key value; do
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
  [[ "$DEPLOY_TX_STATE" =~ ^(prepared|snapshotted|built|nginx-installed|mutated|refreshed|budget-updated|verified|marker-recorded|runtime-verified|rollback-restored)$ ]] ||
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
  while IFS='=' read -r key value; do
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
  [[ "$ADOPT_STATE" =~ ^(prepared|installed|ready|committed|adopted|rollback-restored)$ ]] ||
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
  backup_file="$ADOPTION_BACKUP_DIR/gole.env.$request_id"
  if [ -e "$backup_file" ] || [ -L "$backup_file" ]; then
    die "adoption backup already exists"
  fi
  install -m 0600 -o root -g root "$APP_ENV_FILE" "$backup_file"
  candidate_sha256="$(sha256sum "$staged_candidate" | cut -d' ' -f1)"
  write_adoption_transaction prepared "$previous_version" "$requested_version" \
    "$request_id" "$backup_file" "$candidate_sha256" "$adoption_sha"
  atomic_install "$staged_candidate" "$APP_ENV_FILE" 0600 root
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
  local current_version request_id="$1" sha_candidate
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

  sha_candidate="$(mktemp)"
  register_temp_file "$sha_candidate"
  printf '%s\n' "$ADOPT_DEPLOYMENT_SHA" > "$sha_candidate"
  atomic_install "$sha_candidate" "$DEPLOYED_SHA_FILE" 0644 root
  rm -f -- "$sha_candidate"
  forget_temp_file "$sha_candidate"
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
  rm -f -- "$ADOPTION_TRANSACTION_FILE"
}

restore_adoption_transaction() {
  local current_deployed_sha
  validate_adoption_backup_path "$ADOPT_BACKUP_FILE"
  if [ -e "$DEPLOYED_SHA_FILE" ] || [ -L "$DEPLOYED_SHA_FILE" ]; then
    current_deployed_sha="$(read_deployed_sha)"
    [ "$current_deployed_sha" = "$ADOPT_DEPLOYMENT_SHA" ] ||
      die "deployment marker changed outside the adoption transaction"
    rm -f -- "$DEPLOYED_SHA_FILE"
  fi
  atomic_install "$ADOPT_BACKUP_FILE" "$APP_ENV_FILE" 0600 root
  write_env_version_exact "$ADOPT_PREVIOUS_VERSION"
  write_adoption_transaction rollback-restored "$ADOPT_PREVIOUS_VERSION" \
    "$ADOPT_REQUESTED_VERSION" "$ADOPT_REQUEST_ID" "$ADOPT_BACKUP_FILE" \
    "$ADOPT_CANDIDATE_SHA256" "$ADOPT_DEPLOYMENT_SHA"
}

abort_adoption_transaction() {
  local request_id="$1"
  require_matching_adoption_transaction "$request_id"
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
      rm -f -- "$ADOPTION_TRANSACTION_FILE"
      echo "COMMITTED"
      return
    fi
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
  rm -f -- "$ADOPTION_TRANSACTION_FILE"
}

validate_deployment_target() {
  case "$1" in
    all | backend | frontend) ;;
    *) die "invalid deployment target" ;;
  esac
}

deployment_image_entries() {
  case "$1" in
    all)
      printf '%s\n' \
        'gole-support-agent|gole/support-agent:local' \
        'gole-backend|gole/backend:local' \
        'gole-frontend|gole/frontend:local' \
        'gole-budget-relay|gole/budget-relay:local'
      ;;
    backend)
      printf '%s\n' \
        'gole-support-agent|gole/support-agent:local' \
        'gole-backend|gole/backend:local'
      ;;
    frontend) printf '%s\n' 'gole-frontend|gole/frontend:local' ;;
    *) die "invalid deployment target" ;;
  esac
}

deployment_image_marker() {
  local compact_request_id request_id="$1"
  validate_request_id "$request_id"
  compact_request_id="${request_id//-/}"
  printf '%s/images.%s\n' "$IMAGE_BACKUP_DIR" "$compact_request_id"
}

snapshot_deployment_images() {
  local container count=0 image image_id marker marker_candidate request_id="$2" target="$1"
  validate_deployment_target "$target"
  require_deployment_transaction "$request_id" prepared
  [ "$DEPLOY_TX_TARGET" = "$target" ] || die "deployment image target does not match transaction"
  marker="$(deployment_image_marker "$request_id")"
  install -d -m 0700 -o root -g root "$IMAGE_BACKUP_DIR"
  if [ -e "$marker" ] || [ -L "$marker" ]; then
    die "deployment image snapshot already exists"
  fi
  while IFS='|' read -r container image; do
    image_id="$(docker inspect --format '{{.Image}}' "$container" 2>/dev/null || true)"
    if [ -n "$image_id" ]; then
      docker image tag "$image_id" "${image}:rollback-${request_id//-/}"
      count=$((count + 1))
    fi
  done < <(deployment_image_entries "$target")
  marker_candidate="$(mktemp)"
  register_temp_file "$marker_candidate"
  printf 'target=%s\nrequest_id=%s\nimage_count=%s\n' "$target" "$request_id" "$count" > "$marker_candidate"
  atomic_install "$marker_candidate" "$marker" 0600 root
  rm -f -- "$marker_candidate"
  forget_temp_file "$marker_candidate"
  advance_deployment_transaction "$request_id" prepared snapshotted
}

read_deployment_image_marker() {
  local key marker="$1" value
  local seen_count=0 seen_request=0 seen_target=0
  if [ ! -f "$marker" ] || [ -L "$marker" ] ||
    [ "$(stat -c '%U:%G:%a' "$marker")" != "root:root:600" ]; then
    die "deployment image snapshot marker is missing or invalid"
  fi
  SNAPSHOT_TARGET=""
  SNAPSHOT_REQUEST_ID=""
  SNAPSHOT_IMAGE_COUNT=""
  while IFS='=' read -r key value; do
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
      image_count)
        [ "$seen_count" -eq 0 ] || die "duplicate image snapshot count"
        SNAPSHOT_IMAGE_COUNT="$value"
        seen_count=1
        ;;
      *) die "unknown deployment image snapshot field" ;;
    esac
  done < "$marker"
  [ "$seen_target$seen_request$seen_count" = "111" ] || die "incomplete image snapshot marker"
  validate_deployment_target "$SNAPSHOT_TARGET"
  validate_request_id "$SNAPSHOT_REQUEST_ID"
  [[ "$SNAPSHOT_IMAGE_COUNT" =~ ^[0-4]$ ]] || die "invalid image snapshot count"
}

require_deployment_image_snapshot() {
  local marker request_id="$2" target="$1"
  marker="$(deployment_image_marker "$request_id")"
  read_deployment_image_marker "$marker"
  if [ "$SNAPSHOT_TARGET" != "$target" ] || [ "$SNAPSHOT_REQUEST_ID" != "$request_id" ]; then
    die "deployment image snapshot does not match"
  fi
  SNAPSHOT_MARKER="$marker"
}

restore_deployment_images() {
  local container image restored=0 request_id="$2" rollback_image target="$1"
  require_deployment_image_snapshot "$target" "$request_id"
  [ "$SNAPSHOT_IMAGE_COUNT" -gt 0 ] || die "deployment image snapshot is empty"
  while IFS='|' read -r container image; do
    rollback_image="${image}:rollback-${request_id//-/}"
    if docker image inspect "$rollback_image" >/dev/null 2>&1; then
      docker image tag "$rollback_image" "$image"
      restored=$((restored + 1))
    fi
  done < <(deployment_image_entries "$target")
  [ "$restored" -eq "$SNAPSHOT_IMAGE_COUNT" ] || die "deployment image snapshot is incomplete"
}

cleanup_deployment_images() {
  local container image request_id="$2" rollback_image target="$1"
  require_deployment_image_snapshot "$target" "$request_id"
  while IFS='|' read -r container image; do
    rollback_image="${image}:rollback-${request_id//-/}"
    docker image rm "$rollback_image" >/dev/null 2>&1 || true
  done < <(deployment_image_entries "$target")
  rm -f -- "$SNAPSHOT_MARKER"
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
    built,nginx-installed,mutated,refreshed,budget-updated
  [ "$DEPLOY_TX_NEW_SHA" = "$requested_sha" ] || die "deployment phase SHA does not match"
  select_release "$requested_sha"
  validate_current_production_model
  case "$phase" in
    rollout-all-apps)
      [ "$DEPLOY_TX_TARGET" = all ] && [ "$DEPLOY_TX_STATE" = nginx-installed ] ||
        die "all-services rollout is out of order"
      production_compose "$APP_ENV_FILE" up -d --remove-orphans --wait \
        support-agent backend frontend nginx
      advance_deployment_transaction "$request_id" nginx-installed mutated
      ;;
    rollout-backend)
      [ "$DEPLOY_TX_TARGET" = backend ] && [ "$DEPLOY_TX_STATE" = built ] ||
        die "backend rollout is out of order"
      production_compose "$APP_ENV_FILE" up -d --remove-orphans --wait \
        support-agent backend nginx
      advance_deployment_transaction "$request_id" built mutated
      ;;
    rollout-frontend)
      [ "$DEPLOY_TX_TARGET" = frontend ] && [ "$DEPLOY_TX_STATE" = built ] ||
        die "frontend rollout is out of order"
      production_compose "$APP_ENV_FILE" up -d --remove-orphans --wait frontend nginx
      advance_deployment_transaction "$request_id" built mutated
      ;;
    rollout-budget)
      [ "$DEPLOY_TX_TARGET" = all ] && [ "$DEPLOY_TX_STATE" = refreshed ] ||
        die "budget rollout is out of order"
      production_compose "$APP_ENV_FILE" up -d --no-deps --wait budget-relay
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
  require_deployment_transaction "$request_id" prepared,snapshotted,built,nginx-installed,mutated,refreshed,budget-updated,verified,marker-recorded,runtime-verified
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

renew_certificate() {
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
  validate_current_production_model
  production_compose "$APP_ENV_FILE" --profile certificate run --rm --no-deps -T certbot renew --quiet
  production_compose "$APP_ENV_FILE" exec -T nginx nginx -t >/dev/null
  production_compose "$APP_ENV_FILE" exec -T nginx nginx -s reload >/dev/null
}

verify_deployment_runtime_components() {
  local apex_headers canonical_path container expected_sha="$1" http_redirect https_redirect state
  select_release "$expected_sha"
  validate_current_production_model
  for container in gole-backend gole-frontend gole-budget-relay gole-support-agent gole-nginx; do
    state="$(docker inspect --format \
      '{{.State.Status}}:{{if .State.Health}}{{.State.Health.Status}}{{else}}missing{{end}}' \
      "$container" 2>/dev/null || true)"
    [ "$state" = "running:healthy" ] || die "required production container is not healthy"
  done
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
  systemctl is-active --quiet gole-cost-guard-watchdog.timer ||
    die "cost guard watchdog timer is not active"
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

restart_adoption_services() {
  local available request_id="$1" service
  local services=()
  require_matching_adoption_transaction "$request_id"
  case "$ADOPT_STATE" in
    installed | rollback-restored) ;;
    *) die "adoption services cannot restart in the current transaction state" ;;
  esac
  validate_clean_checkout_sha "$ADOPT_DEPLOYMENT_SHA"
  validate_current_environment
  validate_production_compose "$APP_ENV_FILE" legacy-adoption
  available="$(production_compose "$APP_ENV_FILE" config --services)"
  for service in support-agent backend frontend budget-relay nginx; do
    if grep -qx "$service" <<<"$available"; then
      services+=("$service")
    fi
  done
  for service in backend frontend budget-relay nginx; do
    grep -qx "$service" <<<"$available" || die "legacy Compose is missing a required service"
  done
  production_compose "$APP_ENV_FILE" up -d --no-build --force-recreate \
    --remove-orphans --wait "${services[@]}"
  production_compose "$APP_ENV_FILE" exec -T nginx nginx -t >/dev/null
}

verify_adoption_services() {
  local available request_id="$1" state
  require_matching_adoption_transaction "$request_id"
  case "$ADOPT_STATE" in
    installed | rollback-restored) ;;
    *) die "adoption services cannot be verified in the current transaction state" ;;
  esac
  validate_existing_deployment_runtime "$ADOPT_DEPLOYMENT_SHA" false
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
  while IFS='=' read -r key value; do
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
  candidate_sha256="$(sha256sum "$staged_candidate" | cut -d' ' -f1)"
  write_nginx_transaction prepared "$request_id" "$backup_file" "$candidate_sha256" "$deploy_sha"
  atomic_install "$staged_candidate" "$NGINX_CONFIG_FILE" 0644 root
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
      rm -f -- "$NGINX_TRANSACTION_FILE"
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
  rm -f -- "$NGINX_TRANSACTION_FILE"
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
  require_deployment_transaction "$request_id" runtime-verified
  [ "$NGINX_TXN_STATE" = "committed" ] || die "Nginx transaction is not committed"
  validate_nginx_config
  [ "$(sha256sum "$NGINX_CONFIG_FILE" | cut -d' ' -f1)" = "$NGINX_TXN_CANDIDATE_SHA256" ] ||
    die "committed Nginx configuration hash is invalid"
  current_deployed_sha="$(read_deployed_sha)"
  [ "$current_deployed_sha" = "$NGINX_TXN_DEPLOY_SHA" ] ||
    die "deployment marker does not match the Nginx transaction"
  rm -f -- "$NGINX_TRANSACTION_FILE"
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
  verify_deployment_runtime_components "$expected_sha"
  if [ "$DEPLOY_TX_TARGET" = all ]; then
    verify_seller_identity_launch_preflight
  fi
  advance_deployment_transaction "$request_id" "$expected_state" verified
}

record_deployment_sha() {
  local requested_sha="$1" request_id="$2" initial_deployment=false sha_candidate
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
  sha_candidate="$(mktemp)"
  register_temp_file "$sha_candidate"
  printf '%s\n' "$requested_sha" > "$sha_candidate"
  atomic_install "$sha_candidate" "$DEPLOYED_SHA_FILE" 0644 root
  if [ "$initial_deployment" = true ] && ! rm -f -- "$INITIAL_DEPLOY_FILE"; then
    rm -f -- "$DEPLOYED_SHA_FILE"
    die "could not retire the initial deployment marker"
  fi
  rm -f -- "$sha_candidate"
  forget_temp_file "$sha_candidate"
  advance_deployment_transaction "$request_id" verified marker-recorded
}

finalize_deployment_transaction() {
  local request_id="$1"
  require_deployment_transaction "$request_id" runtime-verified
  if [ "$DEPLOY_TX_TARGET" = all ] && { [ -e "$NGINX_TRANSACTION_FILE" ] || [ -L "$NGINX_TRANSACTION_FILE" ]; }; then
    die "Nginx transaction must be finalized first"
  fi
  require_deployment_image_snapshot "$DEPLOY_TX_TARGET" "$request_id"
  cleanup_deployment_images "$DEPLOY_TX_TARGET" "$request_id"
  rm -f -- "$DEPLOYMENT_TRANSACTION_FILE"
}

finalize_partial_deployment_transaction() {
  local request_id="$1"
  require_deployment_transaction "$request_id" verified
  [ "$DEPLOY_TX_TARGET" != all ] || die "full deployment requires marker verification"
  [ "$(read_deployed_sha)" = "$DEPLOY_TX_NEW_SHA" ] ||
    die "partial deployment may only rebuild the current LKG SHA"
  require_deployment_image_snapshot "$DEPLOY_TX_TARGET" "$request_id"
  cleanup_deployment_images "$DEPLOY_TX_TARGET" "$request_id"
  rm -f -- "$DEPLOYMENT_TRANSACTION_FILE"
}

recover_deployment_transaction() {
  if [ ! -e "$DEPLOYMENT_TRANSACTION_FILE" ] && [ ! -L "$DEPLOYMENT_TRANSACTION_FILE" ]; then
    if [ -e "$NGINX_TRANSACTION_FILE" ] || [ -L "$NGINX_TRANSACTION_FILE" ]; then
      die "orphaned Nginx transaction requires manual root recovery"
    fi
    echo NONE
    return
  fi
  read_deployment_transaction
  rollback_deployment_transaction "$DEPLOY_TX_REQUEST_ID"
  echo RECOVERED
}

rollback_deployment_transaction() {
  local available current_marker request_id="$1" service state
  local services=()
  require_deployment_transaction "$request_id" prepared,snapshotted,built,nginx-installed,mutated,refreshed,budget-updated,verified,marker-recorded,runtime-verified
  if [ "$DEPLOY_TX_PREVIOUS_SHA" = 0 ]; then
    # A first deployment has no known-good application or image. Leaving an
    # unknown partial service online is less safe than stopping the VM.
    systemctl poweroff --no-block
    echo "initial deployment failed closed; transaction retained for audit" >&2
    return 0
  fi
  if [ -e "$NGINX_TRANSACTION_FILE" ] || [ -L "$NGINX_TRANSACTION_FILE" ]; then
    require_matching_nginx_transaction "$request_id"
    if [ "$NGINX_TXN_STATE" != rollback-restored ]; then
      restore_nginx_transaction
    fi
  fi
  require_deployment_image_snapshot "$DEPLOY_TX_TARGET" "$request_id"
  restore_deployment_images "$DEPLOY_TX_TARGET" "$request_id"
  select_release "$DEPLOY_TX_PREVIOUS_SHA"
  validate_current_environment
  validate_production_compose "$APP_ENV_FILE" legacy-adoption
  available="$(production_compose "$APP_ENV_FILE" config --services)"
  for service in support-agent backend frontend budget-relay nginx; do
    if grep -qx "$service" <<<"$available"; then services+=("$service"); fi
  done
  for service in backend frontend budget-relay nginx; do
    grep -qx "$service" <<<"$available" || die "rollback release misses a required service"
  done
  if ! production_compose "$APP_ENV_FILE" up -d --no-build --remove-orphans --wait "${services[@]}"; then
    systemctl poweroff --no-block
    die "rollback Compose failed; VM powered off"
  fi
  for service in gole-backend gole-frontend gole-budget-relay gole-nginx; do
    state="$(docker inspect --format '{{.State.Status}}:{{if .State.Health}}{{.State.Health.Status}}{{else}}missing{{end}}' "$service" 2>/dev/null || true)"
    [ "$state" = running:healthy ] || {
      systemctl poweroff --no-block
      die "rollback health failed; VM powered off"
    }
  done
  curl -fsS --max-time 15 http://127.0.0.1:8080/actuator/health/readiness >/dev/null || {
    systemctl poweroff --no-block
    die "rollback readiness failed; VM powered off"
  }
  current_marker="$(read_deployed_sha 2>/dev/null || true)"
  if [ "$current_marker" != "$DEPLOY_TX_PREVIOUS_SHA" ]; then
    sha_candidate="$(mktemp)"
    printf '%s\n' "$DEPLOY_TX_PREVIOUS_SHA" > "$sha_candidate"
    atomic_install "$sha_candidate" "$DEPLOYED_SHA_FILE" 0644 root
    rm -f -- "$sha_candidate"
  fi
  if [ -e "$NGINX_TRANSACTION_FILE" ] || [ -L "$NGINX_TRANSACTION_FILE" ]; then
    finish_nginx_recovery "$request_id"
  fi
  cleanup_deployment_images "$DEPLOY_TX_TARGET" "$request_id"
  rm -f -- "$DEPLOYMENT_TRANSACTION_FILE"
}

cost_guard_fail_closed() {
  if systemctl is-active --quiet gole-cost-guard-watchdog.timer ||
    verify_budget_relay_health >/dev/null 2>&1; then
    return 0
  fi
  systemctl poweroff --no-block
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
      rm -f -- "$ENV_VERSION_FILE"
    fi
    return
  fi
  [[ "$version" =~ ^[1-9][0-9]{0,11}$ ]] || die "invalid secret version"
  version_candidate="$(mktemp)"
  printf '%s\n' "$version" > "$version_candidate"
  atomic_install "$version_candidate" "$ENV_VERSION_FILE" 0644 root
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
  while IFS='=' read -r key value; do
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
  rm -f -- "$ENV_TRANSACTION_FILE"
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
      rm -f -- "$ENV_TRANSACTION_FILE"
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
  rm -f -- "$ENV_TRANSACTION_FILE"
}

restart_strict_lkg_services() {
  local expected_sha="$1"
  create_release "$expected_sha" false
  select_release "$expected_sha"
  validate_current_production_model
  production_compose "$APP_ENV_FILE" up -d --no-build --no-deps \
    --force-recreate --wait support-agent backend nginx
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
    validate_discord_environment
    read_adoption_transaction || die "adoption recovery transaction is missing"
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

command="${1:-}"
shift || true
case "$command" in
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
    recover_deployment_transaction
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
    cleanup_deployment_images "$1" "$2"
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
  certificate-renew)
    require_argument_count 0 "$@"
    renew_certificate
    ;;
  deployment-verify-commit)
    require_argument_count 2 "$@"
    verify_full_deployment_runtime "$1" "$2"
    ;;
  deployment-verify-runtime)
    require_argument_count 1 "$@"
    [ "$(read_deployed_sha)" = "$1" ] || die "deployed SHA marker does not match"
    verify_deployment_runtime_components "$1"
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
    (
      trap 'systemctl poweroff --no-block || true' ERR
      rollback_deployment_transaction "$1"
    )
    ;;
  deployment-fail-closed)
    require_argument_count 1 "$@"
    require_deployment_transaction "$1" prepared,snapshotted,built,nginx-installed,mutated,refreshed,budget-updated,verified,marker-recorded,runtime-verified
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
