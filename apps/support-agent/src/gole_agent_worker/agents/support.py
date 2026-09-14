"""문의 원문을 외부로 보내지 않는 rules-v1 Brain."""

from gole_agent_worker.agents.contracts import ExecutionContext
from gole_agent_worker.contracts import Submission
from gole_agent_worker.hands.contracts import Provider
from gole_support_agent.agent import analyze_support


class SupportAgent:
    def validate(self, submission: Submission, *, openai_enabled: bool) -> None:
        if submission.provider != "rules" or submission.allow_external:
            raise ValueError("SUPPORT_EXTERNAL_FORBIDDEN")
        payload = submission.payload
        allowed = {"ticket_id", "declared_category", "title", "message", "locale"}
        if set(payload) - allowed:
            raise ValueError("INVALID_PAYLOAD")
        for field, limit in (("ticket_id", 128), ("title", 100), ("message", 2000)):
            value = payload.get(field)
            if not isinstance(value, str) or not value.strip() or len(value) > limit:
                raise ValueError("INVALID_PAYLOAD")
        for field in ("declared_category", "locale"):
            if field in payload and (not isinstance(payload[field], str) or len(payload[field]) > 128):
                raise ValueError("INVALID_PAYLOAD")

    def execute(self, submission, provider: Provider, context: ExecutionContext):
        response = analyze_support(**{"declared_category": "GENERAL", **submission["payload"]})
        return {key: response[key] for key in (
            "recommended_category", "priority", "summary", "draft_reply", "risk_flags",
            "human_review_required", "external_model_used", "engine_version")}
