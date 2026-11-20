# 매물 검색 페이지 나누기 — Spec

> 공개 매물 검색 `GET /api/v1/listings`가 조건에 맞는 활성 매물을 정렬만 해서 **전부** 돌려준다(2026-11-21 확인).
> 다른 목록 API(채팅 1~100, 부품 1~50, 정산 `MAX_ROWS`, 피드 100, 시세 트렌딩 `MAX_LIMIT`)는 모두 상한이 있다.
> 운영 활성 매물은 2026-11-21 기준 0개라 지금 영향은 없다. 매물이 쌓이기 전에 페이지 경로를 먼저 연다.

## 왜 필요한가

- 검색 한 번이 활성 매물 전체를 읽고 내려보낸다. 매물 수에 비례해 응답 크기·DB 부하가 커진다.
- 기존 경로에 상한만 걸면 화면은 잘린 줄 모른 채 일부만 보여 준다. 성공을 가장하는 셈이라 하지 않는다.
- 정렬에 동점 기준이 없어 같은 가격·같은 시각이 겹치면 순서가 흔들린다. 페이지를 나누면 빠지거나 겹친다.

## Requirements (EARS)

- P1. WHEN 클라이언트가 `GET /api/v1/listings/page`를 부르면, 시스템은 기존 검색과 같은 필터(`query`·`condition`·
  `minPrice`·`maxPrice`·`sort`·`category`·`setNumber`)로 활성 매물을 찾아 `{items, nextCursor}`를 돌려준다.
- P2. `limit`은 1~60, 기본 24다. 범위를 벗어나면 가까운 끝값으로 당긴다(부품 요청과 같은 규칙).
- P3. 정렬마다 동점을 `_id`로 가른다. `NEWEST`는 `listedAt` 내림차순 → `_id` 내림차순, `PRICE_ASC`는 `priceAmount`
  오름차순 → `_id` 오름차순, `PRICE_DESC`는 `priceAmount` 내림차순 → `_id` 내림차순.
- P4. 다음 페이지가 있으면 `nextCursor`는 마지막 항목의 정렬 키와 `_id`를 담은 불투명 문자열이고, 없으면 `null`이다.
  다음 요청은 같은 필터·정렬에 `cursor`를 붙인다.
- P5. 커서가 해석되지 않거나 다른 정렬의 커서면 400 `LISTING_CURSOR_INVALID`.
- P6. 기존 `GET /api/v1/listings`의 응답과 동작은 바꾸지 않는다. 웹·모바일이 페이지 경로로 옮긴 뒤 별도 PR에서 정리한다.
- P7. 관심 테마 이름으로 찾을 때 테마 세트 번호를 더하는 기존 검색 규칙은 페이지 경로에도 같다.

## 설계

- **커서는 문자열 하나다.** 커뮤니티 피드(`/posts/page`)는 `beforeCreatedAt`·`beforeId`를 따로 받지만, 매물은
  정렬에 따라 키가 시각 또는 가격으로 바뀐다. 정렬 이름까지 담은 `base64url("{sort}|{key}|{id}")` 하나로 받아
  정렬이 다른 커서를 거절한다(P5).
- 데이터 모양: `application/query/ListingPageCursor`(sort, key, id)가 인코딩·해석을 맡는다. 해석에 실패하면 빈 값을 돌려주고, 컨트롤러 경계에서 400으로 바꾼다.
- 다음 페이지 판정은 `limit + 1`건을 읽어 남는지로 정한다. 추가 조회(count)는 하지 않는다.
- 저장소 조건(`NEWEST` 예): `listedAt < k OR (listedAt == k AND _id < id)`. 가격 정렬도 같은 꼴이다.
- 헥사고날 순서: query(`ListingPageCursor`·`ListingPage`) → port.in `SearchListingPagesUseCase` → port.out
  `ListingRepositoryPort.searchPage` → `ListingService` → `ListingPersistenceAdapter` → `ListingPageController`.
  기존 `SearchListingsUseCase`는 람다로 만드는 시험이 있어 함수형 인터페이스로 두고 따로 나눴다. 컨트롤러도 생성자를
  바꾸지 않으려고 같은 경로 아래 별도 클래스로 뒀다.

## 알려진 성질

- 최신순 커서는 저장된 `listedAt`에 기댄다. 끌올 도입 전 문서는 `ListingListedAtBackfill`이 앱 시작마다 채운다.
  백필 전 문서는 Mongo 정렬에서 맨 뒤로 가고 커서 조건(`$lt`·`$eq`)에 걸리지 않아 빠진다. 통합 시험
  `legacyListingWithoutStoredListedAtIsPagedAfterBackfill`이 이 전제를 지킨다. 스크립트로 문서를 직접 넣을 때도 `listedAt`을 채운다.
- 넘기는 도중 끌올된 매물은 이미 지난 위치로 올라가 이번 탐색에서는 보이지 않는다. 키셋 페이징의 일반적인 성질이라 받아들인다.

## Tasks

- [x] T1. 백엔드 페이지 경로(P1~P5, P7)와 시험
- [ ] T2. 웹 `views/search`가 페이지 경로와 "더 보기"를 쓰게 함 — PR #267(검색 화면) 머지 뒤
- [ ] T3. 모바일 `views/search` 같은 전환
- [ ] T4. 웹·모바일이 모두 옮긴 뒤 기존 `GET /api/v1/listings`에 상한을 두거나 페이지 경로로 합침(P6)
