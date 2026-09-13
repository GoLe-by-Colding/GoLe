#!/bin/sh
# GoLe — 이번 세션의 작업이 "밖으로 나간" 순간을 표시해 둔다.
# 짝: devlog-guard.sh (Stop 훅). AGENTS.md "개발일지 — 나간 작업은 일지를 남긴다"의 하네스 강제판.
#
# 커밋마다 걸면 너무 시끄러워서 push·PR 시점으로 잡았다. 남이 이어받을 수 있게 된
# 순간이 곧 인수인계가 필요한 순간이기 때문이다.
set -u
IN=$(cat)
CMD=$(printf '%s' "$IN" | jq -r '.tool_input.command // ""')

# 볼트 저장소에 하는 커밋·푸시는 표시하지 않는다. 그게 일지 자체이므로
# 표시하면 "일지를 쓰려면 일지가 있어야 한다"는 순환이 된다.
case "$CMD" in
  *GoLe-obsidian*) exit 0 ;;
esac

case "$CMD" in
  *"git push"*|*"gh pr create"*|*"gh pr merge"*) : ;;
  *) exit 0 ;;
esac

DIR="${CLAUDE_PROJECT_DIR:-$PWD}"
SID=$(printf '%s' "$IN" | jq -r '.session_id // "unknown"')
mkdir -p "$DIR/.claude" 2>/dev/null || exit 0
# 명령을 한 줄로 눌러 담는다. heredoc 같은 여러 줄 명령을 그대로 적으면 한 기록이
# 여러 줄로 쪼개지고, 정리할 때 grep -v 가 첫 줄만 지워 나머지가 영구히 남는다.
# (2026-09-13 확인: 이 누수로 마커 파일이 243·265줄까지 불어 있었다.)
ONE_LINE=$(printf '%s' "$CMD" | tr '\n\t' '  ' | cut -c1-160)
printf '%s\t%s\n' "$SID" "$ONE_LINE" >> "$DIR/.claude/.agent-devlog-pending"
exit 0
