# Design System — Tasks

## 현재 구현됨
- [x] T1 `@theme` 토큰 단일 출처(brand/accent/neutral/semantic/typography/radius/elevation)
- [x] T2 UI Kit: Button/LinkButton/Card/Badge/Input/Textarea/Select/Field/Container/Heading/Text
- [x] T3 슬라이스 공개 API(`@shared/ui` 배럴) + boundaries/steiger 강제
- [x] T4 접근성: Field 기반 폼 연결, 아이콘 버튼 aria-label
- [x] T5 반응형 헤더(햄버거) + auto-fill 그리드
- [x] T6 소셜 로그인 외부 브랜드 버튼(예외 규칙 문서화)

## 후속 백로그 — 2026-08-03 실측 감사 반영
- [ ] T7 다크 모드(@theme 다크 토큰 레이어) — 미구현 확인
      (`globals.css`/`shared/ui`에 `prefers-color-scheme`·`data-theme`·`dark:` 0건)
- [ ] T8 토스트/알림 컴포넌트 — 미구현 확인(`shared/ui/`에 toast 슬라이스 없음)
- [x] T9 스켈레톤/로딩 상태 컴포넌트 표준화 — `shared/ui/skeleton` 존재하며
      8개 화면에서 사용 중(chat-list, community-post, notifications, profile, order-detail,
      listing-qna, set-price-insight, chat-panel). 잔여 화면 확산은 아래 T9a로 분리.
- [ ] T9a 미적용 화면에 스켈레톤 확산(search / seller-shop / listing-detail / prices)
- [ ] T10 폼 검증 메시지 공통화(서버 `{code,message}` ↔ 필드 매핑)
- [ ] T11 Storybook 또는 컴포넌트 카탈로그 페이지
- [ ] T12 브랜드 아이콘(고래×브릭) favicon/OG/로고 마크 — `.kiro/steering/brand-identity.md` 컨셉 반영
