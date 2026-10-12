"""후보 화면 촬영과 지난 캡처 재사용. 모델을 부르지 않는 결정적 단계다(D21)."""

from __future__ import annotations

import json
import re
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable, Sequence

from gole_promotion_agent import policy


@dataclass(frozen=True)
class Shot:
    route: str
    path: Path
    captured_at: str


def _slug(route: str) -> str:
    return re.sub(r"[^a-z0-9]+", "-", route.lower()).strip("-") or "home"


def capture_all(
    run_dir: Path, camera: Any | None, routes: Sequence[str], demo: bool, log: Callable[[str], None],
) -> list[Shot]:
    """run_dir/captures 에 후보 화면을 찍는다. camera 가 None 이면 지난 캡처(매니페스트)를 검증해 다시 쓴다."""
    out = run_dir / "captures"
    manifest = out / "manifest.json"
    if camera is None:
        # 지난 릴리스 때 찍어 둔 화면을 그대로 쓴다(서비스 소개). 화면은 다음 릴리스 전까지 같다.
        if not manifest.exists():
            return []
        data = json.loads(manifest.read_text(encoding="utf-8"))
        if (not isinstance(data, dict) or data.get("version") != policy.CAPTURE_MANIFEST_VERSION
                or data.get("viewport") != policy.CAPTURE_VIEWPORT or not isinstance(data.get("captures"), list)):
            return []
        reused = []
        for item in data["captures"]:
            if not isinstance(item, dict):
                return []
            filename, route, captured_at = item.get("file"), item.get("route"), item.get("captured_at")
            if (not isinstance(filename, str) or not filename or Path(filename).name != filename
                    or "/" in filename or "\\" in filename or not filename.endswith(".png")
                    or route not in routes or not policy.is_public_capture_route(route, demo)
                    or not isinstance(captured_at, str) or not captured_at):
                return []
            source = (out / filename).resolve()
            if source.parent != out.resolve():
                return []
            if source.is_file():
                reused.append(Shot(route, source, captured_at))
        return reused
    shots: list[Shot] = []
    try:
        for index, route in enumerate(routes, start=1):
            destination = out / f"{index:02d}-{_slug(route)}.png"
            try:
                camera.capture(route, destination)
            except ValueError as error:
                # 화면 하나가 안 찍혀도 나머지로 판단한다. 무엇이 빠졌는지는 로그에 남긴다.
                log(f"[promotion-agent] 캡처 실패 {route}: {error}")
                continue
            shots.append(Shot(route, destination, datetime.now(timezone.utc).isoformat()))
    finally:
        camera.close()
    # 다음 서비스 소개 실행이 다시 찍지 않고 쓰도록 무엇을 언제 찍었는지 남긴다.
    manifest.parent.mkdir(parents=True, exist_ok=True)
    manifest.write_text(
        json.dumps(
            {"version": policy.CAPTURE_MANIFEST_VERSION, "viewport": policy.CAPTURE_VIEWPORT,
             "captures": [{"route": s.route, "file": s.path.name, "captured_at": s.captured_at} for s in shots]},
            ensure_ascii=False,
        ),
        encoding="utf-8",
    )
    return shots
