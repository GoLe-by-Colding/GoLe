"""LLM 게이트웨이 호출의 공용 모양. 러너는 SSH 로, 서버는 같은 컨테이너 안에서 부른다."""

from __future__ import annotations

import base64
from pathlib import Path
from typing import Any, Mapping, Protocol


class GatewayError(RuntimeError):
    def __init__(self, message: str, usage: Mapping[str, Any] | None = None):
        super().__init__(message)
        # 실패한 호출도 토큰을 쓴다. ssh 자체가 실패했으면 None.
        self.usage = usage


class Gateway(Protocol):
    def call(self, request: Mapping[str, Any]) -> dict[str, Any]: ...


def encode_png(path: Path) -> dict[str, str]:
    return {"data": base64.b64encode(Path(path).read_bytes()).decode("ascii")}


def decode_png(image: Mapping[str, Any]) -> bytes:
    data = base64.b64decode(image["data"], validate=True)
    if not data.startswith(b"\x89PNG"):
        raise GatewayError("게이트웨이가 PNG 가 아닌 이미지를 돌려줬다")
    return data
