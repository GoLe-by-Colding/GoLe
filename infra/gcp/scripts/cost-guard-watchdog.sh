#!/usr/bin/env bash
set -Eeuo pipefail

CONTAINER_NAME="${GOLE_COST_GUARD_CONTAINER_NAME:-gole-budget-relay}"
FAILURE_FILE="${GOLE_COST_GUARD_FAILURE_FILE:-/run/gole-cost-guard-watchdog.failures}"
MAX_FAILURES="${GOLE_COST_GUARD_MAX_FAILURES:-2}"
DRY_RUN="${GOLE_COST_GUARD_WATCHDOG_DRY_RUN:-false}"
BROKER_SERVICE="${GOLE_CLOUD_BROKER_SERVICE:-gole-cloud-broker.service}"
BROKER_HEARTBEAT="${GOLE_CLOUD_BROKER_HEARTBEAT:-/run/gole-cloud-broker/policy-heartbeat}"
BROKER_HEARTBEAT_MAX_AGE="${GOLE_CLOUD_BROKER_HEARTBEAT_MAX_AGE:-45}"

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

broker_status="inactive"
broker_heartbeat_status="missing"
if timeout 5s systemctl is-active --quiet "$BROKER_SERVICE"; then
  broker_status="active"
fi
if [[ "$BROKER_HEARTBEAT_MAX_AGE" =~ ^[1-9][0-9]*$ ]] &&
  [ -f "$BROKER_HEARTBEAT" ] && [ ! -L "$BROKER_HEARTBEAT" ] &&
  [ "$(stat -c '%U:%G:%a' "$BROKER_HEARTBEAT" 2>/dev/null || true)" = "root:golecloud:600" ]; then
  heartbeat_age=$(($(date +%s) - $(stat -c %Y "$BROKER_HEARTBEAT")))
  if [ "$heartbeat_age" -ge 0 ] && [ "$heartbeat_age" -le "$BROKER_HEARTBEAT_MAX_AGE" ]; then
    broker_heartbeat_status="fresh"
  fi
fi

if [ "$status" = "true healthy" ] && [ "$broker_status" = active ] &&
  [ "$broker_heartbeat_status" = fresh ]; then
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
  "cost guard unhealthy (${failures}/${MAX_FAILURES}): relay=${status:-missing} broker=${broker_status} policy=${broker_heartbeat_status}" || true

if [ "$failures" -lt "$MAX_FAILURES" ]; then
  exit 0
fi

power_off
