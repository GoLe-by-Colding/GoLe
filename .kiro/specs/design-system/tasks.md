# Design System — Tasks

## 현재 구현됨
- [x] T1 `@theme` 토큰 단일 출처(brand/accent/neutral/semantic/typography/radius/elevation)
- [x] T2 UI Kit: Button/LinkButton/Card/Badge/Input/Textarea/Select/Field/Container/Heading/Text
- [x] T3 슬라이스 공개 API(`@shared/ui` 배럴) + boundaries/steiger 강제
- [x] T4 접근성: Field 기반 폼 연결, 아이콘 버튼 aria-label
- [x] T5 반응형 헤더(햄버거) + auto-fill 그리드
- [x] T6 소셜 로그인 외부 브랜드 버튼(예외 규칙 문서화)

## 후속 백로그
- [ ] T7 다크 모드(@theme 다크 토큰 레이어)
- [ ] T8 토스트/알림 컴포넌트(현재 인라인 배너만 존재)
- [ ] T9 스켈레톤/로딩 상태 컴포넌트 표준화
- [ ] T10 폼 검증 메시지 공통화(서버 `{code,message}` ↔ 필드 매핑)
- [ ] T11 Storybook 또는 컴포넌트 카탈로그 페이지
- [ ] T12 브랜드 아이콘(고래×브릭) favicon/OG/로고 마크 — `.kiro/steering/brand-identity.md` 컨셉 반영
