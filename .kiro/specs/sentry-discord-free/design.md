# 설계

## 무료 방식과 SDK 선택

Sentry 무료 SDK 수집 + 기존 API 프로세스의 읽기 전용 Sentry 조회 API를 선택한다. 공식 유료 Discord 통합은 사용하지 않는다. 브라우저의 자체 알림 endpoint는 위조/스팸 입력과 수집 실패를 분리하기 어렵고, webhook 수신은 별도 인증·플랜 의존성이 있어 제외했다. 무료 플랜의 실제 계정 quota/조회 권한은 운영 담당자가 확인해야 한다.

web은 @sentry/nextjs 10.74.0을 사용한다. 공식 npm peerDependencies에 Next ^16이 있으며 현재 Next 16.3.4로 typecheck/build를 실행했다. API는 io.sentry:sentry 8.56.0 순수 Java SDK를 명시적 빈으로 사용한다. 공식 SDK 저장소에는 Boot 4 전용 sentry-spring-boot-4-starter와 Spring 7 모듈이 존재하지만 자동 request/logback 수집을 열지 않기 위해 순수 SDK를 선택했다. 순수 SDK는 Spring API에 의존하지 않으며 현재 Boot 4.1.1/Java 21에서 컴파일·단위·HTTP transport를 검증한다.

## 수집과 개인정보

web은 Next instrumentation-client의 전역 error/unhandledrejection, app error/global-error, 서버 instrumentation의 onRequestError를 연결한다. SDK에 원문 오류·요청을 넘기지 않으며 beforeSend도 새 객체에 고정 component=web/diagnostic=UNEXPECTED_ERROR/error 또는 fatal/production만 투영한다. 기존 createSentryPolicy의 5분 중복 억제·분당 3건 시도 제한을 재사용한다. default integrations/replay/traces/logs/client reports를 끄고 첨부도 제거한다. 브라우저 fetch는 no-referrer·credentials omit이다.

API는 기존 Discord 발행기의 APPLICATION ERROR 경계만 Sentry에 복제한다. 원문 이벤트를 SDK에 넘기지 않고, beforeSend는 새 SentryEvent를 만든다. 전송 시도 예산은 5분에 1건이며 이는 전달 성공 상태가 아니다. Sentry 장애는 Discord 발송을 막지 않는다. API Discord 오류 payload도 원문 title/description/요청 경로/예외 종류를 고정 코드로 치환한다. 기존 API 알림은 단일 경로를 유지한다. 스케줄러·임의 로그 전체 자동 수집은 범위 밖이다.

## Mongo 진행점과 전송 원장

- `sentry_web_poll`: 단일 gole-web-production 키, from/until/cursor/readComplete, lease owner/만료, readNotBefore만 보관한다.
- `sentry_web_alerts`: 숫자 issue ID+lastSeen 5분 버킷의 SHA-256 키, 시각, 시도 수, retryAt, delivered만 보관한다. 원문 제목·URL·사용자·요청·auth token은 저장하지 않는다.
- 첫 활성화만 최근 5분에서 시작한다. 이후 저장한 from부터 고정 until까지 production/gole-web/error·fatal을 조회한다. 한 tick에 최대 100건 한 페이지를 읽고 Link의 next cursor로 이어 간다. 링크 URL을 따라가지 않고 고정 Sentry 원점에 cursor만 전달한다.
- 페이지 원장 upsert 이후에만 cursor를 저장한다. 중단 후 같은 페이지를 다시 읽어도 원장 키가 같아 중복 생성되지 않는다.
- 조회 마지막 페이지 후에도 전송 대기 건이 남으면 from을 전진시키지 않는다. 모든 전송이 끝난 뒤 다음 구간으로 이동하며 1분 중첩 조회는 원장 키로 중복 억제한다.
- 기존 ConfirmedOperationalEventPublisher의 publishAndConfirm을 호출한다. 별도 Discord HTTP sender를 만들지 않는다. 웹 조회는 API publish 경계를 통과하지 않으므로 Sentry 재수집·이중 발송을 만들지 않는다.
- Discord 2xx 수락 뒤에만 delivered를 저장한다. 실패는 원장에 남고 60초부터 최대 15분 지수 재시도한다. Retry-After도 반영한다. 실패 응답 뒤 해당 tick 발송을 멈춘다.
- 조회 실패는 진행점을 유지한다. Sentry 429의 숫자 Retry-After(최대 24시간)와 조회 지수 backoff를 Mongo에 저장하므로 재시작 후에도 대기한다.
- 120초 lease로 실행자를 제한하고 소유자 조건으로 진행점을 갱신한다. 이전 소유자는 새 소유자의 lease를 해제하지 못한다.

## 한계와 성공의 의미

SDK capture 반환은 큐 수락/시도일 뿐 Sentry 수집 성공이 아니다. 조회 결과는 Sentry 수집 확인이며 Discord 전달 확인이 아니다. Discord delivered는 HTTP 2xx 수락이며 사람이 읽었다는 뜻은 아니다. 외부 수락 직후 Mongo 기록 전에 중단되면 at-least-once 재발송이 가능하다. 기존 API 알림의 메모리 중복 억제도 재시작을 넘어서지 않는다.

첫 활성화 이전 5분보다 오래된 이력, SDK 자체 수집 실패/샘플링, Sentry 무료 플랜의 보존기간을 넘긴 중단은 복구할 수 없다. 이슈 조회 API 기반이므로 개별 이벤트 원장을 재현하지 않고 같은 이슈의 5분 버킷을 묶는다. 정해진 query 구간의 결과는 제공자의 eventual consistency 영향을 받으며 1분 overlap 밖의 늦은 수집까지 보장하지 않는다. 전송 원장에는 TTL을 두지 않아 중복 억제를 유지한다. 장기 운영 시 크기와 별도 보존정책 검토가 필요하다.

## 공식 근거 (2026-09-12)

- https://github.com/getsentry/sentry-java/tree/8.56.0 : 순수 Java SDK, Boot 4/Spring 7 모듈
- https://docs.sentry.io/platforms/javascript/guides/nextjs/manual-setup/ : instrumentation 초기화
- https://docs.sentry.io/platforms/javascript/guides/nextjs/configuration/filtering/ : beforeSend 필터
- https://docs.sentry.io/api/events/list-an-organizations-issues/ : event:read, project/environment/start/end/cursor
- https://docs.sentry.io/api/pagination/ : Link cursor 기반 페이지 조회
- 설치된 Next 16.3.4 instrumentation/instrumentation-client 문서와 npm @sentry/nextjs 10.74.0 peerDependencies

기존 admin-operations/HANDOFF의 유료 Discord 설치 절차는 이 스펙으로 대체한다. readiness 화면의 정적 SENTRY_NOT_INSTRUMENTED 값은 별도 소유 범위이며 실제 수집 확인을 대체하지 못한다.
