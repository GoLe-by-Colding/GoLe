from __future__ import annotations

import os

import grpc
from grpc_health.v1 import health_pb2, health_pb2_grpc


def check() -> None:
    target = os.environ.get("SUPPORT_AGENT_HEALTH_TARGET", "127.0.0.1:50051")
    timeout = float(os.environ.get("SUPPORT_AGENT_HEALTH_TIMEOUT_SECONDS", "2"))
    with grpc.insecure_channel(target) as channel:
        response = health_pb2_grpc.HealthStub(channel).Check(
            health_pb2.HealthCheckRequest(service="gole.support.v1.SupportAgent"),
            timeout=timeout,
        )
    if response.status != health_pb2.HealthCheckResponse.SERVING:
        raise SystemExit(1)


if __name__ == "__main__":
    check()
