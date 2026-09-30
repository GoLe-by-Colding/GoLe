"""Brain이 사용하는 실행 계약. SDK·환경변수·전송 계층에 의존하지 않는다."""

from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Mapping, Protocol, Sequence


@dataclass(frozen=True)
class Candidate:
    """홍보 후보가 된 릴리스 하나. sha는 소문자 40자 16진수다."""

    sha: str
    subject: str


@dataclass(frozen=True)
class ToolCall:
    id: str
    name: str
    arguments: Mapping[str, Any]


@dataclass(frozen=True)
class ToolResult:
    """모델에 되돌려 줄 도구 결과. 이미지는 바이트가 아니라 경로로만 오간다."""

    call_id: str
    text: str
    image_path: str | None = None
    is_error: bool = False


@dataclass(frozen=True)
class Turn:
    text: str
    calls: tuple[ToolCall, ...]
    stop_reason: str


class Conversation(Protocol):
    """전사의 순수 함수다 — 대화 이력을 구현체가 소유하지 않는다.

    체크포인트에서 되살아난 전사만으로 같은 맥락이 복원돼야 하므로(스펙 D17),
    구현체는 호출마다 transcript에서 요청을 다시 만든다.
    """

    def advance(self, transcript: Sequence[Mapping[str, Any]]) -> Turn: ...


class ConversationFactory(Protocol):
    def __call__(self, *, system: str, tools: Sequence[Mapping[str, Any]]) -> Conversation: ...


@dataclass(frozen=True)
class ToolOutcome:
    """도구 하나를 실행한 결과. update 는 그래프 상태에 합칠 값이다(예: captures, draft)."""

    text: str
    image_path: str | None = None
    update: Mapping[str, Any] = field(default_factory=dict)
    is_error: bool = False


class Toolset(Protocol):
    """루프에 주입하는 도구 묶음 — 스키마와 실행을 함께 가진다.

    루프(brain)는 어떤 도구가 있는지 모른다. 실행 종류(초안·발행)마다 다른 묶음을 끼운다.
    """

    def schemas(self) -> tuple[dict[str, Any], ...]: ...

    def run(self, call: ToolCall, state: Mapping[str, Any]) -> ToolOutcome: ...


class ReleaseScanner(Protocol):
    """main 이력에서 아직 홍보하지 않은 릴리스를 고른다(스펙 D11)."""

    def candidates(self) -> tuple[Candidate, ...]: ...

    def diff(self, sha: str) -> str: ...


class RouteCatalog(Protocol):
    def routes(self) -> tuple[str, ...]: ...


class Camera(Protocol):
    """선언적 캡처(스펙 D12). 호출 하나가 이동·상호작용·촬영을 모두 끝낸다."""

    def capture(
        self,
        route: str,
        interactions: Sequence[Mapping[str, Any]],
        destination: Path,
    ) -> None: ...

    def close(self) -> None: ...


class DraftPublisher(Protocol):
    """백엔드 연동. 제출은 3단으로 나뉘어 각각 멱등하게 재개된다(스펙 D17)."""

    def exists(self, sha: str) -> bool: ...

    def browser_session(self) -> Mapping[str, Any]:
        """캡처 컨텍스트를 로그인시킬 세션. 제출에 쓰는 것과 같은 봇 계정이다(스펙 D12).

        로그인은 이 구현이 소유한다 — 카메라는 토큰의 출처를 모른다.
        """
        ...

    def pending_count(self) -> int: ...

    def history(self, limit: int) -> tuple[Mapping[str, Any], ...]: ...

    def upload(self, paths: Sequence[Path]) -> tuple[str, ...]: ...

    def create(self, sha: str, caption: str, media_keys: Sequence[str]) -> str: ...

    def finalize(self, post_id: str) -> None: ...


class PostBoard(Protocol):
    """발행 실행이 보는 게시물 목록과 발행 동작(스펙 D3·D5 — 실제 게시는 SocialPublishPort 뒤)."""

    def posts(self, status: str, limit: int) -> tuple[Mapping[str, Any], ...]: ...

    def publish(self, post_id: str) -> Mapping[str, Any]: ...
