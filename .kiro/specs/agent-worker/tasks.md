# 구현 작업

- [x] 별도 proto와 도메인/typed provider 계약
- [x] SQLite job 상태·멱등성·lease/fencing·heartbeat·취소·재시도
- [x] fenced LangGraph checkpoint/pending writes와 graph 재개
- [x] 내부 인증 gRPC 서버 및 실행 루프
- [x] fake provider 통합·재시작·경계/실패 테스트
- [x] README와 별도 Obsidian 개발로그/아키텍처 문서
- [x] 실행 결과 및 Java/운영 미연결 범위 보고

## 별도 후속 범위

- Java support opt-in RPC 연결과 승인된 문의 연계 파기는 구현했다. brickfilter quota/adapter 연결은 미구현이다.
- 운영 배포/영속 볼륨/보관·삭제 정책/mTLS는 미적용이다.
- 실제 유료 OpenAI 호출/컨테이너 빌드/운영 부하 테스트는 실행하지 않았다.

## Java support 후속 구현

- [x] 기존 disabled/동기 모드 보존과 별도 opt-in bean 선택
- [x] 내부 인증 Submit/Get/Cancel 및 owner/source/규칙 응답 검증
- [x] pending defer, lease 만료 완료 차단, 기존 실패 재시도 유지
- [x] Purge 계약과 영속 tombstone, Java 파기 fail-closed/rollback 연계
- [x] 실제 gRPC/프로세스 재시작/Mongo rollback·lease 테스트
- [x] 설정·운영·물리 삭제 한계 및 Obsidian 후속 기록
