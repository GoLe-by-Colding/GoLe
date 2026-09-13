#!/bin/sh
# GoLe — 이번 세션에서 작업을 밖으로 내보냈는데 오늘자 개발일지가 없으면 턴을 막는다.
# 볼트를 안 받아뒀거나 팀원 식별이 안 되면 막지 않는다(오탐 방지).
set -u
IN=$(cat)

# 이 훅 때문에 이미 한 번 되돌아온 상태면 다시 막지 않는다(무한 루프 방지).
[ "$(printf '%s' "$IN" | jq -r '.stop_hook_active // false')" = "true" ] && exit 0

DIR="${CLAUDE_PROJECT_DIR:-$PWD}"
MARK="$DIR/.claude/.agent-devlog-pending"
[ -f "$MARK" ] || exit 0

SID=$(printf '%s' "$IN" | jq -r '.session_id // "unknown"')
grep -q "^$SID	" "$MARK" 2>/dev/null || exit 0   # 다른 세션이 남긴 표시는 무시

clear_mark() {
  grep -v "^$SID	" "$MARK" > "$MARK.tmp" 2>/dev/null; mv "$MARK.tmp" "$MARK" 2>/dev/null
  [ -s "$MARK" ] || rm -f "$MARK"
}

# 볼트 위치 — GOLE_VAULT 로 덮어쓸 수 있다. 기본은 코드 저장소의 형제 디렉터리.
VAULT="${GOLE_VAULT:-$(dirname "$DIR")/GoLe-obsidian}"
if [ ! -d "$VAULT/10_개발일지" ]; then
  clear_mark; exit 0        # 볼트를 안 받아둔 사람은 막지 않는다
fi

# 커밋 이메일로 팀원을 고른다. 모르는 사람이면 막지 않는다.
EMAIL=$(git -C "$DIR" config user.email 2>/dev/null || echo "")
case "${GOLE_MEMBER:-$EMAIL}" in
  가원|kgw1999zz@naver.com|202021000@sangmyung.kr|*wongakim-99*) WHO=가원 ;;
  승찬|chan6502@gmail.com|developerkscold@gmail.com|201921339@sangmyung.kr|*kscold*) WHO=승찬 ;;
  수민|tnals72441@daum.net|*codemaker-kim*) WHO=수민 ;;
  *) clear_mark; exit 0 ;;
esac

DAY=$(date +%Y-%m-%d)
# 배치: 10_개발일지/<이름>/YYYY-MM-DD/00_오늘 한 것.md
# 사람 폴더가 먼저 갈리므로 셋이 같은 날 동시에 커밋해도 충돌하지 않는다.
DAYDIR="$VAULT/10_개발일지/$WHO/$DAY"
LOG="$DAYDIR/00_오늘 한 것.md"

# 있고 템플릿만 붙여넣은 게 아니면(빈 칸 표시가 남아 있지 않으면) 통과.
if [ -f "$LOG" ] && ! grep -q '^- (커밋·PR 번호를 붙인다' "$LOG" 2>/dev/null; then
  clear_mark; exit 0
fi

WHAT=$(grep "^$SID	" "$MARK" 2>/dev/null | cut -f2 | sed 's/^/  /' | head -5)

jq -n --arg log "$LOG" --arg dir "$DAYDIR" --arg who "$WHO" --arg vault "$VAULT" --arg what "$WHAT" '{
  decision: "block",
  reason: ("이 세션에서 작업을 밖으로 내보냈는데(아래) 오늘자 개발일지가 없다.\n" + $what +
    "\n\n" + $dir + "/ 를 만들고 " + $vault + "/10_개발일지/_템플릿.md 를 복사해\n" +
    $log + " 를 쓴 뒤 마무리하라.\n" +
    "네 칸이다 — 오늘 한 것(PR·커밋 번호 필수) / 지금 열려 있는 것 / 이어받는 사람이 알아야 할 것 / 건드린 경로.\n" +
    "\"건드린 경로\"는 빼먹지 마라. 셋이 같은 모노레포에서 각자 에이전트를 돌리므로 경로가 겹치면 서로 덮어쓴다.\n" +
    "덩어리가 있으면 같은 폴더에 주제 파일(인프라·백엔드·프론트·운영이슈·기타)을 더한다. 작은 날은 00_ 하나로 끝낸다.\n" +
    $vault + "/10_개발일지/" + $who + "/README.md 의 날짜 목록 맨 위에 한 줄 추가하는 것도 잊지 마라.\n" +
    "볼트는 별개 저장소다 — 거기서 따로 커밋·푸시한다(dev 브랜치)."),
  systemMessage: ("[devlog-guard] " + $who + " 의 오늘자 개발일지가 없어 마무리를 막았습니다.")
}'
