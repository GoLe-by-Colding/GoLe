#!/usr/bin/env bash
set -Eeuo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
DEPLOY_SCRIPT="$REPO_ROOT/scripts/deploy.sh"
OLD_SHA='1111111111111111111111111111111111111111'
NEW_SHA='2222222222222222222222222222222222222222'

run_case() {
  local mode="$1"
  TEST_ROOT="$(mktemp -d)"
  mkdir -p "$TEST_ROOT/bin" "$TEST_ROOT/scripts" "$TEST_ROOT/infra/gcp/scripts"
  cp "$DEPLOY_SCRIPT" "$TEST_ROOT/scripts/deploy.sh"
  : > "$TEST_ROOT/infra/gcp/docker-compose.yml"
  printf 'old-nginx-config\n' > "$TEST_ROOT/nginx.conf"
  printf '%s\n' "$OLD_SHA" > "$TEST_ROOT/deployed.sha"
  printf '%s\n' "$OLD_SHA" > "$TEST_ROOT/git-head"
  : > "$TEST_ROOT/events"

  cat > "$TEST_ROOT/bin/sudo" <<'FAKE_SUDO'
#!/bin/sh
[ "$1" = '-n' ] || exit 90
shift
exec "$@"
FAKE_SUDO

  cat > "$TEST_ROOT/bin/hostctl" <<'FAKE_HOSTCTL'
#!/usr/bin/env bash
set -eu
command="$1"
shift
printf 'hostctl %s %s\n' "$command" "$*" >> "$TEST_ROOT/events"
case "$command" in
  deployment-read-sha) cat "$TEST_ROOT/deployed.sha" ;;
  deployment-recover) printf 'NONE\n' ;;
  deployment-begin) : > "$TEST_ROOT/deployment.transaction" ;;
  deployment-record-sha)
    if [ "${FAIL_MARKER:-0}" = 1 ] && [ "$1" = "$NEW_SHA" ]; then exit 41; fi
    printf '%s\n' "$1" > "$TEST_ROOT/deployed.sha"
    ;;
  nginx-transaction-begin)
    cp "$TEST_ROOT/nginx.conf" "$TEST_ROOT/nginx.backup"
    printf 'new-nginx-config\n' > "$TEST_ROOT/nginx.conf"
    : > "$TEST_ROOT/nginx.transaction"
    ;;
  nginx-transaction-commit) : ;;
  nginx-transaction-finalize) rm -f "$TEST_ROOT/nginx.transaction" ;;
  deployment-rollback)
    [ "${FAIL_ROLLBACK:-0}" != 1 ] || exit 55
    cp "$TEST_ROOT/nginx.backup" "$TEST_ROOT/nginx.conf"
    printf '%s\n' "$OLD_SHA" > "$TEST_ROOT/deployed.sha"
    rm -f "$TEST_ROOT/nginx.transaction" "$TEST_ROOT/deployment.transaction"
    ;;
  deployment-fail-closed) : ;;
  deployment-images-snapshot | deployment-compose-build | deployment-compose-up | \
    deployment-compose-ps | deployment-budget-healthy | \
    deployment-verify-candidate-runtime | deployment-verify-commit | \
    discord-overlay-verify | watchdog-install | watchdog-active) : ;;
  deployment-finalize)
    rm -f "$TEST_ROOT/deployment.transaction"
    ;;
  watchdog-install | watchdog-active) : ;;
  *) exit 42 ;;
esac
FAKE_HOSTCTL

  cat > "$TEST_ROOT/bin/prepare-nginx" <<'FAKE_PREPARE'
#!/usr/bin/env bash
set -eu
compact="${1//-/}"
printf 'new-nginx-config\n' > "/tmp/gole-nginx.$compact"
chmod 0600 "/tmp/gole-nginx.$compact"
FAKE_PREPARE

  cat > "$TEST_ROOT/bin/git" <<'FAKE_GIT'
#!/usr/bin/env bash
set -eu
printf 'git %s\n' "$*" >> "$TEST_ROOT/events"
case "${1:-}" in
  rev-parse) cat "$TEST_ROOT/git-head" ;;
  reset)
    printf '%s\n' "${3:-}" > "$TEST_ROOT/git-head"
    ;;
  status | fetch | cat-file) : ;;
esac
FAKE_GIT

  cat > "$TEST_ROOT/bin/docker" <<'FAKE_DOCKER'
#!/usr/bin/env bash
set -eu
printf 'docker %s\n' "$*" >> "$TEST_ROOT/events"
if [ "${1:-}" = inspect ]; then
  if [[ "$*" == *'.Image'* ]]; then
    printf 'sha256:old-image-%s\n' "${*: -1}"
  else
    printf 'healthy\n'
  fi
  exit 0
fi
if [[ " $* " == *' config --services '* ]]; then
  printf '%s\n' support-agent backend frontend budget-relay nginx
fi
FAKE_DOCKER

  cat > "$TEST_ROOT/bin/curl" <<'FAKE_CURL'
#!/usr/bin/env bash
set -eu
url="${*: -1}"
printf 'curl %s\n' "$url" >> "$TEST_ROOT/events"
printf 'curl-args %s\n' "$*" >> "$TEST_ROOT/events"
if [ "${FAIL_SMOKE:-0}" = 1 ] && [[ "$url" == *'/api/v1/catalog/sets/featured'* ]] &&
  [ ! -e "$TEST_ROOT/smoke-failed" ]; then
  : > "$TEST_ROOT/smoke-failed"
  exit 1
fi
FAKE_CURL

  cat > "$TEST_ROOT/bin/flock" <<'FAKE_FLOCK'
#!/bin/sh
exit 0
FAKE_FLOCK

  chmod 0755 "$TEST_ROOT/bin/"*
  required_variables=(
    GCP_BUDGET_PUBSUB_SUBSCRIPTION GCP_PROJECT_ID GCP_CREDIT_AMOUNT_KRW
    GCP_CREDIT_DEADLINE GCP_FIXED_HOURLY_COST_KRW GCP_HARD_STOP_ENABLED
    GCP_HARD_STOP_DRY_RUN GCP_HARD_STOP_BILLING_COST_KRW
    GCP_HARD_STOP_MIN_RESERVE_KRW GCP_HARD_STOP_ALL_IN_COST_KRW
    GCP_COST_GUARD_WARNING_KRW GCP_COST_GUARD_DANGER_KRW
    GCP_HARD_STOP_NETWORK_GIB GCP_COST_GUARD_NETWORK_WARNING_GIB
    GCP_COST_GUARD_NETWORK_DANGER_GIB GCP_HARD_STOP_MAX_RUNTIME_HOURS
    GCP_COST_GUARD_RUNTIME_WARNING_HOURS GCP_COST_GUARD_RUNTIME_DANGER_HOURS
    GCP_HARD_STOP_EXPECTED_BUDGET_KRW GCP_HARD_STOP_BUDGET_ID
    GCP_HARD_STOP_BILLING_ACCOUNT_ID GCP_HARD_STOP_BUDGET_DISPLAY_NAME
    GCP_HARD_STOP_PERIOD_START GCP_VM_COST_START GCP_HARD_STOP_AT
    GCP_HARD_STOP_ARM_ID GCP_INSTANCE_ZONE GCP_INSTANCE_NAME GCP_VAT_RATE
    GCP_NETWORK_EGRESS_KRW_PER_GIB GCP_STOPPED_RESOURCE_HOURLY_COST_KRW
    GCP_COST_GUARD_INTERVAL_SECONDS GCP_HARD_STOP_RETRY_SECONDS
    BUDGET_HTTP_TIMEOUT_SECONDS
  )
  for variable_name in "${required_variables[@]}"; do
    export "$variable_name=contract-value"
  done
  export GCP_HARD_STOP_ENABLED=true GCP_HARD_STOP_DRY_RUN=false
  export TEST_ROOT OLD_SHA NEW_SHA
  export GOLE_DEPLOY_ROOT="$TEST_ROOT"
  export GOLE_TEST_HOSTCTL="$TEST_ROOT/bin/hostctl"
  export GOLE_TEST_PREPARE_NGINX="$TEST_ROOT/bin/prepare-nginx"
  export GOLE_TEST_HOST_DOCKER_CONTROL=1
  export DEPLOY_SHA="$NEW_SHA" GITHUB_RUN_ID=999
  export FAIL_SMOKE=0 FAIL_MARKER=0 FAIL_ROLLBACK=0
  case "$mode" in
    smoke-failure) export FAIL_SMOKE=1 ;;
    marker-failure) export FAIL_MARKER=1 ;;
    rollback-failure) export FAIL_SMOKE=1 FAIL_ROLLBACK=1 ;;
    success) ;;
  esac

  : > "$TEST_ROOT/rollout.lock"
  exec 7>>"$TEST_ROOT/rollout.lock"
  flock -n 7
  set +e
  PATH="$TEST_ROOT/bin:$PATH" GOLE_ROLLOUT_LOCK_HELD=1 \
    DISCORD_DEPLOY_WEBHOOK_URL=https://discord.invalid/deploy \
    bash "$TEST_ROOT/scripts/deploy.sh" all > "$TEST_ROOT/output" 2>&1
  CASE_STATUS=$?
  set -e
  exec 7>&-
}

assert_rollback_case() {
  local mode="$1" rollback_line
  run_case "$mode"
  [ "$CASE_STATUS" -ne 0 ]
  grep -qx 'old-nginx-config' "$TEST_ROOT/nginx.conf"
  [ "$(cat "$TEST_ROOT/deployed.sha")" = "$OLD_SHA" ]
  [ ! -e "$TEST_ROOT/nginx.transaction" ]
  rollback_line="$(grep -n 'hostctl deployment-rollback' "$TEST_ROOT/events" | cut -d: -f1)"
  [ -n "$rollback_line" ]
  [ ! -e "$TEST_ROOT/deployment.transaction" ]
  rm -rf "$TEST_ROOT"
}

assert_rollback_case smoke-failure
assert_rollback_case marker-failure

# A data-mutation rollback deliberately fails closed until an explicit logical
# restore. deploy.sh must never describe that outcome as automatic LKG
# recovery; it emits the safe-stop notification and preserves the transaction.
run_case rollback-failure
[ "$CASE_STATUS" -ne 0 ]
grep -q 'hostctl deployment-rollback' "$TEST_ROOT/events"
grep -q 'hostctl deployment-fail-closed' "$TEST_ROOT/events"
grep -q '복구를 증명하지 못해 GCP VM을 안전 정지함' "$TEST_ROOT/events"
if grep -q '자동 복구함' "$TEST_ROOT/events"; then
  echo 'failed data rollback emitted a false automatic-recovery notification' >&2
  exit 1
fi
[ -e "$TEST_ROOT/deployment.transaction" ]
rm -rf "$TEST_ROOT"

run_case success
[ "$CASE_STATUS" -eq 0 ]
grep -qx 'new-nginx-config' "$TEST_ROOT/nginx.conf"
[ "$(cat "$TEST_ROOT/deployed.sha")" = "$NEW_SHA" ]
[ ! -e "$TEST_ROOT/nginx.transaction" ]
commit_line="$(grep -n 'hostctl nginx-transaction-commit' "$TEST_ROOT/events" | cut -d: -f1)"
marker_line="$(grep -n "hostctl deployment-record-sha $NEW_SHA" "$TEST_ROOT/events" | cut -d: -f1)"
finalize_line="$(grep -n 'hostctl nginx-transaction-finalize' "$TEST_ROOT/events" | cut -d: -f1)"
[ "$commit_line" -lt "$marker_line" ] && [ "$marker_line" -lt "$finalize_line" ]
grep -q 'hostctl deployment-finalize' "$TEST_ROOT/events"
rm -rf "$TEST_ROOT"

echo 'Nginx deploy transaction rollback integration tests passed.'
