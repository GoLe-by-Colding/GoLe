#!/usr/bin/env bash
set -Eeuo pipefail

HOSTCTL="/usr/local/sbin/gole-hostctl"
ROLLOUT_LOCK="/run/lock/gole-production-rollout.lock"
DEPLOY_SHA=""
REQUEST_ID=""
VERSION_STDIN=0

usage() {
  echo "usage: gole-migrate-and-adopt-existing --sha 40_HEX --request-id UUID --version-stdin" >&2
  exit 2
}

[ "$(id -u)" -eq 0 ] || {
  echo "기존 운영 채택은 IAP 관리자 세션에서 root로만 실행합니다." >&2
  exit 1
}
while [ "$#" -gt 0 ]; do
  case "$1" in
    --sha) [ "$#" -ge 2 ] || usage; DEPLOY_SHA="$2"; shift 2 ;;
    --request-id) [ "$#" -ge 2 ] || usage; REQUEST_ID="$2"; shift 2 ;;
    --version-stdin) VERSION_STDIN=1; shift ;;
    *) usage ;;
  esac
done
[[ "$DEPLOY_SHA" =~ ^[0-9a-f]{40}$ ]] || usage
[[ "$REQUEST_ID" =~ ^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$ ]] || usage
[ "$VERSION_STDIN" -eq 1 ] || usage
IFS= read -r SECRET_VERSION || { echo "Secret 버전 입력이 없습니다." >&2; exit 2; }
[[ "$SECRET_VERSION" =~ ^[1-9][0-9]{0,11}$ ]] || {
  echo "Secret 버전은 양의 정수여야 합니다." >&2
  exit 2
}
if IFS= read -r _extra; then
  echo "Secret 버전은 정확히 한 줄이어야 합니다." >&2
  exit 2
fi
[ -x "$HOSTCTL" ] || { echo "root host helper가 설치되지 않았습니다." >&2; exit 1; }

exec 7>>"$ROLLOUT_LOCK"
flock -n 7 || { echo "다른 운영 rollout이 진행 중입니다." >&2; exit 1; }
SUDO_USER=root "$HOSTCTL" deployment-migrate-adopt-secret \
  "$DEPLOY_SHA" "$SECRET_VERSION" "$REQUEST_ID"
echo "기존 운영 환경 마이그레이션과 LKG 채택이 완료되었습니다."
