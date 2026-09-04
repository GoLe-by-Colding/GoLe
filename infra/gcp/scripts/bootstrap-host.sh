#!/usr/bin/env bash
set -Eeuo pipefail

DOMAIN="${DOMAIN:-gole.co.kr}"
APP_ROOT="${APP_ROOT:-/app}"
DEPLOY_USER="${DEPLOY_USER:-goledeploy}"
GCP_PROJECT_ID="${GCP_PROJECT_ID:-}"
GCP_VM_COST_START="${GCP_VM_COST_START:-}"
GCP_HARD_STOP_AT="${GCP_HARD_STOP_AT:-}"
GCP_CREDIT_DEADLINE="${GCP_CREDIT_DEADLINE:-}"
GCP_EXPECTED_BUDGET_ID="${GCP_EXPECTED_BUDGET_ID:-}"
GCP_EXPECTED_BILLING_ACCOUNT_ID="${GCP_EXPECTED_BILLING_ACCOUNT_ID:-}"
GCP_RUNTIME_RATE_TRANSITION_AT="${GCP_RUNTIME_RATE_TRANSITION_AT:-}"
REPOSITORY_URL="${REPOSITORY_URL:-https://github.com/GoLe-by-Colding/GoLe.git}"
BOOTSTRAP_SOURCE_SHA="${BOOTSTRAP_SOURCE_SHA:-}"
GITHUB_RUNNER_NAME="${GITHUB_RUNNER_NAME:-gole-production}"
GITHUB_RUNNER_LABELS="${GITHUB_RUNNER_LABELS:-gole-gcp-production}"
HOST_BOOTSTRAP_MARKER="/etc/gole/host-bootstrap.complete"
HOST_BOOTSTRAP_PREVIOUS="/etc/gole/.host-bootstrap.previous"
STANDARD_RUNNER_SERVICE="gole-github-runner.service"
standard_runner_was_active=0

cleanup_files=()
cleanup_dirs=()
cleanup() {
  local file
  for file in "${cleanup_files[@]}"; do
    rm -f -- "$file"
  done
  for file in "${cleanup_dirs[@]}"; do
    rm -rf -- "$file"
  done
}
trap cleanup EXIT

if [ "$(id -u)" -ne 0 ]; then
  echo "run as root" >&2
  exit 1
fi
if [ "$APP_ROOT" != "/app" ]; then
  echo "APP_ROOT must be the dedicated /app path" >&2
  exit 1
fi
if [ "${#DOMAIN}" -gt 253 ] ||
  [[ ! "$DOMAIN" =~ ^([A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?\.)+[A-Za-z]{2,63}$ ]]; then
  echo "DOMAIN must be a valid DNS hostname" >&2
  exit 1
fi
if [ "$DEPLOY_USER" != "goledeploy" ]; then
  echo "DEPLOY_USER must be the dedicated goledeploy local service account" >&2
  exit 1
fi
if [ -n "$GCP_PROJECT_ID" ] && [[ ! "$GCP_PROJECT_ID" =~ ^[a-z][a-z0-9-]{4,28}[a-z0-9]$ ]]; then
  echo "GCP_PROJECT_ID is invalid" >&2
  exit 1
fi
if [[ ! "$GCP_VM_COST_START" =~ ^2026-09-01T19:57:05\+09:00$ ]] ||
  [[ ! "$GCP_HARD_STOP_AT" =~ ^2026-10-28T01:50:00\+09:00$ ]] ||
  [[ ! "$GCP_CREDIT_DEADLINE" =~ ^2026-10-28T23:59:59\+09:00$ ]]; then
  echo "cost broker timestamps must match the reviewed production arm" >&2
  exit 1
fi
[[ "$GCP_RUNTIME_RATE_TRANSITION_AT" =~ ^2026-09-06T00:00:00\+09:00$ ]] || {
  echo "runtime rate transition must match the reviewed resize gate" >&2
  exit 1
}
[[ "$GCP_EXPECTED_BUDGET_ID" =~ ^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$ ]] || {
  echo "expected Billing Budget ID is invalid" >&2
  exit 1
}
[[ "$GCP_EXPECTED_BILLING_ACCOUNT_ID" =~ ^[0-9A-F]{6}-[0-9A-F]{6}-[0-9A-F]{6}$ ]] || {
  echo "expected billing account ID is invalid" >&2
  exit 1
}
if [ "$REPOSITORY_URL" != "https://github.com/GoLe-by-Colding/GoLe.git" ]; then
  echo "REPOSITORY_URL must remain the fixed GoLe production repository" >&2
  exit 1
fi
if [[ ! "$BOOTSTRAP_SOURCE_SHA" =~ ^[0-9a-f]{40}$ ]]; then
  echo "BOOTSTRAP_SOURCE_SHA must be a reviewed full lowercase Git SHA" >&2
  exit 1
fi
if [[ ! "$GITHUB_RUNNER_NAME" =~ ^[A-Za-z0-9._-]{1,64}$ ]]; then
  echo "GITHUB_RUNNER_NAME is invalid" >&2
  exit 1
fi
if [[ ! "$GITHUB_RUNNER_LABELS" =~ ^[A-Za-z0-9._-]+(,[A-Za-z0-9._-]+)*$ ]]; then
  echo "GITHUB_RUNNER_LABELS is invalid" >&2
  exit 1
fi
case ",$GITHUB_RUNNER_LABELS," in
  *,gole-gcp-production,*) ;;
  *)
    echo "GITHUB_RUNNER_LABELS must include gole-gcp-production" >&2
    exit 1
    ;;
esac

# Quiesce every repository runner before validating or later copying /app.
# Existing human-account runners stay stopped until the explicit registration
# migration. The standard dedicated runner is resumed only after the new
# completion marker is durable.
for runner_service_file in /etc/systemd/system/actions.runner.*.service; do
  [ -e "$runner_service_file" ] || continue
  runner_service="$(basename "$runner_service_file")"
  systemctl stop "$runner_service" || true
done

standard_runner_file="/etc/systemd/system/$STANDARD_RUNNER_SERVICE"
if [ -e "$standard_runner_file" ] || [ -L "$standard_runner_file" ]; then
  if [ ! -f "$standard_runner_file" ] || [ -L "$standard_runner_file" ] ||
    [ "$(stat -c '%U:%G:%a' "$standard_runner_file")" != "root:root:644" ] ||
    ! grep -Fqx "User=$DEPLOY_USER" "$standard_runner_file" ||
    ! grep -Fqx 'ExecStart=/opt/actions-runner/runsvc.sh' "$standard_runner_file"; then
    echo "existing standard runner service is invalid" >&2
    exit 1
  fi
  if systemctl is-active --quiet "$STANDARD_RUNNER_SERVICE"; then
    standard_runner_was_active=1
    systemctl stop "$STANDARD_RUNNER_SERVICE"
  fi
fi

# Everything copied below becomes a root trust anchor. Never consult /app's
# refs, alternates, replace objects, filters or Git configuration: that checkout
# is runner-owned and therefore attacker-controlled at this privilege boundary.
# Fetch current main into a fresh root-only bare repository and independently
# require a successful push CI before archiving it with replace refs disabled.
TRUST_ROOT="$(mktemp -d /run/gole-bootstrap.XXXXXX)"
TRUST_REPOSITORY="$(mktemp -d /run/gole-bootstrap-repository.XXXXXX)"
cleanup_dirs+=("$TRUST_ROOT")
cleanup_dirs+=("$TRUST_REPOSITORY")
chmod 0700 "$TRUST_ROOT"
chmod 0700 "$TRUST_REPOSITORY"
trusted_git=(
  env -i
  HOME=/root
  PATH=/usr/bin:/bin
  GIT_CONFIG_NOSYSTEM=1
  GIT_CONFIG_GLOBAL=/dev/null
  git
)
"${trusted_git[@]}" init --bare "$TRUST_REPOSITORY" >/dev/null
"${trusted_git[@]}" --git-dir="$TRUST_REPOSITORY" remote add origin "$REPOSITORY_URL"
"${trusted_git[@]}" --git-dir="$TRUST_REPOSITORY" fetch --no-tags --force origin \
  refs/heads/main:refs/gole/bootstrap >/dev/null 2>&1
bootstrap_head="$("${trusted_git[@]}" --git-dir="$TRUST_REPOSITORY" rev-parse refs/gole/bootstrap)"
[ "$bootstrap_head" = "$BOOTSTRAP_SOURCE_SHA" ] || {
  echo "reviewed bootstrap SHA is not current origin/main" >&2
  exit 1
}
env -i HOME=/root PATH=/usr/bin:/bin PYTHONNOUSERSITE=1 \
  python3 - "$BOOTSTRAP_SOURCE_SHA" <<'PY'
import json
import sys
import urllib.parse
import urllib.request

sha = sys.argv[1]
repo = urllib.parse.quote("GoLe-by-Colding/GoLe", safe="/")
request = urllib.request.Request(
    f"https://api.github.com/repos/{repo}/actions/workflows/ci.yml/runs"
    "?branch=main&event=push&status=completed&per_page=20",
    headers={
        "Accept": "application/vnd.github+json",
        "User-Agent": "GoLe-Root-Bootstrap/1.0",
        "X-GitHub-Api-Version": "2022-11-28",
    },
)
with urllib.request.urlopen(request, timeout=15) as response:
    runs = json.load(response).get("workflow_runs", [])
if not any(
    isinstance(run, dict)
    and run.get("head_sha") == sha
    and run.get("conclusion") == "success"
    for run in runs
):
    raise SystemExit("reviewed bootstrap SHA has no successful main push CI")
PY
"${trusted_git[@]}" --no-replace-objects --git-dir="$TRUST_REPOSITORY" \
  archive --format=tar "$BOOTSTRAP_SOURCE_SHA" |
  tar -x --no-same-owner --no-same-permissions -C "$TRUST_ROOT"
if find "$TRUST_ROOT" -xdev -type l -print -quit | grep -q .; then
  echo "reviewed bootstrap snapshot must not contain symbolic links" >&2
  exit 1
fi
chown -R root:root "$TRUST_ROOT"
chmod -R go-w "$TRUST_ROOT"

# Block metadata before apt or Docker can start/restart a runner or container.
# The raw PREROUTING rule covers every container namespace; OUTPUT permits only
# host UID 0, which is subsequently constrained by the fixed broker protocol.
bash "$TRUST_ROOT/infra/gcp/scripts/metadata-firewall.sh"

# During an explicit policy upgrade, move the old completion marker aside
# before mutating the host. A mid-upgrade reboot will therefore rerun the
# Terraform-pinned bootstrap rather than trusting a partially updated policy.
if [ -e "$HOST_BOOTSTRAP_MARKER" ] || [ -L "$HOST_BOOTSTRAP_MARKER" ]; then
  if [ ! -f "$HOST_BOOTSTRAP_MARKER" ] || [ -L "$HOST_BOOTSTRAP_MARKER" ] ||
    [ "$(stat -c '%U:%G:%a' "$HOST_BOOTSTRAP_MARKER")" != "root:root:644" ] ||
    ! grep -Eq '^bootstrap_source_sha=[0-9a-f]{40}$' "$HOST_BOOTSTRAP_MARKER"; then
    echo "existing host bootstrap completion marker is invalid" >&2
    exit 1
  fi
  install -d -m 0755 -o root -g root /etc/gole
  mv -f -- "$HOST_BOOTSTRAP_MARKER" "$HOST_BOOTSTRAP_PREVIOUS"
fi

export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y ca-certificates curl git gnupg iptables jq openssl python3 sudo tar util-linux

install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc
# The Ubuntu image supplies this runtime-owned release metadata file.
# shellcheck disable=SC1091
. /etc/os-release
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu ${UBUNTU_CODENAME:-$VERSION_CODENAME} stable" > /etc/apt/sources.list.d/docker.list

gcloud_key="$(mktemp)"
gcloud_keyring="$(mktemp)"
cleanup_files+=("$gcloud_key" "$gcloud_keyring")
curl -fsSL https://packages.cloud.google.com/apt/doc/apt-key.gpg -o "$gcloud_key"
gpg --batch --yes --dearmor --output "$gcloud_keyring" "$gcloud_key"
install -m 0644 "$gcloud_keyring" /usr/share/keyrings/cloud.google.gpg
echo "deb [signed-by=/usr/share/keyrings/cloud.google.gpg] https://packages.cloud.google.com/apt cloud-sdk main" > /etc/apt/sources.list.d/google-cloud-sdk.list

apt-get update
apt-get install -y \
  containerd.io \
  docker-buildx-plugin \
  docker-ce \
  docker-ce-cli \
  docker-compose-plugin \
  google-cloud-cli

if ! id "$DEPLOY_USER" >/dev/null 2>&1; then
  useradd --system --create-home --home-dir "/home/$DEPLOY_USER" \
    --shell /bin/bash --user-group "$DEPLOY_USER"
fi
if getent group golecloud >/dev/null 2>&1; then
  [ "$(getent group golecloud | cut -d: -f3)" = "10001" ] || {
    echo "existing golecloud group has an unexpected GID" >&2
    exit 1
  }
elif getent group 10001 >/dev/null 2>&1; then
  echo "GID 10001 is already used by another host group" >&2
  exit 1
else
  groupadd --system --gid 10001 golecloud
fi
DEPLOY_GROUP="$(id -gn "$DEPLOY_USER")"
DEPLOY_HOME="$(getent passwd "$DEPLOY_USER" | cut -d: -f6)"
DEPLOY_SHELL="$(getent passwd "$DEPLOY_USER" | cut -d: -f7)"
if [[ ! "$DEPLOY_GROUP" =~ ^[a-z_][a-z0-9_-]{0,31}$ ]] ||
  [ "$(id -u "$DEPLOY_USER")" -eq 0 ] ||
  [ "$DEPLOY_HOME" != "/home/$DEPLOY_USER" ] || [ "$DEPLOY_SHELL" != /bin/bash ]; then
  echo "existing deploy account does not have the expected UID, group, home, or shell" >&2
  exit 1
fi
# Keep the repository runner distinct from OS Login/IAM users. In particular,
# Docker, lxd and google-sudoers are root-equivalent and must never be inherited.
usermod --groups "$DEPLOY_GROUP" "$DEPLOY_USER"
install -d -m 0750 -o "$DEPLOY_USER" -g "$DEPLOY_GROUP" "$DEPLOY_HOME"

# Recreate the runner checkout from the fixed origin without ever promoting its
# Git database into root policy. Preserve an existing checkout as a root-only
# rollback artifact instead of deleting it in place.
if mountpoint -q "$APP_ROOT" 2>/dev/null || [ -L "$APP_ROOT" ]; then
  echo "/app must not be a mount point or symbolic link" >&2
  exit 1
fi
if [ -e "$APP_ROOT" ]; then
  app_checkout_backup="/var/backups/gole-app-checkouts/app.$(date -u +%Y%m%dT%H%M%SZ).${BOOTSTRAP_SOURCE_SHA}"
  install -d -m 0700 -o root -g root /var/backups/gole-app-checkouts
  [ ! -e "$app_checkout_backup" ] || {
    echo "runner checkout backup already exists" >&2
    exit 1
  }
  mv -- "$APP_ROOT" "$app_checkout_backup"
  chown -R root:root "$app_checkout_backup"
  chmod -R go-rwx "$app_checkout_backup"
fi
runuser -u "$DEPLOY_USER" -- env -i HOME="$DEPLOY_HOME" PATH=/usr/bin:/bin \
  GIT_CONFIG_NOSYSTEM=1 GIT_CONFIG_GLOBAL=/dev/null \
  git clone --no-tags "$REPOSITORY_URL" "$APP_ROOT" >/dev/null 2>&1
runuser -u "$DEPLOY_USER" -- env -i HOME="$DEPLOY_HOME" PATH=/usr/bin:/bin \
  GIT_CONFIG_NOSYSTEM=1 GIT_CONFIG_GLOBAL=/dev/null \
  git -C "$APP_ROOT" checkout --detach "$BOOTSTRAP_SOURCE_SHA" >/dev/null 2>&1
[ "$(runuser -u "$DEPLOY_USER" -- env -i HOME="$DEPLOY_HOME" PATH=/usr/bin:/bin \
  GIT_CONFIG_NOSYSTEM=1 GIT_CONFIG_GLOBAL=/dev/null \
  git --no-replace-objects -C "$APP_ROOT" rev-parse HEAD)" = "$BOOTSTRAP_SOURCE_SHA" ] || {
  echo "runner checkout did not resolve the reviewed SHA" >&2
  exit 1
}

install -d -m 0755 /etc/gole /opt/gole /usr/local/libexec/gole
cat > /etc/tmpfiles.d/gole.conf <<EOF
f /run/lock/gole-production-rollout.lock 0660 root $DEPLOY_GROUP -
EOF
chown root:root /etc/tmpfiles.d/gole.conf
chmod 0644 /etc/tmpfiles.d/gole.conf
systemd-tmpfiles --create /etc/tmpfiles.d/gole.conf
infra_candidate="$(mktemp /etc/gole/.infra.env.XXXXXX)"
cleanup_files+=("$infra_candidate")
if [ -e /etc/gole/infra.env ] || [ -L /etc/gole/infra.env ]; then
  if [ ! -f /etc/gole/infra.env ] || [ -L /etc/gole/infra.env ] ||
    [ "$(stat -c '%U:%G:%a' /etc/gole/infra.env)" != "root:root:600" ]; then
    echo "existing infrastructure environment is invalid" >&2
    exit 1
  fi
  minio_user="$(awk -F= '$1 == "MINIO_ROOT_USER" { print substr($0, index($0, "=") + 1) }' /etc/gole/infra.env)"
  minio_password="$(awk -F= '$1 == "MINIO_ROOT_PASSWORD" { print substr($0, index($0, "=") + 1) }' /etc/gole/infra.env)"
  [ "$(grep -Ec '^MINIO_ROOT_USER=' /etc/gole/infra.env)" -eq 1 ] &&
    [ "$(grep -Ec '^MINIO_ROOT_PASSWORD=' /etc/gole/infra.env)" -eq 1 ] || {
    echo "existing infrastructure credentials are incomplete" >&2
    exit 1
  }
else
  minio_user="gole-$(openssl rand -hex 8)"
  minio_password="$(openssl rand -base64 36 | tr -d '\n')"
fi
[[ "$minio_user" =~ ^[A-Za-z0-9._-]{8,64}$ ]] || {
  echo "existing MinIO user is invalid" >&2
  exit 1
}
[[ "$minio_password" =~ ^[A-Za-z0-9+/=]{32,128}$ ]] || {
  echo "existing MinIO password is invalid" >&2
  exit 1
}
printf '%s\n' \
  "MINIO_ROOT_USER=$minio_user" \
  "MINIO_ROOT_PASSWORD=$minio_password" \
  'GCP_BUDGET_PUBSUB_SUBSCRIPTION=gole-billing-budget-discord' \
  "GCP_PROJECT_ID=$GCP_PROJECT_ID" \
  'GCP_CREDIT_AMOUNT_KRW=395600.60' \
  'GCP_CREDIT_DEADLINE=2026-10-28' \
  'GCP_FIXED_HOURLY_COST_KRW=153.390555330' \
  'GCP_HARD_STOP_ENABLED=true' \
  'GCP_HARD_STOP_DRY_RUN=false' \
  'GCP_HARD_STOP_BILLING_COST_KRW=320000' \
  'GCP_HARD_STOP_MIN_RESERVE_KRW=75000' \
  'GCP_HARD_STOP_ALL_IN_COST_KRW=350000' \
  'GCP_COST_GUARD_WARNING_KRW=330000' \
  'GCP_COST_GUARD_DANGER_KRW=340000' \
  'GCP_HARD_STOP_NETWORK_GIB=30' \
  'GCP_COST_GUARD_NETWORK_WARNING_GIB=15' \
  'GCP_COST_GUARD_NETWORK_DANGER_GIB=25' \
  'GCP_HARD_STOP_MAX_RUNTIME_HOURS=1350' \
  'GCP_COST_GUARD_RUNTIME_WARNING_HOURS=1250' \
  'GCP_COST_GUARD_RUNTIME_DANGER_HOURS=1320' \
  'GCP_HARD_STOP_EXPECTED_BUDGET_KRW=370000' \
  "GCP_HARD_STOP_BUDGET_ID=$GCP_EXPECTED_BUDGET_ID" \
  "GCP_HARD_STOP_BILLING_ACCOUNT_ID=$GCP_EXPECTED_BILLING_ACCOUNT_ID" \
  'GCP_HARD_STOP_BUDGET_DISPLAY_NAME=GoLe production credit guard' \
  'GCP_HARD_STOP_PERIOD_START=2026-09-01' \
  "GCP_VM_COST_START=$GCP_VM_COST_START" \
  "GCP_HARD_STOP_AT=$GCP_HARD_STOP_AT" \
  'GCP_HARD_STOP_ARM_ID=2026-09-e2-standard-2-ipv4-v3' \
  'GCP_INSTANCE_ZONE=asia-northeast3-a' \
  'GCP_INSTANCE_NAME=gole-production' \
  'GCP_VAT_RATE=0.10' \
  'GCP_NETWORK_EGRESS_KRW_PER_GIB=318.154399937' \
  'GCP_STOPPED_RESOURCE_HOURLY_COST_KRW=45.725095000' \
  'GCP_COST_GUARD_INTERVAL_SECONDS=10' \
  'GCP_HARD_STOP_RETRY_SECONDS=300' \
  'BUDGET_HTTP_TIMEOUT_SECONDS=5' > "$infra_candidate"
chown root:root "$infra_candidate"
chmod 0600 "$infra_candidate"
mv -f -- "$infra_candidate" /etc/gole/infra.env
if [ -e /etc/gole/gole.env ] || [ -L /etc/gole/gole.env ]; then
  if [ ! -f /etc/gole/gole.env ] || [ -L /etc/gole/gole.env ]; then
    echo "existing application environment path is invalid" >&2
    exit 1
  fi
  chown root:root /etc/gole/gole.env
  chmod 0600 /etc/gole/gole.env
fi
if [ -e /etc/gole/gole.env.version ] || [ -L /etc/gole/gole.env.version ]; then
  if [ ! -f /etc/gole/gole.env.version ] || [ -L /etc/gole/gole.env.version ] ||
    [[ ! "$(cat /etc/gole/gole.env.version)" =~ ^[1-9][0-9]{0,11}$ ]]; then
    echo "existing application environment version marker is invalid" >&2
    exit 1
  fi
  chown root:root /etc/gole/gole.env.version
  chmod 0644 /etc/gole/gole.env.version
fi

printf '%s:%s\n' "$DEPLOY_USER" "$DEPLOY_GROUP" > /etc/gole/deploy-user
chown root:root /etc/gole/deploy-user
chmod 0644 /etc/gole/deploy-user

cat > /etc/gole/github-runner-bootstrap.conf <<EOF
repository_url=$REPOSITORY_URL
runner_name=$GITHUB_RUNNER_NAME
runner_labels=$GITHUB_RUNNER_LABELS
EOF
chown root:root /etc/gole/github-runner-bootstrap.conf
chmod 0644 /etc/gole/github-runner-bootstrap.conf

install -m 0755 "$TRUST_ROOT/infra/gcp/scripts/gole-hostctl.sh" /usr/local/sbin/gole-hostctl
install -m 0755 -o root -g root \
  "$TRUST_ROOT/infra/gcp/scripts/migrate-and-adopt-existing.sh" \
  /usr/local/sbin/gole-migrate-and-adopt-existing
install -m 0755 -o root -g root \
  "$TRUST_ROOT/infra/gcp/scripts/bootstrap-production-env.sh" \
  /usr/local/sbin/gole-bootstrap-production-env
install -m 0755 -o root -g root \
  "$TRUST_ROOT/infra/gcp/scripts/register-github-runner.sh" \
  /usr/local/sbin/gole-register-github-runner
install -m 0755 -o root -g root \
  "$TRUST_ROOT/infra/gcp/scripts/verify-host-bootstrap.sh" \
  /usr/local/sbin/gole-verify-host-bootstrap
install -m 0755 -o root -g root \
  "$TRUST_ROOT/infra/gcp/scripts/validate-production-env.py" \
  /usr/local/libexec/gole/validate-production-env.py
install -m 0755 -o root -g root \
  "$TRUST_ROOT/infra/gcp/scripts/validate-production-compose.py" \
  /usr/local/libexec/gole/validate-production-compose.py
sudoers_candidate="$(mktemp)"
cleanup_files+=("$sudoers_candidate")
sed "s/__DEPLOY_USER__/${DEPLOY_USER}/g" \
  "$TRUST_ROOT/infra/gcp/sudoers/gole-deploy" > "$sudoers_candidate"
chmod 0440 "$sudoers_candidate"
visudo -cf "$sudoers_candidate" >/dev/null
install -m 0440 -o root -g root "$sudoers_candidate" /etc/sudoers.d/gole-deploy

if [ -n "$GCP_PROJECT_ID" ]; then
  cat > /etc/profile.d/gole-gcloud.sh <<EOF
export CLOUDSDK_CORE_PROJECT='$GCP_PROJECT_ID'
export CLOUDSDK_CORE_DISABLE_PROMPTS='1'
EOF
  chmod 0644 /etc/profile.d/gole-gcloud.sh
fi

env DOMAIN="$DOMAIN" APP_ROOT="$APP_ROOT" GOLE_TRUSTED_TEMPLATE_ROOT="$TRUST_ROOT" \
  bash "$TRUST_ROOT/infra/gcp/scripts/ensure-nginx-config.sh"
install -m 0644 "$TRUST_ROOT/infra/gcp/systemd/gole-cert-renew.service" /etc/systemd/system/gole-cert-renew.service
install -m 0644 "$TRUST_ROOT/infra/gcp/systemd/gole-cert-renew.timer" /etc/systemd/system/gole-cert-renew.timer
install -m 0755 "$TRUST_ROOT/infra/gcp/scripts/cost-guard-watchdog.sh" /usr/local/sbin/gole-cost-guard-watchdog
install -m 0644 "$TRUST_ROOT/infra/gcp/systemd/gole-cost-guard-watchdog.service" /etc/systemd/system/gole-cost-guard-watchdog.service
install -m 0644 "$TRUST_ROOT/infra/gcp/systemd/gole-cost-guard-watchdog.timer" /etc/systemd/system/gole-cost-guard-watchdog.timer
install -m 0755 "$TRUST_ROOT/infra/gcp/scripts/metadata-firewall.sh" /usr/local/sbin/gole-metadata-firewall
install -m 0644 "$TRUST_ROOT/infra/gcp/systemd/gole-metadata-firewall.service" /etc/systemd/system/gole-metadata-firewall.service
install -m 0755 "$TRUST_ROOT/infra/gcp/scripts/cloud-broker.py" /usr/local/libexec/gole/cloud-broker.py
install -m 0755 "$TRUST_ROOT/infra/gcp/scripts/verify-github-release.py" /usr/local/libexec/gole/verify-github-release.py
install -m 0644 "$TRUST_ROOT/infra/gcp/systemd/gole-cloud-broker.service" /etc/systemd/system/gole-cloud-broker.service
install -m 0755 "$TRUST_ROOT/infra/gcp/scripts/backup-data.sh" /usr/local/sbin/gole-backup-data
install -m 0755 "$TRUST_ROOT/infra/gcp/scripts/notify-backup-failure.py" /usr/local/libexec/gole/notify-backup-failure.py
for backup_unit in gole-data-backup.service gole-data-backup.timer gole-data-backup-failure.service; do
  install -m 0644 "$TRUST_ROOT/infra/gcp/systemd/$backup_unit" "/etc/systemd/system/$backup_unit"
done

broker_config="$(mktemp /etc/gole/.cloud-broker.conf.XXXXXX)"
cleanup_files+=("$broker_config")
printf '%s\n' \
  "PROJECT_ID=$GCP_PROJECT_ID" \
  'SUBSCRIPTION=gole-billing-budget-discord' \
  "VM_COST_START=$GCP_VM_COST_START" \
  "HARD_STOP_AT=$GCP_HARD_STOP_AT" \
  'MAX_RUNTIME_HOURS=1350' \
  'FIXED_HOURLY_COST_KRW=153.390555330' \
  'HIGH_RATE_HOURLY_COST_KRW=240.749900000' \
  "RATE_TRANSITION_AT=$GCP_RUNTIME_RATE_TRANSITION_AT" \
  'EXPECTED_MACHINE_TYPE=e2-standard-2' \
  'SNAPSHOT_MAX_HOURLY_COST_KRW=39.041010000' \
  'SNAPSHOT_RETENTION_HOURS=72' \
  'MANUAL_SNAPSHOT_HOURLY_COST_KRW=13.013670000' \
  'STOPPED_RESOURCE_HOURLY_COST_KRW=45.725095000' \
  "CREDIT_DEADLINE=$GCP_CREDIT_DEADLINE" \
  'ALL_IN_LIMIT_KRW=350000' \
  'BILLING_LIMIT_KRW=320000' \
  'NETWORK_LIMIT_GIB=30' \
  'NETWORK_PERIOD_BASELINE_BYTES=536870912' \
  'NETWORK_EGRESS_KRW_PER_GIB=318.154399937' \
  'VAT_RATE=0.10' \
  'BUDGET_DISPLAY_NAME=GoLe production credit guard' \
  'BUDGET_AMOUNT_KRW=370000' \
  'BUDGET_PERIOD_START=2026-09-01' \
  "EXPECTED_BUDGET_ID=$GCP_EXPECTED_BUDGET_ID" \
  "EXPECTED_BILLING_ACCOUNT_ID=$GCP_EXPECTED_BILLING_ACCOUNT_ID" > "$broker_config"
chown root:root "$broker_config"
chmod 0600 "$broker_config"
mv -f -- "$broker_config" /etc/gole/cloud-broker.conf
systemctl daemon-reload
systemctl enable --now gole-cert-renew.timer
systemctl enable --now docker
systemctl enable --now gole-metadata-firewall.service
systemctl enable --now gole-cloud-broker.service
systemctl enable --now gole-data-backup.timer

bootstrap_marker="$(mktemp /etc/gole/.host-bootstrap.complete.XXXXXX)"
cleanup_files+=("$bootstrap_marker")
printf 'bootstrap_source_sha=%s\n' "$BOOTSTRAP_SOURCE_SHA" > "$bootstrap_marker"
chown root:root "$bootstrap_marker"
chmod 0644 "$bootstrap_marker"
rm -f -- "$HOST_BOOTSTRAP_PREVIOUS"
mv -f -- "$bootstrap_marker" "$HOST_BOOTSTRAP_MARKER"
if [ "$standard_runner_was_active" -eq 1 ]; then
  systemctl start "$STANDARD_RUNNER_SERVICE"
fi

echo "Host bootstrap complete. Register the dedicated runner, then bootstrap or adopt the environment."
