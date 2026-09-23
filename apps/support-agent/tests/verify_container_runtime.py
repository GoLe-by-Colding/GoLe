"""네트워크·운영 키 없이 설치된 컨테이너의 세 실행 경로를 확인한다."""
from io import BytesIO
from tempfile import TemporaryDirectory

from PIL import Image
from langchain_core.callbacks import BaseCallbackHandler
from langchain_core.runnables import RunnableLambda
from langsmith import tracing_context

from gole.agent.v1 import agent_jobs_pb2
from gole.brick.v1 import brick_images_pb2
from gole.support.v1 import support_agent_pb2
from gole_agent_worker.hands import FakeProvider
from gole_agent_worker.model import Submission
from gole_agent_worker.runner import Runner
from gole_agent_worker.store import Store
from gole_brick_filter.runtime import ImageHarness
from gole_support_agent.agent import analyze_support


events = []


class Observer(BaseCallbackHandler):
    def on_chain_start(self, serialized, inputs, **kwargs):
        events.append(inputs)


class Editor:
    def edit(self, source, mode):
        return source


def verify(_):
    assert agent_jobs_pb2.DESCRIPTOR.services_by_name["AgentJobs"]
    assert brick_images_pb2.DESCRIPTOR.services_by_name["BrickImages"]
    assert support_agent_pb2.DESCRIPTOR.services_by_name
    support = analyze_support(ticket_id="synthetic-id", declared_category="GENERAL",
                              title="synthetic-title", message="synthetic-message")
    assert support["engine_version"] == "rules-v1"
    assert support["human_review_required"] is True
    with TemporaryDirectory() as directory:
        store = Store(directory + "/jobs.sqlite3")
        job = store.submit("java", Submission("owner", "key", "ledger", "synthetic.demo",
                                              {"topic": "brick-colors"}, "fake"))
        Runner(store, provider_factory=lambda _: FakeProvider()).run_once()
        assert store.get("java", "owner", job["id"])["state"] == "SUCCEEDED"
    source = BytesIO()
    Image.new("RGB", (8, 8), "blue").save(source, format="PNG")
    for mode in ("MINIFIGURE", "BRICK_OBJECT"):
        result = ImageHarness(Editor()).run(mode, source.getvalue())
        assert result.image.startswith(b"\x89PNG\r\n\x1a\n")
    return "container-runtime-ok"


with tracing_context(enabled=False):
    result = RunnableLambda(verify).invoke("public-probe", {"callbacks": [Observer()]})
assert events == ["public-probe"]
print(result + ": proto/support/durable/image/privacy (fake providers only)")
