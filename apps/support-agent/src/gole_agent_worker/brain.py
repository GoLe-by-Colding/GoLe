"""기존 build_graph 호출 형식의 호환 진입점."""

from gole_agent_worker.agents.contracts import State
from gole_agent_worker.runtime.context import JobExecutionContext
from gole_agent_worker.runtime.graph import build_graph as build_runtime_graph

__all__ = ["State", "build_graph"]


def build_graph(saver, provider, cancelled, timeout):
    context = JobExecutionContext(saver.store, saver.job_id, saver.fence, cancelled, timeout)
    return build_runtime_graph(saver, provider, context)
