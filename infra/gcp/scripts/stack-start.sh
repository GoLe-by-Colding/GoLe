#!/usr/bin/env bash
# 재부팅이 남겨둔 운영 컨테이너를 다시 켠다. gole-stack.service 가 호출한다.
#
# 왜 docker compose 가 아니라 docker start 인가
# ------------------------------------------------
# `docker compose start` 는 depends_on 을 해석한다. backend 가
# mongo-init·minio-init 을 service_completed_successfully 로 의존하므로,
# 서비스 이름을 명시해도 **부팅마다 init 컨테이너가 다시 실행된다.**
#
# 그게 왜 문제인가: 배포 검증기가 그 컨테이너의 종료 상태를 증거로 읽는다.
#   gole-hostctl.sh: [ "$state" = exited:0 ] ||
#     die "historical initializer did not complete successfully: $service"
# 부팅마다 다시 돌리면 그 증거를 매번 새로 쓰는 셈이고, 한 번이라도 non-zero 로
# 끝나면 **다음 배포가 죽는다.** hostctl 이 deployment_container_name() 에서
# mongo-init·minio-init 만 `return 1` 로 제외하는 것도 같은 맥락이다.
#
# `docker compose up` 은 더 나쁘다. 빌드까지 끝내고 검증 전에 중단된 배포가 있으면
# gole/backend:local 이 미검증 이미지를 가리키는데, up 은 컨테이너를 재생성하며
# 그 이미지를 공개 서비스에 올린다.
#
# `docker start <이름>` 은 재부팅 직전 상태를 그대로 복원한다. 이미지·환경·네트워크·
# 정책 무엇도 바뀌지 않는다. hostctl 의 quiesce_public_runtime() 이
# `docker stop <이름>` 으로 멈추므로 대칭 연산이기도 하다.
set -uo pipefail

ROLLOUT_LOCK=/run/lock/gole-production-rollout.lock
# hostctl 의 deployment_container_name() 과 같은 목록. init 컨테이너는 없다.
CONTAINERS=(gole-mongo gole-redis gole-minio gole-budget-relay
            gole-support-agent gole-backend gole-frontend gole-nginx)

log() { logger -p daemon.info -t gole-stack "$*" || true; printf '%s\n' "$*"; }
err() { logger -p daemon.err  -t gole-stack "$*" || true; printf '%s\n' "$*" >&2; }

[ "$(id -u)" -eq 0 ] || { err "root 로 실행해야 한다"; exit 1; }

# CD 나 인증서 갱신이 롤아웃 락을 쥐고 있으면 조용히 양보한다. 경합하지 않는다.
if [ -e "$ROLLOUT_LOCK" ]; then
  exec 8>>"$ROLLOUT_LOCK" || { err "롤아웃 락을 열 수 없다"; exit 1; }
  if ! flock -n 8; then
    log "롤아웃 락을 다른 작업이 쥐고 있다. 부팅 기동을 건너뛴다."
    exit 0
  fi
fi

# After=docker.service 는 프로세스 기동만 보장한다. API 준비는 따로 기다린다.
for _ in $(seq 1 30); do
  docker info >/dev/null 2>&1 && break
  sleep 1
done
docker info >/dev/null 2>&1 || { err "docker 데몬이 30초 안에 준비되지 않았다"; exit 1; }

started=0 already=0 absent=0 failed=0
for c in "${CONTAINERS[@]}"; do
  state="$(docker inspect --format '{{.State.Status}}' "$c" 2>/dev/null)" || {
    absent=$((absent + 1)); continue
  }
  if [ "$state" = running ]; then
    already=$((already + 1)); continue
  fi
  # 이 컨테이너가 정말 우리 스택의 것인지 확인한 뒤에만 켠다.
  labels="$(docker inspect --format '{{index .Config.Labels "com.docker.compose.project"}}' "$c" 2>/dev/null)"
  if [ "$labels" != gole ]; then
    err "compose project 라벨이 gole 이 아니다: $c (labels=$labels). 건너뛴다."
    failed=$((failed + 1)); continue
  fi
  if docker start "$c" >/dev/null 2>&1; then
    log "기동: $c (이전 상태 $state)"
    started=$((started + 1))
  else
    err "기동 실패: $c"
    failed=$((failed + 1))
  fi
done

log "부팅 기동 결과 — 새로 켬 ${started} · 이미 실행 중 ${already} · 없음 ${absent} · 실패 ${failed}"
[ "$failed" -eq 0 ] || exit 1
