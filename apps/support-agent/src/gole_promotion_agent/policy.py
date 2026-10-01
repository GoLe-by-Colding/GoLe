"""캡처 가드·자원 상한·프롬프트 재료. 코드로 거는 규칙은 전부 여기 있다."""

from __future__ import annotations

import hashlib
import json
import re
from pathlib import Path
from typing import Annotated, Any, Literal, Mapping, Sequence

from pydantic import BaseModel, Field

SHA_PATTERN = re.compile(r"[0-9a-f]{40}\Z")

# diff 상한은 바이트가 아니라 **컨텍스트 예산**이어야 한다. 예전 2MB 상한은 토큰으로 환산하면
# 컨텍스트를 훌쩍 넘겨서, 큰 릴리스 하나가 매일 같은 자리에서 실패하게 만든다. 잘렸다는 사실을
# 모델에게 알려 주는 것까지가 이 상한의 일이다 — 조용히 자르면 모델이 없는 변경을 없다고 단정한다.
MAX_DIFF_CHARS = 120_000
DIFF_TRUNCATED_NOTICE = "\n\n[잘림] 변경이 너무 커서 여기까지만 보여준다. 나머지는 읽을 수 없다."

# 캡션 (스펙 D13)
MAX_CAPTION = 450
MAX_RATIONALE = 300

# 캡처 (스펙 D12)
MAX_INTERACTIONS = 6
MAX_SCREENSHOTS = 10
INTERACTION_TIMEOUT_SECONDS = 5.0
CAPTURE_TIMEOUT_SECONDS = 45.0
NAVIGATION_TIMEOUT_SECONDS = 30.0
READ_ONLY_METHODS = frozenset({"GET", "HEAD", "OPTIONS"})

# 게이트 (스펙 D18)
MAX_PENDING_REVIEW = 5
HISTORY_LIMIT = 10

FORBIDDEN_ROUTE = re.compile(
    r"\A/(?:admin(?:/|\Z)|auth(?:/|\Z)|login\Z|signup\Z|forgot-password\Z|verify\Z|payments(?:/|\Z))"
)

# 로그인한 상태로 찍기 때문에 새로 필요해진 제외 목록이다(스펙 D12·D19).
# 익명일 때 이 라우트들은 로그인 게이트만 보여 무해했지만, 인증 뒤에는 봇 계정의 이메일·
# 전화번호·알림 내역이 그대로 렌더링된다. URL 패턴이 막지 않는다고 찍어도 되는 화면은 아니다.
PRIVATE_ROUTE = re.compile(r"\A/(?:profile(?:/|\Z)|notifications(?:/|\Z)|settings(?:/|\Z))")


# 데모 데이터로 찍을 때만 막는다. 가짜 체결가로 그린 시세 화면이 밖에 나가면 "이 세트가 이 가격에
# 거래된다"는 잘못된 시장 정보가 된다. 기능을 보여주는 화면과 달리 숫자 자체가 사실 주장이다.
DEMO_FACT_ROUTE = re.compile(r"\A/prices(?:/|\Z)")


def is_public_capture_route(route: str, demo: bool = False) -> bool:
    if not route.startswith("/") or route.startswith("//"):
        return False
    if demo and DEMO_FACT_ROUTE.search(route):
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


class CaptureInput(BaseModel):
    route: str = Field(min_length=1, max_length=200)
    interactions: list[Interaction] = Field(default_factory=list, max_length=MAX_INTERACTIONS)
    label: str = Field(min_length=1, max_length=80)


_NO_HISTORY = "아직 올린 글이 없다. 첫 글이므로 톤 가이드만 따른다."

_HISTORY_HEADER = """최근에 올렸거나 올리려던 글이다. **같은 구조·같은 리듬을 반복하지 마라.**
직전 글이 이모지로 끝났으면 이번엔 없이, 직전이 세 문장이었으면 이번엔 길이를 바꾼다.
같은 화면을 또 찍어 비슷한 이야기를 하지 않는다. 반려된 글이 있으면 그 사유를 피한다."""


_BOT_SCREEN_NOTE = """네가 보는 화면에 대해 알아둘 것:
- 너는 **봇 전용 계정으로 로그인된 상태**로 화면을 본다. 로그인해야 쓸 수 있는 기능도
  찍을 수 있다는 뜻이다.
- 다만 이 계정은 **자기 데이터가 없다** — 컬렉션·대화·알림·등록한 매물이 비어 있다.
  화면이 "아직 없어요" 같은 빈 상태로만 보이면 그것은 그 기능의 모습이 아니다.
  그런 화면은 홍보에 쓰지 말고 다른 화면을 고르거나, 보여줄 것이 없으면 건너뛴다."""

_DEMO_SCREEN_NOTE = """네가 보는 화면에 대해 알아둘 것:
- 너는 **데모 데이터로 채운 연습용 사이트**를 관리자 계정으로 로그인해 본다. 매물·닉네임·
  가격·후기는 실제가 아니다. 이 화면은 "이런 걸 할 수 있다"를 보여주는 데만 쓴다.
- 캡션에 **구체적인 가격·체결가·거래 건수·이용자 수·별점을 쓰지 마라.** 데모 숫자가 사실처럼
  읽힌다. 가격이나 거래 숫자가 화면의 주인공인 장면은 고르지 않는다.
- 관리자 링크처럼 운영자에게만 보이는 요소는 촬영 때 감춰진다."""


def render_history(history: Sequence[Mapping[str, Any]]) -> str:
    if not history:
        return _NO_HISTORY
    lines = [_HISTORY_HEADER, ""]
    for entry in history:
        status = entry.get("status", "?")
        caption = str(entry.get("caption", "")).replace("\n", " ")
        lines.append(f"- [{status}] {caption}")
        reason = entry.get("rejectionReason")
        if reason:
            lines.append(f"  반려 사유: {reason}")
    return "\n".join(lines)


def screen_note(demo: bool) -> str:
    return _DEMO_SCREEN_NOTE if demo else _BOT_SCREEN_NOTE


def describe_interactions(interactions: Sequence[Mapping[str, Any]]) -> str:
    """검토자가 읽을 조작 설명. 예: "'필터' 클릭 → 맨 아래로 스크롤"."""
    steps: list[str] = []
    for item in interactions:
        kind = item.get("kind")
        if kind == "click":
            steps.append(f"'{item.get('name')}' 클릭")
        elif kind == "select":
            steps.append(f"'{item.get('label')}'에서 '{item.get('value')}' 선택")
        elif kind == "scroll":
            steps.append("맨 아래로 스크롤" if item.get("to") == "bottom" else "맨 위로 스크롤")
    return " → ".join(steps)
