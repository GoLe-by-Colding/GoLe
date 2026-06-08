# 거래 후기/평점 (Review) — 구현 태스크

## 백엔드 (헥사고날 구현 순서)

- [ ] 1. 도메인: `Review`, `SellerRatingSummary`, `InvalidRatingException`, `DuplicateReviewException` (R1, R3.3)
- [ ] 2. in-port: `WriteReviewUseCase`, `GetSellerReviewsUseCase` (R1, R3)
- [ ] 3. out-port: `ReviewRepositoryPort`, `ReviewIdGeneratorPort`, `OrderQueryPort`
- [ ] 4. service: `ReviewService` (자격 검증 R2 + 작성 + 조회/집계)
- [ ] 5. persistence 어댑터: `ReviewDocument`, `ReviewMongoRepository`, `ReviewPersistenceAdapter`
- [ ] 6. id 어댑터: `ReviewIdGenerator`
- [ ] 7. cross-context 어댑터: `OrderQueryAdapter` (order `GetOrderUseCase` 위임)
- [ ] 8. web: `ReviewDtos`, `ReviewController` (POST/GET 3종)
- [ ] 9. 단위 테스트: `ReviewServiceTest`

## 프론트 (FSD)

- [ ] 10. `entities/review` — 타입 + API 훅 (`useSellerReviews`, `useSellerRating`)
- [ ] 11. `features/write-review` — 작성 폼 + 뮤테이션
- [ ] 12. `views/seller-shop` — 평점 배지 + 후기 목록 연동

## 검증/배포

- [ ] 13. `./gradlew test` + `./gradlew bootJar` 통과
- [ ] 14. `pnpm --filter web build` + steiger lint 통과
- [ ] 15. 커밋 & main push (feat(spec)/feat(backend)/feat(frontend) 분리)
- [ ] 16. 컨테이너 배포 + `/actuator/health` 확인
