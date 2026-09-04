#!/usr/bin/env bash
set -Eeuo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
TEST_ROOT="$(mktemp -d)"
cleanup() { rm -rf -- "$TEST_ROOT"; }
trap cleanup EXIT
OLD_SHA='1111111111111111111111111111111111111111'
NEW_SHA='2222222222222222222222222222222222222222'
mkdir -p "$TEST_ROOT/bin" "$TEST_ROOT/scripts" "$TEST_ROOT/infra/gcp/scripts"
cp "$REPO_ROOT/scripts/deploy.sh" "$TEST_ROOT/scripts/deploy.sh"
: > "$TEST_ROOT/infra/gcp/docker-compose.yml"
printf '%s\n' "$OLD_SHA" > "$TEST_ROOT/deployed.sha"
printf '%s\n' "$OLD_SHA" > "$TEST_ROOT/git-head"
: > "$TEST_ROOT/events"
: > "$TEST_ROOT/rollout.lock"

cat > "$TEST_ROOT/bin/sudo" <<'EOF'
#!/bin/sh
[ "$1" = -n ] || exit 80
shift
exec "$@"
EOF
cat > "$TEST_ROOT/bin/hostctl" <<'EOF'
#!/bin/sh
set -eu
command="$1"
shift
printf '%s %s\n' "$command" "$*" >> "$TEST_ROOT/events"
case "$command" in
  discord-overlay-install) cat > "$TEST_ROOT/discord-request" ;;
  deployment-read-sha) cat "$TEST_ROOT/deployed.sha" ;;
  deployment-recover) echo NONE ;;
  deployment-record-sha) printf '%s\n' "$1" > "$TEST_ROOT/deployed.sha" ;;
  deployment-compose-ps) echo 'root-only status' ;;
  deployment-begin|deployment-images-snapshot|deployment-images-cleanup|deployment-compose-build|\
  deployment-compose-up|deployment-budget-healthy|deployment-verify-candidate-runtime|\
  deployment-verify-commit|deployment-finalize|\
  nginx-transaction-begin|nginx-transaction-commit|nginx-transaction-finalize|\
  watchdog-install|watchdog-active|discord-overlay-verify) : ;;
  *) exit 81 ;;
esac
EOF
cat > "$TEST_ROOT/bin/prepare-nginx" <<'EOF'
#!/bin/sh
compact="$(printf '%s' "$1" | tr -d '-')"
printf 'server {}\n' > "/tmp/gole-nginx.$compact"
chmod 0600 "/tmp/gole-nginx.$compact"
EOF
cat > "$TEST_ROOT/bin/git" <<'EOF'
#!/bin/sh
set -eu
case "$1" in
  rev-parse) cat "$TEST_ROOT/git-head" ;;
  reset) printf '%s\n' "$3" > "$TEST_ROOT/git-head" ;;
  status|fetch|cat-file) : ;;
  *) exit 82 ;;
esac
EOF
cat > "$TEST_ROOT/bin/docker" <<'EOF'
#!/bin/sh
echo 'runner reached unrestricted Docker command' >&2
exit 99
EOF
cat > "$TEST_ROOT/bin/curl" <<'EOF'
#!/bin/sh
exit 0
EOF
cat > "$TEST_ROOT/bin/flock" <<'EOF'
#!/bin/sh
exit 0
EOF
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
for variable_name in "${required_variables[@]}"; do export "$variable_name=contract"; done
export GCP_HARD_STOP_ENABLED=true GCP_HARD_STOP_DRY_RUN=false
export DISCORD_OPERATIONS_WEBHOOK_URL='https://discord.com/api/webhooks/100000000000000001/ABCDEFGHIJKLMNOPQRSTUVWXYZabcdef_1000000001'
export DISCORD_ACCOUNT_WEBHOOK_URL='https://discord.com/api/webhooks/100000000000000002/ABCDEFGHIJKLMNOPQRSTUVWXYZabcdef_1000000002'
export DISCORD_PAYMENT_WEBHOOK_URL='https://discord.com/api/webhooks/100000000000000003/ABCDEFGHIJKLMNOPQRSTUVWXYZabcdef_1000000003'
export DISCORD_SUPPRESS_NOTIFICATIONS=false
export TEST_ROOT OLD_SHA NEW_SHA DEPLOY_SHA="$NEW_SHA"
export GOLE_DEPLOY_ROOT="$TEST_ROOT"
export GOLE_TEST_HOSTCTL="$TEST_ROOT/bin/hostctl"
export GOLE_TEST_PREPARE_NGINX="$TEST_ROOT/bin/prepare-nginx"
export GOLE_TEST_HOST_DOCKER_CONTROL=1

exec 7>>"$TEST_ROOT/rollout.lock"
flock -n 7
PATH="$TEST_ROOT/bin:$PATH" GOLE_ROLLOUT_LOCK_HELD=1 \
  bash "$TEST_ROOT/scripts/deploy.sh" all >/tmp/deploy-hostctl.out 2>&1

[ "$(cat "$TEST_ROOT/deployed.sha")" = "$NEW_SHA" ]
test "$(head -n 1 "$TEST_ROOT/events")" = 'discord-overlay-verify '
grep -q '^deployment-compose-build all ' "$TEST_ROOT/events"
grep -q "^deployment-begin all $NEW_SHA $OLD_SHA " "$TEST_ROOT/events"
grep -q '^deployment-compose-up rollout-all-apps ' "$TEST_ROOT/events"
grep -q '^deployment-compose-up rollout-budget ' "$TEST_ROOT/events"
grep -q "^deployment-verify-candidate-runtime $NEW_SHA" "$TEST_ROOT/events"
grep -q "^deployment-verify-commit $NEW_SHA" "$TEST_ROOT/events"
candidate_line="$(grep -n '^deployment-verify-candidate-runtime ' "$TEST_ROOT/events" | cut -d: -f1)"
marker_line="$(grep -n '^deployment-record-sha ' "$TEST_ROOT/events" | cut -d: -f1)"
verify_line="$(grep -n '^deployment-verify-commit ' "$TEST_ROOT/events" | cut -d: -f1)"
finalize_line="$(grep -n '^nginx-transaction-finalize ' "$TEST_ROOT/events" | cut -d: -f1)"
[ "$candidate_line" -lt "$marker_line" ]
[ "$marker_line" -lt "$verify_line" ]
[ "$verify_line" -lt "$finalize_line" ]

echo 'Production deploy root-helper boundary contract passed.'
