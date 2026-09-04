#!/usr/bin/env python3
"""Send a fixed backup failure notice without exposing the webhook value."""

from __future__ import annotations

import json
import pathlib
import urllib.error
import urllib.request


ENV_PATH = pathlib.Path("/etc/gole/gole.env")


def main() -> int:
    webhook = ""
    try:
        for line in ENV_PATH.read_text(encoding="utf-8").splitlines():
            if line.startswith("DISCORD_OPERATIONS_WEBHOOK_URL="):
                webhook = line.partition("=")[2].strip()
                break
    except OSError:
        return 1
    if not webhook.startswith(
        ("https://discord.com/api/webhooks/", "https://discordapp.com/api/webhooks/")
    ):
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
