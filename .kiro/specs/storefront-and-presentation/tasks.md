# 스토어프론트 데이터 · 표현 — 구현 태스크

> 상태: 전부 구현·배포 완료. (소급 문서화)

## 시드 데이터
- [x] 1. CatalogSeeder / ListingSeeder / PricingSeeder / CommunitySeeder (R1)
- [x] 2. `gole.*.seed-on-empty` 설정 + 테스트 격리(false)

## SEO / GEO
- [x] 3. layout metadata + Organization/WebSite JSON-LD (R2.1, R2.3)
- [x] 4. robots.ts / sitemap.ts (R2.2)
- [x] 5. per-page metadata(공개) + noindex(비공개) (R2.4)

## 브랜드 / 디자인
- [x] 6. GoLe Cobalt + Brick Yellow 토큰, 미니멀 정리 (R3)
- [x] 7. 경쟁사명 카피 제거 + brand-identity steering 문서

## 모바일
- [x] 8. 헤더 햄버거 메뉴 + 반응형 (R4)

## 테스트
- [x] 9. Playwright Chromium 구동 + 깨진 home 스펙 수정 (R5.1)
- [x] 10. 모바일 프로젝트(Pixel 5) + mobile.spec (R5.2)

## 후속 (TODO) — 2026-08-03 실측 감사 반영
- [x] 실제 제품 이미지 정책/호스팅 — `ip-safe-content` R1.3(오리지널 커버 아트) +
      `image-upload`(MinIO 호스팅)로 해소
- [x] 동적 sitemap(상세 URL) — `app/sitemap.ts`가 `/api/v1/listings`·`/api/v1/community/posts`를
      fetch해 각 100건까지 상세 URL 생성(`sitemap.ts:29-65`, `revalidate: 3600`)
- [ ] 상품상세/시세/관리자 화면 모바일 정교화
- [ ] **e2e 전용 테스트 DB 분리 — 실제 오염 확인됨(우선순위 상향).**
      로컬 `gole` DB에서 테스트 잔여 데이터 발견:
      `{title: "환불검증", price: 0, sellerId: "s", photoUrls: ["a.jpg"]}` 등이
      `listings` 컬렉션에 남아 실제 매물 목록에 섞여 노출된다.
      `t`/`t2`/`txncheck` 같은 테스트 컬렉션도 잔존.
      `playwright.config.ts`에 DB 격리 설정이 없어 e2e가 개발 DB에 직접 쓴다.
