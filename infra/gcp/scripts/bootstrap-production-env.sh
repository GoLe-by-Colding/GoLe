#!/usr/bin/env bash
set -Eeuo pipefail

HOSTCTL="/usr/local/sbin/gole-hostctl"
ROLLOUT_LOCK="/run/lock/gole-production-rollout.lock"
DEPLOY_SHA=""
MODE=install
VERSION_STDIN=0

usage() {
  echo "usage: gole-bootstrap-production-env --sha 40_HEX --version-stdin [--dry-run]" >&2
  exit 2
}

[ "$(id -u)" -eq 0 ] || {
  echo "초기 운영 환경 bootstrap은 IAP 관리자 세션에서 root로만 실행합니다." >&2
  exit 1
}
while [ "$#" -gt 0 ]; do
  case "$1" in
    --sha) [ "$#" -ge 2 ] || usage; DEPLOY_SHA="$2"; shift 2 ;;
    --version-stdin) VERSION_STDIN=1; shift ;;
    --dry-run) MODE=validate; shift ;;
    *) usage ;;
  esac
done
[[ "$DEPLOY_SHA" =~ ^[0-9a-f]{40}$ ]] || usage
[ "$VERSION_STDIN" -eq 1 ] || usage
[ -x "$HOSTCTL" ] || { echo "root host helper가 설치되지 않았습니다." >&2; exit 1; }
IFS= read -r SECRET_VERSION || { echo "Secret 버전 입력이 없습니다." >&2; exit 2; }
[[ "$SECRET_VERSION" =~ ^[1-9][0-9]{0,11}$ ]] || {
  echo "Secret 버전은 양의 정수여야 합니다." >&2
  exit 2
}
if IFS= read -r _extra; then
  echo "Secret 버전은 정확히 한 줄이어야 합니다." >&2
  exit 2
fi

exec 7>>"$ROLLOUT_LOCK"
flock -n 7 || { echo "다른 운영 rollout이 진행 중입니다." >&2; exit 1; }
SUDO_USER=root "$HOSTCTL" env-bootstrap-secret "$SECRET_VERSION" "$DEPLOY_SHA" "$MODE"
if [ "$MODE" = validate ]; then
  echo "초기 운영 환경 dry-run 검증이 완료되었습니다."
else
  echo "초기 운영 환경과 최초 배포 marker가 원자적으로 설치되었습니다."
fi
