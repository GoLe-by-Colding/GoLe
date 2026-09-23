# 작업

- [x] 기존 정책/발행기/공식 호환 근거 조사 및 요구·설계 작성
- [x] 웹 SDK instrumentation/error boundary와 Node·브라우저 transport 검증
- [x] API 안전 수집 경계와 실제 SDK HTTP transport 검증
- [x] Mongo 진행점·전송 원장·lease·페이지네이션·재시작·429 backoff 구현
- [x] 기존 ConfirmedOperationalEventPublisher 재사용 및 수집/발송 성공 분리
- [x] 개인정보 제거/비활성/전송 실패/기록 실패/중복억제 단위 검증
- [x] 실제 Mongo Testcontainers lease·원장 복구 검증
- [x] format/lint/typecheck/FSD/build·Spotless 및 인수 문서
- [ ] coordinator: 실제 무료 계정 event:read 권한·quota 및 운영 DSN 설정
- [ ] coordinator: 운영 배포 후 Sentry 수집 조회와 Discord HTTP 수락/실제 채널 수신 각각 확인
- [ ] coordinator: 별도 볼트 개발로그와 정적 admin readiness 표시 갱신 검토
