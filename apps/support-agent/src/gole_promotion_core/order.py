"""러너가 지인 서버에 넣는 주문서(D29). 러너가 만들고 서버의 deliver 가 읽는다.

주문서에는 이번 실행에 쓸 것만 담는다 — 조립을 끝낸 기획 요청, 문구 자리만 빈 제작 지시문, 후보 원본, 원장에 남길
실행 정보. 작업 기억 원문은 프롬프트 안에만 있고 서버가 백엔드에서 따로 읽지 않는다.
"""

from __future__ import annotations

from typing import Any, Literal

from pydantic import BaseModel, Field

from gole_promotion_core.contract import Category

RUN_KEY_PATTERN = r"[a-z0-9][a-z0-9-]{0,63}"


class Shot(BaseModel):
    """후보 화면 한 장. 번호는 기획 요청에 실은 축소본과 같은 순서(1부터)다."""

    route: str = Field(min_length=1)
    captured_at: str = Field(min_length=1)
    data: str = Field(min_length=1, description="원본 PNG(base64)")


class ImageStage(BaseModel):
    """제작 단계에 쓸 작업 기억. 초안을 실제로 만들 때만 원장 memoryContext 에 더한다 — 건너뛴 실행에는 남지 않는다."""

    feedbackIds: list[str] = Field(default_factory=list)
    guidelineIds: list[str] = Field(default_factory=list)
    # 원장에 남기는 지침 스냅숏(id·kind·content·targets·categories).
    guidelines: list[dict[str, Any]] = Field(default_factory=list)


class Order(BaseModel):
    contract_version: int
    run_key: str = Field(pattern=RUN_KEY_PATTERN)
    category: Category
    sha: str | None = Field(default=None, pattern=r"[0-9a-f]{40}")
    data_source: Literal["DEMO", "PRODUCTION"]
    run_url: str | None = None
    agent_sha: str | None = None
    release_title: str
    model: str | None = None
    # 게이트웨이 요청 그대로(engine·prompt·json_schema·축소본 images·reasoning).
    plan: dict[str, Any]
    make_prefix: str
    make_rules: str
    shots: list[Shot] = Field(min_length=1)
    # 원장 memoryContext — 기획 단계까지 채워 온다. 제작 단계는 고른 화면이 정해진 뒤 서버가 더한다.
    memory_context: dict[str, Any]
    image_stage: ImageStage = Field(default_factory=ImageStage)
    # 이번 실행에서 성찰할 미처리 반려. 모델이 낸 제안의 근거는 이 안에서만 허용한다.
    reflection_feedback_ids: list[str] = Field(default_factory=list)
