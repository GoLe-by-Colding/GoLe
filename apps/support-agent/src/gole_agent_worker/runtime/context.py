"""Brain에 DB 대신 작업 실행권 확인 기능을 제공한다."""

import threading
from dataclasses import dataclass

from gole_agent_worker.contracts import LeaseLost
from gole_agent_worker.runtime.store import Store


@dataclass(frozen=True)
class JobExecutionContext:
    store: Store
    job_id: str
    fence: int
    cancelled: threading.Event
    timeout: float

    def check_active(self) -> None:
        if self.cancelled.is_set():
            raise LeaseLost()
        with self.store.transaction() as db:
            self.store.assert_lease(db, self.job_id, self.fence)
