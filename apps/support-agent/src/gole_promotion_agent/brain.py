"""후보 하나의 처리 순서만 정의한다. provider SDK·브라우저·HTTP를 소유하지 않는다."""

from __future__ import annotations

from pathlib import Path
from typing import Any, Callable, TypedDict

from langgraph.graph import END, START, StateGraph
from pydantic import ValidationError

from gole_promotion_agent import policy
from gole_promotion_agent.ports import (
    Camera,
    Conversation,
    DraftPublisher,
    ReleaseScanner,
    RouteCatalog,
    ToolCall,
)
from gole_promotion_agent.session import Stage


class PromotionState(TypedDict, total=False):
    """직렬화 가능한 값만 담는다 — 이미지 바이트는 절대 들어오지 않는다(스펙 D17)."""

    sha: str
    subject: str
    system: str  # 실행 시작에 동결한 시스템 프롬프트(이력 포함)
    transcript: list[dict[str, Any]]
    turns: int
    captures: list[dict[str, Any]]  # {key, label, route, interactions, path}
    stop_reason: str
    draft: dict[str, Any]  # {caption, labels}
    media_keys: list[str]
    post_id: str
    submitted: bool
    outcome: str


def _screens_dir(session_dir: Path) -> Path:
    return Path(session_dir) / "screens"


def build_graph(
    conversation: Conversation,
    scanner: ReleaseScanner,
    routes: RouteCatalog,
    camera: Camera,
    publisher: DraftPublisher,
    session_dir: Path,
    check_active: Callable[[], None],
    on_stage: Callable[[str], None] = lambda _stage: None,
):
    allowed_routes = frozenset(routes.routes())

    def think(state: PromotionState) -> dict[str, Any]:
        check_active()
        on_stage(Stage.EXPLORING if not state.get("draft") else Stage.DRAFTING)
        transcript = list(state.get("transcript", []))
        turn = conversation.advance(transcript)
        if turn.stop_reason == "refusal":
            raise ValueError("MODEL_REFUSED")
        transcript.append(
            {
                "role": "assistant",
                "text": turn.text,
                "calls": [
                    {"id": call.id, "name": call.name, "arguments": dict(call.arguments)}
                    for call in turn.calls
                ],
            }
        )
        return {
            "transcript": transcript,
            "turns": state.get("turns", 0) + 1,
            "stop_reason": turn.stop_reason,
        }

    def _run_tool(call: ToolCall, state: PromotionState, captures: list[dict[str, Any]]):
        """(결과 텍스트, 이미지 경로, 새 draft) 를 돌려준다."""
        model = policy.TOOL_MODELS.get(call.name)
        if model is None:
            return f"알 수 없는 도구: {call.name}", None, None, True
        try:
            payload = model.model_validate(dict(call.arguments))
        except ValidationError as error:
            return f"INVALID_TOOL_INPUT: {error.error_count()}건", None, None, True

        if call.name == "list_releases":
            listed = "\n".join(f"{c.sha}\t{c.subject}" for c in scanner.candidates())
            return listed or "후보 없음", None, None, False

        if call.name == "read_release_diff":
            if payload.sha != state["sha"]:
                return f"현재 후보가 아닌 릴리스는 읽을 수 없음: {payload.sha}", None, None, True
            return scanner.diff(payload.sha), None, None, False

        if call.name == "list_routes":
            return "\n".join(sorted(allowed_routes)), None, None, False

        if call.name == "capture":
            if payload.route not in allowed_routes:
                return f"허용되지 않은 공개 라우트: {payload.route}", None, None, True
            if any(item["label"] == payload.label for item in captures):
                return f"이미 사용한 라벨: {payload.label}", None, None, True
            if len(captures) >= policy.MAX_SCREENSHOTS:
                return "스크린샷 수 상한에 도달함", None, None, True
            interactions = [item.model_dump() for item in payload.interactions]
            key = policy.capture_key(payload.route, interactions)
            existing = next((item for item in captures if item["key"] == key), None)
            if existing is not None:
                # 같은 화면은 다시 찍지 않는다. 재개했을 때도 여기서 걸린다.
                captures.append({**existing, "label": payload.label})
                return f"이미 찍은 화면을 재사용함: {payload.label}", existing["path"], None, False
            destination = _screens_dir(session_dir) / f"{key}.png"
            camera.capture(payload.route, interactions, destination)
            captures.append(
                {
                    "key": key,
                    "label": payload.label,
                    "route": payload.route,
                    "interactions": interactions,
                    "path": str(destination),
                }
            )
            return f"스크린샷 {payload.label}: {destination}", str(destination), None, False

        if call.name == "submit_promotion_draft":
            if payload.sha != state["sha"]:
                return f"현재 후보와 다른 SHA는 제출할 수 없음: {payload.sha}", None, None, True
            missing = [
                label
                for label in payload.screenshot_labels
                if not any(item["label"] == label for item in captures)
            ]
            if missing:
                return f"세션에 없는 스크린샷 라벨: {', '.join(missing)}", None, None, True
            draft = {"caption": payload.caption, "labels": list(payload.screenshot_labels)}
            return "초안 제출을 시작함", None, draft, False

        return f"처리되지 않은 도구: {call.name}", None, None, True

    def act(state: PromotionState) -> dict[str, Any]:
        check_active()
        captures = [dict(item) for item in state.get("captures", [])]
        transcript = list(state.get("transcript", []))
        last = transcript[-1] if transcript else {"calls": []}
        results: list[dict[str, Any]] = []
        draft: dict[str, Any] | None = None

        for raw in last.get("calls", []):
            call = ToolCall(raw["id"], raw["name"], raw.get("arguments", {}))
            text, image_path, produced, is_error = _run_tool(call, state, captures)
            if produced is not None:
                draft = produced
            results.append(
                {
                    "call_id": call.id,
                    "text": text,
                    "image_path": image_path,
                    "is_error": is_error,
                }
            )

        transcript.append({"role": "tool", "results": results})
        update: dict[str, Any] = {"transcript": transcript, "captures": captures}
        if draft is not None:
            update["draft"] = draft
        return update

    def upload(state: PromotionState) -> dict[str, Any]:
        check_active()
        on_stage(Stage.DRAFTING)
        if state.get("media_keys"):
            return {}
        labels = state["draft"]["labels"]
        by_label = {item["label"]: item["path"] for item in state.get("captures", [])}
        paths = [Path(by_label[label]) for label in labels]
        return {"media_keys": list(publisher.upload(paths))}

    def create(state: PromotionState) -> dict[str, Any]:
        check_active()
        if state.get("post_id"):
            return {}
        sha = state["sha"]
        if publisher.exists(sha):
            raise ValueError("DUPLICATE_SOURCE_COMMIT")
        return {
            "post_id": publisher.create(sha, state["draft"]["caption"], state["media_keys"])
        }

    def finalize(state: PromotionState) -> dict[str, Any]:
        check_active()
        if not state.get("submitted"):
            publisher.finalize(state["post_id"])
        on_stage(Stage.SUCCEEDED)
        return {"submitted": True, "outcome": "submitted"}

    def after_think(state: PromotionState) -> str:
        transcript = state.get("transcript", [])
        last = transcript[-1] if transcript else {}
        if last.get("calls"):
            return "act" if state.get("turns", 0) < policy.MAX_TURNS else "give_up"
        return "give_up"

    def after_act(state: PromotionState) -> str:
        return "upload" if state.get("draft") and not state.get("submitted") else "think"

    def give_up(state: PromotionState) -> dict[str, Any]:
        on_stage(Stage.SKIPPED)
        return {"outcome": "skipped"}

    graph = StateGraph(PromotionState)
    graph.add_node("think", think)
    graph.add_node("act", act)
    graph.add_node("upload", upload)
    graph.add_node("create", create)
    graph.add_node("finalize", finalize)
    graph.add_node("give_up", give_up)

    graph.add_edge(START, "think")
    graph.add_conditional_edges("think", after_think, {"act": "act", "give_up": "give_up"})
    graph.add_conditional_edges("act", after_act, {"upload": "upload", "think": "think"})
    graph.add_edge("upload", "create")
    graph.add_edge("create", "finalize")
    graph.add_edge("finalize", END)
    graph.add_edge("give_up", END)
    return graph
