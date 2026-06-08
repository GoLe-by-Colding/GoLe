# GoLe 브랜드 아이덴티티 / 디자인 컬러

GoLe만의 퍼스널 컬러. 당근(오렌지)·번개장터(레드)·KREAM(블랙)과 차별화하고,
레고의 상징 원색(파랑+노랑)에 뿌리를 둔다.

## 컬러 시스템 (단일 출처: `apps/web/src/app/globals.css` `@theme`)

| 역할 | 토큰 | 핵심 값 | 용도 |
|---|---|---|---|
| Primary (브랜드) | `brand-*` (GoLe Cobalt) | `brand-500 #2f56e6` | 기본 버튼/링크/포커스/식별 요소. 신뢰감. |
| Accent (포인트) | `accent-*` (Brick Yellow) | `accent-500 #fbb500` | 커뮤니티·자랑·MOC·뱃지 등 "놀이" 포인트에만 **절제** 사용. |
| Neutral | `neutral-*` | — | 텍스트/보더/배경. 여백 중심. |
| Semantic | `success/danger/warning/info` | — | 상태 표현 전용(브랜드 컬러와 혼동 금지). |

## 사용 원칙 (당근·후르츠패밀리식 미니멀)

- 배경은 흰색 기반, 여백을 넉넉히. 컬러 그라데이션 배경 금지.
- 그림자는 절제(`shadow-soft`, 호버 시 `shadow-lift`). 큰 글로우/네온 금지.
- 버튼 primary는 **솔리드** `brand-500`(그라데이션 금지). 보조는 흰 배경 + 얇은 보더.
- Accent(옐로)는 화면당 1~2곳 포인트로만. 본문 텍스트 색으로는 대비 문제로 지양.
- 카드는 얇은 보더(`border-neutral-200/70`) + `rounded-xl`.

## 컬러 교체 방법

`globals.css`의 `--color-brand-*` 값만 바꾸면 버튼/헤더/뱃지 등 전체가 일관 리컬러된다.
