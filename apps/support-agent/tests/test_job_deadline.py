from __future__ import annotations

import sqlite3

import grpc
import pytest

from gole.agent.v1 import agent_jobs_pb2 as pb, agent_jobs_pb2_grpc as rpc
from gole_agent_worker.model import LeaseLost, NotFound, Submission
from gole_agent_worker.server import AgentJobsService, create_server
from gole_agent_worker.session import FencedSaver
from gole_agent_worker.store import Store
from gole_agent_worker.runner import Runner
from gole_agent_worker.hands import FakeProvider


def submission(key="deadline"):
    return Submission("owner", key, "test-ledger", "synthetic.demo",
                      {"topic": "brick-colors"}, "fake")


@pytest.fixture
def clock_store(tmp_path):
    now = [100.0]
    store = Store(str(tmp_path / "jobs.db"), clock=lambda: now[0],
                  lease_seconds=60, max_job_seconds=10)
    return now, store


@pytest.mark.parametrize("method", ["get", "duplicate", "claim"])
def test_unclaimed_job_expires_without_provider_and_only_once(clock_store, method):
    now, store = clock_store
    job = store.submit("java", submission())
    now[0] = 110
    if method == "get":
        result = store.get("java", "owner", job["id"])
    elif method == "duplicate":
        result = store.submit("java", submission())
    else:
        assert store.claim() is None
        result = store.get("java", "owner", job["id"])
    assert result["state"] == "FAILED"
    assert result["error"] == "JOB_DEADLINE_EXCEEDED"
    assert result["attempts"] == 0
    assert store.submit("java", submission())["id"] == job["id"]
    assert store.claim() is None
    with store.connection() as db:
        assert db.execute("SELECT COUNT(*) FROM events WHERE code='JOB_DEADLINE_EXCEEDED'").fetchone()[0] == 1


def test_deadline_fences_running_writes_before_poll_or_reclaim(clock_store):
    now, store = clock_store
    store.submit("java", submission())
    job = store.claim()
    saver = FencedSaver(store, job["id"], job["fence"])
    config = {"configurable": {"thread_id": job["id"], "checkpoint_id": "old"}}
    now[0] = 110
    for action in (
        lambda: store.finish(job["id"], job["fence"], {"late": True}),
        lambda: store.heartbeat(job["id"], job["fence"]),
        lambda: store.fail(job["id"], job["fence"], retryable=True, code="PROVIDER_TRANSIENT"),
        lambda: saver.put(config, {"id": "late"}, {}, {}),
        lambda: saver.put_writes(config, [("result", "late")], "task"),
        lambda: saver.get_tuple(config),
    ):
        with pytest.raises(LeaseLost):
            action()
    result = store.get("java", "owner", job["id"])
    assert result["fence"] == job["fence"] + 1
    assert result["result"] == ""
    assert result["state"] == "FAILED"


def test_retry_wait_does_not_reset_total_budget(clock_store):
    now, store = clock_store
    store.submit("java", submission())
    job = store.claim()
    now[0] = 109
    store.fail(job["id"], job["fence"], retryable=True, code="PROVIDER_TRANSIENT")
    now[0] = 111
    assert store.claim() is None
    assert store.get("java", "owner", job["id"])["error"] == "JOB_DEADLINE_EXCEEDED"


@pytest.mark.parametrize("terminal", ["SUCCEEDED", "CANCELLED", "FAILED"])
def test_terminal_results_survive_deadline(clock_store, terminal):
    now, store = clock_store
    store.submit("java", submission())
    job = store.claim()
    if terminal == "SUCCEEDED":
        store.finish(job["id"], job["fence"], {"ok": True})
    elif terminal == "CANCELLED":
        store.cancel("java", "owner", job["id"])
    else:
        store.fail(job["id"], job["fence"], retryable=False, code="PROVIDER_FAILED")
    now[0] = 200
    assert store.get("java", "owner", job["id"])["state"] == terminal


def test_restart_preserves_deadline_and_owner_boundary(clock_store):
    now, store = clock_store
    job = store.submit("java", submission())
    restarted = Store(store.path, clock=lambda: now[0], max_job_seconds=900)
    now[0] = 110
    with pytest.raises(NotFound):
        restarted.get("java", "other", job["id"])
    with store.connection() as db:
        assert db.execute("SELECT state FROM jobs").fetchone()[0] == "QUEUED"
    result = restarted.get("java", "owner", job["id"])
    assert result["deadline_at"] == 110
    assert result["state"] == "FAILED"


def test_migration_backfills_from_creation_without_resetting(clock_store):
    now, store = clock_store
    job = store.submit("java", submission())
    with sqlite3.connect(store.path) as db:
        db.execute("DROP INDEX jobs_deadline")
        db.execute("ALTER TABLE jobs DROP COLUMN deadline_at")
    now[0] = 400
    restarted = Store(store.path, clock=lambda: now[0])
    result = restarted.get("java", "owner", job["id"])
    assert result["deadline_at"] == 400
    assert result["state"] == "FAILED"


def test_grpc_reports_expiry_without_execution_loop(clock_store):
    now, store = clock_store
    token = "deadline-test-only-token-32-characters"
    server, port = create_server(AgentJobsService(store, caller="java", token=token), 0)
    server.start()
    try:
        with grpc.insecure_channel(f"127.0.0.1:{port}") as channel:
            client = rpc.AgentJobsStub(channel)
            metadata = (("x-gole-caller", "java"), ("authorization", "Bearer " + token))
            job = client.Submit(pb.SubmitJobRequest(
                owner_id="owner", idempotency_key="deadline", authorization_ref="test-ledger",
                kind="synthetic.demo", payload_json='{"topic":"brick-colors"}', provider="fake"),
                metadata=metadata, timeout=2)
            now[0] = 110
            result = client.Get(pb.JobRequest(owner_id="owner", job_id=job.job_id),
                                metadata=metadata, timeout=2)
            assert result.state == pb.FAILED
            assert result.error_code == "JOB_DEADLINE_EXCEEDED"
    finally:
        server.stop(None).wait()


@pytest.mark.parametrize("budget", [0, -1, float("inf"), float("nan")])
def test_invalid_budget_is_rejected(tmp_path, budget):
    with pytest.raises(ValueError, match="INVALID_STORE_CONFIGURATION"):
        Store(str(tmp_path / "jobs.db"), max_job_seconds=budget)


def test_provider_receives_remaining_total_budget(clock_store):
    now, store = clock_store
    store.submit("java", submission())
    job = store.claim()
    now[0] = 109
    observed = []

    class RecordingProvider(FakeProvider):
        def generate(self, request, **kwargs):
            observed.append(kwargs["timeout"])
            return super().generate(request, **kwargs)

    Runner(store, provider_factory=lambda _: RecordingProvider()).run_claimed(job)
    assert observed == [1.0]
    assert store.get("java", "owner", job["id"])["state"] == "SUCCEEDED"


def test_delayed_runner_never_starts_provider_after_deadline(clock_store):
    now, store = clock_store
    store.submit("java", submission())
    job = store.claim()
    now[0] = 110

    def forbidden(_):
        pytest.fail("만료 작업은 provider를 생성하면 안 된다")

    Runner(store, provider_factory=forbidden).run_claimed(job)
    assert store.get("java", "owner", job["id"])["state"] == "FAILED"
