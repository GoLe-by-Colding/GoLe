# 거래 후기/평점 (Review) — 설계

## 아키텍처

기존 헥사고날 컨텍스트 컨벤션을 그대로 따른다. 새 바운디드 컨텍스트 `com.gole.api.review`.

```
com.gole.api.review/
├── domain/
│   ├── model/
│   │   ├── Review.java               # 애그리거트 (순수 도메인)
│   │   └── SellerRatingSummary.java  # 평균 평점/후기수 값 객체
│   └── exception/
│       ├── InvalidRatingException.java
│       └── DuplicateReviewException.java
├── application/
│   ├── port/in/
│   │   ├── WriteReviewUseCase.java        # 후기 작성
│   │   └── GetSellerReviewsUseCase.java   # 후기 목록 + 평점 요약
│   ├── port/out/
│   │   ├── ReviewRepositoryPort.java
│   │   ├── ReviewIdGeneratorPort.java
│   │   └── OrderQueryPort.java            # CROSS-CONTEXT: 주문 조회
│   └── service/
│       └── ReviewService.java
└── adapter/
    ├── in/web/
    │   ├── ReviewController.java
    │   └── ReviewDtos.java
    └── out/
        ├── persistence/
        │   ├── ReviewDocument.java
        │   ├── ReviewMongoRepository.java
        │   └── ReviewPersistenceAdapter.java
        ├── id/
        │   └── ReviewIdGenerator.java
        └── order/
            └── OrderQueryAdapter.java     # order.GetOrderUseCase 위임
```

## 도메인 모델

### Review (애그리거트)
| 필드 | 타입 | 설명 |
|---|---|---|
| id | String | UUID |
| orderId | String | 대상 주문 (유일성 키) |
| reviewerId | String | 구매자 |
| revieweeId | String | 판매자 |
| rating | int | 1~5 (생성 시 검증, 벗어나면 `InvalidRatingException`) |
| content | String | 1~1000자, 공백 불가 |
| createdAt | Instant | 작성 시각 |

- 정적 팩토리 `Review.write(id, orderId, reviewerId, revieweeId, rating, content, now)`.
- 불변(final 필드). rating 범위 검증은 도메인 내부에서 수행.

### SellerRatingSummary (값 객체)
- `sellerId`, `double average`(소수 1자리 반올림), `long count`.
- 정적 팩토리 `SellerRatingSummary.of(sellerId, reviews)` — 빈 목록이면 average=0.0, count=0.

## 포트

### in-port
- `WriteReviewUseCase.write(WriteReviewCommand)` → reviewId(String)
  - `WriteReviewCommand(orderId, reviewerId, rating, content)`
- `GetSellerReviewsUseCase`
  - `List<Review> bySeller(String sellerId)`
  - `SellerRatingSummary ratingOf(String sellerId)`

### out-port
- `ReviewRepositoryPort`
  - `Review save(Review)`
  - `boolean existsByOrderId(String orderId)`
  - `List<Review> findByRevieweeIdRecentFirst(String revieweeId)`
- `ReviewIdGeneratorPort.newId()`
- `OrderQueryPort` (CROSS-CONTEXT)
  - `OrderSnapshot getById(String orderId)` — 필요한 필드만 노출하는 경량 스냅샷
  - `record OrderSnapshot(String orderId, String buyerId, String sellerId, boolean completed)`

## 서비스 로직 (ReviewService)

`write(command)`:
1. `OrderQueryPort.getById(orderId)` → 주문 스냅샷 (없으면 NotFound).
2. 주문이 completed 아니면 `ConflictException`(REVIEW_ORDER_NOT_COMPLETED).
3. `reviewerId != buyerId` → `ForbiddenException`(NOT_ORDER_BUYER).
4. `existsByOrderId(orderId)` → `DuplicateReviewException`.
5. `Review.write(...)` 생성(revieweeId = snapshot.sellerId), 저장, id 반환.

`bySeller(sellerId)` → repository 최신순.
`ratingOf(sellerId)` → `SellerRatingSummary.of(sellerId, repository.findBy...)`.

## 크로스 컨텍스트 경계

`OrderQueryAdapter`(review의 out 어댑터)가 order 컨텍스트의 **in-port** `GetOrderUseCase`에만 의존한다.
order의 도메인 `Order`를 받아 review의 `OrderSnapshot`으로 변환한다. order의 영속성/내부 구현에 접근하지 않는다.
(discovery → listing 의 `ListingQueryAdapter` 패턴과 동일)

## REST API

| 메서드 | 경로 | 설명 | 상태 |
|---|---|---|---|
| POST | `/api/v1/reviews` | 후기 작성 | 201 |
| GET | `/api/v1/sellers/{sellerId}/reviews` | 셀러 후기 목록 | 200 |
| GET | `/api/v1/sellers/{sellerId}/rating` | 셀러 평점 요약 | 200 |

### 요청/응답 DTO
- `WriteReviewRequest(@NotBlank orderId, @NotBlank reviewerId, @Min(1)@Max(5) int rating, @NotBlank @Size(max=1000) content)`
- `ReviewResponse(id, orderId, reviewerId, revieweeId, rating, content, createdAt)`
- `SellerRatingResponse(sellerId, average, count)`

### 예외 매핑 (기존 GlobalExceptionHandler 재사용)
- `NotFoundException` → 404
- `ConflictException`, `DuplicateReviewException`(DomainException) → 409
- `ForbiddenException` → 403
- Bean Validation → 400

## 영속성

- collection `reviews`. `@Id id`, `@Indexed revieweeId`, `@Indexed(unique=true) orderId`(주문당 1회 보장),
  `reviewerId`, `rating`, `content`, `createdAt`.
- `ReviewMongoRepository.findByRevieweeIdOrderByCreatedAtDesc(String)`, `existsByOrderId(String)`.

## 프론트 (FSD)

- `entities/review` — 타입 `Review`, `SellerRating`, API 훅 `useSellerReviews`, `useSellerRating`.
- `features/write-review` — 후기 작성 폼 + `useWriteReview` 뮤테이션.
- `views/seller-shop` — 평균 평점 배지 + 후기 목록 섹션 추가.
- 각 슬라이스는 `index.ts` 공개 API 보유. cross-feature import 금지.

## 테스트

- `ReviewServiceTest` — in-memory 포트로 작성/자격검증/중복/평점요약 검증 (Clock.fixed, SeqIds).
- 도메인 rating 범위 검증.
