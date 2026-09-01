#!/usr/bin/env bash
set -Eeuo pipefail

cd "$(dirname "$0")/.."
ROOT="$(pwd)"
TARGET="${1:-all}"
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
    "${COMPOSE[@]}" logs --tail=100 backend frontend budget-relay nginx || true
    notify_deploy_result_once "❌ GoLe ${TARGET} 배포 실패 (exit ${status}) · gole.co.kr"
  fi
}
trap on_deploy_exit EXIT

notify_discord "🚀 GoLe ${TARGET} 배포 시작 · gole.co.kr"

log "CI가 검증한 origin/main 동기화"
git fetch --prune origin main

# 운영 checkout에 수동 변경이 있으면 덮어쓰지 않는다. 깨끗한 경우에만 원격 main을
# 정확히 반영해, 승인된 force push나 계정 이전 뒤에도 배포가 막히지 않게 한다.
if [ -n "$(git status --porcelain=v1 --untracked-files=all)" ]; then
  echo "운영 checkout에 커밋되지 않은 변경이 있어 배포를 중단한다" >&2
  git status --short >&2
  exit 1
fi

git reset --hard origin/main

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

log "runtime smoke checks"
curl -fsS --max-time 15 http://127.0.0.1:8080/actuator/health/readiness >/dev/null
curl -fsS --max-time 15 http://127.0.0.1:8080/api/v1/catalog/sets/featured >/dev/null
curl -fsS --max-time 15 http://127.0.0.1:3000/icon.svg >/dev/null

"${COMPOSE[@]}" ps
notify_deploy_result_once "✅ GoLe ${TARGET} 배포 및 헬스체크 완료 · gole.co.kr"
