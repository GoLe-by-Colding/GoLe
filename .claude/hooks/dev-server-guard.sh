#!/bin/sh
# GoLe — 이 세션에서 띄운 로컬 서버가 남아 있으면 턴을 끝내지 못하게 막는다.
# 남이 띄운 서버는 표시가 없으므로 건드리지 않는다.
set -u
IN=$(cat)

# 이 훅 때문에 이미 한 번 되돌아온 상태면 다시 막지 않는다(무한 루프 방지).
[ "$(printf '%s' "$IN" | jq -r '.stop_hook_active // false')" = "true" ] && exit 0

DIR="${CLAUDE_PROJECT_DIR:-$PWD}"
MARK="$DIR/.claude/.agent-dev-servers"
[ -f "$MARK" ] || exit 0

SID=$(printf '%s' "$IN" | jq -r '.session_id // "unknown"')
grep -q "^$SID	" "$MARK" 2>/dev/null || exit 0   # 다른 세션이 남긴 표시는 무시

LEFT=""
for p in 8080 3000; do
  lsof -ti:"$p" >/dev/null 2>&1 && LEFT="$LEFT $p"
done

if [ -z "$LEFT" ]; then
  grep -v "^$SID	" "$MARK" > "$MARK.tmp" 2>/dev/null; mv "$MARK.tmp" "$MARK" 2>/dev/null
  [ -s "$MARK" ] || rm -f "$MARK"
  exit 0
fi

jq -n --arg ports "${LEFT# }" --arg mark "$MARK" '{
  decision: "block",
  reason: ("이 세션에서 띄운 로컬 dev 서버가 아직 떠 있다 (포트: " + $ports + "). AGENTS.md의 \"켠 쪽이 끄고 끝낸다\" 규칙대로 지금 내리고, 무엇을 내렸는지 보고에 한 줄 남긴 뒤 마무리하라. 다 내렸으면 " + $mark + " 를 지워라. Docker(pnpm infra:up)는 기본으로 남긴다 — 내려야 하면 infra:down(볼륨 유지)."),
  systemMessage: ("[dev-server-guard] 포트" + $ports + " 이(가) 아직 열려 있어 마무리를 막았습니다.")
}'
