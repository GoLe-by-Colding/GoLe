from __future__ import annotations

import json
import multiprocessing
import os
import threading
import time
from concurrent.futures import ThreadPoolExecutor
from types import SimpleNamespace

import grpc
import pytest

from gole.agent.v1 import agent_jobs_pb2 as pb, agent_jobs_pb2_grpc as rpc
from gole_agent_worker.brain import build_graph
from gole_agent_worker.hands import FakeProvider, OpenAIProvider, ProviderInput, ProviderOutput
from gole_agent_worker.model import Conflict, LeaseLost, PermanentFailure, Submission, TransientFailure
from gole_agent_worker.runner import Runner
from gole_agent_worker.server import AgentJobsService, create_server
from gole_agent_worker.session import FencedSaver
from gole_agent_worker.store import Store

TOKEN = "unit-test-internal-token-32-characters"
METADATA = (("x-gole-caller", "java"), ("authorization", "Bearer " + TOKEN))


def submission(key="request-1", owner="owner-1"):
    return Submission(owner, key, "java-ledger-ref-1", "synthetic.demo",
                      {"topic": "brick-colors"}, "fake")


def request(**changes):
    fields = dict(owner_id="owner-1", idempotency_key="request-1",
                  authorization_ref="java-ledger-ref-1", kind="synthetic.demo",
                  payload_json='{"topic":"brick-colors"}', provider="fake")
    fields.update(changes)
    return pb.SubmitJobRequest(**fields)


@pytest.fixture
def store(tmp_path):
    return Store(str(tmp_path / "jobs.sqlite3"), lease_seconds=0.3, backoff_seconds=0.01)


@pytest.fixture
def client(store):
    server, port = create_server(AgentJobsService(store, caller="java", token=TOKEN), 0)
    server.start()
    with grpc.insecure_channel(f"127.0.0.1:{port}") as channel:
        yield rpc.AgentJobsStub(channel)
    server.stop(None).wait()


def test_grpc_submit_duplicate_conflict_owner_and_cancel(client, store):
    with ThreadPoolExecutor(max_workers=8) as pool:
        jobs = list(pool.map(lambda _: client.Submit(request(), metadata=METADATA, timeout=2), range(16)))
    assert len({j.job_id for j in jobs}) == 1
    job = jobs[0]
    assert client.Submit(request(payload_json='{ "topic": "brick-colors" }'),
                         metadata=METADATA).job_id == job.job_id
    with pytest.raises(grpc.RpcError) as error:
        client.Submit(request(authorization_ref="different-ref"), metadata=METADATA)
    assert error.value.code() == grpc.StatusCode.ALREADY_EXISTS
    for method in (client.Get, client.Cancel):
        with pytest.raises(grpc.RpcError) as error:
            method(pb.JobRequest(owner_id="other", job_id=job.job_id), metadata=METADATA)
        assert error.value.code() == grpc.StatusCode.NOT_FOUND
    query = pb.JobRequest(owner_id="owner-1", job_id=job.job_id)
    assert client.Cancel(query, metadata=METADATA).state == pb.CANCELLED
    assert client.Cancel(query, metadata=METADATA).state == pb.CANCELLED
    assert not Runner(store).run_once()
    assert client.Submit(request(), metadata=METADATA).state == pb.CANCELLED


@pytest.mark.parametrize("metadata", [(), (("x-gole-caller", "java"),),
                                      (("x-gole-caller", "other"), METADATA[1]),
                                      METADATA + (METADATA[1],)])
def test_grpc_auth_precedes_payload_and_lookup(client, metadata):
    for call, value in ((client.Submit, pb.SubmitJobRequest()),
                        (client.Get, pb.JobRequest()), (client.Cancel, pb.JobRequest())):
        with pytest.raises(grpc.RpcError) as error:
            call(value, metadata=metadata, timeout=2)
        assert error.value.code() == grpc.StatusCode.UNAUTHENTICATED


@pytest.mark.parametrize("changes", [
    {"kind": "brickfilter"}, {"authorization_ref": ""}, {"owner_id": ""},
    {"provider": "openai", "allow_external": True},
    {"payload_json": '{"topic":"user supplied private text"}'},
    {"payload_json": "NaN"}, {"payload_json": "[]"},
    {"payload_json": "x" * 13000},
    {"kind": "support.rules", "provider": "openai", "allow_external": True},
])
def test_grpc_rejects_unapproved_or_invalid_input(client, changes):
    with pytest.raises(grpc.RpcError) as error:
        client.Submit(request(**changes), metadata=METADATA)
    assert error.value.code() == grpc.StatusCode.INVALID_ARGUMENT


def test_grpc_execution_and_support_rules_compatibility(client, store):
    job = client.Submit(request(), metadata=METADATA)
    assert Runner(store).run_once()
    result = client.Get(pb.JobRequest(owner_id="owner-1", job_id=job.job_id), metadata=METADATA)
    assert result.state == pb.SUCCEEDED
    assert json.loads(result.result_json)["external_model_used"] is False
    payload = dict(ticket_id="ticket-1", title="환불 문의", message="결제를 취소하려고 합니다")
    support = client.Submit(request(idempotency_key="support", kind="support.rules", provider="rules",
                                    payload_json=json.dumps(payload)), metadata=METADATA)
    assert Runner(store).run_once()
    result = client.Get(pb.JobRequest(owner_id="owner-1", job_id=support.job_id), metadata=METADATA)
    parsed = json.loads(result.result_json)
    assert result.state == pb.SUCCEEDED
    assert parsed["engine_version"] == "rules-v1"
    assert parsed["human_review_required"] is True
    assert parsed["external_model_used"] is False
    assert parsed["recommended_category"] == "PAYMENT"
    assert payload["message"] not in result.result_json
    with store.connection() as db:
        assert db.execute("SELECT COUNT(*) FROM checkpoints").fetchone()[0] >= 8
        events = [dict(row) for row in db.execute("SELECT * FROM events")]
    assert payload["message"] not in json.dumps(events)
    assert "java-ledger-ref-1" not in json.dumps(events)


def test_lease_expiry_fences_completion_heartbeat_checkpoint_and_pending_writes(tmp_path):
    now = [100.0]
    store = Store(str(tmp_path / "jobs.db"), clock=lambda: now[0], lease_seconds=5, backoff_seconds=1)
    job = store.submit("java", submission())
    first = store.claim()
    saver = FencedSaver(store, job["id"], first["fence"])
    config = {"configurable": {"thread_id": job["id"], "checkpoint_id": "old"}}
    now[0] = 106
    assert store.claim() is None
    now[0] = 107
    second = store.claim()
    assert second["fence"] > first["fence"]
    for action in (
        lambda: store.finish(job["id"], first["fence"], {}),
        lambda: store.heartbeat(job["id"], first["fence"]),
        lambda: saver.put(config, {"id": "stale"}, {}, {}),
        lambda: saver.put_writes(config, [("result", "stale")], "task"),
        lambda: saver.get_tuple(config),
    ):
        with pytest.raises(LeaseLost):
            action()
    store.finish(job["id"], second["fence"], {"valid": True})
    assert store.get("java", "owner-1", job["id"])["result"] == '{"valid": true}'


class FlakyProvider(FakeProvider):
    def __init__(self, failures, permanent=False):
        self.failures, self.permanent, self.calls = failures, permanent, 0
        self.keys = []

    def generate(self, request, **kwargs):
        self.calls += 1
        self.keys.append((request.operation_key, request.authorization_ref))
        if self.calls <= self.failures:
            raise PermanentFailure() if self.permanent else TransientFailure()
        return super().generate(request, **kwargs)


@pytest.mark.parametrize("failures,permanent,expected,attempts", [
    (1, False, "SUCCEEDED", 2), (10, False, "FAILED", 3), (1, True, "FAILED", 1),
])
def test_bounded_retry_resume_and_sanitized_failure(tmp_path, failures, permanent, expected, attempts):
    now = [100.0]
    store = Store(str(tmp_path / "jobs.db"), clock=lambda: now[0], backoff_seconds=2)
    job = store.submit("java", submission())
    provider = FlakyProvider(failures, permanent)
    runner = Runner(store, provider_factory=lambda _: provider)
    assert runner.run_once()
    if attempts > 1:
        assert not runner.run_once()
    for _ in range(5):
        now[0] += 60
        runner.run_once()
    result = store.get("java", "owner-1", job["id"])
    assert result["state"] == expected
    assert result["attempts"] == attempts
    assert provider.calls == attempts
    assert len(set(provider.keys)) == 1
    with store.connection() as db:
        # 재시도마다 prepare를 처음부터 실행하지 않는다.
        checkpoints = db.execute("SELECT metadata_type,metadata FROM checkpoints").fetchall()
    saver = FencedSaver(store, job["id"], 0)
    metadata = [saver.serde.loads_typed((row[0], row[1])) for row in checkpoints]
    assert sum(m.get("step") == 1 for m in metadata) == 1


def test_running_cancel_blocks_late_result_and_heartbeat(store):
    entered, released = threading.Event(), threading.Event()

    class Slow(FakeProvider):
        def generate(self, request, **kwargs):
            entered.set()
            released.wait(3)
            return ProviderOutput("late", False)

    job = store.submit("java", submission())
    thread = threading.Thread(target=Runner(store, provider_factory=lambda _: Slow()).run_once)
    thread.start()
    try:
        assert entered.wait(2)
        time.sleep(0.4)
        # lease 원래 길이보다 오래 걸려도 heartbeat 덕에 재claim되지 않는다.
        assert store.claim() is None
        store.cancel("java", "owner-1", job["id"])
    finally:
        released.set()
        thread.join(3)
    assert not thread.is_alive()
    result = store.get("java", "owner-1", job["id"])
    assert result["state"] == "CANCELLED"
    assert result["result"] == ""


def _checkpoint_and_crash(path, job_id, completed, now):
    # 복구 계약은 실제 프로세스로 검증하되 저장 속도가 임대 만료를 결정하지 않게 한다.
    store = Store(path, clock=lambda: now, lease_seconds=0.2, backoff_seconds=0)
    job = store.claim()
    saver = FencedSaver(store, job_id, job["fence"])
    graph = build_graph(saver, FakeProvider(), threading.Event(), 2)
    graph.invoke({"submission": json.loads(job["submission"])},
                 {"configurable": {"thread_id": job_id}}, interrupt_after=[] if completed else ["execute"], durability="sync")
    os._exit(19)


@pytest.mark.parametrize("completed", [False, True])
def test_real_process_crash_recovers_checkpoint_without_repeating_provider(tmp_path, completed):
    now = [100.0]
    store = Store(str(tmp_path / "crash.sqlite3"), clock=lambda: now[0], backoff_seconds=0)
    job = store.submit("java", submission())
    child = multiprocessing.get_context("spawn").Process(target=_checkpoint_and_crash,
                                                          args=(store.path, job["id"], completed, now[0]))
    child.start()
    try:
        child.join(10)
        assert child.exitcode == 19
    finally:
        if child.is_alive():
            child.terminate()
            child.join(3)
    assert store.get("java", "owner-1", job["id"])["state"] == "RUNNING"
    assert store.claim() is None
    now[0] += 0.3
    reopened = Store(store.path, clock=lambda: now[0], backoff_seconds=0)

    class MustNotCall:
        def generate(self, *args, **kwargs):
            pytest.fail("완료된 provider 노드를 재실행함")

    assert Runner(reopened, provider_factory=lambda _: MustNotCall()).run_once()
    result = reopened.get("java", "owner-1", job["id"])
    assert result["state"] == "SUCCEEDED"
    assert result["attempts"] == 2
    assert json.loads(result["result"])["human_review_required"] is True


def test_timeout_is_bounded_and_fences_late_success(tmp_path):
    # 실행 제한은 실제 monotonic 시계로 검사하고 별개인 임대 만료는 고정한다.
    store = Store(str(tmp_path / "timeout.sqlite3"), clock=lambda: 100.0)
    class Slow:
        def generate(self, request, *, cancelled, timeout):
            cancelled.wait(2)
            return ProviderOutput("late", False)

    job = store.submit("java", submission())
    Runner(store, provider_factory=lambda _: Slow(), execution_timeout=0.1).run_once()
    result = store.get("java", "owner-1", job["id"])
    assert result["state"] == "RETRY_WAIT"
    assert result["error"] == "EXECUTION_TIMEOUT"
    assert result["result"] == ""


def test_openai_double_opt_in_and_fixed_nonpersonal_request(monkeypatch):
    with pytest.raises(ValueError, match="EXTERNAL_DISABLED"):
        OpenAIProvider(client=object())
    monkeypatch.setenv("AGENT_OPENAI_ENABLED", "true")
    calls = []
    client = SimpleNamespace(responses=SimpleNamespace(create=lambda **kwargs:
                             (calls.append(kwargs) or SimpleNamespace(output_text="빨강"))))
    provider = OpenAIProvider(client=client)
    result = provider.generate(ProviderInput("brick-colors", "operation", "ledger-ref"),
                               timeout=1, cancelled=threading.Event())
    assert result.external_model_used is True
    assert calls[0]["store"] is False
    assert calls[0]["timeout"] == 1
    assert "ledger-ref" not in str(calls)
    assert "operation" not in str(calls)


def _rpc_process(path, pipe):
    store = Store(path, backoff_seconds=0)
    server, port = create_server(AgentJobsService(store, caller="java", token=TOKEN), 0)
    server.start()
    pipe.send(port)
    command = pipe.recv()
    if command == "crash":
        os._exit(23)
    if command == "execute":
        Runner(store).run_once()
        pipe.send("executed")
        pipe.recv()
    server.stop(None).wait()
    pipe.close()


def test_real_grpc_process_restart_preserves_submission_and_idempotency(tmp_path):
    context = multiprocessing.get_context("spawn")
    path = str(tmp_path / "rpc.sqlite3")

    def start():
        parent, child_pipe = context.Pipe()
        process = context.Process(target=_rpc_process, args=(path, child_pipe))
        process.start()
        assert parent.poll(10)
        return process, parent, parent.recv()

    first, pipe, port = start()
    try:
        with grpc.insecure_channel(f"127.0.0.1:{port}") as channel:
            stub = rpc.AgentJobsStub(channel)
            job = stub.Submit(request(), metadata=METADATA, timeout=3)
        pipe.send("crash")
        first.join(10)
        assert first.exitcode == 23
    finally:
        if first.is_alive():
            first.terminate()
            first.join(3)
        pipe.close()
    second, pipe, port = start()
    try:
        with grpc.insecure_channel(f"127.0.0.1:{port}") as channel:
            stub = rpc.AgentJobsStub(channel)
            repeated = stub.Submit(request(), metadata=METADATA, timeout=3)
            assert repeated.job_id == job.job_id
            assert repeated.state == pb.QUEUED
            pipe.send("execute")
            assert pipe.poll(10) and pipe.recv() == "executed"
            result = stub.Get(pb.JobRequest(owner_id="owner-1", job_id=job.job_id),
                              metadata=METADATA, timeout=3)
            assert result.state == pb.SUCCEEDED
            # 종결 뒤 Cancel은 결과를 덮어쓰지 않는다.
            assert stub.Cancel(pb.JobRequest(owner_id="owner-1", job_id=job.job_id),
                               metadata=METADATA, timeout=3).state == pb.SUCCEEDED
        pipe.send("stop")
        second.join(10)
        assert second.exitcode == 0
    finally:
        if second.is_alive():
            second.terminate()
            second.join(3)
        pipe.close()


def test_exception_text_never_persisted_in_pending_writes(store):
    marker = "PRIVATE-KEY-SHOULD-NEVER-PERSIST"

    class Broken:
        def generate(self, *args, **kwargs):
            raise RuntimeError(marker)

    job = store.submit("java", submission())
    Runner(store, provider_factory=lambda _: Broken()).run_once()
    assert store.get("java", "owner-1", job["id"])["error"] == "PROVIDER_FAILED"
    with store.connection() as db:
        for table in ("jobs", "checkpoints", "writes", "events"):
            assert marker not in repr([tuple(row) for row in db.execute(f"SELECT * FROM {table}")])


def test_repeated_lease_expiry_reaches_terminal_failure(tmp_path):
    now = [100.0]
    store = Store(str(tmp_path / "jobs.db"), clock=lambda: now[0], lease_seconds=1,
                  backoff_seconds=0, max_attempts=2)
    job = store.submit("java", submission())
    assert store.claim()["attempts"] == 1
    now[0] += 2
    assert store.claim()["attempts"] == 2
    now[0] += 2
    assert store.claim() is None
    result = store.get("java", "owner-1", job["id"])
    assert result["state"] == "FAILED"
    assert result["error"] == "LEASE_EXPIRED"


def test_caller_and_owner_scope_are_independent(store):
    from gole_agent_worker.model import NotFound
    first = store.submit("java", submission())
    second = store.submit("other-caller", submission())
    third = store.submit("java", submission(owner="owner-2"))
    assert len({first["id"], second["id"], third["id"]}) == 3
    with pytest.raises(NotFound):
        store.get("other-caller", "owner-1", first["id"])
    claimed = store.claim()
    saver = FencedSaver(store, claimed["id"], claimed["fence"])
    with pytest.raises(LeaseLost):
        saver.get_tuple({"configurable": {"thread_id": "another-job"}})


def test_concurrent_claim_has_one_lease_holder(store):
    store.submit("java", submission())
    with ThreadPoolExecutor(max_workers=8) as pool:
        results = list(pool.map(lambda _: store.claim(), range(8)))
    claims = [result for result in results if result]
    assert len(claims) == 1
    assert claims[0]["attempts"] == claims[0]["fence"] == 1


def test_retry_backoff_increases_and_does_not_run_early(tmp_path):
    now = [100.0]
    store = Store(str(tmp_path / "jobs.db"), clock=lambda: now[0], backoff_seconds=2)
    job = store.submit("java", submission())
    first = store.claim()
    store.fail(job["id"], first["fence"], retryable=True, code="PROVIDER_TRANSIENT")
    now[0] = 101.99
    assert store.claim() is None
    now[0] = 102
    second = store.claim()
    store.fail(job["id"], second["fence"], retryable=True, code="PROVIDER_TRANSIENT")
    now[0] = 105.99
    assert store.claim() is None
    now[0] = 106
    assert store.claim()["attempts"] == 3


def test_external_gate_remains_closed_for_support_even_when_server_opted_in():
    from gole_agent_worker.model import validate
    for enabled, allowed in ((False, True), (True, False)):
        value = Submission("owner", "key", "ref", "synthetic.demo", {"topic": "brick-colors"},
                           "openai", allowed)
        with pytest.raises(ValueError, match="EXTERNAL_DISABLED"):
            validate(value, openai_enabled=enabled)
    validate(Submission("owner", "key", "ref", "synthetic.demo", {"topic": "brick-colors"},
                        "openai", True), openai_enabled=True)
    with pytest.raises(ValueError, match="SUPPORT_EXTERNAL_FORBIDDEN"):
        validate(Submission("owner", "key", "ref", "support.rules", {}, "openai", True),
                 openai_enabled=True)


def test_purge_grpc_removes_all_job_data_and_blocks_resubmission(client, store):
    job = client.Submit(request(), metadata=METADATA)
    Runner(store).run_once()
    purge = pb.PurgeJobRequest(owner_id="owner-1", idempotency_key="request-1")
    receipt = client.Purge(purge, metadata=METADATA)
    assert receipt.receipt_id
    assert client.Purge(purge, metadata=METADATA) == receipt
    with pytest.raises(grpc.RpcError) as error:
        client.Submit(request(), metadata=METADATA)
    assert error.value.code() == grpc.StatusCode.FAILED_PRECONDITION
    with pytest.raises(grpc.RpcError) as error:
        client.Get(pb.JobRequest(owner_id="owner-1", job_id=job.job_id), metadata=METADATA)
    assert error.value.code() == grpc.StatusCode.NOT_FOUND
    with store.connection() as db:
        for table in ("jobs", "checkpoints", "writes", "events"):
            assert db.execute(f"SELECT COUNT(*) FROM {table}").fetchone()[0] == 0
        tombstone = dict(db.execute("SELECT * FROM tombstones").fetchone())
    assert set(tombstone) == {"caller", "owner", "idempotency_key", "receipt_id", "purged_at"}


def test_purge_requires_auth_and_owner_and_survives_reopen(client, store):
    from gole_agent_worker.model import Purged
    client.Submit(request(), metadata=METADATA)
    with pytest.raises(grpc.RpcError) as error:
        client.Purge(pb.PurgeJobRequest(owner_id="other", idempotency_key="request-1"), metadata=METADATA)
    assert error.value.code() == grpc.StatusCode.NOT_FOUND
    with pytest.raises(grpc.RpcError) as error:
        client.Purge(pb.PurgeJobRequest(owner_id="owner-1", idempotency_key="request-1"))
    assert error.value.code() == grpc.StatusCode.UNAUTHENTICATED
    first = store.purge("java", "owner-1", "request-1")
    reopened = Store(store.path)
    assert reopened.purge("java", "owner-1", "request-1") == first
    with pytest.raises(Purged):
        reopened.submit("java", submission())


def test_purge_fences_inflight_completion_and_checkpoint(store):
    from gole_agent_worker.model import NotFound
    entered, released = threading.Event(), threading.Event()

    class Slow:
        def generate(self, request, **kwargs):
            entered.set()
            released.wait(3)
            return ProviderOutput("late", False)

    job = store.submit("java", submission())
    thread = threading.Thread(target=Runner(store, provider_factory=lambda _: Slow()).run_once)
    thread.start()
    try:
        assert entered.wait(2)
        store.purge("java", "owner-1", "request-1")
    finally:
        released.set()
        thread.join(3)
    assert not thread.is_alive()
    with pytest.raises(NotFound):
        store.get("java", "owner-1", job["id"])
    with store.connection() as db:
        for table in ("jobs", "checkpoints", "writes", "events"):
            assert db.execute(f"SELECT COUNT(*) FROM {table}").fetchone()[0] == 0


def test_concurrent_submit_and_purge_never_leave_live_job(store):
    from gole_agent_worker.model import Purged

    def submit_or_purged(_):
        try:
            return store.submit("java", submission())
        except Purged:
            return None

    with ThreadPoolExecutor(max_workers=8) as pool:
        submits = [pool.submit(submit_or_purged, index) for index in range(16)]
        purge = pool.submit(store.purge, "java", "owner-1", "request-1")
        for future in submits:
            future.result()
        assert purge.result()["receipt_id"]
    with pytest.raises(Purged):
        store.submit("java", submission())
    assert store.claim() is None


def test_purge_tombstone_survives_real_grpc_process_restart(tmp_path):
    context = multiprocessing.get_context("spawn")
    path = str(tmp_path / "purge-restart.sqlite3")
    receipt_id = None
    for attempt in range(2):
        parent, child_pipe = context.Pipe()
        process = context.Process(target=_rpc_process, args=(path, child_pipe))
        process.start()
        try:
            assert parent.poll(10)
            port = parent.recv()
            with grpc.insecure_channel(f"127.0.0.1:{port}") as channel:
                stub = rpc.AgentJobsStub(channel)
                purge = pb.PurgeJobRequest(owner_id="owner-1", idempotency_key="request-1")
                if attempt == 0:
                    stub.Submit(request(), metadata=METADATA, timeout=3)
                    receipt_id = stub.Purge(purge, metadata=METADATA, timeout=3).receipt_id
                    parent.send("crash")
                else:
                    with pytest.raises(grpc.RpcError) as error:
                        stub.Submit(request(), metadata=METADATA, timeout=3)
                    assert error.value.code() == grpc.StatusCode.FAILED_PRECONDITION
                    assert stub.Purge(purge, metadata=METADATA, timeout=3).receipt_id == receipt_id
                    parent.send("stop")
            process.join(10)
            assert process.exitcode == (23 if attempt == 0 else 0)
        finally:
            if process.is_alive():
                process.terminate()
                process.join(3)
            parent.close()
