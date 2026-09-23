# 작업 목록

- [x] legacy Nginx healthcheck 부재를 재현하고 모드별 검증을 수정한다.
- [x] 기존 전송 설정 검증과 strict 거부 경계를 확인한다.
- [x] 관련 Docker runtime 회귀 검사를 실행한다.
- [x] 운영 장애 시각·복구·남은 적용 조건을 팀 볼트에 기록한다.

검증: deployment-rollback-mode-runtime, legacy-adopted-transport-runtime,
deployment-mutation-cleanup-runtime, deployment-runtime-verifier의 Docker 검사 통과함.
강제 SIGKILL 출력은 중단 복구 검사의 의도된 장애 주입임.

- [x] 누락된 legacy image ID와 보존된 플랫폼 manifest를 fixture로 재현한다.
- [x] manifest 일치 시 스냅샷과 재기동 없는 복구를 검증한다.
- [x] manifest 불일치·부재·잘못된 플랫폼과 strict fallback을 거부하는지 확인한다.
- [x] 실제 운영 descriptor와 불변 이미지의 플랫폼 manifest를 읽기 전용으로 대조한다.
- [ ] main CI·bootstrap·CD 경로로 운영에 적용한다.

기존 main helper에서는 새 회귀 검사가 실패함. 수정 후 위 4종과 hostctl-runtime의
Docker 검사 5종 및 ShellCheck warning 게이트가 통과함. 운영 Docker에서도 선택적
variant 필드가 없는 amd64 descriptor의 추출과 manifest 일치를 확인함.

- [x] 실제 운영 이미지의 0.25 CPU import가 3초를 초과함을 재현한다.
- [x] bytecode 사전 생성과 healthcheck 실행 예산을 수정한다.
- [x] 실제 이미지의 운영 자원 제한 기동·문의 RPC·비정상 health 거부를 CI에서 검사한다.
- [x] root Compose 정책과 이전 LKG 복구 경계를 검증한다.
- [ ] main CI·검토된 bootstrap·CD와 운영 소셜 로그인을 확인한다.

로컬 검증: Python 161건·0 스킵, Compose 정책 39건·0 스킵, bootstrap 정적 계약과
actionlint 통과함. 실제 이미지가 0.25 CPU·192 MiB에서 연속 healthcheck 3회와
문의 RPC 2초 제한을 통과함. CI에도 같은 실행을 추가했으며 원격 결과는 별도 확인함.

- [x] 성공한 main CI·check suite와 상태 필터 목록의 불일치를 재현한다.
- [x] 정확한 SHA 조회와 응답 필드 검증을 bootstrap·진입 명령·release verifier에 적용한다.
- [x] 상태 목록 지연 fixture와 미완료·실패·브랜치·이벤트·SHA 거부 경계를 검증한다.

기존 release verifier는 실제 성공한 `c328a7c4`를 거부하며 수정본은 같은 SHA를 통과함.
bootstrap 본문과 README 진입 명령도 HTTP fixture로 직접 실행해 동일 경계를 검증함.
관련 단위 8건, bootstrap 정적 계약 130건, ShellCheck warning·bash 구문·diff 검사 통과함.
