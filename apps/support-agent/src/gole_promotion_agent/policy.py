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
# RETENTION_DAYS 와 같은 값이어야 한다 — 건너뛴 커밋의 원장이 세션 디렉터리이므로,
# 보존이 먼저 끝나면 그 커밋이 탐색 창으로 되돌아와 유료로 재평가된다. 한쪽만 바꾸지 않는다.
MAX_WALK_DAYS = 7
MAX_DRAFTS_PER_RUN = 3

# 대화 루프 (스펙 D16·D17)
MAX_TURNS = 20
MAX_CONTEXT_IMAGES = 4
DEFAULT_MODEL = "claude-opus-5"
MAX_TOKENS = 16_000
# diff 상한은 바이트가 아니라 **컨텍스트 예산**이어야 한다. 예전 2MB 상한은 토큰으로 환산하면
# 컨텍스트를 훌쩍 넘겨서, 큰 릴리스 하나가 매일 같은 자리에서 실패하게 만든다. 잘렸다는 사실을
# 모델에게 알려 주는 것까지가 이 상한의 일이다 — 조용히 자르면 모델이 없는 변경을 없다고 단정한다.
MAX_DIFF_CHARS = 120_000
DIFF_TRUNCATED_NOTICE = "\n\n[잘림] 변경이 너무 커서 여기까지만 보여준다. 나머지는 읽을 수 없다."

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

# 로그인한 상태로 찍기 때문에 새로 필요해진 제외 목록이다(스펙 D12·D19).
# 익명일 때 이 라우트들은 로그인 게이트만 보여 무해했지만, 인증 뒤에는 봇 계정의 이메일·
# 전화번호·알림 내역이 그대로 렌더링된다. URL 패턴이 막지 않는다고 찍어도 되는 화면은 아니다.
PRIVATE_ROUTE = re.compile(r"\A/(?:profile(?:/|\Z)|notifications(?:/|\Z)|settings(?:/|\Z))")


def is_public_capture_route(route: str) -> bool:
    if not route.startswith("/") or route.startswith("//"):
        return False
    return not FORBIDDEN_ROUTE.search(route) and not PRIVATE_ROUTE.search(route)


def capture_key(route: str, interactions: Sequence[Mapping[str, Any]]) -> str:
    """같은 화면을 두 번 찍지 않기 위한 키. 재개했을 때도 같은 값이어야 한다(스펙 D12)."""
    canonical = json.dumps(
        {"route": route, "interactions": list(interactions)},
        sort_keys=True,
        ensure_ascii=False,
        separators=(",", ":"),
    )
    return hashlib.sha256(canonical.encode("utf-8")).hexdigest()[:16]


# --------------------------------------------------------------- 인증된 캡처 (D12·D14)

# 웹앱이 Authorization 헤더를 만들 때 읽는 localStorage 키
# (`apps/web/src/shared/api/session-auth.ts`). 백엔드의 SessionCookie.resolve() 가
# Bearer 헤더를 쿠키보다 우선하므로, 이 키만 심으면 브라우저에서 로그인 POST 를 하지 않고도
# 인증된 화면을 받을 수 있다 — read-only 가드(GET/HEAD/OPTIONS)를 그대로 지킬 수 있는 이유다.
SESSION_STORAGE_KEY = "gole.session"

# 세션에서 브라우저로 넘길 필드만 추린다. 백엔드 응답을 통째로 심지 않는다.
SESSION_FIELDS = ("accountId", "sessionToken", "role", "onboardingRequired")

# 홍보물에 나오면 안 되는 요소를 가리는 표식. 클래스명·DOM 구조에 기대지 않으려고
# `apps/web` 쪽에 이 속성을 직접 단다 — 리팩터링해도 캡처가 조용히 깨지지 않는다.
CAPTURE_HIDE_ATTRIBUTE = "data-promotion-hide"
CAPTURE_HIDE_CSS = f"[{CAPTURE_HIDE_ATTRIBUTE}]{{display:none !important}}"


def session_init_script(session: Mapping[str, Any]) -> str:
    """캡처 컨텍스트를 로그인 상태로 만드는 초기화 스크립트(스펙 D12).

    토큰은 브라우저 안에만 머문다 — 모델에는 PNG 와 경로만 나가므로 D14 의 "세션 토큰을
    모델 컨텍스트에 노출하지 않는다"는 그대로 지켜진다.
    """
    payload = {key: session[key] for key in SESSION_FIELDS if key in session}
    if not payload.get("sessionToken"):
        raise ValueError("SESSION_TOKEN_REQUIRED")
    # ensure_ascii 로 U+2028·U+2029 까지 이스케이프하고, 한 번 더 감싸 JS 문자열 리터럴을
    # 만든다. 토큰에 따옴표가 섞여도 스크립트가 깨지지 않는다.
    literal = json.dumps(json.dumps(payload, ensure_ascii=True, separators=(",", ":")))
    key = json.dumps(SESSION_STORAGE_KEY)
    return f"window.localStorage.setItem({key}, {literal});"


def capture_chrome_script() -> str:
    """운영자 메뉴·온보딩 배너처럼 기능과 무관한 요소를 캡처에서 감춘다(스펙 D12).

    봇 계정이 ADMIN 이라 헤더에 `관리자` 링크가 뜨고, 온보딩 배너는 모든 화면 위에 걸린다.
    둘 다 홍보물에 나올 이유가 없다.
    """
    style_id = json.dumps("gole-promotion-capture-style")
    css = json.dumps(CAPTURE_HIDE_CSS)
    return f"""
(() => {{
  const apply = () => {{
    if (document.getElementById({style_id})) return;
    const root = document.head || document.documentElement;
    if (!root) return;
    const style = document.createElement("style");
    style.id = {style_id};
    style.textContent = {css};
    root.appendChild(style);
  }};
  apply();
  document.addEventListener("DOMContentLoaded", apply);
}})();
""".strip()


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

네가 보는 화면에 대해 알아둘 것:
- 너는 **봇 전용 계정으로 로그인된 상태**로 화면을 본다. 로그인해야 쓸 수 있는 기능도
  찍을 수 있다는 뜻이다.
- 다만 이 계정은 **자기 데이터가 없다** — 컬렉션·대화·알림·등록한 매물이 비어 있다.
  화면이 "아직 없어요" 같은 빈 상태로만 보이면 그것은 그 기능의 모습이 아니다.
  그런 화면은 홍보에 쓰지 말고 다른 화면을 고르거나, 보여줄 것이 없으면 건너뛴다.

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

# 관리자 화면에 그대로 보여도 되는 실패 사유(스펙 D20).
#
# 예외 원문은 기본적으로 내보내지 않는다 — diff·캡션·파일 경로가 섞일 수 있기 때문이다.
# 다만 아래 값들은 이 패키지가 **직접 정한 고정 코드**라 내용이 없고, 이것마저 가리면
# 관리자는 "ValueError" 만 보고 왜 실패했는지 알 수 없다. 목록에 없는 예외는 지금처럼
# 종류 이름만 남긴다.
SAFE_FAILURE_CODES = frozenset(
    {
        "ANTHROPIC_KEY_REQUIRED",
        "BOT_ACCOUNT_NOT_ADMIN",
        "DUPLICATE_SOURCE_COMMIT",
        "EXTERNAL_DISABLED",
        "INVALID_SHA",
        "MODEL_REFUSED",
        "NAVIGATED_OFF_SITE",
        "NAVIGATED_TO_FORBIDDEN_ROUTE",
        "NO_WEB_CHANGES",
        "ROUTE_NOT_ALLOWED",
        "SESSION_TOKEN_MISSING",
        "SESSION_TOKEN_REQUIRED",
        "UNKNOWN_INTERACTION",
    }
)
