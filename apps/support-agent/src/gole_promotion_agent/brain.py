"""후보 하나의 처리 순서만 정의한다. provider SDK·브라우저·HTTP를 소유하지 않는다."""

from __future__ import annotations

from pathlib import Path
from typing import Any, Callable, TypedDict

from langgraph.graph import END, START, StateGraph

from gole_promotion_agent import policy
from gole_promotion_agent.ports import Conversation, DraftPublisher, ToolCall, Toolset
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


def build_graph(
    conversation: Conversation,
    toolset: Toolset,
    publisher: DraftPublisher,
    check_active: Callable[[], None],
    on_stage: Callable[[str], None] = lambda _stage: None,
):
    """루프는 도구를 모른다 — 주입받은 toolset 이 스키마와 실행을 가진다."""

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

    def act(state: PromotionState) -> dict[str, Any]:
        check_active()
        transcript = list(state.get("transcript", []))
        last = transcript[-1] if transcript else {"calls": []}
        # 한 턴에 여러 도구를 부르면 앞 도구의 결과(예: captures)를 뒤 도구가 봐야 한다.
        working: dict[str, Any] = dict(state)
        update: dict[str, Any] = {}
        results: list[dict[str, Any]] = []

        for raw in last.get("calls", []):
            call = ToolCall(raw["id"], raw["name"], raw.get("arguments", {}))
            outcome = toolset.run(call, working)
            working.update(outcome.update)
            update.update(outcome.update)
            results.append(
                {
                    "call_id": call.id,
                    "text": outcome.text,
                    "image_path": outcome.image_path,
                    "is_error": outcome.is_error,
                }
            )

        transcript.append({"role": "tool", "results": results})
        return {**update, "transcript": transcript}

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
        if not last.get("calls"):
            # 도구를 더 부르지 않았다 = 모델이 스스로 "홍보할 것이 없다"고 판단한 것이다.
            return "give_up"
        # 마지막 허용 턴에 결정한 도구도 실행한다. 예산은 다음 모델 호출 전에 검사한다.
        return "act"

    def after_act(state: PromotionState) -> str:
        if state.get("draft") and not state.get("submitted"):
            return "upload"
        return "think" if state.get("turns", 0) < policy.MAX_TURNS else "defer"

    def give_up(state: PromotionState) -> dict[str, Any]:
        on_stage(Stage.SKIPPED)
        return {"outcome": "skipped"}

    def defer(state: PromotionState) -> dict[str, Any]:
        """예산을 다 썼다. 결론이 아니라 중단이므로 다음 실행이 다시 본다."""
        on_stage(Stage.DEFERRED)
        return {"outcome": "deferred"}

    graph = StateGraph(PromotionState)
    graph.add_node("think", think)
    graph.add_node("act", act)
    graph.add_node("upload", upload)
    graph.add_node("create", create)
    graph.add_node("finalize", finalize)
    graph.add_node("give_up", give_up)
    graph.add_node("defer", defer)

    graph.add_edge(START, "think")
    graph.add_conditional_edges(
        "think", after_think, {"act": "act", "give_up": "give_up", "defer": "defer"}
    )
    graph.add_conditional_edges("act", after_act, {"upload": "upload", "think": "think", "defer": "defer"})
    graph.add_edge("upload", "create")
    graph.add_edge("create", "finalize")
    graph.add_edge("finalize", END)
    graph.add_edge("give_up", END)
    graph.add_edge("defer", END)
    return graph
