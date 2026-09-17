"""에이전트와 실행 환경 사이의 최소 계약."""

import threading
from typing import Any, Protocol, TypedDict

from gole_agent_worker.contracts import Submission
from gole_agent_worker.hands.contracts import Provider


class State(TypedDict, total=False):
    # 기존 체크포인트의 채널 이름과 값 형식을 유지한다.
    submission: dict[str, Any]
    prepared: bool
    result: dict[str, Any]


class ExecutionContext(Protocol):
    job_id: str
    cancelled: threading.Event
    timeout: float

    def check_active(self) -> None: ...


class Agent(Protocol):
    def validate(self, submission: Submission, *, openai_enabled: bool) -> None: ...

    def execute(self, submission: dict[str, Any], provider: Provider,
                context: ExecutionContext) -> dict[str, Any]: ...
