# 롱테일 SEO / 구조화 데이터 — 구현 태스크

## 백엔드 (listing — 변경 최소)
- [x] B1 `ListingSearchQuery`에 `setNumber` 추가(레거시 생성자 2종 유지)
- [x] B2 `ListingPersistenceAdapter.search()` `catalogSetNumber` criteria
- [x] B3 `ListingController` `@RequestParam("setNumber")` (optional)
- [x] B4 `ListingServiceTest`에 setNumber 필터 테스트 3건(필터 적용/미적용/빈문자 정규화)

## 프론트 — 공용
- [x] F1 `shared/ui/json-ld` — `JsonLd` 컴포넌트(`</script>` 조기종료 방지 이스케이프) + 배럴
- [x] F2 `layout.tsx`를 `JsonLd`로 치환(인라인 `dangerouslySetInnerHTML` 제거)

## 프론트 — 세트 상세 (R1)
- [x] F3 `entities/listing`에 `setNumber` 파라미터 + `fetchListingsBySet`(ISR 300s)
- [x] F4 `views/set-detail` — 서버 컴포넌트, 세트정보/시세/매물 3섹션 + 공식 링크(R1.7)
- [x] F5 `app/(main)/sets/[setNumber]/page.tsx` — `generateMetadata`(R1.5)
      ※ R1.6 404는 부분 달성 — 아래 X1 참조
- [x] F6 세트 상세 JSON-LD: `Product` + `AggregateOffer`(매물>0일 때만) + `BreadcrumbList`

## 프론트 — 매물 상세 (R2)
- [x] F7 `Product`/`Offer` JSON-LD + availability·itemCondition 매핑
- [x] F8 OG 이미지(첫 사진 절대 URL) + 세트 연결 시 `BreadcrumbList`

## 프론트 — 셀러샵 · 커뮤니티 (R3)
- [x] F9 `/shops/[sellerId]` `generateMetadata` + `AggregateRating`(count>0일 때만)
- [x] F10 `/community/[id]` `generateMetadata` + `Article` JSON-LD

## sitemap · 색인 위생 (R4)
- [x] F11 sitemap에 세트 URL + 셀러샵 URL(매물 보유 셀러만) 추가
- [x] F12 `/collection` sitemap 제외 + `robots: { index: false }`

## 미해결 이슈 (2026-08-04 실측)
- [ ] X1 **soft 404** — `notFound()`가 404 UI는 렌더하지만 응답 상태가 **200**으로 나간다.
      dev·프로덕션 빌드 양쪽에서 재현(`/sets/99999` → 200, 라우트 자체가 없는 `/zzz` → 404).
      본 스펙에서 새로 생긴 문제가 아니라 앱 전역 동작이다 —
      `views/listing-detail/ui/listing-detail-page.tsx:26`도 같은 `notFound()`를 쓴다.
      현재는 `generateMetadata`에서 `robots: noindex`를 돌려 색인만 차단해 둔 상태다.
      R1.6("404를 반환해야 한다")을 완전히 충족하려면 상태코드 원인 규명이 필요하다.

## 검증 (2026-08-04 실측)
- [x] V1 `./gradlew test` — **97건 전부 통과**(실패 0/에러 0)
- [x] V2 `pnpm build` + `typecheck` + `fsd:lint` 전부 통과
- [x] V3 실측 확인
      - `GET /api/v1/listings?setNumber=10307` → 1건, 필터 없음 → 10건, 없는 세트 → `[]`
      - `/sets/10307` SSR HTML에 `<h1>레고 10307 에펠탑</h1>` + 시세 + 매물 포함
      - 없는 세트 404는 **미달성** — X1 참조
- [ ] V4 기존 e2e 회귀 확인 — 미실행(e2e가 개발 DB에 쓰는 문제가 선결돼야 함,
      `storefront-and-presentation` 후속 참조)

## 후속
- [ ] 동적 OG 이미지(`next/og`) — 세트/매물 카드형 이미지
- [ ] 세트 목록 색인 페이지(`/sets`) — 테마별 허브
- [ ] `condition-disclosure` 등급 5단계 도입 시 `itemCondition` 매핑 갱신
