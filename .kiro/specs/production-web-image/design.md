# 운영 웹 이미지 설계

`infra/gcp/docker/web.Dockerfile`이 `pnpm install --frozen-lockfile` 전에 `patches/`를 복사한다. 필터 설치도 루트 patchedDependencies 전체를 해석하므로 Zod 한 파일만 선택하지 않는다. 최종 이미지에도 매니페스트와 패치 파일을 함께 보존한다.

격리 실행에서 기존 Corepack 준비가 root 전용 캐시에 저장돼 gole 사용자 시작 시 npm 레지스트리를 다시 찾는 문제를 재현했다. 최종 이미지의 `COREPACK_HOME=/pnpm/corepack`에 고정 pnpm을 미리 준비하고 gole에 소유권을 준다. 다운로드가 끝난 뒤 `COREPACK_ENABLE_NETWORK=0`을 설정해 실행 시 추가 다운로드에 의존하지 않게 한다.

Frontend CI에서 기존 소스 검사를 유지한 채 운영 Dockerfile을 결제 비활성 설정으로 빌드한다. 생성한 이미지를 `--network none`, CPU 0.75, 메모리 640MiB, no-new-privileges로 실행한다. 검증 프로세스는 컨테이너 안의 루프백으로 아이콘 HTTP 응답을 확인하고, 실행 UID 및 매니페스트의 패치 파일 존재를 검사한다.

런타임 검증 스크립트는 성공·실패·중단 시 자신이 생성한 컨테이너만 삭제한다. 실패 로그를 남기며 호스트 포트를 열거나 기존 Docker 볼륨을 연결하지 않는다. `.codex/`와 모든 깊이의 `playwright-report/`·`test-results/`는 빌드 컨텍스트에서 제외한다. 실제 로컬 이미지 빌드에서 테스트 결과가 Tailwind 탐색에 유입돼 잘못된 클래스 경고를 만들던 것도 함께 차단한다.

수정은 작업 PR → dev → main 릴리스와 main push CI를 거친다. 서버 파일 직접 수정이나 운영 패치 누락 우회는 하지 않는다. GCP 비용 설정은 실제 배포된 호환 SHA가 확정된 뒤 적용한다.

이미지 인자를 받는 검사는 `web-image-runtime.smoke.sh`로 두고 Frontend 잡에서만 실행한다. Infra 잡의 무인자 `*.test.sh` 자동 수집에 포함하지 않으며 ShellCheck에는 명시적으로 포함한다.
