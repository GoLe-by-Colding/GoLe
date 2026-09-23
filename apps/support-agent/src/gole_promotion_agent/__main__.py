"""일회성 엔트리포인트. 하루 한 번 떴다 지는 컨테이너가 이것을 실행한다(스펙 D10)."""

from __future__ import annotations

import argparse
import os
import sys
from pathlib import Path

from gole_agent_runtime.privacy import reject_external_tracing
from gole_promotion_agent import policy
from gole_promotion_agent.hands import AppRouteCatalog, GitReleaseScanner
from gole_promotion_agent.runtime import PromotionHarness, retry_ledger, skipped_ledger

DEFAULT_SESSIONS = "/var/lib/gole/promotion-agent"
DEFAULT_SITE = "https://gole.co.kr"


def _required(name: str) -> str:
    value = (os.environ.get(name) or "").strip()
    if not value:
        raise ValueError(f"{name}_REQUIRED")
    return value


def build_harness(dry_run: bool, repo: Path, sessions: Path) -> PromotionHarness:
    site = (os.environ.get("PROMOTION_AGENT_SITE_URL") or DEFAULT_SITE).rstrip("/")
    routes = AppRouteCatalog(repo)
    # 건너뛴 커밋을 다시 평가하지 않는다(스펙 D11). 드라이런도 같은 원장을 본다.
    skipped = skipped_ledger(sessions)
    # 실패·중단으로 결론이 안 난 커밋은 반대로 다시 본다. 홍보 경계에 가려 사라지지 않게 한다.
    retryable = retry_ledger(sessions)

    if dry_run:
        # 외부 SDK도 백엔드 클라이언트도 만들지 않는다 — 나가는 경로가 없다(스펙 D16).
        from gole_promotion_agent.dryrun import (
            FakeCamera,
            RecordingPublisher,
            scripted_conversation_factory,
        )

        publisher = RecordingPublisher(sessions)
        available = routes.routes()
        return PromotionHarness(
            GitReleaseScanner(repo, publisher.exists, skipped, retryable),
            routes,
            FakeCamera(available),
            publisher,
            scripted_conversation_factory(available[0] if available else "/"),
            sessions,
        )

    from gole_promotion_agent.hands import (
        BackendPublisher,
        PlaywrightCamera,
        anthropic_conversation_factory,
    )

    publisher = BackendPublisher(
        (os.environ.get("PROMOTION_AGENT_API_URL") or site).rstrip("/"),
        _required("PROMOTION_AGENT_ADMIN_EMAIL"),
        _required("PROMOTION_AGENT_ADMIN_PASSWORD"),
    )
    return PromotionHarness(
        GitReleaseScanner(repo, publisher.exists, skipped, retryable),
        routes,
        # 봇 계정으로 로그인된 상태로 찍는다 — 로그인 뒤에만 보이는 기능도 홍보 대상이다(D12).
        PlaywrightCamera(site, routes.routes(), publisher.browser_session),
        publisher,
        anthropic_conversation_factory(),
        sessions,
    )


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="gole_promotion_agent")
    parser.add_argument("--dry-run", action="store_true", help="유료 호출과 제출 없이 흐름만 돈다")
    parser.add_argument("--repo", default=os.environ.get("PROMOTION_AGENT_REPO", "/repo"))
    parser.add_argument(
        "--sessions", default=os.environ.get("PROMOTION_AGENT_OUTPUT_DIR", DEFAULT_SESSIONS)
    )
    arguments = parser.parse_args(argv)

    reject_external_tracing()
    dry_run = arguments.dry_run or os.environ.get("PROMOTION_AGENT_DRY_RUN") == "true"

    harness = build_harness(dry_run, Path(arguments.repo), Path(arguments.sessions))
    result = harness.run()

    mode = "드라이런" if dry_run else "실행"
    print(f"[promotion-agent] {mode}: {result.reason}")
    for item in result.candidates:
        suffix = f" ({item.error})" if item.error else ""
        resumed = " · 재개" if item.resumed else ""
        print(f"[promotion-agent] {item.sha[:12]} {item.outcome}{resumed}{suffix}")

    if result.failed:
        print(f"[promotion-agent] 실패 {len(result.failed)}건", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
