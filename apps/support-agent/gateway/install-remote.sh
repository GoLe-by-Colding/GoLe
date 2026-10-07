#!/usr/bin/env bash
# 게이트웨이 배포 워크플로(.github/workflows/promotion-gateway-deploy.yml)가 SSH stdin 으로 보내는 설치 스크립트.
# 서버에 파일로 두지 않는다. 러너가 이 스크립트 앞에 아래 네 변수를 붙여 `bash -s` 로 실행한다.
#
#   GATEWAY_B64 · RUN_B64   — 배포할 파일 내용(base64). 러너가 체크아웃한 바로 그 바이트다
#   GATEWAY_SHA · RUN_SHA   — 러너가 계산한 sha256
#
# 공개 저장소의 Actions 로그로 서버 경로·사용자명이 새지 않게, 표준 출력에는 고정 문구만 쓰고 오류 상세는
# 게이트웨이와 같은 서버 로그(~/.cache/gole-llm-gateway.log, 0600)에 남긴다.
set -euo pipefail

: "${GATEWAY_B64:?}" "${RUN_B64:?}" "${GATEWAY_SHA:?}" "${RUN_SHA:?}"

log="$HOME/.cache/gole-llm-gateway.log"
mkdir -p "$(dirname "$log")"
touch "$log" && chmod 600 "$log"
exec 2>>"$log"
echo "$(date -u +%FT%TZ) deploy 시작 gateway=${GATEWAY_SHA:0:12}" >&2

dir="$HOME/gole-llm-gateway"
mkdir -p "$dir"
cd "$dir"
staging="$(mktemp -d "$dir/.deploy.XXXXXX")"
trap 'rm -rf "$staging"' EXIT

printf '%s' "$GATEWAY_B64" | base64 -d > "$staging/gole_llm_gateway.py"
printf '%s' "$RUN_B64" | base64 -d > "$staging/run.sh"
if ! printf '%s  %s\n%s  %s\n' "$GATEWAY_SHA" "$staging/gole_llm_gateway.py" "$RUN_SHA" "$staging/run.sh" \
  | sha256sum -c --quiet >&2; then
  echo "배포 중단: 받은 파일의 해시가 다르다(기존 게이트웨이 그대로)"
  exit 1
fi
chmod 0644 "$staging/gole_llm_gateway.py"
chmod 0755 "$staging/run.sh"

# 되돌릴 자리를 남기고 한 번에 바꿔 끼운다. 이미 떠 있는 요청은 옛 파일을 메모리에 들고 끝까지 돈다.
for file in gole_llm_gateway.py run.sh; do
  if [ -f "$file" ]; then
    cp -p "$file" "$file.prev"
  fi
done
mv -f "$staging/gole_llm_gateway.py" gole_llm_gateway.py
mv -f "$staging/run.sh" run.sh

# 연기 테스트: 잘못된 engine 은 모델을 부르지 않고 고정 문장으로 거절된다. 파이썬 파일이 뜨는지만 본다.
smoke="$(printf '{"engine":"smoke","prompt":"ping"}' | ./run.sh || true)"
case "$smoke" in
  *'"ok": false'*'engine'*)
    echo "$(date -u +%FT%TZ) deploy 완료 gateway=${GATEWAY_SHA:0:12}" >&2
    echo "배포 완료: gateway ${GATEWAY_SHA:0:12}"
    ;;
  *)
    for file in gole_llm_gateway.py run.sh; do
      if [ -f "$file.prev" ]; then
        mv -f "$file.prev" "$file"
      fi
    done
    echo "$(date -u +%FT%TZ) deploy 연기 테스트 실패, 이전 파일로 되돌림: $smoke" >&2
    echo "배포 실패: 새 게이트웨이가 뜨지 않아 이전 버전으로 되돌렸다"
    exit 1
    ;;
esac
