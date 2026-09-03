#!/usr/bin/env bash
set -Eeuo pipefail

CONTAINER_NAME="${GOLE_COST_GUARD_CONTAINER_NAME:-gole-budget-relay}"
FAILURE_FILE="${GOLE_COST_GUARD_FAILURE_FILE:-/run/gole-cost-guard-watchdog.failures}"
MAX_FAILURES="${GOLE_COST_GUARD_MAX_FAILURES:-2}"
DRY_RUN="${GOLE_COST_GUARD_WATCHDOG_DRY_RUN:-false}"

power_off() {
  logger -p daemon.crit -t gole-cost-guard-watchdog \
    "cost guard remained unhealthy; powering off the VM to cap spend" || true
  if [ "$DRY_RUN" = "true" ]; then
    printf '비용 가드 비정상으로 VM 정지 조건을 충족함\n' >&2
    return 1
  fi
  systemctl poweroff --no-block
}

status="$(
  timeout 5s docker inspect \
    --format '{{.State.Running}} {{if .State.Health}}{{.State.Health.Status}}{{else}}missing{{end}}' \
    "$CONTAINER_NAME" 2>/dev/null || true
)"

if [ "$status" = "true healthy" ]; then
  rm -f "$FAILURE_FILE" || true
  exit 0
fi

if ! [[ "$MAX_FAILURES" =~ ^[1-9][0-9]*$ ]]; then
  MAX_FAILURES=1
fi

failures=0
if [ -r "$FAILURE_FILE" ]; then
  read -r failures < "$FAILURE_FILE" || failures=0
fi
if ! [[ "$failures" =~ ^[0-9]+$ ]]; then
  failures=0
fi
failures=$((failures + 1))
if ! printf '%s\n' "$failures" > "$FAILURE_FILE"; then
  power_off
  exit $?
fi

logger -p daemon.warning -t gole-cost-guard-watchdog \
  "cost guard unhealthy (${failures}/${MAX_FAILURES}): ${status:-missing}" || true

if [ "$failures" -lt "$MAX_FAILURES" ]; then
  exit 0
fi

power_off
