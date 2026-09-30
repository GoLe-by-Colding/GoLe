"""홍보 경로(기능 홍보·서비스 홍보)의 차이를 한곳에 둔다. 트리거가 경로를 고르고 하네스에 주입한다.

경로마다 다른 것은 네 가지뿐이다 — 첫 지시, 도구 묶음, 시스템 프롬프트, 출처 릴리스 유무.
루프(brain)·촬영(hands)·제출 체인은 같다.
"""

from __future__ import annotations

import hashlib
from dataclasses import dataclass
from datetime import date
from pathlib import Path
from typing import Any, Mapping, Sequence

from gole_promotion_agent import policy
from gole_promotion_agent.ports import Camera, Candidate, ReleaseScanner, RouteCatalog, Toolset
from gole_promotion_agent.tools import DraftToolset, ServiceToolset


@dataclass(frozen=True)
class PromotionKind:
    category: str  # 백엔드 PromotionCategory
    service: bool

    def opening(self, candidate: Candidate) -> str:
        if self.service:
            return "오늘 올릴 GoLe 서비스 소개 글을 하나 써. 최근 글과 겹치지 않는 주제를 골라."
        return (
            f"방금 배포된 릴리스 {candidate.sha} 를 조사해 홍보 초안을 만들지 "
            f"판단해. 릴리스 제목은 탐색 단서로만 써: {candidate.subject}"
        )

    def toolset(
        self, scanner: ReleaseScanner, routes: RouteCatalog, camera: Camera, session_dir: Path
    ) -> Toolset:
        kind = ServiceToolset if self.service else DraftToolset
        return kind(scanner, routes, camera, session_dir)

    def system(self, history: Sequence[Mapping[str, Any]], demo: bool) -> str:
        return policy.build_system_prompt(history, demo, service=self.service)

    def source_sha(self, candidate_sha: str) -> str | None:
        """백엔드에 출처로 남길 릴리스. 서비스 홍보는 릴리스에서 나오지 않는다."""
        return None if self.service else candidate_sha


FEATURE = PromotionKind("FEATURE", service=False)
SERVICE = PromotionKind("SERVICE", service=True)


class ServiceScanner:
    """서비스 홍보의 후보는 "오늘" 하나다. 세션 식별자는 날짜에서 만든 40자 해시다.

    같은 날 다시 돌리면 같은 세션이 되어 체크포인트로 이어진다. 릴리스가 아니므로 diff 는 없다.
    """

    def __init__(self, today: date | None = None):
        self._today = today or date.today()

    def candidates(self) -> tuple[Candidate, ...]:
        day = self._today.isoformat()
        session_id = hashlib.sha1(f"service-{day}".encode()).hexdigest()
        return (Candidate(session_id, f"서비스 홍보 {day}"),)

    def diff(self, sha: str) -> str:
        raise ValueError("NO_RELEASE_FOR_SERVICE_PROMOTION")
