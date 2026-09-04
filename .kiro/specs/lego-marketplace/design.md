# GoLe Brick Marketplace — Design

> 본 문서는 `requirements.md`를 만족하는 시스템의 기술 설계다. 백엔드(헥사고날) · 프론트엔드(FSD) · 데이터 모델 · API 계약 · 횡단 관심사를 다룬다.

## 1. 아키텍처 개요

```
┌──────────────────────────────────────────────────────────────┐
│  Browser ── HTTPS ──> nginx (gole.co.kr)                       │
│                         ├── /  ──────────> Next.js (web :3000)  │
│                         └── /api, /actuator ─> Spring Boot (:8080)│
└──────────────────────────────────────────────────────────────┘
                                  │
                    MongoDB (rs0)  +  Redis
```

- **Frontend**: Next.js 16 (App Router, React 19), TypeScript strict, FSD. 정적/SSR 페이지에서 `apiRequest`로 백엔드 호출.
- **Backend**: Spring Boot 4 (Java 21), 헥사고날 아키텍처 + AOP. 10개 바운디드 컨텍스트.
- **Data**: MongoDB(primary, replica set rs0로 트랜잭션 지원), Redis(캐시/랭킹 등 확장 슬롯).
- **배포**: GCP `gole-production` 단일 VM에서 Docker Compose로 구동. 자세한 절차는 `.kiro/steering/deploy.md`와 `infra/gcp/README.md`를 따른다.

## 2. 백엔드 — 헥사고날 아키텍처

### 2.1 레이어 규약

```
com.gole.api.<context>/
├── domain/
│   ├── model/          # 순수 도메인 애그리거트/값객체/enum (프레임워크 의존 0)
│   └── exception/      # 도메인 예외 (DomainException 계열)
├── application/
│   ├── port/in/        # UseCase 인터페이스 (인바운드 포트)
│   ├── port/out/       # Repository/Gateway 인터페이스 (아웃바운드 포트)
│   ├── query/          # 검색 쿼리 객체 (예: ListingSearchQuery)
│   └── service/        # UseCase 구현 (@Service, 포트에만 의존)
└── adapter/
    ├── in/web/         # REST 컨트롤러 + Request/Response DTO
    └── out/            # persistence(Mongo), id, security, payment, pricing ...
```

### 2.2 의존성 규칙

- 의존 방향: `adapter → application → domain` (domain은 누구도 import하지 않음).
- **컨텍스트 간 연동은 상대 컨텍스트의 인바운드 포트(UseCase)에만 의존**한다. 예:
  - `order` → `listing`의 예약/판매 처리: `ListingReservationPort`(out) 구현체가 `listing`의 UseCase를 호출(`ListingReservationAdapter`).
  - `order` 완료 → `pricing` 기록: `ExecutedPriceRecorderPort`(out) → `pricing`의 `RecordExecutedPriceUseCase`.
  - `collection` 추정가 → `pricing` 최신가: `LatestPriceProviderPort`(out).
  - `discovery` 셀러 매물 → `listing` 조회: `ListingQueryPort`(out) → `listing`의 조회 UseCase.
  - `admin` → `catalog`의 `CreateLegoSetUseCase`/`ListLegoSetsUseCase`.
- 도메인 객체와 Mongo Document는 분리하며, 매핑은 persistence 어댑터의 책임이다.

### 2.3 컨텍스트별 핵심 설계

#### account
- 애그리거트 `Account`: 상태(`UNVERIFIED`/`VERIFIED`), 권한 `Role`(`USER`/`ADMIN`), 인증코드, 로그인 실패창/잠금 불변식 캡슐화.
  - 잠금 규칙: 15분 창 내 5회 실패 시 15분 잠금(`recordFailedSignIn` / `ensureNotLocked`).
- 값객체: `Email`, `PasswordHash`, `VerificationCode`.
- 아웃바운드 포트/어댑터: `AccountRepositoryPort`(Mongo), `PasswordHasherPort`(SHA-256), `SessionTokenPort`(opaque, Redis/메모리), `VerificationCodeGeneratorPort`(numeric), `VerificationCodeSenderPort`(로깅), `IdentifierGeneratorPort`(UUID).
- 인바운드 포트: `RegisterAccountUseCase`, `VerifyEmailUseCase`, `SignInUseCase`, `GetCurrentSessionUseCase`.

#### catalog
- 애그리거트 `LegoSet`(setNumber 식별, `RetirementStatus`).
- 포트: `LoadLegoSetPort`, (admin용) `CreateLegoSetUseCase`/`ListLegoSetsUseCase`, `FindLegoSetUseCase`, `SearchLegoSetsUseCase`, `ListFeaturedLegoSetsUseCase`.

#### listing
- 애그리거트 `Listing`: 상태 머신 `ACTIVE → RESERVED → (SOLD|ACTIVE)` / `ACTIVE → DELETED`.
  - 불변식: 사진 1장 이상 필수(`MissingPhotoException`), `RESERVED` 삭제 거부, `DELETED` 판매 거부, 가격 양수.
  - 값객체 `Money`, `ConditionDisclosure`(완전성/박스/설명서/누락·하자), enum `ItemCondition`, `Completeness`.
- 검색: `ListingSearchQuery`(query/condition/minPrice/maxPrice/`ListingSortOrder`).
- 인바운드 포트: Create/Get/Search/Browse/MarkSold/Delete/**Reserve/Release**(order 연동용).

#### order (안전거래/에스크로)
- 애그리거트 `Order`: 상태 머신
  ```
  PAYMENT_PENDING ──pay──> FUNDS_HELD ──complete──> COMPLETED
        │                       │
        └──fail──> PAYMENT_FAILED └──refund──> REFUNDED
  ```
  - 전이 불변식은 `requireStatus`로 강제, 모든 전이는 `OrderStatusChange` 이력에 적재.
  - 낙관적 락: `version`(@Version) 왕복으로 동시성 이중판매 방지(`OrderConcurrencyIntegrationTest`).
- 아웃바운드 포트: `ListingReservationPort`(예약/해제/판매), `PaymentGatewayPort`(Stub), `SettlementPort`(Stub), `ExecutedPriceRecorderPort`(→pricing), `OrderRepositoryPort`, `OrderIdGeneratorPort`.
- 트랜잭션: 주문 생성(주문 저장 + 매물 RESERVED), 완료(주문+매물 SOLD+정산+시세기록)는 Mongo 트랜잭션 경계.

#### pricing
- `PriceTransaction`(setNumber, amount, executedAt) 기록. `PriceStatistics`(min/max/avg/median/count) 계산.
- 인바운드: `RecordExecutedPriceUseCase`(order가 호출), `GetPriceInsightsUseCase`(statistics/chart/history).

#### collection
- `CollectionItem`(userId, setNumber, `OwnershipStatus`). 추정가는 `LatestPriceProviderPort`로 OWNED 합산.

#### community
- `Post`(type `GENERAL`/`MOC`, status `PUBLISHED`/`DELETED`, 이미지 필수, likes 집합), `Comment`.
- 좋아요 중복 방지(`DuplicateLikeException`), 작성자만 삭제.

#### discovery
- `Follow`(userId→sellerId, 중복방지), `WishlistEntry`(`LISTING`/`CATALOG_SET`, 중복방지).
- 개인화 피드: 팔로우 셀러들의 매물을 `ListingQueryPort`로 취합하고 Mongo에서 최신순·최대 100개로 제한한다. 프론트 `/feed`는 이 매물 피드와 커뮤니티 팔로잉 글 피드를 함께 보여주고 프로필·매물 대화로 연결한다.

#### review
- `Review`(orderId, reviewerId, rating, content) + 셀러 평점 집계(`SellerRating`).

#### admin
- `/api/admin/**`는 `AdminAuthInterceptor`가 ADMIN 권한 강제(요구사항 10.1).
- `overview`: 주요 컬렉션 `estimatedDocumentCount` 집계. 카탈로그 세트 목록/생성은 `catalog` UseCase 위임.

### 2.4 횡단 관심사 (common)
- `GlobalExceptionHandler` + `ErrorResponse{code,message}`: 도메인 예외 → HTTP 상태 매핑(NFR-1).
  - `NotFoundException`→404, `ConflictException`→409, `UnauthorizedException`→401, `ForbiddenException`→403, 검증 실패→400.
- `UseCaseLoggingAspect`(AOP): application.service 호출 로깅(횡단 관심사 분리).
- `MongoTransactionConfig`: `MongoTransactionManager` 등록. `TimeConfig`: `Clock` 주입(테스트 가능 시간). `WebCorsConfig`: CORS.

## 3. 데이터 모델 (MongoDB 컬렉션)

| 컬렉션 | 키 필드 | 비고 |
|---|---|---|
| `accounts` | `_id`(UUID), `email`(unique idx), status, role, passwordHash, verificationCode, failedAttempts, lockedUntil | 인증/잠금 상태 |
| `lego_sets` | `_id`=setNumber, name, theme, pieceCount, releaseYear, retirementStatus, imageUrl, featured | 카탈로그 |
| `listings` | `_id`, sellerId(idx), title, description, price, condition, disclosure{...}, photoUrls[], catalogSetNumber, status(idx), createdAt | 매물 |
| `orders` | `_id`, listingId, buyerId, sellerId, catalogSetNumber, amount, status, history[], `@Version` | 안전거래 |
| `price_transactions` | `_id`, setNumber(idx), amount, executedAt(idx) | 시세 이력 |
| `collection_items` | `_id`, userId(idx), setNumber, status | 컬렉션 |
| `posts` | `_id`, authorId, type, status, content, imageUrls[], likes[], createdAt | 커뮤니티 |
| `comments` | `_id`, postId(idx), authorId, content, createdAt | 댓글 |
| `follows` | `_id`, userId(idx), sellerId, (userId+sellerId unique) | 팔로우 |
| `wishlist_entries` | `_id`, userId(idx), targetType, targetId, (unique 조합) | 위시리스트 |
| `reviews` | `_id`, orderId, sellerId(idx), reviewerId, rating, content, createdAt | 후기 |

- `@Id` 필드에는 `@Indexed(unique=true)`를 추가하지 않는다(`_id`는 이미 unique).
- 금액은 `long`(KRW 최소단위)로 저장(NFR-5).

## 4. API 계약 (요약)

| 컨텍스트 | 메서드 & 경로 |
|---|---|
| account | `POST /api/v1/accounts`, `POST /api/v1/accounts/verification`, `POST /api/v1/accounts/sessions`, `POST /api/v1/accounts/sessions/refresh`, `GET /api/v1/accounts/me` |
| catalog | `GET /api/v1/catalog/sets/featured`, `GET /api/v1/catalog/sets/{setNumber}`, `GET /api/v1/catalog/sets?query=` |
| listing | `POST /api/v1/listings`, `GET /api/v1/listings?query&condition&minPrice&maxPrice&sort`, `GET /api/v1/listings/{id}`, `POST /api/v1/listings/{id}/sold`, `DELETE /api/v1/listings/{id}` |
| order | `POST /api/v1/orders`, `POST /api/v1/orders/{id}/payment`, `POST /api/v1/orders/{id}/completion`, `POST /api/v1/orders/{id}/refund`, `GET /api/v1/orders/{id}` |
| pricing | `GET /api/v1/pricing/sets/{setNumber}/statistics?from&to`, `/chart?from&to`, `/history` |
| collection | `GET /api/v1/collections/{userId}/items`, `POST /api/v1/collections/items`, `DELETE /api/v1/collections/items/{itemId}?userId=`, `GET /api/v1/collections/{userId}/estimate` |
| community | `GET /api/v1/community/posts`, `POST /api/v1/community/posts`, `GET /api/v1/community/posts/{id}`, `POST /api/v1/community/posts/{id}/likes`, `GET|POST /api/v1/community/posts/{id}/comments`, `DELETE /api/v1/community/posts/{id}?requesterId=` |
| discovery | `GET /api/v1/shops/{sellerId}`, `POST|GET /api/v1/users/{userId}/following`, `DELETE /api/v1/users/{userId}/following/{sellerId}`, `GET /api/v1/users/{userId}/feed`, `POST|DELETE|GET /api/v1/users/{userId}/wishlist` |
| review | `POST /api/v1/reviews`, `GET /api/v1/sellers/{sellerId}/reviews`, `GET /api/v1/sellers/{sellerId}/rating` |
| admin | `GET /api/admin/overview`, `GET /api/admin/catalog/sets`, `POST /api/admin/catalog/sets` (ADMIN) |

- 오류 응답: `{ "code": "LISTING_NOT_FOUND", "message": "..." }` + 상태코드(NFR-1).
- 인증: 세션 토큰 `Authorization: Bearer <token>`. `/me`로 권한 확인.

## 5. 프론트엔드 — FSD 설계

### 5.1 레이어
```
app      → Next.js App Router. (auth)/(main) 라우트 그룹. 라우트는 view를 조합만.
views    → 라우트별 화면 (home, search, sell, prices, collection, community, listing-detail, order-detail, seller-shop, sign-in/up, verify-email ...)
widgets  → site-header, listing-grid, post-card, price-chart, auth-layout
features → 사용자 행동 (sign-in/up, verify-email, create-listing, purchase, create-post, comment-post, like-post, follow-seller, wishlist-toggle, listing-filter)
entities → 비즈니스 엔티티 + api 클라이언트 (lego-set, listing, order, user, pricing, collection, community, discovery)
shared   → ui kit(button,input,card,badge,...), api(http-client, apiRequest), config(env), lib(format, class-names)
```

### 5.2 규칙 (강제 도구)
- `eslint-plugin-boundaries`(레이어 방향), `steiger`+`@feature-sliced/steiger-plugin`(FSD 구조), tsconfig strict 풀세트(`noUncheckedIndexedAccess`, `exactOptionalPropertyTypes`).
- 슬라이스 외부 접근은 반드시 `index.ts` 공개 API로만. cross-import 금지.

### 5.3 API 통신
- `shared/api/http-client.ts`의 `apiRequest<T>(path, options)`가 단일 진입점.
- 베이스 URL은 `shared/config/env.ts`의 `env.apiBaseUrl`(`NEXT_PUBLIC_API_BASE_URL`, 미설정 시 `http://localhost:8080`).
- 오류는 `ApiError{status, code}`로 표준화. 각 entities의 `api/*-api.ts`가 백엔드 계약과 1:1 매핑.
- 인증 토큰은 HttpOnly 쿠키로만 보관한다. `entities/user/model/session-store.ts`에는 accountId·role·다음 회전 시각만 저장하고, 모든 API 요청은 `credentials: include`로 쿠키를 전송한다.

## 6. 보안 / 운영 고려

- 현재 인증은 **회전 가능한 불투명 세션 토큰 + BCrypt 비밀번호 해시**(`BCryptPasswordHasherAdapter`, @Primary)다. Redis 세션은 24시간 유휴/7일 절대 만료를 적용하고, 브라우저는 12시간 주기로 `POST /sessions/refresh`를 호출한다. 회전해도 최초 발급 시각을 보존해 절대 만료를 우회할 수 없다. 레거시 SHA-256 비밀번호 해시는 로그인 성공 시 BCrypt로 자동 승격하고, 레거시 Redis 세션도 남은 TTL 동안 호환한다(요구사항 1.12~1.15).
- 결제/정산은 Stub 어댑터(`StubPaymentGatewayAdapter`, `StubSettlementAdapter`). 실 PG 연동 시 포트 구현체만 추가.
- `/api/admin/**`는 ADMIN 강제. 그 외 쓰기 API의 호출자 검증(작성자/소유자)은 도메인/서비스에서 수행.
- 이미지 업로드는 MinIO(S3 호환) 사용 예정(`.kiro/steering/minio.md`). 현재 매물/게시글은 URL 문자열로 사진을 받는다.

## 7. 설계 결정 (Trade-offs)

- **MongoDB 우선, 관계형은 후속**: 정산 도메인이 강한 정합성을 요구하면 헥사고날 포트/어댑터로 해당 컨텍스트에만 PostgreSQL 어댑터 추가 가능.
- **Stub 게이트웨이**: 거래 라이프사이클의 도메인 로직을 PG 의존 없이 완성·검증하기 위함.
- **컨텍스트 간 UseCase-only 의존**: 결합도를 낮춰 컨텍스트 독립 진화를 보장.
