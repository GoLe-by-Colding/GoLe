# 무료 Sentry 수집과 자체 Discord 알림 인수

2026-09-12. 구현/로컬 검증 완료, commit/push/계정 변경/운영 배포/실제 외부 알림 발송 없음.

## 구현파일

- `apps/web/package.json`, `pnpm-lock.yaml`: @sentry/nextjs 10.74.0 고정 의존성
- `apps/web/sentry.options.ts`: DSN 사전 검증, production opt-in, allowlist beforeSend, 첨부/기본 integration/추적/리플레이/로그 비활성, no-referrer/credentials omit
- `apps/web/src/instrumentation-client.ts`, `src/instrumentation.ts`, `src/app/error.tsx`, `src/app/global-error.tsx`: 브라우저 전역 오류/rejection, Next 서버 오류, 렌더 오류 수집
- `apps/web/next.config.ts`: 검증한 Sentry 원점만 CSP connect-src에 추가
- `apps/api/build.gradle.kts`: io.sentry:sentry 8.56.0
- `apps/api/src/main/java/com/gole/api/common/operations/DiscordOperationalEventPublisher.java`: API APPLICATION ERROR의 안전한 SDK 복제, Discord 원문 경로/예외 종류 제거
- `apps/api/src/main/java/com/gole/api/common/operations/sentry/`: SentrySafeEvents, SentryErrorCollector, SentryPollStore, MongoSentryPollStore, SentryWebIssuePoller
- `apps/api/src/test/java/com/gole/api/common/operations/sentry/` 및 `SentryDiscordIntegrationTestCase.java`: 수집/원장/발송 검증
- `.kiro/specs/sentry-discord-free/web-transport.test.mjs`: 실제 Next SDK·BrowserClient envelope 검증

## 운영 설정 계약 — 값은 이 문서에 넣지 않는다

| 실행 위치 | 키 | 활성 조건 |
| --- | --- | --- |
| web 빌드 | NEXT_PUBLIC_SENTRY_ENABLED, NEXT_PUBLIC_SENTRY_ENVIRONMENT, NEXT_PUBLIC_SENTRY_DSN | true + production + gole-web DSN |
| web 서버 런타임 | SENTRY_ENABLED, SENTRY_ENVIRONMENT, SENTRY_DSN | true + production + gole-web DSN |
| API 런타임 수집 | GOLE_SENTRY_ENABLED, GOLE_SENTRY_ENVIRONMENT, GOLE_SENTRY_DSN | true + production + gole-api DSN |
| API 웹 오류 조회 | GOLE_SENTRY_POLL_ENABLED, GOLE_SENTRY_ENVIRONMENT, GOLE_SENTRY_READ_TOKEN | true + production + event:read 토큰 |
| 기존 Discord | 기존 GOLE_DISCORD_* 설정 | enabled와 운영 webhook 구성이 필요함 |

조직 gole-j9와 프로젝트 gole-web/gole-api를 사용한다. 조회 원점은 고정 https://sentry.io/api/0/organizations/gole-j9/issues/ 이며 project=gole-web/environment=production/level error·fatal을 보낸다. 토큰은 API 런타임 전용이고 NEXT_PUBLIC에 넣지 않는다. 소스맵 업로드/인증 토큰/공식 유료 Discord 통합을 추가하지 않았다. NEXT_PUBLIC 값 변경은 web 재빌드가 필요하다.

처음 켤 때는 최근 5분부터 원장이 시작된다. 이후 Mongo `sentry_web_poll`과 `sentry_web_alerts`를 삭제하지 않아야 중단 후 이어 읽을 수 있다. cursor는 각 페이지 원장 upsert 후 저장하고, watermark는 모든 페이지와 Discord 수락을 완료한 뒤에만 전진한다. 아직 전송하지 못한 건은 실패/disabled를 포함해 pending으로 남는다. 원장 삭제/강제 watermark 이동은 하지 않았다.

## 검증 결과

- web SDK transport: **4건 통과 / 0실패 / 0스킵** (`node --test .kiro/specs/sentry-discord-free/web-transport.test.mjs`)
- 기존 core Sentry 정책: 개인정보 투영·기본 비활성·환경/레벨·5분 중복·분당3건/재설정 검사 통과 (`node .kiro/specs/admin-operations/sentry-policy.test.mjs`)
- web `format:check`, `lint`, `typecheck`, `fsd:lint`, `build`: 모두 통과
- build는 `NEXT_DIST_DIR=.next/sentry-check`로 기존 dev 캐시를 분리했고 자동 생성된 tsconfig include 변경은 제거했다.
- API `spotlessCheck`: 통과
- API 실제 단위 실행: **25건 통과 / 0실패 / 0스킵** (`test --tests '*Sentry*' --tests '*DiscordOperationalEventPublisher*' --rerun-tasks`)
- API Mongo 실제 통합: **1건 통과 / 0실패 / 0스킵** (`integrationTest --tests '*MongoSentryPollStoreIntegrationTest' --rerun-tasks`, mongo:7 Testcontainers)
- 기존 Discord 단위 11건은 로컬 HTTP 서버를 사용한 payload·중복·429/5xx/transport 재시도 검증을 포함한다.
- 추가 Java SDK 검증은 실제 기본 HTTP transport로 로컬 수집 서버에 envelope가 도달하고 예외 원문이 빠지는 것까지 확인했다.
- Mongo 통합은 실제 lease 선점 경쟁·120초 만료 후 다른 어댑터 복구·기존 소유자 갱신 차단·원장 upsert 멱등·retryAt/delivered 보존을 확인했다.
- poll 단위는 다중 페이지 cursor 복구, production/project query, 허용필드 전송, 1시간 후 전달 재시도, 페이지 저장 실패 후 재조회, 429 대기 재시작 보존, 5xx 지수 backoff, 수락 후 DB 기록 실패의 재발송을 확인했다.

## 성공 상태와 운영 미검증

SDK capture는 큐 시도이며 Sentry 수집 성공이 아니다. Sentry 조회 성공도 Discord 성공이 아니다. 웹 원장의 delivered는 기존 confirmed publisher가 HTTP 2xx를 받은 뒤만 기록하며, 기록 실패 시 미완료로 보존해 다음 실행에서 재전송한다. API 기존 Discord와 Sentry 수집도 독립 경로다.

실제 계정 무료 quota·토큰 권한, 실제 Sentry 프로젝트의 수집/조회, 실제 Discord 채널 수신, 운영 환경변수 주입/CSP, 실제 브라우저 사용자 오류 플로우는 미검증이다. API 전체 기동은 다른 소유 범위의 media 설정 문제가 coordinator에게 보고되어 있어 시도하지 않았으며 해당 파일을 수정하지 않았다. admin readiness는 후속 승인 범위에서 비활성/설정미완료/구성됨·실제수집미검증으로 갱신했다.

at-least-once이므로 Discord 수락 직후 DB 기록 전 중단에는 중복 가능성이 남는다. 첫 활성화 이전 이력, Sentry 보존기간을 넘긴 중단, SDK 자체 수집 실패, overlap보다 늦은 eventual consistency는 보장하지 못한다. 조회는 개별 이벤트가 아니라 이슈의 5분 버킷을 묶는다. 원장은 자동 삭제하지 않으므로 장기 보존량 정책은 후속 운영 검토가 필요하다.

신규 dev 서버를 띄우지 않았다. 단위 테스트 HTTP 서버와 Testcontainers는 종료되었고 기존 서버·Docker 인프라는 건드리지 않았다. 별도 볼트 개발로그는 후속 승인 범위에서 신규 작성했다.


## 배포 연결 후속 작업 (2026-09-12)

- web.Dockerfile에 NEXT_PUBLIC_SENTRY_ENABLED/ENVIRONMENT/DSN ARG와 build-stage ENV를 연결했다. 기본값은 false/local/빈 DSN이다. SENTRY_DSN과 GOLE_SENTRY_READ_TOKEN은 build ARG로 전달하지 않는다.
- 실제 GCP Compose의 frontend build args를 연결하고 web 서버에는 SENTRY_ENABLED/ENVIRONMENT/DSN만 runtime environment로 전달한다. backend는 기존 gole.env env_file을 사용하므로 GOLE_SENTRY_*를 그대로 받는다. frontend에 backend env_file을 공유하지 않았다.
- production Compose의 정확한 build allowlist와 web runtime allowlist를 갱신했다. 활성화 시 production+DSN을 요구하며 공개 READ_TOKEN·알 수 없는 public Sentry 키·잘못된 DSN은 값 출력 없이 거부한다.
- root/web/production env example을 모두 기본 비활성으로 작성했다. sync-dev-env의 기존 명시적 자격증명 allowlist에는 Sentry를 추가하지 않아 운영 수집/조회 키가 자동으로 local에 유입되지 않는다.
- CSP는 이전 구현에서 공개 DSN의 검증된 https Sentry 원점만 connect-src에 추가하고 있었다. build 인수 연결로 해당 계산에 값이 도달한다. 추가 CSP 완화는 하지 않았다.
- 관리자 ExistingOperationsDiagnostics와 화면은 SDK 미연결이라는 정적 결과를 제거했다. API 수집/웹 조회 설정의 비활성·미완료·구성됨을 구분하며 구성됨도 실제 수집/조회 미검증으로 표시한다. 웹 브라우저 SDK 활성화나 외부 전달을 여기서 검증하지 않는다. 옛 감사 이력의 코드는 이전 진단으로 표시한다.

### 운영 적용 순서 — coordinator 소유

1. hostctl이 사용하는 root-owned `/usr/local/libexec/gole/validate-production-compose.py`와 `validate-production-env.py`를 검토된 `bootstrap-host.sh` 설치 경로로 먼저 갱신한다. 저장소 파일만 바꾸면 운영의 옛 exact build allowlist가 새 args를 거부한다. 호스트 정책 업데이트는 실행하지 않았다.
2. Secret Manager의 운영 env에 필요한 공개 DSN/flags와 서버 전용 키를 넣고 Secret Sync로 적용한다. 이 작업에서 실제 값은 읽거나 출력하지 않았다.
3. 정상 main CI/CD 경로에서 web 이미지를 재빌드하고 컨테이너 runtime 설정을 적용한다. 설치/이미지 빌드/배포는 이 후속 작업에서 실행하지 않았다.
4. 실제 Sentry 프로젝트 수집 조회와 Discord 2xx/채널 수신을 각각 확인한다. 하나만 성공했다고 다른 것을 성공으로 기록하지 않는다.

### 후속 검증 결과와 제한

- production env/Compose 정책 **48건 통과**, prepare-production-env **6건 통과**, 0실패/0스킵.
- 이전 배포 모델의 Sentry 키가 전부 없는 경우도 허용해 정상 이전 버전 rollback을 유지하며 일부 누락/비밀 키 추가는 거부한다.
- 실제 `docker compose config --format json`을 합성 env fixture로 실행해 공개 DSN→build args, 서버 DSN→frontend runtime, read token→backend 전용 경로를 확인했다. 출력 모델은 캡처하고 값은 로그에 출력하지 않았다.
- Dockerfile ARG/ENV 연결과 비밀 build ARG 부재를 계약 테스트로 확인했다. 의존성 설치 금지 때문에 Docker 이미지 빌드 자체는 미실행이다.
- web typecheck와 수정한 admin-operations 파일 eslint 통과, Prettier 적용.
- readiness 단위 **2건 통과 / 0실패 / 0스킵**. 최초에는 다른 워커 chat 테스트 생성자 오류로 임시 테스트 소스 제한을 사용했으나 해당 수정 후 최종적으로 소스 제한 없이 `test --tests '*ExistingOperationsDiagnosticsTest' --rerun-tasks`를 재실행해 전체 테스트 소스 컴파일과 대상 2건이 통과했다. tracked Gradle 설정은 바꾸지 않았으며 전체 API 테스트 실행을 뜻하지 않는다.
- 배포 관련 기존 shell 3개 bash -n 통과. shell/워크플로는 수정하지 않아 actionlint 대상 변경은 없다.
- 실제 운영 계정 권한·quota, Secret Sync, 호스트 validator 업데이트, Docker build, 운영 CSP/브라우저 흐름, Sentry/Discord 수신은 미검증이다. 신규 서버/설치/커밋/푸시/외부 발송은 없다.

별도 문서: `/Users/kscold/Desktop/GoLe-obsidian/06_개발로그/2026-09-12_무료 Sentry 수집과 자체 Discord 알림 배포 경로를 연결함.md`.
