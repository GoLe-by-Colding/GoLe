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
- [x] main CI·bootstrap·CD 경로로 운영에 적용한다.

기존 main helper에서는 새 회귀 검사가 실패함. 수정 후 위 4종과 hostctl-runtime의
Docker 검사 5종 및 ShellCheck warning 게이트가 통과함. 운영 Docker에서도 선택적
variant 필드가 없는 amd64 descriptor의 추출과 manifest 일치를 확인함.

- [x] 실제 운영 이미지의 0.25 CPU import가 3초를 초과함을 재현한다.
- [x] bytecode 사전 생성과 healthcheck 실행 예산을 수정한다.
- [x] 실제 이미지의 운영 자원 제한 기동·문의 RPC·비정상 health 거부를 CI에서 검사한다.
- [x] root Compose 정책과 이전 LKG 복구 경계를 검증한다.
- [x] main CI·검토된 bootstrap·CD와 운영 소셜 로그인을 확인한다.

로컬 검증: Python 161건·0 스킵, Compose 정책 39건·0 스킵, bootstrap 정적 계약과
actionlint 통과함. 실제 이미지가 0.25 CPU·192 MiB에서 연속 healthcheck 3회와
문의 RPC 2초 제한을 통과함. CI에도 같은 실행을 추가했으며 원격 결과는 별도 확인함.

- [x] 성공한 main CI·check suite와 상태 필터 목록의 불일치를 재현한다.
- [x] 정확한 SHA 조회와 응답 필드 검증을 bootstrap·진입 명령·release verifier에 적용한다.
- [x] 상태 목록 지연 fixture와 미완료·실패·브랜치·이벤트·SHA 거부 경계를 검증한다.

기존 release verifier는 실제 성공한 `c328a7c4`를 거부하며 수정본은 같은 SHA를 통과함.
bootstrap 본문과 README 진입 명령도 HTTP fixture로 직접 실행해 동일 경계를 검증함.
관련 단위 8건, bootstrap 정적 계약 130건, ShellCheck warning·bash 구문·diff 검사 통과함.

- [x] 실제 Docker의 이중 개행과 Mongo 암묵적 configdb 볼륨을 재현한다.
- [x] JSON 기반 네트워크·마운트 검사와 경계 회귀를 검증한다.
- [x] CD 진입점도 CI 완료 목록 지연에 독립적으로 검증한다.
- [x] main CI·공식 bootstrap·full CD 후 운영 OAuth와 원장을 확인한다.

실제 고정 Mongo 이미지의 Docker inspect 계약, deployment-runtime-verifier,
secret-sync-runtime, metadata-ratchet-transaction Docker 검사 4종 통과함.
CI 조회 단위 8건(CD 본문 포함)·bootstrap 정적 계약 130건·ShellCheck warning·actionlint·diff 통과함.

## 운영 완료 근거 — 2026-09-24

- PR #159 dev 머지 `ae19e95d`·릴리스 PR #160 main `5cf0f18b`·태그 `v2026.09.24-5`를 확인함.
- 작업 PR CI #35908067172·릴리스 CI #35908930557·main CI #35909805772 모두 성공함. 각각 E2E 213 통과·10 스킵·재시도 0건임. 변경 없는 Java 테스트의 FROM-CACHE를 신규 재실행으로 세지 않음.
- 공식 root bootstrap exit 0과 설치 helper·Compose 정책·CI verifier 해시 일치를 확인함. 설치가 새 main으로 만든 깨끗한 `/app` 체크아웃은 배포 잠금 아래 실제 LKG로 맞춘 뒤 adopted runtime 검증을 통과함.
- [CD #35910838302](https://github.com/GoLe-by-Colding/GoLe/actions/runs/35910838302) 성공 후 실제 SHA `5cf0f18b`·env v9·전체 서비스 healthy·원장과 metadata marker 부재·예외 없는 호스트 검증 통과를 확인함.
- 공개 health 200 UP·www→apex 301·providers kakao/naver 및 두 제공자의 실제 운영 로그인·로그아웃·재로그인과 계정 API 200→401→200을 확인함.
- 최신 v9의 [Secret Sync #35912576700](https://github.com/GoLe-by-Colding/GoLe/actions/runs/35912576700)과 Control `배포 완료`를 확인함. main→dev 자동 동기화 `032894bb`도 반영됨.
- 네이버 일반 공개 검수 승인, Google 로그인, 유료 모델·실제 외부 발행·실결제·실기기는 이 완료 범위에 포함하지 않음.
