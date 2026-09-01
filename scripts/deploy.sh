#!/usr/bin/env bash
#
# GoLe 표준 배포 스크립트 (infra-as-code) — Linux 운영 호스트의 /app에서 실행.
#
#   사용법:  bash scripts/deploy.sh [all|backend|frontend]   (기본: all)
#
# 흐름: git pull(ff-only) → 빌드 → pm2 reload(ecosystem.config.js) → health check.
# 이 스크립트는 현재 저장소와 ecosystem.config.js에 정의된 PM2 앱만 변경한다.
#
# -E(errtrace): ERR 트랩을 셸 함수·서브셸 안까지 상속시킨다. 지금 실패 알림은 아래
# EXIT 트랩이 맡지만(이유는 그쪽 주석), 이후 ERR 트랩을 붙이더라도 build_backend 같은
# 함수 안에서 정상 동작하도록 errtrace를 켜 둔다.
set -Eeuo pipefail

# repo 루트로 이동 (이 스크립트는 scripts/ 하위에 있다)
cd "$(dirname "$0")/.."
ROOT="$(pwd)"
TARGET="${1:-all}"

log() { printf '\n▶ %s\n' "$*"; }

# 배포 자체가 실패하면 애플리케이션 내부 알림도 뜰 수 없으므로 스크립트가 직접 알린다.
# URL은 DISCORD_DEPLOY_WEBHOOK_URL(우선) 또는 DISCORD_OPERATIONS_WEBHOOK_URL로만 주입한다.
notify_discord() {
  local webhook_url="${DISCORD_DEPLOY_WEBHOOK_URL:-${DISCORD_OPERATIONS_WEBHOOK_URL:-}}"
  local avatar_url="${DISCORD_AVATAR_URL:-https://gole.co.kr/icon.svg}"
  local notification_flags=""
  local message="$1"
  if [ -z "$webhook_url" ]; then return 0; fi
  if [ "${DISCORD_SUPPRESS_NOTIFICATIONS:-true}" = "true" ]; then
    notification_flags=',"flags":4096'
  fi
  curl -fsS --max-time 5 \
    -H 'Content-Type: application/json' \
    --data "{\"content\":\"${message}\",\"avatar_url\":\"${avatar_url}\",\"allowed_mentions\":{\"parse\":[]}${notification_flags}}" \
    "$webhook_url" >/dev/null || true
}

# 배포 결과(성공/실패) 알림은 실행당 정확히 한 번만 나간다. 성공 경로와 아래 EXIT
# 트랩이 같은 실행에서 겹쳐도 이 가드가 중복 발송을 막는다.
DEPLOY_RESULT_NOTIFIED=0
notify_deploy_result_once() {
  if [ "$DEPLOY_RESULT_NOTIFIED" = "1" ]; then return 0; fi
  DEPLOY_RESULT_NOTIFIED=1
  notify_discord "$1"
}

# 실패 알림은 ERR이 아니라 EXIT 트랩에서 보낸다. ERR 트랩만으로는 두 방향으로 어긋난다.
#
#   1. 놓친다 — bash는 명시적인 `exit`에서 ERR을 발화하지 않는다. readiness 실패 경로가
#      정확히 그 모양이라 지금까지 조용히 끝났다.
#   2. 두 번 보낸다 — `set -E`는 ERR 트랩을 서브셸까지 상속시키는데, 서브셸은 위의
#      단발 가드를 복사본으로 갖는다. `(cd apps/api && ./gradlew ...)` 가 실패하면
#      서브셸에서 한 번, 부모의 최종 그물에서 또 한 번 나간다.
#
# EXIT 트랩은 서브셸이 상속하지 않고, 함수 내부 실패·명시적 exit·예기치 못한 종료를
# 모두 잡는다. 그래서 실행당 배포 결과 알림이 정확히 한 건으로 수렴한다.
# (`-E` 자체는 유지한다 — 이후 ERR 트랩을 추가하더라도 함수 안에서 동작하도록.)
on_deploy_exit() {
  local status=$?
  if [ "$status" -ne 0 ]; then
    notify_deploy_result_once "❌ GoLe ${TARGET} 배포 실패 (exit ${status}) · gole.co.kr"
  fi
}
trap on_deploy_exit EXIT

notify_discord "🚀 GoLe ${TARGET} 배포 시작 · gole.co.kr"

log "git pull --ff-only origin main"
git pull --ff-only origin main

build_backend() {
  log "backend 빌드: gradlew bootJar (Java 21)"
  (cd "$ROOT/apps/api" && ./gradlew bootJar --no-daemon -q)
}

build_frontend() {
  log "frontend 빌드: pnpm install(frozen) + next build (Node 22)"
  pnpm install --frozen-lockfile --prefer-offline
  pnpm --filter web build
}

case "$TARGET" in
  backend) build_backend ;;
  frontend) build_frontend ;;
  all) build_backend; build_frontend ;;
  *) echo "알 수 없는 대상: $TARGET (all|backend|frontend)" >&2; exit 1 ;;
esac

log "pm2 reload (ecosystem.config.js)"
# 최초 1회는 프로세스가 없을 수 있으니 reload 실패 시 start 로 폴백.
pm2 reload "$ROOT/ecosystem.config.js" --update-env || pm2 start "$ROOT/ecosystem.config.js"
pm2 save

wait_for_url() {
  local label="$1"
  local url="$2"
  local attempts="${3:-30}"
  local response_file
  response_file="$(mktemp)"

  for _ in $(seq 1 "$attempts"); do
    if curl -fsS --max-time 10 -o "$response_file" "$url"; then
      printf '  %s: OK\n' "$label"
      rm -f "$response_file"
      return 0
    fi
    sleep 2
  done

  printf '✖ %s 실패 (%s)\n' "$label" "$url" >&2
  if [ -s "$response_file" ]; then
    sed -n '1,20p' "$response_file" >&2
  fi
  rm -f "$response_file"
  return 1
}

log "runtime smoke checks"
# readiness 그룹은 MongoDB·Redis를 포함한다. MinIO는 전체 health에서 별도로 DOWN을
# 보고하되 번들 미디어 fallback이 있으므로 업로드 기능만 degraded 상태로 둔다.
if ! wait_for_url "backend + core dependencies" "http://localhost:8080/actuator/health/readiness" 30; then
  pm2 logs gole-backend --lines 50 --nostream || true
  exit 1
fi

# 실제 공개 API와 번들 SVG를 호출해 라우팅/직렬화/미디어 응답 회귀까지 함께 막는다.
wait_for_url "catalog API" "http://localhost:8080/api/v1/catalog/sets/featured" 3
wait_for_url "bundled media" "http://localhost:8080/api/v1/media/catalog/10294.svg" 3
wait_for_url "frontend" "http://localhost:3000/" 15

printf '  optional dependencies: '
curl -sS --max-time 10 http://localhost:8080/actuator/health || true
printf '\n'

log "✔ 배포 완료"
pm2 list --no-color | grep -E 'gole-(backend|frontend)' || true
notify_deploy_result_once "✅ GoLe ${TARGET} 배포 및 헬스체크 완료 · gole.co.kr"
