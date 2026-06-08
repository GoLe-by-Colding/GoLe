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

## 후속 (TODO)
- [ ] 실제 제품 이미지 정책/호스팅
- [ ] 상품상세/시세/관리자 화면 모바일 정교화
- [ ] 동적 sitemap(상세 URL)
- [ ] e2e 전용 테스트 DB 분리(현재 운영 DB에 기록)
