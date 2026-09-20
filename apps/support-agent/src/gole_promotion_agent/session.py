"""후보 하나의 단계만 남긴다. 캡션·이미지·SHA 같은 내용물은 담지 않는다."""

from enum import StrEnum


class Stage(StrEnum):
    ACCEPTED = "ACCEPTED"
    EXPLORING = "EXPLORING"
    DRAFTING = "DRAFTING"
    SUCCEEDED = "SUCCEEDED"
    SKIPPED = "SKIPPED"
    FAILED = "FAILED"
    CANCELLED = "CANCELLED"


_TERMINAL = frozenset({Stage.SUCCEEDED, Stage.SKIPPED, Stage.FAILED, Stage.CANCELLED})

# 사진 필터와 달리 탐색 단계가 순환하므로 다음 단계를 집합으로 둔다.
_NEXT: dict[Stage, frozenset[Stage]] = {
    Stage.ACCEPTED: frozenset({Stage.EXPLORING, Stage.SKIPPED}),
    Stage.EXPLORING: frozenset({Stage.EXPLORING, Stage.DRAFTING, Stage.SKIPPED}),
    Stage.DRAFTING: frozenset({Stage.DRAFTING, Stage.SUCCEEDED}),
}


class Audit:
    __slots__ = ("status", "events")

    def __init__(self, status: Stage, events: tuple[Stage, ...]):
        self.status = status
        self.events = events

    def __eq__(self, other: object) -> bool:
        return (
            isinstance(other, Audit)
            and self.status == other.status
            and self.events == other.events
        )

    def __repr__(self) -> str:
        return f"Audit(status={self.status!r}, events={self.events!r})"


class EphemeralSession:
    def __init__(self, start: Stage = Stage.ACCEPTED) -> None:
        # 재개한 세션은 흐름 중간에서 들어오므로 시작 단계를 복원해야 한다. 그렇지 않으면
        # ACCEPTED → DRAFTING 처럼 건너뛰는 전이가 되어 거부된다(스펙 D17).
        if start in _TERMINAL:
            raise ValueError("INVALID_SESSION_START")
        self._events: list[Stage] = [start]

    def advance(self, stage: str) -> None:
        target = Stage(stage)
        current = self._events[-1]
        if current in _TERMINAL:
            raise ValueError("SESSION_TERMINAL")
        # 실패·취소는 어느 단계에서든 받는다.
        if target not in _NEXT[current] and target not in {Stage.FAILED, Stage.CANCELLED}:
            raise ValueError("INVALID_SESSION_TRANSITION")
        self._events.append(target)

    def snapshot(self) -> Audit:
        return Audit(self._events[-1], tuple(self._events))
