#!/bin/sh
# 컨테이너 진입점. CODEX_HOME 에는 로그인·설치 ID 만 남기고, 설정은 이미지 것으로 맞춘다.
# 로그인은 서버가 구운 이미지에서 오고(D28), 잠금·서버 로그·계정 가드 기록은 상태 볼륨(/home/gateway/state)에 있다.
set -eu

# 사람이 쓰던 ~/.codex 를 CODEX_HOME 에 붙였으면 아래 정리가 그 기록·설정을 날린다. 그 흔적이 보이면 아무것도 지우지 않고 멈춘다.
# codex exec(--ephemeral)는 이 파일들을 만들지 않는다. 호출 쪽은 응답 JSON 의 고정 코드만 공개 로그에 남긴다.
for mark in history.jsonl AGENTS.md AGENTS.override.md hooks.json; do
  if [ -e "$CODEX_HOME/$mark" ]; then
    echo "CODEX_HOME 에 사람이 쓰던 codex 파일($mark)이 있다. CODEX_HOME 에는 아무것도 붙이지 않는다(로그인은 이미지에 굽는다)." >&2
    printf '{"ok":false,"error":"GATEWAY_MISCONFIGURED","text":"","structured":null,"images":[],"usage":null}'
    exit 0
  fi
done

# codex 는 실행마다 상태 DB(state·logs·memories·goals)·스킬·셸 스냅숏을 CODEX_HOME 에 쌓는다(2026-10-13 컨테이너 실측).
# 요청끼리 아무것도 이어받지 않게, 지켜야 할 파일 말고는 매번 지운다. 개인 지침·hooks 도 여기서 사라진다.
find "$CODEX_HOME" -mindepth 1 -maxdepth 1 \
  ! -name auth.json ! -name installation_id \
  -exec rm -rf {} +
cp /opt/gateway/config.toml "$CODEX_HOME/config.toml"

exec python3 /opt/gateway/gole_llm_gateway.py
