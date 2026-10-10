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
- [ ] 실제 관리자 세션으로 업로드 → 적용 → 다른 탭 반영 → 삭제 확인 (아래 검증 기록)

## 범위 밖 (후속)

- favicon·앱 아이콘·OG·카카오 아이콘은 빌드 산출물이다 — 관리자 적용으로 바뀌지 않는다.
- RN 네이티브 화면의 고래는 연결하지 않았다.
- 미디어 조회가 `no-store`라 업로드 마스코트 이미지는 페이지를 새로 열 때마다 다시 받는다(사용자 미디어 공통 정책).
