# CI 계약 검사 개선 설계

- 표준 라이브러리만 사용하는 Dockerfile FROM 검사기를 infra/gcp/tests에 둔다. 파일 순서대로 앞에서 선언한 스테이지만 내부 참조로 인정한다.
- FROM/AS 대소문자, 플랫폼 옵션, 행 연결을 처리하고 외부 이미지는 기존 SHA-256 형식을 요구한다. 해석할 수 없는 FROM은 실패시킨다.
- 현재 운영 Dockerfile이 쓰지 않는 heredoc과 비기본 escape 지시자는 지원 전까지 명시적으로 거절한다. heredoc 본문의 가짜 FROM이 내부 스테이지로 등록되는 우회를 막는다. 전체 Dockerfile 문법 검증기는 아니다.
- bootstrap-contract의 기존 Dockerfile 목록은 유지하고 검사기에 전달한다. 별도 unittest로 정상 내부 참조와 외부 이미지 누락·잘못된 pin을 회귀 검증한다.
- CI에서는 이 unittest, 정적 bootstrap 검사, 호스트 계약, Secret Sync 계약을 이름별 단계로 분리한다. 기존 검사를 제거하거나 continue-on-error로 우회하지 않는다.
- E2E는 실제 존재하는 /brick-filter를 허용하고 화면 검사 대상으로 추가한다. 단일 테스트는 유지하며 화면별 step과 soft assertion으로 진단을 모은다.
- actionlint 고정 이미지, bootstrap 정적 검사, 검사기 unittest, 관련 Playwright 테스트로 검증한다. 로컬 환경 제한은 원격 CI 결과와 구분한다.
