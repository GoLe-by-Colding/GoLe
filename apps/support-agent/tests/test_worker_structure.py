"""에이전트 경계와 구조 개편 이전 작업 재개를 검증한다."""

import ast
import json
from pathlib import Path
import threading

import pytest

from fixtures.legacy_graph_v1 import build_graph as build_legacy_graph
from gole_agent_worker.agents.contracts import State
from gole_agent_worker.bootstrap import DEFAULT_DB_PATH
from gole_agent_worker.contracts import LeaseLost, Submission
from gole_agent_worker.hands import FakeProvider
from gole_agent_worker.runtime.graph import build_graph
from gole_agent_worker.runtime.jobs import JobService
from gole_agent_worker.runtime.runner import Runner
from gole_agent_worker.runtime.store import Store
from gole_agent_worker.session import FencedSaver


class MemoryContext:
    job_id = "job-1"
    timeout = 1

    def __init__(self):
        self.cancelled = threading.Event()

    def check_active(self):
        if self.cancelled.is_set():
            raise LeaseLost()


def demo():
    return Submission("owner", "key", "ref", "synthetic.demo", {"topic": "brick-colors"}, "fake")


def test_brain_runs_without_database_or_server():
    context = MemoryContext()
    result = build_graph(None, FakeProvider(), context).invoke(
        {"submission": json.loads(demo().canonical())})
    assert result["prepared"] is True
    assert result["result"] == {
        "text": "브릭 색상 예시: 빨강, 노랑, 파랑",
        "external_model_used": False,
        "human_review_required": True,
    }


def test_cancelled_context_prevents_provider_call():
    class MustNotCall:
        def generate(self, *args, **kwargs):
            pytest.fail("취소 뒤 provider를 호출함")

    context = MemoryContext()
    context.cancelled.set()
    with pytest.raises(LeaseLost):
        build_graph(None, MustNotCall(), context).invoke(
            {"submission": json.loads(demo().canonical())})


@pytest.mark.parametrize("kind", ["synthetic.demo", "support.rules"])
@pytest.mark.parametrize("stage", ["prepare", "execute", "complete"])
def test_legacy_checkpoint_resumes_after_structure_change(tmp_path, kind, stage):
    now = [100.0]
    path = str(tmp_path / "legacy.sqlite3")
    store = Store(path, clock=lambda: now[0], lease_seconds=1, backoff_seconds=0)
    submission = demo() if kind == "synthetic.demo" else Submission(
        "owner", "key", "ref", kind,
        {"ticket_id": "ticket", "title": "환불 문의", "message": "결제를 취소하려고 합니다"}, "rules")
    job = store.submit("java", submission)
    claimed = store.claim()
    saver = FencedSaver(store, job["id"], claimed["fence"])
    build_legacy_graph(saver, FakeProvider(), threading.Event(), 1).invoke(
        {"submission": json.loads(submission.canonical())},
        {"configurable": {"thread_id": job["id"]}},
        interrupt_after=[] if stage == "complete" else [stage], durability="sync")

    class CountingProvider(FakeProvider):
        calls = 0

        def generate(self, *args, **kwargs):
            self.calls += 1
            return super().generate(*args, **kwargs)

    provider = CountingProvider()
    now[0] += 2
    reopened = Store(path, clock=lambda: now[0], backoff_seconds=0)
    assert Runner(reopened, provider_factory=lambda _: provider).run_once()
    saved = reopened.get("java", "owner", job["id"])
    assert saved["state"] == "SUCCEEDED"
    assert saved["attempts"] == 2
    result = json.loads(saved["result"])
    assert result["human_review_required"] is True
    assert result["external_model_used"] is False
    assert provider.calls == int(kind == "synthetic.demo" and stage == "prepare")
    if kind == "support.rules":
        assert result["engine_version"] == "rules-v1"
        assert result["recommended_category"] == "PAYMENT"


def test_job_service_rejects_unknown_kind_without_transport(tmp_path):
    store = Store(str(tmp_path / "jobs.sqlite3"))
    jobs = JobService(store)
    with pytest.raises(ValueError, match="UNSUPPORTED_KIND"):
        jobs.submit("java", Submission("owner", "key", "ref", "unknown", {}, "fake"))
    assert store.claim() is None


def test_default_database_and_legacy_imports_are_preserved():
    from gole_agent_worker.brain import State as LegacyState
    from gole_agent_worker.model import Submission as LegacySubmission
    from gole_agent_worker.runner import Runner as LegacyRunner
    from gole_agent_worker.store import Store as LegacyStore

    assert LegacyState is State
    assert LegacySubmission is Submission
    assert LegacyRunner is Runner
    assert LegacyStore is Store
    assert DEFAULT_DB_PATH == Path(__file__).resolve().parents[1] / "data/agent-jobs.sqlite3"


def test_agent_modules_do_not_import_execution_or_storage_implementations():
    root = Path(__file__).resolve().parents[1] / "src/gole_agent_worker/agents"
    forbidden = ("sqlite3", "grpc", "openai", "gole_agent_worker.runtime",
                 "gole_agent_worker.session", "gole_agent_worker.entrypoints",
                 "gole_agent_worker.hands.providers", "gole_agent_worker.model")
    for path in root.glob("*.py"):
        tree = ast.parse(path.read_text(encoding="utf-8"))
        for node in ast.walk(tree):
            modules = ([node.module or ""] if isinstance(node, ast.ImportFrom)
                       else [alias.name for alias in node.names] if isinstance(node, ast.Import) else [])
            assert not any(module == prefix or module.startswith(prefix + ".")
                           for module in modules for prefix in forbidden), path.name
