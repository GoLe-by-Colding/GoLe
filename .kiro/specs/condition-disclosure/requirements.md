# 상품 상태 고지 / 등급 (Condition Disclosure) — 요구사항

## 개요

중고 레고는 상태 편차가 크다. 판매자가 **등급·구성·하자·누락부품을 명확히 고지**하고
구매자가 구매 전에 확인할 수 있어야 한다. 분쟁/반품을 줄이는 핵심 신뢰 기능.

## 요구사항

### R1. 상태 등급(grade)
- **R1.1** 매물은 다음 등급 중 하나를 가진다:
  - `NEW_SEALED` 새상품(미개봉)
  - `LIKE_NEW` 거의 새것(개봉/전시)
  - `USED_GOOD` 중고-양호
  - `USED_FAIR` 중고-사용감 있음
  - `DAMAGED` 하자/손상 있음
- **R1.2** 기존 3단계(new_sealed/used_complete/used_incomplete)에서 확장. 마이그레이션 매핑 제공.

### R2. 구성(completeness)
- **R2.1** 구성 유형: `FULL_BOX`(풀박스: 박스+설명서+부품 완비), `NO_BOX`(박스 없음/부품·설명서 위주), `BULK`(벌크: 부품만).
- **R2.2** 박스 유무(`hasBox`), 설명서 유무(`hasManual`)를 별도 플래그로 고지.

### R3. 누락 부품(missing parts)
- **R3.1** 누락 부품 여부(`hasMissingParts`)를 명시한다.
- **R3.2** 누락이 있으면 상세 설명(`missingPartsNote`)을 입력한다(누락 시 필수).

### R4. 하자/손상 고지
- **R4.1** 뭉개짐·변색·파손 등 하자 설명(`defectsNote`, 선택)을 자유 텍스트로 고지.

### R5. 표시/확인
- **R5.1** 매물 카드/상세에서 등급·구성·누락·하자를 뱃지/텍스트로 명확히 표시.
- **R5.2** 등록 폼에서 위 항목을 입력받고, 누락 있음인데 설명이 비면 거부(검증).

## 비범위
- 플랫폼 검수/등급 인증(KREAM식 정품검수) — 후속.
- 등급별 시세 분리 — 후속.

## 마이그레이션
- 기존 `condition` → 신규 `grade` 매핑: new_sealed→NEW_SEALED, used_complete→USED_GOOD, used_incomplete→USED_FAIR.
- 기존 매물은 completeness 기본값(NO_BOX), hasMissingParts=false 로 보정(읽기 시 기본).
