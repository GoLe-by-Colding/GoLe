#!/usr/bin/env bash
set -Eeuo pipefail

cd "$(dirname "$0")/.."
ROOT="$(pwd)"
TARGET="${1:-all}"
DEPLOY_SHA="${DEPLOY_SHA:-}"
COMPOSE=(docker compose --env-file /etc/gole/infra.env --env-file /etc/gole/gole.env -f "$ROOT/infra/gcp/docker-compose.yml")

log() { printf '\n▶ %s\n' "$*"; }

notify_discord() {
  local webhook_url="${DISCORD_DEPLOY_WEBHOOK_URL:-${DISCORD_OPERATIONS_WEBHOOK_URL:-}}"
  local avatar_url="${DISCORD_AVATAR_URL:-https://gole.co.kr/icon.svg}"
  local notification_flags=""
  local message="$1"
  if [ -z "$webhook_url" ]; then return 0; fi
  if [ "${DISCORD_SUPPRESS_NOTIFICATIONS:-true}" = "true" ]; then
    notification_flags=',"flags":4096'
  fi
  curl -fsS --max-time 5 -H 'Content-Type: application/json' \
    --data "{\"content\":\"${message}\",\"avatar_url\":\"${avatar_url}\",\"allowed_mentions\":{\"parse\":[]}${notification_flags}}" \
    "$webhook_url" >/dev/null || true
}

DEPLOY_RESULT_NOTIFIED=0
notify_deploy_result_once() {
  if [ "$DEPLOY_RESULT_NOTIFIED" = "1" ]; then return 0; fi
  DEPLOY_RESULT_NOTIFIED=1
  notify_discord "$1"
}

on_deploy_exit() {
  local status=$?
  if [ "$status" -ne 0 ]; then
    "${COMPOSE[@]}" ps || true
    if [ -n "${SECRET_SYNC_REQUEST_ID:-}" ]; then
      echo "Secret Sync 실패 로그는 민감정보 보호를 위해 Actions에 출력하지 않습니다." >&2
    else
      "${COMPOSE[@]}" logs --tail=100 backend frontend budget-relay nginx || true
    fi
    notify_deploy_result_once "❌ GoLe ${TARGET} 배포 실패 (exit ${status}) · gole.co.kr"
    if [ "$TARGET" = "all" ]; then
      notify_discord "🛑 전체 배포가 실패해 새 비용 가드 무장을 보장할 수 없으므로 GCP VM을 안전 정지합니다 · gole.co.kr"
      sudo systemctl poweroff --no-block || true
    fi
  fi
}
trap on_deploy_exit EXIT

notify_discord "🚀 GoLe ${TARGET} 배포 시작 · gole.co.kr"

if [ "$TARGET" = "all" ]; then
  required_cost_guard_variables=(
    GCP_BUDGET_PUBSUB_SUBSCRIPTION
    GCP_PROJECT_ID
    GCP_CREDIT_AMOUNT_KRW
    GCP_CREDIT_DEADLINE
    GCP_FIXED_HOURLY_COST_KRW
    GCP_HARD_STOP_ENABLED
    GCP_HARD_STOP_DRY_RUN
    GCP_HARD_STOP_BILLING_COST_KRW
    GCP_HARD_STOP_MIN_RESERVE_KRW
    GCP_HARD_STOP_ALL_IN_COST_KRW
    GCP_COST_GUARD_WARNING_KRW
    GCP_COST_GUARD_DANGER_KRW
    GCP_HARD_STOP_NETWORK_GIB
    GCP_COST_GUARD_NETWORK_WARNING_GIB
    GCP_COST_GUARD_NETWORK_DANGER_GIB
    GCP_HARD_STOP_MAX_RUNTIME_HOURS
    GCP_COST_GUARD_RUNTIME_WARNING_HOURS
    GCP_COST_GUARD_RUNTIME_DANGER_HOURS
    GCP_HARD_STOP_EXPECTED_BUDGET_KRW
    GCP_HARD_STOP_BUDGET_ID
    GCP_HARD_STOP_BILLING_ACCOUNT_ID
    GCP_HARD_STOP_BUDGET_DISPLAY_NAME
    GCP_HARD_STOP_PERIOD_START
    GCP_VM_COST_START
    GCP_HARD_STOP_AT
    GCP_HARD_STOP_ARM_ID
    GCP_INSTANCE_ZONE
    GCP_INSTANCE_NAME
    GCP_VAT_RATE
    GCP_NETWORK_EGRESS_KRW_PER_GIB
    GCP_STOPPED_RESOURCE_HOURLY_COST_KRW
    GCP_COST_GUARD_INTERVAL_SECONDS
    GCP_HARD_STOP_RETRY_SECONDS
    BUDGET_HTTP_TIMEOUT_SECONDS
  )
  for variable_name in "${required_cost_guard_variables[@]}"; do
    if [ -z "${!variable_name:-}" ]; then
      echo "필수 비용 가드 repository variable 누락: ${variable_name}" >&2
      exit 1
    fi
  done
  if [ "$GCP_HARD_STOP_ENABLED" != "true" ] || [ "$GCP_HARD_STOP_DRY_RUN" != "false" ]; then
    echo "운영 비용 가드는 enabled=true, dry_run=false로 명시적으로 무장해야 합니다." >&2
    exit 1
  fi
fi

log "CI가 검증한 origin/main 동기화"
git fetch --prune origin main

# 운영 checkout에 수동 변경이 있으면 덮어쓰지 않는다. 깨끗한 경우에만 원격 main을
# 정확히 반영해, 승인된 force push나 계정 이전 뒤에도 배포가 막히지 않게 한다.
if [ -n "$(git status --porcelain=v1 --untracked-files=all)" ]; then
  echo "운영 checkout에 커밋되지 않은 변경이 있어 배포를 중단한다" >&2
  git status --short >&2
  exit 1
fi

deploy_ref="origin/main"
if [ -n "$DEPLOY_SHA" ]; then
  if [[ ! "$DEPLOY_SHA" =~ ^[0-9a-f]{40}$ ]]; then
    echo "DEPLOY_SHA는 40자리 Git SHA여야 한다" >&2
    exit 1
  fi
  if ! git cat-file -e "${DEPLOY_SHA}^{commit}" 2>/dev/null; then
    git fetch --no-tags origin "$DEPLOY_SHA"
  fi
  git cat-file -e "${DEPLOY_SHA}^{commit}"
  deploy_ref="$DEPLOY_SHA"
fi

git reset --hard "$deploy_ref"
if [ -n "$DEPLOY_SHA" ] && [ "$(git rev-parse HEAD)" != "$DEPLOY_SHA" ]; then
  echo "CI가 검증한 커밋으로 checkout하지 못했다" >&2
  exit 1
fi

case "$TARGET" in
  backend)
    SERVICES=(backend nginx)
    ;;
  frontend)
    SERVICES=(frontend nginx)
    ;;
  all)
    SERVICES=(backend frontend budget-relay nginx)
    ;;
  *)
    echo "알 수 없는 대상: $TARGET (all|backend|frontend)" >&2
    exit 1
    ;;
esac

log "Docker Compose build"
"${COMPOSE[@]}" build "${SERVICES[@]}"

log "Docker Compose rolling update"
"${COMPOSE[@]}" up -d --remove-orphans --wait "${SERVICES[@]}"

# backend/frontend 컨테이너가 재생성되면 내부 IP가 바뀔 수 있다. Nginx도 매번
# 재생성해 Docker DNS를 다시 조회하게 하고, 오래된 upstream으로 인한 502를 막는다.
log "Nginx upstream refresh"
"${COMPOSE[@]}" up -d --no-deps --force-recreate --wait nginx
"${COMPOSE[@]}" exec -T nginx nginx -t

log "runtime smoke checks"
curl -fsS --max-time 15 http://127.0.0.1:8080/actuator/health/readiness >/dev/null
curl -fsS --max-time 15 http://127.0.0.1:8080/api/v1/catalog/sets/featured >/dev/null
curl -fsS --max-time 15 http://127.0.0.1:3000/icon.svg >/dev/null
curl -fsS --max-time 15 --resolve gole.co.kr:443:127.0.0.1 \
  https://gole.co.kr/actuator/health/readiness >/dev/null
curl -fsS --max-time 15 --resolve gole.co.kr:443:127.0.0.1 \
  https://gole.co.kr/icon.svg >/dev/null

if [ "$TARGET" = "all" ]; then
  log "비용 가드 호스트 watchdog 활성화"
  sudo install -m 0755 infra/gcp/scripts/cost-guard-watchdog.sh \
    /usr/local/sbin/gole-cost-guard-watchdog
  sudo install -m 0644 infra/gcp/systemd/gole-cost-guard-watchdog.service \
    /etc/systemd/system/gole-cost-guard-watchdog.service
  sudo install -m 0644 infra/gcp/systemd/gole-cost-guard-watchdog.timer \
    /etc/systemd/system/gole-cost-guard-watchdog.timer
  sudo systemctl daemon-reload
  sudo systemctl enable --now gole-cost-guard-watchdog.timer
fi

"${COMPOSE[@]}" ps
notify_deploy_result_once "✅ GoLe ${TARGET} 배포 및 헬스체크 완료 · gole.co.kr"
