#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
cd "$ROOT"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

scripts=(
  infra/gcp/scripts/backup-data.sh
  infra/gcp/scripts/bootstrap-host.sh
  infra/gcp/scripts/bootstrap-terraform-state.sh
  infra/gcp/scripts/cost-guard-watchdog.sh
  infra/gcp/scripts/ensure-nginx-config.sh
  infra/gcp/scripts/prepare-nginx-config.sh
  infra/gcp/scripts/gole-hostctl.sh
  infra/gcp/scripts/metadata-firewall.sh
  infra/gcp/scripts/register-github-runner.sh
  infra/gcp/scripts/setup-budget-alerts.sh
  infra/gcp/scripts/verify-host-bootstrap.sh
  infra/gcp/scripts/verify-operator-access.sh
  infra/gcp/scripts/apply-secret-env.sh
  infra/gcp/scripts/bootstrap-production-env.sh
  infra/gcp/scripts/migrate-and-adopt-existing.sh
  infra/gcp/scripts/verify-snapshot-policy.sh
)
for script in "${scripts[@]}"; do
  bash -n "$script"
done

if grep -REq 'variable[[:space:]]+"(github|runner)[^"]*token"' infra/gcp/terraform; then
  fail "Terraform must not accept a GitHub runner token"
fi
if grep -Eq '(GITHUB|RUNNER)_[A-Z_]*TOKEN=' infra/gcp/terraform/main.tf; then
  fail "Terraform startup metadata must not carry a GitHub token"
fi
grep -q 'ubuntu-2404-noble-amd64-v20260826' infra/gcp/terraform/variables.tf ||
  fail "Terraform must default to the tested immutable Ubuntu image"
grep -A8 'variable "machine_type"' infra/gcp/terraform/variables.tf |
  grep -q 'default[[:space:]]*=[[:space:]]*"e2-standard-2"' ||
  fail "Terraform must use the reviewed 2 vCPU/8 GiB production shape"
if grep -REq --include='*.tf' 'ubuntu-2404-lts-amd64|/families/' infra/gcp/terraform; then
  fail "Terraform must not use a mutable Compute Engine image family"
fi
grep -q 'deletion_protection[[:space:]]*=[[:space:]]*true' infra/gcp/terraform/main.tf ||
  fail "production VM deletion protection must be enabled"
grep -q 'name[[:space:]]*=[[:space:]]*var.static_ip_name' infra/gcp/terraform/main.tf ||
  fail "production static address must use the imported resource name variable"
test "$(grep -c 'network_tier[[:space:]]*=[[:space:]]*"STANDARD"' infra/gcp/terraform/main.tf)" -eq 2 ||
  fail "reserved address and VM access config must both preserve STANDARD network tier"
grep -A8 'variable "static_ip_name"' infra/gcp/terraform/variables.tf |
  grep -q 'default[[:space:]]*=[[:space:]]*"he-testbed-feedback-ip"' ||
  fail "the live reserved address name must be the Terraform default"
grep -q 'google_compute_disk_resource_policy_attachment.*daily_boot_disk_snapshots' \
  infra/gcp/terraform/main.tf || fail "daily boot-disk snapshot policy must be attached"
grep -q 'max_retention_days[[:space:]]*=[[:space:]]*var.snapshot_retention_days' \
  infra/gcp/terraform/main.tf || fail "snapshot retention must be declared in Terraform"
if grep -REq --include='*.tf' \
  'google_secret_manager_secret_version|secret_data[[:space:]]*=' infra/gcp/terraform; then
  fail "Terraform must not manage Secret Manager payloads or versions"
fi
grep -q 'backend "gcs"' infra/gcp/terraform/versions.tf ||
  fail "Terraform production state must use the locking GCS backend"
grep -q -- '--public-access-prevention' infra/gcp/scripts/bootstrap-terraform-state.sh ||
  fail "Terraform state bucket bootstrap must enforce public access prevention"
grep -q -- '--uniform-bucket-level-access' infra/gcp/scripts/bootstrap-terraform-state.sh ||
  fail "Terraform state bucket bootstrap must enforce uniform bucket-level access"
grep -q -- '--versioning' infra/gcp/scripts/bootstrap-terraform-state.sh ||
  fail "Terraform state bucket bootstrap must enable object versioning"
grep -q 'google_secret_manager_secret_iam_member.*production_env_accessor' infra/gcp/terraform/main.tf ||
  fail "Terraform must grant resource-level production secret access"
grep -q 'roles/secretmanager.secretAccessor' infra/gcp/terraform/main.tf ||
  fail "runtime identity must receive Secret Manager accessor on the production secret"

while IFS= read -r image_reference; do
  case "$image_reference" in
    gole/*:local) continue ;;
  esac
  [[ "$image_reference" =~ @sha256:[0-9a-f]{64}$ ]] ||
    fail "production Compose image is not digest-pinned: $image_reference"
done < <(sed -n 's/^[[:space:]]*image:[[:space:]]*//p' infra/gcp/docker-compose.yml)
for dockerfile in \
  infra/gcp/docker/api.Dockerfile \
  infra/gcp/docker/web.Dockerfile \
  infra/gcp/budget-relay/Dockerfile \
  apps/support-agent/Dockerfile; do
  while IFS= read -r base_image; do
    [[ "$base_image" =~ @sha256:[0-9a-f]{64}$ ]] ||
      fail "production Dockerfile base is not digest-pinned: $dockerfile ($base_image)"
  done < <(awk '$1 == "FROM" {print $2}' "$dockerfile")
done
grep -q 'google-cloud-cli' infra/gcp/scripts/bootstrap-host.sh ||
  fail "host bootstrap must install Google Cloud CLI"
grep -q '/usr/local/libexec/gole/validate-production-env.py' infra/gcp/scripts/bootstrap-host.sh ||
  fail "host bootstrap must install a root-owned production validator"
grep -q '/usr/local/sbin/gole-verify-host-bootstrap' infra/gcp/scripts/bootstrap-host.sh ||
  fail "host bootstrap must install a root-owned verification command"
grep -q '/usr/local/libexec/gole/validate-production-compose.py' infra/gcp/scripts/bootstrap-host.sh ||
  fail "host bootstrap must install the root-owned Compose privilege validator"
grep -q '/usr/local/sbin/gole-migrate-and-adopt-existing' infra/gcp/scripts/bootstrap-host.sh ||
  fail "host bootstrap must install the existing deployment migration helper"
grep -q '/usr/local/sbin/gole-register-github-runner' infra/gcp/scripts/bootstrap-host.sh ||
  fail "host bootstrap must install the root-owned runner registration helper"
grep -q '/usr/local/sbin/gole-bootstrap-production-env' infra/gcp/scripts/bootstrap-host.sh ||
  fail "host bootstrap must install the root-owned initial environment helper"
grep -q 'APP_ROOT.*dedicated /app' infra/gcp/scripts/bootstrap-host.sh ||
  fail "host bootstrap must reject broad APP_ROOT values"
grep -q 'gole-production-rollout.lock 0660 root' infra/gcp/scripts/bootstrap-host.sh ||
  fail "host bootstrap must create the shared production rollout lock"
grep -q 'flock -n 7' infra/gcp/scripts/apply-secret-env.sh ||
  fail "Secret Sync must serialize the full host rollout"
grep -Fq 'sudo -n "$HOSTCTL" secret-sync "$SECRET_VERSION" "$REQUEST_ID"' \
  infra/gcp/scripts/apply-secret-env.sh ||
  fail "Secret Sync must use only the root-owned fixed transaction"
if grep -Eq 'gcloud|docker|validate-production-env|/app/' \
  infra/gcp/scripts/apply-secret-env.sh; then
  fail "Secret Sync wrapper must not fetch payloads or trust runner-owned code"
fi
grep -q 'ensure-nginx-config.sh' infra/gcp/scripts/bootstrap-host.sh ||
  fail "host bootstrap must delegate idempotent Nginx config handling"
grep -q 'Preserving existing Nginx configuration' infra/gcp/scripts/ensure-nginx-config.sh ||
  fail "host bootstrap must preserve an issued HTTPS configuration on reboot"
if ! grep -q 'certificate_root=.*/gole_letsencrypt' infra/gcp/scripts/ensure-nginx-config.sh ||
  ! grep -q 'certificate_root/fullchain.pem' infra/gcp/scripts/ensure-nginx-config.sh; then
  fail "host bootstrap must recover HTTPS config when a certificate lineage exists"
fi
if rg -n -i 'access-control-|proxy_hide_header' infra/gcp/nginx-*.conf.template; then
  fail "Nginx must not own application CORS or response-header policy"
fi
while IFS= read -r header_directive; do
  grep -Eq 'add_header[[:space:]]+Strict-Transport-Security[[:space:]]+"max-age=31536000"[[:space:]]+always;' \
    <<<"$header_directive" || fail "Nginx may add only the TLS-specific HSTS response header"
done < <(grep -hE '^[[:space:]]*add_header[[:space:]]' infra/gcp/nginx-*.conf.template)
[ "$(grep -hc 'Strict-Transport-Security' infra/gcp/nginx-https.conf.template)" -ge 1 ] ||
  fail "HTTPS Nginx template must emit HSTS"
grep -q 'gole-gcp-production' infra/gcp/terraform/variables.tf ||
  fail "Terraform must configure the production runner label"
grep -q 'default[[:space:]]*=[[:space:]]*"goledeploy"' infra/gcp/terraform/variables.tf ||
  fail "Terraform must use the dedicated local runner account"
if grep -Eq 'usermod[[:space:]].*(-aG|--append).*(docker|lxd|google-sudoers)' \
  infra/gcp/scripts/bootstrap-host.sh; then
  fail "runner bootstrap must not grant a root-equivalent supplemental group"
fi
grep -q 'usermod --groups "\$DEPLOY_GROUP"' infra/gcp/scripts/bootstrap-host.sh ||
  fail "runner bootstrap must remove supplemental groups"
grep -q 'sudo -n /usr/bin/true' infra/gcp/scripts/verify-host-bootstrap.sh ||
  fail "host verification must prove unrestricted passwordless sudo is denied"
grep -q 'docker info' infra/gcp/scripts/verify-host-bootstrap.sh ||
  fail "host verification must prove the runner cannot reach the Docker socket"
grep -q 'billing-budget-alert@system.gserviceaccount.com' infra/gcp/terraform/main.tf ||
  fail "Terraform must grant Cloud Billing permission to publish budget events"
grep -q 'billing-budget-alert@system.gserviceaccount.com' infra/gcp/scripts/setup-budget-alerts.sh ||
  fail "manual budget setup must grant Cloud Billing publisher permission"
if rg -n 'compute\.instances\.stop|goleProductionInstanceStopper' \
  infra/gcp/terraform infra/gcp/scripts/setup-budget-alerts.sh; then
  fail "runtime identity must not receive Compute Engine stop authority"
fi
grep -A80 'resource "google_billing_budget"' infra/gcp/terraform/main.tf |
  grep -q 'google_pubsub_topic_iam_member.billing_budget_publisher' ||
  fail "Budget creation must wait for Cloud Billing publisher permission"
grep -q -- '--token-stdin' infra/gcp/scripts/register-github-runner.sh ||
  fail "runner registration must require token stdin"
grep -q 'RUNNER_SHA256_X64=' infra/gcp/scripts/register-github-runner.sh ||
  fail "runner archive checksum must be pinned"
grep -q 'ACTIONS_RUNNER_INPUT_TOKEN=' infra/gcp/scripts/register-github-runner.sh ||
  fail "runner token must be passed through GitHub's masked environment input"
grep -q -- '--whitelist-environment=ACTIONS_RUNNER_INPUT_TOKEN' \
  infra/gcp/scripts/register-github-runner.sh ||
  fail "runner registration must not preserve the root environment"
if grep -q -- '--preserve-environment' infra/gcp/scripts/register-github-runner.sh; then
  fail "runner registration must not copy the root environment into the service account"
fi
if grep -q -- '--token.*RUNNER_TOKEN' infra/gcp/scripts/register-github-runner.sh; then
  fail "runner token must not appear in a process command line"
fi
if grep -Eq '(^|[[:space:]])\./svc\.sh([[:space:]]|$)' \
  infra/gcp/scripts/register-github-runner.sh; then
  fail "root runner registration must not execute a runner-writable svc.sh"
fi
grep -q 'RUNNER_SERVICE="gole-github-runner.service"' \
  infra/gcp/scripts/register-github-runner.sh ||
  fail "runner service must use the fixed root-owned unit"
grep -Fq '"$HOSTCTL" deployment-migrate-adopt-secret' \
  infra/gcp/scripts/migrate-and-adopt-existing.sh ||
  fail "adoption migration must delegate exact-version fetch to the root host transaction"
if grep -Eq 'gcloud|CANDIDATE|/app/' infra/gcp/scripts/migrate-and-adopt-existing.sh; then
  fail "adoption wrapper must not fetch payloads or trust runner-owned code"
fi
if grep -Eq '(^|[[:space:]])(source|\.)[[:space:]]+.*(gole\.env|CANDIDATE)' \
  infra/gcp/scripts/migrate-and-adopt-existing.sh; then
  fail "adoption migration must never source the existing or candidate payload"
fi
if grep -Eq '(^|[[:space:]])docker([[:space:]]|$)' \
  infra/gcp/scripts/migrate-and-adopt-existing.sh; then
  fail "adoption migration must not bypass the root-owned Compose helper"
fi

if grep -Eq 'NOPASSWD:[[:space:]]*ALL([[:space:]]|$)' infra/gcp/sudoers/gole-deploy; then
  fail "deploy user must not receive passwordless unrestricted sudo"
fi
if grep -v '^[[:space:]]*#' infra/gcp/sudoers/gole-deploy | grep -q '\*'; then
  fail "sudo allowlist must not contain wildcard command arguments"
fi
grep -q '/usr/local/sbin/gole-hostctl' infra/gcp/sudoers/gole-deploy ||
  fail "sudo allowlist must expose the fixed host helper"
if grep -Eq '/usr/bin/(install|systemctl)' infra/gcp/sudoers/gole-deploy; then
  fail "sudo allowlist must not expose raw install or systemctl commands"
fi
for hostctl_operation in \
  certificate-renew \
  deployment-read-sha \
  deployment-record-sha \
  deployment-is-uninitialized \
  deployment-begin \
  deployment-recover \
  deployment-compose-build \
  deployment-compose-up \
  deployment-compose-ps \
  deployment-images-snapshot \
  deployment-images-cleanup \
  deployment-budget-healthy \
  discord-overlay-install \
  discord-overlay-verify \
  deployment-verify-candidate-runtime \
  deployment-verify-commit \
  deployment-verify-runtime \
  deployment-finalize \
  deployment-finalize-partial \
  deployment-rollback \
  deployment-fail-closed \
  nginx-transaction-begin \
  nginx-transaction-commit \
  nginx-transaction-finalize \
  secret-sync \
  watchdog-active \
  watchdog-install \
  cost-guard-fail-closed; do
  grep -q "$hostctl_operation" infra/gcp/scripts/gole-hostctl.sh ||
    fail "host helper is missing $hostctl_operation"
done
grep -q '/usr/local/sbin/gole-hostctl discord-overlay-install ""' \
  infra/gcp/sudoers/gole-deploy ||
  fail "Discord secret overlay must use an exact no-argument sudo command"
grep -q -- '--env-file "$DISCORD_ENV_FILE"' infra/gcp/scripts/gole-hostctl.sh ||
  fail "root Compose must load the validated Discord overlay"

if rg -n '^(ExecStart|ExecStop|ExecReload)=/app/' infra/gcp/systemd; then
  fail "root-owned systemd units must not execute runner-writable /app files"
fi
grep -q '^ExecStart=/usr/local/sbin/gole-hostctl certificate-renew$' \
  infra/gcp/systemd/gole-cert-renew.service ||
  fail "certificate renewal must use the root-owned validated dispatcher"

for removed_hostctl_operation in \
  env-current-sha256 env-backup env-install env-restore env-record-version; do
  if grep -Eq "^[[:space:]]*${removed_hostctl_operation}\\)" \
    infra/gcp/scripts/gole-hostctl.sh; then
    fail "unsafe raw host operation remains exposed: $removed_hostctl_operation"
  fi
done

expected_hostctl_reference="\"\$HOSTCTL\""
while IFS= read -r sudo_line; do
  grep -Fq "$expected_hostctl_reference" <<<"$sudo_line" ||
    fail "apply-secret-env.sh bypasses the restricted host helper: $sudo_line"
done < <(grep 'sudo -n' infra/gcp/scripts/apply-secret-env.sh)

sudoers_candidate="$(mktemp)"
trap 'rm -f -- "$sudoers_candidate"' EXIT
sed "s/__DEPLOY_USER__/$(id -un)/g" infra/gcp/sudoers/gole-deploy > "$sudoers_candidate"
if command -v visudo >/dev/null 2>&1; then
  visudo -cf "$sudoers_candidate" >/dev/null
fi
python3 -m unittest discover -s infra/gcp/tests -p 'test_*.py'
echo "GCP host bootstrap contract tests passed."
