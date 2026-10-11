# Browser Dialogs Tasks

- [x] 스펙(requirements · design · tasks)
- [x] 실패하는 E2E 먼저 — `tests-e2e/app-dialogs.spec.ts` 2건이 교체 전 실패(기본 대화상자라 모달 없음), 교체 후 통과
- [x] `shared/ui/dialog` `useDialog()` + `shared/ui/index.ts` export
- [x] 호출부 14곳 교체(문구·busy·중복 차단 보존). 회원 탈퇴 접수 안내는 세션이 지워진 뒤의 로그인 안내 화면에도 모달을 그린다
- [x] eslint `no-alert: error`
- [x] 기존 E2E의 `page.on("dialog")` 제거 → 모달 조작 + 취소 시 요청 0건 검사(정산·부품 요청·동의 철회·마스코트·탈퇴·즉시 판매)
- [x] web format · lint · typecheck · fsd 통과 (2026-10-11)
- [ ] 실제 백엔드 쓰기 E2E 2건(buy-bids 체결 흐름, part-request 세트 상세 흐름)은 `E2E_WITH_BACKEND=1` 실쓰기라 미실행
- [ ] Orca 내장 브라우저 실세션 수동 확인(관리자 정산·회원 탈퇴 모달)

## 2026-10-11 최종 검수와 회귀 수정

- 320·390px(admin-account-deletions fixture, UUID 요청 ID): 요청 ID 입력·보존 사유 라디오·파기 확인 모달 모두 페이지/모달 가로 넘침 0,
  모달 16px 여백 안(320: 16~304, 390: 16~374), 입력·취소·확인 버튼 모두 화면 안. scratch 측정(저장소 밖)으로 확인.
- [x] **회귀(수정함)**: 선택지 모달에서 기본 선택이 첫 라디오가 아닐 때(보존 사유 기본 `DISPUTE_OR_CLAIM`) 그 라디오에서 Shift+Tab 하면
  초점이 모달 밖(뒤 화면)으로 나간다. `use-dialog.tsx` Tab 가두기가 같은 이름 라디오 묶음을 개별 요소로 세어 `first`가 첫 라디오이기 때문.
  수정: `focusable`에서 같은 묶음에 체크된 라디오가 있으면 체크 안 된 라디오를 뺀다. `app-dialogs.spec.ts` 보존 사유 단계에
  Shift+Tab→확인 버튼, Tab→기본 라디오 검사를 더했다(수정 전 실패, 수정 후 통과).
- `mobile.spec.ts` 차이: 헤더 메뉴의 스크롤 잠금이 inline style → `overflow-hidden` 클래스로 바뀐 데 맞춰 computed style 로 검사.
  기존 :3000 재사용 실행 결과 16 passed · 1 failed — 실패는 "결제 대기 주문 상세"의 결제하기 버튼 부재로, 재사용한 dev 서버가
  Playwright webServer 의 `NEXT_PUBLIC_PAYMENT_MODE=stub` 이 아닌 로컬 env 로 떠 있어 결제가 닫힌 환경 차이다(대화상자·스크롤 잠금과 무관).

- 관련 7스펙 91 passed / 4 skipped, 라디오 초점 수정 후 관련 5스펙 40 passed / 1 skipped. 브라우저 기본 대화상자 호출은 없고 ESLint `no-alert`로 재유입을 막는다.
- 실제 관리자 세션의 마스코트 삭제 모달을 Orca에서 확인: 첫 초점 취소, 취소 시 에셋 유지, 확인 시 삭제, native dialog 없음. 검증 에셋 삭제 후 기본 옆모습 브릭 고래를 유지했다.

- 최종 통합 상태에서 web format:check → lint → typecheck → fsd:lint → build 순서로 모두 통과했다. 빌드는 기존 dev를 보존하도록 별도 `.next/build-verification` 출력 경로를 사용했다.
