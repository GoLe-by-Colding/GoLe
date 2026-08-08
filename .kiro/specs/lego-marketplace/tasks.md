# GoLe LEGO Marketplace — Implementation Tasks

> `requirements.md` / `design.md`를 구현 단위로 분해한다. 체크된 항목(`[x]`)은 현재 코드베이스에 구현·커밋·배포 완료된 것이다.
> 구현 순서는 헥사고날(domain → port-in → port-out → service → persistence → web) 및 FSD(shared → entities → features → widgets → views → app) 규약을 따른다.

## 0. 기반 (Foundations)

- [x] 0.1 모노레포 구성(pnpm workspace, `apps/web`, `apps/api`, docker-compose: mongo/redis)
- [x] 0.2 백엔드 빌드 toolchain Java 21 LTS 고정(`build.gradle.kts`)
- [x] 0.3 공통 예외 계층 + `GlobalExceptionHandler` + `ErrorResponse{code,message}` (NFR-1)
- [x] 0.4 `UseCaseLoggingAspect`(AOP), `MongoTransactionConfig`, `TimeConfig(Clock)`, `WebCorsConfig`
- [x] 0.5 프론트 FSD 골격 + 강제 도구(eslint-boundaries, steiger, tsconfig strict)

## 1. account (요구사항 1)

- [x] 1.1 도메인: `Account` 애그리거트(상태/권한/잠금 불변식), `Email`/`PasswordHash`/`VerificationCode`, `AccountStatus`/`Role`
- [x] 1.2 port-in: `RegisterAccountUseCase`, `VerifyEmailUseCase`, `SignInUseCase`, `GetCurrentSessionUseCase`
- [x] 1.3 port-out: `AccountRepositoryPort`, `PasswordHasherPort`, `SessionTokenPort`, `VerificationCodeGeneratorPort`, `VerificationCodeSenderPort`, `IdentifierGeneratorPort`
- [x] 1.4 service: `AccountService`(가입/인증/로그인 잠금/세션 해석)
- [x] 1.5 adapter-out: Mongo persistence, SHA-256 해시, opaque 세션토큰, numeric 코드 생성/로깅 전송, UUID
- [x] 1.6 adapter-in: `AccountController` + Request/Response DTO
- [x] 1.7 테스트: `AccountServiceTest`
- [x] 1.8 비밀번호 해시 BCrypt 전환 + 레거시 SHA-256 호환 검증 + 로그인 시 자동 승격 (요구사항 1.12) — `BCryptPasswordHasherAdapter`(@Primary), `Account.upgradePasswordHash`, `BCryptPasswordHasherAdapterTest`
- [ ] 1.9 (후속) 세션 만료/회전 정책 강화

## 2. catalog (요구사항 2)

- [x] 2.1 도메인: `LegoSet`, `RetirementStatus`
- [x] 2.2 port-in: `FindLegoSetUseCase`, `SearchLegoSetsUseCase`, `ListFeaturedLegoSetsUseCase`, `CreateLegoSetUseCase`, `ListLegoSetsUseCase`
- [x] 2.3 port-out: `LoadLegoSetPort`
- [x] 2.4 service: `CatalogService`
- [x] 2.5 adapter: Mongo persistence + `CatalogController`
- [x] 2.6 테스트: `CatalogServiceTest`

## 3. listing (요구사항 3)

- [x] 3.1 도메인: `Listing` 상태머신, `Money`, `ConditionDisclosure`, `ItemCondition`, `Completeness`, `ListingStatus`
- [x] 3.2 port-in: Create/Get/Search/Browse/MarkSold/Delete/Reserve/Release
- [x] 3.3 port-out: `ListingRepositoryPort`, `ListingIdGeneratorPort`
- [x] 3.4 query: `ListingSearchQuery`, `ListingSortOrder`
- [x] 3.5 service: `ListingService`(필터/정렬/상태전이/사진·가격 불변식)
- [x] 3.6 adapter: Mongo persistence + `ListingController`
- [x] 3.7 테스트: `ListingServiceTest`

## 4. order (요구사항 4)

- [x] 4.1 도메인: `Order` 상태머신 + 이력 + 낙관적 락 version, `OrderStatus`, `OrderStatusChange`, `Settlement`
- [x] 4.2 port-in: Place/Pay/Complete/Refund/Get
- [x] 4.3 port-out: `ListingReservationPort`, `PaymentGatewayPort`, `SettlementPort`, `ExecutedPriceRecorderPort`, `OrderRepositoryPort`, `OrderIdGeneratorPort`
- [x] 4.4 service: `OrderService`(트랜잭션 경계, 매물 예약/해제/판매, 정산·시세기록 트리거)
- [x] 4.5 adapter: Mongo persistence(@Version) + Stub 결제/정산 + listing/pricing 연동 어댑터 + `OrderController`
- [x] 4.6 테스트: `OrderServiceTest`, `OrderConcurrencyIntegrationTest`(Testcontainers)
- [ ] 4.7 (후속) 실 PG 어댑터(`PaymentGatewayPort` 구현체) 연동

## 5. pricing (요구사항 5)

- [x] 5.1 도메인: `PriceTransaction`, `PriceStatistics`(min/max/avg/median/count)
- [x] 5.2 port-in: `RecordExecutedPriceUseCase`, `GetPriceInsightsUseCase`
- [x] 5.3 port-out: `PriceTransactionRepositoryPort`
- [x] 5.4 service + adapter(Mongo) + `PricingController`(statistics/chart/history)
- [x] 5.5 테스트: `PricingServiceTest`

## 6. collection (요구사항 6)

- [x] 6.1 도메인: `CollectionItem`, `OwnershipStatus`
- [x] 6.2 port-in: `ManageCollectionUseCase`, `EstimateCollectionValueUseCase`
- [x] 6.3 port-out: `CollectionRepositoryPort`, `LatestPriceProviderPort`(→pricing), `CollectionIdGeneratorPort`
- [x] 6.4 service + adapter(Mongo, pricing 연동) + `CollectionController`
- [x] 6.5 테스트: `CollectionServiceTest`

## 7. community (요구사항 7)

- [x] 7.1 도메인: `Post`(type/status/likes/이미지 필수), `Comment`, `PostType`, `PostStatus`
- [x] 7.2 port-in: Publish/Comment/Like/GetFeed/Delete
- [x] 7.3 port-out: `PostRepositoryPort`, `CommentRepositoryPort`, `CommunityIdGeneratorPort`
- [x] 7.4 service + adapter(Mongo) + `CommunityController`
- [x] 7.5 테스트: `CommunityServiceTest`

## 8. discovery (요구사항 8)

- [x] 8.1 도메인: `Follow`, `WishlistEntry`, `WishlistTargetType`
- [x] 8.2 port-in: FollowSeller/GetSellerShop/GetPersonalizedFeed/ManageWishlist
- [x] 8.3 port-out: `FollowRepositoryPort`, `WishlistRepositoryPort`, `ListingQueryPort`(→listing)
- [x] 8.4 service + adapter(Mongo, listing 연동) + `DiscoveryController`
- [x] 8.5 테스트: `DiscoveryServiceTest`

## 9. review (요구사항 9)

- [x] 9.1 도메인: `Review`, 셀러 평점 집계
- [x] 9.2 port-in: `WriteReviewUseCase`, `GetSellerReviewsUseCase`
- [x] 9.3 service + adapter(Mongo) + `ReviewController`

## 10. admin (요구사항 10)

- [x] 10.1 `AdminAuthInterceptor`로 `/api/admin/**` ADMIN 강제
- [x] 10.2 `AdminController`: overview(컬렉션 집계), 카탈로그 세트 목록/생성(catalog UseCase 위임)

## 11. 프론트엔드 (FSD)

- [x] 11.1 shared: ui kit(button/input/card/badge/select/textarea/field/container/typography), `apiRequest`, `env`, lib(format/class-names)
- [x] 11.2 entities + api: lego-set, listing, order, user(session-store), pricing, collection, community, discovery
- [x] 11.3 features: sign-in/up, verify-email, create-listing, purchase, create-post, comment-post, like-post, follow-seller, wishlist-toggle, listing-filter
- [x] 11.4 widgets: site-header, listing-grid, post-card, price-chart, auth-layout
- [x] 11.5 views + app routes: home, search, sell, prices, collection, community(+상세/작성), listing-detail, order-detail, seller-shop, sign-in/up, verify
- [x] 11.6 Playwright E2E 설정

## 12. 인프라 / 배포

- [x] 12.1 docker-compose(mongo rs0, redis)
- [x] 12.2 `ubuntu-gole` 컨테이너 PM2(`gole-backend`, `gole-frontend`) + `scripts/deploy.sh`
- [x] 12.3 nginx 리버스 프록시 + `gole.kscold.com` HTTPS(Let's Encrypt)
- [x] 12.4 SDD 스펙 문서 정식화(`.kiro/specs/lego-marketplace/{requirements,design,tasks}.md`)

## 13. 후속 백로그 (Not started)

- [x] 13.1 비밀번호 해시 강화(BCrypt) + 레거시 호환 + 로그인 시 자동 승격 (요구사항 1.12)
- [ ] 13.1a (후속) 세션 만료/회전 정책 강화
- [ ] 13.2 실 결제 PG 연동(`PaymentGatewayPort` 실구현) (4.7)
- [x] 13.3 이미지 업로드 MinIO(S3) 연동 — 완료. `media` 컨텍스트(`MediaService`/`S3ObjectStorageAdapter`),
      배치 업로드 + 온더플라이 썸네일까지 구현. 프론트 `create-listing-form`/`create-post-form` 연동.
      스펙: `.kiro/specs/image-upload/` (2026-08-03 실측 감사로 소급 체크)
- [x] 13.4 시세 인기 세트 랭킹(체결 거래량) + Redis 캐싱 — `GetTrendingSetsUseCase`/`TrendingService`/`RedisTrendingCacheAdapter`, 프론트 `widgets/trending-sets`(홈). 스펙: `.kiro/specs/trending-sets/`
- [~] 13.5 알림 — **기반만 완료, 지정 트리거 미구현.** `notification` 컨텍스트와
      헤더 벨·`/notifications` 화면은 동작하나(스펙 `.kiro/specs/notifications/`),
      실제 트리거는 주문 생성(`ORDER_PLACED`) **1종뿐**이다.
      본 항목이 요구한 **팔로우 셀러 신규 매물**·**위시리스트 가격 변동** 트리거는 둘 다 없다.
- [x] 13.6 거래 후기/평점 — 스펙 `.kiro/specs/review/` 전 항목 완료·배포 검증
      (프로덕션 `seller-aurora` 평점 4.7/3건). 감사 시점에 백로그에 누락되어 있어 추가.
