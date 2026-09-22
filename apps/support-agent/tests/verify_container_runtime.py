"""네트워크·운영 키 없이 설치된 컨테이너의 실행 경로를 확인한다.

**이미지가 실제로 싣는 것만 검사한다.** 운영 이미지(`Dockerfile`)는 상주 엔트리포인트인
`gole_support_agent.server`와 그것이 쓰는 관측 경계(`gole_agent_runtime`)만 담는다.
보류 중인 `gole_agent_worker`와 미배포 `gole_brick_filter`는 이미지에 들어가지 않으므로
여기서도 검사하지 않는다 — 싣지 않는 것을 검사하면 통과해도 의미가 없다.

그 둘의 검증이 약해지는 것은 아니다. CI 가 `uv run pytest`로 전체를 매 PR 마다 돌린다
(`.github/workflows/ci.yml`). 이 probe 의 역할은 "배포되는 이미지가 자기가 싣는 것을 실제로
돌릴 수 있는가" 하나다.
"""
from langchain_core.callbacks import BaseCallbackHandler
from langchain_core.runnables import RunnableLambda
from langsmith import tracing_context

from gole.support.v1 import support_agent_pb2
from gole_support_agent.agent import analyze_support


events = []


class Observer(BaseCallbackHandler):
    def on_chain_start(self, serialized, inputs, **kwargs):
        events.append(inputs)


def verify(_):
    assert support_agent_pb2.DESCRIPTOR.services_by_name["SupportAgent"]
    support = analyze_support(ticket_id="synthetic-id", declared_category="GENERAL",
                              title="synthetic-title", message="synthetic-message")
    assert support["engine_version"] == "rules-v1"
    assert support["human_review_required"] is True
    assert support["external_model_used"] is False
    # 보류·미배포 패키지가 이미지에 섞여 들어오지 않았는지 확인한다.
    for absent in ("gole_agent_worker", "gole_brick_filter", "gole_promotion_agent"):
        try:
            __import__(absent)
        except ImportError:
            continue
        raise AssertionError(f"{absent} 가 운영 이미지에 들어 있다")
    return "container-runtime-ok"


with tracing_context(enabled=False):
    result = RunnableLambda(verify).invoke("public-probe", {"callbacks": [Observer()]})
assert events == ["public-probe"]
print(result + ": proto/support/privacy (no external calls)")
