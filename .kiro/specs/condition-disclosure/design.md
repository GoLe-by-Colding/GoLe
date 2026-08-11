# 상품 상태 고지 / 등급 — 설계

## 도메인 (listing 컨텍스트)

- `ItemCondition` { NEW_SEALED, LIKE_NEW, USED_GOOD, USED_FAIR, DAMAGED } — 소문자 `key()` 노출.
- `Completeness` { FULL_BOX, NO_BOX, BULK }.
- 값 객체 `ConditionDisclosure`:
  ```
  record ConditionDisclosure(
      Completeness completeness,
      boolean hasBox,
      boolean hasManual,
      boolean hasMissingParts,
      String missingPartsNote,   // hasMissingParts=true 면 필수(공백 불가)
      String defectsNote)        // nullable
  ```
  - 불변식: `hasMissingParts && (missingPartsNote 공백)` → `IllegalArgumentException`.

> **설계 변경 (2026-08-11).** 초안은 신규 enum `ItemGrade`를 만들고 `ConditionDisclosure`가
> grade를 품는 구조였다. 실제로는 **기존 `ItemCondition`을 5단계로 확장**하고 등급은
> `Listing.condition`에 그대로 두었다.
>
> 이유 — `ItemGrade`를 새로 만들면 같은 개념이 두 이름으로 존재하게 되고, `Listing`·
> `ListingDocument`·`CreateListingCommand`·`ListingResponse`·프론트 타입까지 필드명을 모두
> 갈아야 한다. 저장 필드명(`condition`)과 API 필드명(`condition`)을 유지하면 마이그레이션 표면이
> "값 5개 추가"로 줄어든다. 등급은 어차피 매물당 하나이므로 별도 값 객체로 뺄 이유도 없다.

### 레거시 값 흡수

저장된 문서를 일괄 변환하지 않고 **읽기·조회 시점에** 흡수한다.

| 경로 | 메서드 | 정책 |
|---|---|---|
| DB 읽기 | `ItemCondition.fromKey` | 관대 — 모르는 값은 `USED_GOOD`. 저장값 하나로 조회 전체가 죽으면 안 된다 |
| API 입력 | `ItemCondition.parseKey` → `Optional` | 엄격 — 모르는 값은 거부. 오타를 흡수하면 걸리지도 않은 필터를 신뢰하게 된다 |
| 검색 필터 | `ItemCondition.storageNames()` | 현재 이름 + 레거시 이름으로 `$in` 조회. 안 그러면 옛 매물이 필터에서 사라진다 |

매핑: `USED_COMPLETE`→`USED_GOOD`, `USED_INCOMPLETE`→`USED_FAIR`.

`ListingWebConfig`가 `Converter<String, ItemCondition>`를 등록한다. 기본 enum 컨버터는
`Enum.valueOf`라 대소문자를 가려서, API 규약대로 소문자 키를 보내면 변환에 실패했다.

## 영속성 (ListingDocument)

- `condition`(String, enum 이름 대문자) + 분해 필드 `completeness`, `hasBox`, `hasManual`,
  `hasMissingParts`, `missingPartsNote`, `defectsNote`.
- `ListingPersistenceAdapter`: 읽기는 `fromKey`(레거시 보정), 필터는 `storageNames()` `$in`.

## API (listing)

- `CreateListingCommand`/`ListingResponse`에 disclosure 필드 노출, `condition`은 소문자 키.
- 검증: 컨트롤러 DTO `@NotNull condition`, 서버에서 누락노트 규칙.

## 시세 집계 축 (pricing 컨텍스트)

등급을 5개로 늘리면 **등급당 체결 표본이 흩어진다.** 표본이 흩어지면 등급별 실측 중앙값을
낼 수 없어 감가 모델로 후퇴하고, 등급을 늘린 만큼 시세가 오히려 부정확해진다.

그래서 **고지 축(5등급)과 집계 축(3그룹)을 분리**한다.

```
ConditionGroup.SEALED      ← NEW_SEALED
ConditionGroup.COMPLETE    ← LIKE_NEW, USED_GOOD
ConditionGroup.INCOMPLETE  ← USED_FAIR, DAMAGED
```

그룹 경계는 3단계 시절과 일치시켜, 레거시 체결 이력이 올바른 그룹에 그대로 떨어지게 한다.

### 공정가 3단계 폴백

`PricingService.getValuation`이 근거가 강한 순으로 밟는다. 어느 단계를 썼는지는
`ValuationBasis`로 API에 노출한다(`basis` 필드).

| basis | 조건 | 산출 |
|---|---|---|
| `grade` | 해당 등급 체결 ≥ 3건 | 등급 체결가의 중앙값 |
| `group` | 등급은 미달, 그룹 체결 ≥ 3건 | `그룹중앙값 × 등급계수 / 그룹대표계수` |
| `model` | 둘 다 미달 | `미개봉시세 × 등급계수` |

- 그룹 대표계수 = 소속 등급 계수의 평균(파생값, 별도로 적어 두지 않는다).
- `SEALED`는 단독 등급이라 `group` 단계를 건너뛴다(그룹 표본 = 등급 표본).
- 등급 감가 계수: 1.00 / 0.88 / 0.78 / 0.62 / 0.45 — **실측이 쌓이기 전까지의 폴백**이지
  정답이 아니다.

조회는 `SetCondition.storageKeys()`(현재 키 + 레거시 키)로 한다.

## 프론트 (FSD)

- `entities/listing`: `ItemCondition` 5단계 + `ITEM_CONDITIONS`(순서 단일 정의) + 라벨.
- `entities/pricing`: `SetCondition` 5단계 + `ValuationBasis` + `valuationBasisLabel/Tone`.
- 밸류에이션 표에 근거를 함께 표시 — `실거래 N건` / `유사 등급 N건 기준` / `추정`.
  숫자만 보여주고 근거를 감추면 표본 1건 추정과 실거래 50건이 같아 보인다.
- `shared/lib/seo`: `damaged` → schema.org `DamagedCondition`.

## 시드

- `ListingSeeder`: 5등급 조합 반영.
- `PricingSeeder`: 등급별 밴드. `LIKE_NEW`·`DAMAGED`는 일부러 얇게(각 2건/1건) 둬서
  실제 시장을 닮게 하고 `basis=group` 경로를 로컬에서 바로 확인할 수 있게 한다.
- 기존 체결 이력은 **지우지 않는다.** 레거시 키만 새 키로 재매핑한다(문서 id 유지).
