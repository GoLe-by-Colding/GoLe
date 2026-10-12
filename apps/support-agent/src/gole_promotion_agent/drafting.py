"""초안 한 편을 만드는 codex 한 번의 호출 — 프롬프트와 출력 계약(D25).

한 호출이 ① 후보 화면 하나 고르기·캡션 ② 미처리 반려 성찰 ③ 고른 화면을 홍보 이미지로 제작을 모두 한다.
화면 내용은 바꾸지 말라고 고정한다(제작 절차 파일).

규칙(톤·데모 안내·최근 글)은 `policy` 에서 가져온다. 검증의 정본은 아래 pydantic 모델이다.
"""

from __future__ import annotations

from pathlib import Path
from typing import Any, Literal, Mapping, Sequence

from pydantic import BaseModel, Field, field_validator, model_validator

from gole_promotion_agent import memory, policy

MAX_PICKS = 1
STYLE_ID = "royal-blue-brick-mobile"
STYLE_VERSION = 1


class Pick(BaseModel):
    image: int = Field(ge=1, description="후보 이미지 번호(1부터)")
    # 검토 화면에 사진 이름으로 붙는다. 왜 골랐는지는 rationale 에 쓴다.
    label: str = Field(min_length=1, max_length=40)
    headline: str = Field(min_length=1, max_length=40, description="이미지 밖에 넣을 짧은 문구, 최대 40자/두 줄")

    @field_validator("headline", mode="before")
    @classmethod
    def _normalize_headline(cls, value: Any) -> Any:
        if not isinstance(value, str):
            return value
        normalized = value.replace("\r\n", "\n").replace("\r", "\n")
        if any((ord(char) < 32 or 127 <= ord(char) <= 159) and char != "\n" for char in normalized):
            raise ValueError("문구에 제어문자를 쓰지 않는다")
        return normalized.strip()

    @model_validator(mode="after")
    def _headline_valid(self) -> "Pick":
        if (memory.utf16_length(self.headline) > 40 or len(self.headline.split("\n")) > 2
                or not all(line.strip() for line in self.headline.split("\n"))):
            raise ValueError("문구는 빈 줄 없이 최대 40자/두 줄이다")
        return self


class DraftOutput(BaseModel):
    decision: Literal["draft", "skip"]
    skip_reason: str | None = Field(default=None, max_length=policy.MAX_RATIONALE)
    caption: str | None = Field(default=None, min_length=1, max_length=policy.MAX_CAPTION)
    rationale: str | None = Field(default=None, min_length=1, max_length=policy.MAX_RATIONALE)
    picks: list[Pick] = Field(default_factory=list, max_length=MAX_PICKS)

    @model_validator(mode="after")
    def _complete(self) -> "DraftOutput":
        if self.decision == "skip":
            if not self.skip_reason:
                raise ValueError("건너뛰면 skip_reason 이 필요하다")
        elif not (self.caption and self.rationale and self.picks):
            raise ValueError("초안에는 caption·rationale·picks 가 모두 필요하다")
        if len({pick.image for pick in self.picks}) != len(self.picks):
            raise ValueError("같은 화면을 두 번 고르지 않는다")
        return self


_NULLABLE_TEXT = {"type": ["string", "null"]}
# codex --output-schema 는 엄격 모드다 — 모든 속성이 required, additionalProperties=false. 길이·개수는 pydantic 이 본다.
OUTPUT_SCHEMA: Mapping[str, Any] = {
    "type": "object",
    "additionalProperties": False,
    "required": ["decision", "skip_reason", "caption", "rationale", "pick", "reflection"],
    "properties": {
        "decision": {"type": "string", "enum": ["draft", "skip"]},
        "skip_reason": _NULLABLE_TEXT,
        "caption": _NULLABLE_TEXT,
        "rationale": _NULLABLE_TEXT,
        "pick": {"anyOf": [{"type": "null"}, {
            "type": "object", "additionalProperties": False, "required": ["image", "label", "headline"],
            "properties": {"image": {"type": "integer"}, "label": {"type": "string"}, "headline": {"type": "string"}},
        }]},
        "reflection": {"type": "array", "items": {
            "type": "object", "additionalProperties": False,
            "required": ["kind", "content", "targets", "categories", "sourceFeedbackIds"],
            "properties": {
                "kind": {"type": "string", "enum": ["KNOWLEDGE", "PROCEDURE"]},
                "content": {"type": "string"},
                "targets": {"type": "array", "items": {"type": "string", "enum": list(memory.ALL_TARGETS)}},
                "categories": {"type": "array", "items": {"type": "string", "enum": ["FEATURE", "SERVICE"]}},
                "sourceFeedbackIds": {"type": "array", "items": {"type": "string"}},
            },
        }},
    },
}


def parse(structured: Any) -> DraftOutput:
    """한 호출의 구조화 출력에서 초안 부분. 성찰(reflection)은 memory.Reflection 이 따로 검증한다."""
    data = structured if isinstance(structured, dict) else {}
    pick = data.get("pick")
    return DraftOutput.model_validate(
        {key: data.get(key) for key in ("decision", "skip_reason", "caption", "rationale")}
        | {"picks": [pick] if pick else []}
    )


_SYSTEM = """{tone}

너는 GoLe 홍보 초안 에이전트다. {mission}

첨부 이미지는 방금 찍은 GoLe 의 실제 모바일 화면들이다. 후보 목록에 번호와 화면 경로가 있다.
첨부 이미지를 **모두 보고 실제로 무엇이 보이는지 확인한 뒤** 판단한다.

{screen_note}

지켜야 할 것:
- 캡션은 **네가 이미지에서 직접 본 화면**에 근거해 쓴다. 화면에 없는 기능을 말하지 않는다.
- 다른 이용자의 닉네임·프로필 사진·매물 사진이 크게 잡힌 화면은 고르지 않는다.
- "아직 없어요" 같은 **빈 상태 화면은 고르지 않는다** — 그것은 그 기능의 모습이 아니다.
- 고른 화면 한 장이 홍보 이미지의 주인공이다. 작업 3에서 그 화면을 휴대폰 목업에 넣어 만든다.
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


_TASKS = """

## 작업 1 — 화면 하나 고르기·캡션
{task}

- 초안: decision="draft", skip_reason=null, caption,
  rationale(이 화면을 고른 이유만 검토자가 읽을 한두 문장으로. 작업 과정·파일 저장·검증 여부는 쓰지 않는다),
  pick(image 는 후보 번호, label 은 검토자가 볼 짧은 사진 이름 — 예: "매물 탐색",
  headline 은 화면 밖에 넣을 최대 40자/두 줄의 짧은 문구. 화면에서 확인한 가치만 쓰고 근거 없는 기능·수치·성과를 말하지 않는다).
- 건너뜀: decision="skip", skip_reason, caption·rationale·pick 은 null. 이때 작업 3은 하지 않는다.
- 작업 기억의 지침은 targets 에 CAPTION·SCREEN_SELECTION 이 있는 것만 이 작업에 적용한다.

## 작업 2 — 반려 성찰 (reflection 필드)
{reflection}

## 작업 3 — 고른 화면을 홍보 이미지로 제작
decision="draft" 일 때만 한다. pick.image 번호의 첨부 원본 한 장을 아래 절차로 만든다.
headline 은 작업 1의 pick.headline 을 그대로 쓴다. 작업 기억의 지침은 targets 에 IMAGE_EDIT 가 있는 것만 적용한다.

{procedure}

완성 이미지 한 장만 out/ 폴더에 PNG 로 저장한다. 이미지 생성이 실패해도 JSON 은 낸다.

최종 메시지는 스키마에 맞는 JSON 하나뿐이다."""


def run_prompt(system: str, memory_block: str, task: str, reflection: str | None) -> str:
    """codex 한 호출의 지시문. reflection 이 None 이면 성찰할 반려가 없다."""
    return system + memory_block + _TASKS.format(
        task=task,
        reflection=reflection or "미처리 반려가 없다. reflection=[] 로 낸다.",
        procedure=POLISH_PROMPT.strip(),
    )
