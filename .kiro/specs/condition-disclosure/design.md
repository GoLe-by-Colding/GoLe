# 상품 상태 고지 / 등급 — 설계

## 도메인 (listing 컨텍스트)

- 신규 enum `ItemGrade` { NEW_SEALED, LIKE_NEW, USED_GOOD, USED_FAIR, DAMAGED }.
- 신규 enum `Completeness` { FULL_BOX, NO_BOX, BULK }.
- 신규 값 객체 `ConditionDisclosure`:
  ```
  record ConditionDisclosure(
      ItemGrade grade,
      Completeness completeness,
      boolean hasBox,
      boolean hasManual,
      boolean hasMissingParts,
      String missingPartsNote,   // hasMissingParts=true 면 필수(공백 불가)
      String defectsNote)        // nullable
  ```
  - 불변식: `hasMissingParts && (missingPartsNote 공백)` → `IllegalArgumentException`(또는 도메인 예외).
- `Listing`: 기존 `ItemCondition condition` → `ConditionDisclosure condition` 로 교체.
  - 정적 팩토리/생성자 시그니처 갱신. 기존 `ItemCondition`은 `ItemGrade`로 대체(또는 deprecated 매핑).

## 영속성 (ListingDocument)

- `condition`(String) → 분해 필드로 저장: `grade`, `completeness`, `hasBox`, `hasManual`,
  `hasMissingParts`, `missingPartsNote`, `defectsNote`.
- `ListingPersistenceAdapter`: 매핑 추가. 읽기 시 레거시(구 condition 문자열)면 기본값 보정.

## API (listing)

- `CreateListingUseCase.CreateListingCommand`에 disclosure 필드 추가.
- `ListingResponse`에 grade/completeness/플래그/note 노출.
- 검증: 컨트롤러 DTO에 `@NotNull grade/completeness`, 서버에서 누락노트 규칙.

## 프론트 (FSD)

- `entities/listing`: 타입에 `grade`/`completeness`/플래그/note 추가 + 라벨 맵(한국어).
- `features/create-listing`: 등급/구성 셀렉트 + 박스·설명서·누락 체크박스 + 누락/하자 노트 입력(누락 시 노트 필수).
- `widgets/listing-grid`·`views/listing-detail`: 뱃지(등급/풀박스/벌크/누락) + 하자 고지 섹션.

## 시드

- `ListingSeeder`: 데모 매물에 다양한 등급/구성/누락/하자 조합 반영.

## 마이그레이션
- 읽기 호환: 문서에 `grade` 없으면 구 `condition`에서 매핑(new_sealed→NEW_SEALED 등), completeness 기본 NO_BOX.
- 재시드로 신규 스키마 적용(빈 컬렉션 조건).
