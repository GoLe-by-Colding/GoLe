# Tailwind Unification Requirements

## 배경 (2026-10-11)

- 사용자 지시: **Tailwind를 쓰지 않은 CSS를 전부 Tailwind로 통일한다.**
- 2026-10-11 `feat/theme-search-catalog`(15f78533) 전수 조사 결과(`apps/web/src`):
  - CSS/SCSS/CSS Module 파일은 `src/app/globals.css` 하나뿐이다.
  - 커스텀 컴포넌트 클래스 10개(`.gole-spout-brick(-a|-b|-c)`, `.gole-spout-stream`, `.gole-mascot-float`,
    `.gole-rise-in`, `.gole-hero-flow`)가 `@layer components`에 있고 13개 파일이 쓴다.
    `chat-list-page.tsx`의 `gole-rise`는 **어디에도 정의되지 않은 죽은 클래스**였다.
  - 전역 base(html·body·h1~h4·selection·전환·포커스 링)가 손으로 쓴 CSS 선언이다.
  - `style=`는 6개 파일 27곳: 라인 차트 7, OG 이미지 2개 파일 17, 채팅 패널 높이 1, 관리자 표 최소 폭 1, 관리자 디자인 미리보기 1.

## 요구사항

### R1. 전역 CSS는 Tailwind v4 정식 설정만 남긴다

1. `@theme` 디자인 토큰과 keyframes는 그대로 보존한다(값 변경 없음).
2. `.gole-*` 컴포넌트 선언은 지운다. 애니메이션은 `@theme`의 `--animate-*` 토큰으로 옮기고 사용처는 `animate-*`·
   `origin-*`·`transform-fill` 같은 유틸리티 className으로 쓴다. 지연은 토큰 값에 넣는다(지연 유틸리티가 shorthand에 덮이지 않게).
3. 전역 base는 `@apply`로 쓴다. 값(색·폰트·전환 속성·지속 시간·이징·포커스 링·selection)은 지금과 같다.
4. iOS 입력 16px 규칙은 utilities보다 우선해야 하므로 레이어 밖 미디어 쿼리로 남긴다(근거를 주석에 둔다).

### R2. 회귀 금지

1. 모션을 줄인 사용자에게 분수·떠다님·등장 애니메이션은 재생되지 않고 요소는 보인다(`motion-safe:`). 앵커 스크롤도 즉시다.
2. 키보드 포커스 링(2px brand-400, offset 2px)·텍스트 선택색·제목 행간/자간이 그대로다.
3. 원래 옆모습 고래(`side-brick-original`)를 포함한 모든 마스코트 프리셋의 분수 브릭 3개의 위상차(-200/-120/-40ms)·
   물줄기 대시가 그대로다. 홈 히어로 우측 레일의 0/90/180ms 순차 등장이 그대로다. 사용자 레이아웃은 바꾸지 않는다.

### R3. inline style

1. 정적인 값은 Tailwind className으로 옮긴다.
2. 런타임 값(차트 좌표·호출부가 넘기는 높이/최소 폭·관리자 디자인 토큰 초안)은 **CSS 변수만** `style`로 주입하고
   className이 `var()`로 소비한다. 값을 클래스에 하드코딩하지 않는다.
3. OG `ImageResponse`는 브라우저가 아니라 Satori가 그린다. 설치된 `next/og` 타입이 실험 기능으로 제공하는 `tw` prop으로
   옮기되, Satori에서 실제 렌더 결과가 같은 것만 옮긴다. 옮기지 못한 값은 근거를 남긴다.

### R4. 범위 밖

- 브라우저 대화상자 교체 작업 파일(`admin-mascot`·`account-security`·관리자 정산/탈퇴·커뮤니티 글·부품 요청·
  manage* 화면, `shared/ui/dialog`·`index.ts`·`eslint.config`)은 이 작업에서 편집하지 않는다. 그 안의 커스텀 클래스는
  호환용으로 남기고 담당자에게 넘긴다.
- RN(`apps/mobile`)·백엔드는 범위 밖이다.

## 수용 기준

- `rg "gole-(spout|mascot|rise|hero)" apps/web/src`가 호환 대상(R4) 외 0건.
- `style=`은 OG의 근거 있는 값과 CSS 변수 주입만 남는다.
- web format · lint · typecheck · fsd 통과, 홈·차트·디자인 토큰 관련 E2E 통과.
