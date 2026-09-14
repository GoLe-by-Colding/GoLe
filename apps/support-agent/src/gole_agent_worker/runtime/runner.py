from __future__ import annotations

import json
import threading
import time

from gole_agent_worker.runtime.context import JobExecutionContext
from gole_agent_worker.runtime.graph import build_graph
from gole_agent_worker.hands import FakeProvider, OpenAIProvider
from gole_agent_worker.contracts import LeaseLost, TransientFailure
from gole_agent_worker.session import FencedSaver
from gole_agent_worker.runtime.store import Store


class Runner:
    def __init__(self, store: Store, *, provider_factory=None, execution_timeout=60.0):
        if execution_timeout <= 0:
            raise ValueError("INVALID_EXECUTION_TIMEOUT")
        self.store = store
        self.execution_timeout = execution_timeout
        self.provider_factory = provider_factory or (
            lambda name: OpenAIProvider() if name == "openai" else FakeProvider())

    def run_once(self):
        job = self.store.claim()
        if job is None:
            return False
        self.run_claimed(job)
        return True

    def run_claimed(self, job):
        cancelled = threading.Event()
        finished = threading.Event()
        deadline = time.monotonic() + self.execution_timeout

        def heartbeat():
            while not finished.wait(min(self.store.lease_seconds / 3, self.execution_timeout / 3)):
                try:
                    if time.monotonic() >= deadline:
                        self.store.fail(job["id"], job["fence"], retryable=True, code="EXECUTION_TIMEOUT")
                        cancelled.set()
                        return
                    self.store.heartbeat(job["id"], job["fence"])
                except Exception:
                    cancelled.set()
                    return

        keeper = threading.Thread(target=heartbeat, daemon=True)
        keeper.start()
        try:
            submission = json.loads(job["submission"])
            saver = FencedSaver(self.store, job["id"], job["fence"])
            context = JobExecutionContext(self.store, job["id"], job["fence"], cancelled,
                                          self.execution_timeout)
            graph = build_graph(saver, self.provider_factory(submission["provider"]), context)
            config = {"configurable": {"thread_id": job["id"]}, "recursion_limit": 8}
            existing = saver.get_tuple(config)
            result = graph.invoke(None if existing else {"submission": submission}, config,
                                  durability="sync")
            if time.monotonic() >= deadline:
                self.store.fail(job["id"], job["fence"], retryable=True, code="EXECUTION_TIMEOUT")
            else:
                self.store.finish(job["id"], job["fence"], result["result"])
        except LeaseLost:
            pass
        except Exception as error:
            # 예외 본문에는 provider 키/원문이 있을 수 있어 저장하거나 로깅하지 않는다.
            try:
                transient = isinstance(error, TransientFailure)
                self.store.fail(job["id"], job["fence"], retryable=transient,
                                code="PROVIDER_TRANSIENT" if transient else "PROVIDER_FAILED")
            except LeaseLost:
                pass
        finally:
            finished.set()
            cancelled.set()
            keeper.join(timeout=2)

    def run(self, stop: threading.Event):
        while not stop.is_set():
            if not self.run_once():
                stop.wait(0.1)
