# 검증 — 2026-10-26

## 최종 선택과 전역 적용 검증

- 사용자가 `side-brick-assembled`를 최종 선택하고 전역 적용·배포를 승인했다. 8개 후보를 신규 선택에서 제외해 알려진 17개·선택 가능한 9개를 유지한다. 과거 적용과 이력은 계속 읽을 수 있다.
- 사용자 승인 후 기존 Orca API8090 터미널에서 최신 코드로 재시작했다. 실제 로컬 관리자 화면으로 사유와 확인을 입력해 조립 고래를 revision 6으로 게시했고, 공개 API·헤더·비교 화면에서 같은 적용본과 9개 후보를 확인했다.
- web 포맷·린트·타입·FSD·빌드, core 포맷·타입·플랫폼 경계, mobile 포맷·린트·타입 검사 모두 통과했다. 웹 빌드는 `.next/verify-approved-mascot`에서 수행하고 자동 생성된 tsconfig 항목만 제거했다.
- 최종 API 대상 회귀 42건(서비스 28·웹 14), 실패·스킵 0. `--rerun-tasks`로 실제 실행했다. 최종 관리자 fixture E2E는 29건, 실패·스킵·재시도 0이다.
- 다른 Codex가 `assembled-mark.json` 정본과 공용 렌더러·생성기를 맡았다. 초기 승인 프리셋과 최종 프리셋·mark.svg를 1024×600 RGBA로 비교해 변경 채널 0·최대 차이 0을 확인했다.
- 생성기 `--check`는 파일을 쓰지 않고 17개 산출물 일치와 안전 영역을 확인했다. 별도 임시 복사본의 PNG 변조는 실패로 탐지했다. Android 전경/단색 반경 30.3dp(상한 33), 스플래시 88.5dp(상한 94), 알림 여백 4px, iOS/Apple/Kakao 알파 없음.
- 실제 `/opengraph-image`는 200·1200×630 PNG다. Satori의 SVG 내부 함수 컴포넌트 오류를 재현하고 기본 SVG 요소를 전달하도록 수정했다.
- 근거: `/tmp/gole-approved-final-gates-20261026.log`, `/tmp/gole-approved-final-e2e-20261026.log`, `/tmp/gole-approved-mascot-api-tests.log`, `/tmp/gole-approved-brand-worker-20261026.md`, `/tmp/gole-approved-brand-pixels-20261026.json`.

## 배포와 확인 범위

- PR #262를 최신 변경으로 갱신한 뒤 dev → main 릴리스와 해당 main CI·CD를 거쳐 `gole.co.kr`을 확인한다. 운영 완료 SHA·검증 결과는 PR과 GoLe Obsidian의 해당 날짜 개발일지에 기록한다.
- 운영은 배포 전 revision 0·미소 고래로 확인됐다. 게시 이력이 없는 revision 0은 새 기본값을 따라간다. 저장된 별도 적용본이 발견되면 관리자 게시 경로로 변경한다.
- 기존 설치 앱의 아이콘·네이티브 스플래시·알림 이미지는 새 앱 바이너리 배포가 필요하다. 이 검증은 에셋 생성/안전 영역까지이며 실기기·스토어 배포는 포함하지 않는다. 카카오 콘솔 업로드는 수행하지 않았다.
- 사용자 WEB3000·Metro8082·관리자 탭·공유 체크아웃·미추적 `.codex/`를 보존한다. API8090도 재시작한 같은 Orca 터미널에 남긴다. 새 워크스페이스는 만들지 않았다.

## 초기 세 후보 검증

- 기존 관리자 세션의 `/admin/mascot`에서 확인했다. 자격 증명·토큰을 출력하거나 변경하지 않았다.
- 새 3후보 × 320/375/414/768/1440px의 15조합에서 가로 넘침 0, 큰 SVG의 미리보기 슬롯 이탈 0. 선택 목록 13개, 사용자 제외 4개 미노출.
- 일반 로고와 홈 히어로 6경우의 SVG가 viewBox 안에 들어온다. 밝은 헤더·어두운 푸터·16/24/48px 크기에서 형태를 확인했다.
- 스크린샷 `/tmp/gole-family-final-320.png`와 `-1440.png`, 검사 근거 `/tmp/gole-family-responsive.json`·`/tmp/gole-family-svg-bounds.json`.
- 현재 적용본은 `side-brick-soft`, revision 5 그대로다. 새 후보를 실제 사이트 설정에 게시하거나 업로드·삭제하지 않았다.

## 초기 자동 검사

- web `format:check → lint → typecheck → fsd:lint → build` 통과. 빌드는 별도 `.next/verify-mascot-family`를 사용해 사용자 dev 서버의 산출물을 보존했고 자동 추가된 tsconfig 항목만 제거했다.
- core `format:check → typecheck → check:platform`, mobile `typecheck` 통과.
- API `spotlessCheck → cleanTest test`: 1,751건 중 1,749 실행, 실패/오류 0, 기존 실제 HEIF 디코더 환경 검사 2건 스킵. `test`는 UP-TO-DATE가 아닌 실제 실행이다.
- API 대상 서비스/웹 회귀 32건 0실패·0스킵. 제외 4키의 직접 적용 400, 기존 적용/이력 보존, 새 3키의 리비전·이름 스냅숏을 확인했다.
- `cleanIntegrationTest integrationTest --tests com.gole.api.design.MascotPersistenceIntegrationTest`: 실제 Testcontainers 2건, 실패/스킵 0.
- fixture E2E: `admin-mascot` 25건 + `admin-console` 마스코트 2건, 27건 0실패·0스킵. 합성 API로 제외 현재본·이력, 서버 미지원/빈/null/알 수 없는 목록, 업로드·충돌·적용 상태를 검증했다. 실제 서버에 게시한 검증은 아니다.
