# 롱테일 SEO / 구조화 데이터 — 설계

## 전략 요약

```
브랜드 검색(적음)          롱테일 검색(많음)
  "고레" "GoLe"      vs    "레고 10307 에펠탑 중고" "밀레니엄 팰컨 시세"
       │                          │
   기존 홈/layout            ← 신규 /sets/{setNumber} 가 흡수
```

세트 상세 페이지는 **이미 있는 3개 컨텍스트를 한 URL로 합치는 것**이라 신규 데이터가 필요 없다:

| 섹션 | 출처 API | 상태 |
|---|---|---|
| 세트 정보 | `GET /api/v1/catalog/sets/{setNumber}` | 기존 |
| 시세 통계 | `GET /api/v1/pricing/sets/{setNumber}/statistics` | 기존 |
| 활성 매물 | `GET /api/v1/listings?setNumber=` | **신규 파라미터 1개** |

## 백엔드 (변경 최소)

`listing` 컨텍스트에만 손댄다. 도메인 모델·문서 스키마는 그대로다
(`ListingDocument.catalogSetNumber`가 이미 존재).

- `ListingSearchQuery`에 `setNumber` 필드 추가. 기존 6-arg 생성자는 레거시 호환으로 유지
  (`ListingSearchQuery.java`가 이미 5-arg 레거시 생성자를 두는 관례를 따른다).
- `ListingPersistenceAdapter.search()`에 `catalogSetNumber` criteria 추가 —
  기존 `condition`/`category` 필터와 동일한 형태.
- `ListingController` `@RequestParam("setNumber")` 추가(optional).

**설계 판단:** 프론트에서 전체 매물을 받아 필터링하는 방법도 있으나, 매물이 늘면 세트 페이지마다
전체 목록을 전송하게 된다. 서버 필터가 정공법이고 변경량도 3개 파일로 작다.

## 프론트 (FSD)

### 신규 슬라이스

```
shared/ui/json-ld/          JsonLd 컴포넌트 — 구조화 데이터 렌더 단일 지점
views/set-detail/           세트 상세 화면
app/(main)/sets/[setNumber]/page.tsx
```

`JsonLd`를 공용화하는 이유: 현재 `layout.tsx`가 `dangerouslySetInnerHTML`을 인라인으로 쓰는데,
이 패턴이 5곳으로 퍼지면 XSS 리뷰 지점이 5개가 된다. 한 곳에서 직렬화하고
`<`를 이스케이프해 `</script>` 조기 종료를 막는다.

### 데이터 로딩

세트 상세는 **서버 컴포넌트**로 만든다. 색인 대상이므로 SSR HTML에 본문이 있어야 한다
(`views/seller-shop`처럼 `useEffect` 클라이언트 로딩이면 크롤러가 빈 페이지를 본다).

세 API를 `Promise.all`로 병렬 호출하고, 카탈로그 조회 실패 시 `notFound()`(R1.6).
시세·매물은 실패해도 페이지를 살린다(부분 실패 허용).

### 구조화 데이터 매핑

| 페이지 | 타입 | 근거 데이터 |
|---|---|---|
| 세트 상세 | `Product` + `AggregateOffer` + `BreadcrumbList` | 실매물 가격 범위·건수 |
| 매물 상세 | `Product` + `Offer` + `BreadcrumbList` | 매물 가격·상태 |
| 셀러샵 | `ProfilePage` + `AggregateRating` | `review` 평점 집계 |
| 커뮤니티 글 | `Article` | 게시글 제목·작성일 |

`AggregateOffer`는 **활성 매물이 1건 이상일 때만** 넣는다. 매물 0건인데 offer를 선언하면
구조화 데이터 정책 위반이다(R5.2).

`AggregateRating`도 `count > 0`일 때만 — `review` API가 후기 0건이면 `average: 0.0`을
반환하는데, 이걸 그대로 넣으면 별점 0점짜리 리치 스니펫이 노출된다.

### 상태 → schema.org 매핑

```
ACTIVE   → https://schema.org/InStock
RESERVED → https://schema.org/LimitedAvailability
SOLD     → https://schema.org/SoldOut
```

`itemCondition`은 `NEW_SEALED` → `NewCondition`, 그 외 → `UsedCondition`.
(`condition-disclosure` 등급 5단계 확장이 들어오면 이 매핑도 함께 갱신해야 한다.)

## sitemap 변경

- 추가: `/sets/{setNumber}` (카탈로그 전체), `/shops/{sellerId}` (매물 보유 셀러 distinct)
- 제거: `/collection` — 개인 화면. 페이지에도 `robots: { index: false }` 추가
- `changeFrequency`: 세트 상세는 매물/시세가 자주 바뀌므로 `daily`

## 검증

- `curl`로 SSR HTML에 JSON-LD와 본문 텍스트가 포함되는지 확인(크롤러 관점)
- `sitemap.xml`에 세트 URL이 나오는지 확인
- 없는 세트번호 → 404
- 기존 e2e(home/search/mobile) 회귀 없음
