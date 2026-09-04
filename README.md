# GoLe — 브릭 중고거래 플랫폼 🐳🧱

브릭 중고거래에 특화된 모노레포 플랫폼. 공개 초기에는 **시세**, **직거래 채팅**, 보유/희망
**컬렉션**, 셀러 **샵·팔로우**와 커뮤니티를 제공하고, 계약·운영 준비가 끝난 뒤에만 결제 단계를 연다.

> **GoLe = 고래(Whale) × 레고(Brick)** — 깊은 바다(Cobalt)와 브릭 옐로의 톤. 자세한 컨셉은 `.kiro/steering/brand-identity.md`.

- 🌐 운영: **https://gole.co.kr**
- 🧩 아키텍처: 백엔드 **헥사고날(Hexagonal)** + 프론트 **FSD(Feature-Sliced Design)**
- 📐 개발 방식: **SDD(Spec-Driven Development)** — 모든 기능은 `.kiro/specs/<기능>/`에 스펙 먼저

---

## Stack

| 영역     | 기술                                                                                                                |
| -------- | ------------------------------------------------------------------------------------------------------------------- |
| Frontend | Next.js 16 (App Router, React 19, Turbopack), TypeScript strict, FSD, Playwright E2E                                |
| Backend  | Spring Boot 4 (Spring Framework 7), Java 21 LTS, 헥사고날 + AOP                                                     |
| Data     | MongoDB (primary, replica set `rs0` → 멀티도큐먼트 트랜잭션), Redis (캐시/랭킹)                                     |
| Storage  | MinIO (S3 호환) — 이미지 업로드                                                                                     |
| Infra    | Docker Compose, GCP Compute Engine, Nginx 리버스 프록시, Google Trust Services HTTPS, self-hosted GitHub Actions CD |

> **저장소 전략**: 초기엔 MongoDB + Redis. 정산 도메인이 강한 관계형 정합성을 요구하면, 헥사고날 포트/어댑터 덕분에 해당 컨텍스트에만 PostgreSQL 어댑터를 추가할 수 있다.

---

## 주요 기능

| 도메인(컨텍스트) | 기능                                                                                                                                                                                                                                    |
| ---------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **account**      | 이메일 회원가입·인증, 로그인(불투명 세션 토큰), **로그아웃(서버 세션 폐기)**, **소셜 로그인(Google/Kakao/Naver OAuth2)**, RBAC(USER/ADMIN), BCrypt 해시(레거시 자동 승격), 로그인 잠금                                                  |
| **catalog**      | 레고 세트 카탈로그(검색·추천·조회)                                                                                                                                                                                                      |
| **listing**      | 중고 매물 등록·검색·필터·상태 전이(ACTIVE→RESERVED→SOLD/DELETED), 컨디션·구성 고지                                                                                                                                                      |
| **order**        | 에스크로 안전거래(결제대기→자금보유→완료/환불), 상태 이력, 낙관적 락                                                                                                                                                                    |
| **pricing**      | 체결가 기반 시세 통계·차트·이력, **인기 세트 랭킹(Redis 캐싱)**                                                                                                                                                                         |
| **collection**   | 보유/희망 컬렉션, 추정가                                                                                                                                                                                                                |
| **community**    | 자랑/MOC 피드, 댓글, 좋아요                                                                                                                                                                                                             |
| **discovery**    | 셀러 샵, 팔로우, 위시리스트, 개인화 피드                                                                                                                                                                                                |
| **review**       | 거래 후기·셀러 평점                                                                                                                                                                                                                     |
| **media**        | **이미지 업로드(단일/다중, MinIO)** + 백엔드 스트리밍 공개                                                                                                                                                                              |
| **admin**        | **운영자 콘솔(ADMIN 전용)** — 대시보드·GMV 집계, 신고 큐, 매물·게시글 모더레이션(사유 필수), 회원 정지·권한(세션 즉시 폐기), 카탈로그 관리, **append-only 감사 로그**. 같은 사이트·같은 로그인으로 진입하며 일반 화면에서도 인라인 조치 |

---

## Repository Layout

```
GoLe/
├── apps/
│   ├── web/                     # Next.js 16 프론트엔드 (FSD)
│   │   └── src/{app,views,widgets,features,entities,shared}
│   └── api/                     # Spring Boot 4 백엔드 (헥사고날)
│       └── src/main/java/com/gole/api/<context>/{domain,application,adapter}
├── .kiro/
│   ├── specs/                   # SDD 스펙 (requirements/design/tasks)
│   └── steering/                # 배포·컨벤션·브랜드·인프라 가이드
├── docker-compose.yml           # mongo(rs0) + redis + minio
├── pnpm-workspace.yaml
└── package.json
```

### 아키텍처 규약 (요약)

- **백엔드(헥사고날)**: `domain → application(port in/out) → adapter`. 컨텍스트 간 연동은 **상대 컨텍스트의 인바운드 포트(UseCase)에만** 의존. 도메인은 프레임워크 무의존.
- **프론트(FSD)**: `app → views → widgets → features → entities → shared` 단방향 의존. 슬라이스는 `index.ts` 공개 API로만 접근(cross-import 금지). `eslint-plugin-boundaries` + `steiger`로 강제.
- 상세: `apps/web/src/ARCHITECTURE.md`, `.kiro/steering/dev-conventions.md`.

---

## Getting Started

필수 환경: Node.js 22+, pnpm 10.30.3, Java 21, Docker Compose 2.20+.

```bash
# 최초 1회
pnpm install

# 인프라 기동 (MongoDB rs0 + Redis + MinIO)
pnpm infra:up

# 터미널 1: 백엔드 (Java 21, http://localhost:8080)
pnpm dev:api

# 터미널 2: 프론트엔드 (http://localhost:3000)
pnpm dev:web
```

루트 스크립트 `pnpm dev:api`는 OS에 맞는 Gradle Wrapper와 로컬 Spring 프로필을 자동
적용한다. 기본 포트에서는 별도 환경 파일이 필요 없다. 다른 프로젝트와 포트가 겹칠 때만
팀 공통 예시를 개인 파일로 복사해 필요한 값을 변경한다.

```bash
cp .env.example .env
cp apps/web/.env.example apps/web/.env.development.local
```

`.env`는 Docker Compose와 백엔드가 함께 읽고, `.env.development.local`은 Next.js 개발
서버에서만 읽는다. 두 개인 파일은 Git에서 제외된다. 기본 접속 주소는 다음과 같다.

운영 Secret의 최신 외부 연동 자격증명을 맥 개발환경에 맞춰 안전하게 동기화하려면 아래
명령을 사용한다. 운영 DB·스토리지 주소는 복사하지 않고, 로컬 MinIO를 19000/19001 포트로
격리하며 실제 결제·문자·메일·Discord·배송조회·정산은 모두 비활성으로 강제한다.

```bash
pnpm sync:dev-env
```

적용한 Secret 버전과 원문 해시는 `.env.gcp-version`에 남는다. 기존 `.env`가 있으면
`.env.backup.<UTC 시각>`으로 보관한 뒤 원자적으로 교체한다. 출력에는 비밀값을 남기지 않는다.

| 서비스              | 주소                                                                                                                  |
| ------------------- | --------------------------------------------------------------------------------------------------------------------- |
| Frontend            | `http://localhost:3000`                                                                                               |
| Backend / Swagger   | `http://localhost:8080` / `http://localhost:8080/swagger-ui.html`                                                     |
| MongoDB / Redis     | `localhost:27017` / `localhost:6379`                                                                                  |
| MinIO API / Console | 기본 `http://localhost:9000` / `http://localhost:9001`, 동기화 후 `http://localhost:19000` / `http://localhost:19001` |

### 품질 게이트

```bash
# 프론트
pnpm --filter web typecheck     # tsc --noEmit
pnpm --filter web lint          # eslint (boundaries)
pnpm --filter web fsd:lint      # steiger (FSD 구조)
pnpm --filter web build         # next build
pnpm --filter web e2e           # Playwright

# 백엔드
cd apps/api && ./gradlew test            # 단위 테스트
cd apps/api && ./gradlew integrationTest # Testcontainers (Docker 필요)
```

E2E 사전 조건: 인프라(`pnpm infra:up`)와 **API 서버(`pnpm dev:api`)가 따로 떠 있어야** 한다.
web dev 서버는 Playwright가 자동 기동한다. 쓰기 플로우(`create-listing`·`purchase`)는 서버가
검증하는 실제 세션이 필요하므로 `pnpm e2e:seed`로 계정·세션을 한 번 심어야 한다(멱등).
`E2E_BASE_URL`을 지정하면 배포 대상 읽기전용 검증이 되고 쓰기 플로우는 자동 skip된다.

---

## API 개요

모든 경로는 `/api/v1/...` 프리픽스(관리자만 `/api/admin`). 오류는 `{ "code", "message" }` + HTTP 상태.

| 컨텍스트    | 대표 엔드포인트                                                                                                                                         |
| ----------- | ------------------------------------------------------------------------------------------------------------------------------------------------------- |
| account     | `POST /accounts` · `POST /accounts/verification` · `POST /accounts/sessions`(로그인) · `DELETE /accounts/sessions`(로그아웃) · `GET /accounts/me`       |
| social auth | `GET /auth/oauth/providers` · `POST /auth/oauth/{provider}/authorize-url` · `POST /auth/oauth/{provider}/callback`                                      |
| catalog     | `GET /catalog/sets/featured` · `GET /catalog/sets/{setNumber}` · `GET /catalog/sets?query=`                                                             |
| listing     | `POST /listings` · `GET /listings?query&condition&minPrice&maxPrice&sort` · `GET /listings/{id}` · `POST /listings/{id}/sold` · `DELETE /listings/{id}` |
| order       | `POST /orders` · `POST /orders/{id}/payment` · `POST /orders/{id}/completion` · `POST /orders/{id}/refund` · `GET /orders/{id}`                         |
| pricing     | `GET /pricing/sets/{setNumber}/statistics·chart·history` · `GET /pricing/trending?limit=`                                                               |
| collection  | `GET /collections/{userId}/items` · `POST /collections/items` · `GET /collections/{userId}/estimate`                                                    |
| community   | `GET\|POST /community/posts` · `POST /community/posts/{id}/likes` · `GET\|POST /community/posts/{id}/comments`                                          |
| discovery   | `/shops/{sellerId}` · `/users/{userId}/following` · `/users/{userId}/feed` · `/users/{userId}/wishlist`                                                 |
| review      | `POST /reviews` · `GET /sellers/{sellerId}/reviews·rating`                                                                                              |
| media       | `POST /media/images` · `POST /media/images/batch` · `GET /media/images/{key}`(스트리밍)                                                                 |
| admin       | `GET /admin/overview` · `GET\|POST /admin/catalog/sets`                                                                                                 |

### 소셜 로그인 활성화 (토큰만 주입하면 동작)

1. 각 provider 콘솔에서 OAuth 앱 생성 → redirect URI `https://gole.co.kr/auth/callback/{google|kakao|naver}` 등록.
2. 백엔드 컨테이너 환경변수 주입: `GOOGLE_OAUTH_CLIENT_ID`/`GOOGLE_OAUTH_CLIENT_SECRET` (카카오·네이버는 `KAKAO_*`/`NAVER_*`).
3. Control에서 `gole-production-env`의 새 immutable Secret Manager version을 발행하고 **Actions → Secret Sync**로 exact version을 적용한 뒤 CD에서 이미지를 재빌드하면 해당 버튼이 자동 활성화된다. 서버 파일을 직접 편집하지 않는다. (전체 키: `apps/api/src/main/resources/application.yml`의 `oauth.providers`)

### 선택적 이용 분석(GA4/GTM)

`NEXT_PUBLIC_GA_MEASUREMENT_ID`와 `NEXT_PUBLIC_GTM_ID`는 선택적인 공개 빌드 변수다. 둘 다 비우면
동의 UI, 분석용 브라우저 저장소 접근, Google 스크립트와 네트워크 요청이 모두 비활성화된다. 값의
형식이 각각 `G-...`, `GTM-...`가 아니면 빌드가 실패한다.

분석 ID가 있어도 Google 태그는 이용자가 화면에서 **분석 허용**을 선택한 뒤에만 로드한다. 거부해도
기능 제한이 없고, 푸터의 **분석 설정**에서 언제든 철회하거나 선택을 초기화할 수 있다. 철회하면
알려진 `_ga` 계열 쿠키를 지우고 새로고침해 이미 실행된 태그의 후속 전송도 끊는다.

두 ID를 모두 설정하면 코드가 **GTM만** 선택하며 직접 `gtag.js`를 함께 로드하지 않는다. GTM
컨테이너 안에서도 페이지 조회 태그를 하나만 두어야 한다. 속성·컨테이너 생성부터 게시 전 검증까지의
필수 운영 계약은 [`docs/operations/analytics-consent.md`](docs/operations/analytics-consent.md)를
따른다.

### 결제(PortOne) 활성화

초기 공개는 `NEXT_PUBLIC_PAYMENT_MODE=disabled`, `PORTONE_ENABLED=false`,
`GOLE_SETTLEMENT_MODE=DISABLED`, Launch Stage 0~1로 운영한다. 이 상태에서는 결제 버튼과 API를
모두 닫고 매물·컬렉션·커뮤니티·문의 기능만 제공한다. 스텁 승인은 local·development·dev·test·e2e
환경에서만 허용되며, staging·production에서 스텁 어댑터가 호출되면 실패하도록 막혀 있다.

> ⚠️ **프론트 키는 빌드 타임에 번들에 박힌다.** `NEXT_PUBLIC_*`은 `next build` 시점의 환경변수를
> 읽어 정적 인라인되므로, 실행 중인 컨테이너의 환경변수만 바꿔서는 **반영되지 않는다**.
> `gole-production-env`의 새 immutable Secret Manager version을 적용한 뒤 CD에서 프론트 이미지를 반드시 **재빌드**해야 한다. GitHub repository variables는 PortOne/GA/GTM 설정의 source of truth가 아니다.

| 변수                                       | 위치   | 주입 시점     | 비고                                                                                        |
| ------------------------------------------ | ------ | ------------- | ------------------------------------------------------------------------------------------- |
| `NEXT_PUBLIC_PAYMENT_MODE`                 | 프론트 | **빌드 타임** | `disabled`·`stub`·`portone-test`·`portone-live`. 미설정 시 개발은 `stub`, 운영은 `disabled` |
| `NEXT_PUBLIC_PORTONE_STORE_ID`             | 프론트 | **빌드 타임** | 공개 가능                                                                                   |
| `NEXT_PUBLIC_PORTONE_CHANNEL_KEY`          | 프론트 | **빌드 타임** | 공개 가능                                                                                   |
| `PORTONE_ENABLED`                          | 백엔드 | 런타임        | `true`면 실연동, 초기 공개는 `false`(공개 환경에서는 스텁 승인 없음)                        |
| `PORTONE_API_SECRET`                       | 백엔드 | 런타임        | **서버 전용 비밀값. 프론트·저장소 금지**                                                    |
| `PORTONE_WEBHOOK_SECRET`                   | 백엔드 | 런타임        | **서버 전용 비밀값.** 웹훅 서명 검증에 쓴다                                                 |
| `PORTONE_STORE_ID` · `PORTONE_CHANNEL_KEY` | 백엔드 | 런타임        | 원장 검증에서 프론트 값과 일치하는지 확인한다                                               |
| `PORTONE_CHANNEL_TYPE`                     | 백엔드 | 런타임        | 기본 `TEST`. 실채널은 `LIVE`                                                                |

카카오페이와 카드 채널을 서로 다른 채널 키로 설정할 수 있다. 어댑터는 원장의 결제수단,
상점·채널 키·금액·채널 유형을 모두 확인한 뒤에만 승인한다. 카드 채널 키를 비우면 카카오페이만
노출한다.

초기 공개 배포는 CD·Compose·운영 환경 검증기가 `PORTONE_ENABLED=false`,
`NEXT_PUBLIC_PAYMENT_MODE=disabled`, `GOLE_SETTLEMENT_MODE=DISABLED`를 강제한다. 이때 PortOne
웹훅 컨트롤러는 등록되지 않아 404가 되고, 기존 주문의 환불 요청도 주문 상태를 바꾸기 전에
거부한다. 과거 Secret이나 GitHub variable만으로 결제가 다시 열리지 않는다.

활성화는 Launch Stage 0~1을 유지한 채 아래 준비를 마친 뒤, 운영 정책을 바꾸는 별도 코드 리뷰와
배포로만 진행한다. 관리자 대시보드에서 준비 상태를 확인한 뒤에만 Stage 2로 올린다.

```bash
# 1) 공개 빌드 키와 서버 비밀값을 함께 Control에서 Secret Manager의 새 immutable version으로 발행한다
NEXT_PUBLIC_PAYMENT_MODE=portone-test
NEXT_PUBLIC_PORTONE_STORE_ID=store-...
NEXT_PUBLIC_PORTONE_CHANNEL_KEY=channel-key-...
PORTONE_ENABLED=true
PORTONE_API_SECRET=...
PORTONE_WEBHOOK_SECRET=...
PORTONE_STORE_ID=store-...
PORTONE_CHANNEL_KEY=channel-key-...

# 2) Secret Sync로 exact version을 적용한다. 값은 runner나 명령 인자에 노출하지 않는다.

# 3) CD/Compose/validator의 disabled 고정을 함께 바꾸는 PR을 리뷰하고 main CI/CD로 재빌드·배포한다

# 4) 포트원 콘솔에 웹훅 등록
#    https://<도메인>/api/v1/payments/portone/webhook

# 5) 관리자 결제 준비 상태와 최소 금액 실결제·환불을 확인한 뒤 Stage 2로 전환한다
```

관리자 대시보드의 결제 준비 상태(`GET /admin/overview`의 `paymentReadiness`)로도 설정 누락을
설정값 원문 없이 확인할 수 있다.

결제 흐름은 verify-on-server다. 브라우저가 결제 후 서버에 알리고, 서버가 포트원에 **직접
재조회**해 `status=PAID`와 금액·상점·채널·결제수단 일치를 확인한 뒤에만 자금 보유로 전이한다.
금액 불일치나 알 수 없는 상태는 자동 실패시키지 않고 `PAYMENT_REVIEW`로 보존해 운영 검토함에
올린다. TTL이 지난 결제 대기 주문은 `PaymentReconciliationScheduler`가 원장과 대조해 정리한다.

미구현: 부분 환불(전액 취소만 가능). **판매자 지급 실행도 자동화되어 있지 않다** — 완료 주문의
정산 전표는 `settlements` 원장에 멱등 생성되지만(`MongoSettlementAdapter`), 실제 이체는
관리자가 지급 증빙 번호를 입력해 수동 확정한다(`/admin/settlements`). 플랫폼이 자금을 직접
보관·송금하는 구조는 전자금융 관련 등록 문제가 따르므로, 포트원 "파트너 정산 자동화" 같은
PG의 하위 판매자 정산 대행을 먼저 검토해야 한다.

> 카카오페이는 PG사 정책상 **에스크로 결제를 지원하지 않는다.** 통신판매업 신고에 필요한
> 구매안전서비스를 붙일 때는 카드 PG 쪽을 써야 한다.

### 운영팀 문의

문의는 외부 Discord 초대 링크가 아니라 인앱 채팅(`/chat?compose=support`)으로 들어온다.
(2026-08-30, `NEXT_PUBLIC_DISCORD_INVITE_URL`로 지정하던 고래방 초대 링크 버튼을 대체함)

### Discord 운영 관제

백엔드는 회원가입, 주문·결제·환불, PortOne 웹훅 이상, 애플리케이션 기동, 처리되지 않은 500 오류를
구조화된 Discord embed로 전송한다. 비밀번호·세션 토큰·이메일·결제 비밀값은 전송하지 않는다.
앱이 완전히 내려간 경우까지 감지하도록 `.github/workflows/production-health.yml`이 매시간 외부에서
readiness를 확인한다. 성공 알림은 보내지 않고 실패할 때만 운영 Discord로 알려 중복 알림을 줄인다.

```bash
# root 전용 Discord overlay(`/etc/gole/discord.env`, root:root 0600)
GOLE_DISCORD_ALERTS_ENABLED=true
DISCORD_DEPLOY_WEBHOOK_URL=https://discord.com/api/webhooks/...
DISCORD_OPERATIONS_WEBHOOK_URL=https://discord.com/api/webhooks/...
DISCORD_ACCOUNT_WEBHOOK_URL=https://discord.com/api/webhooks/...
DISCORD_PAYMENT_WEBHOOK_URL=https://discord.com/api/webhooks/...
DISCORD_SUPPORT_WEBHOOK_URL=https://discord.com/api/webhooks/...
DISCORD_SUPPRESS_NOTIFICATIONS=false
```

- GitHub Actions secret `DISCORD_CI_WEBHOOK_URL`: main CI 결과 알림
- GitHub Actions secret `DISCORD_OPERATIONS_WEBHOOK_URL`: 운영 헬스체크 실패 알림
- GitHub Actions secrets `DISCORD_ACCOUNT_WEBHOOK_URL`, `DISCORD_PAYMENT_WEBHOOK_URL`: 가입·결제 알림 경로(필수)
- GitHub Actions secret `DISCORD_SUPPORT_WEBHOOK_URL`: 문의 알림 경로(미설정 시 operations URL 사용)
- GitHub Actions secret `DISCORD_DEPLOY_WEBHOOK_URL`: 배포 알림 경로(미설정 시 operations URL 사용)
- webhook URL은 Discord 채널 설정 → 연동 → 웹후크에서 만들며 저장소나 채팅에 붙여 넣지 않는다.

역할별 목적지는 같은 GoLe Discord 채널이어도 된다. `CD`와 `Secret Sync`는 위 값을 stdin으로
고정된 root helper에 전달하고, helper가 strict Discord webhook URI를 검증한 뒤
`/etc/gole/discord.env`를 원자적으로 갱신한다. runner는 이 파일을 읽을 수 없다. Compose는
runner 환경 대신 이 root-owned overlay만 사용한다. 컨테이너를 손으로 재생성하지 말고
self-hosted workflow를 통해 배포한다.

### 문의 분류 보조(LangGraph · gRPC)

문의 첫 메시지는 백엔드가 내부 gRPC 서비스에 전달해 카테고리·우선순위·관리자 답변 초안을
만든다. 현재 `rules-v1`은 외부 LLM/API를 호출하지 않는 결정론적 LangGraph이며, 원문·제목·사용자
식별자를 로그나 Discord에 보내지 않는다. 결과는 관리자 검토용일 뿐 자동 답변이나 자동 종결에
사용하지 않는다. 서비스가 느리거나 내려가도 문의 접수는 정상 완료된다. 분석 작업에는 원문을
복제하지 않고 방 ID만 저장하며, 기존 문의방 제목과 문의자의 첫 메시지를 다시 읽어 최대 5회
지수 백오프로 재시도한다. 큐 포화·백엔드 재시작 중 남은 작업과 30초 임대가 만료된 작업은
스케줄러가 회수하므로 문의가 영구 처리 중 상태에 남지 않는다.

```bash
# Mac에서 필요할 때만 실행(기본 infra:up에는 포함되지 않음)
pnpm infra:agent:up

# 로컬 API를 연결할 때
GOLE_SUPPORT_AGENT_ENABLED=true \
GOLE_SUPPORT_AGENT_GRPC_TARGET=localhost:50051 \
pnpm dev:api
```

GCP Compose에서는 외부 포트를 열지 않고 `support-agent:50051` 내부 네트워크로만 연결한다.
워커 2개, CPU 0.25, 메모리 192MiB 상한을 기본값으로 두어 현재 VM 고정요금 안에서 운영한다.
외부 모델은 개인정보처리방침에 처리업체·리전·보관정책을 먼저 고지하고 별도 기능 플래그와
계약 테스트를 추가하기 전까지 연결하지 않는다.

공개 서버를 `GOLE_ENVIRONMENT=production`으로 전환하려면 실제 SMTP 설정이 필요하다.
결제는 초기 이용자 모집 기간에 `PORTONE_ENABLED=false`로 유지할 수 있으며, 이때 프론트도
`NEXT_PUBLIC_PAYMENT_MODE=disabled`, 정산도 `DISABLED`, Launch Stage도 0~1로 맞춘다.
PortOne 비밀값은 결제를 실제로 열기 직전에만 등록하고 위 단계식 준비 확인을 거친다.

CoolSMS 채널과 승인된 알림톡 템플릿이 없는 초기 공개는
`GOLE_ONBOARDING_PHONE_REQUIRED=false`로 배포한다. 온보딩 상태 API가 이 정책을 내려주므로
웹은 닉네임 → 관심 태그 → 동의의 3단계만 표시하며, 로그인 응답과 서버의 거래성 액션 가드도
동일한 완료식을 쓴다. 나중에 전화 인증을 켤 때는 `COOLSMS_ENABLED=true`와
`GOLE_ONBOARDING_PHONE_TEMPLATE_ID`를 동시에 갖춰야 공개 서버가 기동한다. 로깅 어댑터는
production/staging에서 항상 실패하며 OTP 원문 로그는 로컬에서도 기본 비활성이다.

---

## Specs (SDD)

기능별 스펙은 `.kiro/specs/<기능>/`에 보관한다.

| 스펙                          | 내용                                              |
| ----------------------------- | ------------------------------------------------- |
| `lego-marketplace`            | 전체 도메인 requirements/design/tasks (단일 기준) |
| `image-upload`                | 이미지 업로드(단일/다중, MinIO)                   |
| `trending-sets`               | 인기 세트 랭킹 + Redis 캐싱                       |
| `social-login`                | OAuth2 소셜 로그인                                |
| `condition-disclosure`        | 매물 컨디션·구성 고지                             |
| `ip-safe-content`             | IP(저작권) 안전 콘텐츠 정책                       |
| `storefront-and-presentation` | 셀러 샵·프레젠테이션                              |
| `admin-and-payments`          | 운영자·결제                                       |
| `review`                      | 거래 후기·평점                                    |
| `design-system`               | UI 디자인 시스템·브랜드 토큰                      |

---

## 배포

운영 대상은 GCP의 단일 `gole-production` VM 하나뿐이다. 현재 Mac 작업공간은 로컬 개발용이며,
예전 `gole.kscold.com` 서버는 배포·DNS·GitHub Actions 대상에 포함하지 않는다.

GCP `gole-production` VM에서 Backend, Frontend, MongoDB, Redis, MinIO, Nginx,
Certbot, 비용 릴레이를 Docker Compose로 구동한다. Nginx가 `gole.co.kr`을 프록시하고
Google Trust Services 인증서를 제공한다. 재현·이전 절차는 `infra/gcp/README.md`에 있다.

`main` 브랜치의 CI가 성공하면 GCP VM의 저장소 전용 self-hosted runner가 성공한 커밋 SHA를
정확히 checkout하고 `scripts/deploy.sh all`을 실행한다. 이 스크립트는 Compose 이미지를 빌드하고
컨테이너를 `--wait`로 갱신한 뒤 내부·HTTPS readiness를 검증한다. GitHub의
**Actions → CD → Run workflow**에서 수동 재배포도 가능하다.

```bash
# 로컬: 보호된 main에 직접 push하지 않고 피처 브랜치 → PR로 병합
git switch -c feat/<작업명>
git push --force-with-lease origin feat/<작업명>
gh pr create --base main --head feat/<작업명>

# GCP VM: root-owned marker와 런타임 전체 계약을 값 노출 없이 확인
gcloud compute ssh gole-production --zone asia-northeast3-a --tunnel-through-iap \
  --command='sha="$(sudo -n /usr/local/sbin/gole-hostctl deployment-read-sha)"; sudo -n /usr/local/sbin/gole-hostctl deployment-verify-runtime "$sha"'
```

GoLe 운영 Ubuntu에는 IAP SSH로 접속한다.

```bash
gcloud compute ssh gole-production \
  --project project-72a52bf1-06aa-4519-b2c \
  --zone asia-northeast3-a --tunnel-through-iap
cd /app
```

### 운영 레거시 데모 콘텐츠 비활성화

초기 PoC 시드가 운영 DB에 남아 있으면 존재하지 않는 데모 판매자에게 주문·대화를 시작할 수
있다. `scripts/migrate-production-demo-content.js`는 알려진 데모 seller/author의 매물과 게시글을
삭제하지 않고 `DELETED`로 바꾼다. 제목·본문·이메일은 출력하지 않으며 기본 실행은 항상
dry-run이다. 현재 소스의 매물 11건 또는 이전 운영 시드 13건, 게시글 8건 분포만 허용한다.

```bash
# 1) VM의 /app에서 대상·참조만 확인 — 환경변수 없이 실행하면 변경하지 않음
sudo docker compose --env-file /etc/gole/infra.env --env-file /etc/gole/gole.env \
  -f infra/gcp/docker-compose.yml exec -T mongo \
  mongosh --host mongo:27017 --quiet gole < scripts/migrate-production-demo-content.js

# 2) 출력 건수를 검토한 뒤 명시적으로 적용
sudo docker compose --env-file /etc/gole/infra.env --env-file /etc/gole/gole.env \
  -f infra/gcp/docker-compose.yml exec -T -e DRY_RUN=false mongo \
  mongosh --host mongo:27017 --quiet gole < scripts/migrate-production-demo-content.js

# 3) 같은 dry-run을 다시 실행해 예상 변경이 매물 0건·게시글 0건인지 확인
```

대상 매물을 참조하는 `orders`, `chat_rooms`, `social_chat_rooms` 문서가 하나라도 있으면 원칙적으로
어떤 문서도 바꾸지 않고 실패한다. 단, 2026-09-04 운영 점검에서 메시지·주문·거래확정·구매자
계정이 모두 없음을 확인한 정확한 고아 채팅방 1건은 방을 보존한 채 해당 매물만 비활성화하도록
ID와 조건을 함께 고정했다. 조건 하나라도 달라지면 다시 전체 변경을 거부한다. 출력된 비식별
`listingId`와 참조 건수를 기준으로 주문·대화 이력을 먼저 검토하며, 임의 참조 삭제나 일반화된
우회는 허용하지 않는다. 실행 전에는 `infra/gcp/scripts/backup-data.sh`로 MongoDB 백업을 남긴다.

마이그레이션 안전장치와 로컬 데모 계정 참조 무결성은 다음처럼 따로 재검증할 수 있다.

```bash
node --test scripts/__tests__/migrate-production-demo-content.test.mjs
cd apps/api && ./gradlew test --tests com.gole.api.account.bootstrap.DemoContentAccountSeederTest
```

## 커밋 컨벤션

제목은 `인프라: 운영 배포 검증을 강화`처럼 변경 영역과 내용을 **한국어로** 작성한다.
본문의 모든 항목은 `- `로 시작하고 `함`으로 끝낸다. 백엔드/프론트는 **레이어별로 커밋을
분리**한다. 상세는 `.kiro/steering/dev-conventions.md`에 있다.

```text
인프라: 운영 배포 검증을 강화

- Compose 설정의 유효성을 CI에서 확인함
- 운영 readiness를 매시간 외부에서 확인함
```
