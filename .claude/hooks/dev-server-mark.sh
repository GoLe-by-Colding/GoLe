#!/bin/sh
# GoLe — 에이전트가 로컬 dev 서버를 띄우면 그 사실을 표시해 둔다.
# 짝: dev-server-guard.sh (Stop 훅). AGENTS.md "로컬 서버 — 켠 쪽이 끄고 끝낸다"의 하네스 강제판.
set -u
IN=$(cat)
CMD=$(printf '%s' "$IN" | jq -r '.tool_input.command // ""')

# Orca 터미널에 띄운 것은 표시하지 않는다. 사람이 탭에서 로그를 직접 보는 창이고,
# AGENTS.md가 "Orca 터미널에 띄운 dev 서버는 보고 후 남겨도 된다"고 명시한다.
# (표시하면 Stop 훅이 정당한 서버를 두고 마무리를 막는 오탐이 난다.)
case "$CMD" in
  *"orca terminal"*) exit 0 ;;
esac

case "$CMD" in
  *dev:api*|*dev:web*|*"next dev"*|*bootRun*) : ;;
  *) exit 0 ;;
esac
DIR="${CLAUDE_PROJECT_DIR:-$PWD}"
SID=$(printf '%s' "$IN" | jq -r '.session_id // "unknown"')
mkdir -p "$DIR/.claude" 2>/dev/null || exit 0
printf '%s\t%s\n' "$SID" "$CMD" >> "$DIR/.claude/.agent-dev-servers"
exit 0
