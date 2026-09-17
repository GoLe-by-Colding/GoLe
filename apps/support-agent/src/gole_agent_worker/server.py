"""기존 gRPC import 및 python -m gole_agent_worker.server 실행 호환 진입점."""

from gole_agent_worker.bootstrap import serve
from gole_agent_worker.entrypoints.grpc import AgentJobsService, create_server, response

__all__ = ["AgentJobsService", "create_server", "response", "serve"]


if __name__ == "__main__":
    serve()
