# GoLe 디자인 작업 기준

브릭을 좋아하는 성인이 탐색·거래·수집하는 마켓이다. 어린이 장난감 광고처럼 만들거나 금융 대시보드처럼 꾸미지 않는다. 흰 바탕, 코발트 행동 버튼, 옆모습 브릭 고래와 읽기 쉬운 상품 정보를 기준으로 한다.

## Codex 스킬 구성

2026-10-21 사용자가 디자인 스킬 구성을 요청해 설치 출처와 내용을 확인했다. 스킬 수보다 각 역할을 구분한다.

1. `hallmark`: 기존 레이아웃을 진단하고 정보 위계·반응형을 점검한다. 기존 브랜드의 토큰을 유지하며 임의 테마를 고르지 않는다.
2. [`frontend-design`](https://github.com/anthropics/skills/tree/main/skills/frontend-design): 폰트·레이아웃·브랜드 표현을 구체화하고 실제 화면을 보고 수정한다. 제목과 본문은 SUITE·SUIT 두 계열로 고정한다.
3. [`web-design-guidelines`](https://github.com/vercel-labs/agent-skills/tree/main/skills/web-design-guidelines): 키보드·초점·터치 영역·로딩/오류·모션·반응형을 검사한다. 매 검수 때 원본 지침을 읽는다.
4. 기존 React 검토·verification 스킬: 공통 컴포넌트의 소유 레이어, 상태 정리, 실제 API와 사용자 동선을 확인한다.
5. [`vercel-composition-patterns`](https://github.com/vercel-labs/agent-skills/tree/main/skills/composition-patterns): 2026-10-26 공식 저장소에서 설치했다. 미리보기·후보·업로드처럼 목적이 다른 UI를 작은 조합으로 나누되, 상태의 소유자는 하나로 유지한다. 단순 화면을 불필요한 Context·불리언 모드로 일반화하지 않는다.

설치가 없는 환경에서는 현재 세션의 사용 가능한 스킬 목록부터 확인한다. 임의 이름의 스킬을 호출하지 않는다. 외부 스킬의 일반적인 권장은 저장소 규칙과 사용자의 명시 지시보다 우선하지 않는다.

## 구현 순서

- `.kiro/specs`에 변경할 화면·행동·검증 기준을 적는다.
- `globals.css` 토큰 → `shared/ui` 기본 요소 → `widgets` 조합 → `views`를 수정한다. 간격·버튼 스타일을 화면별로 복사하지 않는다.
- 일반 화면은 `PageLayout`·`PageHeader`, 로그인 안내는 `LoginPrompt`, 빈 목록은 `EmptyState`를 사용한다. 상세·관리자·인증 셸은 목적에 맞는 구성을 유지한다.
- 로고는 기존 SVG 프리셋 체계를 확장한다. 이전 프리셋의 그림·식별자는 보존한다. 새 정본과 OG 경로를 맞춘 뒤 `pnpm brand:icons`로 플랫폼 출력을 생성한다.
- `next/font/local`로 폰트를 실제 로드한다. 글꼴 이름만 적어 기기 기본 글꼴에 의존하지 않는다.
- 실제 Orca 화면과 320·375·414·768px 검사로 긴 한글·버튼 줄바꿈·가로 넘침·하단 메뉴 겹침을 확인한다. 장식·수치를 검증 근거 대신 사용하지 않는다.
- 변경에 맞는 테스트와 품질 게이트를 통과한 뒤 커밋하고 dev에 반영한다. 실결제·실기기·운영 설정처럼 확인하지 못한 경계는 따로 기록한다.

## 현재 디자인 기준

색상: `brand-600 #1D4ED8`, `brand-50 #EFF4FF`, `accent-500 #EAB308`, `neutral-900 #18181B`, 흰 바탕. 제목은 왼쪽 정렬·SUITE 700–800, 본문은 SUIT 400–600. 상품 가격은 줄바꿈 없이 숫자 자릿수를 정렬한다. 골드는 고래 스터드처럼 한정된 위치에만 둔다.

공통 페이지는 제목 → 현재 상태/필터 → 본문 → 다음 행동 순서다. 홈은 짧은 브랜드 소개 다음에 실제 매물로 이어진다. 비어 있음과 요청 실패를 같은 문구로 숨기지 않는다.

## 자동 생성 화면처럼 보이지 않게 하는 기준 (2026-10-26 사용자 지시)

- 상품 사진·가격·거래 상태를 먼저 읽히게 한다. 고래는 브랜드 표식이나 빈 상태의 안내 역할로 쓰고 모든 구획에 반복하지 않는다.
- 카드·배지·영문 보조 제목·숫자 요약은 선택이나 행동 판단에 필요한 경우에만 둔다. 장식용 통계·성급한 신뢰 문구를 만들지 않는다.
- 구획은 여백과 정렬·얇은 구분선으로 나누고, 같은 정보를 제목·배지·설명에서 반복하지 않는다.
- 화면마다 다른 폰트·색·버튼을 만들지 않는다. 긴 한글·실제 이미지 비율·빈 데이터로도 구성이 유지되는지 본다.
- 가로 넘침이 없다는 검사만으로 디자인 검수를 끝내지 않는다. 작은 화면에서 제목이 한두 글자씩 꺾이거나 그림이 슬롯을 벗어나지 않는지 스크린샷으로 확인한다.

## 누름 반동과 모션 토큰 (2026-11-21)

- 누를 수 있는 공용 요소는 `transition-press` 유틸리티(`globals.css`)를 쓴다. 색은 150ms `ease-out`, 크기(`scale`)만 220ms `--ease-spring`으로 따로 움직여 놓을 때 살짝 넘쳤다 돌아온다. 스프링 곡선을 색에 쓰지 않는다.
- Tailwind 4의 `scale-*`는 `transform`이 아니라 독립 `scale` 속성이다. 전환 목록에 `transform`만 두면 누름이 애니메이션 없이 튄다.
- 누름 크기는 `motion-safe:not-disabled:active:scale-96`으로 건다. 모션을 줄인 사용자와 비활성 요소에는 반동이 없다. `tests-e2e/press-motion.spec.ts`가 이를 지킨다.
- 화면마다 `active:scale-*`·`duration-*`를 새로 쓰기 전에 위 유틸리티로 충분한지 먼저 본다. `active:scale-*`를 `transition-press` 없는 클래스 문자열에 쓰면 웹 ESLint(`no-restricted-syntax`)가 막는다.
- 그림자는 토큰만 쓴다: 카드·작은 요소 `shadow-soft`, 떠 있는 바·메뉴 `shadow-md`·`shadow-lift`, 대화상자·시트 `shadow-overlay`. Tailwind 기본 `shadow-sm/lg/xl/2xl`은 웹 ESLint가 막는다.

## 브릭 누름과 한국어 줄바꿈 (2026-11-21)

- 면이 있는 버튼(primary·secondary·accent·danger)은 `brick` 유틸리티로 아래 4px "턱"을 두고, 누르면 턱만큼
  내려앉았다가 `--ease-spring`으로 돌아온다. 턱 색은 `--brick-edge`로 변형마다 면보다 진하게 둔다.
  참고: Duolingo의 두께 있는 버튼, 레고 브릭의 아랫면. 면이 없는 ghost·inverse는 작아지는 누름을 유지한다.
- 턱은 그림자라 레이아웃 높이를 바꾸지 않는다. 동작 줄이기 설정에서는 내려앉지 않는다.
- 새 버튼은 `<button>`에 직접 색을 칠하지 말고 `Button`·`LinkButton`(또는 `BUTTON_VARIANT`)을 쓴다.
- 본문 전체가 `break-keep`(어절 단위 줄바꿈) + `wrap-break-word`다. 컴포넌트마다 `break-keep`을 다시 붙일 필요 없다.
- 여러 개 중 켜고 끄는 필터·태그는 `Chip`(`@shared/ui`)을 쓴다. 켜진 칩은 2px 브릭 턱을 갖고 `aria-pressed`를 단다.
  하나만 고르는 정렬·기간 전환은 칩이 아니라 세그먼트다.
