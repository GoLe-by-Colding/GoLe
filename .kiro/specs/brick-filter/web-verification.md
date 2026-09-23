# 웹 브릭 필터·이미지 업로드 검증 보고

- 작성: 2026-09-13 UTC
- 작업: `task_4c86f48d0625` / `ctx_c035e1a3387a`
- 범위: 기존 dirty 웹/core 흐름의 최소 보완. Java/Python/media·빌드 설정·환경 설정·lock 파일은 수정하지 않았다.

## 구현 결과

1. `packages/core/src/brick-filter/index.ts`: 인증 쿠키/기존 bearer를 유지한 전용 request 경계, GET 15초/POST 180초 deadline, 모든 응답 no-store, 비 JSON/null/빈 오류의 한국어 조치 안내, 이미지 아닌 결과 응답 거부. 지연 401에서 공유 세션을 직접 삭제하지 않는다.
2. `apps/web/src/views/brick-filter/ui/brick-filter-page.tsx`: 자동 polling 직렬화, cleanup 뒤 응답 폐기, 자동·수동 늦은 RESERVED 응답의 종료 상태 역행 차단, 503/timeout의 기존 멱등키 보존과 404 후 동일 payload 재전송, 생성 결과와 quota 재조회 오류 분리, 401 및 오래된 quota refresh 차단, 잘못된 파일 선택 시 이전 사진/동의 제거, 이용 상태 live region.
3. 상품/게시글 업로드 폼: ref 잠금으로 같은 렌더 안의 중복 업로드·등록 및 업로드 도중 Enter 제출 차단. 실패 후 잠금 해제, 성공 후 화면 전환 전까지 제출 잠금 유지. 업로드 중 role=status 안내. 기존 HEIC/HEIF accept·설명 변경은 보존했다.

## 실제 실행한 검증

| 명령 | 결과 |
|---|---|
| `pnpm --filter web format:check` | 통과 |
| `pnpm --filter web lint` | 통과, 경고 0 |
| `pnpm --filter web typecheck` | 통과 |
| `pnpm --filter web fsd:lint` | 통과 |
| `pnpm --filter @gole/core typecheck` | 통과 |
| `pnpm --filter @gole/core exec prettier --check src/brick-filter/index.ts` | 통과 |
| `node .kiro/specs/brick-filter/web-core-tests.cjs` | 7개 통과, `web-core-results.json` |
| `node .kiro/specs/brick-filter/web-form-tests.cjs` | 4개 통과, `web-form-results.json` |
| `python3 .kiro/specs/brick-filter/web-browser-check.py` | 12개 시나리오 통과, `web-browser-results.json` |
| 소유 tracked TSX `git diff --check` | 통과 |

core 테스트는 실제 TS 모듈을 transpile해 fake fetch로 실행한다. 쿠키·헤더·멱등키·폼 데이터·오류·결과 타입을 검증하며, deadline 값은 15,000/180,000ms를 확인하고 테스트에서는 1ms로 단축해 실제 AbortSignal 중단 전파를 검증했다.

폼 테스트는 실제 TSX 핸들러를 메모리 hooks/JSX harness로 실행한다. DOM/브라우저 업로드나 실제 media 저장소 테스트라고 주장하지 않는다. 업로드 중 Enter, 동일 렌더 중복 파일 이벤트, 중복 등록, 실패 후 재시도, 성공 후 잠금 유지가 대상이다.

브라우저는 기존 web3000의 실제 Next/React 화면을 Orca 전용 테스트 탭에서 조작했다. 브릭 API는 해당 탭의 window.fetch만 fake로 교체했고, 세션은 해당 탭의 Storage.getItem 결과만 메모리 fake로 교체했다. 실제 localStorage·쿠키는 쓰지 않았고 모든 API 요청을 fake 경계에서 차단했다. 실제 Java quota/owner 인증이나 OpenAI 생성 검증은 아니다. 완료 후 원래 fetch/getItem을 복원하고 로그아웃 화면을 확인했으며 본인이 만든 테스트 탭만 닫았다.

중간 브라우저 재실행에서 `runtime_unavailable`이 발생했다. 서버/Orca를 재시작하지 않고 연결 복구 후 본인 테스트 탭만 reload했으며, 테스트용 세션 스냅샷을 상수로 고정한 뒤 최종 스크립트를 재실행했다. 오류를 통과로 치환하지 않았고 최종 JSON에는 성공한 실행만 기록한다.

## 독립적으로 묶을 정확한 파일과 의존성

### 이번 작업에서 편집한 제품 소스 4개

- `packages/core/src/brick-filter/index.ts`
- `apps/web/src/views/brick-filter/ui/brick-filter-page.tsx`
- `apps/web/src/features/create-listing/ui/create-listing-form.tsx`
- `apps/web/src/features/create-post/ui/create-post-form.tsx`

### A: 브릭 필터 기능 묶음

- `packages/core/src/brick-filter/index.ts`
- `apps/web/src/views/brick-filter/index.ts`
- `apps/web/src/views/brick-filter/ui/brick-filter-page.tsx`
- `apps/web/src/app/(main)/brick-filter/page.tsx`
- `apps/web/src/widgets/site-header/ui/site-header.tsx`

view index·route·header는 작업 시작 전부터 존재한 사용자 변경이며 이 작업에서 편집하지 않았다. 신규 기능을 독립적으로 반영할 때 함께 포함해야 import/진입 경로가 완성된다. 기존 core wildcard exports를 사용하므로 package/build/lock 변경은 필요 없다. 런타임은 Java `/api/v1/brick-filter/quota`, `/jobs`, `/jobs/{id}`, `/jobs/{id}/result`와 인증·멱등키·quota 응답 계약에 의존한다. Java/Python 구현은 coordinator 담당이며 이 목록에 포함하지 않는다.

### B: 상품·게시글 업로드 폼 묶음

- `apps/web/src/features/create-listing/ui/create-listing-form.tsx`
- `apps/web/src/features/create-post/ui/create-post-form.tsx`

동시성 보완 자체는 기존 `uploadImages`·등록 API만 사용한다. 두 파일에는 작업 전부터 HEIC/HEIF 허용 문구와 accept 변경이 함께 있으므로 전체 파일을 반영하면 media 측 HEIF 정규화 구현도 필요하다. 사용자 변경을 제거하거나 부분 stage하지 않았다.

### C: 검증/설계 증거 8개

- `.kiro/specs/brick-filter/web-hardening.md`
- `.kiro/specs/brick-filter/web-verification.md`
- `.kiro/specs/brick-filter/web-core-tests.cjs`
- `.kiro/specs/brick-filter/web-core-results.json`
- `.kiro/specs/brick-filter/web-form-tests.cjs`
- `.kiro/specs/brick-filter/web-form-results.json`
- `.kiro/specs/brick-filter/web-browser-check.py`
- `.kiro/specs/brick-filter/web-browser-results.json`

Node 테스트는 저장소에 이미 설치된 TypeScript를 사용한다. 브라우저 재현에는 실행 중인 web과 Orca가 필요하고 `ORCA_BRICK_TEST_PAGE` 환경 변수에 별도로 만든 로그아웃 검증 탭 ID를 지정해야 한다. fixture는 `/tmp/gole-web-brick-test.png`와 `.txt`만 사용하며 커밋 대상이 아니다.

## Coordinator 후속 검증

- 결과 계약을 정확한 `image/png` MIME·PNG signature·수신 스트림 8MiB 상한으로 좁혔다. SVG/위조 PNG/빈 응답/Content-Length와 실제 스트림 초과 거절을 추가하여 core 계약은 현재 8건이다. 서버의 실제 이미지 디코딩을 브라우저가 대체하는 것은 아니다.
- `NEXT_DIST_DIR=build/agent-hour-next pnpm --filter web build` 통과. 개발 캐시와 분리했으며 자동 추가된 tsconfig include만 제거했다.
- main 별도 Orca 탭에서 비회원 업로드 입력 없음·로그인 returnTo 연결을 실제 확인했다. viewport 375 명령 receipt와 달리 실제 innerWidth는 648이었으므로 375px 검증 완료로 기록하지 않는다. 스크린리더와 실기기 확인은 남는다.
- PNG 제한 추가 후 main이 동일 스크립트를 두 번 재실행했으나 첫 상태 대기와 복원 eval에서 `runtime_unavailable`로 실패했다. 위 12개 성공 JSON은 worker가 최종 PNG 제한 추가 전에 수행한 결과이며 main 재실행 성공으로 해석하지 않는다. main 테스트 탭을 닫아 메모리 fake를 제거했고 사용자 탭/쿠키/localStorage는 변경하지 않았다. 최종 PNG 경계는 core 8건과 정적 게이트로 검증했으며 변경 후 브라우저 재검증은 남는다.

## 미구현·잔여 위험

- 실제 유료 생성, 실제 API8090 세션/원장·quota 경합, HEIF 바이트 디코딩, 업로드 저장소·등록 E2E는 이 작업에서 실행하지 않았다. 해당 검증은 backend 담당 결과와 별도로 합쳐야 한다.
- 불확실한 요청 id/file은 현재 Editor 수명 안에서만 보존된다. 새로고침·탭 종료 후 동일 키와 원본 파일을 복구하는 브라우저 영속 계층은 추가하지 않았다. 서버 작업 목록·quota가 최종 기준이다.
- 자동 polling은 한 번씩 실행되지만 명시적 수동 조회와 자동 조회는 겹칠 수 있다. 그 경우 종료 상태 역행을 방지한다. 컴포넌트 해제 시 진행 중 fetch를 즉시 abort하지는 않으며 최대 15초 deadline과 응답 폐기로 제한한다.
- 오류/업로드 상태의 semantic live region은 확인했으나 실제 스크린리더·모바일 실기기 검증은 하지 않았다.
- 빌드 실행은 이번 acceptance 범위에 없고 실행 중인 web의 `.next`를 건드리지 않기 위해 수행하지 않았다. 프런트 설정·서버 프로세스·8090/3000/8081 상태를 변경하지 않았고 설치·commit·push·branch 변경·배포·유료 API 호출은 없었다.
