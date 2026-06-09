# IP 안전 콘텐츠 — 설계

## 카탈로그 (텍스트 중심 + 오리지널 커버 아트)
- `seed-media/`: 세트별 **GoLe 오리지널 커버 SVG**(고래+브릭 모티프, 테마 컬러, 세트명/번호)를 `scripts/seed-media/generate.mjs`로 생성해 백엔드 리소스에 번들. 공식 LEGO 이미지는 사용하지 않는다.
- `MediaSeeder`(@Order 0): 번들된 SVG를 MinIO 버킷에 멱등 업로드(키 `catalog/<번호>.svg`, `community/<slug>.svg`). 공개 조회는 `GET /api/v1/media/<key>`.
- `CatalogSeeder`: 시드 세트의 `imageUrl`을 `/api/v1/media/catalog/<번호>.svg`로 설정(상대경로 → 동일 출처 서빙). 공식 이미지 미호스팅 원칙은 유지.
- `LegoSetCard`: `imageUrl`이 있으면 실제 커버를 렌더(`object-cover`), 없으면 중립 플레이스홀더(🧱). 하단에 "레고 공식 페이지 ↗" 외부 링크 추가.
  - URL: `https://www.lego.com/ko-kr/search?q={setNumber}` (검색 — 죽은 링크/사칭 방지)
  - `target="_blank"`, `rel="noopener noreferrer nofollow"`.

## 매물 사진 (사용자 직접 촬영)
- 서버: `Listing` 도메인이 이미 사진 ≥1 강제(`MissingPhotoException`). 변경 없음.
- 프론트 `create-listing-form`: 사진 필드 라벨/힌트를 "본인이 직접 촬영한 사진"으로 명확히 하고
  공식 이미지 사용 금지를 안내(필수 유지).

## 상표 고지 (전역 푸터)
- `widgets/site-footer`(신규): 고지문구 + (선택) 외부 링크 안내.
- `app/(main)/layout.tsx`에 `<SiteFooter/>` 추가(헤더와 동일 셸). 인증 레이아웃은 선택.

## 카피/문구
- 고지: "LEGO®, 레고®는 LEGO Group의 상표이며, 본 사이트는 LEGO Group의 후원·승인을 받지 않습니다."
- 사진 힌트: "직접 촬영한 실물 사진을 올려주세요. 공식 제품 이미지 도용은 금지됩니다."

## 영향 범위
- 백엔드: `MediaSeeder`(신규, MinIO 멱등 업로드), `CatalogSeeder`/`CommunitySeeder`(imageUrl → MinIO 경로). (도메인/포트 변경 없음)
- 프론트: `LegoSetCard`, `create-listing-form`, `widgets/site-footer`(신규), `(main)/layout`.
