# 상품 상태 고지 / 등급 — 구현 태스크

> 상태: **부분 구현.** 고지(구성·박스·설명서·누락·하자)는 백엔드~프론트 전 구간 동작하나,
> **등급(grade) 5단계 확장(R1.1/R1.2)은 미구현**이다. (2026-08-03 실측 감사)
> 근거: `GET /api/v1/listings` 응답에 `completeness`/`hasBox`/`hasManual`/`hasMissingParts`/
> `missingPartsNote`/`defectsNote` 존재. 반면 `condition`은 여전히 구 3단계(`new_sealed`)이며
> `ItemGrade`·`LIKE_NEW`·`USED_GOOD`·`USED_FAIR`·`DAMAGED` 심볼은 코드베이스 전체에 **0건**.

## 백엔드 (listing)
- [ ] 1. **`ItemGrade` 5단계 enum — 미구현.** `Completeness`·`ConditionDisclosure`는 구현 완료.
      현재 `ItemCondition`은 구 3단계(`NEW_SEALED`/`USED_COMPLETE`/`USED_INCOMPLETE`) 그대로다.
      R1.2가 요구하는 마이그레이션 매핑도 없다.
- [x] 2. `Listing` 도메인에 disclosure 반영(생성자/팩토리) — `Listing.java:21,35,47`, null이면 `basic()` 폴백
- [x] 3. `ListingDocument` 분해 필드 + `ListingPersistenceAdapter` 매핑(레거시 보정)
- [x] 4. `CreateListingUseCase.Command` + `ListingResponse` + 컨트롤러 DTO 확장/검증
- [x] 5. `ListingSeeder` 다양한 상태 조합
- [ ] 6. **도메인/서비스 테스트 — 미구현.** `ListingServiceTest`는 `ConditionDisclosure.basic()`을
      단순 인자 채우기로만 쓴다(5회). **누락노트 필수 불변식**
      (`ConditionDisclosure.java` — `hasMissingParts && missingPartsNote.isBlank()` → 예외)과
      1000자 상한을 검증하는 테스트가 **한 건도 없다**.

## 프론트
- [x] 7. `entities/listing` 타입/라벨 확장
- [x] 8. `create-listing-form` 구성·플래그·노트 입력(검증) — 단, 등급 선택은 구 3단계 기준
- [x] 9. listing-grid/상세에 구성·누락·하자 표시 — `listing-card.tsx`, `listing-detail-page.tsx`

## 검증/배포
- [ ] 10. **e2e 미갱신.** `tests-e2e/` 8개 스펙 어디에도 `completeness`/누락/풀박스 관련
      단언이 없다(grep 0건). `create-listing.spec.ts`/`purchase.spec.ts`가 고지 필드를 검증하지 않는다.
- [x] 11. 커밋+push, 배포, listings 재시드 — 프로덕션 응답에 고지 필드 반영 확인

## 남은 작업 (등급 확장 — SDD 순서)
- [ ] A. `requirements.md` R1.1/R1.2 재확인 후 `design.md`에 `ItemGrade` 도입 + 구 3단계 매핑 설계 확정
      (`USED_COMPLETE`→`USED_GOOD`, `USED_INCOMPLETE`→`USED_FAIR` 등 마이그레이션 규칙 명시)
- [ ] B. `ItemGrade` enum 추가 + `Listing`/`ListingDocument`/어댑터 매핑(레거시 값 보정)
- [ ] C. API DTO(`ListingRequests`/`ListingResponse`) 확장 — 구 `condition` 필드 호환 유지 여부 결정
- [ ] D. 프론트 등급 라벨/셀렉트/뱃지 5단계화
- [ ] E. 불변식 테스트(위 6번) + e2e 고지 단언(위 10번)

## 후속
- [ ] 플랫폼 검수/정품 인증
- [ ] 등급별 시세 분리
