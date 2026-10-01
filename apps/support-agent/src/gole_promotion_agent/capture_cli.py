"""Claude Code 가 부르는 캡처 명령. 화면 한 장을 찍고 PNG 와 메타를 남긴다.

에이전트는 이 명령만 Bash 로 실행할 수 있다(허용 목록). 경로 허용·데모 가드·쓰기 요청 차단은
여기서 코드로 건다 — 프롬프트가 무엇이라 하든 금지 화면은 찍히지 않는다.

    python -m gole_promotion_agent.capture_cli \
        --label 필터 --route /listings --interactions '[{"kind":"scroll","to":"bottom"}]'

환경:
    PROMOTION_AGENT_CAPTURE_SITE           찍을 사이트(러너 안 데모 스택)
    PROMOTION_AGENT_REPO                   라우트 목록을 읽을 대상 소스
    PROMOTION_AGENT_DATA_SOURCE            DEMO 면 시세 화면을 막는다
    PROMOTION_AGENT_CAPTURE_SESSION_FILE   로그인 세션 JSON(래퍼가 데모 스택에 로그인해 남긴다)
"""

from __future__ import annotations

import argparse
import json
import os
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable, Mapping, Sequence

from pydantic import ValidationError

from gole_promotion_agent import policy

DEFAULT_OUT = "out/captures"


def _load_session(path: str | None) -> Callable[[], Mapping[str, Any]] | None:
    if not path:
        return None
    session = json.loads(Path(path).read_text(encoding="utf-8"))
    return lambda: session


def _default_camera(routes: Sequence[str]) -> Any:
    from gole_promotion_agent.hands import PlaywrightCamera

    site = (os.environ.get("PROMOTION_AGENT_CAPTURE_SITE") or "").rstrip("/")
    if not site:
        raise ValueError("PROMOTION_AGENT_CAPTURE_SITE_REQUIRED")
    return PlaywrightCamera(
        site, routes, _load_session(os.environ.get("PROMOTION_AGENT_CAPTURE_SESSION_FILE"))
    )


def _default_routes(demo: bool) -> tuple[str, ...]:
    from gole_promotion_agent.hands import AppRouteCatalog

    return AppRouteCatalog(Path(os.environ.get("PROMOTION_AGENT_REPO", ".")), demo=demo).routes()


def _fail(message: str) -> int:
    print(f"capture 거부: {message}", file=sys.stderr)
    return 2


def main(
    argv: Sequence[str] | None = None,
    *,
    routes: Callable[[bool], Sequence[str]] = _default_routes,
    camera_factory: Callable[[Sequence[str]], Any] = _default_camera,
) -> int:
    parser = argparse.ArgumentParser(prog="capture_cli")
    parser.add_argument("--label", required=True, help="초안에서 이 사진을 부를 이름")
    parser.add_argument("--route", help="찍을 공개 화면 경로")
    parser.add_argument("--interactions", default="[]", help="조작 목록 JSON")
    parser.add_argument("--out", default=DEFAULT_OUT)
    parser.add_argument("--list-routes", action="store_true", help="찍을 수 있는 경로만 출력")
    arguments = parser.parse_args(argv)

    demo = os.environ.get("PROMOTION_AGENT_DATA_SOURCE", "DEMO") == "DEMO"
    allowed = tuple(routes(demo))
    if arguments.list_routes:
        print("\n".join(allowed))
        return 0

    try:
        request = policy.CaptureInput.model_validate(
            {
                "route": arguments.route or "",
                "interactions": json.loads(arguments.interactions),
                "label": arguments.label,
            }
        )
    except (ValidationError, json.JSONDecodeError) as error:
        return _fail(f"입력 형식 오류 — {error}")
    if request.route not in allowed or not policy.is_public_capture_route(request.route, demo):
        return _fail(f"허용되지 않은 화면: {request.route} (--list-routes 로 확인)")

    out = Path(arguments.out)
    metas = sorted(out.glob("*.json")) if out.is_dir() else []
    labels = {json.loads(meta.read_text(encoding="utf-8"))["label"] for meta in metas}
    if request.label in labels:
        return _fail(f"이미 사용한 라벨: {request.label}")
    if len(metas) >= policy.MAX_SCREENSHOTS:
        return _fail("스크린샷 수 상한에 도달함")

    interactions = [item.model_dump() for item in request.interactions]
    key = policy.capture_key(request.route, interactions)
    destination = out / f"{key}.png"
    if (out / f"{key}.json").exists():
        return _fail("이미 찍은 화면이다 — 그 라벨을 다시 쓴다")
    camera = camera_factory(allowed)
    try:
        camera.capture(request.route, interactions, destination)
    except ValueError as error:
        return _fail(str(error))
    finally:
        camera.close()

    meta = {
        "label": request.label,
        "file": destination.name,
        "route": request.route,
        "interactions": interactions,
        "captured_at": datetime.now(timezone.utc).isoformat(),
    }
    (out / f"{key}.json").write_text(json.dumps(meta, ensure_ascii=False), encoding="utf-8")
    print(json.dumps({"label": request.label, "file": str(destination)}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
