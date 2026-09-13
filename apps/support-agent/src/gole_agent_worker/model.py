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


def validate(submission: Submission, *, openai_enabled: bool) -> None:
    for value in (submission.owner, submission.key, submission.authorization_ref):
        identifier(value)
    payload = submission.payload
    if not isinstance(payload, dict) or len(submission.canonical().encode()) > 12_000:
        raise ValueError("INVALID_PAYLOAD")
    if submission.kind == "support.rules":
        if submission.provider != "rules" or submission.allow_external:
            raise ValueError("SUPPORT_EXTERNAL_FORBIDDEN")
        allowed = {"ticket_id", "declared_category", "title", "message", "locale"}
        if set(payload) - allowed:
            raise ValueError("INVALID_PAYLOAD")
        for field, limit in (("ticket_id", 128), ("title", 100), ("message", 2000)):
            value = payload.get(field)
            if not isinstance(value, str) or not value.strip() or len(value) > limit:
                raise ValueError("INVALID_PAYLOAD")
        for field in ("declared_category", "locale"):
            if field in payload and (not isinstance(payload[field], str) or len(payload[field]) > 128):
                raise ValueError("INVALID_PAYLOAD")
    elif submission.kind == "synthetic.demo":
        # 자유 입력을 외부로 보내는 우회로가 되지 않도록 고정 비개인정보 입력만 받는다.
        if payload != {"topic": "brick-colors"}:
            raise ValueError("INVALID_SYNTHETIC_PAYLOAD")
        if submission.provider not in {"fake", "openai"}:
            raise ValueError("INVALID_PROVIDER")
        if submission.provider == "openai" and not (openai_enabled and submission.allow_external):
            raise ValueError("EXTERNAL_DISABLED")
    else:
        raise ValueError("UNSUPPORTED_KIND")
