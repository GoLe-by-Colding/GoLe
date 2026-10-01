"""Claude Code 실행 경로의 프롬프트와 출력 계약.

규칙(톤·데모 안내·최근 글)은 `policy` 와 같은 것을 쓴다. 다른 것은 손이 도구가 아니라 셸 명령이고,
결과를 도구 호출이 아니라 구조화 출력(`--json-schema`)으로 돌려준다는 점뿐이다.
"""

from __future__ import annotations

from typing import Any, Literal, Mapping, Sequence

from pydantic import BaseModel, Field, model_validator

from gole_promotion_agent import policy

CAPTURE_COMMAND = "python -m gole_promotion_agent.capture_cli"
COMPOSE_COMMAND = "python -m gole_promotion_agent.compose_cli"


class DraftImage(BaseModel):
    card: str | None = Field(default=None, pattern=r"^card-\d{2}\.png$")
    capture: str | None = Field(default=None, min_length=1, max_length=80)

    @model_validator(mode="after")
    def _exactly_one(self) -> "DraftImage":
        if (self.card is None) == (self.capture is None):
            raise ValueError("card 와 capture 중 하나만 쓴다")
        return self


class DraftOutput(BaseModel):
    decision: Literal["draft", "skip"]
    skip_reason: str | None = Field(default=None, max_length=policy.MAX_RATIONALE)
    caption: str | None = Field(default=None, min_length=1, max_length=policy.MAX_CAPTION)
    rationale: str | None = Field(default=None, min_length=1, max_length=policy.MAX_RATIONALE)
    images: list[DraftImage] = Field(default_factory=list, max_length=policy.MAX_SCREENSHOTS)

    @model_validator(mode="after")
    def _complete(self) -> "DraftOutput":
        if self.decision == "skip":
            if not self.skip_reason:
                raise ValueError("건너뛰면 skip_reason 이 필요하다")
        elif not (self.caption and self.rationale and self.images):
            raise ValueError("초안에는 caption·rationale·images 가 모두 필요하다")
        return self


# 모델에 주는 스키마. 검증의 정본은 위 pydantic 모델이고, 이것은 모양을 안내할 뿐이다.
OUTPUT_SCHEMA: Mapping[str, Any] = {
    "type": "object",
    "properties": {
        "decision": {"type": "string", "enum": ["draft", "skip"]},
        "skip_reason": {"type": "string"},
        "caption": {"type": "string", "maxLength": policy.MAX_CAPTION},
        "rationale": {"type": "string", "maxLength": policy.MAX_RATIONALE},
        "images": {
            "type": "array",
            "maxItems": policy.MAX_SCREENSHOTS,
            "items": {
                "type": "object",
                "properties": {"card": {"type": "string"}, "capture": {"type": "string"}},
            },
        },
    },
    "required": ["decision"],
}


_SYSTEM = """{tone}

너는 GoLe 홍보 초안 에이전트다. {mission}

손은 셸 명령 두 개뿐이다. 다른 명령은 실행되지 않는다.
- 화면 찍기: `{capture} --label <이름> --route <경로> [--interactions '<JSON>']`
  - 찍을 수 있는 경로는 `{capture} --label x --list-routes` 로 본다.
  - interactions 는 최대 {max_interactions}개, 종류는 셋이다:
    {{"kind":"click","role":"button","name":"필터"}} · {{"kind":"select","label":"정렬","value":"최신순"}} ·
    {{"kind":"scroll","to":"bottom"}}
  - 사진은 out/captures/ 에 남는다. Read 로 열어 실제로 무엇이 찍혔는지 **반드시 본다.**
- 홍보 카드 만들기: `{compose} --capture <찍은 라벨> --headline "<한 줄>"`
  - 찍은 화면에 브랜드 카드를 씌워 out/cards/card-NN.png 로 남긴다. 헤드라인은 {max_headline}자 이내,
    **숫자 금지**. 카드도 Read 로 열어 확인한다.
- 명령이 거부되면 이유가 출력된다. 같은 요청을 반복하지 말고 고쳐서 다시 한다.

{screen_note}

지켜야 할 것:
- 캡션과 헤드라인은 **네가 직접 찍어서 본 화면**에 근거해 쓴다. 화면에 없는 기능을 말하지 않는다.
- 스크린샷에 다른 이용자의 닉네임·프로필 사진·매물 사진이 크게 잡히지 않는 화면을 고른다.
- "아직 없어요" 같은 **빈 상태 화면은 쓰지 않는다** — 그것은 그 기능의 모습이 아니다.
  데이터가 채워진 다른 화면을 고르거나, 없으면 다른 주제로 바꾼다.
- 보여줄 만한 것이 없으면 억지로 만들지 말고 건너뛴다.

끝낼 때 결과를 구조화 출력으로 낸다:
- 초안: decision="draft", caption, rationale(왜 이 화면을 골랐는지 검토자가 읽을 한두 문장),
  images(올릴 순서대로. 카드는 {{"card":"card-01.png"}}, 카드 없이 원본은 {{"capture":"라벨"}}).
  첫 장은 카드로 한다.
- 건너뜀: decision="skip", skip_reason.

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


def system_prompt(history: Sequence[Mapping[str, Any]], demo: bool, service: bool) -> str:
    from gole_promotion_agent.compose_cli import MAX_HEADLINE

    return _SYSTEM.format(
        tone=policy.tone_guide(),
        mission=_SERVICE_MISSION if service else _FEATURE_MISSION,
        capture=CAPTURE_COMMAND,
        compose=COMPOSE_COMMAND,
        max_interactions=policy.MAX_INTERACTIONS,
        max_headline=MAX_HEADLINE,
        screen_note=policy.screen_note(demo),
        history=policy.render_history(history),
    )


def task_prompt(*, service: bool, subject: str | None = None, diff: str | None = None) -> str:
    if service:
        return "오늘 올릴 GoLe 서비스 소개 글을 하나 만들어."
    return (
        f"릴리스 제목(단서로만): {subject}\n\n"
        f"apps/web/src 변경:\n```diff\n{diff}\n```\n\n"
        "이 변화가 화면에서 어떻게 보이는지 찍어 보고 초안을 만들지 판단해."
    )
