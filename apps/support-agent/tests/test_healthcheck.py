from concurrent import futures
import threading

import grpc
import pytest
from grpc_health.v1 import health, health_pb2, health_pb2_grpc

from gole_support_agent.healthcheck import check


@pytest.fixture
def health_server(monkeypatch):
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=1))
    port = server.add_insecure_port("127.0.0.1:0")
    monkeypatch.setenv("SUPPORT_AGENT_HEALTH_TARGET", f"127.0.0.1:{port}")
    monkeypatch.delenv("SUPPORT_AGENT_HEALTH_TIMEOUT_SECONDS", raising=False)
    yield server
    server.stop(None).wait()


def test_accepts_serving(health_server):
    service = health.HealthServicer()
    service.set("gole.support.v1.SupportAgent", health_pb2.HealthCheckResponse.SERVING)
    health_pb2_grpc.add_HealthServicer_to_server(service, health_server)
    health_server.start()
    check()


def test_rejects_not_serving(health_server):
    service = health.HealthServicer()
    service.set("gole.support.v1.SupportAgent", health_pb2.HealthCheckResponse.NOT_SERVING)
    health_pb2_grpc.add_HealthServicer_to_server(service, health_server)
    health_server.start()
    with pytest.raises(SystemExit) as failure:
        check()
    assert failure.value.code == 1


def test_rejects_unregistered_service(health_server):
    health_pb2_grpc.add_HealthServicer_to_server(health.HealthServicer(), health_server)
    health_server.start()
    with pytest.raises(grpc.RpcError) as failure:
        check()
    assert failure.value.code() == grpc.StatusCode.NOT_FOUND


def test_unresponsive_server_reaches_rpc_deadline(health_server):
    release = threading.Event()

    class UnresponsiveHealth(health_pb2_grpc.HealthServicer):
        def Check(self, request, context):  # noqa: N802 - gRPC 계약
            release.wait(10)
            return health_pb2.HealthCheckResponse(status=health_pb2.HealthCheckResponse.SERVING)

    health_pb2_grpc.add_HealthServicer_to_server(UnresponsiveHealth(), health_server)
    health_server.start()
    try:
        with pytest.raises(grpc.RpcError) as failure:
            check()
        assert failure.value.code() == grpc.StatusCode.DEADLINE_EXCEEDED
    finally:
        release.set()
