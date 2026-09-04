#!/usr/bin/env bash
set -Eeuo pipefail

TARGET="${1:-}"
SECRET_VERSION="${2:-}"
REQUEST_ID="${3:-}"
HOSTCTL="/usr/local/sbin/gole-hostctl"
ROLLOUT_LOCK="/run/lock/gole-production-rollout.lock"

die() {
  echo "$*" >&2
  exit 1
}

[ "$TARGET" = gole-production ] || die "지원하지 않는 배포 대상입니다."
[[ "$SECRET_VERSION" =~ ^[1-9][0-9]{0,11}$ ]] || die "Secret Manager 버전이 올바르지 않습니다."
[[ "$REQUEST_ID" =~ ^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$ ]] ||
  die "배포 요청 ID가 올바르지 않습니다."
[ -x "$HOSTCTL" ] || die "서버의 제한 권한 도우미가 설치되지 않았습니다."
[ -r /etc/gole/deploy-user ] || die "배포 사용자 설정을 읽을 수 없습니다."
IFS=: read -r deploy_user deploy_group < /etc/gole/deploy-user
[ "$(id -un)" = "$deploy_user" ] || die "설정된 전용 배포 사용자만 실행할 수 있습니다."
[[ "$deploy_group" =~ ^[a-z_][a-z0-9_-]{0,31}$ ]] || die "배포 그룹 설정이 올바르지 않습니다."
if [ ! -f "$ROLLOUT_LOCK" ] || [ -L "$ROLLOUT_LOCK" ] ||
  [ "$(stat -c '%U:%G:%a' "$ROLLOUT_LOCK")" != "root:${deploy_group}:660" ]; then
  die "운영 rollout lock이 설치되지 않았습니다."
fi

if [ "${GOLE_ROLLOUT_LOCK_HELD:-0}" = 1 ]; then
  [ -e /proc/self/fd/7 ] && flock -n 7 || die "부모 rollout lock을 확인할 수 없습니다."
else
  exec 7>>"$ROLLOUT_LOCK"
  flock -n 7 || die "다른 운영 rollout이 진행 중입니다."
fi

# Secret payload, metadata OAuth token and Docker socket are never exposed to
# the runner. The exact version is fetched, validated, installed and rolled
# back inside one root-owned host transaction.
sudo -n "$HOSTCTL" secret-sync "$SECRET_VERSION" "$REQUEST_ID"
echo "GoLe 운영 환경 변수 동기화가 완료되었습니다."
