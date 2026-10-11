# Mascot Assets Tasks

## 백엔드 (`design` 컨텍스트)

- [x] domain: `MascotPresets`(키 12개·이름), `MascotAsset`(입력 검증), `MascotSelection`(리비전 = 감사), `ActiveMascot`(공개 모양)
- [x] port/in `ManageMascotUseCase`, port/out `MascotRepositoryPort`·`MascotMediaPort`
- [x] service `MascotService` — 적용(리비전 펜스·이미 적용 중 거부), 등록(입력 검증 → 미디어 연결 → 저장, 50개 상한), 삭제(적용 중 409·이미지 회수), 공개 조회(사라진 에셋은 기본 고래)
- [x] adapter/out `MongoMascotAdapter`(`mascot_selections`·`mascot_assets`), `MediaMascotAdapter`(`MediaTargetType.MASCOT_ASSET`)
- [x] adapter/in `MascotController` — 공개 1개 + 관리자 5개, 성공 뒤 중앙 감사(`MASCOT_ASSET_CREATE`·`MASCOT_PUBLISH`·`MASCOT_ASSET_DELETE`)
- [x] 테스트: `MascotServiceTest` 10 · `MascotWebTest` 5 · `MascotPersistenceIntegrationTest` 2(Testcontainers, 동시 적용 한 건만 성공)

## 공유 코어

- [x] `@gole/core/mascot` — 프리셋 메타, `parseActiveMascot`(모르는 값·외부 URL은 기본값), 공개·관리 API, 적용 이벤트 이름

## 웹

- [x] `shared/ui/logo/presets/` — git 이력의 고래 11벌을 그때 SVG 그대로 옮기고, 지금 고래(`side-brick`)는 `mark.svg` 사본으로 둔다
- [x] `Logo` client 컴포넌트화 — 적용된 마스코트(context) 또는 `mascot` prop, `tone="inverse"`, `maxHeight`, 업로드 이미지 실패 시 기본 고래
- [x] `SiteMascot`(RootLayout, 서버 fetch 60초 캐시) + `MascotProvider`(적용 이벤트로 즉시 갱신)
- [x] 푸터를 `tone="inverse"`로, `build-brand-icons.mjs --check` 사본 위치를 `presets/side-brick.tsx`로
- [x] `/admin/mascot` 화면, NAV "마스코트", 감사 라벨 3개
- [x] 화면 동작 회귀(fixture) `tests-e2e/admin-mascot.spec.ts` — 밝은·어두운 업로드 등록, SVG·작은 이미지 거부, 적용 409 뒤 확인 재요구,
      지우기 409·404 뒤 최신 상태 재조회, 이력 끝에서 "더 보기" 숨김·지운 그림 비활성, 320·390·768·1440 가로 넘침 없음 (2026-10-11)
- [x] 실제 관리자 세션으로 업로드 → 적용 → 다른 탭 반영 → 삭제 확인 (아래 검증 기록)

## 범위 밖 (후속)

- favicon·앱 아이콘·OG·카카오 아이콘은 빌드 산출물이다 — 관리자 적용으로 바뀌지 않는다.
- RN 네이티브 화면의 고래는 연결하지 않았다.
- 미디어 조회가 `no-store`라 업로드 마스코트 이미지는 페이지를 새로 열 때마다 다시 받는다(사용자 미디어 공통 정책).

## 2026-10-11 검증

- 최신 dev `b53d9822`(가원 PR #232·#233)을 통합하고 기존 12개 고래·옆모습 기본값을 보존했다.
- 적용 409 뒤 검토 확인 초기화, 삭제 409·404 뒤 최신 목록 재조회, 마지막 이력 페이지의 더 보기 숨김을 수정했다.
- 실제 로컬 관리자 계정과 Mongo·MinIO로 밝은/어두운 이미지 업로드·등록·적용·공개 이미지 응답·이력·활성 에셋 삭제 409·오래된 리비전 적용 409를 확인했다. 기본값 복원 후 검증 에셋 삭제 204도 확인했다.
- 웹 format·lint·typecheck·fsd·별도 distDir 빌드 통과. fixture 회귀 9건 통과, 기존 관리자 메뉴/마스코트 2건 통과·실세션 가드 1건 스킵. 실제 관리자 API 401 경계는 별도 확인했다.
- 전체 백엔드 단위 1,662건 중 실패 0·스킵 2(실 HEIC 디코더), 통합 180건 중 실패 0·스킵 1(실 알림톡 발송). cleanTest/--rerun-tasks로 실행했다.
- 운영 사이트·배포 앱 아이콘·실기기 변경은 검증 범위 밖이다. main 배포는 하지 않았다.
- Orca 내장 브라우저에서 실제 관리자 로그인 → PNG 두 장 업로드 → 적용(revision 3) → 헤더·푸터 이미지 로드 완료 → 별도 홈 탭 새로고침 후 같은 공개 이미지 반영 → 옆모습 기본값 복원(revision 4) → 확인 대화상자로 검증 에셋 삭제를 확인했다. 적용 중인 기존 다른 탭의 자동 갱신은 제공하지 않으며, 새 페이지/새로고침의 서버 캐시 반영을 확인한 것이다.
