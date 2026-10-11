# 홍보 CoALA 설계

## 기억과 초기 지식

`promotion_guidelines`의 기존 kind/status/version/maker-checker를 재사용한다. `sourceType`은 FEEDBACK 또는 CORE_SEED, `sourceReference`는 시드 문서 근거(반려 기반은 null)다. 옛 문서는 FEEDBACK으로 읽는다. CORE_SEED만 빈 sourceFeedbackIds를 허용한다. reflectionRunKey는 seed-core-v1이며 시드 UUID는 고정이다. 업데이트/상태 전이에서 출처는 불변이다.

`POST /api/admin/promotion-memory/seed-core`는 body 없이 3개 PROPOSED KNOWLEDGE를 등록해 배열을 반환한다. 인증 actor가 제안자다. `_id` 조건의 insert-if-absent로 경합 시 중복/덮어쓰기를 방지한다. 기존 시드가 있으면 기존 상태를 반환한다. 근거는 `.kiro/steering/brand-identity.md`의 검토 버전이다.

## 단계와 작업 기억

실행 시작 context에서 같은 category의 모든 ACTIVE 지침을 가져와 동결한다. 성찰 제안은 다음 확정 이후 실행에만 적용한다. 기획은 CAPTION/SCREEN_SELECTION, 제작은 IMAGE_EDIT만 사용한다. 제작 경험은 선택한 화면 경로로 다시 조회하며 새로운 context의 지침은 사용하지 않는다. 시멘틱/절차/경험은 프롬프트에서 각각 다른 제목으로 표시한다.

실행 memoryContext에는 합집합 feedbackIds/guidelines와 `stages`를 보존한다. 각 stage는 stage(PLAN 또는 IMAGE_EDIT), routes, feedbackIds, guidelineIds를 가진다. 기존 stages 누락은 빈 배열이다. 각 단계 경험 최대 3건, 합집합 최대 6건이며 지침 수의 8개 제한은 제거한다. 이미지 제작 1회에는 최대 3장의 선택 이미지를 같은 작업 기억으로 순차 처리한다.

## 제작 절차와 검토 자료

기본 절차 ID는 royal-blue-brick-mobile, 버전은 1이다. 원본 프롬프트는 패키지의 Markdown 파일이며 모델 출력으로 절차/스킬/명령을 정하지 않는다. 스킬명 imagegen을 명시 호출한다. Pick에 headline(최대 40자/두 줄)을 추가하고 generation 시 필수다. 제작 프롬프트는 절차+고정 제약+headline+작업 기억이며 최종본을 edit에 저장한다.

캡처 설명표에 styleId/styleVersion/headline/polishFailed를 추가한다(과거 문서는 null/null/null/false). 제작 성공은 기존 originalUrl/edit 쌍을 보존하고 실패는 원본을 게시하되 polishFailed를 표시한다. 로고 재창작·화면 내 텍스트 변경·근거 없는 사실을 금지한다. 이미지 외부 headline만 새로 추가할 수 있다.

모바일 viewport는 390×844, is_mobile/has_touch=true다. manifest는 `{version:2, viewport:{width:390,height:844}, captures:[...]}`다. 워크플로의 재사용 판단도 동일 형식을 검사하고 구형 아티팩트는 새로 촬영한다.

편집 프롬프트는 Java와 동일한 UTF-16 길이로 6,000자까지 허용한다. 초과 시 fixed/style, headline, knowledge, procedure, episodes, total 길이를 detail에 기록하며 원문은 공개 run.json/로그에서 제외한다.

## 호환과 검증

이 변경의 새 응답 필드는 과거 클라이언트가 무시 가능하며 옛 문서는 기본값으로 매핑한다. 새 seed endpoint 외 기존 API 경로는 유지한다. 외부 계정/운영 데이터/실제 발행은 로컬 검증에서 사용하지 않는다. fake CLI와 실제 API E2E, Mongo 통합/동시성, Python/웹 회귀로 계약을 검증하고 실제 모델 품질은 구분해 보고한다.
