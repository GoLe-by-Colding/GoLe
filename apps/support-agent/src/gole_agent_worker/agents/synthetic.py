"""고정된 비개인정보 입력으로 외부 생성 계약을 검증하는 데모 Brain."""

from gole_agent_worker.agents.contracts import ExecutionContext
from gole_agent_worker.contracts import LeaseLost, PermanentFailure, Submission, TransientFailure
from gole_agent_worker.hands.contracts import Provider, ProviderInput


class SyntheticAgent:
    def validate(self, submission: Submission, *, openai_enabled: bool) -> None:
        if submission.payload != {"topic": "brick-colors"}:
            raise ValueError("INVALID_SYNTHETIC_PAYLOAD")
        if submission.provider not in {"fake", "openai"}:
            raise ValueError("INVALID_PROVIDER")
        if submission.provider == "openai" and not (openai_enabled and submission.allow_external):
            raise ValueError("EXTERNAL_DISABLED")

    def execute(self, submission, provider: Provider, context: ExecutionContext):
        try:
            response = provider.generate(
                ProviderInput(submission["payload"]["topic"], context.job_id + ":generate:v1",
                              submission["authorization_ref"]),
                timeout=context.timeout, cancelled=context.cancelled,
            )
        except LeaseLost:
            raise LeaseLost() from None
        except TransientFailure:
            raise TransientFailure() from None
        except Exception:
            # LangGraph 오류 pending write에 원본 예외·키가 남지 않게 한다.
            raise PermanentFailure() from None
        return {"text": response.text, "external_model_used": response.external_model_used}
