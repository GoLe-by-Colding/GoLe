#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
IMAGE="ubuntu@sha256:33ceb71981b602c1a7443a53469e4dba065f7503eab3078a2d7a57a2ab987517"

docker run --rm --interactive --volume "$ROOT:/source:ro" "$IMAGE" bash -seu <<'CONTAINER_TEST'
bootstrap_sha=3333333333333333333333333333333333333333
standard_service=gole-github-runner.service
legacy_alpha=actions.runner.GoLe.alpha.service
legacy_beta=actions.runner.GoLe.beta.service

install -d -m 0755 /etc/gole /etc/systemd/system /test-bin
touch "/etc/systemd/system/$legacy_alpha" "/etc/systemd/system/$legacy_beta"
cat > "/etc/systemd/system/$standard_service" <<'EOF'
[Unit]
Requires=gole-cloud-broker.service
[Service]
User=goledeploy
ExecCondition=/usr/local/libexec/gole/runner-start-allowed.sh
ExecStart=/opt/gole-actions-runner/runsvc.sh
KillMode=process
EOF
chmod 0644 "/etc/systemd/system/$standard_service"

sed -n '1,/^# Detect the one live legacy relay/p' \
  /source/infra/gcp/scripts/bootstrap-host.sh > /tmp/bootstrap-runner-prefix.sh
sed -i 's#RUNNER_CGROUP_ROOT="/sys/fs/cgroup"#RUNNER_CGROUP_ROOT="/tmp/test-cgroup"#' \
  /tmp/bootstrap-runner-prefix.sh

cat > /test-bin/systemctl <<'EOF'
#!/bin/sh
printf '%s\n' "$*" >> /tmp/systemctl.calls
mode="$(cat /tmp/test-mode)"
command_name="$1"
shift
case "$command_name" in
  poweroff)
    [ "${1:-}" = --no-block ] || exit 90
    printf 'poweroff\n' >> /tmp/poweroff-calls
    ;;
  disable)
    if [ "$mode" = legacy-disable-failure ]; then
      exit 41
    fi
    ;;
  stop)
    if [ "$mode" = standard-stop-failure ]; then
      exit 42
    fi
    touch /tmp/standard-stopped
    ;;
  show)
    service_name=''
    for argument in "$@"; do
      service_name="$argument"
    done
    if [ "${1:-}" = --property=ControlGroup ]; then
      if [ "$mode" = lingering-child ] &&
        [ "$service_name" = actions.runner.GoLe.beta.service ]; then
        printf '/legacy-beta\n'
      else
        printf '\n'
      fi
    elif [ "$mode" = lingering-legacy ] &&
      [ "$service_name" = actions.runner.GoLe.beta.service ]; then
      printf 'active\n'
    elif { [ "$mode" = standard-stop-failure ] ||
      [ "$mode" = standard-active-success ]; } &&
      [ "$service_name" = gole-github-runner.service ] &&
      [ ! -e /tmp/standard-stopped ]; then
      printf 'active\n'
    else
      printf 'inactive\n'
    fi
    ;;
  *) exit 91 ;;
esac
EOF
chmod 0755 /test-bin/systemctl
export PATH="/test-bin:/usr/sbin:/usr/bin:/sbin:/bin"

run_prefix() {
  env \
    BOOTSTRAP_SOURCE_SHA="$bootstrap_sha" \
    GCP_PROJECT_ID=test-project \
    GCP_VM_COST_START=2026-09-01T19:57:05+09:00 \
    GCP_HARD_STOP_AT=2026-10-28T01:50:00+09:00 \
    GCP_CREDIT_DEADLINE=2026-10-28T23:59:59+09:00 \
    GCP_RUNTIME_RATE_TRANSITION_AT=2026-09-06T00:00:00+09:00 \
    GCP_EXPECTED_BUDGET_ID=00000000-0000-4000-8000-000000000001 \
    GCP_EXPECTED_BILLING_ACCOUNT_ID=ABCDEF-123456-ABCDEF \
    bash /tmp/bootstrap-runner-prefix.sh
}

reset_case() {
  printf '%s\n' "$1" > /tmp/test-mode
  rm -f /tmp/systemctl.calls /tmp/poweroff-calls /tmp/standard-stopped
  rm -rf /tmp/test-cgroup
  mkdir -p /tmp/test-cgroup
  if [ "$1" = lingering-child ]; then
    mkdir -p /tmp/test-cgroup/legacy-beta
    printf '4242\n' > /tmp/test-cgroup/legacy-beta/cgroup.procs
  fi
}

assert_one_poweroff() {
  [ "$(wc -l < /tmp/poweroff-calls | tr -d ' ')" -eq 1 ] || {
    echo "expected exactly one fail-closed poweroff for $1" >&2
    exit 1
  }
}

reset_case success
run_prefix
[ ! -e /tmp/poweroff-calls ]
grep -Fqx 'KillMode=control-group' "/etc/systemd/system/$standard_service"
grep -Fqx "disable --now $legacy_alpha" /tmp/systemctl.calls
grep -Fqx "disable --now $legacy_beta" /tmp/systemctl.calls
grep -Fqx "show --property=ActiveState --value $legacy_alpha" /tmp/systemctl.calls
grep -Fqx "show --property=ActiveState --value $legacy_beta" /tmp/systemctl.calls

reset_case legacy-disable-failure
if run_prefix > /tmp/disable-failure.out 2>&1; then
  echo 'bootstrap accepted a failed legacy runner disable/stop' >&2
  exit 1
fi
grep -Fq 'failed to stop and disable legacy runner service' /tmp/disable-failure.out
assert_one_poweroff legacy-disable-failure

reset_case lingering-legacy
if run_prefix > /tmp/lingering.out 2>&1; then
  echo 'bootstrap accepted a legacy runner that remained active' >&2
  exit 1
fi
grep -Fq "legacy runner service did not become inactive: $legacy_beta" /tmp/lingering.out
assert_one_poweroff lingering-legacy

reset_case lingering-child
if run_prefix > /tmp/lingering-child.out 2>&1; then
  echo 'bootstrap accepted a stopped runner with a live cgroup child' >&2
  exit 1
fi
grep -Fq "runner child processes remain after service stop: $legacy_beta" \
  /tmp/lingering-child.out
assert_one_poweroff lingering-child

reset_case standard-stop-failure
if run_prefix > /tmp/stop-failure.out 2>&1; then
  echo 'bootstrap accepted a failed standard runner stop' >&2
  exit 1
fi
grep -Fq 'failed to stop standard runner service' /tmp/stop-failure.out
assert_one_poweroff standard-stop-failure

reset_case standard-active-success
run_prefix
[ -e /tmp/standard-stopped ]
[ ! -e /tmp/poweroff-calls ]
grep -Fqx "stop $standard_service" /tmp/systemctl.calls

echo 'Bootstrap runner quiesce fail-closed runtime test passed.'
CONTAINER_TEST
