# 관리자 · 결제 — 설계

## 인증 세션 (account 컨텍스트)

- 도메인: `Role`(USER/ADMIN) 추가. `Account`에 `role` 필드 + `provisioned(...)` 팩토리(인증완료+지정 role).
- out-port `SessionStorePort`: `store/resolve/revoke` + `SessionPrincipal(accountId, role)`.
  - 어댑터 `RedisSessionStoreAdapter`: `StringRedisTemplate`, 키 `gole:session:<token>`, 값 `accountId|ROLE`, TTL 7일.
- `AccountService.signIn`: 토큰 발급 후 `sessionStore.store(...)` + `SignInResult`에 role 포함.
- in-port `GetCurrentSessionUseCase.resolve(token)` → `CurrentSession(accountId, role)`.
- `AccountController`: `POST /sessions`(role 포함 응답), `GET /me`(Bearer 해석).
- 부트스트랩: `account/bootstrap/AdminAccountSeeder`(`@Value gole.admin.*`, 멱등 ADMIN 생성).

## 관리자 (admin 컨텍스트 — 신규)

```
com.gole.api.admin/
├── adapter/in/web/
│   ├── AdminAuthInterceptor   # Bearer→GetCurrentSessionUseCase→ADMIN 강제(401/403)
│   ├── AdminController        # /api/admin: overview, catalog/sets(GET·POST)
│   └── AdminDtos
└── config/AdminWebConfig      # /api/admin/** 에 인터셉터 등록
```

- 경계: admin은 account의 인바운드 포트(`GetCurrentSessionUseCase`)와 catalog 인바운드 포트에만 의존.
- overview 집계는 운영 리포팅 성격으로 `MongoTemplate`로 컬렉션 카운트(읽기 전용).

## 카탈로그 쓰기 (catalog 컨텍스트 확장)

- in-port `CreateLegoSetUseCase`, `ListLegoSetsUseCase`.
- out-port `CatalogAdminPort`(save/findAll) — 읽기 전용 `LoadLegoSetPort`와 분리, 같은 영속성 어댑터가 구현.

## REST API

| 메서드 | 경로 | 권한 | 설명 |
|---|---|---|---|
| GET | `/api/v1/accounts/me` | 로그인 | 현재 계정/권한 |
| GET | `/api/admin/overview` | ADMIN | 컬렉션 집계 |
| GET | `/api/admin/catalog/sets` | ADMIN | 세트 목록 |
| POST | `/api/admin/catalog/sets` | ADMIN | 세트 등록 |

## 포트원 (order 컨텍스트)

- `PortOnePaymentGatewayAdapter implements PaymentGatewayPort` (`@ConditionalOnProperty portone.enabled=true`).
  - `authorize(orderId, amount)`: `GET {api-base}/payments/{orderId}` (헤더 `Authorization: PortOne <secret>`) → `status==PAID && amount.total==amount`.
  - `refund(orderId, amount)`: `POST /payments/{orderId}/cancel`.
- `StubPaymentGatewayAdapter`는 `@ConditionalOnProperty(enabled=false, matchIfMissing=true)` — 기본.
- 핵심 설계: 포트원 `paymentId == 우리 orderId` 규약으로 기존 pay 플로우 시그니처를 바꾸지 않는다.

## 프론트 (FSD)

- `entities/user`: `Session.role` 추가(로그인 응답이 그대로 저장됨).
- `entities/admin`: 관리자 API 클라이언트(`Authorization: Bearer` 헤더).
- `views/admin` + `app/(main)/admin`: ADMIN 게이트 대시보드(현황/세트 관리), noindex.
- `widgets/site-header`: ADMIN에게 관리자 링크.
- 포트원 프론트: `shared/lib/portone.ts`(CDN SDK 동적 로드, env 게이트) → 주문 상세 결제 단계에서 `requestPayment(paymentId=orderId)` 후 `payOrder`로 서버 검증.

## 설정 (application.yml / env)

- `gole.admin.email/password` (GOLE_ADMIN_EMAIL/PASSWORD)
- `portone.enabled/api-base/api-secret` (PORTONE_ENABLED/API_BASE/API_SECRET)
- 프론트: `NEXT_PUBLIC_PORTONE_STORE_ID`, `NEXT_PUBLIC_PORTONE_CHANNEL_KEY`
