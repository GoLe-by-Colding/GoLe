#!/usr/bin/env bash
#
# deploy.sh Discord 알림 계약 테스트 — 외부 네트워크 없이 결정적으로 돈다.
#
#   실행:  bash scripts/__tests__/deploy-notify.test.sh
#
# 검증하는 계약:
#   1. 백엔드 빌드 실패(셸 함수 내부 실패) → "배포 실패" 알림 정확히 1건
#   2. readiness 실패(명시적 exit 1)      → "배포 실패" 알림 정확히 1건
#   3. 정상 배포                            → "완료" 알림 1건 · "실패" 알림 0건
#   4. DISCORD_SUPPRESS_NOTIFICATIONS=false → payload에 무음 플래그(4096) 없음
#
# 실제 저장소를 건드리지 않도록 매 케이스마다 임시 디렉터리에 가짜 repo를 만들고
# git·pnpm·pm2·curl·sleep 를 PATH 스텁으로 대체한다.
set -Eeuo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DEPLOY_SH="$REPO_ROOT/scripts/deploy.sh"
FAKE_WEBHOOK="http://webhook.test/hook"
failures=0

# 가짜 배포 대상을 만들고 deploy.sh 를 그 안에서 실행한다.
#   $1 = deploy.sh 인자 (all|backend|frontend)
#   환경변수 DOCKER_BUILD_EXIT / FAIL_URL_SUBSTR 로 실패 지점을 주입한다.
run_deploy() {
  local target="$1"
  local sandbox bin
  sandbox="$(mktemp -d)"
  bin="$sandbox/bin"
  mkdir -p "$bin" "$sandbox/scripts" "$sandbox/infra/gcp"
  cp "$DEPLOY_SH" "$sandbox/scripts/deploy.sh"
  touch "$sandbox/infra/gcp/docker-compose.yml"
  CAPTURE="$sandbox/sent.txt"
  : >"$CAPTURE"

  # Compose 빌드 스텁: DOCKER_BUILD_EXIT 로 성공/실패를 고른다.
  cat >"$bin/docker" <<DOCKER
#!/usr/bin/env bash
for arg in "\$@"; do
  if [ "\$arg" = "build" ]; then exit ${DOCKER_BUILD_EXIT:-0}; fi
done
exit 0
DOCKER
  chmod +x "$bin/docker"

  # curl 스텁: 마지막 인자가 URL이다. webhook 이면 payload 를 캡처하고,
  # 그 외에는 헬스체크로 보고 FAIL_URL_SUBSTR 에 걸릴 때만 실패시킨다.
  cat >"$bin/curl" <<CURL
#!/usr/bin/env bash
url="\${@: -1}"
data=""
prev=""
for arg in "\$@"; do
  if [ "\$prev" = "--data" ]; then data="\$arg"; fi
  prev="\$arg"
done
case "\$url" in
  ${FAKE_WEBHOOK}*)
    printf '%s\n' "\$data" >>"$CAPTURE"
    exit 0
    ;;
esac
if [ -n "${FAIL_URL_SUBSTR:-}" ] && [[ "\$url" == *"${FAIL_URL_SUBSTR:-}"* ]]; then
  exit 1
fi
exit 0
CURL
  chmod +x "$bin/curl"

  # 나머지 외부 명령은 무해한 no-op. sleep 을 죽여 재시도 루프가 즉시 끝나게 한다.
  for stub in git sleep; do
    printf '#!/usr/bin/env bash\nexit 0\n' >"$bin/$stub"
    chmod +x "$bin/$stub"
  done

  set +e
  PATH="$bin:$PATH" \
  DISCORD_DEPLOY_WEBHOOK_URL="$FAKE_WEBHOOK" \
  DISCORD_SUPPRESS_NOTIFICATIONS="${SUPPRESS:-true}" \
    bash "$sandbox/scripts/deploy.sh" "$target" >"$sandbox/stdout.txt" 2>&1
  DEPLOY_STATUS=$?
  set -e
  SENT="$(cat "$CAPTURE")"
  SANDBOX="$sandbox"
}

count_matching() {
  # grep -c 는 0건일 때 1을 반환하므로 파이프로 세어 산술 비교를 안전하게 한다.
  printf '%s\n' "$SENT" | grep -F -- "$1" | grep -c . || true
}

expect_eq() {
  local label="$1" expected="$2" actual="$3"
  if [ "$expected" = "$actual" ]; then
    printf '  ok   %s (%s)\n' "$label" "$actual"
  else
    printf '  FAIL %s — 기대 %s, 실제 %s\n' "$label" "$expected" "$actual"
    failures=$((failures + 1))
  fi
}

printf '\n[1] 백엔드 빌드 실패 → 배포 실패 알림 1건\n'
DOCKER_BUILD_EXIT=1 FAIL_URL_SUBSTR="" run_deploy backend
expect_eq "종료 코드 비정상" "yes" "$([ "$DEPLOY_STATUS" -ne 0 ] && echo yes || echo no)"
expect_eq "배포 실패 알림 건수" 1 "$(count_matching '배포 실패')"
expect_eq "완료 알림 건수" 0 "$(count_matching '배포 및 헬스체크 완료')"

printf '\n[2] readiness 실패(exit 1) → 배포 실패 알림 1건\n'
DOCKER_BUILD_EXIT=0 FAIL_URL_SUBSTR="health/readiness" run_deploy backend
expect_eq "종료 코드 비정상" "yes" "$([ "$DEPLOY_STATUS" -ne 0 ] && echo yes || echo no)"
expect_eq "배포 실패 알림 건수" 1 "$(count_matching '배포 실패')"
expect_eq "완료 알림 건수" 0 "$(count_matching '배포 및 헬스체크 완료')"

printf '\n[3] 정상 배포 → 완료 알림 1건, 실패 알림 0건\n'
DOCKER_BUILD_EXIT=0 FAIL_URL_SUBSTR="" run_deploy backend
expect_eq "종료 코드 정상" 0 "$DEPLOY_STATUS"
expect_eq "시작 알림 건수" 1 "$(count_matching '배포 시작')"
expect_eq "완료 알림 건수" 1 "$(count_matching '배포 및 헬스체크 완료')"
expect_eq "배포 실패 알림 건수" 0 "$(count_matching '배포 실패')"

printf '\n[4] 무음 플래그는 설정을 그대로 따른다\n'
DOCKER_BUILD_EXIT=0 FAIL_URL_SUBSTR="" SUPPRESS=true run_deploy backend
expect_eq "suppress=true 이면 flags:4096 포함" 2 "$(count_matching '"flags":4096')"
DOCKER_BUILD_EXIT=0 FAIL_URL_SUBSTR="" SUPPRESS=false run_deploy backend
expect_eq "suppress=false 이면 flags 없음" 0 "$(count_matching '"flags":4096')"

printf '\n'
if [ "$failures" -eq 0 ]; then
  printf '✔ deploy.sh 알림 계약 테스트 통과\n'
else
  printf '✖ 실패 %d건\n' "$failures"
  exit 1
fi
