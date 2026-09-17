"""허용한 작업의 검증과 실행을 같은 등록 항목으로 선택한다."""

from types import MappingProxyType

from gole_agent_worker.agents.contracts import Agent
from gole_agent_worker.agents.support import SupportAgent
from gole_agent_worker.agents.synthetic import SyntheticAgent

AGENTS = MappingProxyType({"support.rules": SupportAgent(), "synthetic.demo": SyntheticAgent()})


def get_agent(kind: str) -> Agent:
    try:
        return AGENTS[kind]
    except KeyError:
        raise ValueError("UNSUPPORTED_KIND") from None
