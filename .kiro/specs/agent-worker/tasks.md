# 구현 작업

## 사진 기능의 역할 분리

- [x] Brain을 policy/ports만 사용하는 graph 모듈로 분리함
- [x] 이미지 codec·OpenAI 구현을 Hands로 이동하고 기존 import facade를 유지함
- [x] Harness가 실행 한도·취소·안전한 오류와 요청별 Session을 소유하게 함
- [x] 사진 Session을 원문 없는 한정된 단계 이벤트로 제한함
- [x] HTTP/gRPC가 동일 Harness를 사용하게 연결함
- [x] 구조 의존 경계·Session 전이·동시 요청 격리·실패·취소 회귀를 추가함
- [ ] 공동 지식/결정 승격(Co-brain)과 tenant ACL 저장소는 별도 설계·구현 필요
- [ ] 원격 credential vault·sandbox 프로세스 격리와 운영 transport는 별도 작업

- [x] 별도 proto와 도메인/typed provider 계약
- [x] SQLite job 상태·멱등성·lease/fencing·heartbeat·취소·재시도
- [x] fenced LangGraph checkpoint/pending writes와 graph 재개
- [x] 내부 인증 gRPC 서버 및 실행 루프
- [x] fake provider 통합·재시작·경계/실패 테스트
- [x] README와 별도 Obsidian 개발로그/아키텍처 문서
- [x] 실행 결과 및 Java/운영 미연결 범위 보고

## 별도 후속 범위

- Java support opt-in RPC 연결과 승인된 문의 연계 파기는 구현했다. 사진은 별도 BrickImages gRPC로 quota 뒤에 연결했으며 AgentJobs 영속 사진 재개는 미구현이다.
- 운영 배포/영속 볼륨/보관·삭제 정책/mTLS는 미적용이다.
- 실제 유료 OpenAI 호출/컨테이너 빌드/운영 부하 테스트는 실행하지 않았다.

## Java support 후속 구현

- [x] 접수부터 300초 전체 기한 영속화와 기존 원장 마이그레이션
- [x] 워커 없는 Get 만료·중복 접수·재시도 기한 유지·늦은 쓰기 차단 17건 검증
- [x] 추적 환경 변수 네 별칭 거절과 상위 callback/context 격리 회귀 검증

- [x] 기존 disabled/동기 모드 보존과 별도 opt-in bean 선택
- [x] 내부 인증 Submit/Get/Cancel 및 owner/source/규칙 응답 검증
- [x] pending defer, lease 만료 완료 차단, 기존 실패 재시도 유지
- [x] Purge 계약과 영속 tombstone, Java 파기 fail-closed/rollback 연계
- [x] 실제 gRPC/프로세스 재시작/Mongo rollback·lease 테스트
- [x] 설정·운영·물리 삭제 한계 및 Obsidian 후속 기록
