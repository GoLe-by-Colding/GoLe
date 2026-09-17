# Brand Icon Requirements

## Goal

`ui-renewal` 스펙은 웹에만 적용됐다. 모바일 앱 아이콘과 스플래시는 여전히 **Expo 기본 자산**이고,
웹 `public/`에는 **create-next-app 기본 SVG**가 남아 있다. `brand-identity.md`가 예고한 후속 스펙
(`brand-icon`)을 실제로 수행해, 사용자가 처음 보는 면을 브랜드 마크로 통일한다.

## 현황 (측정값)

- `apps/mobile/assets/expo.icon/`은 레이어가 `expo-symbol 2.svg` + `grid.png`이고 채움색이
  `extended-srgb:0.0,0.478,1.0`(iOS 시스템 블루)다. **iOS 앱 아이콘이 Expo 로고다.**
- `assets/images/icon.png`(1024², 784KB)와 `android-icon-foreground.png`도 같은 Expo 로고이며,
  브랜드 문서가 금지한 **그라데이션 배경**을 쓴다.
- `splash-icon.png`는 228×213 비정방형이다.
- 참조 0건인 템플릿 잔재: 웹 `next.svg` `vercel.svg` `file.svg` `globe.svg` `window.svg` `logo.svg`,
  모바일 `assets/images/tabIcons/`(6개).

## Requirements

1. 모바일 아이콘 일체를 정본 마크(`apps/web/src/app/icon.svg` — 고래 + 골드 브릭 스터드)에서 생성한다.
2. 배경은 **단색** `brand-600 #1D4ED8`을 쓴다. 그라데이션·그리드·글로우를 넣지 않는다.
3. iOS 아이콘은 1024×1024 정방형, 알파 없음, 자체 라운딩 없음(OS가 마스크한다).
4. Android 어댑티브 아이콘은 전경/배경/모노크롬 3종을 규격 세이프존 안에 맞춘다.
5. 자산은 **재생성 가능**해야 한다 — 소스 SVG와 빌드 스크립트를 저장소에 둔다.
6. 참조 0건인 템플릿 자산을 삭제한다. 참조가 있는 자산은 건드리지 않는다.
7. 골드(`accent`)는 스터드 한 곳에만 남긴다(브랜드 규칙: 화면당 1~2곳).

## Acceptance Criteria

- `app.json`이 Expo 기본 `.icon` 번들을 더는 참조하지 않는다.
- 생성된 iOS 아이콘에 알파 채널이 없다.
- `pnpm --filter mobile format:check`·`lint`·`typecheck`가 통과한다.
- 삭제한 파일의 참조가 저장소 전체에서 0건이다.
