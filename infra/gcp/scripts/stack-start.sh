#!/usr/bin/env bash
# 부팅 시 운영 컨테이너를 다시 켠다. gole-stack.service 가 호출한다.
#
# 설계 원칙
#   1. `docker compose start` 만 쓴다. `up -d` 는 쓰지 않는다.
#      - compose 는 support-agent·certbot 등 호스트에 배포되지 않은 서비스도 정의한다.
#        `up` 은 그것들까지 새로 만들어 배포 토폴로지를 바꾼다.
#      - backend·frontend·budget-relay·support-agent 는 build: 를 가진다. 이미지가 없으면
#        `up` 이 부팅 중에 빌드를 시작한다. 느리고, 실패하면 부팅이 지저분해진다.
#      - `start` 는 이미 존재하는 컨테이너만 켠다. 만들지도, 빌드하지도, 지우지도 않는다.
#   2. compose 파일은 /app 이 아니라 **불변 릴리스 사본**을 쓴다.
#      /app 은 GitHub Actions 러너가 쓰는 경로라 root 로 다루지 않는다는 것이 이 호스트의 규칙이다.
#   3. 실패해도 0 이 아닌 값으로 끝나되, 전원을 내리지 않는다. 사람이 고친다.
#
# 확인된 동작 (2026-09-13 운영 호스트에서 실측)
#   - `compose start` 는 멈춰 있던 컨테이너를 되살린다. mongo-init·minio-init 이
#     실제로 다시 실행되어 종료코드 0 으로 끝나는 것을 확인했다.
#   - 그 둘은 멱등하다. mongo-init 은 rs.conf() 를 확인하고 다를 때만 reconfig 하며,
#     minio-init 은 `mc mb --ignore-existing` 과 `mc anonymous set none` 만 한다.
#     매 부팅마다 다시 돌아도 데이터에 영향이 없다.
#   - 이미 전부 running 인 상태에서 실행하면 사실상 no-op 이고 컨테이너가 재시작되지 않는다.
set -uo pipefail

SHA_FILE=/etc/gole/deployed.sha
APP_ENV="${GOLE_APP_ENV_FILE:-/etc/gole/gole.env}"
INFRA_ENV="${GOLE_INFRA_ENV_FILE:-/etc/gole/infra.env}"

log() { logger -p daemon.info -t gole-stack "$*" || true; printf '%s\n' "$*"; }
err() { logger -p daemon.err  -t gole-stack "$*" || true; printf '%s\n' "$*" >&2; }

sha="$(tr -d '[:space:]' < "$SHA_FILE" 2>/dev/null || true)"
if ! [[ "$sha" =~ ^[0-9a-f]{40}$ ]]; then
  err "deployed.sha 가 40자리 hex 가 아니다: '${sha:-비어있음}'. 스택을 건드리지 않는다."
  exit 1
fi

compose_file="/var/lib/gole/releases/${sha}/infra/gcp/docker-compose.yml"
if [ ! -f "$compose_file" ]; then
  err "릴리스 사본이 없다: $compose_file. 스택을 건드리지 않는다."
  exit 1
fi

# docker 데몬이 API 를 받을 준비가 될 때까지 기다린다.
# After=docker.service 는 "프로세스가 떴다"만 보장하고 API 준비는 보장하지 않는다.
for _ in $(seq 1 30); do
  docker info >/dev/null 2>&1 && break
  sleep 2
done
if ! docker info >/dev/null 2>&1; then
  err "docker 데몬이 60초 안에 준비되지 않았다."
  exit 1
fi

compose=(docker compose --env-file "$INFRA_ENV" --env-file "$APP_ENV" -f "$compose_file")

# 이미 전부 돌고 있으면 아무것도 하지 않는다 (정상 부팅에서 unless-stopped 가 먼저 살린 경우).
running="$("${compose[@]}" ps --status running --quiet 2>/dev/null | wc -l | tr -d ' ')"
existing="$("${compose[@]}" ps --all --quiet 2>/dev/null | wc -l | tr -d ' ')"
log "부팅 시점 컨테이너: 실행 ${running} / 존재 ${existing}"

if [ "${existing:-0}" -eq 0 ]; then
  err "컨테이너가 하나도 없다. start 로는 복구할 수 없다 — CD 로 배포해야 한다."
  exit 1
fi

if ! "${compose[@]}" start; then
  err "docker compose start 실패. 사람이 확인해야 한다."
  "${compose[@]}" ps --all || true
  exit 1
fi

after="$("${compose[@]}" ps --status running --quiet 2>/dev/null | wc -l | tr -d ' ')"
log "스택 기동 완료: 실행 ${after} / 존재 ${existing}"
