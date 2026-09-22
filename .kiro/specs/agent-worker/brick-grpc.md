# 사진 필터 gRPC 연결

## 요구사항

- 기존 Java BrickFilterService의 사용자 인증·서울 날짜 하루 3회·전역 provider gate를 그대로 사용한다.
- HTTP 기본값은 유지하고 `gole.brick-filter.transport=grpc`에서만 새 어댑터를 선택한다. 실패 시 HTTP fallback/자동 재시도를 하지 않는다.
- Python은 별도 loopback :50053에서 내부 토큰을 검사한다. 영속 작업자 :50052와 충돌하지 않는다.
  (2026-09-22 정정: 이 항목은 gRPC 경로에 대해서만 참이었다. 같은 패키지의 **HTTP** 서버가
  이미 :50052를 기본값으로 잡고 있었고 — 둘 다 미배포라 드러나지 않았다 — 이를 :50054로
  옮겼다. 배분은 `apps/support-agent/README.md`의 "loopback 포트 배분" 표가 정본이며
  `tests/test_port_allocation.py`가 강제한다.)
- 입력 4MiB·결과 8MiB, 알려진 모드, 필수 deadline, 동시 provider 2개를 제한한다.
- LangGraph validate→generate→result를 그대로 사용하며 provider 직전과 결과 반환 전 취소를 확인한다.
- 원본 사진·결과를 worker checkpoint/로그/디스크에 저장하지 않는다. 기존 Java 저장·만료 정책을 유지한다.
- 실제 유료 호출·운영 활성화는 이번 검증에 포함하지 않는다.

## 설계

별도 `gole.brick.v1.BrickImages.Generate` unary RPC는 PNG bytes와 고정 모드만 받는다.
Java가 기존 예약과 전역 gate를 획득한 다음 호출한다. Python은 사용자 계정이나 quota를 다시 구현하지 않는다.
인증된 Java 호출자를 신뢰하는 내부 실행 경계이며 공개 API가 아니다. Java/Python 모두 local 환경에만 허용한다.
이미지 RPC는 영속 텍스트 AgentJobs와 분리한다. 원본 이미지를 SQLite에 넣어 재시도하는 설계는 채택하지 않는다.
RPC timeout 뒤 이미 시작된 외부 모델의 물리 취소·비용 환급은 보장하지 않는다. 자동 재시도하지 않고 기존 Java 실패 처리로 돌린다.

## 검증 계획

- 실제 loopback gRPC + fake editor로 정상 두 모드, 메타데이터 제거, 인증/모드/크기/deadline 거부를 검증한다.
- 취소 뒤 결과 차단과 semaphore 해제, provider 오류 메시지 비노출을 검증한다.
- Java 실제 gRPC fake server로 인증 메타데이터·deadline·PNG 응답·실패 시 단일 호출을 검증한다.
- 전체 Python 회귀와 Java brickfilter 테스트를 재실행한다.

## 구현 및 로컬 설정

- Python 실행 모듈: `gole_brick_filter.grpc_server`; 기존 support 서버나 AgentJobs 엔트리포인트는 바꾸지 않는다.
- Java: `gole.brick-filter.enabled=true`, `gole.brick-filter.transport=grpc`, `gole.brick-filter.grpc-target=127.0.0.1:50053`, `gole.brick-filter.internal-token`을 명시한다.
- Python: `GOLE_ENVIRONMENT=local`, `BRICK_FILTER_PROVIDER_ENABLED=true`, `BRICK_FILTER_INTERNAL_TOKEN`(Java와 동일), `OPENAI_API_KEY`를 안전한 프로세스 환경으로 주입한다.
- Python 모듈 탐색에는 `PYTHONPATH=apps/support-agent/src:apps/support-agent/generated`가 필요하며 먼저 `bash apps/support-agent/scripts/generate-proto.sh`를 실행한다.
- OpenAI 모델 설정은 기존 `BRICK_FILTER_MODEL`을 재사용한다. 키 값은 문서·커밋·로그에 기록하지 않는다.
- 로컬 서버를 실제 켜거나 유료 provider를 활성화하지 않았다. 두 transport를 동시에 실행하거나 :50052를 재사용하지 않는다.

## 2026-09-13 검증 근거

- 후속 `fc4d5cd`·`1045860` 기준 Python 전체 97건, Java 전체 단위 1,190건 통과. 전체 Java 통합은 115건 통과·실제 알림톡 발송 1건 스킵이며 아래 브릭 11건은 모두 실행했다. Linux arm64 worker 이미지 빌드 및 네트워크 없는 읽기 전용 컨테이너의 proto/support/durable/image/privacy smoke도 통과했다. 아래 62/71건은 단계별 과거 기준이다.

- 역할 분리 후 최종 Python 회귀는 71건 통과(0실패/0스킵)이며 아래 62건은 gRPC 연결 직후의 기준이다. 실제 Java/Python 두 모드도 역할 분리 후 재검증했다.
- 전체 Python pytest 62건 통과(0실패/0스킵). 추가 12건은 실제 gRPC 두 모드, 인증, 중복 인증 헤더, 크기, deadline, 취소·capacity, 원본 메타데이터 제거를 검증한다.
- Java brickfilter 단위·web·gRPC 테스트 17건 통과(0실패/0스킵). 7건은 새 gRPC 어댑터이며 Spring HTTP 기본값/gRPC 명시 선택에서 provider bean이 하나인지 포함한다.
- 실제 Mongo `BrickFilterIntegrationTest` 11건 통과(0실패/0스킵). 기존 예약·실패 환급·복구 경계의 회귀를 확인했다. scoped Spotless도 통과했다.
- `uv run --project apps/support-agent python apps/support-agent/tests/verify_brick_grpc_interop.py`로 실제 Java 어댑터 → Python gRPC → LangGraph → fake editor를 연결했다. 두 모드 각 1회·정상 PNG를 확인했다.
- `BrickGrpcInteropProbe`는 선택 실행하는 단발 검증이며 기본 Java CI에서 Python을 요구하거나 조용히 skip하지 않는다.
- fake editor는 통신과 제어 흐름만 검증한다. 실제 사진 변환 품질·OpenAI 계정 접근 권한·전체 웹 로그인 업로드·운영 배포 검증은 아니다.
