# 매물 수정·끌올·찜 매물 가격 인하 알림 — Spec

> 커머스 기본기 1순위. 매물 수정 유스케이스가 없어서 `notifications/spec.md`·`lego-marketplace/tasks.md 13.5`·
> `interest-tag-alimtalk/design.md`가 각각 "후속으로 남긴다"고 적고 넘어갔던 것을 닫는다.
> 가격을 바꾸려면 지우고 다시 올려야 했고, 그러면 찜·댓글·채팅 맥락이 끊겼다.

## 왜 필요한가

- 중고거래에서 가격 조정은 가장 흔한 판매자 행동이다. 지금은 삭제 → 재등록뿐이다.
- 가격 인하는 찜한 사람에게 가장 강한 재방문 신호다. 위시리스트는 매물(`LISTING`) 대상을 이미 받는데
  **매물을 찜하는 UI가 없고**, 가격이 바뀌는 사건 자체가 없어 알림을 보낼 수 없었다.
- 끌올은 오래된 매물의 노출을 회복시킨다. 매물이 적은 콜드스타트에서 재등록 남용을 막는 정식 경로다.

## Requirements (EARS)

### 수정

- E1. WHEN 판매자가 `PUT /api/v1/listings/{id}`로 수정하면, 시스템은 아래 필드를 **통째로 교체**한다.
  `title`(1~120) · `description`(≤5000) · `price`(≥0) · `condition` · `completeness` · `hasBox` ·
  `hasManual` · `hasMissingParts` · `missingPartsNote`(≤1000) · `defectsNote`(≤1000) ·
  `photoKeys`(1~10) · `interestTag`(nullable). 검증은 등록(`CreateListingRequest`)과 같다.
- E2. `category`·`catalogSetNumber`는 수정할 수 없다. 세트가 바뀌면 관심 세트 알림·입찰 매칭·
  시세 귀속이 모두 틀어진다. 요청 본문에 와도 무시한다.
- E3. 본인 매물이 아니면 403 `LISTING_ACCESS_DENIED`. `RESERVED`면 409 `LISTING_ORDER_IN_PROGRESS`,
  `SOLD`·`DELETED`면 409 `LISTING_NOT_EDITABLE`.
- E4. 저장은 `{_id, status: ACTIVE}` 조건의 **원자적 갱신**이다. 수정 도중 주문이 예약을 잡으면 수정이
  지고(E3 오류), 예약 상태를 덮어쓰지 않는다. (지금 `save()`는 문서 전체를 덮어써서 `reserveIfActive`와
  경합하면 `RESERVED`를 `ACTIVE`로 되돌릴 수 있다.)
- E5. 사진은 `ManageMediaAssetsUseCase.replaceReferences(..., LISTING, id, photoKeys, true)`로 교체한다.
  빠진 사진은 기존 수명주기대로 회수된다.
- E6. 가격이 **내려가면** `previousPrice`(직전 가격)와 `priceChangedAt`을 기록한다. 올라가면
  `previousPrice`를 비운다(인하 표시는 "지금 가격이 직전보다 싸다"일 때만 보인다). 같으면 둘 다 그대로.
- E7. 관심 테마(`interestTag`)를 바꿔도 알림톡을 다시 보내지 않는다. 팔로워·관심 세트 알림도 다시
  보내지 않는다. 수정은 새 매물이 아니다. (`interest-tag-alimtalk/design.md`가 미뤄둔 정책 결정)

### 찜 매물 가격 인하 알림

- E8. WHEN 수정으로 가격이 내려가면, 시스템은 그 매물을 찜한 사용자(`WishlistTargetType.LISTING`)에게
  `LISTING_PRICE_DROPPED` 알림을 보낸다. 판매자 본인은 제외한다.
  - 문구 `찜한 매물 가격이 내려갔어요: {title} {이전가}원 → {새 가격}원`, 링크 `/listings/{id}`.
  - 멱등 키 `listing-price-drop:{id}:{새 가격}` — 재시도는 한 번만 울리고, 더 내리면 다시 울린다.
- E9. 수신자 조회·발송 장애는 흡수한다(best-effort). 수정은 실패하지 않는다.
- E10. 매물 상세에 **매물 찜 버튼**을 둔다(지금은 "관심 세트" 버튼뿐이다). 판매자 본인에게는 보이지 않는다.

### 끌올

- B1. WHEN 판매자가 `POST /api/v1/listings/{id}/bump`를 부르면, 시스템은 `bumpedAt`과 `listedAt`을
  지금으로 바꾸고 `ListingResponse`를 돌려준다.
- B2. 쿨다운은 `listedAt`(등록 또는 마지막 끌올) 기준 `gole.listing.bump.cooldown`(기본 `PT24H`)이다.
  안이면 429 `LISTING_BUMP_COOLDOWN` + `Retry-After`(초). 등록 직후 끌올도 같은 규칙으로 막힌다.
- B3. `ACTIVE`만 끌올할 수 있다. `RESERVED`는 409 `LISTING_ORDER_IN_PROGRESS`, 나머지는 409
  `LISTING_NOT_BUMPABLE`. 본인 매물이 아니면 403 `LISTING_ACCESS_DENIED`. 원자적 갱신(E4와 같은 조건).
- B4. 끌올은 알림을 보내지 않는다.
- B5. "최신순"은 `listedAt` 내림차순이다. 검색·세트 상세·홈(`NEWEST`), 팔로우 피드, 셀러샵,
  `/listings/mine`이 모두 같은 키를 쓴다. 셀러샵은 지금 정렬이 아예 없으므로 같이 맞춘다.
- B6. `listedAt`이 없는 기존 문서는 기동 시 `listedAt = createdAt`으로 한 번 채운다(멱등).
  읽을 때도 없으면 `createdAt`으로 본다.

### 응답

- R1. `ListingResponse`에 추가: `listedAt`, `bumpedAt`(nullable), `bumpAvailableAt`(= `listedAt + 쿨다운`),
  `previousPrice`(nullable), `priceChangedAt`(nullable), `photoKeys`(수정 폼이 다시 제출할 저장 키 —
  `MediaKey.safeStoredKey()`. 커뮤니티 `PostResponse`와 같은 선례).
- R2. 디스커버리 `ListingSummaryResponse`에 `listedAt`, `previousPrice` 추가.

## Design

- domain `Listing`: `bumpedAt`·`listedAt`·`previousPrice`·`priceChangedAt` 필드. 행위 메서드
  `revise(ListingRevision, Instant now)`(E3 상태 검사 + E6 가격 이력)와 `bump(Instant now, Duration cooldown)`
  (B2·B3). `listedAt()`은 저장값이 없으면 `createdAt`.
- port-in `ReviseListingUseCase.revise(ReviseListingCommand)` → `RevisionResult(listing, priceDropped, oldPrice)`,
  `BumpListingUseCase.bump(listingId, sellerId)`. 소유자 검사는 기존 컨트롤러 `requireSeller` 패턴을 따른다.
- port-out `ListingRepositoryPort.updateIfActive(Listing)` → `boolean`. `false`면 다시 읽어 E3/B3 오류로 바꾼다.
  `ListingPriceDropNotifierPort.priceDropped(listingId, sellerId, title, oldPrice, newPrice)`.
- discovery: port-in `ListListingWishersUseCase.wishersOf(listingId)` ← 저장소만 의존하는 읽기 서비스
  (`SetWisherQueryService`와 같은 이유 — 순환 의존 방지). 인덱스 `ix_target`이 이미 있다.
- persistence: `ListingDocument`에 `listedAt`·`bumpedAt`·`previousPrice`·`priceChangedAt`.
  `@CompoundIndex {status:1, listedAt:-1}`. 기동 백필은 `ApplicationRunner`가 `listedAt` 없는 문서에
  `$set: {listedAt: "$createdAt"}` 파이프라인 갱신.
- properties `gole.listing.bump.cooldown`(`GOLE_LISTING_BUMP_COOLDOWN`).

### 프론트

- core `listing`: `Listing` 타입에 R1 필드, `updateListing(id, input)`(PUT), `bumpListing(id)`(POST).
  `discovery` `ListingSummary`에 R2 필드.
- 수정 화면 `/listings/[id]/edit`: 등록 폼을 공유한다(세트·카테고리는 읽기 전용 표시). 판매자 아니면 안내.
- 매물 상세: 판매자에게 "수정"·"끌올"(쿨다운 남은 시간 표시) 패널, 그 외에게 "찜" 버튼.
  `previousPrice > price`면 직전가 취소선 + "N원 내림" 배지.
- 프로필 "내 매물": `ACTIVE` 카드에 "수정" 링크와 "끌올" 버튼.
- 매물 카드(`entities/listing`): 가격 인하 배지(작게).

## 범위에서 뺀 것

- 가격 변경 이력 전체 보관 — 직전가 하나만 둔다. 시세는 체결가만 쓰므로 호가 이력이 필요 없다.
- 끌올 유료화·횟수 제한 — 쿨다운만 둔다.
- 관리자 대리 수정.

## Tasks

- [x] B1 domain 필드·`revise`·`bump` + 도메인 테스트
- [x] B2 port-in/out, `ListingService` 수정·끌올, discovery `ListListingWishersUseCase`
- [x] B3 persistence 원자 갱신·정렬 키·인덱스·백필
- [x] B4 web `PUT`·`POST /bump`, 응답 필드, 가격 인하 알림 어댑터
- [x] B5 단위 테스트(상태별 거부, 원자 갱신 실패 매핑, 쿨다운 경계, 인하 알림 수신자·멱등 키·장애 흡수)
- [x] B6 통합 테스트(레거시 문서 `listedAt` 백필·정렬, 예약 경합 시 수정이 짐)
- [x] F1 core 타입·API
- [x] F2 수정 화면, 매물 상세 판매자 패널·찜 버튼·인하 표시, 프로필 내 매물 버튼
- [ ] V1 로컬 실검증: 수정 → 찜한 계정 알림 도착, 끌올 → 최신순 맨 위, 쿨다운 429
