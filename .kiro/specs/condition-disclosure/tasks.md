# 상품 상태 고지 / 등급 — 구현 태스크

## 백엔드 (listing)
- [ ] 1. `ItemGrade`, `Completeness` enum + `ConditionDisclosure` 값 객체(누락노트 불변식)
- [ ] 2. `Listing` 도메인에 disclosure 반영(생성자/팩토리)
- [ ] 3. `ListingDocument` 분해 필드 + `ListingPersistenceAdapter` 매핑(레거시 보정)
- [ ] 4. `CreateListingUseCase.Command` + `ListingResponse` + 컨트롤러 DTO 확장/검증
- [ ] 5. `ListingSeeder` 다양한 상태 조합
- [ ] 6. 도메인/서비스 테스트(누락노트 필수 등)

## 프론트
- [ ] 7. `entities/listing` 타입/라벨 확장
- [ ] 8. `create-listing-form` 등급·구성·플래그·노트 입력(검증)
- [ ] 9. listing-grid/상세에 등급·구성·누락·하자 표시(뱃지+고지 섹션)

## 검증/배포
- [ ] 10. 백엔드 test + 프론트 build/lint + e2e(create-listing/purchase 갱신)
- [ ] 11. 커밋+push, deploy.sh, listings 재시드, 라이브 확인

## 후속
- [ ] 플랫폼 검수/정품 인증
- [ ] 등급별 시세 분리
