"""루프에 주입하는 도구 묶음. 가드(허용 라우트·상한·SHA 일치)는 프롬프트가 아니라 여기서 강제한다."""

from __future__ import annotations

from pathlib import Path
from typing import Any, Mapping

from pydantic import BaseModel, ValidationError

from gole_promotion_agent import policy
from gole_promotion_agent.ports import (
    Camera,
    ReleaseScanner,
    RouteCatalog,
    ToolCall,
    ToolOutcome,
)


def _error(text: str) -> ToolOutcome:
    return ToolOutcome(text, is_error=True)


def validate(
    models: Mapping[str, type[BaseModel]], call: ToolCall
) -> tuple[BaseModel | None, ToolOutcome | None]:
    model = models.get(call.name)
    if model is None:
        return None, _error(f"알 수 없는 도구: {call.name}")
    try:
        return model.model_validate(dict(call.arguments)), None
    except ValidationError as error:
        return None, _error(f"INVALID_TOOL_INPUT: {error.error_count()}건")


class DraftToolset:
    """릴리스 하나를 조사해 초안을 내는 도구들(스펙 D12)."""

    def __init__(
        self,
        scanner: ReleaseScanner,
        routes: RouteCatalog,
        camera: Camera,
        session_dir: Path,
    ):
        self._scanner = scanner
        self._allowed_routes = frozenset(routes.routes())
        self._camera = camera
        self._screens = Path(session_dir) / "screens"

    def schemas(self) -> tuple[dict[str, Any], ...]:
        return policy.tool_schemas()

    def run(self, call: ToolCall, state: Mapping[str, Any]) -> ToolOutcome:
        payload, invalid = validate(policy.TOOL_MODELS, call)
        if invalid is not None:
            return invalid
        handler = getattr(self, f"_{call.name}")
        return handler(payload, state)

    def _list_releases(self, _payload: Any, _state: Mapping[str, Any]) -> ToolOutcome:
        listed = "\n".join(f"{c.sha}\t{c.subject}" for c in self._scanner.candidates())
        return ToolOutcome(listed or "후보 없음")

    def _read_release_diff(self, payload: Any, state: Mapping[str, Any]) -> ToolOutcome:
        if payload.sha != state["sha"]:
            return _error(f"현재 후보가 아닌 릴리스는 읽을 수 없음: {payload.sha}")
        return ToolOutcome(self._scanner.diff(payload.sha))

    def _list_routes(self, _payload: Any, _state: Mapping[str, Any]) -> ToolOutcome:
        return ToolOutcome("\n".join(sorted(self._allowed_routes)))

    def _capture(self, payload: Any, state: Mapping[str, Any]) -> ToolOutcome:
        captures = [dict(item) for item in state.get("captures", [])]
        if payload.route not in self._allowed_routes:
            return _error(f"허용되지 않은 공개 라우트: {payload.route}")
        if any(item["label"] == payload.label for item in captures):
            return _error(f"이미 사용한 라벨: {payload.label}")
        if len(captures) >= policy.MAX_SCREENSHOTS:
            return _error("스크린샷 수 상한에 도달함")
        interactions = [item.model_dump() for item in payload.interactions]
        key = policy.capture_key(payload.route, interactions)
        existing = next((item for item in captures if item["key"] == key), None)
        if existing is not None:
            # 같은 화면은 다시 찍지 않는다. 재개했을 때도 여기서 걸린다.
            captures.append({**existing, "label": payload.label})
            return ToolOutcome(
                f"이미 찍은 화면을 재사용함: {payload.label}",
                existing["path"],
                {"captures": captures},
            )
        destination = self._screens / f"{key}.png"
        self._camera.capture(payload.route, interactions, destination)
        captures.append(
            {
                "key": key,
                "label": payload.label,
                "route": payload.route,
                "interactions": interactions,
                "path": str(destination),
            }
        )
        return ToolOutcome(
            f"스크린샷 {payload.label}: {destination}", str(destination), {"captures": captures}
        )

    def _submit_promotion_draft(self, payload: Any, state: Mapping[str, Any]) -> ToolOutcome:
        if payload.sha != state["sha"]:
            return _error(f"현재 후보와 다른 SHA는 제출할 수 없음: {payload.sha}")
        captures = state.get("captures", [])
        missing = [
            label
            for label in payload.screenshot_labels
            if not any(item["label"] == label for item in captures)
        ]
        if missing:
            return _error(f"세션에 없는 스크린샷 라벨: {', '.join(missing)}")
        draft = {"caption": payload.caption, "labels": list(payload.screenshot_labels)}
        return ToolOutcome("초안 제출을 시작함", update={"draft": draft})
