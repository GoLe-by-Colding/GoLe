# Design System — Requirements

> GoLe의 UI 일관성·브랜드 정체성·접근성을 보장하는 디자인 시스템. 단일 출처는
> `apps/web/src/app/globals.css`의 Tailwind v4 `@theme` 토큰이며, 재사용 컴포넌트는
> `apps/web/src/shared/ui`에 둔다. 브랜드 컨셉은 `.kiro/steering/brand-identity.md`.

## 요구사항 (EARS)

### D1 — 토큰 단일 출처
- D1.1 모든 색/타이포/라운드/그림자는 `globals.css @theme` 토큰으로 정의하고, 컴포넌트는 토큰 유틸리티(`bg-brand-500`, `rounded-xl`, `shadow-soft` 등)만 사용해야 한다.
- D1.2 브랜드 컬러 교체는 `--color-brand-*` 값만 변경하면 전체가 일관 리컬러되어야 한다.
- D1.3 임의 hex 사용은 외부 브랜드(소셜 로그인 버튼 등) 등 불가피한 경우로 제한해야 한다.

### D2 — 브랜드 적용 원칙 (미니멀)
- D2.1 Primary 액션은 솔리드 `brand-500`(그라데이션 금지), 보조는 흰 배경 + 얇은 보더(secondary).
- D2.2 Accent(Brick Yellow)는 커뮤니티/자랑/MOC/뱃지 등 "놀이" 포인트에 화면당 1~2곳만 절제 사용해야 한다.
- D2.3 배경은 흰색 기반, 여백 중심. 컬러 그라데이션 배경·큰 글로우/네온 금지.
- D2.4 카드는 얇은 보더(`border-neutral-200/70`) + `rounded-xl` + `shadow-soft`(호버 `shadow-lift`).

### D3 — 컴포넌트(UI Kit)
- D3.1 재사용 UI는 `shared/ui`에 두고 슬라이스 `index.ts` 공개 API로만 노출해야 한다.
- D3.2 모든 인터랙티브 컴포넌트는 hover/disabled/focus 상태와 변형(variant/size)을 일관 제공해야 한다.

### D4 — 접근성
- D4.1 폼 입력은 `Field`로 label·hint·에러를 `aria-describedby`로 연결해야 한다.
- D4.2 아이콘 전용 버튼은 `aria-label`을 제공해야 한다.
- D4.3 색만으로 의미를 전달하지 않고(텍스트/아이콘 병행), 본문 텍스트에 Accent 옐로를 사용하지 않는다(대비).
- D4.4 포커스 가시성(브랜드 포커스 링)을 유지해야 한다.

### D5 — 반응형
- D5.1 레이아웃은 모바일 우선. 헤더는 `max-sm`에서 햄버거 메뉴로 전환해야 한다.
- D5.2 그리드는 `auto-fill minmax`로 자연스러운 열 수 조정을 해야 한다.
