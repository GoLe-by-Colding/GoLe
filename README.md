# GoLe — LEGO Marketplace 🐳🧱

레고 중고거래에 특화된 모노레포 플랫폼. 체결가 기반 **시세**와 에스크로 **안전거래**, 동네 **직거래**, 보유/희망 **컬렉션**, 셀러 **샵·팔로우**와 커뮤니티를 한곳에서 제공한다.

> **GoLe = 고래(Whale) × 레고(Brick)** — 깊은 바다(Cobalt)와 브릭 옐로의 톤. 자세한 컨셉은 `.kiro/steering/brand-identity.md`.

- 🌐 운영: **https://gole.kscold.com**
- 🧩 아키텍처: 백엔드 **헥사고날(Hexagonal)** + 프론트 **FSD(Feature-Sliced Design)**
- 📐 개발 방식: **SDD(Spec-Driven Development)** — 모든 기능은 `.kiro/specs/<기능>/`에 스펙 먼저

---

## Stack

| 영역 | 기술 |
| --- | --- |
| Frontend | Next.js 16 (App Router, React 19, Turbopack), TypeScript strict, FSD, Playwright E2E |
| Backend | Spring Boot 4 (Spring Framework 7), Java 21 LTS, 헥사고날 + AOP |
| Data | MongoDB (primary, replica set `rs0` → 멀티도큐먼트 트랜잭션), Redis (캐시/랭킹) |
| Storage | MinIO (S3 호환) — 이미지 업로드 |
| Infra | Docker(Colima) + PM2, nginx 리버스 프록시, Let's Encrypt HTTPS, pnpm workspace |

> **저장소 전략**: 초기엔 MongoDB + Redis. 정산 도메인이 강한 관계형 정합성을 요구하면, 헥사고날 포트/어댑터 덕분에 해당 컨텍스트에만 PostgreSQL 어댑터를 추가할 수 있다.

---

## 주요 기능

| 도메인(컨텍스트) | 기능 |
| --- | --- |
| **account** | 이메일 회원가입·인증, 로그인(불투명 세션 토큰), **로그아웃(서버 세션 폐기)**, **소셜 로그인(Google/Kakao/Naver OAuth2)**, RBAC(USER/ADMIN), BCrypt 해시(레거시 자동 승격), 로그인 잠금 |
| **catalog** | 레고 세트 카탈로그(검색·추천·조회) |
| **listing** | 중고 매물 등록·검색·필터·상태 전이(ACTIVE→RESERVED→SOLD/DELETED), 컨디션·구성 고지 |
| **order** | 에스크로 안전거래(결제대기→자금보유→완료/환불), 상태 이력, 낙관적 락 |
| **pricing** | 체결가 기반 시세 통계·차트·이력, **인기 세트 랭킹(Redis 캐싱)** |
| **collection** | 보유/희망 컬렉션, 추정가 |
| **community** | 자랑/MOC 피드, 댓글, 좋아요 |
| **discovery** | 셀러 샵, 팔로우, 위시리스트, 개인화 피드 |
| **review** | 거래 후기·셀러 평점 |
| **media** | **이미지 업로드(단일/다중, MinIO)** + 백엔드 스트리밍 공개 |
| **admin** | **운영자 콘솔(ADMIN 전용)** — 대시보드·GMV 집계, 신고 큐, 매물·게시글 모더레이션(사유 필수), 회원 정지·권한(세션 즉시 폐기), 카탈로그 관리, **append-only 감사 로그**. 같은 사이트·같은 로그인으로 진입하며 일반 화면에서도 인라인 조치 |

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

| 서비스 | 주소 |
|---|---|
| Frontend | `http://localhost:3000` |
| Backend / Swagger | `http://localhost:8080` / `http://localhost:8080/swagger-ui.html` |
| MongoDB / Redis | `localhost:27017` / `localhost:6379` |
| MinIO API / Console | `http://localhost:9000` / `http://localhost:9001` |

### 품질 게이트

```bash
# 프론트
pnpm --filter web typecheck     # tsc --noEmit
pnpm --filter web lint          # eslint (boundaries)
pnpm --filter web fsd:lint      # steiger (FSD 구조)
pnpm --filter web build         # next build
pnpm --filter web e2e           # Playwright 전체(브라우저)
pnpm --filter web exec playwright test --project=unit  # 순수 로직만(브라우저 없이, 빠름)

# 백엔드
cd apps/api && ./gradlew test            # 단위 테스트
cd apps/api && ./gradlew integrationTest # Testcontainers (Docker 필요)
```

---

## API 개요

모든 경로는 `/api/v1/...` 프리픽스(관리자만 `/api/admin`). 오류는 `{ "code", "message" }` + HTTP 상태.

| 컨텍스트 | 대표 엔드포인트 |
| --- | --- |
| account | `POST /accounts` · `POST /accounts/verification` · `POST /accounts/sessions`(로그인) · `DELETE /accounts/sessions`(로그아웃) · `GET /accounts/me` |
| social auth | `GET /auth/oauth/providers` · `GET /auth/oauth/{provider}/authorize-url` · `POST /auth/oauth/{provider}/callback` |
| catalog | `GET /catalog/sets/featured` · `GET /catalog/sets/{setNumber}` · `GET /catalog/sets?query=` |
| listing | `POST /listings` · `GET /listings?query&condition&minPrice&maxPrice&sort` · `GET /listings/{id}` · `POST /listings/{id}/sold` · `DELETE /listings/{id}` |
| order | `POST /orders` · `POST /orders/{id}/payment` · `POST /orders/{id}/completion` · `POST /orders/{id}/refund` · `GET /orders/{id}` |
| pricing | `GET /pricing/sets/{setNumber}/statistics·chart·history` · `GET /pricing/trending?limit=` |
| collection | `GET /collections/{userId}/items` · `POST /collections/items` · `GET /collections/{userId}/estimate` |
| community | `GET\|POST /community/posts` · `POST /community/posts/{id}/likes` · `GET\|POST /community/posts/{id}/comments` |
| discovery | `/shops/{sellerId}` · `/users/{userId}/following` · `/users/{userId}/feed` · `/users/{userId}/wishlist` |
| review | `POST /reviews` · `GET /sellers/{sellerId}/reviews·rating` |
| media | `POST /media/images` · `POST /media/images/batch` · `GET /media/images/{key}`(스트리밍) |
| admin | `GET /admin/overview` · `GET\|POST /admin/catalog/sets` |

### 소셜 로그인 활성화 (토큰만 주입하면 동작)

1. 각 provider 콘솔에서 OAuth 앱 생성 → redirect URI `https://gole.kscold.com/auth/callback/{google|kakao|naver}` 등록.
2. 백엔드 컨테이너 환경변수 주입: `GOOGLE_OAUTH_CLIENT_ID`/`GOOGLE_OAUTH_CLIENT_SECRET` (카카오·네이버는 `KAKAO_*`/`NAVER_*`).
3. `pm2 restart gole-backend --update-env` → 해당 버튼 자동 활성화. (전체 키: `apps/api/src/main/resources/application.yml`의 `oauth.providers`)

### 결제(PortOne) 활성화

미설정이면 `StubPaymentGatewayAdapter`가 **모든 결제를 무료로 승인**한다. 주문·정산 흐름은
전부 진짜로 돌지만 돈은 오가지 않는다. 데모 환경에서는 의도된 동작이고, 실결제를 받으려면
아래를 설정해야 한다.

> ⚠️ **프론트 키는 빌드 타임에 번들에 박힌다.** `NEXT_PUBLIC_*`은 `next build` 시점의 환경변수를
> 읽어 정적 인라인되므로, pm2 `env`나 `--update-env`로는 **반영되지 않는다**. 소셜 로그인처럼
> "환경변수 넣고 재시작"이 통하지 않는 유일한 항목이다. 반드시 **재빌드**해야 한다.

| 변수 | 위치 | 주입 시점 | 비고 |
|---|---|---|---|
| `NEXT_PUBLIC_PORTONE_STORE_ID` | 프론트 | **빌드 타임** | 공개 가능 |
| `NEXT_PUBLIC_PORTONE_CHANNEL_KEY` | 프론트 | **빌드 타임** | 공개 가능 |
| `PORTONE_ENABLED` | 백엔드 | 런타임 | `true`면 실연동, 기본 `false`(스텁) |
| `PORTONE_API_SECRET` | 백엔드 | 런타임 | **서버 전용 비밀값. 프론트·저장소 금지** |

환경별 권장 조합:

| 환경 | 채널 | 비고 |
|---|---|---|
| 개발자 로컬 | 미설정(스텁) 또는 테스트 채널 | 스텁이면 계정 없이 전체 흐름 검증 가능 |
| 개발 서버 | 테스트 채널 | 실제 결제창까지 뜨지만 돈은 안 나간다 |
| 운영 서버 | 실채널 | PG 심사 통과 후 |

활성화 순서 — **프론트를 먼저** 한다. 백엔드만 켜면 프론트가 결제창을 건너뛰는데 서버는
포트원에 조회해 결제 기록이 없으므로 **모든 결제가 실패**한다.

```bash
# 1) 프론트 키를 서버의 apps/web/.env.production 에 둔다 (.gitignore 대상)
NEXT_PUBLIC_PORTONE_STORE_ID=store-...
NEXT_PUBLIC_PORTONE_CHANNEL_KEY=channel-key-...

# 2) 프론트 재빌드 (반드시 빌드를 다시 해야 반영된다)
bash /app/scripts/deploy.sh frontend

# 3) 백엔드 환경변수 주입 후 재시작
PORTONE_ENABLED=true
PORTONE_API_SECRET=...
pm2 restart gole-backend --update-env

# 4) 포트원 콘솔에 웹훅 등록
#    https://<도메인>/api/v1/payments/portone/webhook
```

확인:

```bash
pm2 env gole-backend | grep -i portone   # 비어 있으면 스텁으로 동작 중
```

결제 흐름은 verify-on-server다. 브라우저가 결제 후 서버에 알리고, 서버가 포트원에 **직접
재조회**해 `status=PAID`와 금액 일치를 확인한 뒤에만 자금 보유로 전이한다. 웹훅도 같은 검증을
거치므로 서명 시크릿 없이도 위조 웹훅으로 결제가 확정되지 않는다.

미구현: 부분 환불(전액 취소만 가능), 간편결제 사업자는 카카오페이만 선택지에 있다.

### Discord 고래방

사이트의 고래방 버튼은 만료되지 않는 GoLe Discord 초대 링크로 연결된다. 배포 환경에서
`NEXT_PUBLIC_DISCORD_INVITE_URL`로 다른 초대 링크를 지정할 수 있다.

#### Discord 운영 관제

백엔드는 회원가입, 주문·결제·환불, PortOne 웹훅 이상, 애플리케이션 기동, 처리되지 않은 500 오류를
구조화된 Discord embed로 전송한다. 비밀번호·세션 토큰·이메일·결제 비밀값은 전송하지 않는다.
앱이 완전히 내려간 경우에는 `.github/workflows/production-health.yml`이 5분마다 readiness를 외부에서 확인한다.

```bash
# 백엔드/PM2 환경변수
GOLE_DISCORD_ALERTS_ENABLED=true
GOLE_ENVIRONMENT=production
DISCORD_WEBHOOK_URL=https://discord.com/api/webhooks/...             # 공통 fallback
DISCORD_ACCOUNT_WEBHOOK_URL=https://discord.com/api/webhooks/...     # 선택: 가입
DISCORD_PAYMENT_WEBHOOK_URL=https://discord.com/api/webhooks/...     # 선택: 결제
DISCORD_OPERATIONS_WEBHOOK_URL=https://discord.com/api/webhooks/...  # 선택: 오류/기동/배포
```

- GitHub Actions secret `DISCORD_CI_WEBHOOK_URL`: main CI 결과 알림
- GitHub Actions secret `DISCORD_OPERATIONS_WEBHOOK_URL`: 운영 헬스체크 실패 알림
- 서버 환경변수 `DISCORD_DEPLOY_WEBHOOK_URL`: 배포 스크립트 알림(미설정 시 operations URL 사용)
- webhook URL은 Discord 채널 설정 → 연동 → 웹후크에서 만들며 저장소나 채팅에 붙여 넣지 않는다.

---

## Specs (SDD)

기능별 스펙은 `.kiro/specs/<기능>/`에 보관한다.

| 스펙 | 내용 |
| --- | --- |
| `lego-marketplace` | 전체 도메인 requirements/design/tasks (단일 기준) |
| `image-upload` | 이미지 업로드(단일/다중, MinIO) |
| `trending-sets` | 인기 세트 랭킹 + Redis 캐싱 |
| `social-login` | OAuth2 소셜 로그인 |
| `condition-disclosure` | 매물 컨디션·구성 고지 |
| `ip-safe-content` | IP(저작권) 안전 콘텐츠 정책 |
| `storefront-and-presentation` | 셀러 샵·프레젠테이션 |
| `admin-and-payments` | 운영자·결제 |
| `review` | 거래 후기·평점 |
| `design-system` | UI 디자인 시스템·브랜드 토큰 |

---

## 배포

`ubuntu-gole` 컨테이너에서 PM2(`gole-backend`, `gole-frontend`)로 구동, nginx가 `gole.kscold.com`을 프록시(HTTPS). 표준 절차·명령은 `.kiro/steering/deploy.md`.

`main` 브랜치의 CI가 성공하면 저장소 전용 self-hosted runner가 `ubuntu-gole` 내부에서 CD를 자동 실행한다. GitHub의 **Actions → CD → Run workflow**에서 수동 배포도 가능하다.

```bash
# 로컬: 커밋 → push
git push origin main
# 컨테이너: git pull → 빌드 → pm2 reload → health
DOCKER_HOST=unix:///Users/kscold/.colima/default/docker.sock \
  docker exec ubuntu-gole bash -lc "cd /app && bash scripts/deploy.sh all"
```

GoLe Ubuntu에 직접 접속할 때는 다른 인스턴스 포트가 아닌 `2223`만 사용한다.

```bash
ssh -p 2223 root@localhost              # Mac mini 내부
ssh -p 2223 root@kscold.iptime.org      # 외부(공유기 포트포워딩 필요)
cd /app
```

## 커밋 컨벤션

`<type>(<scope>): <한국어 설명>` — type: `feat|fix|refactor|docs|chore`, scope: `backend|frontend|infra|spec`. 백엔드/프론트는 **레이어별로 커밋을 분리**한다. 상세는 `.kiro/steering/dev-conventions.md`.
