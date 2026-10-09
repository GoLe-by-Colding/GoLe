# 가격 제안(네고) — Spec

> 커머스 기본기 1순위. 채팅에 구조화된 제안이 없어 합의 가격이 대화문 속에 묻혔다. 제안 → 수락 →
> 그 가격으로 주문까지 잇는다. `01_기획/경쟁 서비스 기능 비교와 도입 순서` 1단계의 남은 항목.

## 왜 필요한가

- 중고거래 체결의 상당수가 네고를 거친다. 구조가 없으면 합의가가 주문으로 이어지지 않고, 결제가 열린
  뒤에도 구매자는 정가로 주문하거나 판매자가 매물을 지웠다 다시 올려야 한다.
- 합의가가 주문 금액이 되면 정산·결제·시세가 **기존 경로 그대로** 합의가를 쓴다.
- 단, 시세에는 **결제 완료 후 구매확정된 주문**만 들어간다(기존 규칙). 채팅 합의가 자체는 검증할 수
  없으므로 시세에 넣지 않는다 — 기획 갭 목록 P2 "직거래 체결가 시세 오염"과 같은 문제다.

## Requirements (EARS)

### 제안 생성 — 구매자

- O1. WHEN 매물 채팅방의 구매자가 `POST /api/v1/offers {roomId, price}`를 보내면, 시스템은 `PENDING`
  제안을 만든다. 응답 201 `OfferResponse`.
- O2. 가드는 채팅 메시지 전송과 같다 — 방 참여·차단·정지·종료 검사(`requireSendable`),
  제3자 제공 동의(`requireCurrent(actor)`), `@RequiresOnboarding`. 판매자 신원확인
  (`requireVerifiedSeller(sellerId)`)도 건다 — 주문과 같은 "새 판매 행동"이다.
- O3. 요청자가 그 방의 **구매자**가 아니면 403 `OFFER_BUYER_ONLY`. 매물 방(`LISTING`)이 아니면 400
  `OFFER_ROOM_NOT_LISTING`.
- O4. 매물이 `ACTIVE`가 아니면 409 `OFFER_LISTING_UNAVAILABLE`. 가격이 `0 < price < 매물가`가 아니면
  400 `OFFER_PRICE_INVALID`.
- O5. 같은 매물에 구매자의 유효한 `PENDING`이 이미 있으면 409 `OFFER_ALREADY_PENDING`. 같은 매물에
  24시간 안에 5건을 넘기면 429 `OFFER_RATE_LIMITED`.
- O6. 생성 후 방에 구매자 명의로 메시지 `[가격 제안] {가격}원을 제안했어요`를 남기고, 판매자에게
  `OFFER_RECEIVED`(링크 `/chat?room={roomId}`, 키 `offer-received:{offerId}`)를 보낸다.
  메시지·알림 실패는 흡수한다 — 제안 상태가 원장이다.

### 응답 — 판매자·구매자

- O7. `POST /api/v1/offers/{id}/accept` — 판매자만. 유효한 `PENDING`이 아니면 409 `OFFER_NOT_PENDING`,
  매물이 `ACTIVE`가 아니면 409 `OFFER_LISTING_UNAVAILABLE`. `ACCEPTED`로 바꾸고 만료를
  `수락 시각 + gole.offer.accepted-ttl`(기본 `PT72H`)로 다시 잡는다. 방 메시지
  `[가격 제안] {가격}원 제안을 수락했어요`, 구매자에게 `OFFER_ACCEPTED`(링크 `/listings/{listingId}`,
  키 `offer-accepted:{id}`).
- O8. `POST /api/v1/offers/{id}/decline` — 판매자만. `PENDING`·`ACCEPTED`를 `DECLINED`로. 그 외 409
  `OFFER_NOT_OPEN`. 방 메시지(거절 또는 수락 취소), 구매자에게 `OFFER_DECLINED`(키 `offer-declined:{id}`).
- O9. `POST /api/v1/offers/{id}/withdraw` — 구매자만. `PENDING`·`ACCEPTED`를 `WITHDRAWN`으로. 그 외 409
  `OFFER_NOT_OPEN`. 방 메시지만 남기고 알림은 없다.
- O10. 당사자가 아니면 403 `OFFER_ACCESS_DENIED`, 없으면 404 `OFFER_NOT_FOUND`.
- O11. 상태 전이는 **현재 상태를 조건으로 한 원자적 갱신**이다. 동시에 수락·철회하면 하나만 이긴다.

### 만료

- O12. `PENDING`은 `생성 + gole.offer.pending-ttl`(기본 `PT48H`), `ACCEPTED`는 O7의 만료를 지나면
  **유효 상태가 `EXPIRED`**다. 스케줄러 없이 읽을 때 계산한다. 응답 `status`는 유효 상태다.

### 조회

- O13. `GET /api/v1/offers?roomId=` — 그 방 참여자만. 최신순 최대 50건.
- O14. `GET /api/v1/offers?listingId=` — 판매자면 그 매물의 모든 제안, 아니면 내 제안만. 최신순 최대 50건.
- O15. `OfferResponse`: `id, listingId, roomId(nullable), buyerId, sellerId, price, listingPriceAtOffer,
  origin("chat"|"bid"), status("pending"|"accepted"|"declined"|"withdrawn"|"expired"), createdAt,
  respondedAt(nullable), expiresAt`.

### 주문 연결

- O16. `POST /api/v1/orders` 본문에 선택 필드 `offerId`(≤100)를 받는다. WHEN 주어지면, 시스템은 예약·자기거래
  검사 뒤에 그 제안이 **이 매물·이 구매자·유효 `ACCEPTED`**인지 확인한다. 아니면 예약을 풀고 409
  `OFFER_NOT_USABLE`.
- O17. 주문 금액은 `min(제안가, 예약 시점 매물가)`다. 판매자가 그 사이 가격을 더 내렸으면 더 싼 쪽.
- O18. 주문에 `offerId`를 남긴다(`OrderResponse.offerId`, nullable). 제안은 소모하지 않는다 — 결제 대기
  만료로 예약이 풀리면 같은 제안으로 다시 주문할 수 있고, 이중 구매는 매물 예약이 막는다.
- O19. 시세 기록은 바뀌지 않는다. 구매확정 시 `order.amount`(= 합의가)가 기존 경로로 들어간다.
- O20. 출시 단계와 무관하게 제안은 열려 있다. 직거래 단계(`DIRECT_CHAT`)에서는 수락된 제안이 "합의한
  가격" 표시이고, 거래 완료는 기존 직거래 확인으로 한다(시세 기록 없음).

### 입찰에서 오는 제안

- O21. port-in `CreateAcceptedOfferUseCase.createAccepted(listingId, sellerId, buyerId, price, origin=BID)`.
  매물이 그 판매자의 `ACTIVE` 매물이어야 한다. 방 없이(`roomId=null`) 바로 `ACCEPTED`로 만든다. 가격이
  매물가 이상이어도 된다 — 주문 금액은 O17로 매물가를 넘지 않는다. (`buy-bids` 스펙이 쓴다)

### 탈퇴

- O22. 계정 삭제 시 그 계정이 구매자·판매자인 제안을 지운다(`MongoAccountDeletionAdapter`).

## Design

새 컨텍스트 `com.gole.api.offer` — domain `PriceOffer`·`OfferStatus`·`OfferOrigin` → port-in
(`MakeOfferUseCase`, `RespondToOfferUseCase`(accept/decline/withdraw), `ListOffersUseCase`,
`ResolveAcceptedOfferUseCase.usablePrice(offerId, listingId, buyerId, now)`, `CreateAcceptedOfferUseCase`)
→ port-out → service → persistence → web.

- 의존 방향: offer → listing(`GetListingUseCase`), offer → chat(아래 두 인바운드 포트), offer → notification.
  order → offer(`ResolveAcceptedOfferUseCase`). listing·chat은 offer를 모른다. **순환이 생기지 않게** 이 방향을
  지킨다.
- chat에 인바운드 포트 2개 추가:
  - `ListingRoomAccessUseCase.requireSendableListingRoom(roomId, actorId)`·`requireReadableListingRoom(...)` →
    `ListingRoomParticipants(roomId, listingId, buyerId, sellerId)`. 기존 `SocialChatService.requireSendable`
    ·`requireReadable`과 `chat_rooms` 문서의 구매자·판매자를 쓴다.
  - `PostChatMessageUseCase.post(roomId, actorId, content)` → 기존 `ChatMessagingService.send` 경로
    (저장·읽음·SSE 발행)를 그대로 탄다.
- persistence `offers` 컬렉션. 인덱스: `{listingId, buyerId, createdAt}`, `{roomId, createdAt}`,
  `{listingId, createdAt}`, partial unique `{listingId, buyerId}` where `status: "PENDING"`(저장 상태 기준 —
  만료된 `PENDING`은 새 제안 전에 `EXPIRED`로 원자 전이해서 자리를 비운다).
- properties `gole.offer.pending-ttl`, `gole.offer.accepted-ttl`, `gole.offer.max-per-listing-per-day`.
- order: `PlaceOrderCommand`에 `offerId`, port-out `AcceptedOfferPort` ← 어댑터가
  `ResolveAcceptedOfferUseCase`에 위임. `Order`·`OrderDocument`·`OrderResponse`에 `offerId`.
  `/api/v1/orders` POST 매핑은 그대로라 `LaunchGateInterceptor` 분류는 바뀌지 않는다.
- `GET /api/v1/offers`는 세션이 필요하다 — `UserAuthInterceptor`의 비공개 조회 접두사에 넣었다.

### 프론트

- core `offer` 모듈: 타입, `makeOffer(roomId, price)`, `acceptOffer`, `declineOffer`, `withdrawOffer`,
  `fetchRoomOffers(roomId)`, `fetchListingOffers(listingId)`. core `order`: `placeOrder(..., offerId?)`,
  `Order.offerId`.
- `features/chat-listing`에 `ListingOfferPanel` — 메시지 목록 위 배너. 구매자: "가격 제안" 입력(매물가·할인율
  표시), 진행 중 제안 상태, 철회. 판매자: 대기 제안 수락·거절. 수락됨: 결제가 열려 있으면 "제안가로 구매하기"
  (매물 상세로), 직거래 단계면 "합의한 가격 N원 — 직거래로 진행". 10초 폴링 + 새 메시지 도착 시 다시 읽는다.
  `InlineChatPanel`과 `/chat`의 매물 방 양쪽에 붙인다. 동의 래퍼는 `CHAT_MESSAGE` 경로를 쓴다.
- `features/purchase`: 내 유효 `ACCEPTED` 제안이 있으면 "제안가 N원으로 구매"로 `offerId`를 실어 주문.
- 매물 상세 판매자 패널: 받은 제안 목록(수락·거절).

## 범위에서 뺀 것

- 판매자 역제안(카운터) — 거절 후 매물 가격을 내리는 것으로 대신한다.
- 제안 메시지의 별도 메시지 종류 — 채팅 메시지에 종류 필드가 없다. 텍스트 메시지 + 제안 원장으로 충분하다.
- 제안 만료 알림.

## Tasks

- [x] B1 domain + 상태 전이·만료 테스트
- [x] B2 chat 인바운드 포트 2개
- [x] B3 offer port·service·persistence·web + 단위 테스트
- [x] B4 order `offerId` 연결 + `OrderServiceTest` 보강(금액 min, 거부 시 예약 해제)
- [x] B5 통합 테스트(동시 수락·철회 하나만 성공, 대기 제안 유일성)
- [ ] F1 core offer·order
- [ ] F2 채팅 제안 배너, 구매 버튼 제안가, 판매자 받은 제안
- [ ] V1 로컬 실검증: 제안 → 수락 → 제안가 주문 → 결제·구매확정 → 시세에 합의가
