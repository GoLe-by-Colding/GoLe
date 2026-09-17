"""구조 개편 직전 그래프. 이전 체크포인트 호환 검증용 고정 구현."""

from __future__ import annotations

import threading
from typing import Any, TypedDict

from langgraph.graph import END, START, StateGraph

from gole_agent_worker.hands import Provider, ProviderInput
from gole_agent_worker.model import LeaseLost, PermanentFailure, TransientFailure
from gole_support_agent.agent import analyze_support


class State(TypedDict, total=False):
    submission: dict[str, Any]
    prepared: bool
    result: dict[str, Any]


def build_graph(saver, provider: Provider, cancelled: threading.Event, timeout: float):
    def check():
        if cancelled.is_set():
            raise LeaseLost()
        # 외부 호출 직전에도 fencing을 확인한다.
        with saver.store.transaction() as db:
            saver.store.assert_lease(db, saver.job_id, saver.fence)

    def prepare(state):
        check()
        return {"prepared": True}

    def execute(state):
        check()
        submission = state["submission"]
        if submission["kind"] == "support.rules":
            payload = submission["payload"]
            response = analyze_support(**{"declared_category": "GENERAL", **payload})
            result = {key: response[key] for key in (
                "recommended_category", "priority", "summary", "draft_reply", "risk_flags",
                "human_review_required", "external_model_used", "engine_version")}
        else:
            try:
                response = provider.generate(
                    ProviderInput(submission["payload"]["topic"], saver.job_id + ":generate:v1",
                                  submission["authorization_ref"]),
                    timeout=timeout, cancelled=cancelled,
                )
            except LeaseLost:
                raise LeaseLost() from None
            except TransientFailure:
                raise TransientFailure() from None
            except Exception:
                # LangGraph의 오류 pending write에도 원본 예외/키를 남기지 않는다.
                raise PermanentFailure() from None
            result = {"text": response.text, "external_model_used": response.external_model_used}
        check()
        return {"result": result}

    def review(state):
        check()
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
