#!/usr/bin/env bash
#
# GoLe 표준 배포 스크립트 (infra-as-code) — ubuntu-gole 컨테이너 내부 /app 에서 실행.
#
#   사용법:  bash scripts/deploy.sh [all|backend|frontend]   (기본: all)
#
# 흐름: git pull(ff-only) → 빌드 → pm2 reload(ecosystem.config.js) → health check.
# 이 스크립트는 ubuntu-gole 컨테이너 한정으로 동작하며, 다른 컨테이너/호스트에 영향을 주지 않는다.
#
set -euo pipefail

# repo 루트로 이동 (이 스크립트는 scripts/ 하위에 있다)
cd "$(dirname "$0")/.."
ROOT="$(pwd)"
TARGET="${1:-all}"

log() { printf '\n▶ %s\n' "$*"; }

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

log "health check"
ok=0
for _ in $(seq 1 30); do
  if curl -fsS http://localhost:8080/actuator/health >/dev/null 2>&1; then ok=1; break; fi
  sleep 2
done
if [ "$ok" -ne 1 ]; then
  echo "✖ 백엔드 헬스체크 실패 (8080/actuator/health)" >&2
  pm2 logs gole-backend --lines 30 --nostream || true
  exit 1
fi
echo -n "  backend: "; curl -fsS http://localhost:8080/actuator/health; echo
curl -fsS -o /dev/null -w "  frontend: HTTP %{http_code}\n" http://localhost:3000/ || true

log "✔ 배포 완료"
pm2 list --no-color | grep -E 'gole-(backend|frontend)' || true
