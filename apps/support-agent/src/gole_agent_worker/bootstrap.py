"""환경 설정을 읽고 내부 gRPC 서버와 실행기를 조립한다."""

import os
import signal
import threading
from pathlib import Path

from gole_agent_worker.entrypoints.grpc import AgentJobsService, create_server
from gole_agent_worker.runtime.runner import Runner
from gole_agent_worker.runtime.store import Store
from gole_agent_runtime.privacy import reject_external_tracing

DEFAULT_DB_PATH = Path(__file__).resolve().parents[2] / "data/agent-jobs.sqlite3"


def serve():
    # trace exporter가 문의 원문을 외부로 전송하는 우회 경로가 되지 않도록 차단한다.
    reject_external_tracing()
    store = Store(os.environ.get("AGENT_DB_PATH", str(DEFAULT_DB_PATH)))
    service = AgentJobsService(store, caller=os.environ.get("AGENT_INTERNAL_CALLER", ""),
                               token=os.environ.get("AGENT_INTERNAL_TOKEN", ""),
                               openai_enabled=os.environ.get("AGENT_OPENAI_ENABLED") == "true")
    server, _ = create_server(service, int(os.environ.get("AGENT_GRPC_PORT", "50052")))
    stop = threading.Event()
    runner = threading.Thread(target=Runner(store).run, args=(stop,), daemon=True)

    def shutdown(*_):
        stop.set()
        server.stop(5)

    signal.signal(signal.SIGTERM, shutdown)
    signal.signal(signal.SIGINT, shutdown)
    server.start()
    runner.start()
    server.wait_for_termination()
    stop.set()
    runner.join(timeout=5)
