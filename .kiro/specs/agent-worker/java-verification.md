# Java support 영속 작업 연결 검증

2026-09-13, 미커밋 작업 트리 기준이다. Python 단독 1차 기록은 `verification.md`이며 현재 구현과
잔여 범위는 이 문서가 정본이다. 실행 중 API8090·운영 설정은 변경하지 않았다.

## 구현

- 기존 disabled/동기 Analyze 기본값을 유지하고 durable.enabled opt-in에서 실제 Java
  `SupportAssistantPort`를 Submit/Get 어댑터로 교체했다. Spring bean 및 환경변수 선택을 검증했다.
- Java가 이미 승인·저장한 ticket/requester/최초 문의만 보내며 provider=rules/allow_external=false를
  고정한다. human review, external_model_used=false, rules-v1, job ID, source/owner를 확인한다.
- 전체 deadline 기본2초/상한10초와 25ms poll을 사용한다. 오래된/다른 job/잘못된 결과는 반환하지
  않고 원문·owner가 바뀌면 기존 원격 job을 Cancel한다. 실행 중 pending은 취소하지 않는다.
- 기존 Mongo 방별 최초 문의 원장을 재사용한다. pending은 lease로 보호된 defer로 이번 claim
  횟수를 제외하고 실제 실패/취소는 기존 5회 재시도 정책을 따른다. 만료 lease 완료도 차단했다.
- 원격 접수 전 기존 분석 문서에 remoteCopyPossible을 유효 lease로 표시한다. 이 표식은 완료 뒤에도
  남고 durable 파기 설정을 끈 채 문의 파기를 요청하면 fail-closed한다. 새 원장/quota는 없다.
- 기존 관리자 권한·보존/거래/신고 검사 후 Mongo 연계 삭제 commit 전에 원격 Purge를 확인한다.
  원격 실패는 Mongo 삭제/성공 영수증을 rollback한다. 원격 성공 뒤 Mongo rollback은 로컬 원문을
  보존하고 동일 요청 재시도로 복구하며 분산 원자성을 주장하지 않는다.
- 승인받은 Python/proto 확장으로 Purge와 원문 없는 영속 tombstone을 추가했다. jobs/checkpoints/
  writes/events를 삭제하고 caller/owner/key/receipt/time만 남겨 지연 Submit/완료를 막는다.

## 실제 실행 결과

| 검사 | 통과 | 실패 | 스킵 |
|---|---:|---:|---:|
| Java chat 단위/회귀, fake gRPC, Spring opt-in 조건 | 202 | 0 | 0 |
| 실제 Mongo 통합, lease/defer/표식/파기 rollback | 9 | 0 | 0 |
| Python 전체, 실제 gRPC 프로세스 재시작/tombstone | 50 | 0 | 0 |

Java 결과는 `/tmp/gole-agent-worker-java/test-results/{test,integrationTest}/TEST-*.xml`을 합산했다.
Gradle `--rerun-tasks`로 테스트를 실제 실행했다. 두 Mongo 통합 클래스는 Testcontainers를 사용했고
원격 호출 실패/성공은 파기 port의 테스트 대역으로 제어했다. 실제 네트워크 gRPC는 별도 adapter
테스트와 Python 프로세스 테스트에서 검증했다. Java 앱과 Python 서버를 함께 기동한 운영 E2E를
수행했다는 뜻은 아니다.

```bash
bash apps/support-agent/scripts/generate-proto.sh
uv run --project apps/support-agent pytest apps/support-agent/tests

cd apps/api
./gradlew -I ../../.kiro/specs/agent-worker/java-validation.init.gradle \
  spotlessApply test --tests 'com.gole.api.chat.*' \
  integrationTest --tests '*SupportAssistantAnalysisRecoveryIntegrationTest' \
  --tests '*SupportConversationPurgeIntegrationTest' --rerun-tasks --offline
./gradlew -I ../../.kiro/specs/agent-worker/java-validation.init.gradle spotlessCheck --offline
```

- 마지막 Spotless 검사: `spotlessJava`/`spotlessJavaCheck` 실제 실행, 통과.
- init script는 별도 build 디렉터리와 소유 파일 target만 지정하며 formatter/unused-import 규칙은
  저장소 기본과 같다. 최초 offline 의존성 누락 뒤 coordinator가 formatter 다운로드만 승인했고
  정규 도구를 내려받아 해소했다. pnpm/Python 패키지 설치는 하지 않았다.
- 소유 경로 및 별도 Obsidian 저장소 `git diff --check` 통과.
- 정상 gRPC 테스트 예산은 기본2초로 두고, timeout 테스트만500ms로 분리했다. 정상 테스트의
  300ms 예산이 부하에 걸렸던 실패를 이 방식으로 수정한 뒤 전체202건을 다시 실행했다.

## 주요 회귀 근거

- Java fake gRPC: 인증 metadata, stable owner/key, 중복 재시도 payload, Submit/Get, Cancel,
  실패/취소/잘못된 job/외부모델/인간검토 위반 거절, 늦은 성공 deadline 차단, Purge 실패 예외.
- Spring: 기본 disabled/기존 동기/opt-in bean 단일 선택, 문서화한 환경변수 이름,
  분석 비활성화 뒤 purge 연결 유지.
- 실제 Mongo: 기존 중복 enqueue/claim 및 재시작 lease 회복, stale token과 순수 만료 완료 거절,
  pending defer가 실패 시도 수를 소모하지 않음, 사본 표식의 lease 보호/완료 후 보존,
  원격 실패 rollback, 원격 성공 후 강제 로컬 rollback/동일 요청 재시도,
  durable 파기 설정 해제 시 원문·영수증 보존.
- Python: 실행 중 purge 뒤 late 결과/checkpoint 재생성 차단, 동시 Submit/Purge,
  다른 owner 파기 거절, 동일 파기 영수증 재사용, 실제 서버 프로세스 비정상 종료 후 tombstone 보존.
- 기존 Java 문의 권한/파기/재시도와 Python support rules-v1·brickfilter 회귀 포함.

## 변경 경로

Java는 `chat/adapter/out/assistant/` 신규 durable adapter/config/settings/purge adapter,
기존 Grpc 어댑터 조건, `SupportAssistantPort`, `SupportAssistantPurgePort`, 기존 분석 repository port와
Mongo adapter, 분석/파기 service, 기존 config guard와 대응 chat 테스트를 변경했다.

Python은 `gole_agent_worker/{model,store,server}.py`, `test_durable_worker.py`, README 및 신규
agent proto를 확장했다. `.kiro/specs/agent-worker/`와 본인이 만든 Obsidian 문서 두 편을 갱신했다.
Obsidian 실제 경로는 `/Users/kscold/Desktop/GoLe-obsidian`이며 개발로그/아키텍처 문서에
frontmatter·위키링크·코드 근거와 구현/미구현 구분을 유지했다.

다른 dirty 파일, Java media/Sentry/web/mobile/빌드 설정/lock 파일은 편집하지 않았다.

## 미구현과 잔여 위험

- 운영 적용/배포와 원격 mTLS transport는 미구현이다. Java opt-in은 개발 환경·loopback만 허용한다.
  실제 API8090에 플래그를 켜거나 서버를 재시작하지 않았다.
- brickfilter provider·3회/일 quota ledger 연결은 여전히 미구현이다. support 접수 승인 참조는
  brickfilter 사용량 차감이나 사용자 권한 원장을 대체하지 않는다.
- Python worker가 없는 QUEUED는 Java에서 계속 pending defer할 수 있다. 총 대기 SLA/알림은
  미구현이며 운영에서 원문 없는 작업 상태 관측이 필요하다.
- 원격 성공 후 Mongo commit 실패 시 원격 tombstone은 남고 원본 Mongo는 보존된다. 명시적
  동일 요청 재시도가 필요하며 자동 파기 완료나 2PC는 없다. 이 상태의 원격 분석은 재생성되지 않는다.
- SQLite Purge는 논리 파기다. WAL/free page/과거 snapshot·백업의 물리 바이트 삭제, 볼륨 암호화와
  백업 폐기 정책은 별도다. 임의로 원격 DB를 교체한 경우 과거 DB까지 찾아 삭제하지 않는다.
- 사본 표식이 있는 상태에서 durable 설정 해제는 파기를 거절하도록 보완했다. 분석 중단만 필요하면
  support-agent.enabled=false를 쓰고 기존 durable 연결/인증/DB는 유지해야 한다.
- 외부 호출 물리 취소·비용 exactly-once는 보장하지 않는다. 실제 유료 API 호출, 이미지 빌드,
  운영 데이터/부하/HA 검증은 하지 않았다.
- 테스트용 gRPC/자식 프로세스와 Testcontainers는 테스트 안에서 정리했다. 기존 서버 중단,
  commit/push/브랜치 전환/배포/추가 worker 생성은 하지 않았다.
