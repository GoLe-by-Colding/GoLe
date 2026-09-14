from __future__ import annotations

import json
import re
from dataclasses import dataclass
from typing import Any

TERMINAL = frozenset({"SUCCEEDED", "FAILED", "CANCELLED"})
IDENTIFIER = re.compile(r"[A-Za-z0-9_.:@/-]{1,128}\Z")


class Conflict(Exception):
    pass


class Purged(Exception):
    pass


class NotFound(Exception):
    pass


class LeaseLost(Exception):
    pass


class TransientFailure(Exception):
    pass


class PermanentFailure(Exception):
    pass


@dataclass(frozen=True)
class Submission:
    owner: str
    key: str
    authorization_ref: str
    kind: str
    payload: dict[str, Any]
    provider: str
    allow_external: bool = False

    def canonical(self) -> str:
        return json.dumps(
            {"authorization_ref": self.authorization_ref, "kind": self.kind,
             "payload": self.payload, "provider": self.provider,
             "allow_external": self.allow_external},
            sort_keys=True, ensure_ascii=False, separators=(",", ":"), allow_nan=False,
        )


def identifier(value: str) -> str:
    if not IDENTIFIER.fullmatch(value):
        raise ValueError("INVALID_IDENTIFIER")
    return value


