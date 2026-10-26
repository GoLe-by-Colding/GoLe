# 검증 — 2026-10-26

## 실제 Orca 로컬 화면

- 기존 관리자 세션의 `/admin/mascot`에서 확인했다. 자격 증명·토큰을 출력하거나 변경하지 않았다.
- 새 3후보 × 320/375/414/768/1440px의 15조합에서 가로 넘침 0, 큰 SVG의 미리보기 슬롯 이탈 0. 선택 목록 13개, 사용자 제외 4개 미노출.
- 일반 로고와 홈 히어로 6경우의 SVG가 viewBox 안에 들어온다. 밝은 헤더·어두운 푸터·16/24/48px 크기에서 형태를 확인했다.
- 스크린샷 `/tmp/gole-family-final-320.png`와 `-1440.png`, 검사 근거 `/tmp/gole-family-responsive.json`·`/tmp/gole-family-svg-bounds.json`.
- 현재 적용본은 `side-brick-soft`, revision 5 그대로다. 새 후보를 실제 사이트 설정에 게시하거나 업로드·삭제하지 않았다.

## 자동 검사

- web `format:check → lint → typecheck → fsd:lint → build` 통과. 빌드는 별도 `.next/verify-mascot-family`를 사용해 사용자 dev 서버의 산출물을 보존했고 자동 추가된 tsconfig 항목만 제거했다.
- core `format:check → typecheck → check:platform`, mobile `typecheck` 통과.
- API `spotlessCheck → cleanTest test`: 1,751건 중 1,749 실행, 실패/오류 0, 기존 실제 HEIF 디코더 환경 검사 2건 스킵. `test`는 UP-TO-DATE가 아닌 실제 실행이다.
- API 대상 서비스/웹 회귀 32건 0실패·0스킵. 제외 4키의 직접 적용 400, 기존 적용/이력 보존, 새 3키의 리비전·이름 스냅숏을 확인했다.
- `cleanIntegrationTest integrationTest --tests com.gole.api.design.MascotPersistenceIntegrationTest`: 실제 Testcontainers 2건, 실패/스킵 0.
- fixture E2E: `admin-mascot` 25건 + `admin-console` 마스코트 2건, 27건 0실패·0스킵. 합성 API로 제외 현재본·이력, 서버 미지원/빈/null/알 수 없는 목록, 업로드·충돌·적용 상태를 검증했다. 실제 서버에 게시한 검증은 아니다.

## 범위와 남은 확인

- 기존 API8090은 이전 세션에서 실행한 구버전이다. 새 키가 없는 동안 UI는 비교를 허용하되 적용을 막는다. 최신 코드로 재시작하려면 해당 사용자 프로세스의 재시작 확인이 필요하며 요청했다.
- 이번 변경은 새 후보와 선택 제외 정책이다. 운영 설정·main 릴리스·앱 아이콘·favicon·OG는 바꾸지 않는다. 실제 운영 게시·실기기 렌더링은 확인하지 않았다.
- 원격 CI·dev 병합·코드 리뷰 결과는 완료 후 추가한다.
