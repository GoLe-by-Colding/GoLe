# 영속 AI 작업자 1차 검증 기록

이 문서는 Python 단독 구현 시점의 기록이다. Java 연결 후속 최종 결과는 `java-verification.md`를 따른다.

2026-09-12, 미커밋 로컬 작업 트리에서 실행했다. 구현 범위는 Python 영속 작업자와 신규 proto이며
Java 연결/운영 적용 완료를 뜻하지 않는다.

## 실행 결과

| 명령/검증 | 결과 |
|---|---|
| `bash apps/support-agent/scripts/generate-proto.sh` | support 및 agent Python gRPC stub 생성 성공 |
| `uv run --project apps/support-agent pytest apps/support-agent/tests` | **45 passed, 0 failed, 0 skipped**, 4.05초 |
| `uv run --project apps/support-agent python -m compileall -q apps/support-agent/src/gole_agent_worker` | 통과 |
| 소유 경로 `git diff --check` | 통과 |

기존 support/rules-v1 및 brickfilter 테스트를 포함한 결과다. 새 테스트는
`apps/support-agent/tests/test_durable_worker.py`이며 실제 유료 API를 호출하지 않는다.

- 실제 localhost gRPC: 동시 중복 Submit, 정규화 JSON, 다른 payload 충돌, owner/caller 격리,
  인증 선행 검사, 미승인 외부 사용/잘못된 입력 거절, Get/Cancel/종결 결과 보존.
- 실제 프로세스: Submit한 서버를 비정상 종료한 뒤 새 프로세스에서 동일 DB를 열어 같은
  idempotency key가 같은 job을 반환하고 실행/조회되는 것을 확인.
- 실제 checkpoint 복구: provider 실행 checkpoint 직후 또는 graph 최종 checkpoint 직후
  자식 프로세스를 `os._exit`로 비정상 종료. 새 Store/Runner가 완료 provider를 다시 부르지
  않고 마지막 상태에서 이어 실행하고 SUCCEEDED로 종결.
- lease 만료 후 새 fence를 획득하면 이전 heartbeat/완료/checkpoint/pending write 모두 거절.
- 동시 claim 단일 실행, heartbeat로 lease 유지, 반복 lease 만료 최대 시도 종료.
- transient 실패 재시도와 checkpoint 재개, 2초/4초 backoff와 조기 claim 거절,
  permanent 실패 즉시 종결, 실행 timeout 뒤 늦은 결과 차단, running 취소.
- provider 오류 원문이 jobs/checkpoints/writes/events에 남지 않음.
- OpenAI adapter는 fake client에서 고정 비개인정보 요청/store=False/timeout 및 이중 opt-in 검증.

## 변경 소유권

- 신규 `apps/api/src/main/proto/gole/agent/v1/agent_jobs.proto`.
- 신규 `apps/support-agent/src/gole_agent_worker/`: model, hands, store, session, brain, runner, server.
- 신규 `apps/support-agent/tests/test_durable_worker.py`, `apps/support-agent/.gitignore`.
- 수정 `apps/support-agent/scripts/generate-proto.sh`, `Dockerfile`, `README.md`.
- 신규 `.kiro/specs/agent-worker/{requirements,design,tasks,verification}.md`.
- 기존 `pyproject.toml`, `uv.lock`, brickfilter Python 코드의 미커밋 변경은 이 작업에서 편집하지 않았다.
- Java 앱/media/Sentry/web/RN/root lock/다른 작업의 미커밋 파일은 편집하지 않았다.

## 별도 문서 저장소

실제 위치 `/Users/kscold/Desktop/GoLe-obsidian`를 확인했다. 이 checkout의 origin은
`https://github.com/GoLe-by-Colding/GoLe-obsidian.git`이며 root 안내의 조직명과 다르다.
기존 frontmatter/위키링크 형식을 읽고 다음 신규 문서만 작성했다.

- `02_아키텍처/영속 AI 작업자.md`
- `06_개발로그/2026-09-12_영속 AI 작업자를 구현함.md`

## 미완료/잔여 위험

- Java의 사용자 인증·권한·quota 승인 → Submit/Get/Cancel 실제 연결은 미구현이다.
  authorization_ref는 신뢰된 Java 호출자가 제공하는 참조이며 Python이 별도 승인 원장을 만들지 않는다.
- brickfilter의 기존 3회/일 ledger 및 이미지 provider를 새 작업자에 연결하지 않았다.
  typed ProviderInput에 승인 참조/operation key를 보존하는 연결점만 있다.
- 기존 Analyze/rules-v1 및 원문 외부전송 금지/인간검토 필수 정책은 유지했다.
- 서버는 loopback에만 bind한다. 원격 Java 연결/mTLS, 운영 적용·배포·볼륨·암호화·백업·보관/삭제
  정책·부하/HA 테스트는 하지 않았다. SQLite는 단일 호스트 로컬 디스크용이다.
- 취소/timeout은 논리 상태와 이후 쓰기를 차단한다. 진행 중 HTTP 호출은 SDK timeout까지
  남을 수 있고 timeout/cancel을 무시하는 임의 provider의 스레드 강제 종료는 제공하지 않는다.
- 외부 응답과 checkpoint 사이 crash에는 재호출 가능성이 있어 유료 호출 exactly-once를
  보장하지 않는다. 실제 OpenAI 유료 호출과 컨테이너 이미지 빌드는 실행하지 않았다.
- 테스트 자식 프로세스/서버는 테스트 내에서 종료했다. 개발 서버를 띄우거나 기존 서버를
  중단하지 않았으며 commit/push/배포/추가 worker 생성도 하지 않았다.
