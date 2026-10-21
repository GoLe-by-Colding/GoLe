# 계정·알림 흐름 검증 근거

## 환경과 범위

- 2026-10-21, 공유 체크아웃 `fix/complete-service-flows`.
- 브라우저 UI 회귀는 루트가 띄운 WEB 3002에서 `workers=1`, `retries=0`으로 실행했다.
- 수동 검증은 Orca의 별도 isolated profile `b387008e-5b97-4931-a766-a2529d3815d7`,
  전용 탭 `be9b80db-8018-45eb-9930-7e34c921d282`를 사용했다. 사용자 기본 프로필과
  localhost 3000/API 8090 로그인 상태는 조작하지 않았다.
- worker는 git 변경 명령·install·서버 기동/종료를 수행하지 않았다. 백엔드 전체 검증과
  커밋·push·dev 병합은 루트가 담당한다.

## 변경 전 재현

| 흐름 | 재현 결과 |
| --- | --- |
| 전체 읽음 | 성공 응답 후에도 헤더 `알림 2건`이 남음 |
| 한 건 읽음 실패 | 기존 목록이 조회 실패 화면으로 바뀌어 같은 항목 재시도 불가 |
| 알림 설정 두 분류 연속 변경 | 동시에 처리되는 PUT 2개, 뒤늦은 전체 응답으로 다른 분류 유실 가능 |
| WebView 저장 세션 복원 | 새로고침에서 기존 단말 토큰 DELETE 2회 발생 |
| WebView 등록 중 로그아웃 | 늦은 POST 완료 후 DELETE 요청 없음 |
| WebView 해제 응답 유실 후 같은 계정 로그인 | 서버 등록이 지워졌지만 캐시 때문에 POST 0회 |
| 다른 WebView 등록 뒤 이전 DELETE 완료 | 새 계정의 등록이 지워지고 포커스 복귀에도 재등록 없음 |
| 종료된 문서의 요청으로 서버 등록만 사라진 뒤 재진입 | 완료 캐시를 믿어 재등록하지 않음 |
| 온보딩 일시 오류 | 같은 화면에서 다시 시도 버튼 없음 |
| Android 권한 요청 | 채널이 없어 권한 프롬프트가 허용을 반환하지 않는 계약 재현 |
| 관리자 정지 해제·이메일 인증 | `reinstate()`의 무조건 VERIFIED 대입과 정지 검사 없는 `verify()`를 코드로 확인 |

- 웹 변경 전 로그: `/tmp/gole-account-flow-before.log`,
  `/tmp/gole-account-onboarding-before.log`.
- Android 변경 전 로그: `/tmp/gole-account-device-before.log`.
- 최종 리뷰의 푸시 P2 변경 전 로그: `/tmp/gole-account-push-review-before.log`,
  `/tmp/gole-account-push-review-before-multi.log`. 첫 다중 WebView 실행은 계정 전환에 대한
  알림 조회 mock 누락으로 먼저 실패해 fixture를 고쳤고, 두 번째 실행에서 완료된 등록을
  늦은 DELETE가 지운 뒤 복구하지 않는 결함을 재현했다. POST 응답 지연 변형은 기존 코드도
  통과했으며 최종 회귀에서 함께 유지했다.
- 앱 재진입의 완료 캐시 재사용 실패 2건: `/tmp/gole-account-push-resume-before.log`.

## 변경 후 자동 검증

- 계정·가입·이메일 인증·재설정·복귀·프로필·컬렉션·알림 설정·피드 E2E **82 passed,
  0 failed, 0 skipped**. 명령은 `E2E_WEB_PORT=3002 pnpm --filter web e2e`에
  `account-flow-completion`, `profile`, `auth-navigation`, `auth-return-to`,
  `notification-settings`, `onboarding-phone-policy`, `account-security-password`,
  `collection-value-history`, `price-collection-loop`, `following-feed` 스펙을 지정했다.
  로그: `/tmp/gole-account-regression.log`.
- 최종 코드에서 `account-flow-completion.spec.ts` 16건 + `notification-settings.spec.ts`
  3건을 실행해 **19 passed, 0 failed, 0 skipped**. 토큰 교체·응답 유실·해제 재시도와
  저장소를 공유하는 브라우저 페이지 2개의 지연 DELETE/POST 응답 경쟁, `randomUUID` 없는
  HTTP 개발 환경, 완료 캐시가 있는 앱 재진입·focus·online 재등록을 포함한다.
  로그: `/tmp/gole-account-push-resume-after.log`.
- 알림 대상 이동 회귀(`connected-actions`, `buy-bids`, `price-offer`, `part-request`의
  `-g '알림'`) **4 passed, 0 failed, 0 skipped**. 안전한 내부 경로와 외부 링크 차단,
  입찰 매물·가격 제안 채팅·부품 요청 상세 이동을 확인했다.
  로그: `/tmp/gole-account-notification-targets.log`.
- `node --test apps/mobile/src/features/push-notifications/lib/device-push-token.test.cjs`:
  **4 passed, 0 failed, 0 skipped**. 네이티브 SDK를 모의한 호출 계약이며 실기기 테스트가 아니다.
- `node .kiro/specs/service-polish/notification.test.cjs`: 기존 RN 알림 경로·생명주기 **39건 통과**.
- 웹·모바일 `tsc --noEmit --incremental false` 통과. 웹 최종 로그:
  `/tmp/gole-account-web-types-final-review.log`, 모바일: `/tmp/gole-account-mobile-types.log`.
- 변경한 웹·모바일 소스의 ESLint·Prettier 검사 통과. 테스트 파일은 웹 ESLint의 기존
  제외 규칙 대상이므로 별도 타입·실행·Prettier로 확인했다.
- `pnpm --filter web fsd:lint`: **No problems found**.
- API 회귀는 `AccountStatusTransitionTest` 5건, `AccountServiceTest` 추가 2건이다.
  루트 실행 XML에서 각각 **5건/32건, 실패 0·스킵 0**을 확인했다.
  루트 전체 실행 보고는 단위 **1,718건 실패 0·스킵 2**, 통합 **196건 실패 0·스킵 1**이다.

## 실제 API와 Orca 검증

- 격리 프로필에서 비로그인 컬렉션 → 로그인 CTA → 실제 E2E 관리자 이메일 로그인 →
  `/collection` 복귀를 확인했다. 사용자 관리자 계정과 다른 시드 계정이며 자격은 기록하지 않았다.
- 컬렉션에 보유 세트 `10307`을 추가하고 새로고침 뒤 목록·추정가 유지를 확인했다.
  시세 화면 `/prices?set=10307`의 `보유 컬렉션에 있음`과
  `/sets/10307#set-listings-heading` 매물 목록으로 이어지는 것을 확인했다.
  추가한 항목은 삭제했으며 새로고침 뒤 빈 컬렉션과 추정가 0원으로 복구됐다.
- `/profile/notifications`에서 watch·community를 연속 OFF로 변경하고 새로고침 뒤
  둘 다 OFF, offer ON, trade ON/disabled 유지를 확인했다. 두 분류를 다시 ON으로 돌리고
  새로고침해 원래 상태 복구까지 확인했다.
- 알림함의 실제 빈 목록·설정 이동과 내 정보의 `닉네임 설정하기` →
  `/onboarding?returnTo=%2Fprofile` 1단계 진입을 확인했다. 온보딩 개인정보·SMS는 제출하지 않았다.
- Orca ref 클릭이 일부 항목에서 반영되지 않아 해당 링크·스위치는 같은 Orca 탭의
  `eval`로 실제 DOM click을 호출했다. API 응답은 가로채지 않았으며 실제 포인터 조작과 구분한다.
- 검증용 컬렉션·알림 설정을 원복하고 전용 탭·isolated profile을 삭제했다. 서버는
  worker가 띄우거나 끄지 않았으며 루트가 전체 회귀를 위해 유지한다.

## 미검증 경계

- 10월 22일 감독자 후속 검토: 서로 다른 네이티브 토큰을 가진 WebView가 공유 등록 캐시를
  교체 대상으로 오인해 최신 토큰을 삭제하는 경합을 재현했다. 각 문서가 직접 시도한 이전
  토큰만 교체 정리하도록 보완했고 로그아웃은 공유 기록과 문서 기록을 함께 정리한다.
  추가 회귀는 후속 로컬 세 실행에서 모두 통과했다. 각 20건 묶음은 Mac 반복 절전으로
  일부 시간초과가 있어 전체 성공으로 표시하지 않는다. 세부는 `service-flow-completion/verification.md`,
  최종 게이트는 동일 커밋의 원격 CI를 참조한다.

- 실제 Google/Kakao/Naver OAuth 승인, SMS/알림톡·이메일 외부 발송, 실제 FCM/APNs 전송,
  iOS/Android 실기기 권한창·백그라운드 수신은 실행하지 않았다.
- 위 E2E 82건/19건/4건은 API 응답을 가로채는 UI 회귀를 포함한다. 운영·실 공급자 검증으로
  해석하지 않는다. Android 채널 선행 조건의 근거는
  [Expo SDK 57 알림 문서](https://docs.expo.dev/versions/v57.0.0/sdk/notifications/)다.
- 운영 공개·결제 잠금 변경, 운영 배포는 수행하지 않았다.
