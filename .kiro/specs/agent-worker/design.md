# 영속 AI 작업자 설계

## 계약과 보안

`gole.agent.v1.AgentJobs`는 Submit/Get/Cancel/Purge를 제공한다. owner_id는 Java가 검증한 불투명 주체이고 authorization_ref는 Java의 기존 quota/권한 승인 참조다. Python은 사용자를 인증하거나 quota를 차감하지 않는다. 호출자는 `x-gole-caller`와 `authorization: Bearer ...` 메타데이터로 인증하며 서버 환경변수의 caller/token 한 쌍을 상수시간 비교한다. 저장소는 caller+owner로 격리한다. 존재하지 않는 작업과 다른 owner 작업은 동일 NOT_FOUND다.

별도 서버는 127.0.0.1에만 bind한다. 원격 Java 연결에는 별도 인증된 터널 또는 mTLS 프록시가 필요하며 공개인터넷 포트를 만들지 않는다. 기존 support 서버와 계약은 변경하지 않는다.

## 상태와 영속성

SQLite WAL 파일 하나에 jobs, checkpoints, writes, events를 저장한다. 영속 로컬 디스크의 단일 호스트용이며 NFS·다중호스트 공유는 지원하지 않는다. BEGIN IMMEDIATE로 claim, cancel, heartbeat, checkpoint 쓰기, 완료를 직렬화한다. QUEUED → RUNNING → SUCCEEDED/FAILED 또는 RETRY_WAIT → RUNNING이며 모든 비종결 상태에서 CANCELLED로 전이한다. 취소는 즉시 논리 종결하고 이후 결과/체크포인트를 차단한다.

각 claim은 attempts와 fence를 증가시킨다. 모든 실행 쓰기는 RUNNING, fence, lease_until > 현재시각을 한 트랜잭션에서 확인한다. 만료된 RUNNING은 backoff를 거쳐 재시도하며 최대 시도 도달 시 FAILED다. 실행시간 제한과 별도 heartbeat 스레드가 있어 호출이 느려도 유효 lease를 갱신하되 전체 제한 뒤에는 갱신하지 않는다. 재시작 시 같은 DB를 열고 claim 루프를 시작하면 만료 작업을 복구한다.

## Brain / Hands / Session

Brain은 prepare → execute → review의 LangGraph다. support 작업은 기존 analyze_support만 호출한다. 별도 synthetic 작업만 fake/OpenAI provider를 사용할 수 있다. provider 포트는 typed input/output, 안정된 job 기반 operation key, timeout/cancel 경계를 갖는다. brickfilter는 Java 승인 참조를 보존한 새 provider adapter를 연결할 경계만 제공하며 기존 ledger나 이미지 경로는 변경하지 않는다.

Session은 BaseCheckpointSaver를 구현하며 pending writes까지 저장한다. 각 saver는 job/fence에 결박되고 임의 thread 접근을 거절한다. 재시도는 마지막 checkpoint에서 invoke(None)으로 이어 간다. 원문은 작업 payload/checkpoint에만 남고 events에는 상태·시도·고정 오류코드만 남긴다. 보관기간 정책은 운영 연결 전에 결정해야 한다.

완료 checkpoint 뒤 작업 상태 기록 전에 죽으면 완료 graph를 재개해 결과만 기록한다. provider 응답 뒤 checkpoint 전에 죽으면 provider가 재호출될 수 있다. operation key는 adapter의 멱등 실행 연결점이며 외부 비용의 exactly-once 보장은 아니다.

## 검증 근거

LangGraph persistence와 pending writes 계약: https://docs.langchain.com/oss/python/langgraph/persistence 및 설치된 langgraph.checkpoint.base 소스. 실제 DB 재개와 프로세스 종료 테스트로 호환성을 확인한다. OpenAI SDK는 실제 네트워크 대신 가짜 client로 검증한다.

## Java support 연결 구현

`DurableSupportAssistantAdapter`는 기존 `SupportAssistantAnalysisService`의 outbound port로 연결한다.
기본 disabled/동기 Analyze 모드를 유지하며 `gole.support-agent.durable.enabled=true`일 때만 교체한다.
서비스가 이미 Mongo 방별 작업을 claim하므로 별도 Java 원장을 만들지 않는다. 기존 ticket/requester와
최초 문의 source가 일치하는지 Submit 전 및 응답 후 확인한다. provider=rules, allow_external=false를
고정하고 human_review_required=true, external_model_used=false, engine_version=rules-v1를 모두
검증한다. job ID 불일치·원문/owner 변경·deadline 뒤 응답은 저장하지 않는다.

owner는 namespace가 붙은 requester ID의 SHA-256이고 작업 key는 room ID의 SHA-256이다.
기존 Mongo 원장은 방별 최초 문의 한 건이며 후속 메시지 추가나 담당/상태 변경 때 ticket version이
바뀐다. 따라서 version/후속 메시지를 작업 키에 넣지 않는다. 최초 문의 payload가 실제 변경되면 같은
key의 payload 충돌로 거절하며 새 작업을 만들거나 예전 결과를 채택하지 않는다. Python에는 raw room
ID 대신 작업 key를 ticket_id로 보내고 Java 승인 참조도 이 최초 문의 key로 고정한다. 이는 support의
접수 승인 경계이며 brickfilter quota 승인/원장을 대체하지 않는다.

한 analyze 호출의 전체 gRPC deadline은 기본 2초, 상한 10초로 Java 30초 lease보다 짧다.
원격 pending을 확인한 뒤 예산이 끝나면 AnalysisPendingException으로 구분하고 Mongo의 기존 작업을
`defer`한다. 같은 lease token/아직 유효한 lease에서 이번 claim의 attempts만 1 차감한다. 따라서 정상
poll은 실패 횟수를 소비하지 않고 실제 실패/취소는 기존 5회 실패 정책을 따른다. 완료 쓰기에도
leaseUntil > completedAt을 추가해 순수 lease 만료 뒤 응답도 막는다.

### 연계 파기와 분산 실패

기존 관리자 권한·정확한 확인값·보존 검토·분쟁/거래 검사를 먼저 통과한다. Mongo 트랜잭션 안에서
기존 연계 삭제와 영수증을 작성한 후 commit 전에 원격 Purge(owner,key)를 호출한다. 원격 실패/잘못된
영수증은 예외로 반환하여 Mongo 전체를 rollback하며 파기 성공으로 보고하지 않는다.

Python은 BEGIN IMMEDIATE 안에서 jobs/checkpoints/writes/events를 삭제하고 caller/owner/key,
receipt UUID, 시각만 tombstones에 남긴다. 같은 요청은 같은 영수증을 반환하고 Submit은
FAILED_PRECONDITION으로 거절한다. 실행 중 saver/heartbeat/완료는 job이 없어졌으므로 쓰기를 못 한다.
동시 Submit과 파기는 직렬화하며 아직 Submit이 없더라도 tombstone을 먼저 남겨 지연 요청을 막는다.

원격 성공 뒤 Mongo commit/상위 트랜잭션이 실패할 수 있다. 이 경우 Python tombstone은 유지되지만
원본 Mongo 문의와 기존 Java 분석은 rollback으로 남는다. 자동으로 문의 파기를 완료하지 않으며
관리자가 같은 요청을 재시도하면 같은 원격 영수증으로 로컬 파기를 다시 끝낼 수 있다. 이는 2PC가
아니며 원격 사본 파기와 로컬 파기가 원자적이라고 주장하지 않는다. tombstone이 남은 문의를 다시
분석할 수 없다는 제한도 있다.

### 설정과 운영 한계

`durable.enabled`는 원격 사본 보유/파기 연결 설정이다. 한번 사용한 뒤 사본/tombstone을 관리하는 동안
유지해야 하며 분석만 중단하려면 기존 `support-agent.enabled=false`를 쓴다. 이 경우에도 purge port는
유지된다. 사본 표식이 있으면 설정 해제를 감지해 파기를 거절한다. 다만 원격 DB를 임의 교체했을 때 과거 DB까지 찾아 파기하지는 않는다.

현재 Java opt-in은 local/development/dev/test/e2e 환경과 127.0.0.1만 허용한다. 운영 transport/배포는
미적용이다. Python 실행 시도는 bounded지만 QUEUED에서 worker 자체가 없으면 Java pending poll은
계속 defer할 수 있다. 별도 최대 총 대기 SLA·운영 경보는 미구현이며 원문 없는 작업 상태를 관측해야 한다.

Purge는 SQL 조회/재생성 경계의 논리 파기다. WAL, free page, 과거 볼륨 snapshot/백업에서의 물리 바이트
삭제를 보장하지 않는다. 영속 볼륨 암호화/백업 폐기/보관 정책은 운영 적용 전에 따로 정해야 한다.


원격 사본 보유 여부는 기존 Java 분석 문서의 `remoteCopyPossible` 표식으로 보존한다. 유효한
lease/token으로 표식을 기록한 뒤에만 원격 Submit을 허용하며, 완료·retry/defer 뒤에도 표식은 남는다.
분석만 비활성화하면 원격 purge 연결을 유지하고, durable 설정까지 해제했는데 이 표식이 있으면
문의 파기를 fail-closed로 거절한다. 따라서 설정 해제로 과거 원격 사본 파기가 조용히 누락되지 않는다.
표식 없는 기존 동기 작업은 기존 파기 흐름을 유지한다. 이 표식은 새 원장이나 quota 차감이 아니다.
