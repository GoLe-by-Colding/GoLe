# 설계

세트 route의 병렬 조회에 fetchLaunchConfig(no-store, 실패 시 안전 기본값)를 추가한다.
SetDetailPage에 sellerTradingOpen을 명시 전달하며 누락 시 false다. 상세 제목/입찰 문구/판매
행동은 같은 준비 값을 사용한다. SetBidSection에는 읽기 전용 문구 판정만 전달하며 입찰 API는 유지한다.

매물 배열은 route에서 null과 빈 배열을 구분한다. 화면에는 목록과 listingsLoadFailed를 전달하고,
실패 시 판매 중 건수는 확인 불가로 표시한다. JSON-LD에는 검증되지 않은 offer를 추가하지 않는다.
기존 RefreshButton을 사용해 재조회한다. 상태별 UI는 기존 Card/Text/LinkButton을 재사용한다.

원격 조회와 375px 캡처, 로컬 격리 API 상태별 브라우저 테스트를 구분한다. 공개 운영에는 쓰지 않는다.
