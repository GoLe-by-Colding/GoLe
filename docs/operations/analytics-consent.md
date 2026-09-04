# GA4·GTM 분석 동의 운영 계약

GoLe는 Google의 **기본 동의 모드**처럼 동작한다. 이용자가 명시적으로 허용하기 전에는 Google
Analytics·Google Tag Manager 스크립트를 DOM에 만들지 않으며, 동의 상태 ping을 포함한 Google
네트워크 요청도 보내지 않는다. Google이 설명하는 고급 동의 모드의 쿠키 없는 ping도 사용하지
않는다.

## 빌드 모드 선택

| 공개 빌드 변수                               | 결과                                        |
| -------------------------------------------- | ------------------------------------------- |
| 둘 다 빈 값                                  | 동의 UI·분석 저장소·Google 요청 완전 비활성 |
| `NEXT_PUBLIC_GA_MEASUREMENT_ID=G-...`만 설정 | 동의 뒤 `gtag.js` 직접 로드                 |
| `NEXT_PUBLIC_GTM_ID=GTM-...` 설정            | 동의 뒤 GTM만 로드                          |
| 두 값 모두 설정                              | **GTM만** 로드; 직접 GA 로더는 무시         |

ID는 비밀값이 아니며 브라우저 번들에 포함된다. 형식이 올바르지 않거나 앞뒤 공백이 있으면 빌드를
실패시킨다. 공개 ID도 운영 설정의 단일 신뢰 경계를 유지하기 위해 Control에서
`gole-production-env`의 새 immutable Secret Manager 버전에 넣고, 승인된 main CD에서 프론트
이미지를 다시 빌드해야 반영된다. runner 환경변수나 GitHub repository variable로 우회 주입하지
않는다. 실 ID가 준비되기 전에는 두 변수를 만들지 않거나 빈 값으로 둔다.

## GA4 속성의 필수 개인정보 설정

ID를 운영에 넣기 전에 속성 관리자에서 아래를 모두 확인한다.

1. 사용자·이벤트 데이터 보유기간을 **2개월**로 설정한다.
2. 향상된 측정을 끄고 페이지 조회만 수집한다.
3. Google Signals, 광고 개인 최적화, Google Ads 연결, User-ID, 데이터 가져오기를 사용하지 않는다.
4. Google 제품·벤치마킹·기술 지원 목적의 계정 데이터 공유를 끈다.
5. 이벤트 이름·파라미터·사용자 속성에 계정 ID, 이메일, 전화번호, 채팅·문의·결제 입력값을 넣지
   않는다.

앱은 쿼리·해시를 버리고 주문·매물·판매자·게시글·OAuth 경로 식별자를 `:id` 같은 경로
템플릿으로 치환한 화면 유형만 전송한다. 사용자 작성 제목 대신 이 경로 템플릿을 화면명으로 쓰고,
같은 사이트 referrer도 같은 방식으로 치환하며 외부 referrer는 origin만 보낸다. 직접 GA 모드는
`_ga` 계열 쿠키 만료를 90일로 고정하고 재방문 시 만료일을 갱신하지 않는다. Google 공식 문서상
기본 `_ga` 만료는 2년이고 태그 설정이 관리 화면 설정을 덮어쓰므로, 이 코드 설정을 제거하지 않는다.

## GTM 컨테이너의 단일 페이지뷰 규칙

GTM을 쓰면 컨테이너가 분석 태그의 유일한 소유자다. 다음 구성 외의 게시를 금지한다.

1. Google tag 하나에 GA4 측정 ID를 설정한다. 앱이 GTM보다 먼저 push하는
   `gole_analytics_policy`의 `gole_send_page_view`, `gole_cookie_expires`,
   `gole_cookie_update`를 데이터 영역 변수로 만들고, Google tag의 `send_page_view`,
   `cookie_expires`, `cookie_update` 구성 필드에 각각 연결한다. Preview에서 최종값이 반드시
   `false`, `7776000`, `false`여야 하며 다른 값이면 게시하지 않는다.
2. 데이터 영역 변수 `page_location`, `page_path`, `page_title`, `page_referrer`를 만든다.
3. 정확히 `gole_page_view`인 Custom Event trigger 하나를 만든다.
4. GA4 `page_view` 이벤트 태그 하나를 위 trigger에만 연결하고 네 데이터 영역 변수를 같은 이름의
   이벤트 파라미터로 전달한다.
5. All Pages의 자동 page_view, History Change page_view, 향상된 측정의 페이지 변경 수집을 모두
   끈다. 앱이 최초 진입과 Next.js 경로 변경마다 `gole_page_view`를 정확히 한 번 push한다.
6. 광고·리마케팅·Conversion Linker·맞춤 HTML·제3자 픽셀 태그를 넣지 않는다. 추가 목적이 생기면
   별도 동의 범주와 개인정보처리방침 변경을 먼저 배포한다.

앱은 GTM을 로드하기 직전에 `analytics_storage=granted`만 지정하고 `ad_storage`, `ad_user_data`,
`ad_personalization`과 나머지 Google 저장 목적은 `denied`로 큐에 넣는다. `ads_data_redaction=true`,
`url_passthrough=false`, Google signals·광고 개인화 허용도 `false`로 고정한다. GTM 컨테이너의 각
태그에도 `analytics_storage` 기본 제공 동의 검사를 요구한다.

## 게시 전 검증

브라우저의 새 프로필이나 시크릿 창에서 다음을 확인한다.

1. 첫 화면에서 선택하기 전 DevTools Network의 `google`, `gtm`, `collect` 요청이 0건이고
   `document.cookie`에 `_ga`가 없어야 한다.
2. **거부** 뒤 페이지를 이동·새로고침해도 요청이 0건이어야 한다.
3. **분석 허용** 뒤 선택한 공급자의 스크립트만 한 번 로드되어야 한다. GTM ID가 있으면
   `/gtag/js` 직접 요청은 없어야 한다.
4. DebugView에서 쿼리·해시가 없고 동적 식별자가 `:id`로 치환된 `page_view`가 경로당 한 건이며,
   이메일·전화번호·내부 계정 ID·사용자 작성 제목이 없는지 확인한다. GTM Preview의 Google tag
   최종 구성에도 `cookie_expires=7776000`, `cookie_update=false`가 표시되는지 확인한다.
5. 푸터 **분석 설정 → 동의 철회** 뒤 새로고침되고 `_ga`, `_ga_*`, `_gid`, `_gat`, `_gac_*`,
   `_gcl_au`가 없어지며 추가 전송이 멈춰야 한다.
6. **선택 초기화** 뒤 로컬 동의 기록이 없어지고 최초 선택 화면이 다시 떠야 한다.

자동 검증은 `pnpm --filter web e2e --grep "분석 동의"`로 실행한다. 테스트용 형식 ID만 사용하고
Google 요청은 브라우저에서 가로채 외부로 보내지 않는다.

## 철회와 기존 데이터

철회 시 브라우저 선택을 먼저 `denied`로 저장하고 알려진 분석 쿠키와 스크립트를 지운 뒤 문서를 한
번 새로 읽는다. 새로고침은 이미 실행된 GTM 태그가 등록한 History listener·timer까지 제거하기 위한
것이다. 철회 이후에는 새 데이터를 보내지 않지만, 철회 전에 수집된 사용자·이벤트 데이터는 속성의
2개월 보유기간과 Google의 월별 삭제 절차가 끝날 때까지 남을 수 있다. 표준 집계 보고서는 GA4의
사용자·이벤트 보유기간 설정 대상이 아니므로 분석 목적 종료 시 속성과 함께 삭제한다.

## 근거 문서

- [Google Consent Mode: 기본 모드는 동의 전 태그와 데이터 전송을 차단](https://developers.google.com/tag-platform/security/concepts/consent-mode)
- [GA4 기본 수집 항목과 `_ga` 클라이언트 ID](https://support.google.com/analytics/answer/11593727)
- [GA4 데이터 보유기간과 집계 보고서 예외](https://support.google.com/analytics/answer/7667196)
- [GA4 쿠키 이름·기본 만료와 태그 설정 우선순위](https://support.google.com/analytics/answer/11397207)
- [Google Analytics 지역 수집과 HTTPS 전송](https://support.google.com/analytics/answer/11598602)
