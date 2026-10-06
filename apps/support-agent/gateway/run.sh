#!/usr/bin/env bash
# authorized_keys 의 forced command 가 부르는 진입점. 클라이언트가 보낸 명령(SSH_ORIGINAL_COMMAND)은 무시한다.
set -euo pipefail

# 비대화형 SSH 세션에는 로그인 셸 PATH 가 없다. claude·codex 가 흔히 깔리는 곳을 더한다.
export PATH="$HOME/.local/bin:$HOME/.npm-global/bin:$HOME/bin:/usr/local/bin:$PATH"
if [ -d "$HOME/.nvm/versions/node" ]; then
  for bin in "$HOME"/.nvm/versions/node/*/bin; do PATH="$bin:$PATH"; done
fi
# 서버마다 다른 값(CLI 경로·타임아웃)은 여기 둔다. 없어도 된다.
if [ -f "$HOME/gole-llm-gateway/env" ]; then
  # shellcheck disable=SC1091
  . "$HOME/gole-llm-gateway/env"
fi

exec python3 "$(dirname "$0")/gole_llm_gateway.py"
