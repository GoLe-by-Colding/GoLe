# 상품 상태 고지 / 등급 — 구현 태스크

> 상태: **등급 확장 구현 완료.** 5단계 등급(R1.1/R1.2)이 백엔드~프론트 전 구간 동작하고,
> 후속으로 잡아 뒀던 **등급별 시세 분리**까지 함께 들어갔다. (2026-08-11)
> 남은 것은 **테스트 공백 2건**(고지 불변식 단위 테스트, e2e 단언)과 플랫폼 검수다.

## 백엔드 (listing)
- [x] 1. 등급 5단계 — `ItemCondition` { NEW_SEALED, LIKE_NEW, USED_GOOD, USED_FAIR, DAMAGED }.
      신규 `ItemGrade`를 만들지 않고 기존 enum을 확장했다(사유는 `design.md` 설계 변경 절).
      레거시 매핑은 `fromKey`(관대) / `parseKey`(엄격) / `storageNames()`(조회) 세 경로로 분리.
- [x] 2. `Listing` 도메인에 disclosure 반영(생성자/팩토리) — `Listing.java:21,35,47`, null이면 `basic()` 폴백
- [x] 3. `ListingDocument` 분해 필드 + `ListingPersistenceAdapter` 매핑(레거시 보정)
- [x] 4. `CreateListingUseCase.Command` + `ListingResponse` + 컨트롤러 DTO 확장/검증
- [x] 5. `ListingSeeder` 다양한 상태 조합 — 5등급 전부 등장
- [ ] 6. **고지 불변식 테스트 — 여전히 없음.** `ConditionDisclosure`의 누락노트 필수
      (`hasMissingParts && missingPartsNote.isBlank()` → 예외)과 1000자 상한을 검증하는
      테스트가 한 건도 없다. 등급 확장과는 별개 공백이라 이번에 손대지 않았다.

## 백엔드 (pricing) — 등급별 시세 분리
- [x] 12. `ConditionGroup` { SEALED, COMPLETE, INCOMPLETE } 도입. 고지 축(5등급)과 집계 축(3그룹) 분리.
- [x] 13. `SetCondition` 5등급 + 계수 + 그룹 소속 + `storageKeys()`(레거시 키 포함 조회)
- [x] 14. `PricingService.getValuation` 3단계 폴백(grade → group → model), `ValuationBasis` 노출
- [x] 15. `PriceTransactionRepositoryPort.findByConditionsAscending` — 그룹 표본 일괄 조회
- [x] 16. `PricingSeeder` 등급별 밴드 + 레거시 키 재매핑(삭제 없이 id 유지)
- [x] 17. 단위 테스트 — `SetConditionTest`, `PriceValuationTest`, `PricingServiceTest`(3단계 전환·레거시 흡수)

## 프론트
- [x] 7. `entities/listing` 타입/라벨 5단계 + `ITEM_CONDITIONS`(순서 단일 정의)
- [x] 8. `create-listing-form` 등급 선택 5단계
- [x] 9. listing-grid/상세에 구성·누락·하자 표시 — `listing-card.tsx`, `listing-detail-page.tsx`
- [x] 18. `entities/pricing` `ValuationBasis` + `valuationBasisLabel/Tone`,
      밸류에이션 표에 근거 표시(`실거래 N건` / `유사 등급 N건 기준` / `추정`)
- [x] 19. `shared/lib/seo` — `damaged` → schema.org `DamagedCondition`

## 함께 고친 선행 버그 (등급 확장과 무관하게 원래 깨져 있던 것)
- [x] 20. **소문자 enum 쿼리 파라미터 500.** 기본 컨버터가 `Enum.valueOf`라 대소문자를 가려
      `GET /api/v1/listings?condition=new_sealed`가 처음부터 500이었다(구 3단계에서도 동일).
      `ListingWebConfig`에 `Converter<String, ItemCondition>` 등록으로 해결.
- [x] 21. **소문자 enum JSON 본문 500.** 같은 이유로 매물 등록이 `completeness: "full_box"`에서
      실패했다. `spring.jackson.mapper.accept-case-insensitive-enums=true`로 해결.
      > `sort=price_asc` 등 **다른 enum 쿼리 파라미터는 아직 같은 문제가 남아 있다.**
      > `ListingSortOrder`·`ListingCategory`는 이번 범위 밖이라 손대지 않았다.

## 검증/배포
- [ ] 10. **e2e 미갱신.** `tests-e2e/` 어디에도 `completeness`/누락/풀박스/등급 관련 단언이 없다.
      5등급 확장에 대한 e2e도 없다.
- [x] 11. 커밋+push, 배포, listings 재시드 — 프로덕션 응답에 고지 필드 반영 확인
- [ ] 22. 배포 후 프로덕션에서 레거시 키 재매핑 로그 확인(`[seed] pricing: ... 레거시 키 N건 재매핑`)

## 후속
- [ ] 플랫폼 검수/정품 인증
- [ ] `MIN_REAL_SAMPLES`(현재 3)·등급 감가 계수를 설정으로 외부화 — 체결이 쌓이면 실측으로 재보정
- [ ] 나머지 enum 쿼리 파라미터 대소문자 처리(`sort`, `category`)
