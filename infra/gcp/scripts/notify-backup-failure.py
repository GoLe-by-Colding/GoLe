#!/usr/bin/env python3
"""Send a fixed unit failure notice without exposing the webhook value."""

from __future__ import annotations

import json
import pathlib
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from typing import Sequence


DISCORD_ENV_PATH = pathlib.Path("/etc/gole/discord.env")

# 유닛마다 알림 스크립트를 새로 만들지 않는다. OnFailure= 유닛이 어떤 실패인지만 인자로
# 넘기고, 실제 문구는 여기 고정한 목록에서 고른다 — 루트로 도는 프로세스이므로 argv 의
# 자유 문자열을 그대로 Discord 로 흘리지 않는다. 인자가 없으면 논리 백업(원래 용도)이다.
NOTICES = {
    "data-backup": "❌ GoLe 운영 논리 백업 실패 · 다음 자동 스냅샷 복원 보장 중단",
    "promotion-agent": "❌ GoLe 홍보 초안 에이전트 실패 · 오늘자 초안 생성 중단",
}
DEFAULT_NOTICE = "data-backup"


def notice_content(argv: Sequence[str]) -> str:
    if not argv:
        return NOTICES[DEFAULT_NOTICE]
    if len(argv) != 1 or argv[0] not in NOTICES:
        raise ValueError("unknown failure notice")
    return NOTICES[argv[0]]


def operations_webhook_url() -> str:
    if DISCORD_ENV_PATH.is_symlink() or not DISCORD_ENV_PATH.is_file():
        raise ValueError("operations notification configuration is missing")
    metadata = DISCORD_ENV_PATH.stat()
    if metadata.st_uid != 0 or metadata.st_gid != 0 or metadata.st_mode & 0o077:
        raise ValueError("operations notification configuration is not root-only")
    matches = [
        line.split("=", 1)[1]
        for line in DISCORD_ENV_PATH.read_text(encoding="utf-8").splitlines()
        if line.startswith("DISCORD_OPERATIONS_WEBHOOK_URL=")
    ]
    if len(matches) != 1:
        raise ValueError("operations webhook configuration is invalid")
    webhook = matches[0]
    parsed = urllib.parse.urlsplit(webhook)
    if (
        parsed.scheme != "https"
        or parsed.hostname not in {"discord.com", "discordapp.com"}
        or parsed.username is not None
        or parsed.password is not None
        or parsed.port not in (None, 443)
        or not re.fullmatch(
            r"/api/webhooks/[0-9]{15,24}/[A-Za-z0-9._-]{40,200}", parsed.path
        )
        or parsed.query
        or parsed.fragment
    ):
        raise ValueError("operations webhook configuration is invalid")
    return webhook


def main(argv: Sequence[str] | None = None) -> int:
    try:
        content = notice_content(() if argv is None else tuple(argv))
        webhook = operations_webhook_url()
    except (OSError, ValueError):
        return 1
    request = urllib.request.Request(
        webhook,
        data=json.dumps(
            {
                "content": content,
                "allowed_mentions": {"parse": []},
            },
            ensure_ascii=False,
        ).encode("utf-8"),
        method="POST",
        headers={"Content-Type": "application/json", "User-Agent": "GoLe-Backup/1.0"},
    )
    try:
        with urllib.request.urlopen(request, timeout=10) as response:
            return 0 if 200 <= response.status < 300 else 1
    except (OSError, urllib.error.URLError):
        return 1


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
