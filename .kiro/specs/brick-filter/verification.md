# 재현 가능한 검증

비용 발생 요청 없이 실행한다. 아래 SDK 테스트는 가짜 credential과 HTTP MockTransport, Java/Python 내부 HTTP 테스트는 loopback 서버만 사용한다.

```sh
UV_PROJECT_ENVIRONMENT=/tmp/gole-brick-filter-venv uv sync --project apps/support-agent --frozen
UV_PROJECT_ENVIRONMENT=/tmp/gole-brick-filter-venv bash apps/support-agent/scripts/generate-proto.sh
UV_PROJECT_ENVIRONMENT=/tmp/gole-brick-filter-venv uv run --project apps/support-agent pytest apps/support-agent/tests
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home apps/api/gradlew -p apps/api -I ../../.kiro/specs/brick-filter/validation.init.gradle spotlessCheck test --tests 'com.gole.api.brickfilter.*' integrationTest --tests 'com.gole.api.brickfilter.BrickFilterIntegrationTest'
pnpm --dir apps/web typecheck
pnpm --dir packages/core typecheck
pnpm --dir apps/web exec eslint src/views/brick-filter 'src/app/(main)/brick-filter' src/widgets/site-header/ui/site-header.tsx
pnpm --dir apps/web exec prettier --check src/views/brick-filter 'src/app/(main)/brick-filter' src/widgets/site-header/ui/site-header.tsx
pnpm --dir packages/core exec prettier --check src/brick-filter
```

- Python: 14 tests 통과(기존 support 테스트 포함). 두 모드 graph, metadata 제거, invalid input/mode/size/pixels, 실제 설치 SDK multipart 계약, provider retry 없음, empty result 거부, 내부 HTTP auth/type/result 계약.
- Java: 단위/웹/provider 계약 10개와 실제 Mongo 통합 11개, 총 21개 통과(실패/skip 0). 별도 빌드 디렉터리 `/tmp/gole-brick-filter-java` 사용. 기존 실행 서버와 다른 세션의 Gradle 출력 보존. 단위/웹/provider 계약 및 실제 Mongo Testcontainers 테스트 원본은 해당 디렉터리 `test-results`와 `reports/tests`.
- Mongo: 두 모드 12개 경쟁에서 3개 예약, 동일 키 경쟁에서 1개 예약, 성공 idempotency 및 payload conflict, IDOR, 실패 환급/원본 삭제, lease 이후 성공 거부, crash 복구, Seoul 자정, 결과 만료, 전역 동시성/분당·일별 예산 및 lease 회복, DB commit 응답 유실 회귀.
- 웹/core 타입, 수정 파일 ESLint/Prettier 통과. shared header에는 브릭 필터 NAV 1개만 추가했다.
- Orca 전용 탭 `6e9496cd-bd2e-468b-b068-d90d37296029`, `browser-check.py` 및 `browser-results.json`: 파일 업로드, 미니피겨 성공/결과 이미지, 응답 유실 후 POST 1회만 유지하고 GET 복구, 사물 브릭 실패 후 횟수 유지, CSS viewport 390×843/1441×1000 가로 넘침 없음, main landmark 1개 확인.
- `browser-check.py`는 해당 전용 탭의 현재 로그인 UI 상태를 전제로 한다. 다른 탭/운영 API를 수정하지 않고 `/api/v1/brick-filter`만 모킹하며, 실제 OpenAI 요청을 하지 않는다.

잔여 검증: 실제 provider 활성화/유료 생성 품질, 실제 사용자 세션부터 배포된 Python까지의 통합 호출은 금지 범위에 따라 미실행. HEIF 외부 decoder 실행 검증은 C의 공통 미디어 테스트 담당이며 여기서는 HEIF signature→공통 port 전달 계약과 변환된 이미지 처리를 검증한다.
