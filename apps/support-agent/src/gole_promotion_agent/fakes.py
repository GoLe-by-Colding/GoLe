"""브라우저 없이 캡처·합성 경로를 시험하기 위한 대체 구현. 외부 SDK를 import하지 않는다."""

from __future__ import annotations

import base64
from pathlib import Path
from typing import Sequence

# 1x1 PNG. 실제 촬영 대신 형식만 맞춘 파일을 남긴다.
PIXEL = base64.b64decode(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg=="
)


class FakeCamera:
    """브라우저 없이 형식만 맞춘 PNG를 남긴다."""

    def __init__(self, allowed_routes: Sequence[str]):
        self._allowed = frozenset(allowed_routes)
        self.calls: list[str] = []

    def capture(self, route: str, destination: Path) -> None:
        if route not in self._allowed:
            raise ValueError("ROUTE_NOT_ALLOWED")
        self.calls.append(route)
        destination = Path(destination)
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(PIXEL)

    def close(self) -> None:
        return None
