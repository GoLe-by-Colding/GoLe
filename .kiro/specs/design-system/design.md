# Design System — Design

## 1. 토큰 (단일 출처: `apps/web/src/app/globals.css` `@theme`)

| 그룹 | 토큰 | 핵심 값 | 용도 |
|---|---|---|---|
| Brand (GoLe Cobalt) | `brand-50…900` | `brand-500 #2f56e6` | 기본 버튼/링크/포커스/식별. 신뢰의 코발트. |
| Accent (Brick Yellow) | `accent-50…700` | `accent-500 #fbb500` | 커뮤니티·자랑·MOC·뱃지 포인트(절제). |
| Neutral | `neutral-50…900` | — | 텍스트/보더/배경(Tailwind 기본 neutral 재정의). |
| Semantic | `success/danger/warning/info` (+`*-soft`) | — | 상태 표현 전용. 브랜드와 혼동 금지. |
| Typography | `--font-sans`(Pretendard/Noto Sans KR 우선), `--font-mono` | — | 한글 친화 시스템 폰트 스택. |
| Radius | `sm 6 / md 10 / lg 16 / xl 24` | — | 카드·버튼·입력. 카드 기본 `rounded-xl`. |
| Elevation | `shadow-soft`, `shadow-lift` | 옅은 깊이 | 기본/호버. 큰 그림자 금지. |

- 베이스: `html` 흰 배경 + neutral-900 텍스트, `::selection` brand-200, 헤딩 `line-height 1.2 / letter-spacing -0.01em`.

## 2. UI Kit (`apps/web/src/shared/ui`)

| 컴포넌트 | 변형/주요 props | 비고 |
|---|---|---|
| `Button` | variant: primary/secondary/ghost, size: sm/md/lg, fullWidth, disabled | primary=solid brand-500 |
| `LinkButton` | Button 스타일의 `next/link` | 네비게이션용 |
| `Card` | elevation | 얇은 보더 + rounded-xl + shadow-soft |
| `Badge` | tone: neutral/brand/success/danger/warning | ring-inset 미세 보더 |
| `Input` / `Textarea` / `Select` | 표준 폼 컨트롤 | `Field`와 함께 사용 |
| `Field` | label, hint, render-prop(`inputId`, `describedBy`) | 접근성 연결 자동화 |
| `Container` | width: sm/md/lg/xl | 페이지 폭 일관 |
| `Heading` / `Text` | level, size, tone, weight | 타이포 스케일 |

- 공개 규칙: 외부에서는 `@shared/ui` 배럴만 import. 내부 경로 deep import 금지(steiger/boundaries).

## 3. 컴포지션 패턴
- **폼**: `Field` 안에 `Input/Textarea/Select`를 render-prop으로 배치 → label/hint/aria 자동 연결. 제출 중 버튼 `disabled`, 에러는 `bg-danger-soft` 배너(role="alert").
- **카드 그리드**: `[grid-template-columns:repeat(auto-fill,minmax(220px,1fr))]`로 반응형.
- **헤더**: 데스크톱 인라인 네비 + 우측 액션, `max-sm`에서 햄버거 패널. 로그인 시 아바타(→`/profile`)·판매하기·로그아웃.
- **랭킹/리스트**: `divide-y` + 얇은 보더 카드(예: `widgets/trending-sets`).

## 4. 외부 브랜드 예외
- 소셜 로그인 버튼은 각 사 브랜드 색을 따른다: Google(흰 배경+보더), Kakao(`#FEE500`), Naver(`#03C75A`). 이는 D1.3 허용 예외이며 토큰화하지 않는다.

## 5. 접근성 체크리스트
- 폼: label/hint/error `aria-describedby` 연결(`Field`).
- 아이콘 버튼: `aria-label`(예: 헤더 아바타 "내 정보", 햄버거 "메뉴 열기").
- 색+텍스트 병행, 본문에 Accent 옐로 금지(대비).
- 포커스 링 유지, `disabled` 시 `cursor-not-allowed` + opacity.

## 6. 확장 가이드
- 새 컴포넌트는 `shared/ui/<name>/{<name>.tsx,index.ts}` + 배럴 export. 토큰 유틸리티만 사용.
- 다크 모드 도입 시 `@theme`에 다크 토큰 레이어를 추가하고 컴포넌트는 변경하지 않는다(토큰 단일 출처 원칙).
