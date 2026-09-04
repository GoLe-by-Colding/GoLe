#!/usr/bin/env bash
set -Eeuo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
DEPLOY_SCRIPT="$REPO_ROOT/scripts/deploy.sh"
DEPLOY_SHA_VALUE='2222222222222222222222222222222222222222'

line_of() {
  local pattern="$1"
  grep -n -m1 -- "$pattern" "$TEST_ROOT/events" | cut -d: -f1
}

assert_no_gole_https_before_certificate() {
  local certificate_line https_line
  certificate_line="$(line_of '^hostctl certificate-issue $')"
  [ -n "$certificate_line" ]
  while IFS=: read -r https_line _; do
    [ "$https_line" -gt "$certificate_line" ] || {
      echo "gole.co.kr HTTPS was attempted before certificate issuance" >&2
      sed -n '1,240p' "$TEST_ROOT/events" >&2
      return 1
    }
  done < <(grep -n '^curl-gole-https ' "$TEST_ROOT/events" || true)
}

install_fakes() {
  mkdir -p "$TEST_ROOT/bin" "$TEST_ROOT/scripts" "$TEST_ROOT/infra/gcp/scripts"
  cp "$DEPLOY_SCRIPT" "$TEST_ROOT/scripts/deploy.sh"
  : > "$TEST_ROOT/infra/gcp/docker-compose.yml"
  : > "$TEST_ROOT/events"
  : > "$TEST_ROOT/rollout.lock"
  printf '%s\n' "$DEPLOY_SHA_VALUE" > "$TEST_ROOT/git-head"

  cat > "$TEST_ROOT/bin/sudo" <<'EOF'
#!/bin/sh
[ "$1" = -n ] || exit 80
shift
exec "$@"
EOF

  cat > "$TEST_ROOT/bin/flock" <<'EOF'
#!/bin/sh
set -eu
case "$1" in
  -u)
    [ "$2" = 7 ]
    printf 'flock-unlock 7\n' >> "$TEST_ROOT/events"
    printf 'unlocked\n' > "$TEST_ROOT/lock-state"
    ;;
  -n)
    [ "$2" = 7 ]
    printf 'flock-lock 7\n' >> "$TEST_ROOT/events"
    printf 'held\n' > "$TEST_ROOT/lock-state"
    ;;
  *) exit 81 ;;
esac
EOF

  cat > "$TEST_ROOT/bin/curl" <<'EOF'
#!/usr/bin/env bash
set -eu
url="${*: -1}"
case "$url" in
  https://gole.co.kr/*)
    printf 'curl-gole-https %s\n' "$url" >> "$TEST_ROOT/events"
    [ -e "$TEST_ROOT/certificate-issued" ] || exit 82
    ;;
  http://*) printf 'curl-http %s\n' "$url" >> "$TEST_ROOT/events" ;;
  *) printf 'curl-other %s\n' "$url" >> "$TEST_ROOT/events" ;;
esac
EOF

  cat > "$TEST_ROOT/bin/git" <<'EOF'
#!/bin/sh
set -eu
case "$1" in
  rev-parse)
    case "${2:-}" in
      HEAD|refs/remotes/origin/main) cat "$TEST_ROOT/git-head" ;;
      *) cat "$TEST_ROOT/git-head" ;;
    esac
    ;;
  reset) printf '%s\n' "$3" > "$TEST_ROOT/git-head" ;;
  status|fetch|cat-file) : ;;
  *) exit 83 ;;
esac
EOF

  cat > "$TEST_ROOT/bin/docker" <<'EOF'
#!/bin/sh
echo 'runner reached unrestricted Docker command' >&2
exit 84
EOF

  cat > "$TEST_ROOT/bin/prepare-nginx" <<'EOF'
#!/bin/sh
set -eu
compact="$(printf '%s' "$1" | tr -d '-')"
printf 'server {}\n' > "/tmp/gole-nginx.$compact"
chmod 0600 "/tmp/gole-nginx.$compact"
EOF

  cat > "$TEST_ROOT/bin/hostctl" <<'EOF'
#!/usr/bin/env bash
set -Eeuo pipefail
command="$1"
shift
printf 'hostctl %s %s\n' "$command" "$*" >> "$TEST_ROOT/events"

case "$command" in
  discord-overlay-verify) : ;;
  deployment-recover)
    if [ "$TEST_MODE" = recovery ] && [ ! -e "$TEST_ROOT/recovery-returned" ]; then
      : > "$TEST_ROOT/recovery-returned"
      curl -fsS http://127.0.0.1/initial-recovery-proof >/dev/null
      printf 'INITIAL_TLS_REQUIRED\n'
    else
      printf 'NONE\n'
    fi
    ;;
  deployment-read-sha)
    [ -f "$TEST_ROOT/deployed.sha" ]
    cat "$TEST_ROOT/deployed.sha"
    ;;
  deployment-is-uninitialized)
    [ "$TEST_MODE" = fresh ]
    [ ! -e "$TEST_ROOT/deployed.sha" ]
    [ ! -e "$TEST_ROOT/deployment-active" ]
    ;;
  deployment-begin)
    [ ! -e "$TEST_ROOT/deployment-active" ]
    : > "$TEST_ROOT/deployment-active"
    printf '%s\n' "$3" > "$TEST_ROOT/transaction-previous-sha"
    printf 'prepared\n' > "$TEST_ROOT/transaction-state"
    ;;
  deployment-images-snapshot)
    [ -e "$TEST_ROOT/deployment-active" ]
    printf 'snapshotted\n' > "$TEST_ROOT/transaction-state"
    ;;
  deployment-compose-build)
    [ "$(cat "$TEST_ROOT/transaction-state")" = snapshotted ]
    printf 'built\n' > "$TEST_ROOT/transaction-state"
    ;;
  nginx-transaction-begin)
    [ "$(cat "$TEST_ROOT/transaction-state")" = built ]
    : > "$TEST_ROOT/nginx-active"
    printf 'nginx-installed\n' > "$TEST_ROOT/transaction-state"
    ;;
  deployment-compose-up)
    [ -e "$TEST_ROOT/deployment-active" ]
    printf 'mutated\n' > "$TEST_ROOT/transaction-state"
    ;;
  deployment-budget-healthy|watchdog-install|watchdog-active|deployment-compose-ps) : ;;
  deployment-verify-candidate-runtime)
    [ -e "$TEST_ROOT/deployment-active" ]
    if [ "$(cat "$TEST_ROOT/transaction-previous-sha")" = 0 ]; then
      [ ! -e "$TEST_ROOT/certificate-issued" ]
      curl -fsS http://127.0.0.1/initial-candidate-proof >/dev/null
    else
      curl -fsS https://gole.co.kr/regular-candidate-proof >/dev/null
    fi
    printf 'verified\n' > "$TEST_ROOT/transaction-state"
    ;;
  nginx-transaction-commit)
    [ "$(cat "$TEST_ROOT/transaction-state")" = verified ]
    [ -e "$TEST_ROOT/nginx-active" ]
    : > "$TEST_ROOT/nginx-committed"
    ;;
  deployment-record-sha)
    [ -e "$TEST_ROOT/nginx-committed" ]
    printf '%s\n' "$1" > "$TEST_ROOT/deployed.sha"
    printf 'marker-recorded\n' > "$TEST_ROOT/transaction-state"
    ;;
  deployment-verify-initial-http-commit)
    [ "$(cat "$TEST_ROOT/transaction-previous-sha")" = 0 ]
    [ "$(cat "$TEST_ROOT/transaction-state")" = marker-recorded ]
    [ ! -e "$TEST_ROOT/certificate-issued" ]
    curl -fsS http://127.0.0.1/initial-commit-proof >/dev/null
    printf 'initial-http-verified\n' > "$TEST_ROOT/transaction-state"
    ;;
  nginx-transaction-finalize)
    [ -e "$TEST_ROOT/nginx-committed" ]
    if [ "$(cat "$TEST_ROOT/transaction-previous-sha")" = 0 ]; then
      [ "$(cat "$TEST_ROOT/transaction-state")" = initial-http-verified ]
    else
      [ "$(cat "$TEST_ROOT/transaction-state")" = runtime-verified ]
    fi
    rm -f "$TEST_ROOT/nginx-active" "$TEST_ROOT/nginx-committed"
    ;;
  certificate-issue)
    [ "$(cat "$TEST_ROOT/lock-state")" = unlocked ]
    [ "$(cat "$TEST_ROOT/transaction-state")" = initial-http-verified ]
    [ ! -e "$TEST_ROOT/nginx-active" ]
    : > "$TEST_ROOT/certificate-issued"
    ;;
  deployment-complete-initial-tls)
    [ -e "$TEST_ROOT/certificate-issued" ]
    [ "$(cat "$TEST_ROOT/lock-state")" = held ]
    [ "$(cat "$TEST_ROOT/transaction-state")" = initial-http-verified ]
    curl -fsS https://gole.co.kr/initial-full-tls-proof >/dev/null
    rm -f "$TEST_ROOT/deployment-active"
    printf 'initial-complete\n' > "$TEST_ROOT/transaction-state"
    ;;
  deployment-verify-commit)
    [ "$(cat "$TEST_ROOT/transaction-previous-sha")" != 0 ]
    [ "$(cat "$TEST_ROOT/transaction-state")" = marker-recorded ]
    curl -fsS https://gole.co.kr/regular-commit-proof >/dev/null
    printf 'runtime-verified\n' > "$TEST_ROOT/transaction-state"
    ;;
  deployment-finalize)
    [ "$(cat "$TEST_ROOT/transaction-state")" = runtime-verified ]
    [ ! -e "$TEST_ROOT/nginx-active" ]
    rm -f "$TEST_ROOT/deployment-active"
    printf 'regular-complete\n' > "$TEST_ROOT/transaction-state"
    ;;
  deployment-images-cleanup) : ;;
  deployment-rollback|deployment-fail-closed|deployment-fail-closed-initial-tls)
    printf 'unexpected failure action\n' >&2
    exit 85
    ;;
  *) exit 86 ;;
esac
EOF

  chmod 0755 "$TEST_ROOT/bin/"*
}

export_cost_guard_contract() {
  local variable_name
  local -a required_variables=(
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
    export "$variable_name=contract"
  done
  export GCP_HARD_STOP_ENABLED=true GCP_HARD_STOP_DRY_RUN=false
}

run_case() {
  local mode="$1"
  TEST_ROOT="$(mktemp -d)"
  export TEST_ROOT TEST_MODE="$mode"
  install_fakes
  export_cost_guard_contract
  export DEPLOY_SHA="$DEPLOY_SHA_VALUE"
  export GOLE_DEPLOY_ROOT="$TEST_ROOT"
  export GOLE_TEST_HOSTCTL="$TEST_ROOT/bin/hostctl"
  export GOLE_TEST_PREPARE_NGINX="$TEST_ROOT/bin/prepare-nginx"
  export GOLE_TEST_HOST_DOCKER_CONTROL=1
  export GOLE_INITIAL_DEPLOY=0
  if [ "$mode" = fresh ]; then
    export GOLE_INITIAL_DEPLOY=1
  else
    printf '%s\n' "$DEPLOY_SHA_VALUE" > "$TEST_ROOT/deployed.sha"
    : > "$TEST_ROOT/deployment-active"
    printf '0\n' > "$TEST_ROOT/transaction-previous-sha"
    printf 'initial-http-verified\n' > "$TEST_ROOT/transaction-state"
  fi

  exec 7>>"$TEST_ROOT/rollout.lock"
  PATH="$TEST_ROOT/bin:$PATH" GOLE_ROLLOUT_LOCK_HELD=1 \
    bash "$TEST_ROOT/scripts/deploy.sh" all > "$TEST_ROOT/output" 2>&1
  exec 7>&-
}

run_case fresh
[ "$(cat "$TEST_ROOT/deployed.sha")" = "$DEPLOY_SHA_VALUE" ]
[ "$(cat "$TEST_ROOT/transaction-state")" = initial-complete ]
[ ! -e "$TEST_ROOT/deployment-active" ]
assert_no_gole_https_before_certificate
fresh_http_line="$(line_of '^hostctl deployment-verify-initial-http-commit ')"
fresh_nginx_line="$(line_of '^hostctl nginx-transaction-finalize ')"
fresh_unlock_line="$(line_of '^flock-unlock 7$')"
fresh_certificate_line="$(line_of '^hostctl certificate-issue $')"
fresh_relock_line="$(awk '/^hostctl certificate-issue $/{seen=1; next} seen && /^flock-lock 7$/{print NR; exit}' "$TEST_ROOT/events")"
fresh_tls_line="$(line_of '^hostctl deployment-complete-initial-tls $')"
[ "$fresh_http_line" -lt "$fresh_nginx_line" ]
[ "$fresh_nginx_line" -lt "$fresh_unlock_line" ]
[ "$fresh_unlock_line" -lt "$fresh_certificate_line" ]
[ "$fresh_certificate_line" -lt "$fresh_relock_line" ]
[ "$fresh_relock_line" -lt "$fresh_tls_line" ]
grep -q "^hostctl deployment-begin all $DEPLOY_SHA_VALUE 0 " "$TEST_ROOT/events"
! grep -q '^hostctl deployment-verify-commit ' "$TEST_ROOT/events"
! grep -q '^hostctl deployment-finalize ' "$TEST_ROOT/events"
rm -rf -- "$TEST_ROOT"

run_case recovery
[ "$(cat "$TEST_ROOT/deployed.sha")" = "$DEPLOY_SHA_VALUE" ]
[ "$(cat "$TEST_ROOT/transaction-state")" = regular-complete ]
[ ! -e "$TEST_ROOT/deployment-active" ]
assert_no_gole_https_before_certificate
recovery_line="$(line_of '^hostctl deployment-recover $')"
recovery_certificate_line="$(line_of '^hostctl certificate-issue $')"
recovery_tls_line="$(line_of '^hostctl deployment-complete-initial-tls $')"
recovery_begin_line="$(line_of '^hostctl deployment-begin ')"
[ "$recovery_line" -lt "$recovery_certificate_line" ]
[ "$recovery_certificate_line" -lt "$recovery_tls_line" ]
[ "$recovery_tls_line" -lt "$recovery_begin_line" ]
grep -q "^hostctl deployment-begin all $DEPLOY_SHA_VALUE $DEPLOY_SHA_VALUE " "$TEST_ROOT/events"
grep -q '^hostctl deployment-verify-commit ' "$TEST_ROOT/events"
grep -q '^hostctl deployment-finalize ' "$TEST_ROOT/events"
! grep -q '^hostctl deployment-is-uninitialized ' "$TEST_ROOT/events"
rm -rf -- "$TEST_ROOT"

echo 'Initial certless deployment and TLS recovery integration tests passed.'
