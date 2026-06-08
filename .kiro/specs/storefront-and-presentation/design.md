# 스토어프론트 데이터 · 표현 — 설계

## 시드 데이터 (컨텍스트별 CommandLineRunner)

- `catalog/bootstrap/CatalogSeeder` (Order1): `LegoSetMongoRepository`로 실제 12세트, featured 8, 브랜드색 placeholder 이미지.
- `listing/bootstrap/ListingSeeder` (Order2): `CreateListingUseCase` in-port로 10개 매물(seller-aurora/brickbank/minifig).
- `pricing/bootstrap/PricingSeeder` (Order3): `PriceTransactionMongoRepository`로 5세트×25주 체결 이력(결정적 변동).
- `community/bootstrap/CommunitySeeder` (Order4): `PublishPostUseCase`로 자랑/MOC 게시글.
- 게이트: `@ConditionalOnProperty(gole.<ctx>.seed-on-empty, matchIfMissing=true)` + `count()==0` 가드.

## SEO / GEO (Next.js App Router)

- `app/layout.tsx`: `metadata`(metadataBase, title template `%s · GoLe`, OG/Twitter/robots/keywords/canonical) + JSON-LD(`Organization`+`WebSite`/SearchAction).
- `app/robots.ts`, `app/sitemap.ts`: 공개 경로 색인, 비공개 disallow.
- 각 page.tsx: 공개 페이지 per-page `metadata`, 비공개 `robots: { index:false }`.
- `shared/config/env.ts`: `siteUrl`(NEXT_PUBLIC_SITE_URL, 기본 https://gole.kscold.com).

## 브랜드 토큰 (`app/globals.css @theme`)

| 역할 | 토큰 | 핵심값 |
|---|---|---|
| Primary | `brand-*` (GoLe Cobalt) | `#2f56e6` |
| Accent | `accent-*` (Brick Yellow) | `#fbb500` |
| Elevation | `--shadow-soft` / `--shadow-lift` | 절제된 그림자 |

- 공용 프리미티브(Button/Card/Badge)와 홈 히어로가 토큰을 사용 → 일관 적용.
- 상세 원칙은 `.kiro/steering/brand-identity.md` 참조.

## 모바일

- `widgets/site-header`: `useState` 토글 햄버거(`max-sm:`에서 노출), 모바일 패널에 전체 내비 + 계정 액션. `LinkButton`에 `onClick` 추가(메뉴 닫기).
- 레이아웃은 Tailwind 반응형 유틸(`max-sm:`/`sm:`/`lg:`)과 `Container`로 대응.

## 테스트 (Playwright)

- `playwright.config.ts`: `chromium`(데스크톱, mobile.spec 제외) + `mobile-chrome`(Pixel 5, mobile.spec 전용). pm2 서버 재사용.
- 스펙: home/auth/create-listing/purchase(데스크톱) + mobile(오버플로우 없음, 햄버거 내비).
- 브라우저: 컨테이너에 Chromium + OS 라이브러리 1회 설치(`npx playwright install --with-deps chromium`).
