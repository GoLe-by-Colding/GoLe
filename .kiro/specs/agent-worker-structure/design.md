# 에이전트 작업자 구조 개편 설계

## 책임

- agents: 작업별 Brain과 입력 검증. support와 synthetic 데모를 명시적 registry에 등록한다.
- runtime: 공통 고정 순서 그래프, 실행 context, 작업 서비스, runner와 SQLite 작업 저장소.
- hands: typed Provider 계약과 fake/OpenAI 구현을 분리한다.
- session: FencedSaver가 체크포인트·pending writes를 저장한다. 장기 기억은 맡지 않는다.
- entrypoints: gRPC 인증·직렬화·오류 매핑. bootstrap은 환경 설정과 프로세스 조립을 담당한다.

## 의존 경계와 호환

Brain은 Provider 계약과 실행 context의 check_active에만 의존하고 SQLite·FencedSaver를 알지 못한다.
Runtime이 구체 실행권 검사를 제공한다. FencedSaver와 Store는 같은 SQLite 트랜잭션 안에서
fencing과 저장을 검사하는 기존 경계를 유지한다. 저장소를 분리 DB로 나누지 않는다.

기존 루트 model/brain/runner/store/server 모듈은 호환 진입점이다. 실제 구현은 새 경로에 둔다.
hands/session은 동일 import를 제공하는 패키지로 전환한다. server 실행 명령과 기본 DB 경로를 유지한다.

그래프는 prepare → execute → review와 submission/prepared/result 상태를 유지한다.
이번 변경은 기존 v1 흐름의 내부 이동이며 DB·payload에 버전을 새로 쓰지 않는다.
향후 그래프 구조가 바뀌면 별도 버전과 이전 작업 drain/재개 정책을 설계한다.
신규 작업은 입력 검증과 Brain을 함께 registry에 등록한다. 요청 입력으로 임의 모듈을 로드하지 않는다.

## 검증

변경 전 전체 pytest 기준선을 확인한다. 이동 단위로 회귀 검증하고, DB 없이 Brain 실행,
취소 경계, 기존 버전 그래프 체크포인트 재개, 기본 DB 경로와 import 호환을 추가 검사한다.
최종 전체 pytest는 실제 gRPC·프로세스 재시작·중복·파기·lease 테스트를 포함한다.

## 최신 dev와의 통합

작업 중 dev에 반영된 전체 작업 기한과 privacy 경계도 보존한다.
Store의 deadline_at 초기화·기한 검사, Runner의 남은 기한 전달·private_execution,
기동 시 reject_external_tracing은 새 경로로 이동한다. 이 개편 자체는 dev 대비
DB 마이그레이션이나 의존성을 추가하지 않는다.
