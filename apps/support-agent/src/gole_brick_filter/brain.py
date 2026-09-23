"""사진 변환의 처리 순서만 정의하며 provider SDK와 저장소를 소유하지 않는다."""
from __future__ import annotations
from typing import Callable, TypedDict
from langgraph.graph import END, START, StateGraph
from gole_brick_filter.policy import MAX_INPUT, MAX_OUTPUT, PROMPTS
from gole_brick_filter.ports import Editor, ImageSanitizer


class BrickState(TypedDict, total=False):
    mode: str
    source: bytes
    result: bytes


def build_graph(editor: Editor, check_active: Callable[[], None], sanitize: ImageSanitizer,
                on_stage: Callable[[str], None] = lambda _stage: None):
    def validate(state: BrickState):
        check_active()
        on_stage("VALIDATING")
        if state.get("mode") not in PROMPTS:
            raise ValueError("MODE")
        return {"source": sanitize(state.get("source", b""), MAX_INPUT)}

    def generate(state: BrickState):
        check_active()
        on_stage("GENERATING")
        return {"result": editor.edit(state["source"], state["mode"]), "source": b""}

    def finish(state: BrickState):
        check_active()
        on_stage("REVIEWING")
        return {"result": sanitize(state["result"], MAX_OUTPUT)}

    graph = StateGraph(BrickState)
    graph.add_node("validate", validate)
    graph.add_node("generate", generate)
    graph.add_node("result", finish)
    graph.add_edge(START, "validate")
    graph.add_edge("validate", "generate")
    graph.add_edge("generate", "result")
    graph.add_edge("result", END)
    # No checkpointer, tracing or persistence of source images in graph state.
    return graph.compile()
