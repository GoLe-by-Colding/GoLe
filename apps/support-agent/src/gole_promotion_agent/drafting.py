"""초안 한 편을 만드는 두 번의 모델 호출 — 프롬프트와 출력 계약.

1. Claude: 미리 찍어 둔 후보 화면들을 한 번에 보고, 올릴 화면을 고르고 캡션을 쓴다(구조화 출력).
2. Codex: 고른 실제 화면을 홍보 이미지로 다듬는다(생성형 편집). 화면 내용은 바꾸지 말라고 고정한다.

규칙(톤·데모 안내·최근 글)은 `policy` 에서 가져온다. 검증의 정본은 아래 pydantic 모델이다.
"""

from __future__ import annotations

from typing import Any, Literal, Mapping, Sequence

from pydantic import BaseModel, Field, model_validator

from gole_promotion_agent import policy

MAX_PICKS = 3


class Pick(BaseModel):
    image: int = Field(ge=1, description="후보 이미지 번호(1부터)")
    # 검토 화면에 사진 이름으로 붙는다. 왜 골랐는지는 rationale 에 쓴다.
    label: str = Field(min_length=1, max_length=40)


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


OUTPUT_SCHEMA: Mapping[str, Any] = {
    "type": "object",
    "properties": {
        "decision": {"type": "string", "enum": ["draft", "skip"]},
        "skip_reason": {"type": "string"},
        "caption": {"type": "string", "maxLength": policy.MAX_CAPTION},
        "rationale": {"type": "string", "maxLength": policy.MAX_RATIONALE},
        "picks": {
            "type": "array",
            "maxItems": MAX_PICKS,
            "items": {
                "type": "object",
                "properties": {"image": {"type": "integer"}, "label": {"type": "string", "maxLength": 40}},
                "required": ["image", "label"],
            },
        },
    },
    "required": ["decision"],
}

_SYSTEM = """{tone}

너는 GoLe 홍보 초안 에이전트다. {mission}

첨부 이미지는 방금 찍은 GoLe 의 실제 화면들이다. 이미지 목록에 번호와 화면 경로가 있다.
Read 로 이미지를 **모두 열어 실제로 무엇이 보이는지 확인한 뒤** 판단한다.

{screen_note}

지켜야 할 것:
- 캡션은 **네가 이미지에서 직접 본 화면**에 근거해 쓴다. 화면에 없는 기능을 말하지 않는다.
- 다른 이용자의 닉네임·프로필 사진·매물 사진이 크게 잡힌 화면은 고르지 않는다.
- "아직 없어요" 같은 **빈 상태 화면은 고르지 않는다** — 그것은 그 기능의 모습이 아니다.
- 고른 화면은 디자이너가 브라우저 목업에 넣어 다듬는다. 화면 자체가 홍보 이미지의 주인공이다.
- 보여줄 만한 것이 없으면 억지로 만들지 말고 건너뛴다.

결과는 구조화 출력으로 낸다:
- 초안: decision="draft", caption, rationale(왜 이 화면들을 골랐는지 검토자가 읽을 한두 문장),
  picks(올릴 순서대로 최대 {max_picks}장. image 는 후보 번호, label 은 검토자가 볼 짧은 사진 이름 — 예: "매물 탐색").
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

# 작업 기억을 붙인 실제 지시문을 검토 화면에 남긴다. 백엔드 상한은 6000자다.
POLISH_PROMPT = (
    "첨부 이미지는 GoLe(레고 중고거래) 웹앱의 실제 화면 캡처다. 이것을 SNS 홍보용 정사각형 이미지로 다듬어라. "
    "화면을 살짝 기울인 브라우저 창 목업에 넣고, 코발트 블루(#2f56e6) 배경과 부드러운 그림자를 더한다. "
    "화면 안의 글자·숫자·버튼·아이콘·배치는 **원본과 똑같이** 유지한다. 새 글자·로고·워터마크를 넣지 않는다. "
    "레고 로고나 미니피규어를 그리지 않는다."
)


def system_prompt(history: Sequence[Mapping[str, Any]], demo: bool, service: bool) -> str:
    return _SYSTEM.format(
        tone=policy.tone_guide(),
        mission=_SERVICE_MISSION if service else _FEATURE_MISSION,
        screen_note=policy.screen_note(demo),
        max_picks=MAX_PICKS,
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
