#!/usr/bin/env python3
"""Send a fixed backup failure notice without exposing the webhook value."""

from __future__ import annotations

import json
import pathlib
import re
import urllib.error
import urllib.parse
import urllib.request


DISCORD_ENV_PATH = pathlib.Path("/etc/gole/discord.env")


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


def main() -> int:
    try:
        webhook = operations_webhook_url()
    except (OSError, ValueError):
        return 1
    request = urllib.request.Request(
        webhook,
        data=json.dumps(
            {
                "content": "❌ GoLe 운영 논리 백업 실패 · 다음 자동 스냅샷 복원 보장 중단",
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
    raise SystemExit(main())
