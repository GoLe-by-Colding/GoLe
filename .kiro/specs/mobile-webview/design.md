# RN 탭 + 모바일 웹

Expo Router Tabs → 공통 WebScreen → Next.js 모바일 화면으로 구성한다.
홈 /, 검색 /search, 판매 /sell, 채팅 /chat, 내 정보 /profile을 연다.
탭은 네이티브가 소유하고 웹 내부 상세 이동은 해당 WebView의 이력을 사용한다.
기존 RN view 코드는 삭제하지 않되 라우트에서는 더 이상 본문으로 사용하지 않는다.
웹 세션은 WebView의 영속 웹 저장소에만 두며 SecureStore 토큰을 JavaScript로 주입하지 않는다.
허용 원점은 URL 파싱 후 정확한 origin 비교를 사용한다. file/data/javascript 및 자격 증명 URL은 차단한다.
앱 루트의 기존 네이티브 인증 부트스트랩·푸시 등록은 웹 로그인과 혼동하지 않도록 분리한다.
웹뷰에 임의 실행 메시지 브리지는 노출하지 않는다.
