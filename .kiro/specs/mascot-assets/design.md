# Mascot Assets Design

## 한눈에

```
관리자 /admin/mascot ──(업로드)──> POST /api/v1/media/images        (기존, staged)
                    ──(등록)────> POST /api/admin/mascot/assets      → mascot_assets + 미디어 공개 연결
                    ──(적용)────> POST /api/admin/mascot/publish     → mascot_selections 새 리비전(감사 포함)
                    ──(삭제)────> DELETE /api/admin/mascot/assets/{id} → 문서 삭제 + 미디어 revoke

방문자 RootLayout(서버) ──> GET /api/v1/config/mascot (Next 캐시 60초) ──> MascotProvider ──> <Logo/>
```

## 백엔드 — `design` 컨텍스트에 둔다

디자인 토큰과 같은 "사이트 외형을 관리자가 게시한다" 문제라 새 컨텍스트를 만들지 않고 `design` 안에 둔다.

| 레이어 | 클래스 | 하는 일 |
|---|---|---|
| domain/model | `MascotPresets` | 기본 제공 에셋 키 12개와 기본값(`side-brick`). 그림은 웹이 가진다 |
| domain/model | `MascotAsset` | 업로드 에셋. `register()`가 이름·설명·치수·키를 검증한다 |
| domain/model | `MascotSelection` | 적용 리비전 = 감사 기록. `initial()`은 리비전 0, 기본 고래 |
| domain/model | `ActiveMascot` | 공개 응답 모양. 프리셋이면 이미지 없음, 업로드면 경로·치수 |
| port/in | `ManageMascotUseCase` | 조회·이력·등록·적용·삭제 |
| port/out | `MascotRepositoryPort` · `MascotMediaPort` | 저장 / 미디어 공개 연결·회수 |
| service | `MascotService` | 규칙 전부. 등록·삭제는 `@Transactional`(미디어 원장과 함께 커밋) |
| adapter/out | `MongoMascotAdapter` · `MediaMascotAdapter` | `mascot_selections`·`mascot_assets` / media `ManageMediaAssetsUseCase` |
| adapter/in | `MascotController` | 공개 1개 + 관리자 5개. 감사는 admin `RecordAdminActionUseCase` |

- **리비전 펜스**: `mascot_selections._id = revision`(long). 같은 기대 리비전의 동시 적용은 unique `_id` insert로 한 건만
  성공하고 나머지는 409다(디자인 토큰과 같은 방식). 리비전 0은 DB에 쓰지 않는다.
- **이름 스냅숏**: 리비전에 에셋 이름을 함께 저장한다. 업로드 에셋을 지워도 이력에 무엇이었는지 남는다.
- **미디어**: `MediaTargetType.MASCOT_ASSET`을 더한다. 등록 때 `replaceReferences(관리자, MASCOT_ASSET, assetId, keys, public=true)`로
  staged 키를 공개 연결한다 — 키를 올린 관리자 본인만 연결할 수 있다(미디어 원장 규칙). 삭제는 `revokeTarget`.
  이미지 URL은 클라이언트가 보낸 값을 믿지 않고 키에서 `MediaKey.publicPath`로 만든다(상대 경로 `/api/v1/media/...`).
- **치수**: 업로드 응답에는 치수가 없어 화면이 `naturalWidth/Height`를 보낸다. 16~4096으로만 검증한다(관리자 입력, 비율 용도).
- **삭제와 적용의 경합**: 적용 중 에셋은 지울 수 없다. 그래도 적용과 삭제가 겹쳐 사라진 에셋을 가리키면 `active()`가 기본 고래로
  떨어진다(화면이 깨지지 않는 쪽).
- **감사**: 리비전 자체가 적용 감사다. 중앙 감사 피드에는 `MASCOT_ASSET_CREATE`·`MASCOT_PUBLISH`·`MASCOT_ASSET_DELETE`
  (`AdminTargetType.MASCOT_ASSET`)를 조치 성공 뒤 남긴다.

### API

| 메서드 | 경로 | 본문 / 응답 |
|---|---|---|
| GET | `/api/v1/config/mascot` | `{revision, asset:{id, kind:"PRESET"\|"UPLOAD", name, imageUrl?, darkImageUrl?, width?, height?}}` · `no-store` |
| GET | `/api/admin/mascot` | `{current: MascotSelection, active: ActiveMascot, presets: [key], uploads: [MascotAsset]}` |
| GET | `/api/admin/mascot/history?before=` | 리비전 25개씩 |
| POST | `/api/admin/mascot/assets` | `{name, description?, imageKey, darkImageKey?, width, height}` → `MascotAsset` (201) |
| POST | `/api/admin/mascot/publish` | `{expectedRevision, assetId, reason}` → `MascotSelection` |
| DELETE | `/api/admin/mascot/assets/{id}` | 204 · 적용 중이면 409 |

## 웹

### 그림 — `shared/ui/logo/presets/`

- 기본 제공 에셋 12벌을 **당시 SVG 그대로** 파일 하나씩 옮겼다(`git show <커밋>:apps/web/src/shared/ui/logo/logo.tsx`의 `<svg>` 내용).
  각 파일은 `MascotArt` 하나를 내보낸다 — `viewBox(spout)`와 `Art({spout})`.
- 지금 기본 고래(`side-brick`)는 `mark.svg` 정본의 다섯 경로를 그대로 쓴다. `scripts/build-brand-icons.mjs --check`가
  검사하는 사본 위치를 `logo.tsx`에서 `presets/side-brick.tsx`로 옮긴다.
- 어두운 배경(`tone="inverse"`): `side-brick`은 기존처럼 CSS 변수로 몸만 흰색이 된다. 나머지 기본 제공 에셋은 색이 SVG에
  박혀 있어 흰 실루엣 필터(`brightness(0) invert(1)`)로 그린다. 업로드 에셋은 어두운 배경용 이미지가 있으면 그것을, 없으면 원본을 쓴다.

### 적용된 에셋 전달

- `@gole/core/mascot`: 프리셋 메타(이름·설명·출처), 공개 응답 파서(`parseActiveMascot` — 모르는 값은 기본값), 조회·관리 API.
- `RootLayout`(서버)이 `fetchActiveMascot({ revalidate: 60 })`로 받아 `MascotProvider`(client context)에 넘긴다.
  `Logo`는 client 컴포넌트가 되어 context를 읽는다. `mascot` prop을 주면 그 에셋을 그린다(관리자 미리보기).
- 관리자 화면이 적용에 성공하면 `gole:mascot-published` 이벤트를 쏘고 Provider가 `no-store`로 다시 받는다.
- 업로드 이미지는 `thumbnailUrl`로 표시 폭의 2배에 맞는 썸네일(240/480/960)을 받는다. 로드에 실패하면 기본 고래를 그린다.
- `global-error.tsx`는 RootLayout 밖이라 Provider가 없다 → context 기본값(기본 고래).

### 관리자 화면 `/admin/mascot`

- 위: 지금 적용된 마스코트(헤더 32px·푸터 남색·히어로 크기).
- 에셋 목록: 기본 제공 12 + 업로드. 카드를 누르면 아래 미리보기 패널이 그 에셋을 헤더·푸터·히어로·작은 크기로 보여 준다.
- 적용: 사유 + "검토했습니다" 체크 + 적용 버튼. 충돌(409)이면 고른 에셋을 유지하고 다시 불러오기를 안내한다.
- 업로드: 이름·설명·이미지·어두운 배경용 이미지(선택). 파일을 고르면 바로 미리 보고 치수를 읽는다.
- 이력: 리비전·에셋 이름·사유·시각, "다시 적용"(지운 에셋은 비활성).
- NAV에 "마스코트"를 디자인 토큰 다음에 둔다. 감사 라벨 3개를 더한다.
