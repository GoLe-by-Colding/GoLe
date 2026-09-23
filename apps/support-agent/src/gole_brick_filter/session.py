"""요청 수명 동안의 최소 상태. 이미지·프롬프트·토큰·예외 원문은 저장하지 않는다."""
from dataclasses import dataclass
from enum import StrEnum


class Stage(StrEnum):
    ACCEPTED = "ACCEPTED"
    VALIDATING = "VALIDATING"
    GENERATING = "GENERATING"
    REVIEWING = "REVIEWING"
    SUCCEEDED = "SUCCEEDED"
    FAILED = "FAILED"
    CANCELLED = "CANCELLED"


@dataclass(frozen=True)
class Audit:
    status: Stage
    events: tuple[Stage, ...]


class EphemeralSession:
    _NEXT = {
        Stage.ACCEPTED: Stage.VALIDATING,
        Stage.VALIDATING: Stage.GENERATING,
        Stage.GENERATING: Stage.REVIEWING,
        Stage.REVIEWING: Stage.SUCCEEDED,
    }

    def __init__(self):
        self._events = [Stage.ACCEPTED]

    def advance(self, stage: str):
        stage = Stage(stage)
        current = self._events[-1]
        if current not in self._NEXT:
            raise ValueError("SESSION_TERMINAL")
        if stage not in {self._NEXT[current], Stage.FAILED, Stage.CANCELLED}:
            raise ValueError("INVALID_SESSION_TRANSITION")
        self._events.append(stage)

    def snapshot(self) -> Audit:
        return Audit(self._events[-1], tuple(self._events))
