#!/bin/sh
# 컨테이너 진입점. 볼륨(CODEX_HOME)에는 로그인(auth.json)·잠금·서버 로그만 남기고, 설정은 이미지 것으로 맞춘다.
set -eu

cp /opt/gateway/config.toml "$CODEX_HOME/config.toml"
# 누가 볼륨에 개인 지침·hooks 를 넣어 두었어도 요청에 섞이지 않게 지운다.
rm -rf "$CODEX_HOME/AGENTS.md" "$CODEX_HOME/AGENTS.override.md" "$CODEX_HOME/hooks.json"

exec python3 /opt/gateway/gole_llm_gateway.py
