"""모델 출력 계약. 러너는 이 스키마를 기획 호출에 싣고, 결과를 받는 쪽은 같은 규칙으로 검증한다(D25·D29).

기획 호출의 출력 = 초안 부분(DraftOutput) + 반려 성찰(Reflection). 모델 출력은 저장 전에 다시 검증한다.
"""

from __future__ import annotations

import json
from typing import Any, Literal, Mapping, Sequence

from pydantic import BaseModel, Field, field_validator, model_validator

from gole_promotion_core import limits

Target = Literal["CAPTION", "SCREEN_SELECTION", "IMAGE_EDIT"]
Category = Literal["FEATURE", "SERVICE"]
Kind = Literal["KNOWLEDGE", "PROCEDURE"]
ALL_TARGETS: tuple[Target, ...] = ("CAPTION", "SCREEN_SELECTION", "IMAGE_EDIT")
MAX_FEEDBACK = 3

MAX_PICKS = 1
MAX_HEADLINE = 40
STYLE_ID = "royal-blue-brick-mobile"
STYLE_VERSION = 1


def utf16_length(text: str) -> int:
    return len(text.encode("utf-16-le", errors="surrogatepass")) // 2


def headline_block(headline: str) -> str:
    """제작 지시문 가운데 모델이 정한 문구 자리. 러너(길이 미리 재기)와 서버(실제 제작)가 같은 모양을 쓴다."""
    return "\n외부 제목 문구 (JSON 데이터):\n" + json.dumps({"headline": headline}, ensure_ascii=False)


def edit_prompt(prefix: str, headline: str, rules: str) -> str:
    """제작 호출의 지시문이자 검토 화면에 남는 기록 — 고정 절차·작업 기억(prefix) + 문구 + 진행 규칙."""
    return prefix + headline_block(headline) + rules


class Pick(BaseModel):
    image: int = Field(ge=1, description="후보 이미지 번호(1부터)")
    # 검토 화면에 사진 이름으로 붙는다. 왜 골랐는지는 rationale 에 쓴다.
    label: str = Field(min_length=1, max_length=40)
    headline: str = Field(min_length=1, max_length=MAX_HEADLINE, description="이미지 밖에 넣을 짧은 문구, 최대 40자/두 줄")

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
        if (utf16_length(self.headline) > MAX_HEADLINE or len(self.headline.split("\n")) > 2
                or not all(line.strip() for line in self.headline.split("\n"))):
            raise ValueError("문구는 빈 줄 없이 최대 40자/두 줄이다")
        return self


class DraftOutput(BaseModel):
    decision: Literal["draft", "skip"]
    skip_reason: str | None = Field(default=None, max_length=limits.MAX_RATIONALE)
    caption: str | None = Field(default=None, min_length=1, max_length=limits.MAX_CAPTION)
    rationale: str | None = Field(default=None, min_length=1, max_length=limits.MAX_RATIONALE)
    picks: list[Pick] = Field(default_factory=list, max_length=MAX_PICKS)

    @field_validator("caption", "rationale", "skip_reason")
    @classmethod
    def _fits_java_length(cls, value: str | None, info: Any) -> str | None:
        # 백엔드는 UTF-16 단위로 잰다. 이모지가 많은 글은 글자 수로는 통과해도 납품 전체가 400 이 된다.
        limit = limits.MAX_CAPTION if info.field_name == "caption" else limits.MAX_RATIONALE
        if value is not None and utf16_length(value) > limit:
            raise ValueError(f"{info.field_name} 은 UTF-16 {limit}자 이하다")
        return value

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
                "targets": {"type": "array", "items": {"type": "string", "enum": list(ALL_TARGETS)}},
                "categories": {"type": "array", "items": {"type": "string", "enum": ["FEATURE", "SERVICE"]}},
                "sourceFeedbackIds": {"type": "array", "items": {"type": "string"}},
            },
        }},
    },
}


def parse(structured: Any) -> DraftOutput:
    """한 호출의 구조화 출력에서 초안 부분. 성찰(reflection)은 Reflection 이 따로 검증한다."""
    data = structured if isinstance(structured, dict) else {}
    pick = data.get("pick")
    return DraftOutput.model_validate(
        {key: data.get(key) for key in ("decision", "skip_reason", "caption", "rationale")}
        | {"picks": [pick] if pick else []}
    )


class Proposal(BaseModel):
    kind: Kind
    content: str = Field(min_length=1, max_length=1_000)
    targets: list[Target] = Field(min_length=1, max_length=3)
    categories: list[Category] = Field(min_length=1, max_length=2)
    sourceFeedbackIds: list[str] = Field(min_length=1, max_length=MAX_FEEDBACK)

    @model_validator(mode="after")
    def _valid(self) -> "Proposal":
        if not self.content.strip():
            raise ValueError("빈 지침은 저장하지 않는다")
        for values in (self.targets, self.categories, self.sourceFeedbackIds):
            if len(set(values)) != len(values):
                raise ValueError("지침 범위와 근거는 중복하지 않는다")
        return self


class Reflection(BaseModel):
    proposals: list[Proposal] = Field(max_length=3)

    def payload(self, feedback_ids: Sequence[str], run_key: str) -> dict[str, Any]:
        if len(set(feedback_ids)) != len(feedback_ids):
            raise ValueError("같은 반려를 두 번 처리하지 않는다")
        allowed = set(feedback_ids)
        if any(not set(item.sourceFeedbackIds).issubset(allowed) for item in self.proposals):
            raise ValueError("이번에 읽은 반려만 지침 근거로 쓴다")
        return {"feedbackIds": list(feedback_ids), "runKey": run_key, **self.model_dump()}
