# GoLe Support Agent

문의와 피드백을 gRPC로 받아 LangGraph에서 분류하고 관리자 답변 초안을 만드는 내부 서비스다.
현재 그래프는 외부 모델을 호출하지 않는 결정론적 `rules-v1`이며 다음 원칙을 강제한다.

- 문의 원문·제목·사용자 식별자를 로그나 Discord로 보내지 않음
- 결제·개인정보·긴급 위험 문의는 사람 검토 우선순위를 높임
- 모든 답변은 관리자 초안이며 자동 발송·자동 해결하지 않음
- 외부 모델 사용 여부를 응답에 명시하고 현재는 항상 `false`임

```bash
uv sync --project apps/support-agent
bash apps/support-agent/scripts/generate-proto.sh
(cd apps/support-agent && uv run pytest)
```

gRPC 계약은 `apps/api/src/main/proto/gole/support/v1/support_agent.proto` 한 곳을 Java와 Python이
공유한다. 외부 LLM은 처리업체·리전·보관정책을 개인정보처리방침에 먼저 고지한 뒤 별도
기능 플래그와 계약 테스트를 추가하기 전까지 연결하지 않는다.
