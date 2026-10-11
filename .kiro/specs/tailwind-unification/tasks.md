# Tailwind Unification Tasks

- [x] 전수 조사(CSS 파일·커스텀 클래스·`style=`) — requirements 배경
- [x] `@theme` 애니메이션 토큰, `.gole-*` 제거, base `@apply` 전환
- [x] 사용처 className 교체(logo presets·home·admin·empty-state·chat-list)
- [x] line-chart·chat-button·admin table CSS 변수/className 전환
- [x] OG 이미지 `tw` 전환 + 이전/이후 픽셀 비교
- [x] 컴파일된 CSS로 지연·명시도·reduced-motion 확인
- [x] web format · lint · typecheck · fsd, 홈·차트·디자인 E2E
- [x] 대화상자 담당 파일(admin-mascot)의 `gole-mascot-float` 교체 후 `globals.css`의 호환 `@utility gole-mascot-float` 제거(root가 모달 담당에 요청함)

## 검증 기록 (2026-10-11)

- web format:check · lint · typecheck · fsd:lint 통과
- E2E chromium: home 9/9, collection-value-history·price-collection-loop·admin-console 46 passed / 6 skipped
  (skip은 `E2E_WITH_BACKEND` 없는 관리자 API 가드). prices.spec 첫 실행은 기본 API(:8080) 미기동으로 4건 실패; 실제 API 원점 `NEXT_PUBLIC_API_BASE_URL=http://localhost:8090`으로 다시 실행해 7/7 통과
- `@tailwindcss/postcss`로 globals.css를 컴파일해 지연·명시도·`motion-safe` 출력과 base 값이 이전과 같음을 확인
- Orca 브라우저(기존 :3000 dev 서버)에서 홈 계산 스타일 확인: hero 0/90/180ms, 브릭 -200/-120/-40ms·fill-box, 물줄기 42/10, 떠다님 원점 50% 62%
- OG 이미지 2개(매물은 데이터 유무 2가지)를 이전/이후 `next/og` Node 렌더러로 그려 픽셀 동일 확인

- `body.style.overflow` 3곳을 `overflow-hidden` 클래스로 바꾸고 모바일 검사의 구현 의존 단정을 계산 스타일 검사로 바꿈: mobile 15 passed / 2 skipped, 스크롤 잠금·동의·직거래 9/9 통과
- `pnpm brand:icons --check` 통과(브랜드 정본과 2개 사본 일치)
- 최종 `style=` 예외: 런타임 CSS 변수 7곳(차트 5·테이블 1·디자인 토큰 미리보기 1), Satori가 `tw`로 렌더하지 못하는 매물 OG 그라데이션 1곳. 화면 정적 스타일과 OG 간격은 모두 Tailwind로 전환함
