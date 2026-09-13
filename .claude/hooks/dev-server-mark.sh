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
# 명령을 한 줄로 눌러 담는다. heredoc 같은 여러 줄 명령을 그대로 적으면 한 기록이
# 여러 줄로 쪼개지고, 정리할 때 grep -v 가 첫 줄만 지워 나머지가 영구히 남는다.
# (2026-09-13 확인: 이 누수로 마커 파일이 243·265줄까지 불어 있었다.)
ONE_LINE=$(printf '%s' "$CMD" | tr '\n\t' '  ' | cut -c1-160)
printf '%s\t%s\n' "$SID" "$ONE_LINE" >> "$DIR/.claude/.agent-dev-servers"
exit 0
