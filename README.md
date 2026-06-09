# GoLe — LEGO Marketplace 🐳🧱

레고 중고거래 플랫폼. **KREAM**(시세·검수·안전거래) · **당근**(동네 직거래) · **콜리**(컬렉션·자랑) · **후르츠패밀리**(셀러 샵·팔로우·큐레이션)의 강점을 결합한 모노레포 프로젝트.

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
| **admin** | 운영 대시보드 집계, 카탈로그 관리(ADMIN 전용) |

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
├── docker-compose.yml           # mongo(rs0) + redis
├── pnpm-workspace.yaml
└── package.json
```

### 아키텍처 규약 (요약)

- **백엔드(헥사고날)**: `domain → application(port in/out) → adapter`. 컨텍스트 간 연동은 **상대 컨텍스트의 인바운드 포트(UseCase)에만** 의존. 도메인은 프레임워크 무의존.
- **프론트(FSD)**: `app → views → widgets → features → entities → shared` 단방향 의존. 슬라이스는 `index.ts` 공개 API로만 접근(cross-import 금지). `eslint-plugin-boundaries` + `steiger`로 강제.
- 상세: `apps/web/src/ARCHITECTURE.md`, `.kiro/steering/dev-conventions.md`.

---

## Getting Started

```bash
# 0. 인프라 기동 (MongoDB rs0 + Redis)
docker compose up -d

# 1. 프론트엔드
pnpm install
pnpm --filter web dev          # http://localhost:3000

# 2. 백엔드 (Java 21)
cd apps/api && ./gradlew bootRun   # http://localhost:8080
```

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

```bash
# 로컬: 커밋 → push
git push origin main
# 컨테이너: git pull → 빌드 → pm2 reload → health
DOCKER_HOST=unix:///Users/kscold/.colima/default/docker.sock \
  docker exec ubuntu-gole bash -lc "cd /app && bash scripts/deploy.sh all"
```

## 커밋 컨벤션

`<type>(<scope>): <한국어 설명>` — type: `feat|fix|refactor|docs|chore`, scope: `backend|frontend|infra|spec`. 백엔드/프론트는 **레이어별로 커밋을 분리**한다. 상세는 `.kiro/steering/dev-conventions.md`.
