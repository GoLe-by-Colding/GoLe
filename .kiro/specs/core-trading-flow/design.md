# 설계

## 탐색·홈 지표 문구

검색의 `ready` 상태에서만 `검색 결과 N개`를 표시한다. 홈은 `loadTrending()`이 조회한 최대 8세트의 `tradeCount` 합계를 그대로 사용한다. 합계가 표시될 때 집계 기준 설명도 함께 표시하고, 서버의 최근 30일 우선·전체 기간 대체 및 출처 정책은 변경하지 않는다.

## 공통 화면 구성

판매자 샵의 출시 설정은 `sellerId`별 진입 시 별도 effect에서 조회한다. 후기 effect는 확인한 `reviewsOpen`과 `sellerId`·재시도 횟수에만 의존하며 평점·후기 API를 조회한다. 각 effect의 종료 후 응답 무시 처리는 유지한다.

- `shared/ui/page`의 PageLayout은 컨테이너 폭·상하 여백·섹션 간격을, PageHeader는 h1·설명·주요 액션의 반응형 정렬을 소유한다.
- `widgets/login-prompt`는 기존 EmptyState·LinkButton·옆모습 고래로 로그인 안내를 조합한다. 복귀 경로는 기존 shared/lib로 검증한다.
- 브릭 필터 편집기의 개별 버튼·카드·결과 대기도 Button·Card·EmptyState를 사용한다. 변환 API·동의·한도·공개 잠금은 유지한다.
- 적용 경로: views/{brick-filter,collection,following-feed,sell,profile,search,prices,community}, shared/ui/{page,card}, widgets/login-prompt.

## 화면과 동선

기존 `.kiro/steering/brand-identity.md`와 `apps/web/src/app/globals.css`가 디자인 정본이다. 홈은 실제 매물→시세/세트→커뮤니티 순의 거래 입구, 탐색·판매·주문은 행동 중심 화면을 유지한다. 2026-10-21 추가 요청에 따라 SUIT·SUITE를 자체 제공하고, 기존 팔레트에서 옆모습 브릭 고래의 새 프리셋을 추가한다. 작은 화면은 제목/보조 행동을 쌓고 4px 기반 Tailwind 간격과 최소 44px 터치 영역을 쓴다.

`widgets/mobile-navigation`을 `(main)` 셸에 추가한다. 서버 User-Agent의 GoLeApp 표식으로 네이티브 앱을 제외하고, 화면 아래 안전 영역 및 채팅·관리자 도구 여백을 맞춘다. 홈은 기존 목록 API·ListingCard를 재사용한다.

판매 화면의 returnTo에 선택 세트 번호를 포함한다. `onboarding?purpose=seller&returnTo=...`는 서버의 판매 준비 래치를 확인한 뒤 번호 확인 단계를 추가한다. 완료 여부는 서버 상태를 재조회하며 일반 온보딩 면제를 판매 권한으로 바꾸지 않는다.

## 결제

RN SDK 결제는 일반 서비스 WebView와 분리한다. 웹의 `requestPortOnePayment`가 버전이 일치하는 앱 브리지로 공개 결제 파라미터를 보내고 원본 WebView는 유지한다. RN은 주문 경로와 paymentId 일치, 정확한 원점, 필드 크기/형식, 금액·수단을 검사한다. SDK 입력은 허용 필드만 재구성하며 HTML 삽입 문자를 거부한다. 취소·오류·완료 결과는 요청 ID를 맞춘 웹 이벤트로 돌려보내며 모든 성공 응답은 기존 payOrder의 서버 원장 검증을 통과해야 한다.

웹 redirect 복귀는 유효한 주문번호를 먼저 확인하고, 서버 주문 상태별로 결과를 표시한다. 세션 만료 시 복귀 URL을 보존한 로그인, 네트워크/승인 지연 시 상태 재확인, 이미 환불·분쟁 중인 주문에는 해당 상태를 보여준다. 브라우저 성공 파라미터를 승인 근거로 삼지 않는다.

공식 근거(2026-10-21 확인):
- https://developer.apple.com/app-store/review/guidelines/#goods-and-services-outside-of-the-app
- https://support.google.com/googleplay/android-developer/answer/9858738?hl=en-GB
- https://developers.portone.io/opi/ko/extra/mobile-payment/readme-v2?v=v2
- https://github.com/portone-io/react-native-sdk
