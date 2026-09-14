from io import BytesIO
from unittest.mock import Mock

import pytest
from PIL import Image
from langsmith import tracing_context
from langsmith.utils import tracing_is_enabled
from langchain_core.callbacks import BaseCallbackHandler
from langchain_core.runnables import RunnableLambda

from gole_agent_runtime.privacy import TRACING_KEYS, private_execution, reject_external_tracing
from gole_agent_worker.hands import FakeProvider
from gole_agent_worker.model import Submission
from gole_agent_worker.runner import Runner
from gole_agent_worker.store import Store
from gole_brick_filter.runtime import ImageHarness
from gole_support_agent.agent import analyze_support


@pytest.mark.parametrize("key", TRACING_KEYS)
def test_all_tracing_aliases_rejected(monkeypatch, key):
    for name in TRACING_KEYS:
        monkeypatch.delenv(name, raising=False)
    monkeypatch.setenv(key, "true")
    with pytest.raises(ValueError, match="EXTERNAL_TRACING_FORBIDDEN"):
        reject_external_tracing()


def test_execution_overrides_parent_and_restores_after_error():
    @private_execution
    def work():
        assert tracing_is_enabled() is False
        raise ValueError("synthetic")

    with tracing_context(enabled=True):
        with pytest.raises(ValueError, match="synthetic"):
            work()
        assert tracing_is_enabled() is True


def test_image_and_durable_provider_do_not_inherit_enabled_exporter(tmp_path):
    client = Mock()
    image = BytesIO()
    Image.new("RGB", (8, 8), "blue").save(image, format="PNG")

    class Editor:
        def edit(self, source, mode):
            assert tracing_is_enabled() is False
            return source

    class Provider(FakeProvider):
        def generate(self, request, **kwargs):
            assert tracing_is_enabled() is False
            return super().generate(request, **kwargs)

    store = Store(str(tmp_path / "jobs.db"))
    job = store.submit("java", Submission("owner", "key", "ledger", "synthetic.demo",
                                          {"topic": "brick-colors"}, "fake"))
    with tracing_context(enabled=True, client=client):
        assert ImageHarness(Editor()).run("MINIFIGURE", image.getvalue()).image
        Runner(store, provider_factory=lambda _: Provider()).run_once()
        assert store.get("java", "owner", job["id"])["state"] == "SUCCEEDED"
        result = analyze_support(ticket_id="private-id", declared_category="GENERAL",
                                 title="private-title", message="private-message")
        assert result["human_review_required"] is True
        assert tracing_is_enabled() is True
    client.create_run.assert_not_called()
    client.update_run.assert_not_called()


def test_inherited_callback_cannot_observe_support_source():
    events = []

    class Observer(BaseCallbackHandler):
        def on_chain_start(self, serialized, inputs, **kwargs):
            events.append(inputs)

    def work(_):
        result = analyze_support(ticket_id="private-id", declared_category="GENERAL",
                                 title="private-title", message="private-message")
        return result["engine_version"]

    with tracing_context(enabled=False):
        result = RunnableLambda(work).invoke("outer-public-input", {"callbacks": [Observer()]})
    assert result == "rules-v1"
    assert events == ["outer-public-input"]


@pytest.mark.parametrize("kind", ["image", "durable"])
def test_inherited_callback_cannot_observe_other_private_graphs(tmp_path, kind):
    events = []

    class Observer(BaseCallbackHandler):
        def on_chain_start(self, serialized, inputs, **kwargs):
            events.append(inputs)

    def work(_):
        if kind == "image":
            source = BytesIO()
            Image.new("RGB", (8, 8), "blue").save(source, format="PNG")

            class Editor:
                def edit(self, data, mode):
                    return data

            return bool(ImageHarness(Editor()).run("MINIFIGURE", source.getvalue()).image)
        store = Store(str(tmp_path / "jobs.db"))
        job = store.submit("java", Submission("owner", "key", "ledger", "synthetic.demo",
                                              {"topic": "brick-colors"}, "fake"))
        Runner(store, provider_factory=lambda _: FakeProvider()).run_once()
        return store.get("java", "owner", job["id"])["state"] == "SUCCEEDED"

    with tracing_context(enabled=False):
        assert RunnableLambda(work).invoke("outer-public-input", {"callbacks": [Observer()]})
    assert events == ["outer-public-input"]
