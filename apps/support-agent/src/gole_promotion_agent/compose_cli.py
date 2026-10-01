"""Claude Code 가 부르는 합성 명령. 찍어 둔 캡처 한 장에 브랜드 카드를 씌운다.

에이전트는 HTML 을 쓰지 못하고 **캡처 라벨과 헤드라인 텍스트만** 넘긴다. 레이아웃·색은 템플릿이
고정하므로 카드가 브랜드에서 벗어나지 않고, 텍스트는 이스케이프돼 마크업이 되지 못한다.

    python -m gole_promotion_agent.compose_cli --capture 목록 --headline "원하는 부품만 골라 보기"
"""

from __future__ import annotations

import argparse
import base64
import html
import json
import re
import sys
from pathlib import Path
from typing import Callable, Sequence

from gole_promotion_agent import policy

TEMPLATE = Path(__file__).parent / "templates" / "card.html"
CARD_SIZE = {"width": 1080, "height": 1350}
MAX_HEADLINE = 40
# 카드의 숫자는 캡션보다 더 사실 주장처럼 읽힌다 — 데모 데이터 여부와 무관하게 막는다.
DIGIT = re.compile(r"[0-9０-９]")


def render_card(headline: str, image: Path) -> str:
    data = base64.b64encode(Path(image).read_bytes()).decode("ascii")
    return (
        TEMPLATE.read_text(encoding="utf-8")
        .replace("{{IMAGE}}", f"data:image/png;base64,{data}")
        .replace("{{HEADLINE}}", html.escape(headline))
    )


def _playwright_render(markup: str, destination: Path) -> None:
    from playwright.sync_api import sync_playwright

    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(args=["--disable-dev-shm-usage", "--no-sandbox"])
        try:
            page = browser.new_page(viewport=CARD_SIZE)
            # 외부로 나가는 요청이 없어야 한다 — 이미지는 data URI, 폰트는 시스템 폰트다.
            page.route("**/*", lambda route: route.abort())
            page.set_content(markup, wait_until="load")
            page.screenshot(path=str(destination), animations="disabled")
        finally:
            browser.close()


def _fail(message: str) -> int:
    print(f"compose 거부: {message}", file=sys.stderr)
    return 2


def main(
    argv: Sequence[str] | None = None,
    *,
    renderer: Callable[[str, Path], None] = _playwright_render,
) -> int:
    parser = argparse.ArgumentParser(prog="compose_cli")
    parser.add_argument("--capture", required=True, help="capture_cli 로 찍은 사진의 라벨")
    parser.add_argument("--headline", required=True)
    parser.add_argument("--captures", default="out/captures")
    parser.add_argument("--out", default="out/cards")
    arguments = parser.parse_args(argv)

    headline = " ".join(arguments.headline.split())
    if not headline or len(headline) > MAX_HEADLINE:
        return _fail(f"헤드라인은 1~{MAX_HEADLINE}자")
    if DIGIT.search(headline):
        return _fail("헤드라인에 숫자를 쓰지 않는다")

    # 라벨로만 찾는다 — 임의 파일 경로를 받으면 카드에 무엇이든 실을 수 있다.
    captures = Path(arguments.captures)
    metas = [
        json.loads(meta.read_text(encoding="utf-8"))
        for meta in (sorted(captures.glob("*.json")) if captures.is_dir() else [])
    ]
    match = next((meta for meta in metas if meta["label"] == arguments.capture), None)
    if match is None:
        return _fail(f"찍은 적 없는 캡처: {arguments.capture}")

    out = Path(arguments.out)
    out.mkdir(parents=True, exist_ok=True)
    cards = sorted(out.glob("*.json"))
    if len(cards) >= policy.MAX_SCREENSHOTS:
        return _fail("카드 수 상한에 도달함")
    index = len(cards) + 1
    destination = out / f"card-{index:02d}.png"
    renderer(render_card(headline, captures / match["file"]), destination)

    meta = {"file": destination.name, "capture": match["label"], "headline": headline}
    (out / f"card-{index:02d}.json").write_text(json.dumps(meta, ensure_ascii=False), encoding="utf-8")
    print(json.dumps({"card": str(destination), "capture": match["label"]}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
