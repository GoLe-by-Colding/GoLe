#!/bin/sh
# 컨테이너 진입점. 볼륨(CODEX_HOME)에는 로그인·설치 ID·잠금·서버 로그·계정 가드 기록만 남기고, 설정은 이미지 것으로 맞춘다.
set -eu

# codex 는 실행마다 상태 DB(state·logs·memories·goals)·스킬·셸 스냅숏을 CODEX_HOME 에 쌓는다(2026-10-13 컨테이너 실측).
# 요청끼리 아무것도 이어받지 않고 볼륨이 자라지 않게, 지켜야 할 파일 말고는 매번 지운다. 개인 지침·hooks 도 여기서 사라진다.
find "$CODEX_HOME" -mindepth 1 -maxdepth 1 \
  ! -name auth.json ! -name installation_id ! -name 'gateway.*' ! -name gole-llm-gateway-usage.jsonl \
  -exec rm -rf {} +
cp /opt/gateway/config.toml "$CODEX_HOME/config.toml"

exec python3 /opt/gateway/gole_llm_gateway.py
