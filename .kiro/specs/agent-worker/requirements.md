# 영속 AI 작업자 요구사항

- 기존 support Analyze 계약과 rules-v1 결과, 원문 외부전송 금지, 인간검토 필수를 유지한다.
- 별도 내부 Submit/Get/Cancel RPC로 작업을 접수하고 프로세스 재시작 후 복구한다.
- 내부 호출자 인증을 먼저 검사하고 owner와 job 경계를 강제한다. 사용자 인증·권한·일일 quota와 brickfilter 3회/일 ledger는 Java 책임이다.
- owner별 idempotency key가 같고 정규화한 payload가 같으면 같은 작업을 반환한다. 내용이 다르면 ALREADY_EXISTS로 거절한다.
- 상태와 checkpoint를 영속화한다. lease 획득마다 fencing token을 증가시키고 heartbeat·checkpoint·완료에 유효 lease/token을 요구한다.
- 재시도 횟수·지수 backoff·실행시간을 제한하며 취소와 lease 만료 복구를 지원한다. 외부 부수효과 exactly-once는 보장하지 않는다.
- Brain(graph), Hands(typed provider port), Session(checkpoint·원문 없는 최소 이벤트)를 분리한다.
- LangSmith의 네 가지 tracing 환경변수 별칭을 모두 검사하고, 실제 실행 경계에서 tracing과 상속 callback을 끈다. 사진·문의 원문을 관측 exporter로 보내지 않는다.
- 기본은 rules/fake이며 OpenAI는 서버 opt-in 및 요청 opt-in을 모두 요구한다. 키는 환경변수만 읽고 출력하지 않는다. support 원문은 opt-in과 무관하게 외부로 보내지 않는다.
- fake provider만으로 실제 gRPC, 재시작, 중복/충돌, stale 완료/checkpoint, 취소, 실패를 검증한다.
- 접수부터 완료까지 기본 300초의 전체 예산을 적용한다. 만료 시각은 DB에 저장해 재시작이나 설정 변경으로 연장하지 않으며, 실행 워커가 없어도 Get/재접수에서 만료를 확정한다.
- 만료 작업은 JOB_DEADLINE_EXCEEDED로 실패하고 같은 키로 다시 실행하지 않는다. 만료 뒤 heartbeat·checkpoint·완료를 차단한다.
- Java support 연결은 아래 후속 범위로 확장했다. 운영 배포, 공개 endpoint, 사용자 quota 구현은 범위 밖이다.

## Java support 연결 후속 요구사항

- 기본 동기 Analyze 어댑터와 disabled 기본값을 유지하고 별도 durable.enabled opt-in으로 교체한다.
- 기존 Java 방별 원장·5회 재시도·30초 lease를 재사용하고 새 quota/분석 원장을 만들지 않는다.
- Submit은 검증한 support 문의와 요청자 owner에서만 만들고 provider=rules, allow_external=false를 고정한다.
- 전체 호출 예산을 Java lease보다 짧게 제한하고 다른 job/잘못된 rules-v1/인간검토 누락 결과를 거절한다.
- 실패/취소/일시적 timeout은 기존 Java 재시도 흐름으로 반환한다. 파기되거나 변경된 원문 결과를 저장하지 않는다.
- Python 원문·checkpoint가 Java 문의 파기 뒤 남지 않도록 계약/연계 파기 경계를 먼저 해결한다.

- 승인된 문의 파기는 내부 Purge RPC의 원문 없는 영속 tombstone으로 지연 Submit/완료/checkpoint를 차단하고, 원격 실패 시 Java Mongo 파기를 rollback한다.
- 정상 pending poll은 기존 Java 작업의 실패 시도 수를 소비하지 않는다. 실제 실패와 취소는 기존 5회 재시도 정책을 유지한다.
