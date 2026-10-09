# 구매 입찰 — Spec

> 레고 차별화 3순위. 세트 시세의 "즉시판매가"는 지금 체결가 × 0.96 **추정치**다(`PriceValuation`).
> 구매자가 세트·상태·가격으로 입찰을 걸어두면, 판매자에게 "지금 팔면 받는 값"이 실제 대기 수요로 보인다.

## 왜 필요한가

- 매물이 없어도 수요가 보인다 → 판매자 등록 동기. 콜드스타트의 병목이 공급이다.
- 판매자는 최고 입찰가에 바로 팔 수 있다 → 체결이 빨라지고 실거래가 쌓인다.

## 설계 선택 — 자동 결제 체결이 아니라 "수락된 제안"으로 잇는다

KREAM식 입찰은 체결 즉시 결제가 일어나야 한다. GoLe는 운영 결제가 닫혀 있고(직거래 단계), 입찰 시점
카드 보관(빌링키)도 없다. 그래서 **판매자가 입찰을 수락하면 그 입찰자에게 "수락된 가격 제안"을 만든다**
(`price-offer` O21). 입찰자는 결제가 열려 있으면 그 제안으로 주문하고, 직거래 단계면 그 가격으로 직거래한다.
결제 체결·정산·시세 기록은 기존 주문 경로를 그대로 탄다.

## Requirements (EARS)

- D1. 입찰은 `세트 번호 × 상태 × 가격`이다. 상태 키는 매물 상태와 같다:
  `new_sealed`, `like_new`, `used_good`, `used_fair`, `damaged`.
- D2. `POST /api/v1/bids {setNumber, condition, price, durationDays}` — `durationDays ∈ {7, 30, 60}`(기본 30).
  카탈로그에 없는 세트는 404 `BID_SET_NOT_FOUND`, 가격이 1~100,000,000 밖이면 400 `BID_PRICE_INVALID`,
  기간이 허용값이 아니면 400 `BID_DURATION_INVALID`, 상태 키가 틀리면 400 `BID_CONDITION_INVALID`.
  `@RequiresOnboarding`.
- D3. 같은 사용자의 같은 `(세트, 상태)` 유효 입찰이 있으면 **새로 만들지 않고 가격·만료를 갱신**한다.
  응답은 200 `BidResponse`(생성·갱신 공통). 유효 입찰이 30건을 넘으면 409 `BID_LIMIT_EXCEEDED`.
- D4. `DELETE /api/v1/bids/{id}` — 본인만(403 `BID_ACCESS_DENIED`). 유효 `ACTIVE`가 아니면 409
  `BID_NOT_ACTIVE`. `CANCELED`로 바꾸고 204.
- D5. `GET /api/v1/bids/mine` — 내 입찰 최신순 최대 100건, 유효 상태 포함.
- D6. `GET /api/v1/bids/book/{setNumber}` — 공개. `{setNumber, conditions: [{condition, highestPrice|null,
  bidCount, levels: [{price, count}]}]}`. 다섯 상태 모두를 순서대로 담고, `levels`는 가격 내림차순 상위 5단.
  유효 `ACTIVE`만 센다. **입찰자 식별자는 내보내지 않는다.**
- D7. `POST /api/v1/bids/book/{setNumber}/fill {listingId}` — 판매자가 최고 입찰가에 판다.
  - 매물이 본인의 `ACTIVE` 매물이고 `catalogSetNumber == setNumber`여야 한다(아니면 403
    `LISTING_ACCESS_DENIED` / 409 `BID_LISTING_MISMATCH`). 상태는 매물 상태를 쓴다.
  - 그 `(세트, 상태)`의 유효 `ACTIVE` 중 **본인 입찰을 뺀** 최고가, 동가면 먼저 건 입찰을 고른다. 없으면
    409 `BID_NOT_FOUND`.
  - `ACTIVE → FILLED` 원자 전이(경합에 지면 다음 후보로, 최대 3회). 그다음
    `CreateAcceptedOfferUseCase.createAccepted(listingId, sellerId, bidderId, bidPrice, BID)`.
    제안 생성이 실패하면 입찰을 `ACTIVE`로 되돌리고 오류를 그대로 낸다.
  - 입찰자에게 `BID_FILLED`: `판매자가 입찰가 {가격}원에 {세트} 판매를 수락했어요. 72시간 안에 진행해 주세요`,
    링크 `/listings/{listingId}`, 키 `bid-filled:{bidId}`.
  - 응답 `{bidPrice, offerId, listingId}`. `@RequiresOnboarding @RequiresVerifiedSellerIdentity`.
- D8. WHEN 세트에 연결된 매물이 등록되거나 수정으로 가격이 내려가면, 시스템은 그 `(세트, 매물 상태)`의 유효
  입찰 중 `입찰가 ≥ 매물가`인 입찰자(판매자 제외, 최대 200명)에게 `BID_LISTING_MATCHED`를 보낸다:
  `입찰가 이하 매물이 올라왔어요: {title} {가격}원`, 링크 `/listings/{id}`, 키 `bid-match:{listingId}:{price}`.
  best-effort.
- D9. 만료는 `createdAt`/갱신 시각 + 기간. 스케줄러 없이 읽을 때 유효 상태 `EXPIRED`로 계산한다.
- D10. 출시 단계와 무관하게 열려 있다. 주문은 기존 출시 게이트를 그대로 탄다.
- D11. 계정 삭제 시 그 계정의 입찰을 지운다.

## Design

새 컨텍스트 `com.gole.api.bid`.

- domain `Bid`, `BidStatus(ACTIVE, CANCELED, FILLED)` + 유효 `EXPIRED`, `BidCondition`(D1 키, 자체 enum —
  다른 컨텍스트 도메인을 가져오지 않는다), `BidBook`.
- port-in `PlaceBidUseCase`, `CancelBidUseCase`, `ListMyBidsUseCase`, `GetBidBookUseCase`,
  `FillBidUseCase`, `NotifyMatchingBidsUseCase`.
- **순환 방지**: listing → bid(매칭 알림), bid → listing(매물 확인)·offer(제안 생성)이 함께 있으므로
  `NotifyMatchingBidsUseCase`는 **저장소와 알림만 의존하는 별도 서비스**(`BidMatchNotificationService`)가
  구현한다. listing은 port-out `ListingBidMatchNotifierPort.listingAvailable(listingId, sellerId, title,
  setNumber, conditionKey, price)`를 등록·가격 인하 시 부르고, 어댑터가 위 유스케이스에 위임한다.
- port-out: `BidRepositoryPort`, `BidCatalogPort`(세트 존재 — catalog `FindLegoSetUseCase`),
  `BidListingPort`(listing `GetListingUseCase`), `BidOfferPort`(offer `CreateAcceptedOfferUseCase`),
  `BidNotifierPort`(notification).
- persistence `bids` — 인덱스 `{setNumber, condition, status, price:-1, createdAt:1}`(호가·채움 후보),
  `{bidderId, createdAt:-1}`, `{bidderId, setNumber, condition, status}`.
- `GET /api/v1/bids/mine`은 세션 필요 — `UserAuthInterceptor` 비공개 접두사에 넣었다. `book`은 공개.

### 프론트

- core `bid` 모듈: 타입, `placeBid`, `cancelBid`, `fetchMyBids`, `fetchBidBook(setNumber)`, `fillBid(setNumber, listingId)`.
- 세트 상세 "구매 입찰" 섹션: 상태별 최고 입찰가(= 지금 팔면 받는 값)·건수·호가 단, "입찰하기" 폼
  (상태·가격·기간, 로그인 필요).
- 매물 상세 판매자 패널: 그 세트·상태에 입찰이 있으면 "최고 입찰가 N원에 바로 판매"(확인 후 `fill`).
- 프로필 "입찰" 탭: 내 입찰, 취소. 수락된 입찰은 매물로 가는 링크.
- 구매: 입찰에서 온 수락 제안도 `price-offer`의 "제안가로 구매" 경로를 그대로 쓴다.

### 구현하며 정한 것 (2026-10-09)

- **동가 순서는 `createdAt`이 아니라 `placedAt`(마지막으로 가격을 건 시각)이다.** 가격을 올려 다시 건 입찰이
  처음부터 그 가격이던 입찰보다 앞서면 안 된다. 호가 인덱스도 `{setNumber, condition, status, price:-1, placedAt:1}`.
- **재입찰(D3)과 체결(D7)의 원자 조건을 나눴다.** 체결·취소는 읽은 원본(저장 상태·`placedAt`·만료)을 조건으로
  걸어 재입찰 직전 가격으로 체결되지 않게 한다. 재입찰은 "아직 유효 `ACTIVE`"만 조건으로 덮어쓴다 — 같은 사람이
  연달아 고친 값끼리는 마지막 값이 이긴다. 원본 조건을 재입찰에도 걸면 동시 재입찰 6건이 서로를 계속 무효로
  만들어 재시도가 바닥나는 것을 통합 테스트가 잡았다.
- 같은 자리 저장 `ACTIVE`는 부분 유일 인덱스로 하나만 둔다. 만료된 `ACTIVE`는 새 입찰 직전에 저장 상태
  `EXPIRED`로 바꿔 자리를 비운다(`price-offer`의 만료 대기 제안 처리와 같다).
- `DELETE /api/v1/bids/{id}`에서 입찰이 아예 없으면 404 `BID_NOT_FOUND`다. 즉시 판매의 "받을 입찰 없음"(409
  `BID_NOT_FOUND`)과 코드는 같고 상태가 다르다.
- 즉시 판매는 판매 중이 아닌 매물도 409 `BID_LISTING_MISMATCH`로 거부한다(문구로 구분).
- **매물 하나는 입찰 하나만 받는다.** 이 매물로 체결한 입찰의 수락 제안이 아직 쓸 수 있으면 409
  `BID_LISTING_ALREADY_FILLED`. 겹쳐 체결하면 먼저 주문한 입찰자만 사고 나머지 입찰은 물건 없이 소진된다. 그 제안이
  만료·철회·거절되면 다음 입찰을 받는다. 같은 판매자의 동시 두 번 누름까지는 막지 않는다(매물 예약이 이중 구매를 막는다).

## 범위에서 뺀 것

- 판매 입찰(매도 호가) — 매물 자체가 매도 호가다.
- 입찰 시점 결제 보관·자동 체결 — 결제가 열리고 빌링키 계약이 생긴 뒤.
- 호가를 시세 그래프에 섞기 — 시세는 체결가만 쓴다.

## Tasks

- [x] B1 domain·호가 계산 + 테스트
- [x] B2 port·service(생성·갱신·취소·조회·채움·매칭 알림)
- [x] B3 persistence·인덱스, web, listing 매칭 훅
- [x] B4 단위 테스트(갱신 vs 생성, 본인 입찰 제외, 경합 재시도, 제안 실패 보상, 매칭 수신자)
- [x] B5 통합 테스트(동시 채움 한 명만)
- [ ] F1 core, 세트 상세 섹션, 판매자 즉시 판매, 프로필 입찰 탭
- [ ] V1 로컬 실검증: 입찰 → 매물 등록 시 매칭 알림 → 판매자 채움 → 입찰자 제안가 주문
