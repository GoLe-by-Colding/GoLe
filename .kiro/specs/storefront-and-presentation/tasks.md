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
- [x] **e2e 전용 테스트 저장소 분리.** `application-e2e.yml`과 CI가 `gole_e2e` DB,
      `gole-e2e` 버킷, Redis DB 15를 사용한다. 시더 기본 DB도 `gole_e2e`이며
      `gole`·`admin`·`local` 및 Redis DB 0은 즉시 거부한다. 충돌 계정은 삭제하지 않고 오류로 중단해 개발 데이터가 보존된다.
      로컬은 `pnpm dev:api:e2e` → `pnpm e2e:seed` 순서로 전용 런타임을 사용한다.
