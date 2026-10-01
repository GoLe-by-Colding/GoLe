"""초안 실행(drafter)이 쓰는 계약. SDK·환경변수·전송 계층에 의존하지 않는다."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Any, Mapping, Protocol, Sequence


@dataclass(frozen=True)
class Candidate:
    """홍보 후보가 된 릴리스 하나. sha는 소문자 40자 16진수다."""

    sha: str
    subject: str


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
    """백엔드 연동. 업로드 → 생성 → 검토 요청 순서로 제출한다."""

    def exists(self, sha: str) -> bool: ...

    def pending_count(self) -> int: ...

    def history(self, limit: int) -> tuple[Mapping[str, Any], ...]: ...

    def upload(self, paths: Sequence[Path]) -> tuple[str, ...]: ...

    def create(
        self, sha: str | None, caption: str, media_keys: Sequence[str], details: Mapping[str, Any]
    ) -> str:
        """details: captures(사진별 설명표, media_keys 와 같은 순서)·rationale·releaseTitle."""
        ...

    def finalize(self, post_id: str) -> None: ...
