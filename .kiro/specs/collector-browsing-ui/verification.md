# 검증 근거

2026-10-24, `/Users/kscold/Desktop/GoLe`, `feat/collector-browsing-ui`.

## 구현과 재현

| 경계 | 이전 | 결과 |
| --- | --- | --- |
| 자동완성 선택 | 검색어 외 조건 소실 | 상태·카테고리·가격·정렬 유지, 키보드 선택 지원 |
| 가격 | 역전 범위·소수·지수 표기 허용 | 서버/폼 공통 검증, 이동 차단과 입력 초점 안내 |
| 적용 조건 | 모바일에서만 보임 | 모든 폭에서 확인·개별 해제·검색어를 유지한 전체 해제 |
| 늦은 검색 응답 | 닫은 자동완성이 다시 열림 | Escape/초점 이탈 후 결과가 다시 열지 않음 |
| 조회 실패 | 같은 주소 링크로 재조회 불확실 | `router.refresh()`로 같은 조건을 실제 재조회 |
| 상품 비교 | 제목·가격 높이가 다르고 판매 완료 미표시 | 식별·제목·가격 위계 고정, 거래 상태/누락 고지 |
| 비세트 상품 | 디자인 정리 중 출처 번호가 종류를 가릴 가능성 | 부품·미니피그·창작품 종류와 출처 번호를 함께 표시 |

## 테스트

최종 대상 시나리오 **45건 통과, 실패 0, 스킵 0**. 최초 테스트 입력 `sealed`를 실제 상태 키 `new_sealed`로 고쳤고, 재조회 전환 중 보존된 숨김 트리까지 찾던 검증은 접근 가능한 textbox로 수정했다. 후자는 2개 복구 시나리오를 재시도 없이 3회 반복해 6회 모두 통과했다.

| 대상 | 결과 | 환경 |
| --- | --- | --- |
| `collector-browsing.spec.ts`, `search-and-listing.spec.ts` | 27 통과 | WEB 3000/API 8090 읽기, 카탈로그 지연·실패만 브라우저 응답 재현 |
| `home.spec.ts`, `interaction-contracts.spec.ts` | 10 통과 | 공개 홈과 27개 주요 경로의 버튼·링크 계약 |
| `mobile.spec.ts`, `auth-navigation.spec.ts` 관련 항목 | 5 통과 | 모바일 필터·메뉴, 인증 화면 이동·컨트롤 |
| `search-recovery.spec.ts` | 3 통과 | 별도 WEB 3003/읽기 fixture 8093, SSR 실패→실제 재조회 및 상품 고지 |

전용 복구 테스트는 `E2E_SEARCH_FIXTURE=1`일 때만 실행한다. 일반 전체 E2E에서는 해당 3건이 명시적으로 skip되므로 숫자를 구분한다. 재현 순서:

```sh
# 각각 Orca 터미널에서 실행한다. 실제 서버 데이터는 변경하지 않는다.
node apps/web/tests-e2e/support/search-api-fixture.mjs
NEXT_DIST_DIR=.next/collector-e2e NEXT_PUBLIC_API_BASE_URL=http://127.0.0.1:8093 \
  NEXT_PUBLIC_PAYMENT_MODE=disabled pnpm --filter web exec next dev --port 3003

E2E_SEARCH_FIXTURE=1 E2E_BASE_URL=http://localhost:3003 \
  NEXT_PUBLIC_API_BASE_URL=http://127.0.0.1:8093 \
  pnpm --filter web e2e tests-e2e/search-recovery.spec.ts --project=chromium --workers=1 --retries=0
```

## 디자인 검수와 한계

- Orca 독립 탭에서 정상 목록·가격 오류·폼 수정을 확인했다. 사용자가 IME로 입력 중인 순간의 클릭은 Orca가 보호 차단했으며 우회하지 않았다. 검색 제출·복구 동작은 Playwright로 검증했다.
- 320·375·414·768·1280px: 가로 넘침 1px 이하, 기본 입력·주요 버튼 44px, 키보드 초점 2px 확인. 스크린샷은 `/tmp/gole-search-<폭>.png`에 보존했다.
- 기존 SUITE/SUIT·코발트·브릭 골드를 유지했다. 새 그라데이션·과한 모션·장식 카드를 추가하지 않고 상품 비교, 입력 상태, 문장 위계를 검수했다.
- 결제·공개 잠금은 변경하지 않았다. 실제 결제·외부 메시지·운영 데이터 쓰기·실기기 검증은 하지 않았다.
- 사용자 WEB 3000/API 8090은 보존했다. 검증용 3003/8093은 생성한 Orca 터미널만 종료했다. 공유 기본 체크아웃은 삭제 대상이 아니다.

## 품질 게이트

`format:check → lint → typecheck → fsd:lint → build` 순서 통과. 빌드는 `NEXT_DIST_DIR=.next/collector-build`, 결제 비활성으로 프리뷰 캐시와 분리했다. Next가 임시 경로를 추가한 tsconfig 변경은 원복하고 검증 캐시는 git에 포함하지 않는다.

## 코드 리뷰

`codex review`의 P2 두 건(비세트 종류 고지, 어두운 배경 초점 대비)을 수정했다. 비세트 출처 세트 고지와 밝은 폼·어두운 푸터의 초점 대비 3:1 이상을 실제 브라우저 회귀로 검증했다. 전체 중요 지적 없음으로 오기하지 않고 발견·수정 범위를 기록한다.
