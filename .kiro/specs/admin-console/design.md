# 운영자 콘솔 (Admin Console) — 설계

## 1. 설계 원칙

1. **하나의 사이트, 하나의 로그인.** 관리자 전용 도메인/앱/인증을 만들지 않는다. `role=ADMIN` 세션이 곧 운영 권한이다. (R1)
2. **조치는 도메인을 통과한다.** 관리자 컨텍스트는 상태를 직접 바꾸지 않고 각 컨텍스트의 인바운드 포트에 위임한다. (R5.3, R9.1)
3. **조회는 읽기 모델로 분리한다.** 운영 리포팅은 도메인 애그리거트가 아니라 read model이다. 아웃바운드 포트 뒤에 격리한다. (R9.2)
4. **모든 조치는 감사된다.** 성공한 조치는 예외 없이 append-only 로그에 남는다. (R8)

---

## 2. 컨텍스트 맵 (변경 후)

```
                        ┌──────────────────────────────┐
                        │        admin (운영)          │
                        │  AdminAuditService           │
                        │  AdminReadModelPort ──┐      │
                        └───────┬───────────────┼──────┘
   인바운드 포트에만 의존 ↓      │               │ (읽기 전용)
   ┌──────────┬──────────┬──────┴────┬──────────┴──┐   ┌─────────────────┐
   │ account  │ listing  │ community │  report     │   │ MongoAdminRead  │
   │ Manage   │ Moderate │ Moderate  │  Manage     │   │ ModelAdapter    │
   │ Accounts │ Listing  │ Post      │  Reports    │   └─────────────────┘
   │ UseCase  │ UseCase  │ UseCase   │  UseCase    │
   └──────────┴──────────┴───────────┴─────────────┘
   ┌──────────┐
   │ catalog  │  CreateLegoSetUseCase / UpdateLegoSetUseCase / ListLegoSetsUseCase
   └──────────┘
```

관리자 컨텍스트가 아는 것은 **UseCase 인터페이스뿐**이다. 어떤 컬렉션에 무엇이 저장되는지는 모른다.
(예외: `AdminReadModelPort`는 운영 대시보드 집계 전용 읽기 모델이며, 구현 어댑터만 MongoDB를 안다.)

---

## 3. 백엔드 설계

### 3.1 account — 계정 정지 / 권한 (R6)

#### 도메인

```java
enum AccountStatus { UNVERIFIED, VERIFIED, SUSPENDED }   // SUSPENDED 추가

final class Account {
    private Role role;                  // final 제거 (권한 변경 지원)
    private String suspendedReason;     // nullable

    void suspend(String reason);        // → SUSPENDED, 사유 보관
    void reinstate();                   // → VERIFIED, 사유 제거 + 실패 카운터/잠금 초기화
    void changeRole(Role newRole);
    boolean isSuspended();
    void ensureNotSuspended();          // 로그인 경로 가드 → AccountSuspendedException
}
```

- `AccountSuspendedException extends ForbiddenException` — 코드 `ACCOUNT_SUSPENDED` (403). (R6.4)
- `reinstate()`가 `recordSuccessfulSignIn()`과 동일하게 카운터를 리셋한다 → 정지 해제 즉시 로그인 가능. (R6.6)

#### 세션 폐기 (R6.3)

`SessionStorePort`에 계정 단위 폐기를 추가한다.

```java
void revokeAllForAccount(String accountId);
```

`RedisSessionStoreAdapter` 구현:

| 키 | 타입 | 값 | TTL |
|---|---|---|---|
| `gole:session:<token>` | string | `<accountId>|<ROLE>` | 7d |
| `gole:session:acct:<accountId>` | set | 해당 계정의 토큰들 | 7d (store 때마다 갱신) |

- `store` → 토큰 키 set + 인덱스 SADD + 인덱스 EXPIRE
- `revoke` → 토큰 키 DEL (인덱스의 죽은 토큰은 다음 `revokeAllForAccount`에서 정리되므로 무해)
- `revokeAllForAccount` → 인덱스 SMEMBERS → 토큰 키 일괄 DEL → 인덱스 DEL

> 인덱스가 유실돼도 R6.5(세션 해석 시 상태 검사)가 최종 방어선이라 정지는 항상 실효된다.

#### 세션 해석 가드 (R6.5)

`AccountService.resolve(token)`은 계정을 조회한 뒤 `account.isSuspended()`이면 `Optional.empty()`를 반환한다.
→ 이미 발급된 토큰이 남아 있어도 401이 된다.

#### 인바운드 포트 (신규)

```java
interface ManageAccountsUseCase {
    List<AccountSummary> list(String query, int limit);
    AccountSummary suspend(String accountId, String actorAccountId, String reason);
    AccountSummary reinstate(String accountId, String actorAccountId);
    AccountSummary changeRole(String accountId, String actorAccountId, Role newRole);

    record AccountSummary(String id, String email, Role role, AccountStatus status,
                          Instant lockedUntil, String suspendedReason) {}
}
```

- 구현: `AccountAdminService`(account/application/service). `AccountService`와 분리해 로그인 경로를 오염시키지 않는다.
- 가드: 자기 자신 대상 → `ADMIN_SELF_TARGET`(400), 마지막 ADMIN → `LAST_ADMIN`(400). (R6.8, R6.9)
- 아웃바운드 포트 확장: `AccountRepositoryPort.findRecent(String emailQuery, int limit)`, `countByRole(Role)`.

### 3.2 community — 운영자 게시글 내림 (R5)

```java
interface ModeratePostUseCase {
    void removeByModerator(String postId, String reason);
}
```

- 구현: `CommunityService`에 추가. 기존 `DeletePostUseCase.delete(postId, requesterId)`(작성자 검증)와 **별도 포트**로 두어 권한 모델을 명시적으로 구분한다.
- 도메인 호출은 동일하게 `Post.delete()`. 사유는 감사 로그에 남긴다(게시글 스키마 변경 없음).

### 3.3 listing — 운영자 강제 내림 (R4)

```java
interface ModerateListingUseCase {
    void takedown(String listingId, String reason);
}
```

- 기존 `DeleteListingUseCase`는 진행 중 주문(`RESERVED`)이면 `LISTING_ORDER_IN_PROGRESS`로 거부한다. 모더레이션은 이 규칙보다 우선하므로(R4.3) 별도 포트로 분리하고, 도메인에 `Listing.takedown()`(상태 무관 → `DELETED`, 멱등)을 추가한다.
- 구현: `ListingService`에 추가.

### 3.4 catalog — 세트 수정 / 추천 토글 (R7)

```java
interface UpdateLegoSetUseCase {
    void update(UpdateLegoSetCommand command);        // 전체 필드 갱신
    void setFeatured(String setNumber, boolean featured);
}
```

- `CatalogAdminPort.save(set, featured)`가 upsert이므로 `update`는 존재 확인(`LoadLegoSetPort`) 후 save로 구현한다. 없으면 404 `LEGO_SET_NOT_FOUND`.

### 3.5 admin — 감사 로그 + 읽기 모델 (신규 구조)

```
com.gole.api.admin/
├── domain/model/
│   ├── AdminAction.java          # 감사 레코드 애그리거트
│   ├── AdminActionType.java      # 조치 유형 enum
│   └── AdminTargetType.java      # LISTING / POST / ACCOUNT / REPORT / CATALOG_SET
├── application/
│   ├── port/in/
│   │   ├── RecordAdminActionUseCase.java
│   │   └── ListAdminActionsUseCase.java
│   ├── port/out/
│   │   ├── AdminAuditPort.java       # append / findRecent
│   │   └── AdminReadModelPort.java   # 운영 조회(집계·목록)
│   └── service/AdminAuditService.java
├── adapter/
│   ├── in/web/
│   │   ├── AdminAuthInterceptor.java       # (기존) Bearer → ADMIN 강제, actor 속성 주입
│   │   ├── AdminActor.java                 # 요청 속성에서 조치자(id·email) 추출 헬퍼
│   │   ├── AdminDashboardController.java   # overview, audit
│   │   ├── AdminModerationController.java  # listings, posts, reports
│   │   ├── AdminAccountController.java     # accounts
│   │   ├── AdminCatalogController.java     # catalog/sets
│   │   └── AdminDtos.java
│   └── out/
│       ├── persistence/AdminActionDocument / MongoRepository / AuditPersistenceAdapter
│       └── readmodel/MongoAdminReadModelAdapter.java
└── config/AdminWebConfig.java
```

#### AdminAction (감사 레코드)

| 필드 | 설명 |
|---|---|
| `id` | UUID |
| `actorId` / `actorEmail` | 조치자. 이메일은 스냅샷(계정 삭제 후에도 추적) |
| `type` | `AdminActionType` |
| `targetType` / `targetId` | 대상 |
| `reason` | 조치 사유(정지/내림은 필수, 그 외 선택) |
| `occurredAt` | 시각 |

`AdminActionType`: `LISTING_TAKEDOWN`, `POST_REMOVE`, `ACCOUNT_SUSPEND`, `ACCOUNT_REINSTATE`, `ACCOUNT_ROLE_CHANGE`, `REPORT_RESOLVE`, `REPORT_DISMISS`, `CATALOG_SET_CREATE`, `CATALOG_SET_UPDATE`, `CATALOG_SET_FEATURE`.

- MongoDB 컬렉션 `admin_actions`, 인덱스 `occurredAt desc`.
- 기록은 **조치 성공 후** 호출한다. 기록 실패는 로그만 남기고 삼킨다(R8.5) — `AdminAuditService.record`가 try/catch.

#### AdminReadModelPort (운영 조회)

```java
interface AdminReadModelPort {
    Map<String, Long> collectionCounts(List<String> collections);
    OrderStats orderStats();                              // 상태별 건수 + 완료 GMV
    long activeListingCount();
    List<OrderRow> recentOrders(String status, int limit);
    List<ListingRow> recentListings(int limit);
    List<PostRow> recentPosts(int limit);
}
```

- 구현 `MongoAdminReadModelAdapter`가 유일하게 `MongoTemplate`을 안다. 컨트롤러는 DB를 모른다. (R9.2)
- 반환 타입은 admin 컨텍스트 소유의 얇은 record(read model). 타 컨텍스트 도메인 객체를 재사용하지 않는다.

#### AdminActor (조치자 식별)

`AdminAuthInterceptor`가 세션 해석 결과에서 `accountId`/`email`을 요청 속성으로 주입하고, 컨트롤러는 `AdminActor.of(request)`로 꺼낸다. 별도 인증 로직 중복이 없다.

#### CORS 프리플라이트 예외 (필수)

`AdminAuthInterceptor.preHandle`은 `CorsUtils.isPreFlightRequest`로 OPTIONS를 먼저 통과시킨다.
브라우저는 프리플라이트에 `Authorization` 헤더를 싣지 않으므로, 이를 막으면 **본 요청이 시작조차 못 한다**.
curl(프리플라이트 없음)은 성공하는데 화면만 실패하는 형태라 원인 파악이 오래 걸린다 — 실제로 이 함정에 한 번 빠졌다.

또한 허용 오리진(`gole.web.allowed-origins`) 기본값에 `http://localhost:3010`을 포함한다.
로컬 웹 dev 서버가 3000 점유 시 3010으로 뜨는데, 오리진이 어긋나면 CORS 단계에서 403이 되어 같은 증상이 난다.

### 3.6 REST API

| 메서드 | 경로 | 설명 | 요구사항 |
|---|---|---|---|
| GET | `/api/admin/overview` | 집계·GMV·주문상태·활성매물 | 2.2 |
| GET | `/api/admin/audit?limit=` | 감사 로그 최근순 | 8.3 |
| GET | `/api/admin/reports?status=&limit=` | 신고 큐 | 3.1 |
| POST | `/api/admin/reports/{id}/resolve` | 조치완료 | 3.3 |
| POST | `/api/admin/reports/{id}/dismiss` | 기각 | 3.4 |
| GET | `/api/admin/listings?limit=` | 매물(전체 상태) | 4.1 |
| POST | `/api/admin/listings/{id}/takedown` | 강제 내림 `{reason}` | 4.2 |
| GET | `/api/admin/posts?limit=` | 게시글(전체 상태) | 5.1 |
| POST | `/api/admin/posts/{id}/remove` | 강제 삭제 `{reason}` | 5.2 |
| GET | `/api/admin/orders?status=&limit=` | 주문 모니터링 | 7.1 |
| GET | `/api/admin/accounts?q=&limit=` | 회원 목록 | 6.1 |
| POST | `/api/admin/accounts/{id}/suspend` | 정지 `{reason}` | 6.2 |
| POST | `/api/admin/accounts/{id}/reinstate` | 정지 해제 | 6.6 |
| POST | `/api/admin/accounts/{id}/role` | 권한 변경 `{role}` | 6.7 |
| GET | `/api/admin/catalog/sets` | 세트 목록 | 7.2 |
| POST | `/api/admin/catalog/sets` | 세트 등록(201) | 7.2 |
| POST | `/api/admin/catalog/sets/{setNumber}` | 세트 수정 | 7.3 |
| POST | `/api/admin/catalog/sets/{setNumber}/featured` | 추천 토글 `{featured}` | 7.4 |

> 하위호환: 기존 `/api/admin/accounts/{id}/lock`·`/unlock`은 제거하고 `suspend`/`reinstate`로 대체한다(정지가 실효되지 않던 편법이므로 유지 가치 없음). 프론트도 함께 전환한다.

### 3.7 오류 코드

| 코드 | 상태 | 의미 |
|---|---|---|
| `INVALID_SESSION` | 401 | 토큰 없음/만료/정지 계정 |
| `ADMIN_ONLY` | 403 | USER가 관리자 API 호출 |
| `ACCOUNT_SUSPENDED` | 403 | 정지 계정 로그인 시도 |
| `ADMIN_SELF_TARGET` | 400 | 자기 자신 대상 조치 |
| `LAST_ADMIN` | 400 | 마지막 관리자 정지·강등 |
| `MODERATION_REASON_REQUIRED` | 400 | 사유 누락 |

---

## 4. 프론트엔드 설계 (FSD)

### 4.1 라우팅

```
src/app/(main)/admin/
├── layout.tsx          # AdminShell(권한 게이트 + 좌측 내비) + noindex
├── page.tsx            # 대시보드
├── reports/page.tsx
├── listings/page.tsx
├── orders/page.tsx
├── community/page.tsx
├── accounts/page.tsx
├── catalog/page.tsx
└── audit/page.tsx
```

`(main)` 그룹 안에 두어 사이트 헤더/푸터를 공유한다 — "같은 사이트"라는 원칙의 UI적 구현. (R1)

### 4.2 레이어 배치

| 레이어 | 슬라이스 | 책임 |
|---|---|---|
| views | `admin` | 섹션별 화면 컴포넌트(대시보드/신고/매물/주문/커뮤니티/회원/카탈로그/감사) |
| widgets | `admin-shell` | 권한 게이트 + 좌측 내비 + 미처리 신고 뱃지 |
| widgets | `admin-bar` | 온사이트 어드민 모드 — 일반 화면 하단 고정 바 |
| features | `admin-moderation` | 조치 액션(내림/삭제/정지) + 사유 입력 다이얼로그 |
| entities | `admin` | 관리자 API 클라이언트 + 타입 |
| shared | `ui` | 기존 디자인 시스템 재사용(Card/Badge/Button/Table 스타일) |

- `views → widgets → features → entities → shared` 단방향 유지. `AdminShell`은 layout(app)에서 사용하므로 widgets에 둔다.
- 조치 확인 다이얼로그는 `features/admin-moderation`의 `ReasonPrompt`로 공통화한다(사유 필수 입력 — R4.2/R5.2/R6.2).

### 4.3 온사이트 어드민 모드 (R1.6)

`widgets/admin-bar`를 `(main)/layout.tsx`에 배치한다.

- `session.role === "ADMIN"`이 아니면 **아무것도 렌더링하지 않는다**(R1.7).
- 하단 고정 바에 표시: `ADMIN` 뱃지 · 미처리 신고 수 · 콘솔 바로가기.
- 현재 경로를 파싱해 컨텍스트 조치를 노출한다.
  - `/listings/{id}` → "이 매물 내리기"
  - `/community/{id}` → "이 게시글 삭제"
- 조치 후 `router.refresh()`로 현재 화면을 갱신한다.
- 바는 접기(collapse) 가능하며 상태를 `localStorage`에 보존한다.

### 4.4 세션 정지 반영

정지된 사용자의 토큰은 서버에서 401이 된다. `shared/api`가 401을 받으면 세션 스토어를 비우고 로그인으로 유도하는 기존 흐름을 그대로 사용한다(추가 작업 없음).

---

## 4.5 로컬 구동 (운영자 콘솔을 실제로 쓰기 위한 최소 조건)

```bash
pnpm infra:up     # mongo(rs0) + redis + minio
pnpm dev:api      # :8090 — 관리자 계정과 시드가 여기서 만들어진다
pnpm dev:web      # :3010
```

- **관리자 계정**: `dev:api`가 `GOLE_ADMIN_EMAIL`/`GOLE_ADMIN_PASSWORD` 기본값(`admin@gole.local` / `gole-admin-1234`)을
  주입해 `AdminAccountSeeder`가 ADMIN 계정을 1회 생성한다. 환경변수가 이미 있으면 그 값을 쓰므로 배포 경로에는 영향이 없다.
  이 기본값이 없으면 시더가 no-op이라 **로컬에서 관리자 로그인 자체가 불가능**했다.
- **신고 시드**: `ReportSeeder`(`gole.report.seed-on-empty`)가 시드된 매물·게시글을 대상으로 PENDING 신고 4건을 접수한다.
  신고 큐는 콘솔의 핵심 동선인데 비어 있으면 조치→감사 흐름을 로컬에서 확인할 수 없다.

## 5. 데이터 마이그레이션

- `accounts.status`에 `SUSPENDED`가 추가되지만 기존 문서는 영향 없음(enum 확장).
- 기존 편법 잠금(`lockedUntil = 9999-12-31`)이 남아 있을 수 있다. 운영 스크립트로 해당 문서를 `status=SUSPENDED, suspendedReason="마이그레이션: 레거시 잠금"`으로 전환하고 `lockedUntil`을 제거한다. (tasks.md 참조)
- `admin_actions` 컬렉션은 신규 — 별도 마이그레이션 없음.

---

## 6. 테스트 전략

| 대상 | 유형 | 검증 |
|---|---|---|
| `Account.suspend/reinstate/changeRole` | 단위 | 상태 전이·사유 보관·카운터 초기화 |
| `AccountAdminService` | 단위(Fake 포트) | 자기대상·마지막 관리자 가드, 세션 폐기 호출 |
| `AccountService.signIn` | 단위 | 정지 계정 403 |
| `AccountService.resolve` | 단위 | 정지 계정 → empty |
| `ModerateListingUseCase` | 단위 | RESERVED 매물도 내려짐(멱등) |
| `ModeratePostUseCase` | 단위 | 작성자 아닌 운영자도 삭제 성공 |
| `AdminAuditService` | 단위 | append 호출, 포트 예외 삼킴(R8.5) |
| 관리자 API 가드 | E2E | 토큰없음 401 / USER 403 / ADMIN 200 |
| 정지 실효성 | E2E | 정지 후 기존 토큰 401 |
