from __future__ import annotations

from langgraph.graph import END, START, StateGraph

from gole_agent_worker.agents.contracts import ExecutionContext, State
from gole_agent_worker.hands.contracts import Provider
from gole_agent_worker.runtime.registry import get_agent


def build_graph(saver, provider: Provider, context: ExecutionContext):
    """기존 v1 노드·상태를 보존하는 공통 고정 순서 흐름."""

    def prepare(state):
        context.check_active()
        return {"prepared": True}

    def execute(state):
        context.check_active()
        submission = state["submission"]
        result = get_agent(submission["kind"]).execute(submission, provider, context)
        context.check_active()
        return {"result": result}

    def review(state):
        context.check_active()
        return {"result": {**state["result"], "human_review_required": True}}

    graph = StateGraph(State)
    graph.add_node("prepare", prepare)
    graph.add_node("execute", execute)
    graph.add_node("review", review)
    graph.add_edge(START, "prepare")
    graph.add_edge("prepare", "execute")
    graph.add_edge("execute", "review")
    graph.add_edge("review", END)
    return graph.compile(checkpointer=saver)
