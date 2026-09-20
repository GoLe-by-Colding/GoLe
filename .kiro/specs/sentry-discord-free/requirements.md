# 무료 Sentry 수집과 자체 Discord 알림 요구사항

- web/API 오류를 production 명시적 opt-in에서만 Sentry에 수집한다. local/test 기본 비활성이다.
- 원문 user/request/cookie/token/URL/예외 메시지/스택/첨부/임의 태그는 외부 전송하지 않는다.
- API는 기존 APPLICATION ERROR 발행 경계에서 안전한 이벤트를 Sentry에 복제하고 Discord 단일 경로를 유지한다.
- web은 Sentry 조회 API에서 확인한 최근 오류만 자체 Discord 발행기로 전달한다. 공식 유료 Discord 통합을 사용하지 않는다.
- 수집 조회 성공, SDK 큐 수락, Discord HTTP 수락을 서로 다른 사실로 취급한다.
- Mongo 진행점·전송 원장·페이지네이션·lease 복구로 장기 중단과 재시작을 처리한다. 전달 실패 전에는 watermark를 확정하지 않는다.
- 개인정보 제거, 비활성, 중복억제, 실패 및 SDK transport를 로컬에서 실제 실행 검증한다.
- 운영 자격증명/계정/배포는 coordinator 소유이며 이 작업에서 수정하거나 값을 출력하지 않는다.
