# Tailwind Unification Design

## 애니메이션 토큰

`@theme`에 keyframes 옆으로 `--animate-*`를 둔다. Tailwind v4가 `animate-<이름>` 유틸리티를 만들고, keyframes는 토큰이
쓰일 때만 출력된다.

| 이전 클래스 | 토큰 / className |
|---|---|
| `.gole-spout-brick` + `-a/-b/-c` | `--animate-spout-brick-a/b/c`(지연 -200/-120/-40ms 포함) · `transform-fill origin-center motion-safe:animate-spout-brick-a` |
| `.gole-spout-stream` | `--animate-spout-stream` · `[stroke-dasharray:42_10] motion-safe:animate-spout-stream` |
| `.gole-mascot-float` | `--animate-mascot-float` · `origin-[50%_62%] motion-safe:animate-mascot-float` |
| `.gole-rise-in` | `--animate-rise-in` · `motion-safe:animate-rise-in` |
| `.gole-hero-flow > *` (+nth-child 2/3 지연) | `--animate-hero-flow`, `-2`, `-3` · `*:motion-safe:animate-hero-flow *:nth-2:motion-safe:animate-hero-flow-2 *:nth-3:motion-safe:animate-hero-flow-3` |

- 지연을 `[animation-delay:…]`로 따로 주면 같은 변형 안에서 `animation` shorthand와 출력 순서 경쟁을 한다. 토큰마다
  지연을 넣어 한 선언으로 끝낸다.
- 이전 `@media (prefers-reduced-motion: reduce) { animation:none; opacity:1 }`은 `motion-safe:`로 대체한다. 애니메이션이
  적용되지 않으면 keyframes의 opacity 0이 걸리지 않으므로 요소는 원래대로 보인다.
- `*:nth-2:`는 `:is(.x > *):nth-child(2)`라 `*:`보다 명시도가 높아 같은 미디어 쿼리 안에서 이긴다.
- `cn()`은 tailwind-merge 없이 이어 붙이기만 하므로, `SpoutBrick` 기본 클래스에는 애니메이션을 넣지 않고 호출부가
  `-a/-b/-c` 중 하나만 준다(충돌 없음).
- `gole-rise`(미정의)는 의도대로 `motion-safe:animate-rise-in`으로 바꾼다.

## 전역 base

`@layer base` 안에서 `@apply`로 쓴다. `--design-font-size`는 관리자 디자인 토큰이 덮는 변수라 선언으로 둔다.

- html: `text-(length:--design-font-size) font-sans text-neutral-900 bg-neutral-50 scroll-smooth motion-reduce:scroll-auto
  [-webkit-tap-highlight-color:transparent] [scrollbar-gutter:stable]`
- body: `min-h-dvh [-webkit-font-smoothing:antialiased] [text-rendering:optimizeLegibility]`
  (`antialiased`는 Firefox용 `-moz-osx-font-smoothing`까지 바꾸므로 기존 값 그대로 임의 속성으로 둔다)
- h1~h4: `leading-[1.15] tracking-tight font-bold text-neutral-900` (tracking-tight = -0.025em)
- `::selection`: `bg-brand-200 text-brand-900`
- 상호작용 요소: `transition-[color,background-color,border-color,opacity] duration-150 ease-[cubic-bezier(0.4,0,0.2,1)]`
- `:focus-visible`: `outline-2 outline-brand-400 outline-offset-2`
- iOS 16px: 레이어 밖 `@media (pointer: coarse), (max-width: 639.98px)`를 유지한다. utilities 레이어의 `text-sm`보다
  이겨야 하는 덮어쓰기라 유틸리티/변형으로 표현하면 우선순위를 보장할 수 없다. `text-base`는 행간까지 바꾸고 rem이라
  관리자 글자 크기 토큰을 따라가므로 16px 고정 선언을 둔다.

## inline style

| 위치 | 처리 |
|---|---|
| line-chart 빈 상태 높이(prop) | `--chart-h` 주입 · `h-(--chart-h)` |
| line-chart SVG 비율(상수 720/280) | `aspect-[720/280]` |
| 툴팁 x/y(데이터 좌표) | `--tip-x`·`--tip-y` 주입 · `left-[clamp(52px,var(--tip-x),calc(100%-52px))] top-(--tip-y)`; 위/아래 뒤집기는 `translate-y-[14px]`/`-translate-y-[calc(100%+14px)]` className |
| 툴팁 꼬리 | `top-[-4px]` / `bottom-[-4px]` |
| 가격 눈금·현재가 배지 y | `--tick-y` · `top-(--tick-y)`; 배지 색은 `bg-rise`/`bg-fall` |
| X축 라벨 x | `--label-x` · `left-(--label-x)`; 정렬은 `translate-x-0`/`-translate-x-1/2`/`-translate-x-full` |
| 채팅 패널 높이 420 | `h-[420px]` |
| 관리자 표 최소 폭(prop) | `--table-min-w` · `min-w-(--table-min-w)` |
| 관리자 디자인 미리보기 | 이미 토큰 초안을 CSS 변수로 주입하고 `bg-brand-*`·`p-[var(--space-card)]` 등이 소비한다. 유지 |

px 값은 rem으로 바꾸지 않는다. 루트 글자 크기가 관리자 토큰(`--design-font-size`)을 따라가 rem 간격은 함께 변한다.

## OG 이미지

`next/og`(`node_modules/next/dist/compiled/@vercel/og/types.d.ts`)는 `tw?: string`을 실험 기능으로 선언한다. Satori의 `tw`는
자체 Tailwind 해석기(v3 기본 스케일)를 쓰고 이 앱의 `@theme`을 읽지 않는다. 그래서 색은 임의 값(`bg-[#1d4ed8]`)으로 쓴다.
옮긴 뒤 이전/이후 PNG를 같은 Node 렌더러로 그려 픽셀 비교한다. 같지 않은 속성은 `style`에 남기고 이유를 주석으로 단다.
