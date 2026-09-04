#!/usr/bin/env python3
"""Verify a candidate has trusted public-main provenance and successful push CI."""

from __future__ import annotations

import json
import re
import sys
import urllib.error
import urllib.parse
import urllib.request


REPOSITORY = "GoLe-by-Colding/GoLe"


def get_json(url: str) -> dict:
    request = urllib.request.Request(
        url,
        headers={
            "Accept": "application/vnd.github+json",
            "User-Agent": "GoLe-Root-Release-Verifier/1.0",
            "X-GitHub-Api-Version": "2022-11-28",
        },
    )
    with urllib.request.urlopen(request, timeout=15) as response:
        payload = json.load(response)
    if not isinstance(payload, dict):
        raise ValueError("GitHub returned an invalid response")
    return payload


def verify(sha: str, *, historical_main: bool = False) -> None:
    if not re.fullmatch(r"[0-9a-f]{40}", sha):
        raise ValueError("candidate SHA is invalid")
    encoded_repo = urllib.parse.quote(REPOSITORY, safe="/")
    reference = get_json(
        f"https://api.github.com/repos/{encoded_repo}/git/ref/heads/main"
    )
    current_main = reference.get("object", {}).get("sha")
    if not isinstance(current_main, str) or not re.fullmatch(r"[0-9a-f]{40}", current_main):
        raise ValueError("current main SHA is invalid")
    if historical_main:
        comparison = get_json(
            f"https://api.github.com/repos/{encoded_repo}/compare/{sha}...{current_main}"
        )
        if comparison.get("merge_base_commit", {}).get("sha") != sha:
            raise ValueError("candidate is not in current main history")
    elif current_main != sha:
        raise ValueError("candidate is not the current main SHA")
    runs = get_json(
        f"https://api.github.com/repos/{encoded_repo}/actions/workflows/ci.yml/runs"
        f"?branch=main&event=push&status=completed&head_sha={sha}&per_page=20"
    ).get("workflow_runs", [])
    if not isinstance(runs, list) or not any(
        isinstance(run, dict)
        and run.get("head_sha") == sha
        and run.get("conclusion") == "success"
        for run in runs
    ):
        raise ValueError("candidate has no successful main push CI")


def main() -> int:
    historical_main = False
    arguments = sys.argv[1:]
    if arguments[:1] == ["--historical-main"]:
        historical_main = True
        arguments = arguments[1:]
    if len(arguments) != 1:
        print(
            "usage: verify-github-release.py [--historical-main] 40_HEX_SHA",
            file=sys.stderr,
        )
        return 2
    try:
        verify(arguments[0], historical_main=historical_main)
    except (OSError, ValueError, urllib.error.URLError):
        print("release provenance verification failed", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
