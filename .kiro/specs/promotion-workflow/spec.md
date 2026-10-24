# 홍보 워크플로 실행 경계 개선

## 확인한 원인

- CI 완료를 받는 CD에 브랜치 필터가 없어 PR CI마다 CD가 생성되고, 배포 조건에서 스킵된 CD가 다시 홍보 워크플로를 호출한다. CD의 head_branch는 기본 브랜치 main이므로 홍보 목록의 main 표기만으로 실제 배포를 판단하면 안 된다.
- 2026-10-07 CD 연동 실행 `37578187936`과 2026-10-09 정기 실행 `37896864764`는 MinIO 다운로드의 unauthorized 오류로 중단됐다. 에이전트 호출은 실행되지 않았다.
- GHCR 읽기 권한·로그인·이미지 digest 고정 수정 `2d26915d`는 dev에 있지만 조사 시점 main에는 없다. 코드 수정과 운영 반영을 구분한다.

## 요구사항과 설계

- CD는 main에서 실행된 CI의 완료 이벤트만 받는다. 기존 push/success 조건, 호스트 준비 상태와 main 수동 실행 경계는 유지한다.
- 홍보 실행은 캡처 재사용 여부를 결정한 직후 GHCR 로그인과 MinIO·mc 이미지 다운로드를 검증한다. 설치·빌드·데모 스택은 검증 성공 후 시작한다.
- 재사용 캡처가 있으면 이미지 검증과 스택 기동을 건너뛴다.
- 로그인 실패는 `PROMOTION_REGISTRY_LOGIN_FAILED`, 이미지 다운로드 실패는 `PROMOTION_IMAGE_PULL_FAILED`로 Actions 오류와 요약에 표시한다. 토큰 값은 출력하지 않는다.
- production 시크릿·패키지 권한 변경, 실제 LLM 실행, PR·머지·배포는 이번 코드 개선 범위에 포함하지 않는다.

### 2026-10-12: runner 환경변수 반복 출력 제거

- GitHub runner는 스크립트 실행 전 job/step `env`와 앞 단계가 `GITHUB_ENV`로 추가한 값을 자동 출력한다. 실행 안에서 `add-mask`를 부르는 것으로 첫 헤더 노출을 막을 수 없다.
- 홍보 job의 공통 `env`와 `GITHUB_ENV` 쓰기를 없앤다. SHA·서비스 여부는 `GITHUB_EVENT_PATH`를 JSON으로 읽고, 단계 조건·캡처 경로는 step output으로 전달한다. 재사용 표시는 임시 파일로만 보관한다.
- 데모 API·웹·시더와 에이전트의 고정 설정은 해당 셸 실행 중에만 export한다. 레지스트리 이미지와 이벤트·재사용 설정은 `scripts/promotion-workflow-env.sh`에서 읽으며 runner 전역 환경에는 넣지 않는다.
- 운영 시크릿의 기존 자동 마스킹과 모델·포트·Discord 변수의 의미를 유지한다. 필요한 step에만 이 `env`를 남긴다. setup action이 추가한 도구 경로와 남아 있는 step env의 헤더는 runner 출력 규칙 때문에 남는다.
- checkout 이전 실패도 Summary·Discord가 처리할 수 있게 두 후처리는 저장소 스크립트에 의존하지 않고 이벤트 파일을 읽는다.
- 근거: [runner ScriptHandler](https://github.com/actions/runner/blob/main/src/Runner.Worker/Handlers/ScriptHandler.cs), [runner Handler](https://github.com/actions/runner/blob/main/src/Runner.Worker/Handlers/Handler.cs).

## 작업과 검증

- [x] CD의 main 필터와 기존 배포 게이트를 회귀 검사한다.
- [x] 실제 워크플로 셸을 가짜 Docker로 실행해 로그인 실패, 두 이미지의 개별 다운로드 실패, 정상 다운로드를 검사한다.
- [x] actionlint와 홍보 Python 테스트 80건을 실행해 통과했다. 새 회귀는 6건이며 실제 GHCR 토큰과 운영 실행은 검증하지 않았다.
- [x] `test_workflow.py` 17건 통과: 공통 env·GITHUB_ENV 제거, CD·수동 SHA·서비스 스케줄·캡처 재사용, SHA 셸 인젝션 차단과 기존 이미지 사전 검증을 확인했다.
- [x] 공식 릴리스 actionlint 1.7.12 + ShellCheck 0.11.0으로 홍보 워크플로와 source 스크립트를 검사했다. Docker engine이 꺼져 CI와 같은 digest 이미지 실행 및 실제 Actions 실행은 확인하지 않았다.
