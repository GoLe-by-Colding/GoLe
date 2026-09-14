"""Brain이 사용하는 외부 생성 기능의 계약. SDK와 환경 설정을 알지 못한다."""

import threading
from dataclasses import dataclass
from typing import Protocol


@dataclass(frozen=True)
class ProviderInput:
    topic: str
    operation_key: str
    authorization_ref: str


@dataclass(frozen=True)
class ProviderOutput:
    text: str
    external_model_used: bool


class Provider(Protocol):
    # Java 승인 참조를 보존하며 여기서 quota를 차감하지 않는다.
    def generate(self, request: ProviderInput, *, timeout: float,
                 cancelled: threading.Event) -> ProviderOutput: ...
