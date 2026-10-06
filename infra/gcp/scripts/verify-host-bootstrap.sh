#!/usr/bin/env bash
set -Eeuo pipefail

REQUIRE_RUNNER="false"
REQUIRE_DEPLOYMENT="false"
ALLOW_METADATA_MIGRATION_PENDING="false"
while [ "$#" -gt 0 ]; do
  case "$1" in
    --require-runner) REQUIRE_RUNNER="true" ;;
    --require-deployment) REQUIRE_DEPLOYMENT="true" ;;
    --allow-metadata-migration-pending) ALLOW_METADATA_MIGRATION_PENDING="true" ;;
    *)
      echo "Usage: verify-host-bootstrap.sh [--require-runner] [--require-deployment] [--allow-metadata-migration-pending]" >&2
      exit 2
      ;;
  esac
  shift
done

METADATA_MIGRATION_MARKER="/etc/gole/metadata-migration.pending"

failures=0
pass() { echo "PASS: $*"; }
fail() {
  echo "FAIL: $*" >&2
  failures=$((failures + 1))
}

for command_name in docker git gcloud python3; do
  if command -v "$command_name" >/dev/null 2>&1; then
    pass "$command_name installed"
  else
    fail "$command_name missing"
  fi
done

if [ -x /usr/local/sbin/gole-metadata-firewall ] &&
  [ "$(stat -c '%U:%G:%a' /usr/local/sbin/gole-metadata-firewall)" = "root:root:755" ] &&
  systemctl is-enabled --quiet gole-metadata-firewall.service &&
  systemctl is-active --quiet gole-metadata-firewall.service; then
  pass "metadata firewall is root-owned, enabled and active"
else
  fail "metadata firewall installation or service state is invalid"
fi
metadata_migration_state="full"
if [ -e "$METADATA_MIGRATION_MARKER" ] || [ -L "$METADATA_MIGRATION_MARKER" ]; then
  marker_state="$(sed -n 's/^state=//p' "$METADATA_MIGRATION_MARKER" 2>/dev/null || true)"
  marker_sha="$(sed -n 's/^legacy_sha=//p' "$METADATA_MIGRATION_MARKER" 2>/dev/null || true)"
  if [ -f "$METADATA_MIGRATION_MARKER" ] && [ ! -L "$METADATA_MIGRATION_MARKER" ] &&
    [ "$(stat -c '%U:%G:%a' "$METADATA_MIGRATION_MARKER" 2>/dev/null || true)" = "root:root:644" ] &&
    [ "$(grep -Ec '^(state|legacy_sha)=' "$METADATA_MIGRATION_MARKER" 2>/dev/null || true)" -eq 2 ] &&
    [ "$(wc -l < "$METADATA_MIGRATION_MARKER" 2>/dev/null || true)" -eq 2 ] &&
    [ "$(tail -c 1 "$METADATA_MIGRATION_MARKER" 2>/dev/null | wc -l)" -eq 1 ] &&
    [ "$(grep -Ec '^state=' "$METADATA_MIGRATION_MARKER" 2>/dev/null || true)" -eq 1 ] &&
    [ "$(grep -Ec '^legacy_sha=' "$METADATA_MIGRATION_MARKER" 2>/dev/null || true)" -eq 1 ] &&
    [[ "$marker_sha" =~ ^[0-9a-f]{40}$ ]] && [ "$marker_state" = pending ]; then
    metadata_migration_state="pending"
  else
    metadata_migration_state="invalid"
    fail "metadata migration marker is invalid or ratcheting is incomplete"
  fi
fi

if [ "$metadata_migration_state" = pending ] &&
  [ "$ALLOW_METADATA_MIGRATION_PENDING" != true ]; then
  fail "temporary metadata migration mode remains active"
elif [ "$metadata_migration_state" = pending ]; then
  pass "temporary metadata migration mode is explicitly allowed"
fi

metadata_output_valid=false
if iptables -w -C OUTPUT -d 169.254.169.254/32 -j GOLE_METADATA_OUTPUT >/dev/null 2>&1 &&
  iptables -w -C GOLE_METADATA_OUTPUT -m owner --uid-owner 0 -j RETURN >/dev/null 2>&1 &&
  iptables -w -C GOLE_METADATA_OUTPUT -j REJECT >/dev/null 2>&1; then
  metadata_output_valid=true
fi
if [ "$metadata_migration_state" = pending ] && [ "$metadata_output_valid" = true ] &&
  ! iptables -w -t raw -C PREROUTING -j GOLE_METADATA_INPUT >/dev/null 2>&1; then
  pass "metadata IPv4 policy blocks non-root host processes while preserving legacy containers"
elif [ "$metadata_migration_state" = full ] && [ "$metadata_output_valid" = true ] &&
  iptables -w -t raw -C PREROUTING -j GOLE_METADATA_INPUT >/dev/null 2>&1 &&
  iptables -w -t raw -C GOLE_METADATA_INPUT -d 169.254.169.254/32 -j DROP >/dev/null 2>&1; then
  pass "metadata IPv4 policy blocks containers and non-root host processes"
else
  fail "metadata IPv4 firewall policy is missing"
fi

if command -v ip6tables >/dev/null 2>&1; then
  if [ "$metadata_migration_state" = pending ] &&
    ! ip6tables -w -t raw -C PREROUTING -j GOLE_METADATA_INPUT >/dev/null 2>&1 &&
    ip6tables -w -C OUTPUT -d fd20:ce::254/128 -j GOLE_METADATA_OUTPUT >/dev/null 2>&1; then
    pass "metadata IPv6 pending policy preserves only legacy container access"
  elif [ "$metadata_migration_state" = full ] &&
    ip6tables -w -t raw -C PREROUTING -j GOLE_METADATA_INPUT >/dev/null 2>&1 &&
    ip6tables -w -t raw -C GOLE_METADATA_INPUT -d fd20:ce::254/128 -j DROP >/dev/null 2>&1 &&
    ip6tables -w -C OUTPUT -d fd20:ce::254/128 -j GOLE_METADATA_OUTPUT >/dev/null 2>&1; then
    pass "metadata IPv6 policy blocks containers and non-root host processes"
  else
    fail "metadata IPv6 firewall policy is missing"
  fi
fi

if [ ! -r /etc/gole/deploy-user ]; then
  fail "deploy identity missing"
  DEPLOY_USER=""
  DEPLOY_GROUP=""
else
  IFS=: read -r DEPLOY_USER DEPLOY_GROUP < /etc/gole/deploy-user
  if id "$DEPLOY_USER" >/dev/null 2>&1 && [ "$(id -gn "$DEPLOY_USER")" = "$DEPLOY_GROUP" ]; then
    pass "deploy identity exists"
  else
    fail "deploy identity invalid"
  fi
fi

if [ "$DEPLOY_USER" = "goledeploy" ] &&
  [ "$(id -nG "$DEPLOY_USER" | tr ' ' '\n' | sort -u | tr '\n' ' ')" = "$DEPLOY_GROUP " ]; then
  pass "runner account is isolated from supplemental groups"
else
  fail "runner account has unexpected supplemental or privileged groups"
fi

if systemctl is-enabled --quiet docker && systemctl is-active --quiet docker; then
  pass "Docker service enabled and active"
else
  fail "Docker service is not enabled and active"
fi
docker_security_dropin=/etc/systemd/system/docker.service.d/gole-security.conf
docker_requires="$(systemctl show --property=Requires --value docker.service 2>/dev/null || true)"
docker_after="$(systemctl show --property=After --value docker.service 2>/dev/null || true)"
if [ -f "$docker_security_dropin" ] && [ ! -L "$docker_security_dropin" ] &&
  [ "$(stat -c '%U:%G:%a' "$docker_security_dropin")" = "root:root:644" ] &&
  grep -Fqx 'Requires=gole-metadata-firewall.service gole-cloud-broker.service' "$docker_security_dropin" &&
  grep -Fqx 'After=gole-metadata-firewall.service gole-cloud-broker.service' "$docker_security_dropin" &&
  [[ " $docker_requires " == *' gole-metadata-firewall.service '* ]] &&
  [[ " $docker_requires " == *' gole-cloud-broker.service '* ]] &&
  [[ " $docker_after " == *' gole-metadata-firewall.service '* ]] &&
  [[ " $docker_after " == *' gole-cloud-broker.service '* ]]; then
  pass "Docker is fail-closed behind metadata firewall and root broker"
else
  fail "Docker security dependency graph is invalid"
fi

if [ -x /usr/local/sbin/gole-hostctl ] && [ "$(stat -c '%U:%G:%a' /usr/local/sbin/gole-hostctl)" = "root:root:755" ]; then
  pass "restricted host helper installed"
else
  fail "restricted host helper installation invalid"
fi

if [ -f /etc/gole/host-bootstrap.complete ] && [ ! -L /etc/gole/host-bootstrap.complete ] &&
  [ "$(stat -c '%U:%G:%a' /etc/gole/host-bootstrap.complete)" = "root:root:644" ] &&
  grep -Eq '^bootstrap_source_sha=[0-9a-f]{40}$' /etc/gole/host-bootstrap.complete; then
  pass "immutable host bootstrap completion marker valid"
else
  fail "host bootstrap completion marker missing or invalid"
fi
if [ -e /etc/gole/.host-bootstrap.previous ] || [ -L /etc/gole/.host-bootstrap.previous ]; then
  fail "an interrupted host policy upgrade marker remains"
else
  pass "no interrupted host policy upgrade marker"
fi

if [ -x /usr/local/sbin/gole-migrate-and-adopt-existing ] &&
  [ "$(stat -c '%U:%G:%a' /usr/local/sbin/gole-migrate-and-adopt-existing)" = "root:root:755" ]; then
  pass "existing-deployment migration helper installed"
else
  fail "existing-deployment migration helper installation invalid"
fi

for installed_helper in \
  /usr/local/sbin/gole-bootstrap-production-env \
  /usr/local/sbin/gole-register-github-runner; do
  if [ -x "$installed_helper" ] && [ "$(stat -c '%U:%G:%a' "$installed_helper")" = "root:root:755" ]; then
    pass "$(basename "$installed_helper") installed as root-owned reviewed code"
  else
    fail "$(basename "$installed_helper") installation invalid"
  fi
done
if [ -x /usr/local/libexec/gole/runner-start-allowed.sh ] &&
  [ "$(stat -c '%U:%G:%a' /usr/local/libexec/gole/runner-start-allowed.sh)" = "root:root:755" ]; then
  pass "runner metadata-ratchet start gate is installed"
else
  fail "runner metadata-ratchet start gate installation invalid"
fi

if [ -x /usr/local/libexec/gole/validate-production-compose.py ] &&
  [ "$(stat -c '%U:%G:%a' /usr/local/libexec/gole/validate-production-compose.py)" = "root:root:755" ]; then
  pass "production Compose privilege validator installed"
else
  fail "production Compose privilege validator installation invalid"
fi

if [ -f /etc/systemd/system/gole-cert-renew.service ] &&
  [ ! -L /etc/systemd/system/gole-cert-renew.service ] &&
  [ "$(stat -c '%U:%G:%a' /etc/systemd/system/gole-cert-renew.service)" = "root:root:644" ] &&
  grep -Fqx 'ExecStart=/usr/local/sbin/gole-hostctl certificate-renew' \
    /etc/systemd/system/gole-cert-renew.service; then
  pass "certificate timer executes only the root-owned validated dispatcher"
else
  fail "certificate renewal service trust boundary is invalid"
fi

for protected_env in /etc/gole/infra.env /etc/gole/gole.env; do
  if [ -e "$protected_env" ]; then
    if [ -f "$protected_env" ] && [ ! -L "$protected_env" ] &&
      [ "$(stat -c '%U:%G:%a' "$protected_env")" = "root:root:600" ] &&
      [ "$(stat -c '%h' "$protected_env")" = 1 ] &&
      [ "$(stat -c '%s' "$protected_env")" -gt 0 ] &&
      [ "$(stat -c '%s' "$protected_env")" -le 131072 ]; then
      pass "$(basename "$protected_env") is root-only"
    else
      fail "$(basename "$protected_env") permissions are not root-only"
    fi
  fi
done

if [ -n "$DEPLOY_USER" ]; then
  if [ "$(id -un)" = "$DEPLOY_USER" ]; then
    sudo_rules="$(sudo -n -l 2>/dev/null || true)"
  elif [ "$(id -u)" -eq 0 ]; then
    sudo_rules="$(sudo -n -l -U "$DEPLOY_USER" 2>/dev/null || true)"
  else
    sudo_rules=""
  fi
  if grep -q '/usr/local/sbin/gole-hostctl' <<<"$sudo_rules" &&
    ! grep -Eq '/usr/bin/(install|systemctl)' <<<"$sudo_rules" &&
    ! grep -Eq 'NOPASSWD:[[:space:]]*ALL([[:space:]]|$)' <<<"$sudo_rules"; then
    pass "deploy sudo allowlist is active"
  else
    fail "deploy sudo allowlist cannot be verified"
  fi

  if runuser -u "$DEPLOY_USER" -- sudo -n /usr/bin/true >/dev/null 2>&1; then
    fail "runner can execute arbitrary passwordless sudo"
  else
    pass "arbitrary passwordless sudo is denied"
  fi
  if runuser -u "$DEPLOY_USER" -- sudo -n /usr/bin/systemctl --version >/dev/null 2>&1 ||
    runuser -u "$DEPLOY_USER" -- sudo -n /usr/bin/install --version >/dev/null 2>&1; then
    fail "runner can execute privileged systemctl or install directly"
  else
    pass "raw privileged systemctl and install are denied"
  fi
  if runuser -u "$DEPLOY_USER" -- sudo -n /usr/local/sbin/gole-hostctl \
    privilege-probe >/dev/null 2>&1; then
    pass "fixed host helper remains authorized"
  else
    fail "fixed host helper is not authorized"
  fi
  if runuser -u "$DEPLOY_USER" -- docker info >/dev/null 2>&1; then
    fail "runner can reach the unrestricted Docker daemon"
  else
    pass "runner cannot reach the unrestricted Docker daemon"
  fi
  if runuser -u "$DEPLOY_USER" -- curl -fsS --max-time 2 \
    -H 'Metadata-Flavor: Google' \
    http://169.254.169.254/computeMetadata/v1/instance/service-accounts/default/token \
    >/dev/null 2>&1; then
    fail "runner can obtain a GCE metadata service-account token"
  else
    pass "runner cannot reach the GCE metadata token endpoint"
  fi
fi

for root_service in gole-cloud-broker.service gole-data-backup.timer; do
  if systemctl is-enabled --quiet "$root_service" && systemctl is-active --quiet "$root_service"; then
    pass "$root_service is enabled and active"
  else
    fail "$root_service is not enabled and active"
  fi
done

if [ "$REQUIRE_DEPLOYMENT" = "true" ]; then
  if [ "$(id -u)" -eq 0 ]; then
    deployed_sha="$(SUDO_USER=root /usr/local/sbin/gole-hostctl deployment-read-sha 2>/dev/null || true)"
  elif [ "$(id -un)" = "$DEPLOY_USER" ]; then
    deployed_sha="$(sudo -n /usr/local/sbin/gole-hostctl deployment-read-sha 2>/dev/null || true)"
  else
    deployed_sha=""
  fi
  if [[ "$deployed_sha" =~ ^[0-9a-f]{40}$ ]]; then
    pass "last-known-good deployment SHA recorded"
  else
    fail "last-known-good deployment SHA marker missing or invalid"
  fi
fi

if [ "$REQUIRE_RUNNER" = "true" ]; then
  if [ -d /opt/gole-actions-runner ] &&
    [ ! -L /opt/gole-actions-runner ] &&
    [ "$(stat -c '%U:%G:%a' /opt/gole-actions-runner)" = "$DEPLOY_USER:$DEPLOY_GROUP:750" ]; then
    pass "runner workspace is hidden from unrelated host users"
  else
    fail "runner root identity or permissions are invalid"
  fi

  runner_service_file=/etc/systemd/system/gole-github-runner.service
  runner_service=gole-github-runner.service
  legacy_runner_services=(/etc/systemd/system/actions.runner.*.service)
  if [ -e "${legacy_runner_services[0]}" ]; then
    fail "legacy human-account GitHub Actions runner service remains"
  elif [ ! -f "$runner_service_file" ] || [ -L "$runner_service_file" ] ||
    [ "$(stat -c '%U:%G:%a' "$runner_service_file" 2>/dev/null || true)" != "root:root:644" ]; then
    fail "GitHub Actions runner service unit missing or invalid"
  else
    runner_service_user="$(systemctl show -p User --value "$runner_service" 2>/dev/null || true)"
    if [ "$runner_service_user" = "$DEPLOY_USER" ] &&
      grep -Fqx 'Requires=gole-cloud-broker.service' "$runner_service_file" &&
      grep -Fqx 'ExecCondition=/usr/local/libexec/gole/runner-start-allowed.sh' "$runner_service_file" &&
      grep -Fqx "ExecStart=/opt/gole-actions-runner/runsvc.sh" "$runner_service_file" &&
      grep -Fqx 'KillMode=control-group' "$runner_service_file" &&
      systemctl is-enabled --quiet "$runner_service" && systemctl is-active --quiet "$runner_service"; then
      pass "GitHub Actions runner service enabled and active"
    else
      fail "GitHub Actions runner service identity or state is invalid"
    fi
  fi

  runner_label_line="$(sed -n 's/^runner_labels=//p' /etc/gole/github-runner-registration.conf 2>/dev/null || true)"
  if [[ ",$runner_label_line," == *,gole-gcp-production,* ]]; then
    pass "production runner label recorded"
  else
    fail "production runner label marker missing"
  fi

  for credential_file in \
    /opt/gole-actions-runner/.credentials \
    /opt/gole-actions-runner/.credentials_rsaparams; do
    if [ -e "$credential_file" ]; then
      mode="$(stat -c '%a' "$credential_file")"
      if (( (8#$mode & 0077) == 0 )); then
        pass "$(basename "$credential_file") is not group/world-readable"
      else
        fail "$(basename "$credential_file") permissions are too broad"
      fi
    fi
  done
fi

if [ "$failures" -ne 0 ]; then
  echo "$failures host bootstrap verification(s) failed" >&2
  exit 1
fi
echo "Host bootstrap verification passed."
