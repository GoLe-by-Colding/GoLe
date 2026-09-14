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

- 별도 dev 대상 PR의 결과를 확인한 후 기록한다. 아직 전체 CI 통과로 판단하지 않는다.
