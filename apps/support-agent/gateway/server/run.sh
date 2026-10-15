#!/bin/sh
# 지인 서버의 게이트웨이 진입점(promotion-review D28). authorized_keys 의 forced command 가 이것 하나만 부른다.
#   restrict,command="/opt/gole-gateway/run.sh" ssh-ed25519 AAAA... gole-promotion
#
# codex 로그인(auth.json)은 이 서버에만 있다. GHCR 의 코드 이미지(:stable) 위에 그것을 얹어 :local 을 굽고,
# 요청마다 :local 로 일회용 컨테이너를 띄운다. 끝나면 codex 가 갱신했을 수 있는 auth.json 을 꺼내 바뀌었으면 다시 굽는다.
# stdin(요청 JSON)·stdout(응답 JSON) 계약은 게이트웨이와 같다. 이 스크립트의 실패도 같은 모양의 고정 코드로 낸다.
# 서버 사정(명령 출력·오류 원문)은 run.log 에만 남긴다 — 응답은 공개 저장소의 Actions 로그로 간다.
set -u
umask 077

DIR=${GOLE_GATEWAY_DIR:-/opt/gole-gateway}
BASE=${GOLE_GATEWAY_BASE:-ghcr.io/gole-by-colding/gole-llm-gateway:stable}
STATE=${GOLE_GATEWAY_STATE_VOLUME:-gole-gateway-state}
LOCAL=gole-llm-gateway:local

fail() {
  printf '{"ok":false,"error":"%s","text":"","structured":null,"images":[],"usage":null}' "$1"
  exit 0
}
log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" >>"$DIR/run.log"; }
bake() { docker build -q --build-arg "BASE=$BASE" -t "$LOCAL" -f Dockerfile.local . >>"$DIR/run.log" 2>&1; }

# Actions 취소로 SSH 가 끊겨도 끝까지 가서 갱신된 토큰을 회수한다(컨테이너도 진행 중인 호출을 마친다).
trap '' HUP PIPE
cd "$DIR" 2>/dev/null || fail GATEWAY_MISCONFIGURED
[ -s auth.json ] || { log "auth.json 없음 — README 의 서버 설치를 따른다"; fail GATEWAY_MISCONFIGURED; }

# 굽기·실행·다시 굽기를 한 번에 하나만. 게이트웨이 안의 잠금(30분)과 같은 시간을 기다린다.
exec 9>run.lock
flock -w "${GOLE_GATEWAY_LOCK_WAIT:-1800}" 9 || fail GATEWAY_BUSY

# 새 코드면 다시 굽는다. GHCR 이 안 되거나 빌드가 실패해도 이미 구운 :local 이 있으면 그것으로 돈다.
docker pull -q "$BASE" >>run.log 2>&1 || log "stable 받기 실패 — 있는 이미지로 진행"
base_id=$(docker image inspect -f '{{.Id}}' "$BASE" 2>>run.log) || base_id=""
if [ -n "$base_id" ] && { [ "$base_id" != "$(cat base.digest 2>/dev/null)" ] \
    || ! docker image inspect "$LOCAL" >/dev/null 2>&1; }; then
  if bake; then
    printf '%s\n' "$base_id" >base.digest
    docker image prune -f >/dev/null 2>&1 || true  # 옛 :local(옛 토큰)·옛 stable 레이어를 지운다
  else
    log "새 stable 로 굽기 실패"
  fi
fi
docker image inspect "$LOCAL" >/dev/null 2>&1 || fail GATEWAY_IMAGE_UNAVAILABLE

# D26 의 제약 그대로. --read-only 만 뺀다 — codex 가 토큰을 고쳐 쓰는 곳이 컨테이너 쓰기 층이고, 요청이 끝나면 지운다.
cid=$(docker create -i --log-driver none --cap-drop ALL --security-opt no-new-privileges \
  --pids-limit 256 --tmpfs /tmp --memory 2g --cpus 2 \
  -v "$STATE:/home/gateway/state" "$LOCAL" 2>>run.log) || fail GATEWAY_IMAGE_UNAVAILABLE
trap 'docker rm -f "$cid" >/dev/null 2>&1; rm -f auth.new' EXIT
docker start -ai "$cid" || log "start -ai 종료 코드 $? — 연결이 끊겼으면 컨테이너는 계속 돈다"
docker wait "$cid" >/dev/null 2>&1 || true

# 토큰이 바뀌었으면 다시 굽는다. 읽을 수 없는 JSON 이면 지금 열쇠를 지킨다.
docker cp "$cid:/home/gateway/.codex/auth.json" auth.new 2>>run.log || exit 0
cmp -s auth.new auth.json && exit 0
if ! docker run --rm -i --network none --entrypoint python3 "$LOCAL" \
    -c 'import json,sys; json.load(sys.stdin)' <auth.new >/dev/null 2>>run.log; then
  log "꺼낸 auth.json 이 JSON 이 아니다 — 지금 열쇠를 유지"
  exit 0
fi
mv auth.new auth.json
log "토큰 갱신 — 다시 굽는다"
if bake; then
  docker image prune -f >/dev/null 2>&1 || true
else
  log "새 토큰으로 굽기 실패 — 다음 요청이 다시 굽는다"
  rm -f base.digest
fi
