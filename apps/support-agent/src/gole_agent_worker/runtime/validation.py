"""공통 요청 경계를 검증한 뒤 작업별 입력 정책에 위임한다."""

from gole_agent_worker.contracts import Submission, identifier
from gole_agent_worker.runtime.registry import get_agent


def validate(submission: Submission, *, openai_enabled: bool) -> None:
    for value in (submission.owner, submission.key, submission.authorization_ref):
        identifier(value)
    if not isinstance(submission.payload, dict) or len(submission.canonical().encode()) > 12_000:
        raise ValueError("INVALID_PAYLOAD")
    get_agent(submission.kind).validate(submission, openai_enabled=openai_enabled)
