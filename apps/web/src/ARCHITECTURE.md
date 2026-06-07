# Frontend Architecture — Feature-Sliced Design (FSD)

이 앱은 [Feature-Sliced Design](https://feature-sliced.design)을 엄격하게 적용한다.

## 레이어 (위 → 아래로만 의존)

```
app      → Next.js App Router (routing). 라우트는 얇게, view를 조합만 한다.
views    → FSD의 "pages" 레이어. 라우트별 화면 조합. (Next의 Pages Router와 혼동 방지 위해 views로 명명)
widgets  → 독립적으로 동작하는 큰 UI 블록 (헤더, 상품 그리드 등)
features → 사용자 시나리오/행동 (상품 찜하기, 셀러 팔로우 등)
entities → 비즈니스 엔티티 (lego-set, listing, user, order ...)
shared   → 재사용 가능한 기술 코드 (ui kit, api client, config, lib)
```

### 의존 규칙 (단방향)

- 상위 레이어는 하위 레이어만 import 할 수 있다. (app → views → widgets → features → entities → shared)
- 같은 레이어의 다른 슬라이스끼리 직접 import 금지 (cross-import 금지). 필요 시 상위 레이어에서 조합한다.
- 슬라이스 외부에서는 반드시 **public API(`index.ts`)** 를 통해서만 import 한다. 내부 경로 직접 참조 금지.

## 슬라이스 내부 세그먼트

```
<slice>/
├── ui/        # 컴포넌트
├── model/     # 상태, 타입, 스토어, 비즈니스 로직
├── api/       # 서버 통신
├── lib/       # 슬라이스 전용 유틸
├── config/    # 슬라이스 전용 상수
└── index.ts   # public API (외부 노출 대상만 export)
```

## 강제 도구

- **steiger** + `@feature-sliced/steiger-plugin`: FSD 구조 규칙 린팅 (`pnpm fsd:lint`)
- **eslint-plugin-boundaries**: 레이어 간 import 방향 강제 (`pnpm lint`)
- **tsconfig strict 풀세트**: `noUncheckedIndexedAccess`, `exactOptionalPropertyTypes` 등으로 덕 타이핑 차단
- **Playwright**: E2E 자동화 (`pnpm e2e`)
