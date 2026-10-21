# 설계

## 통합

`fix/complete-service-flows`는 dev에서 시작한다. PR #244·#245는 머지 커밋으로 가져와 팀원 커밋을 보존한다. 홈/검색의 새로운 공통 레이아웃과 통계의 정확한 의미를 함께 유지하고, 전화 인증의 발송 번호 대조와 중복 요청·번호 수정·판매자 복귀 처리를 함께 적용한다.

## 병렬 소유권

동일한 환경을 갖춘 단일 체크아웃의 Orca Codex 터미널 세 개가 경로를 나눈다. 조정자는 Git·설치·서버·최종 게이트·통합 문서만 맡고, 다른 파일 수정은 담당자와 메시지로 조율한다. 개별 개선의 요구사항과 근거는 `flow-completion-parts-community`, `flow-completion-trading`, `flow-completion-account` 스펙에서 관리한다.

## 검증 환경

- 사용자 프리뷰는 WEB 3000 / API 8090 / Metro 8082이며 기존 사용자 데이터를 보존한다.
- 쓰기 E2E는 WEB 3002 / API 8080 / Mongo `gole_e2e` / Redis DB 15 / MinIO `gole-e2e`에서만 실행한다.
- 실제 로컬 API 왕복과 브라우저 고정 응답 검증을 구분한다. 외부 실발송·실승인을 수행하지 않는다.
- 수동 화면 검수는 Orca 내장 브라우저의 담당자별 탭을 사용한다. 자동 회귀는 저장소 E2E와 백엔드 단위/통합 테스트를 사용한다.
- 컴파일 중인 백엔드 클래스를 실행 서버가 읽지 않도록 서버 재기동과 Gradle 게이트를 조정자가 순서대로 수행한다.

## 관리자 결함 수정

기존 `CatalogService.create`는 갱신 가능한 `save`를 호출해 동일 번호를 덮어썼다. 신규 등록용 포트 `insert`를 분리하고 Mongo `_id` 유일성으로 경쟁 요청을 제어한다. 어댑터는 중복 키를 도메인 `LEGO_SET_ALREADY_EXISTS` 충돌로 변환한다. 선조회만으로 중복을 막지 않는다.

출시 준비 확인 취소는 서버의 기존 Stage 1 안전 잠금 정책을 바꾸지 않는다. UI에서 모달을 먼저 확인하고 단일 진행 잠금 아래 기존 API를 호출한다. 실제 운영 설정은 이번 검증에서 변경하지 않는다.

알림 설정은 읽은 뒤 배열 전체를 저장하면 다른 분류의 변경도 유실된다. 도메인에서 변경 요청을 검증한 뒤 저장 포트가 현재 배열에서 켜는 분류를 빼고 끄는 분류를 합치는 한 번의 Mongo 갱신을 수행한다. [MongoDB의 update pipeline](https://www.mongodb.com/docs/manual/reference/method/db.collection.findoneandupdate/)을 사용하며 신규 계정의 동시 upsert 충돌은 기존 문서 갱신으로 재시도한다. 저장 스키마와 기본 수신 정책은 유지한다.
