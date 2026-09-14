# CI 계약 검사 검증

## 실패 근거

- 기존 실행: https://github.com/GoLe-by-Colding/GoLe/actions/runs/34796299992
- Infra: api.Dockerfile의 media-decoder 내부 스테이지를 미고정 외부 이미지로 오인함.
- E2E: 헤더의 /brick-filter를 unknown-internal-route로 판정함. 208 passed / 1 failed / 10 skipped.
- 문법 근거: https://docs.docker.com/reference/dockerfile/#from

## 로컬 검증

- `python -m unittest discover -s infra/gcp/tests -p test_dockerfile_pins.py`: 12건 통과, 실패·스킵 0.
- Linux Python 컨테이너에서 검사기 CLI로 운영 Dockerfile 4개 통과. 네트워크 없이 저장소 읽기 전용 마운트 사용.
- 고정 digest actionlint 이미지로 워크플로 검사 통과.
- Chromium interaction-contracts.spec.ts: 단일 테스트가 24개 화면 순회, 1 passed / 0 failed / 0 skipped, 기본 30초 제한에서 9.8초.
- 첫 cold compile 검증은 명시적 180초 제한에서 31.2초에 통과. API 없이 익명 DOM 계약만 확인함.
- 변경한 E2E 파일의 Prettier 및 git diff --check 통과. ESLint는 기존 설정에서 tests-e2e를 제외하므로 실검증으로 세지 않음.

## 로컬 한계

- Windows 체크아웃을 Linux에 직접 마운트해 bootstrap 전체 실행 시 CRLF의 pipefail 오류로 중단됨. 전체 host/policy 계약은 원격 Ubuntu CI에서 확인해야 함.
- 로컬 Next 설치 16.3.3과 선언 16.3.4 차이가 있음. 잠금 설치 및 전체 백엔드 E2E는 원격 CI에서 확인해야 함.
- 검증용 Orca WEB 서버는 종료함. Docker Desktop은 기동 상태로 유지하며 테스트 컨테이너는 --rm으로 정리됨.

## 원격 검증

- [PR #131](https://github.com/GoLe-by-Colding/GoLe/pull/131): `fix/ci-contract-validation` → `dev`, 사용자 머지 대기.
- 구현 커밋 `e930c812`의 [실행 #34850976633](https://github.com/GoLe-by-Colding/GoLe/actions/runs/34850976633): Frontend·Backend·E2E·Infra·Mobile·Support agent 6개 잡 성공.
- E2E: **209 passed / 0 failed / 10 skipped**, 4.0분. 기존 정상 기준선으로 복귀함. 10건 스킵이 있으므로 전 경로 검증으로 해석하지 않음.
- Support agent: **97 passed**, 11.45초. #130의 구조 개편을 포함하지 않는 dev 기반이므로 #130 로컬 108건과 범위가 다름.
- Infra: Dockerfile 12건·bootstrap Python 120건·budget relay 27건 통과. 이후 호스트·Secret Sync·앱 운영·ShellCheck·actionlint·Compose·Terraform 단계도 성공.
- Backend: `:test`와 `:integrationTest`가 UP-TO-DATE 없이 실제 실행된 뒤 BUILD SUCCESSFUL. 공개 잡 로그에서 총 건수는 확인되지 않아 숫자를 추정하지 않음.
- Discord CI result 잡은 PR 조건상 스킵됨. 테스트 스킵 10건과 별개임.
- 이 기록 이후 문서 커밋의 최신 체크 상태는 PR에서 확인한다. 운영 배포·실결제·실제 Threads 게시·PR 머지는 실행하지 않음.

## 재실행에서 발견한 시간 의존 테스트

- 문서 커밋 `78d04d01`의 [실행 #34851754786](https://github.com/GoLe-by-Colding/GoLe/actions/runs/34851754786)에서 Support agent가 96 passed / 1 failed로 실패함. 실제 프로세스 복구 테스트에서 자식의 0.2초 임대가 체크포인트 저장 중 만료되어 `LeaseLost`, 종료 코드 1이 발생함(기대 19).
- 부모·자식 Store에 동일한 시각을 주입하고 강제 종료 후 부모 시각만 전진하도록 수정함. 실제 spawn·os._exit·SQLite 재개와 provider 중복 실행 방지 검증을 유지하고 만료 전 재claim 불가도 확인함.
- 복구 테스트 2개 경우를 5회 반복해 10건 모두 통과함.
- 로컬 파일 전체 검사 중 timeout 테스트에서도 임대 경합으로 RUNNING이 남는 실패가 관찰됨. 이 테스트는 임대 시각만 고정하고 실제 monotonic 실행 제한은 유지함. 수정 후 test_durable_worker.py 전체 통과, 스킵 없음.
- 변경은 테스트와 스펙뿐이며 운영 Store·Runner 및 #130 브랜치는 수정하지 않음. 최종 원격 결과는 PR과 수민 일지에 기록함.
