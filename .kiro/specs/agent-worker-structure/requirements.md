# 에이전트 작업자 구조 개편 요구사항

- 여러 목적의 에이전트가 공통 실행 기반을 공유하도록 책임을 분리한다.
- 고정 순서 Brain, 외부 기능 Hands, 작업별 체크포인트 Session, 실행 관리 Runtime, gRPC 진입점을 구분한다.
- 기존 support.rules와 synthetic.demo의 입력 검증·출력·외부전송 제한을 보존한다.
- gRPC 계약, 실행 명령, 기존 Python import, DB 스키마와 정규화 payload를 유지한다.
- 기존 prepare/execute/review 체크포인트에서 재개할 수 있어야 한다.
- lease/fencing·재시도·취소·파기의 원자적 저장 경계를 유지한다.
- 홍보 기능, 실제 발행, 목표 기반 플래너, 장기 기억, 배포·새 의존성은 범위 밖이다.
