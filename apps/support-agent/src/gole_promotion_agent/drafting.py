"""초안 한 편을 만드는 codex 두 번의 호출의 지시문(D25). 출력 계약은 gole_promotion_core.contract 에 있다.

1. 기획: 후보 화면 축소본을 한 번에 보고 화면 하나 고르기·캡션·반려 성찰(구조화 출력, 도구 없음).
2. 제작: 고른 원본 한 장을 고정 절차로 홍보 이미지로 만든다. 화면 내용은 바꾸지 말라고 고정한다.
"""

from __future__ import annotations

from pathlib import Path
from typing import Any, Mapping, Sequence

from gole_promotion_agent import policy

_SYSTEM = """{tone}

너는 GoLe 홍보 초안 에이전트다. {mission}

첨부 이미지는 방금 찍은 GoLe 의 실제 모바일 화면들이다. 후보 목록에 번호와 화면 경로가 있다.
첨부 이미지를 **모두 보고 실제로 무엇이 보이는지 확인한 뒤** 판단한다.

{screen_note}

지켜야 할 것:
- 캡션은 **네가 이미지에서 직접 본 화면**에 근거해 쓴다. 화면에 없는 기능을 말하지 않는다.
- 다른 이용자의 닉네임·프로필 사진·매물 사진이 크게 잡힌 화면은 고르지 않는다.
- "아직 없어요" 같은 **빈 상태 화면은 고르지 않는다** — 그것은 그 기능의 모습이 아니다.
- 고른 화면 한 장이 홍보 이미지의 주인공이다. 다음 단계가 그 화면을 휴대폰 목업에 넣어 만든다.
- 보여줄 만한 것이 없으면 억지로 만들지 말고 건너뛴다.

{history}"""

_FEATURE_MISSION = (
    "방금 배포된 릴리스 하나를 보고 홍보할 사용자 가시 변화가 있는지 판단해, 있다면 초안을 만든다. "
    "커밋 제목·diff 는 무엇이 바뀌었는지 찾는 단서로만 쓰고 그대로 옮기지 않는다."
)
_SERVICE_MISSION = (
    "오늘은 새 기능이 아니라 GoLe 라는 서비스 자체를 소개하는 글을 하나 쓴다. 레고 중고거래에서 "
    "GoLe 가 주는 가치 하나를 주제로 고른다 — 예: 세트 번호로 찾는 매물, 구매확정까지 돈을 맡아 두는 "
    "안전거래, 내 컬렉션 정리, 판매자와의 대화, 커뮤니티. 최근 글과 같은 주제·화면·구조를 반복하지 않는다."
)

# 작업 기억을 붙인 실제 지시문을 검토 화면에 남긴다. 백엔드 상한은 6000자다.
POLISH_PROMPT = (Path(__file__).parent / "procedures" / "royal-blue-brick-mobile-v1.md").read_text(encoding="utf-8")


def system_prompt(history: Sequence[Mapping[str, Any]], demo: bool, service: bool) -> str:
    return _SYSTEM.format(
        tone=policy.tone_guide(),
        mission=_SERVICE_MISSION if service else _FEATURE_MISSION,
        screen_note=policy.screen_note(demo),
        history=policy.render_history(history),
    )


def task_prompt(
    routes: Sequence[str], *, service: bool, subject: str | None = None, diff: str | None = None
) -> str:
    listed = "\n".join(f"{index}. {route}" for index, route in enumerate(routes, start=1))
    head = (
        "오늘 올릴 GoLe 서비스 소개 글을 하나 만들어."
        if service
        else (
            f"릴리스 제목(단서로만): {subject}\n\napps/web/src 변경:\n```diff\n{diff}\n```\n\n"
            "이 변화가 화면에서 보이는지 확인하고 초안을 만들지 판단해."
        )
    )
    return f"{head}\n\n후보 화면(첨부 이미지와 같은 순서):\n{listed}"


_PLAN_TASKS = """

## 작업 1 — 화면 하나 고르기·캡션
{task}

- 초안: decision="draft", skip_reason=null, caption,
  rationale(이 화면을 고른 이유만 검토자가 읽을 한두 문장으로. 작업 과정은 쓰지 않는다),
  pick(image 는 후보 번호, label 은 검토자가 볼 짧은 사진 이름 — 예: "매물 탐색",
  headline 은 화면 밖에 크게 넣을 문구. 두 줄로 나누고 한 줄은 15자 안쪽, 전체 40자 이하.
  화면에서 확인한 가치만 쓰고 근거 없는 기능·수치·성과를 말하지 않는다).
- 건너뜀: decision="skip", skip_reason, caption·rationale·pick 은 null.

## 작업 2 — 반려 성찰 (reflection 필드)
{reflection}

이미지는 만들지 않는다 — 고른 화면은 다음 단계가 따로 제작한다. 첨부 이미지는 고르기용 축소본이다.
도구·셸을 쓰지 말고 첨부 이미지만 보고 곧바로 스키마에 맞는 JSON 하나만 낸다."""

# 제작 호출 끝에 붙는 고정 지시. 확인 단계마다 맥락 전체가 다시 실려 토큰이 커진다(D25 실측).
MAKE_RULES = "\n\n셸 명령·파일 목록 확인·저장 결과 재확인 없이 이미지를 만들고 바로 끝낸다."


def plan_prompt(system: str, memory_block: str, task: str, reflection: str | None) -> str:
    """기획 호출(고르기·캡션·성찰)의 지시문. reflection 이 None 이면 성찰할 반려가 없다."""
    return system + memory_block + _PLAN_TASKS.format(
        task=task, reflection=reflection or "미처리 반려가 없다. reflection=[] 로 낸다.",
    )
