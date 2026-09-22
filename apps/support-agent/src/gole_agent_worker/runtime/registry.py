"""허용한 작업의 검증과 실행을 같은 등록 항목으로 선택한다."""

from types import MappingProxyType

from gole_agent_worker.agents.contracts import Agent
from gole_agent_worker.agents.support import SupportAgent
from gole_agent_worker.agents.synthetic import SyntheticAgent

# 두 항목은 격이 다르다. 나란히 있어서 동등해 보이지만 그렇지 않다.
#   support.rules   실사용 kind. 다만 gole_support_agent.analyze_support 한 줄 위임이라
#                   동기 서버(:50051, 운영 배포됨)와 로직이 같다. 워커가 더하는 것은
#                   영속성·재개뿐이고, 그 영속성을 요구하는 호출자는 아직 없다.
#   synthetic.demo  **데모 전용.** 고정 비개인정보 입력(topic=brick-colors)으로 외부
#                   provider 계약만 확인한다. 실제 기능이 아니므로 "등록된 kind 2개"를
#                   사용처 2곳으로 읽으면 안 된다.
# 이 패키지의 보류 상태는 gole_agent_worker/__init__.py 를 본다.
AGENTS = MappingProxyType({"support.rules": SupportAgent(), "synthetic.demo": SyntheticAgent()})


def get_agent(kind: str) -> Agent:
    try:
        return AGENTS[kind]
    except KeyError:
        raise ValueError("UNSUPPORTED_KIND") from None
