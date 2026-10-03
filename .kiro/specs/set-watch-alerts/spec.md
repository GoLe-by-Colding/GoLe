# 관심 세트 알림 (단종 임박·단종·새 매물) — Spec

> 콜드스타트 대응 1단계. `01_기획/경쟁 서비스 기능 비교와 도입 순서`의 "단종 임박 알림"을 구현하고,
> 같은 수신자 해석으로 "관심 세트 새 매물" 알림을 함께 연다. 새 컨텍스트 없이 catalog·listing이
> discovery(위시리스트)·collection(보유/희망)의 인바운드 포트와 notification을 잇는다.

## 왜 필요한가

- 단종은 가격이 뛰는 **확정 이벤트**다. 레고 수집가에게 재방문 이유가 된다.
- 단종 시점에 **보유자**에게 "지금 시세 확인"을 보내면 공급(매물)을 끌어낼 수 있다 — 콜드스타트의
  병목이 공급이다.
- 직거래(Stage 0~1)에서 매물·수요가 적을수록 "찾던 세트가 올라왔다"는 연결 한 번의 가치가 크다.
  지금은 셀러 팔로워에게만 새 매물 알림이 간다.

## Requirements (EARS)

- W1. 카탈로그 단종 상태에 `RETIRING_SOON`(단종 임박)을 추가한다. API 응답은 기존 규칙대로 소문자
  `retiring_soon`이다.
- W2. WHEN 관리자가 세트의 단종 상태를 바꾸면, 시스템은 **바뀐 경우에만** 관심 사용자에게 알린다.
  같은 상태로 다시 저장하면 알리지 않는다.
  - `RETIRING_SOON`: 위시리스트(`CATALOG_SET`) 사용자 + 컬렉션 `WANTED` 사용자.
  - `RETIRED`: 위 사용자에게 "단종됨", 컬렉션 `OWNED` 사용자에게 "단종됨 — 시세 확인" 문구.
    한 사용자가 양쪽에 속하면 보유자 문구 한 건만 보낸다.
  - `ACTIVE`로 돌아가는 변경은 알리지 않는다.
- W3. WHEN 카탈로그 세트에 연결된 매물이 등록되면, 시스템은 그 세트의 위시리스트·`WANTED` 사용자에게
  새 매물 알림을 보낸다. 판매자 본인은 제외한다.
- W4. 알림은 수신자별 멱등 키를 쓴다 — 단종: `set-retirement:{세트}:{상태}`,
  새 매물: `set-listing:{매물id}`. 재시도·재저장이 같은 단말을 두 번 울리지 않는다.
- W5. 알림·수신자 조회 장애는 흡수한다(best-effort). 세트 수정·매물 등록은 실패하지 않는다.
  한 수신자의 실패가 나머지를 막지 않는다.
- W6. 세트 상세 화면에 "관심 세트" 버튼을 둔다(지금은 매물 상세에만 있다). 단종 임박 세트는
  카드·상세에 "단종 임박" 배지를 보인다. 관리자 카탈로그 폼에서 세 상태를 고를 수 있다.

## Design

컨텍스트 간 연동은 기존 `NotificationNewListingNotifierAdapter` 패턴(내 아웃바운드 포트 → 상대
인바운드 포트 위임)을 그대로 따른다. 수신자 조회는 순환 의존을 피하려고 `SellerFollowerQueryService`
처럼 **저장소만 의존하는 읽기 서비스**로 분리한다.

- discovery: port-in `ListSetWishersUseCase.wishersOf(setNumber)` ← `SetWisherQueryService`.
  `WishlistRepositoryPort.findUserIdsByTarget` + Mongo 인덱스 `{targetType, targetId}`.
- collection: port-in `ListSetHoldersUseCase.holdersOf(setNumber, status)` ← `SetHolderQueryService`.
  `CollectionRepositoryPort.findUserIdsBySetAndStatus` + Mongo 인덱스 `{setNumber, status}`.
- notification: `NotificationType`에 `SET_RETIREMENT`, `WATCHED_SET_LISTING` 추가.
- catalog: port-out `SetRetirementNotifierPort.retirementChanged(setNumber, name, status)`.
  `CatalogService.update`가 이전 상태와 비교해 바뀐 경우에만 호출.
  어댑터 `catalog/adapter/out/notification/NotificationSetRetirementNotifierAdapter`.
- listing: `NewListingNotifierPort.notifySetWatchers(sellerId, listingId, title, setNumber)` 추가.
  `ListingService.create`가 `catalogSetNumber`가 있을 때 호출.
- 프론트: `@gole/core` `RetirementStatus`에 `retiring_soon` + `isRetiringSoon`, 세트 카드·상세 배지,
  세트 상세 `WishlistButton`, 관리자 카탈로그 폼 옵션.

### 범위에서 뺀 것

- 단종 예정일(`retiringAt`)과 날짜 기반 자동 전환 — 출처 데이터가 없다. 지금은 관리자 수동 전환.
- 알림 수신 설정(끄기) — 알림 전체 설정이 아직 없다. 생기면 같이 붙인다.
- 대량 팬아웃의 비동기화 — 세트당 관심 사용자가 수십 명 수준인 동안은 동기 루프로 충분하다.

## Tasks

- [x] B1 RetirementStatus.RETIRING_SOON, NotificationType 2종
- [x] B2 discovery 위시리스트 수신자 조회(port·adapter·service·인덱스)
- [x] B3 collection 보유/희망 수신자 조회(port·adapter·service·인덱스)
- [x] B4 catalog 단종 변경 감지 + 알림 어댑터
- [x] B5 listing 관심 세트 새 매물 알림
- [x] B6 단위 테스트(서비스 변경 감지, 어댑터 수신자 해석·중복 제거·장애 흡수)
- [x] F1 core 타입·배지, 세트 상세 관심 버튼, 관리자 폼
- [x] V1 로컬 실검증: 세트 상태 변경·매물 등록 → 알림 목록에 도착
