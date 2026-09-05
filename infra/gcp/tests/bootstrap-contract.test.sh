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
  infra/gcp/scripts/issue-certificate.sh
  infra/gcp/scripts/metadata-firewall.sh
  infra/gcp/scripts/register-github-runner.sh
  infra/gcp/scripts/restore-data.sh
  infra/gcp/scripts/runner-start-allowed.sh
  infra/gcp/scripts/verify-host-bootstrap.sh
  infra/gcp/scripts/verify-cloud-broker-ready.sh
  infra/gcp/scripts/verify-operator-access.sh
  infra/gcp/scripts/apply-secret-env.sh
  infra/gcp/scripts/bootstrap-production-env.sh
  infra/gcp/scripts/migrate-and-adopt-existing.sh
  infra/gcp/scripts/verify-snapshot-policy.sh
)
for script in "${scripts[@]}"; do
  bash -n "$script"
done

startup_script="infra/gcp/terraform/main.tf"
startup_cleanup_line="$(grep -n 'trap startup_cleanup EXIT' "$startup_script" | cut -d: -f1)"
startup_apt_line="$(grep -n '^[[:space:]]*apt-get update' "$startup_script" | head -n1 | cut -d: -f1)"
startup_success_line="$(grep -n '^[[:space:]]*startup_complete=1' "$startup_script" | tail -n1 | cut -d: -f1)"
[ -n "$startup_cleanup_line" ] && [ -n "$startup_apt_line" ] &&
  [ "$startup_cleanup_line" -lt "$startup_apt_line" ] ||
  fail "Terraform startup must arm fail-closed poweroff before its first external mutation"
[ -n "$startup_success_line" ] && [ "$startup_success_line" -gt "$startup_apt_line" ] ||
  fail "Terraform startup must mark success only after bootstrap verification"
grep -Fq 'systemctl poweroff --no-block || true' "$startup_script" ||
  fail "Terraform startup failure must request VM poweroff"
if [ "$(sed -n '/startup-script = <<-EOT/,/^[[:space:]]*EOT$/p' "$startup_script" |
  grep -Ec 'trap startup_cleanup EXIT')" -ne 1 ] ||
  sed -n '/startup-script = <<-EOT/,/^[[:space:]]*EOT$/p' "$startup_script" |
    grep -Eq "trap 'rm .* EXIT"; then
  fail "Terraform startup must not overwrite its fail-closed EXIT trap"
fi
grep -Fq 'bootstrap_fail_closed_armed=1' infra/gcp/scripts/bootstrap-host.sh ||
  fail "host bootstrap must arm fail-closed poweroff before quiescing production"
grep -Fq 'validate_legacy_infrastructure_credentials_file' \
  infra/gcp/scripts/bootstrap-host.sh ||
  fail "host bootstrap must validate the exact legacy infrastructure credential shape"
grep -Fq 'kscold:kscold:600' infra/gcp/scripts/bootstrap-host.sh ||
  fail "host bootstrap must restrict one-time legacy infrastructure ownership"
grep -Fq 'METADATA_MIGRATION_LEGACY_SHA' infra/gcp/scripts/bootstrap-host.sh ||
  fail "legacy infrastructure adoption must bind to the metadata migration SHA"
grep -Fq "stat -c '%h'" infra/gcp/scripts/verify-host-bootstrap.sh ||
  fail "strict host verification must reject hard-linked protected environments"

test "$(grep -Ec '^[[:space:]]{4}timeout-minutes:[[:space:]]+60$' .github/workflows/cd.yml)" -eq 1 ||
  fail "production CD must retain the exact 60-minute backup/build/rollback envelope"
if awk '$1 == "timeout-minutes:" && $2 != "60" { found = 1 } END { exit found ? 0 : 1 }' \
  .github/workflows/cd.yml; then
  fail "production CD contains a timeout outside the reviewed 60-minute envelope"
fi

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
grep -A10 'variable "billing_account_id"' infra/gcp/terraform/variables.tf |
  grep -Fq '^[0-9A-F]{6}-[0-9A-F]{6}-[0-9A-F]{6}$' ||
  fail "Terraform must require a billing account for the production cost guard"
if grep -A10 'variable "billing_account_id"' infra/gcp/terraform/variables.tf |
  grep -Eq 'default[[:space:]]*=[[:space:]]*""'; then
  fail "Terraform must not bootstrap production without a Billing Budget identity"
fi
if grep -REn --include='*.tf' \
  'variable "expected_budget_id"|var\.expected_budget_id' \
  infra/gcp/terraform; then
  fail "Terraform must derive the root cost guard Budget ID instead of accepting a copied UUID"
fi
grep -Fq 'gole-budget-id = basename(google_billing_budget.gole_credit_guard[0].id)' \
  infra/gcp/terraform/main.tf ||
  fail "Terraform must bind the exact Budget resource identity to instance metadata"
if [ -e infra/gcp/scripts/setup-budget-alerts.sh ] ||
  [ -L infra/gcp/scripts/setup-budget-alerts.sh ]; then
  fail "Terraform must remain the only production budget infrastructure owner"
fi
grep -Fq 'GCP_EXPECTED_BUDGET_ID="$expected_budget_id"' infra/gcp/terraform/main.tf ||
  fail "Terraform startup must pass only the validated instance Budget identity"
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
grep -Fq '/usr/local/libexec/gole/issue-certificate.sh' infra/gcp/scripts/bootstrap-host.sh ||
  fail "host bootstrap must install the reviewed root-owned certificate issuer"
grep -q '/usr/local/libexec/gole/validate-production-compose.py' infra/gcp/scripts/bootstrap-host.sh ||
  fail "host bootstrap must install the root-owned Compose privilege validator"
grep -q '/usr/local/sbin/gole-migrate-and-adopt-existing' infra/gcp/scripts/bootstrap-host.sh ||
  fail "host bootstrap must install the existing deployment migration helper"
grep -q '/usr/local/sbin/gole-register-github-runner' infra/gcp/scripts/bootstrap-host.sh ||
  fail "host bootstrap must install the root-owned runner registration helper"
grep -q '/usr/local/sbin/gole-bootstrap-production-env' infra/gcp/scripts/bootstrap-host.sh ||
  fail "host bootstrap must install the root-owned initial environment helper"
grep -Fqx 'Requires=gole-metadata-firewall.service gole-cloud-broker.service' \
  infra/gcp/systemd/docker-gole-security.conf ||
  fail "Docker must require both metadata firewall and root broker"
grep -Fqx 'After=gole-metadata-firewall.service gole-cloud-broker.service' \
  infra/gcp/systemd/docker-gole-security.conf ||
  fail "Docker must start only after both security prerequisites"
for security_unit in \
  infra/gcp/systemd/gole-metadata-firewall.service \
  infra/gcp/systemd/gole-cloud-broker.service; do
  grep -Fqx 'OnFailure=poweroff.target' "$security_unit" ||
    fail "security prerequisite failure must power off the VM: $security_unit"
  grep -Fqx 'OnFailureJobMode=replace-irreversibly' "$security_unit" ||
    fail "security prerequisite poweroff must be irreversible: $security_unit"
done
grep -Fq '/etc/systemd/system/docker.service.d/gole-security.conf' \
  infra/gcp/scripts/bootstrap-host.sh ||
  fail "host bootstrap must install the Docker security dependency drop-in"
grep -Fqx 'ExecStartPost=/usr/local/libexec/gole/verify-cloud-broker-ready.sh' \
  infra/gcp/systemd/gole-cloud-broker.service ||
  fail "root broker start must wait for its socket round trip and policy heartbeat"
if grep -Fq 'systemctl enable --now gole-cloud-broker.service' \
  infra/gcp/scripts/bootstrap-host.sh; then
  fail "bootstrap must not leave an already-active stale broker process running"
fi
grep -Fq 'systemctl enable gole-cloud-broker.service' \
  infra/gcp/scripts/bootstrap-host.sh ||
  fail "bootstrap must enable the root broker for subsequent boots"
grep -Fq 'systemctl restart gole-cloud-broker.service' \
  infra/gcp/scripts/bootstrap-host.sh ||
  fail "bootstrap must restart the root broker after replacing its trust anchors"
grep -Fq 'systemctl is-active --quiet gole-cloud-broker.service' \
  infra/gcp/scripts/bootstrap-host.sh ||
  fail "bootstrap must verify the restarted root broker remains active"
python3 - <<'PY'
from pathlib import Path

bootstrap = Path("infra/gcp/scripts/bootstrap-host.sh").read_text(encoding="utf-8")
firewall_start = bootstrap.index("systemctl enable --now gole-metadata-firewall.service")
broker_enable = bootstrap.index("systemctl enable gole-cloud-broker.service")
broker_restart = bootstrap.index("systemctl restart gole-cloud-broker.service")
broker_active = bootstrap.index("systemctl is-active --quiet gole-cloud-broker.service")
failure_poweroff = bootstrap.index("systemctl poweroff --no-block", broker_active)
failure_exit = bootstrap.index("exit 1", failure_poweroff)
docker_start = bootstrap.index("systemctl enable --now docker", firewall_start)
if not (
    firewall_start
    < broker_enable
    < broker_restart
    < broker_active
    < failure_poweroff
    < failure_exit
    < docker_start
):
    raise SystemExit(
        "broker replacement must fail closed before Docker can continue"
    )
PY
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
if grep -Ein 'access-control-|proxy_hide_header' infra/gcp/nginx-*.conf.template; then
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
if grep -REn --include='*.tf' \
  'compute\.instances\.stop|goleProductionInstanceStopper' \
  infra/gcp/terraform; then
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
grep -Fq 'KillMode=control-group' infra/gcp/scripts/register-github-runner.sh ||
  fail "runner service stop must terminate the complete job cgroup"
grep -Fq 'unsealed legacy runner root has no reviewed retired unit' \
  infra/gcp/scripts/register-github-runner.sh ||
  fail "runner migration must require exact retirement evidence after unit removal"
grep -Fq 'registration_succeeded' infra/gcp/scripts/register-github-runner.sh ||
  fail "failed runner registration must leave the reviewed command retryable"
grep -Fq 'runner_registration_marker_is_valid' \
  infra/gcp/scripts/register-github-runner.sh ||
  fail "runner registration must commit and verify a strict root-owned marker"
grep -Fq 'mktemp /etc/gole/.github-runner-registration.XXXXXX' \
  infra/gcp/scripts/register-github-runner.sh ||
  fail "runner registration marker must be staged on the target filesystem"
grep -Fq 'verify_runner_control_group_empty' infra/gcp/scripts/bootstrap-host.sh ||
  fail "host bootstrap must prove stopped runner cgroups are empty"
grep -Fq "grep -Fqx 'KillMode=control-group'" infra/gcp/scripts/verify-host-bootstrap.sh ||
  fail "host verification must reject process-only runner shutdown"
grep -Eq 'default[[:space:]]*=[[:space:]]*"gole-gcp-production"' \
  infra/gcp/terraform/variables.tf ||
  fail "Terraform must keep the replacement runner name stable"
grep -A10 'variable "github_runner_name"' infra/gcp/terraform/variables.tf |
  grep -Fq 'condition     = var.github_runner_name == "gole-gcp-production"' ||
  fail "Terraform must reject a second production runner name"
if grep -Fq 'GITHUB_RUNNER_NAME=gole-production' infra/gcp/README.md; then
  fail "the migration runbook must not create a second production runner name"
fi
grep -Fq 'GOLE_METADATA_MIGRATION_SOURCE_SHA="$legacy_sha"' infra/gcp/README.md ||
  fail "the legacy bootstrap runbook must pin the metadata migration source SHA"
if [ "$(grep -c -- '--allow-metadata-migration-pending' infra/gcp/README.md)" -lt 3 ]; then
  fail "pre-ratchet runbook checks must explicitly allow only the pending migration state"
fi
python3 - <<'PY'
from pathlib import Path

runbook = Path("infra/gcp/README.md").read_text(encoding="utf-8")
gts_section = runbook.index("### 신규/인증서 없는 VM의 1회 GTS 권한 부여")
gts_section_end = runbook.index("exact version은", gts_section)
gts_runbook = runbook[gts_section:gts_section_end]
gts_grant_plan = gts_runbook.index("terraform plan -out=gts-eab-grant.tfplan")
gts_grant_true = gts_runbook.index("grant_gts_eab_creator=true", gts_grant_plan)
gts_grant_apply = gts_runbook.index("terraform apply gts-eab-grant.tfplan", gts_grant_true)
gts_iam_present = gts_runbook.index(
    "roles/publicca.externalAccountKeyCreator", gts_grant_apply
)
gts_env_install = gts_runbook.index("gole-bootstrap-production-env", gts_iam_present)
gts_first_cd = gts_runbook.index("gh workflow run cd.yml", gts_env_install)
gts_ci_success = gts_runbook.index('"${EXPECTED_GTS_DEPLOY_SHA}|success"', gts_first_cd)
gts_issuer = gts_runbook.index("Google Trust Services", gts_ci_success)
gts_apex = gts_runbook.index("-checkhost gole.co.kr", gts_issuer)
gts_www = gts_runbook.index("-checkhost www.gole.co.kr", gts_apex)
gts_revoke_plan = gts_runbook.index("terraform plan -out=gts-eab-revoke.tfplan", gts_www)
gts_grant_false = gts_runbook.index("grant_gts_eab_creator=false", gts_revoke_plan)
gts_revoke_apply = gts_runbook.index(
    "terraform apply gts-eab-revoke.tfplan", gts_grant_false
)
gts_iam_absent = gts_runbook.index('test -z "$(gcloud projects get-iam-policy', gts_revoke_apply)
if not (
    gts_grant_plan
    < gts_grant_true
    < gts_grant_apply
    < gts_iam_present
    < gts_env_install
    < gts_first_cd
    < gts_ci_success
    < gts_issuer
    < gts_apex
    < gts_www
    < gts_revoke_plan
    < gts_grant_false
    < gts_revoke_apply
    < gts_iam_absent
):
    raise SystemExit("new-host GTS grant, first TLS, and revocation ordering is unsafe")
for contract in (
    "기존 `gole-production` migrate-and-adopt",
    "certificate volume",
    "grant_gts_eab_creator=false",
    "google_project_iam_member.gts_eab_creator[0] 추가 외 변경이 없을 때만",
    "google_project_iam_member.gts_eab_creator[0] 제거 외 변경이 없을 때만",
):
    if contract not in gts_runbook:
        raise SystemExit(f"GTS least-privilege runbook contract missing: {contract}")
migration_section = runbook.index("## 현재 운영 VM의 1회 migrate-and-adopt")
offline_resize = runbook.index("### bootstrap 전 오프라인 resize", migration_section)
exact_project = runbook.index(
    "RECOVERY_PROJECT_ID=project-72a52bf1-06aa-4519-b2c", offline_resize
)
host_gate = runbook.index(
    "GOLE_PRODUCTION_HOST_READY --body false", exact_project
)
auto_delete_baseline = runbook.index(
    'recovery_auto_delete="$(gcloud compute instances describe', host_gate
)
pre_auto_delete_json = runbook.index("--format=json --quiet", auto_delete_baseline)
pre_auto_delete_disk_identity = runbook.index(
    "expected exactly one production disk before auto-delete transition",
    pre_auto_delete_json,
)
metadata_json = runbook.index("--format=json --quiet", pre_auto_delete_disk_identity)
metadata_exact = runbook.index(
    'expected=[{"key":"enable-oslogin","value":"FALSE"}]', metadata_json
)
stop = runbook.index(
    'gcloud compute instances stop "$RECOVERY_INSTANCE"', metadata_exact
)
terminated = runbook.index("= TERMINATED", stop)
disable_auto_delete = runbook.index(
    'gcloud compute instances set-disk-auto-delete "$RECOVERY_INSTANCE"',
    terminated,
)
no_auto_delete = runbook.index(
    '--disk="$RECOVERY_DISK" --no-auto-delete', disable_auto_delete
)
auto_delete_disabled = runbook.index(
    "--format='value(disks[0].autoDelete)' --quiet)\" = False",
    no_auto_delete,
)
boot_id_preserved_after_auto_delete = runbook.index(
    "--format='value(id)' --quiet)\" = \\", auto_delete_disabled
)
resize = runbook.index(
    'gcloud compute instances set-machine-type "$RECOVERY_INSTANCE"',
    boot_id_preserved_after_auto_delete,
)
desired_shape = runbook.index('--machine-type="$RECOVERY_MACHINE_TYPE"', resize)
shape_verified = runbook.index('= "$RECOVERY_MACHINE_TYPE"', desired_shape)
instance_identity = runbook.index(
    "expected exactly one VM network interface", shape_verified
)
actual_reserved_nat = runbook.index(
    'access.get("natIP") == expected_ip', instance_identity
)
exact_network_tags = runbook.index(
    'sorted(model.get("tags", {}).get("items", [])) == ["gole-ssh-iap", "gole-web"]',
    actual_reserved_nat,
)
exact_runtime_scopes = runbook.index(
    'accounts[0].get("scopes") == [expected_scope]', exact_network_tags
)
exact_boot_disk = runbook.index(
    'canonical(disk.get("source")) == expected_disk', exact_runtime_scopes
)
policy_verified = runbook.index(
    "for recovery_firewall in gole-web gole-ssh-iap gole-deny-public-admin",
    exact_boot_disk,
)
firewall_direction = runbook.index(
    'model.get("direction") == "INGRESS"', policy_verified
)
firewall_priority = runbook.index(
    'model.get("priority") == expected["priority"]', firewall_direction
)
firewall_sources = runbook.index(
    'sorted(model.get("sourceRanges", [])) == expected["sources"]',
    firewall_priority,
)
firewall_targets = runbook.index(
    'sorted(model.get("targetTags", [])) == expected["tags"]', firewall_sources
)
firewall_allow = runbook.index(
    'flattened("allowed") == expected["allowed"]', firewall_targets
)
firewall_deny = runbook.index(
    'flattened("denied") == expected["denied"]', firewall_allow
)
start = runbook.index(
    'gcloud compute instances start "$RECOVERY_INSTANCE"', firewall_deny
)
limited_recovery = runbook.index(
    'if ! gcloud compute ssh "$RECOVERY_INSTANCE"', start
)
failure_stop = runbook.index(
    'gcloud compute instances stop "$RECOVERY_INSTANCE"', limited_recovery
)
candidate_secret = runbook.index("gcloud secrets versions access 5", failure_stop)
final_live_shape_gate = runbook.index(
    'test "$FINAL_MACHINE_TYPE" = e2-standard-2', candidate_secret
)
approval_source = runbook.index(
    "환경 변수로 승인 상태를 만들지 않고", candidate_secret
)
old_runner_stop = runbook.index("old runner가 유휴인지 확인", final_live_shape_gate)
root_bootstrap = runbook.index("cat <<'ROOT_BOOTSTRAP'", old_runner_stop)
if not (
    migration_section
    < offline_resize
    < exact_project
    < host_gate
    < auto_delete_baseline
    < pre_auto_delete_json
    < pre_auto_delete_disk_identity
    < metadata_json
    < metadata_exact
    < stop
    < terminated
    < disable_auto_delete
    < no_auto_delete
    < auto_delete_disabled
    < boot_id_preserved_after_auto_delete
    < resize
    < desired_shape
    < shape_verified
    < instance_identity
    < actual_reserved_nat
    < exact_network_tags
    < exact_runtime_scopes
    < exact_boot_disk
    < policy_verified
    < firewall_direction
    < firewall_priority
    < firewall_sources
    < firewall_targets
    < firewall_allow
    < firewall_deny
    < start
    < limited_recovery
    < failure_stop
    < candidate_secret
    < approval_source
    < final_live_shape_gate
    < old_runner_stop
    < root_bootstrap
):
    raise SystemExit("unconditional offline resize/bootstrap ordering is unsafe")
for migration_contract in (
    "RECOVERY_ZONE=asia-northeast3-a",
    "RECOVERY_INSTANCE=gole-production",
    "RECOVERY_DISK=gole-production",
    "RECOVERY_OLD_MACHINE_TYPE=e2-custom-4-8192",
    "RECOVERY_MACHINE_TYPE=e2-standard-2",
    "RECOVERY_STATIC_IP=35.216.80.123",
    'RECOVERY_NETWORK="projects/${RECOVERY_PROJECT_ID}/global/networks/default"',
    'RECOVERY_SUBNETWORK="projects/${RECOVERY_PROJECT_ID}/regions/${RECOVERY_REGION}/subnetworks/default"',
    "RECOVERY_RUNTIME_SCOPE=https://www.googleapis.com/auth/cloud-platform",
    "2026-09-05 live baseline은 autoDelete=true",
    "--no-auto-delete",
    "GOLE_PRODUCTION_HOST_READY --body false",
    "환경 변수로 승인 상태를 만들지 않고",
):
    if migration_contract not in runbook[migration_section:root_bootstrap]:
        raise SystemExit(f"offline migration contract missing: {migration_contract}")
if "--machine-type=e2-custom-4-8192" in runbook[offline_resize:candidate_secret]:
    raise SystemExit("offline migration must never resize back to the custom shape")
for exact_restart_guard in (
    'access.get("type") == "ONE_TO_ONE_NAT"',
    'access.get("networkTier") == "STANDARD"',
    'not interface.get("ipv6AccessConfigs")',
    'accounts[0].get("email") == expected_sa',
    'disk.get("autoDelete") is False',
    'disk.get("mode") == "READ_WRITE"',
    '"gole-web": {',
    '"gole-ssh-iap": {',
    '"gole-deny-public-admin": {',
    '"sources": ["35.235.240.0/20"]',
    '"denied": [("tcp", "22"), ("tcp", "3389")]',
):
    if exact_restart_guard not in runbook[offline_resize:start]:
        raise SystemExit(
            f"offline restart identity guard missing: {exact_restart_guard}"
        )
first_adopted = runbook.index("deployment-verify-adopted-runtime")
operator_apply = runbook.index("terraform apply operator-access.tfplan", first_adopted)
profile_account = runbook.index("value(posixAccounts[0].accountId)", operator_apply)
profile_username = runbook.index("value(posixAccounts[0].username)", profile_account)
profile_primary = runbook.index("value(posixAccounts[0].primary)", profile_username)
pre_apply_sudo = runbook.index("--command='sudo -n /usr/bin/true'", operator_apply)
logical_backup = runbook.index("sudo -n /usr/local/sbin/gole-backup-data\n", pre_apply_sudo)
verified_backup = runbook.index(
    "sudo -n /usr/local/sbin/gole-backup-data --verify-latest", logical_backup
)
boot_disk_source = runbook.index('EXPECTED_BOOT_DISK="projects/${PROJECT_ID}/zones/${ZONE}/disks/gole-production"', verified_backup)
manual_policy_boundary = runbook.index(
    "기존 gole-production-daily-snapshots 정책", boot_disk_source
)
manual_snapshot_name = runbook.index(
    'PRE_IAC_SNAPSHOT="gole-production-pre-iac-$(date -u +%Y%m%d-%H%M%S)"',
    manual_policy_boundary,
)
snapshot_collision_gate = runbook.index(
    'test -z "$(gcloud compute snapshots list', manual_snapshot_name
)
manual_snapshot_create = runbook.index(
    'gcloud compute snapshots create "$PRE_IAC_SNAPSHOT"', snapshot_collision_gate
)
snapshot_ready = runbook.index('test "$SNAPSHOT_STATUS" = READY', manual_snapshot_create)
snapshot_source = runbook.index(
    'test "${SNAPSHOT_SOURCE#https://www.googleapis.com/compute/v1/}" = "$EXPECTED_BOOT_DISK"',
    snapshot_ready,
)
snapshot_source_id = runbook.index(
    'test "$SNAPSHOT_SOURCE_ID" = "$BOOT_DISK_ID"', snapshot_source
)
terraform_plan = runbook.index("terraform plan -out=existing-project.tfplan", snapshot_source_id)
terraform_apply = runbook.index("terraform apply existing-project.tfplan", first_adopted)
post_apply_sudo = runbook.index("sudo -n /usr/bin/true", terraform_apply)
second_adopted = runbook.index("deployment-verify-adopted-runtime", terraform_apply)
reviewed_checkout = runbook.index("checkout --detach --force FETCH_HEAD", second_adopted)
replacement_runner = runbook.index("gole-register-github-runner --token-stdin", reviewed_checkout)
required_secret_names = runbook.index('required_secret_names="$(printf', replacement_runner)
secret_names_only = runbook.index("--json name --jq '.[].name'", required_secret_names)
secret_set_intersection = runbook.index("comm -12", secret_names_only)
secret_count = runbook.index("-eq 3", secret_set_intersection)
secret_set_equality = runbook.index(
    'test "$matched_secret_names" = "$required_secret_names"', secret_count
)
open_host_gate = runbook.index(
    "gh variable set GOLE_PRODUCTION_HOST_READY --body true", secret_set_equality
)
if not (
    first_adopted
    < operator_apply
    < profile_account
    < profile_username
    < profile_primary
    < pre_apply_sudo
    < logical_backup
    < verified_backup
    < boot_disk_source
    < manual_policy_boundary
    < manual_snapshot_name
    < snapshot_collision_gate
    < manual_snapshot_create
    < snapshot_ready
    < snapshot_source
    < snapshot_source_id
    < terraform_plan
    < terraform_apply
    < post_apply_sudo
    < second_adopted
    < reviewed_checkout
    < replacement_runner
    < required_secret_names
    < secret_names_only
    < secret_set_intersection
    < secret_count
    < secret_set_equality
    < open_host_gate
):
    raise SystemExit("legacy migration runbook ordering is unsafe")
secret_gate = runbook[required_secret_names:open_host_gate]
if "grep -E '^(DISCORD_" in secret_gate:
    raise SystemExit("Discord secret readiness must not use an OR grep")
if "gh secret view" in secret_gate or "--json value" in secret_gate:
    raise SystemExit("Discord secret readiness must inspect names only")
for secret_name in (
    "DISCORD_OPERATIONS_WEBHOOK_URL",
    "DISCORD_ACCOUNT_WEBHOOK_URL",
    "DISCORD_PAYMENT_WEBHOOK_URL",
):
    if secret_gate.count(secret_name) != 1:
        raise SystemExit(f"Discord required secret set is not exact: {secret_name}")
for backup_contract in (
    "--source-disk=gole-production",
    '--source-disk-zone="$ZONE"',
    '--storage-location="$REGION"',
    "--no-guest-flush",
    "gole-production-daily-snapshots",
    "자동 삭제하지",
):
    if backup_contract not in runbook:
        raise SystemExit(f"pre-IaC backup runbook contract missing: {backup_contract}")
if 'gcloud compute snapshots delete "$PRE_IAC_SNAPSHOT"' in runbook:
    raise SystemExit("pre-IaC snapshot must not have an automatic deletion command")
for contract in (
    "### 첫 CD metadata ratchet 중단 복구",
    "GOLE_PRODUCTION_HOST_READY --body false",
    "gole-hostctl deployment-recover",
    "test \"$recovery\" = RECOVERED",
):
    if contract not in runbook:
        raise SystemExit(f"metadata ratchet recovery runbook contract missing: {contract}")
PY
for readiness_contract in \
  '/etc/gole/metadata-migration.pending' \
  '/usr/local/libexec/gole/runner-start-allowed.sh' \
  'deployment-budget-healthy' \
  'watchdog-active'; do
  grep -Fq "$readiness_contract" .github/workflows/cd.yml ||
    fail "CD final readiness is missing $readiness_contract"
done
grep -Fq '"$HOSTCTL" deployment-migrate-adopt-existing' \
  infra/gcp/scripts/migrate-and-adopt-existing.sh ||
  fail "adoption migration must delegate preserved-env adoption to the root host transaction"
grep -Fq 'GOLE_PRODUCTION_ENV_SECRET_VERSION --body "$NEW_SECRET_VERSION"' \
  infra/gcp/README.md ||
  fail "first migrated CD must pin the reviewed SMTP-off Secret version"
if grep -q -- '--version-stdin' infra/gcp/scripts/migrate-and-adopt-existing.sh; then
  fail "preserved-env adoption must not accept a replacement Secret version"
fi
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
  deployment-environment-prepare \
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

if grep -REn '^(ExecStart|ExecStop|ExecReload)=/app/' infra/gcp/systemd; then
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
