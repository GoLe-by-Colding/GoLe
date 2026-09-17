"""전송 프로토콜과 독립적인 작업 접수·조회·취소·파기."""

from gole_agent_worker.contracts import Submission, identifier
from gole_agent_worker.runtime.store import Store
from gole_agent_worker.runtime.validation import validate


class JobService:
    def __init__(self, store: Store, *, openai_enabled: bool = False):
        self.store = store
        self.openai_enabled = openai_enabled

    def submit(self, caller: str, submission: Submission):
        validate(submission, openai_enabled=self.openai_enabled)
        return self.store.submit(caller, submission)

    def get(self, caller: str, owner: str, job_id: str):
        return self.store.get(caller, identifier(owner), identifier(job_id))

    def cancel(self, caller: str, owner: str, job_id: str):
        return self.store.cancel(caller, identifier(owner), identifier(job_id))

    def purge(self, caller: str, owner: str, key: str):
        return self.store.purge(caller, identifier(owner), identifier(key))
