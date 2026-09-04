# 거래 후기/평점 (Review) — 구현 태스크

> 상태: **전부 구현·배포 완료.** (2026-08-03 실측 감사로 소급 체크)
> 현재 운영 확인 주소: `GET https://gole.co.kr/api/v1/sellers/{sellerId}/rating`
> → `{"average":4.7,"count":3}` 정상 응답. 로컬 `ReviewSeeder` 7건 적재 확인.

## 백엔드 (헥사고날 구현 순서)

- [x] 1. 도메인: `Review`, `SellerRatingSummary`, `InvalidRatingException`, `DuplicateReviewException` (R1, R3.3)
- [x] 2. in-port: `WriteReviewUseCase`, `GetSellerReviewsUseCase` (R1, R3)
- [x] 3. out-port: `ReviewRepositoryPort`, `ReviewIdGeneratorPort`, `OrderQueryPort`
- [x] 4. service: `ReviewService` (자격 검증 R2 + 작성 + 조회/집계)
- [x] 5. persistence 어댑터: `ReviewDocument`, `ReviewMongoRepository`, `ReviewPersistenceAdapter`
- [x] 6. id 어댑터: `ReviewIdGenerator`
- [x] 7. cross-context 어댑터: `OrderQueryAdapter` (order `GetOrderUseCase` 위임)
- [x] 8. web: `ReviewDtos`, `ReviewController` (POST/GET 3종)
- [x] 9. 단위 테스트: `ReviewServiceTest`

## 프론트 (FSD)

- [x] 10. `entities/review` — 타입 + API 함수
      ※ 설계 문서는 `useSellerReviews`/`useSellerRating` **훅**으로 기술했으나, 실제 구현은
      평범한 async 함수(`fetchSellerReviews`/`fetchSellerRating`)다. 호출부(`views/seller-shop`)가
      `useEffect` + `Promise.all`로 직접 로드한다. 기능상 동등하며 FSD 경계도 유지된다.
- [x] 11. `features/write-review` — 작성 폼 + 뮤테이션 (`views/order-detail`에서 사용)
- [x] 12. `views/seller-shop` — 평점 배지(`data-testid="seller-rating"`) + 후기 목록 연동

## 검증/배포

- [x] 13. `./gradlew test` + `./gradlew bootJar` 통과
- [x] 14. `pnpm --filter web build` + steiger lint 통과
- [x] 15. 커밋 & main push (`6d2b4df` 후기 시드, `b5146a1` 판매자 미니 프로필 카드)
- [x] 16. 컨테이너 배포 + 프로덕션 응답 확인

## 후속 (TODO)

- [x] 후기 작성 진입 동선 강화 — 완료 주문 목록은 `#review` CTA를 표시하고,
      수동·자동 구매확정 및 판매자 승 분쟁 알림도 같은 앵커로 유도
- [x] 판매자 답글(reply) 기능 — 판매자 본인만 작성·수정 가능, 셀러샵 공개 노출,
      서비스·컨트롤러·브라우저 회귀 테스트 포함
- [x] 후기 신고/블라인드 처리 — `report` 컨텍스트의 `REVIEW` 대상으로 접수하고,
      어드민 조치 시 원문을 보존한 채 공개 목록·평점 집계에서 제외
