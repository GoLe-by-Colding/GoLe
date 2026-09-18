"""허용한 도구와 자원 상한. 자유 프롬프트는 받지 않는다."""

from __future__ import annotations

import hashlib
import json
import re
from pathlib import Path
from typing import Annotated, Any, Literal, Mapping, Sequence

from pydantic import BaseModel, Field

SHA_PATTERN = re.compile(r"[0-9a-f]{40}\Z")

# 후보 선정 (스펙 D11)
MAX_WALK_COMMITS = 10
MAX_WALK_DAYS = 7
MAX_DRAFTS_PER_RUN = 3

# 대화 루프 (스펙 D16·D17)
MAX_TURNS = 20
MAX_CONTEXT_IMAGES = 4
DEFAULT_MODEL = "claude-opus-5"
MAX_TOKENS = 16_000

# 캡션 (스펙 D13)
MAX_CAPTION = 450

# 캡처 (스펙 D12)
MAX_INTERACTIONS = 6
MAX_SCREENSHOTS = 10
INTERACTION_TIMEOUT_SECONDS = 5.0
CAPTURE_TIMEOUT_SECONDS = 45.0
NAVIGATION_TIMEOUT_SECONDS = 30.0
READ_ONLY_METHODS = frozenset({"GET", "HEAD", "OPTIONS"})

# 게이트·보존 (스펙 D17·D18)
MAX_PENDING_REVIEW = 5
HISTORY_LIMIT = 10
RETENTION_DAYS = 7

FORBIDDEN_ROUTE = re.compile(
    r"\A/(?:admin(?:/|\Z)|auth(?:/|\Z)|login\Z|signup\Z|forgot-password\Z|verify\Z|payments(?:/|\Z))"
)


def is_public_capture_route(route: str) -> bool:
    return route.startswith("/") and not route.startswith("//") and not FORBIDDEN_ROUTE.search(route)


def capture_key(route: str, interactions: Sequence[Mapping[str, Any]]) -> str:
    """같은 화면을 두 번 찍지 않기 위한 키. 재개했을 때도 같은 값이어야 한다(스펙 D12)."""
    canonical = json.dumps(
        {"route": route, "interactions": list(interactions)},
        sort_keys=True,
        ensure_ascii=False,
        separators=(",", ":"),
    )
    return hashlib.sha256(canonical.encode("utf-8")).hexdigest()[:16]


def tone_guide() -> str:
    """사람도 읽는 기준 문서를 그대로 시스템 프롬프트에 싣는다(스펙 D13)."""
    return (Path(__file__).parent / "prompts" / "caption-tone.md").read_text(encoding="utf-8")


class Click(BaseModel):
    kind: Literal["click"]
    role: str = Field(min_length=1, max_length=40)
    name: str = Field(min_length=1, max_length=200)


class Select(BaseModel):
    kind: Literal["select"]
    label: str = Field(min_length=1, max_length=200)
    value: str = Field(min_length=1, max_length=200)


class Scroll(BaseModel):
    kind: Literal["scroll"]
    to: Literal["top", "bottom"]


Interaction = Annotated[Click | Select | Scroll, Field(discriminator="kind")]


class ListReleasesInput(BaseModel):
    pass


class ReadReleaseDiffInput(BaseModel):
    sha: str = Field(pattern=r"^[0-9a-f]{40}$")


class ListRoutesInput(BaseModel):
    pass


class CaptureInput(BaseModel):
    route: str = Field(min_length=1, max_length=200)
    interactions: list[Interaction] = Field(default_factory=list, max_length=MAX_INTERACTIONS)
    label: str = Field(min_length=1, max_length=80)


class SubmitPromotionDraftInput(BaseModel):
    sha: str = Field(pattern=r"^[0-9a-f]{40}$")
    caption: str = Field(min_length=1, max_length=MAX_CAPTION)
    screenshot_labels: list[str] = Field(min_length=1, max_length=MAX_SCREENSHOTS)


TOOL_MODELS: Mapping[str, type[BaseModel]] = {
    "list_releases": ListReleasesInput,
    "read_release_diff": ReadReleaseDiffInput,
    "list_routes": ListRoutesInput,
    "capture": CaptureInput,
    "submit_promotion_draft": SubmitPromotionDraftInput,
}

_DESCRIPTIONS: Mapping[str, str] = {
    "list_releases": "아직 홍보하지 않은 릴리스 후보를 나열한다.",
    "read_release_diff": "현재 후보 릴리스의 apps/web/src 변경만 읽는다.",
    "list_routes": "캡처할 수 있는 공개 정적 라우트를 나열한다.",
    "capture": (
        "라우트로 이동해 상호작용을 순서대로 실행한 뒤 화면을 찍는다. "
        "호출 하나가 독립적이므로 이전 호출의 페이지 상태는 남지 않는다. "
        "같은 라우트·상호작용 조합을 다시 요청하면 이미 찍은 화면을 그대로 돌려준다."
    ),
    "submit_promotion_draft": (
        "고른 스크린샷과 캡션으로 홍보 초안을 만들어 검토 요청 상태까지 올린다."
    ),
}


def tool_schemas() -> tuple[dict[str, Any], ...]:
    return tuple(
        {
            "name": name,
            "description": _DESCRIPTIONS[name],
            "input_schema": model.model_json_schema(),
        }
        for name, model in TOOL_MODELS.items()
    )


_SYSTEM_TEMPLATE = """{tone}

너는 GoLe 홍보 초안 에이전트다. 방금 배포된 릴리스 하나를 조사해 홍보할 가치가 있는지
판단하고, 가치가 있다면 초안을 만든다.

작업 순서:
1. read_release_diff 로 이 릴리스가 무엇을 바꿨는지 읽는다.
2. list_routes 로 찍을 수 있는 화면을 확인한다.
3. capture 로 사용자에게 보이는 변화가 드러나는 화면을 순서대로 찍는다.
4. 캡션을 쓰고 submit_promotion_draft 를 호출한다.

지켜야 할 것:
- 캡션은 diff 원문이 아니라 **네가 직접 찍어서 본 화면**에 근거해 쓴다.
- 커밋 메시지는 무엇이 바뀌었는지 찾는 단서로만 쓰고 그대로 옮기지 않는다.
- 보여줄 만한 사용자 가시 변화가 없다고 판단하면 초안을 만들지 말고 그렇게 말하고 끝낸다.
  억지로 만드는 것보다 건너뛰는 편이 낫다.
- 스크린샷에 다른 이용자의 닉네임·프로필 사진·매물 사진이 크게 잡히지 않는 화면을 고른다.

{history}"""

_NO_HISTORY = "아직 올린 글이 없다. 첫 글이므로 톤 가이드만 따른다."

_HISTORY_HEADER = """최근에 올렸거나 올리려던 글이다. **같은 구조·같은 리듬을 반복하지 마라.**
직전 글이 이모지로 끝났으면 이번엔 없이, 직전이 세 문장이었으면 이번엔 길이를 바꾼다.
같은 화면을 또 찍어 비슷한 이야기를 하지 않는다. 반려된 글이 있으면 그 사유를 피한다."""


def build_system_prompt(history: Sequence[Mapping[str, Any]]) -> str:
    """이력은 시스템 프롬프트(안정 접두사)에 둔다 — 프롬프트 캐싱이 걸린다(스펙 D18)."""
    if not history:
        rendered = _NO_HISTORY
    else:
        lines = [_HISTORY_HEADER, ""]
        for entry in history:
            status = entry.get("status", "?")
            caption = str(entry.get("caption", "")).replace("\n", " ")
            lines.append(f"- [{status}] {caption}")
            reason = entry.get("rejectionReason")
            if reason:
                lines.append(f"  반려 사유: {reason}")
        rendered = "\n".join(lines)
    return _SYSTEM_TEMPLATE.format(tone=tone_guide(), history=rendered)
