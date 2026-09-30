"""루프에 주입하는 도구 묶음. 가드(허용 라우트·상한·SHA 일치)는 프롬프트가 아니라 여기서 강제한다."""

from __future__ import annotations

from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any, Callable, Mapping

from pydantic import BaseModel, Field, ValidationError

from gole_promotion_agent import policy
from gole_promotion_agent.ports import (
    Camera,
    PostBoard,
    ReleaseScanner,
    RouteCatalog,
    ToolCall,
    ToolOutcome,
)


def _error(text: str) -> ToolOutcome:
    return ToolOutcome(text, is_error=True)


def schemas_for(
    models: Mapping[str, type[BaseModel]], descriptions: Mapping[str, str]
) -> tuple[dict[str, Any], ...]:
    return tuple(
        {
            "name": name,
            "description": descriptions[name],
            "input_schema": model.model_json_schema(),
        }
        for name, model in models.items()
    )


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


class _NoInput(BaseModel):
    pass


class _PublishNowInput(BaseModel):
    post_id: str = Field(min_length=1, max_length=100)
    reason: str = Field(min_length=1, max_length=300)


_PUBLISH_MODELS: Mapping[str, type[BaseModel]] = {
    "list_approved_posts": _NoInput,
    "list_recent_published": _NoInput,
    "publish_now": _PublishNowInput,
}

_PUBLISH_DESCRIPTIONS: Mapping[str, str] = {
    "list_approved_posts": "사람이 승인해 발행을 기다리는 글을 오래된 순으로 나열한다.",
    "list_recent_published": "최근에 발행한 글을 최신순으로 나열한다.",
    "publish_now": (
        "승인된 글 하나를 지금 발행한다. 되돌릴 수 없다. "
        "고른 이유를 reason 에 한 문장으로 적는다."
    ),
}


def _preview(caption: str, limit: int = 80) -> str:
    flat = " ".join(str(caption).split())
    return flat if len(flat) <= limit else flat[: limit - 1] + "…"


def _parse_instant(value: Any) -> datetime | None:
    if not value:
        return None
    return datetime.fromisoformat(str(value).replace("Z", "+00:00"))


class PublishToolset:
    """승인된 글을 고르고 발행하는 도구들. 되돌릴 수 없는 행동이라 가드를 코드가 쥔다.

    - APPROVED 가 아닌 글·이미 발행한 글은 거부한다(백엔드도 409 로 한 번 더 막는다).
    - 실행당 발행 수와 직전 발행과의 최소 간격을 넘으면 거부한다.
    """

    def __init__(self, board: PostBoard, clock: Callable[[], datetime] | None = None):
        self._board = board
        self._clock = clock or (lambda: datetime.now(timezone.utc))
        self.published: list[dict[str, str]] = []

    def schemas(self) -> tuple[dict[str, Any], ...]:
        return schemas_for(_PUBLISH_MODELS, _PUBLISH_DESCRIPTIONS)

    def run(self, call: ToolCall, state: Mapping[str, Any]) -> ToolOutcome:
        payload, invalid = validate(_PUBLISH_MODELS, call)
        if invalid is not None:
            return invalid
        return getattr(self, f"_{call.name}")(payload)

    def _approved(self) -> list[Mapping[str, Any]]:
        posts = self._board.posts("APPROVED", policy.PUBLISH_LIST_LIMIT)
        return sorted(posts, key=lambda post: str(post.get("reviewedAt") or ""))

    def _list_approved_posts(self, _payload: Any) -> ToolOutcome:
        lines = [
            f"{post['id']}\t승인 {post.get('reviewedAt')}\t{_preview(post.get('caption', ''))}"
            for post in self._approved()
        ]
        return ToolOutcome("\n".join(lines) or "승인된 글 없음")

    def _recent(self) -> list[Mapping[str, Any]]:
        posts = self._board.posts("PUBLISHED", policy.PUBLISH_LIST_LIMIT)
        return sorted(posts, key=lambda post: str(post.get("publishedAt") or ""), reverse=True)

    def _list_recent_published(self, _payload: Any) -> ToolOutcome:
        lines = [
            f"{post['id']}\t발행 {post.get('publishedAt')}\t{_preview(post.get('caption', ''))}"
            for post in self._recent()
        ]
        return ToolOutcome("\n".join(lines) or "아직 발행한 글 없음")

    def _publish_now(self, payload: Any) -> ToolOutcome:
        if len(self.published) >= policy.MAX_PUBLISH_PER_RUN:
            return _error(f"이번 실행의 발행 상한({policy.MAX_PUBLISH_PER_RUN}건)에 도달함")
        if not any(post.get("id") == payload.post_id for post in self._approved()):
            return _error(f"승인 대기 중인 글이 아님: {payload.post_id}")
        latest = next((_parse_instant(p.get("publishedAt")) for p in self._recent()), None)
        if latest is not None:
            wait = latest + timedelta(hours=policy.MIN_PUBLISH_INTERVAL_HOURS) - self._clock()
            if wait > timedelta(0):
                minutes = int(wait.total_seconds() // 60) + 1
                return _error(f"직전 발행과 간격이 부족함: {minutes}분 뒤에 가능")
        result = self._board.publish(payload.post_id)
        self.published.append({"id": payload.post_id, "reason": payload.reason})
        return ToolOutcome(f"발행함: {payload.post_id} (외부 ID {result.get('externalPostId')})")
