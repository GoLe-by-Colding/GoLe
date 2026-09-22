# GoLe Support Agent

> **이 패키지는 이름보다 넓다.** 문의(`gole_support_agent`)·사진(`gole_brick_filter`)·
> 홍보(`gole_promotion_agent`) 세 에이전트가 들어 있다. 공유하는 것은 구조 관용구
> (Brain/Hands/Session)와 관측 격리(`gole_agent_runtime`)이지 프로세스·이미지·자원 한도가
> 아니다. 디렉터리 이름을 아직 바꾸지 않은 것은 Dockerfile·compose·CI 경로가 여기에 묶여
> 있어서다 — 바꾼다면 그 배선을 함께 옮겨야 한다.

문의와 피드백을 gRPC로 받아 LangGraph에서 분류하고 관리자 답변 초안을 만드는 내부 서비스다.
현재 그래프는 외부 모델을 호출하지 않는 결정론적 `rules-v1`이며 다음 원칙을 강제한다.

- 문의 원문·제목·사용자 식별자를 로그나 Discord로 보내지 않음
- 결제·개인정보·긴급 위험 문의는 사람 검토 우선순위를 높임
- 모든 답변은 관리자 초안이며 자동 발송·자동 해결하지 않음
- 외부 모델 사용 여부를 응답에 명시하고 현재는 항상 `false`임

## 영속 작업자의 코드 구조와 읽는 순서

> **⏸ 보류 중 — 2026-09-22 기준 이 패키지를 쓰는 소비자가 0개다.**
>
> 지우지도 켜지도 않기로 했다. 버리기엔 만든 지 얼마 안 됐고(커밋 3개, 마지막 2026-09-14),
> 켜기엔 저장소·보관기간·경보 정책을 먼저 세워야 하는데 **쓸 사람이 없는 상태에서 그걸
> 정하는 건 순서가 뒤집힌 것**이라 판단했다.
>
> - **운영 이미지에 싣지 않는다.** `Dockerfile`이 `gole_support_agent`·`gole_agent_runtime`만
>   복사한다. 돌지 않는 코드로 운영 컨테이너 표면을 넓히지 않기 위해서다.
> - **소스·테스트·스펙은 그대로 둔다.** CI 가 매 PR 마다 전부 돌린다 — 보류는 검증을 멈추는
>   것이 아니다. 깨울 때는 `Dockerfile`의 `COPY`와 배선만 되돌린다.
> - **깨우는 조건**: ① ~~홍보 트리거가 관리자 콘솔 발 요청으로 바뀔 때~~ → 2026-09-22 에
>   왔으나 재검토 결과 편입하지 않음(D20). 다시 볼 지점은 T12(온디맨드 기동)
>   ② brickfilter 3회/일 ledger 를 영속 작업과 연결할 때(아래 "내부 호출 계약" 참고)
>   ③ 둘 다 없이 결정 기한이 지나면 제거 재검토.
> - 판단 근거와 기한은 볼트 `08_개선과제/알려진 개선 과제.md`, 코드 쪽 요약은
>   `src/gole_agent_worker/__init__.py`.
>
> 아래 구조 설명은 보류와 무관하게 유효하다.

`src/gole_agent_worker/`는 여러 목적의 에이전트가 공유하도록 **설계된** 실행 기반이다.
다만 사진·홍보는 편입을 명시적으로 거부했고(각각 이미지의 SQLite 영속 금지, 분산 leasing
불필요), 문의는 동기 서버로 충분해 **실제 공유 사용자는 아직 없다.**
기존 `gole_support_agent/` 동기 서버와 `gole_brick_filter/` 이미지 서비스는 별도 진입점을 유지한다.

```text
gole_agent_worker/
├─ contracts.py          # 작업 입력·공통 오류, 외부 라이브러리 의존 없음
├─ agents/               # support·synthetic Brain과 작업별 입력 정책
├─ hands/                # Provider 계약과 fake/OpenAI 구현
├─ runtime/              # 접수·registry·고정 순서 그래프·실행권·재시도·작업 저장
├─ session/              # 작업별 체크포인트와 pending writes
├─ entrypoints/grpc.py   # 내부 인증·요청 변환·gRPC 오류 매핑
└─ bootstrap.py          # 환경 설정·서버와 실행기 조립
```

요청 하나를 따라 읽을 때는 다음 순서로 본다.

1. `entrypoints/grpc.py`의 `AgentJobsService.Submit`: 인증하고 요청을 `Submission`으로 변환한다.
2. `runtime/jobs.py`의 `JobService.submit`: 공통·작업별 입력 정책을 검증한다.
3. `runtime/store.py`의 `Store.submit`: 중복을 확인하고 `QUEUED` 작업을 저장한다. 접수는 여기서 응답한다.
4. 별도 실행 루프인 `runtime/runner.py`의 `Runner.run_once`: 작업 실행권을 얻고 `run_claimed`로 넘긴다.
5. `runtime/graph.py`: `prepare → execute → review`를 실행하고 `agents/`의 작업별 Brain에 위임한다.
6. `session/checkpoints.py`: 단계 사이 상태를 저장한다. 그래프 완료 후 Runner가 결과를 작업에 저장한다.

Brain은 `ExecutionContext.check_active()`로 실행 가능 여부를 확인하는 흐름 안에서 동작한다.
DB 트랜잭션과 lease 검사는 Runtime이, 체크포인트 쓰기의 fencing은 Session이 책임진다.
Session은 한 작업의 복구를 위한 것으로 콘텐츠 발행 이력·장기 기억 저장소가 아니다.

**새 에이전트를 어디에 붙일지는 기동 방식이 정한다.** 이 문서 안에 규칙이 둘 있어 보이는데,
서로 다른 것을 말한다.

- **Java 가 gRPC 로 접수하는 작업**이면 `agents/`에 `Agent` 계약(입력 검증 + 실행)을 구현하고
  `runtime/registry.py`에 명시적으로 등록한다. lease·fence·재시도·재개를 공통 실행 계층이 준다.
- **스스로 깨어나는 일회성 배치**면 독립 모듈로 세운다(`gole_promotion_agent` 가 그 예다).
  워커 하나가 프로세스 하나를 통째로 쓰므로 분산 조정이 필요 없고, SQLite 잡 큐를 끌고 오면
  쓰지 않을 lease·fence까지 함께 유지해야 한다.

어느 쪽이든 **구조 관용구(Brain/Hands/Policy/Ports/Runtime/Session)와 관측 격리
(`gole_agent_runtime`)는 공유한다.** 공유하지 않는 것은 프로세스·이미지·자원 한도다.
필요한 외부 기능은 Hands의 계약 뒤에 둔다. 현재 공통 그래프는 v1 고정 순서이며 목표 기반
플래너는 아직 없다. 다른 단계·상태가 필요해지면 흐름 버전과 이전 작업의 재개 정책부터 정한다.

기존 `brain/model/runner/store/server` import는 얇은 호환 진입점으로 남긴다.
`hands`와 `session`도 기존 공개 이름을 재노출한다. 실행 명령·기본 DB 경로·gRPC·DB 스키마와
기존 체크포인트의 노드 이름·상태 형식은 바뀌지 않았다.
설계·검증 근거는 [구조 개편 스펙](../../.kiro/specs/agent-worker-structure/design.md)을 참고한다.

```bash
uv sync --project apps/support-agent
bash apps/support-agent/scripts/generate-proto.sh
(cd apps/support-agent && uv run pytest)
```

gRPC 계약은 `apps/api/src/main/proto/gole/support/v1/support_agent.proto` 한 곳을 Java와 Python이
공유한다. 외부 LLM은 처리업체·리전·보관정책을 개인정보처리방침에 먼저 고지한 뒤 별도
기능 플래그와 계약 테스트를 추가하기 전까지 연결하지 않는다.

## 별도 영속 작업자

기존 `gole_support_agent.server`와 Analyze RPC는 그대로다. 새
`gole_agent_worker.server`는 `gole.agent.v1.AgentJobs`의 Submit/Get/Cancel/Purge를 제공하는
별도 프로세스다. Python 사용자용 공개 API는 만들지 않았다.

### 실행과 저장소

위 proto 생성 명령을 먼저 실행한다. 실행 환경에 `AGENT_INTERNAL_CALLER`와
`AGENT_INTERNAL_TOKEN`(최소 32자)을 안전하게 주입한 뒤 다음 명령을 Orca 터미널에서 실행한다.
키와 토큰의 값은 명령 이력·로그·문서에 넣지 않는다.

```bash
PYTHONPATH=apps/support-agent/src:apps/support-agent/generated \
  uv run --project apps/support-agent python -m gole_agent_worker.server
```

- `AGENT_GRPC_PORT`: 기본 50052, **127.0.0.1에만 bind**한다. 원격 Java 연결과 mTLS 프록시는 미구현이다.
  포트 배분은 아래 "loopback 포트 배분"을 본다 — 이 패키지의 서버 넷이 같은 호스트를 나눠 쓴다.
- `AGENT_DB_PATH`: 기본 패키지 루트의 `apps/support-agent/data/agent-jobs.sqlite3`(이미지 안에서는 `/app/data/agent-jobs.sqlite3`). 재시작 때 반드시 같은 파일을 쓴다.
  컨테이너 재생성까지 보존하려면 영속 로컬 볼륨 경로를 명시해야 한다.
- 기본 시도 3회, lease 15초, heartbeat 5초, 재시도 2초/4초(상한 60초), 실행 제한 60초다.
  `Store`/`Runner` 생성자에서 테스트할 수 있으며 외부 요청은 이 제한을 변경할 수 없다.
- SQLite WAL 단일 호스트용이다. 여러 프로세스의 동일 로컬 파일 claim은 직렬화하지만
  다중 호스트/NFS 공유 또는 운영 고가용성 저장소를 제공하지 않는다.
- DB는 0600으로 생성한다. payload/checkpoint에 문의 내용이 있으므로 디렉터리 접근권한,
  볼륨 암호화·백업·보관기간·삭제 절차는 운영 연결 전에 정해야 한다. 내부 Purge는 논리 파기를 제공하며 WAL/백업 물리 삭제는 별도 정책이 필요하다.
- `LANGCHAIN_TRACING`, `LANGCHAIN_TRACING_V2`, `LANGSMITH_TRACING`, `LANGSMITH_TRACING_V2` 중 하나라도 `true`면 기동을 거절한다.

### loopback 포트 배분

이 패키지의 서버는 넷이고 전부 같은 호스트의 loopback 에 뜬다. **기본값이 겹치면 나중에
뜨는 쪽이 조용히 bind 에 실패하므로** 배분을 여기에 고정한다. Python 서버와 Java 클라이언트는
**짝이며 함께 움직인다** — 한쪽만 바꾸면 기본값이 어긋나 그 경로가 끊긴다.

| 포트 | Python (서버) | Java (클라이언트 기본값) | 상태 |
|---|---|---|---|
| 50051 | `gole_support_agent.server` | `GOLE_SUPPORT_AGENT_GRPC_TARGET` | 운영 배포됨 |
| 50052 | `gole_agent_worker.bootstrap` | `gole.support-agent.durable.target` | opt-in, 미배포 |
| 50053 | `gole_brick_filter.grpc_server` | `gole.brick-filter.grpc-target` | opt-in(local 전용), 미배포 |
| 50054 | `gole_brick_filter.server` (HTTP) | `gole.brick-filter.endpoint` | 기본 transport, 미배포 |

`tests/test_port_allocation.py`가 이 표를 강제한다 — 기본값이 겹치거나 위 배분에서 벗어나면
서버를 켜 보지 않아도 테스트가 깨진다. 포트를 옮기려면 표·테스트·Java 기본값을 함께 고친다.

> 사진 HTTP 서버는 원래 :50052 에 있었다. 영속 작업자가 나중에 같은 포트를 기본값으로 잡았고,
> 둘 다 배포돼 있지 않아 아무도 모르고 지나갔다. `.kiro/specs/agent-worker/brick-grpc.md` 는
> gRPC 를 :50053 에 두면서 ":50052 를 재사용하지 않는다"고 적었지만, 그때 이미 HTTP 가
> 거기 앉아 있는 것은 보지 못했다. 2026-09-22 에 HTTP 를 :50054 로 옮겨 정리했다.

### 내부 호출 계약

모든 RPC는 `x-gole-caller`와 `authorization: Bearer …`를 필요로 한다. Java는 사용자 인증,
owner 확정, 권한과 quota 승인을 먼저 끝내고 `owner_id`, `authorization_ref`를 보낸다.
Python은 승인 참조를 보존하고 provider adapter에 전달하지만 그 참조를 별도 원장으로 검증하거나
일일 사용량을 차감하지 않는다. AgentJobs와 brickfilter 3회/일 ledger의 영속 작업 연결은 후속 작업이다.
사진 필터에는 별도 비영속 `BrickImages.Generate` gRPC를 추가했다(아래 참고).

Submit의 `idempotency_key`는 caller/owner 범위이며 같은 정규화 payload/승인 참조/provider는
같은 job을 반환한다. 하나라도 다르면 ALREADY_EXISTS다. 다른 owner나 caller의 job은
Get/Cancel에서 NOT_FOUND로 취급한다. RPC result에는 요청 원문·내부 승인 참조를 넣지 않는다.

| kind | provider | payload_json | 현재 동작 |
|---|---|---|---|
| support.rules | rules | ticket_id/title/message 및 선택 declared_category/locale | 기존 rules-v1 분석, 외부전송 금지 |
| synthetic.demo | fake | `{"topic":"brick-colors"}` | 고정 비개인정보 테스트 결과 |
| synthetic.demo | openai | `{"topic":"brick-colors"}` | 아래 이중 opt-in일 때만 고정 질문 호출 |

OpenAI는 서버 `AGENT_OPENAI_ENABLED=true`와 Submit `allow_external=true`를 모두 요구한다.
`OPENAI_API_KEY`는 환경변수에서만 읽고 `AGENT_OPENAI_MODEL` 기본값은 `gpt-4.1-mini`다.
SDK 재시도는 끄고 runner의 제한을 적용하며 `store=False`, 최대 출력 128 token을 사용한다.
문의 원문이나 자유 입력은 OpenAI 데모로 받을 수 없다. **support의 외부 모델 정책은 기존대로**다.
이 변경을 검증할 때 실제 유료 API는 호출하지 않았다.

### 실행 의미와 한계

Brain은 prepare → execute → review, Hands는 typed Provider, Session은 FencedSaver와
원문 없는 상태 이벤트다. 모든 결과에 `human_review_required=true`를 강제하며 자동 발송하지 않는다.
LangGraph checkpoint와 pending writes, 작업 완료 쓰기는 유효 lease와 fencing token을 같은
SQLite 트랜잭션에서 확인한다. 만료된 실행은 새 작업자의 checkpoint를 덮어쓰지 못한다.

취소는 즉시 CANCELLED로 논리 종결하며 이후 완료/checkpoint를 차단한다. 진행 중 OpenAI HTTP
요청은 물리적으로 취소하지 못하고 SDK timeout까지 남을 수 있다. Provider adapter는 전달한
`timeout`과 `cancelled` 계약을 지켜야 한다. 이를 무시하는 임의 Python 코드를 강제 종료하는
샌드박스는 아니며, 해당 코드가 영원히 block하면 실행 스레드도 돌아오지 않는다.

재시작은 마지막 checkpoint에서 재개한다. 외부 응답과 checkpoint 사이 crash는 호출을
반복할 수 있다. `operation_key`는 미래 adapter의 멱등 부수효과 연결점이며 OpenAI 비용의
exactly-once를 보장하지 않는다. Java support 요청/조회 연결은 opt-in으로 구현했으며 이미지의 영속 재개는 연결하지 않았다.

### 사진 필터 gRPC

사진 구현도 Brain/Hands/Session으로 나눴다. `brain.py`는 graph와 포트만 사용하고,
`hands.py`는 이미지 codec·OpenAI 호출, `runtime.py`는 동시성·취소·실행시간을 담당한다.
`session.py`에는 요청 수명 동안의 단계 이벤트만 남기며 사진이나 프롬프트는 넣지 않는다.
HTTP와 gRPC 모두 같은 Harness를 사용하고 `agent.py`는 기존 import 호환용으로만 남긴다.
문의의 SQLite 영속 Session과 사진의 메모리 전용 Session은 보존 정책이 달라 구분한다.
공유 Co-brain·원격 vault·프로세스 sandbox까지 구현한 상태는 아니다.

`gole_brick_filter.grpc_server`는 기존 Java BrickFilterService의 quota·provider gate 뒤에서 호출하는
별도 사진 실행 프로세스다. `gole.brick.v1.BrickImages.Generate` 계약과 :50053을 사용한다.
`gole.brick-filter.transport=grpc`에서만 Java 어댑터가 바뀌며 기본 HTTP(:50054)는 유지한다.
사진은 LangGraph에서 validate→generate→result로 처리하지만 checkpoint·디스크에 저장하지 않는다.
입력 4MiB·출력 8MiB·필수 deadline·동시 실행 2개·인증을 검사하며 자동 재시도는 하지 않는다.

설정과 실행 전제는 `.kiro/specs/agent-worker/brick-grpc.md`를 따른다. 운영 원격 transport는
허용하지 않으며 Java/Python의 명시적 활성화와 provider 키가 필요하다. 실제 유료 호출을 검증한 것은 아니다.
기존 support Docker 기본 entrypoint나 실행 중인 사용자 서버는 바꾸지 않았다.

외부 호출 없이 실제 Java/Python 연결을 확인하는 단발 명령(Java 21·Gradle·uv 필요):

```bash
bash apps/support-agent/scripts/generate-proto.sh
uv run --project apps/support-agent python apps/support-agent/tests/verify_brick_grpc_interop.py
```

테스트는 임시 loopback 서버를 띄우고 종료하며 두 모드를 각각 한 번 호출한다. 이는 전체 웹 로그인/
업로드나 이미지 생성 품질의 E2E 검증을 대신하지 않는다.

### 검증

`uv run --project apps/support-agent pytest apps/support-agent/tests`로 기존 규칙과 이미지
테스트를 포함해 검증한다. `tests/test_durable_worker.py`는 실제 localhost gRPC, 프로세스
비정상 종료/재시작, provider 완료 뒤와 graph 완료 뒤 복구, 중복/충돌, 소유자 경계,
lease/fencing/heartbeat, backoff/실패/취소와 fake OpenAI client를 검사한다.

설계와 검증 기록: `.kiro/specs/agent-worker/`.
LangGraph checkpoint 계약 참고: [공식 persistence 문서](https://docs.langchain.com/oss/python/langgraph/persistence).


## Java support opt-in 연결

기본 기존 `gole.support-agent.enabled`와 동기 Analyze 경로는 유지한다. 새 경로는 Java 환경에
`GOLE_SUPPORT_AGENT_DURABLE_ENABLED=true`를 명시한 경우만 선택한다. 아래 속성은
환경변수 또는 비밀 설정에서 주입한다(토큰 값은 로그/커밋/CLI 이력에 넣지 않는다).

| Java 속성 | 환경변수 | 값/의미 |
|---|---|---|
| gole.support-agent.durable.enabled | GOLE_SUPPORT_AGENT_DURABLE_ENABLED | 기본 false |
| gole.support-agent.durable.target | GOLE_SUPPORT_AGENT_DURABLE_TARGET | 기본 127.0.0.1:50052 |
| gole.support-agent.durable.caller | GOLE_SUPPORT_AGENT_DURABLE_CALLER | Python AGENT_INTERNAL_CALLER와 일치 |
| gole.support-agent.durable.token | GOLE_SUPPORT_AGENT_DURABLE_TOKEN | Python AGENT_INTERNAL_TOKEN과 일치 |
| gole.support-agent.durable.timeout | GOLE_SUPPORT_AGENT_DURABLE_TIMEOUT | 기본 PT2S, 최대 PT10S |

Java는 local/development/dev/test/e2e와 loopback 대상만 허용한다. 실행 중 API8090이나 운영 설정은
바꾸지 않았다. 기존 `GOLE_SUPPORT_AGENT_ENABLED=false`로 분석을 꺼도 durable.enabled=true이면
원격 파기 연결은 유지된다. 과거 원격 사본이 있는 동안 durable 설정/DB/인증을 제거하면 안 된다.

기존 방별 최초 문의 원장을 재사용하고 Submit/Get 전체 예산은 Java lease보다 짧게 제한한다.
정상 원격 pending은 기존 Mongo 작업을 defer하며 실패 시도 수를 소비하지 않는다. Python worker가
없는 동안에도 Python Get이 동작하면 접수 후 300초 기한을 검사한다. Python 서버까지 중단된 동안의 Java 원장 총 SLA/경보는 아직 없다. 실패/취소는 기존 Java 재시도로 처리한다.

관리자의 기존 파기 승인·보존검토를 통과한 뒤 Mongo 삭제 트랜잭션 commit 전에 내부 Purge를
호출한다. 원격 실패면 Mongo 삭제/영수증은 rollback한다. 원격 성공 뒤 Mongo rollback이 나면
원문은 Mongo에 남고 원격 tombstone은 유지되며, 동일 요청 재시도가 같은 원격 영수증을 받는다.
원격/로컬 파기가 분산 원자 트랜잭션인 것은 아니다.

Purge는 job/payload/checkpoint/pending writes/events를 삭제하고 caller/owner/key/receipt/time만
보존한다. 지연 완료와 재Submit을 차단하며 프로세스 재시작 뒤에도 유지한다. 키는 Java에서
namespace+ID를 SHA-256으로 만든 불투명 값으로 원문·raw room/requester ID를 tombstone에 넣지 않는다.
SQLite WAL/과거 백업에서 물리 바이트가 즉시 없어지는 것은 보장하지 않는다.


원격 사본 보유 여부는 기존 Java 분석 문서의 `remoteCopyPossible` 표식으로 보존한다. 유효한
lease/token으로 표식을 기록한 뒤에만 원격 Submit을 허용하며, 완료·retry/defer 뒤에도 표식은 남는다.
분석만 비활성화하면 원격 purge 연결을 유지하고, durable 설정까지 해제했는데 이 표식이 있으면
문의 파기를 fail-closed로 거절한다. 따라서 설정 해제로 과거 원격 사본 파기가 조용히 누락되지 않는다.
표식 없는 기존 동기 작업은 기존 파기 흐름을 유지한다. 이 표식은 새 원장이나 quota 차감이 아니다.
## 전체 작업 기한

영속 AgentJobs 작업은 접수 후 기본 300초 안에 끝나야 한다. SQLite에 만료 시각을 저장하므로 재시작·재시도·설정 변경으로 연장되지 않는다. 실행 루프가 멈춰 있어도 인증된 `Get` 또는 같은 키 재접수는 만료 작업을 `FAILED / JOB_DEADLINE_EXCEEDED`로 반환한다. 같은 키를 다시 보내도 생성하지 않으며, 만료 뒤 checkpoint와 완료 쓰기도 거절한다. 기존 성공 결과를 기한 경과만으로 실패로 바꾸지 않는다. 외부 제공자가 시작한 작업의 강제 종료나 비용 환급은 보장하지 않는다.

## 외부 관측과 실행 컨텍스트 분리

`gole_agent_runtime.privacy`는 문의 분석·영속 Runner·사진 Harness의 진입점에서 LangSmith tracing을 끄고 상위 Runnable의 callback/tags/metadata/configurable을 격리한다. 단순히 graph에 `callbacks=[]`를 넘기는 것만으로는 상위 callback이 병합될 수 있어 별도 실행 context가 필요하다. 호출이 끝나거나 예외가 나면 상위 context는 복원된다. 외부 exporter mock과 상위 callback 회귀 테스트는 실제 모델 호출 없이 원문·사진이 내부 graph의 trace로 전파되지 않는지 검사한다.

이는 비신뢰 코드를 격리하는 sandbox가 아니다. 호출자가 경계에 넘기기 전에 이미 원문을 기록했거나 제공자 구현이 직접 전송하는 것까지 막지는 않는다. LangChain/LangGraph 변경 시 이 회귀 테스트를 반드시 실행한다.

## 홍보 초안 에이전트 (`gole_promotion_agent`)

배포된 릴리스를 근거로 사이트를 캡처하고 Threads 홍보 게시 초안을 만드는 **일회성 배치**다.
문의·사진과 달리 서버가 아니다 — 하루 한 번 떴다 진다. 설계 근거는
`.kiro/specs/promotion-review/spec.md` D9~D19에 있다.

```text
gole_promotion_agent/
├─ policy.py       # 상수·pydantic 툴 스키마·톤 가이드. 자유 프롬프트를 받지 않는다
├─ ports.py        # Protocol만. SDK·환경변수·HTTP를 모른다
├─ brain.py        # 순환 LangGraph (think ⇄ act) + 제출 3단 체인
├─ hands.py        # git·Playwright·Anthropic·백엔드 HTTP. SDK import는 전부 지연
├─ session.py      # 단계 기계. 내용물을 담지 않는다
├─ checkpoints.py  # 세션 로컬 saver. lease·fencing 없음
├─ runtime.py      # 후보 루프·데드라인·보존 정리
└─ __main__.py     # 일회성 엔트리포인트
```

**영속 워커(`gole_agent_worker`)에 넣지 않은 이유**는 필요한 것이 영속성이지 분산 job
leasing이 아니기 때문이다. 일회성 프로세스 하나뿐이라 `FencedSaver`의 lease·fencing이 풀
문제가 없고, 그 워커는 운영에 배포된 적도 없다. 대신 `gole_brick_filter`처럼 같은 패키지
안의 별도 모듈로 두고 관용구만 공유한다.

**후보 선정은 시간창이 아니라 이력이다.** `main`을 `HEAD`부터 뒤로 걸으며 백엔드가 이미
아는 `sourceCommitSha`를 만나면 멈춘다. 이전 구현은 `feat(` 커밋만 찾았는데 운영 checkout은
squash 전용 `main`에 고정돼 있어 **영구히 0건**을 냈고, 0건이 정상 종료라 매일 초록으로
실패했다. 그 실패를 `tests/test_promotion_agent.py`가 직접 겨냥한다.

**체크포인트를 둔다.** 모델 루프가 최대 20턴이고 여유 메모리가 900 MB뿐이라 중단이 현실적
시나리오인데, 전사를 잃으면 지불한 토큰을 다시 지불하게 된다. 제출은 업로드 → 생성 →
검토요청 3단으로 쪼개 각 단계가 멱등하게 재개되므로 업로드 직후 죽어도 고아 이미지가 남지
않는다. **이미지 바이트는 상태·체크포인트에 넣지 않는다** — saver가 직렬화 전에 거부한다.

### 실행

유료 호출 없이 전 과정을 돌리는 드라이런이 기본 검증 경로다. 외부 SDK와 백엔드 클라이언트를
import조차 하지 않으므로 나가는 경로가 물리적으로 없다.

```bash
uv sync --project apps/support-agent --extra promotion
uv run --project apps/support-agent playwright install chromium   # 실제 실행에만 필요

PYTHONPATH=apps/support-agent/src \
  uv run --project apps/support-agent python -m gole_promotion_agent \
  --dry-run --repo . --sessions /tmp/promotion-sessions
```

실제 실행은 운영 VM의 `gole-promotion-agent.timer`가 `apps/support-agent/Dockerfile.promotion`
으로 만든 별도 이미지를 oneshot으로 띄운다. 환경 변수는 `.env.example`을 본다. 브라우저를
넣으면 이미지가 수백 MB 커지므로 **상주 50051 서비스 이미지와 일부러 분리했다.**

`anthropic`·`playwright`·`httpx`는 `promotion` extra에만 있다. 기본 설치(`uv sync --locked`)로도
테스트가 전부 돌아야 하며, 그래서 `hands.py`의 SDK import는 전부 함수 안에 있다.

## 배포 이미지의 오프라인 smoke 검사

관측 경계가 직접 사용하는 `langchain-core`와 `langsmith`는 검증한 버전을 직접 의존성으로 고정한다.
컨테이너에서는 **이미지가 실제로 싣는 것만** 실행한다 — support proto, rules-v1, 상위 callback
격리. 더해서 보류·미배포 패키지(`gole_agent_worker`·`gole_brick_filter`·`gole_promotion_agent`)가
이미지에 섞여 들어오지 않았는지 import 로 확인한다. 운영 키·네트워크·호스트 데이터 쓰기 없이
실행하며 장기 실행 서비스나 운영 배포는 시작하지 않는다.

> 예전에는 이 probe 가 워커의 SQLite 작업과 사진 두 모드까지 돌렸다. 그 코드가 이미지에서
> 빠지면서 함께 줄였다. **검증이 약해진 것이 아니라 옮겨간 것이다** — 두 패키지는 CI 의
> `uv run pytest`가 매 PR 마다 전부 돌린다. 이 probe 의 역할은 "배포되는 이미지가 자기가
> 싣는 것을 실제로 돌릴 수 있는가" 하나다.

```sh
docker build -f apps/support-agent/Dockerfile -t gole-agent-hour:test .
docker run --rm --network none --read-only --tmpfs /tmp:rw,noexec,nosuid,size=64m \
  --cap-drop ALL --security-opt no-new-privileges --pids-limit 64 --memory 512m --cpus 1 \
  --mount type=bind,src="$PWD/apps/support-agent/tests/verify_container_runtime.py",dst=/probe.py,readonly \
  --entrypoint python gole-agent-hour:test /probe.py
```

2026-09-13 Linux arm64 이미지 빌드와 이 smoke 검사 통과. 원격 gRPC 배포, 실제 OpenAI 호출 및 이미지 품질 검증과는 구분한다.
2026-09-22 보류 패키지를 이미지에서 빼면서 probe 범위를 축소했다.
