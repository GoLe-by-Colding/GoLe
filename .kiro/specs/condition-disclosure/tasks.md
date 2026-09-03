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
- [x] 6. **고지 불변식 테스트.** `ConditionDisclosure`의 누락노트 필수
      (`hasMissingParts && missingPartsNote.isBlank()` → 예외)과 1000자 상한을 검증하는
      경계·정규화·보수적 레거시 기본값을 `ConditionDisclosureTest`에서 검증한다.

## 백엔드 (pricing) — 등급별 시세 분리
- [x] 12. `ConditionGroup` { SEALED, COMPLETE, INCOMPLETE } 도입. 고지 축(5등급)과 집계 축(3그룹) 분리.
- [x] 13. `SetCondition` 5등급 + 계수 + 그룹 소속 + `storageKeys()`(레거시 키 포함 조회)
- [x] 14. `PricingService.getValuation` 3단계 폴백(grade → group → model), `ValuationBasis` 노출
- [x] 15. `PriceTransactionRepositoryPort.findByConditionsAscending` — 그룹 표본 일괄 조회
- [x] 16. `PricingSeeder` 등급별 밴드 + 레거시 키 재매핑(삭제 없이 id 유지)
- [x] 17. 단위 테스트 — `SetConditionTest`, `PriceValuationTest`, `PricingServiceTest`(3단계 전환·레거시 흡수)
- [x] 24. **그룹 환산 기준계수 편향 수정.** 앵커(`medianPrice`)는 표본 가중인데 기준계수는
      등급 계수의 단순 평균(`ConditionGroup.referenceFactor`)이라 기준점이 어긋나 있었다.
      그룹 폴백은 정의상 대상 등급 표본이 얇을 때만 타므로 풀이 한 등급에 쏠리는 게 기본 상황이고,
      그래서 편향이 상시적이었다 — INCOMPLETE가 USED_FAIR 체결로만 차 있으면 DAMAGED가 **+15.9%**,
      COMPLETE가 USED_GOOD으로만 차 있으면 LIKE_NEW가 **−6.0%**.
      기준계수를 같은 표본에서 같은 방식(중앙값)으로 뽑도록 `PricingService.medianFactor`로 교체하고
      `referenceFactor()`는 제거했다(잘못된 기준을 남겨 두면 다시 쓰인다).

## 프론트
- [x] 7. `entities/listing` 타입/라벨 5단계 + `ITEM_CONDITIONS`(순서 단일 정의)
- [x] 8. `create-listing-form` 등급 선택 5단계
- [x] 9. listing-grid/상세에 구성·누락·하자 표시 — `listing-card.tsx`, `listing-detail-page.tsx`
- [x] 18. `entities/pricing` `ValuationBasis` + `valuationBasisLabel/Tone`,
      밸류에이션 표에 근거 표시(`실거래 N건` / `유사 등급 N건 기준` / `추정`)
- [x] 19. `shared/lib/seo` — `damaged` → schema.org `DamagedCondition`

## 함께 고친 계약 불일치
> ⚠️ 2026-08-11 정정. 아래 20·21을 처음에 "웹앱이 소문자를 보내서 500이었다"고 적었으나
> **사실이 아니다.** `listing-api.ts`가 `condition`·`completeness`·`sort`를 전부
> `.toUpperCase()` 해서 보낸다(`listing-api.ts:31,32,74,84`). 즉 웹앱 경로는 원래 정상이었다.
> 실제 문제는 **OpenAPI 설명이 소문자 키를 규약으로 못박아 둔 것과 구현이 어긋난 것**이다.
> 문서대로 `?condition=new_sealed`를 보내는 외부/직접 호출자만 500을 맞았다.
> 이 오진을 남겨 두면 다음 사람이 "프론트가 대문자로 보내는 게 버그"라고 읽고
> `.toUpperCase()`를 걷어낼 수 있어 정정한다.

- [x] 20. **소문자 enum 쿼리 파라미터 → 문서와 구현 불일치.** 기본 컨버터가 `Enum.valueOf`라
      문서에 적힌 소문자 키를 못 받았다. `ListingWebConfig`에 `Converter<String, ItemCondition>`
      등록으로 해결. 대문자 이름·레거시 3단계 값도 함께 받는다.
- [x] 21. **소문자 enum JSON 본문.** 같은 이유. `spring.jackson.mapper.accept-case-insensitive-enums=true`로 해결.
- [x] 23. **모르는 값이 400이 아니라 500이었다.** `GlobalExceptionHandler`의 catch-all
      `@ExceptionHandler(Exception.class)`가 Spring 기본 400 매핑보다 먼저 잡는 탓에,
      `?condition=오타`가 500 + **ERROR 등급 운영 이벤트**로 나갔다(쿼리스트링 훑는 봇 하나로
      알림 채널이 막힌다). `MethodArgumentTypeMismatchException` 핸들러 추가로 400 반환.
      `ListingConditionBindingTest`가 컨버터와 예외 핸들러를 함께 세워 회귀를 막는다.
      > `sort`·`category`도 이제 오타 시 500이 아닌 400이 된다. 다만 **소문자 수용**은
      > 여전히 안 되며(`ListingSortOrder`), 웹앱은 대문자로 보내므로 영향 없다.

## 검증/배포
- [x] 10. **상태 고지 e2e.** 레거시 `used_complete` URL이 `used_good`으로 이어지는지와,
      실제 시드 매물 상세에서 5등급·구성·누락 배지·누락 상세가 함께 노출되는지 검증한다.
- [x] 11. 커밋+push, 배포, listings 재시드 — 프로덕션 응답에 고지 필드 반영 확인
- [ ] 22. 배포 후 프로덕션에서 레거시 키 재매핑 로그 확인(`[seed] pricing: ... 레거시 키 N건 재매핑`)

## 후속
- [ ] 플랫폼 검수/정품 인증
- [ ] `MIN_REAL_SAMPLES`(현재 3)·등급 감가 계수를 설정으로 외부화 — 체결이 쌓이면 실측으로 재보정
- [ ] 나머지 enum 쿼리 파라미터의 **소문자 수용**(`sort`) — 오타 400 처리는 23번으로 끝났다
- [ ] 레거시 키 조회 경로(`storageKeys()`/`storageNames()`를 쓰는 Mongo 쿼리) 통합 테스트.
      enum 단위로는 덮여 있지만 `criteria.and("condition").in(...)`과
      `findBySetNumberAndConditionIn...`은 무검증이다. `PricingServiceTest`의 인메모리 페이크는
      도메인 enum으로 필터해서 저장 키 경로를 타지 않는다.
- [x] 프론트 레거시 URL 매핑 — `parseItemCondition`에서 백엔드와 동일하게
      `used_complete → used_good`, `used_incomplete → used_fair`를 흡수한다.
