#!/bin/sh
# 지인 서버의 게이트웨이 진입점(promotion-review D28·D29). authorized_keys 의 forced command 가 이것 하나만 부른다.
#   restrict,command="/opt/gole-gateway/run.sh" ssh-ed25519 AAAA... gole-promotion
#
# 러너는 `ssh … submit <run_key>` 로 주문서(stdin JSON)를 넣고 "접수" 응답을 받자마자 끝난다. 이 스크립트가 주문을
# jobs/<run_key>/ 에 두고 자기 자신을 `work <run_key>` 로 백그라운드에 띄운다. work 는 :local 컨테이너에서 deliver 를
# 돌려 백엔드 납품 창구로 결과를 보낸 뒤, codex 가 갱신했을 수 있는 auth.json 을 꺼내 바뀌었으면 다시 굽는다(D28).
#
# codex 로그인(auth.json)과 홍보 토큰·백엔드 주소·Discord 웹훅(promotion.env)은 이 서버에만 있다. 응답은 공개 저장소의
# Actions 로그로 가므로 고정 코드만 낸다. 명령 출력·오류 원문은 run.log 에만 남긴다.
set -u
umask 077

DIR=${GOLE_GATEWAY_DIR:-/opt/gole-gateway}
BASE=${GOLE_GATEWAY_BASE:-ghcr.io/gole-by-colding/gole-llm-gateway:stable}
STATE=${GOLE_GATEWAY_STATE_VOLUME:-gole-gateway-state}
LOCAL=gole-llm-gateway:local
# 이 시간보다 오래 queued·running 인 주문은 멈춘 것으로 본다(서버 재부팅·일꾼 사망).
STALE_MINUTES=${GOLE_GATEWAY_STALE_MINUTES:-120}
# 구독 계정 보호: 최근 24시간에 받은 주문 수 상한. 수동 실행·스케줄·릴리스가 무엇이든 여기를 지난다.
DAILY_ORDERS=${GOLE_GATEWAY_DAILY_ORDERS:-3}
MAX_ORDER_BYTES=67108864

answer() { printf '{"ok":%s,"error":%s,"job":%s,"text":"","structured":null,"images":[],"usage":null}' "$@"; exit 0; }
fail() { answer false "\"$1\"" null; }
log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" >>"$DIR/run.log"; }
bake() { docker build -q --build-arg "BASE=$BASE" -t "$LOCAL" -f Dockerfile.local . >>"$DIR/run.log" 2>&1; }
valid_key() { printf '%s' "$1" | grep -Eqx '[a-z0-9][a-z0-9-]{0,63}'; }

# 운영 채널에 한 줄. 고정 문구·코드·run_key 만 싣는다(D23). 보내지 못해도 결과는 바꾸지 않는다.
notify() {
  url=$(sed -n 's/^DISCORD_OPERATIONS_WEBHOOK_URL=//p' "$DIR/promotion.env" 2>/dev/null | head -n 1)
  [ -n "$url" ] || return 0
  curl -fsS --max-time 10 -H 'Content-Type: application/json' \
    --data "{\"content\":\"$1\",\"allowed_mentions\":{\"parse\":[]}}" "$url" >/dev/null 2>&1 || log "Discord 알림 실패"
}

submit() {
  key=$1
  valid_key "$key" || fail GATEWAY_BAD_REQUEST
  mkdir -p jobs
  # 접수 검사부터 확정까지 한 번에 하나만 — 두 주문이 동시에 "진행 중 없음"을 보고 함께 들어오지 않게.
  exec 8>submit.lock
  flock -w 30 8 || fail GATEWAY_BUSY
  # 실패해 남겨 둔 주문(과 끊긴 접수의 임시 폴더)은 7일 뒤 지운다.
  find jobs -mindepth 1 -maxdepth 1 -type d -mtime +7 -exec rm -rf {} + 2>/dev/null
  # 응답 유실 뒤 같은 실행이 다시 넣으면, 아직 처리 중일 때만 받은 것으로 답한다. 끝났거나 실패한 주문은
  # 다시 돌지 않으므로 "접수"라고 하면 안 된다.
  if [ -d "jobs/$key" ]; then
    grep -Eqx 'queued|running' "jobs/$key/status" 2>/dev/null && answer true null "\"$key\""
    fail GATEWAY_DUPLICATE
  fi
  for status in jobs/*/status; do
    [ -f "$status" ] && grep -Eqx 'queued|running' "$status" || continue
    if [ -n "$(find "$status" -mmin "-$STALE_MINUTES")" ]; then
      fail GATEWAY_BUSY
    fi
    stale=$(basename "$(dirname "$status")")
    echo failed >"$status"
    log "멈춘 주문 $stale 을 실패로 둔다"
    notify "❌ GoLe 홍보 주문이 멈춤 · ${STALE_MINUTES}분 넘게 진척 없음 · $stale"
  done
  # 받은 주문(성공·실패 모두)을 orders.log 에 "시각 run_key" 로 남기고 24시간 안의 줄 수로 센다.
  now=$(date +%s)
  recent=$(awk -v since="$((now - 86400))" '$1 >= since' orders.log 2>/dev/null | wc -l)
  if [ "$recent" -ge "$DAILY_ORDERS" ]; then
    log "하루 주문 상한($DAILY_ORDERS) — $key 거절"
    fail GATEWAY_DAILY_LIMIT
  fi
  # 주문서를 다 받은 뒤에만 보이게 한다. 받는 도중 끊기면 점(.)으로 시작하는 임시 폴더만 남고 접수되지 않는다.
  incoming="jobs/.incoming-$key"
  rm -rf "$incoming"
  mkdir "$incoming" || fail GATEWAY_FAILED
  head -c "$((MAX_ORDER_BYTES + 1))" >"$incoming/order.json"
  if [ "$(wc -c <"$incoming/order.json")" -gt "$MAX_ORDER_BYTES" ]; then
    rm -rf "$incoming"
    fail GATEWAY_BAD_REQUEST
  fi
  echo queued >"$incoming/status"
  mv "$incoming" "jobs/$key" || { rm -rf "$incoming"; fail GATEWAY_FAILED; }
  printf '%s %s\n' "$now" "$key" >>orders.log
  # 일꾼이 접수 잠금(8)을 물려받으면 다음 접수가 일꾼이 끝날 때까지 막힌다.
  setsid nohup "$0" work "$key" </dev/null >/dev/null 2>&1 8>&- &
  answer true null "\"$key\""
}

work() {
  key=$1
  valid_key "$key" && [ -f "jobs/$key/order.json" ] || exit 1
  job="jobs/$key"
  # 러너는 이미 떠났다. 끊길 연결은 없지만, 서버 쪽 신호로 중간에 멈춰 토큰을 잃지 않게 한다.
  trap '' HUP PIPE
  exec 9>run.lock
  if ! flock -w 1800 9; then
    echo failed >"$job/status"
    notify "❌ GoLe 홍보 주문 실패 · GATEWAY_BUSY · $key"
    exit 1
  fi
  echo running >"$job/status"

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
  if ! docker image inspect "$LOCAL" >/dev/null 2>&1; then
    echo failed >"$job/status"
    notify "❌ GoLe 홍보 주문 실패 · GATEWAY_IMAGE_UNAVAILABLE · $key"
    exit 1
  fi

  # D26 의 제약 그대로. --read-only 만 뺀다 — codex 가 토큰을 고쳐 쓰는 곳이 컨테이너 쓰기 층이고, 끝나면 지운다.
  # 주문서는 stdin 으로 넘긴다 — 주문 폴더(0700)를 마운트하면 컨테이너 사용자가 읽지 못한다.
  cid=$(docker create -i --log-driver none --cap-drop ALL --security-opt no-new-privileges \
    --pids-limit 256 --tmpfs /tmp --memory 2g --cpus 2 --env-file promotion.env \
    -v "$STATE:/home/gateway/state" "$LOCAL" python3 -m gole_promotion_deliver - 2>>run.log)
  if [ -z "$cid" ]; then
    echo failed >"$job/status"
    notify "❌ GoLe 홍보 주문 실패 · GATEWAY_IMAGE_UNAVAILABLE · $key"
    exit 1
  fi
  trap 'docker rm -f "$cid" >/dev/null 2>&1; rm -f auth.new' EXIT
  docker start -ai "$cid" <"$job/order.json" >>run.log 2>&1
  rc=$(docker wait "$cid" 2>>run.log) || rc=125

  # 0 = 납품함, 2·3 = deliver 가 이미 알림. 그 밖의 코드는 컨테이너가 비정상으로 끝난 것이다.
  case "$rc" in
    0) rm -rf "$job" ;;
    2 | 3) echo failed >"$job/status" ;;
    *)
      echo failed >"$job/status"
      notify "❌ GoLe 홍보 주문 실패 · 작업이 비정상 종료(코드 $rc) · $key"
      ;;
  esac

  # 토큰이 바뀌었으면 다시 굽는다. 읽을 수 없는 JSON 이면 지금 열쇠를 지킨다(D28).
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
    log "새 토큰으로 굽기 실패 — 다음 주문이 다시 굽는다"
    rm -f base.digest
  fi
}

cd "$DIR" 2>/dev/null || fail GATEWAY_MISCONFIGURED
if [ ! -s auth.json ] || [ ! -s promotion.env ]; then
  log "auth.json 또는 promotion.env 없음 — gole_llm_gateway.py 머리말의 서버 설치를 따른다"
  fail GATEWAY_MISCONFIGURED
fi

if [ "$#" -gt 0 ]; then
  # 이 스크립트가 스스로 띄운 백그라운드 작업. SSH 로는 이 길에 들어올 수 없다(아래는 submit 만 받는다).
  [ "$1" = work ] && work "${2:-}" && exit 0
  exit 1
fi
# "submit <run_key>" 를 두 낱말로 나눈다. 글롭은 끄고(set -f) 나눈다. run_key 는 위 정규식으로만 통과한다.
set -f
# shellcheck disable=SC2086
set -- ${SSH_ORIGINAL_COMMAND:-}
set +f  # submit 이 jobs/*/status 글롭을 쓴다
case "${1:-}" in
  submit) submit "${2:-}" ;;
  *) fail GATEWAY_BAD_REQUEST ;;
esac
