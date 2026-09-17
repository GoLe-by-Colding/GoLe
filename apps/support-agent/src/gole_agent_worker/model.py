"""기존 모델·검증 import 호환 진입점."""

from gole_agent_worker.contracts import (
    Conflict, IDENTIFIER, LeaseLost, NotFound, PermanentFailure, Purged,
    Submission, TERMINAL, TransientFailure, identifier,
)
from gole_agent_worker.runtime.validation import validate

__all__ = ["Conflict", "IDENTIFIER", "LeaseLost", "NotFound", "PermanentFailure", "Purged",
           "Submission", "TERMINAL", "TransientFailure", "identifier", "validate"]
