# 앱 푸시 토큰 연결 (app-push-bridge) — Spec

> `mobile-webview` 이후 남은 "실기기 FCM 등록·웹 로그인 연결"을 닫는다. 앱은 하단 탭만 네이티브이고
> 본문과 로그인은 웹(WebView)이 가진다. 그래서 푸시 토큰 등록이 어느 쪽에서도 일어나지 않았다.

## 왜 필요한가

- 루트 레이아웃이 `usePushRegistration(false)` 로 네이티브 등록을 꺼 두었다. 네이티브는 웹 세션을 모르고,
  예전 SecureStore 계정으로 등록하면 **다른 계정의 알림이 이 단말로 온다.**
- 웹은 로그인을 알지만 FCM 토큰을 얻을 수 없다.
- 결과적으로 서버에 등록된 단말이 0이라 운영에 FCM 을 켜도 푸시가 나갈 곳이 없었다.

## Requirements (EARS)

- P1. 앱은 실행당 한 번만 알림 권한을 묻고 FCM 토큰을 얻는다. 탭마다 WebView 가 따로 떠도 한 번이다.
- P2. 앱은 토큰을 **데이터로만** 웹에 건넨다 — `window.__GOLE_APP_PUSH__ = {token, platform}` 와
  `gole:app-push-token` 이벤트. 임의 실행 브리지(postMessage 명령 등)는 열지 않는다.
- P3. WHEN 웹에 로그인 세션과 토큰이 모두 있으면, 웹은 자기 세션으로 `POST /api/v1/notifications/devices`
  를 부른다. 같은 (계정, 토큰)은 다시 보내지 않는다(로컬 표식). 서버 등록은 멱등이라 계정이 바뀌면 소유자만 바뀐다.
- P4. WHEN 로그아웃되면, 웹은 마지막으로 등록한 토큰을 `DELETE …?token=` 으로 지운다(인증 불필요).
- P5. 앱 밖 브라우저에서는 아무것도 하지 않는다.
- P6. 앱이 열려 있을 때도 알림 배너를 띄운다.
- P7. 알림을 탭하면 link 로 이동한다. FCM data 는 iOS 는 `trigger.payload`, Android 는
  `trigger.remoteMessage.data` 에 오므로 둘 다 본다(기존 코드는 `content.data` 만 봤다).
- P8. 서버는 기본 알림음을 켠다(`apns.payload.aps.sound`, `android.notification.default_sound`).

## Design

- 웹: `shared/lib/app-push-token.ts`(읽기·구독) + `app/(main)/app-push-registration.tsx`(등록·해제).
  features 슬라이스로 두지 않은 이유 — 메인 레이아웃에서 한 번만 쓰는 앱 셸 관심사이고, features 레이어가
  steiger 의 슬라이스 수 상한(20)에 닿아 있다.
- 앱: `features/push-notifications` 에 `useDevicePushToken`(메모이즈된 권한·토큰), `webPushTokenScript`,
  `configureForegroundNotifications` 를 더하고 `views/web` 의 WebScreen 이 주입한다
  (`injectedJavaScriptBeforeContentLoaded` + 늦게 온 토큰은 `injectJavaScript`).
- 서버: `FcmPushSenderAdapter.body` 에 알림음.

## 운영 전제 (코드 밖)

- Firebase `gole-prod` 의 iOS 앱 `kr.gole.app` 에 APNs 인증 키(`U5GSKYBD9V`)가 개발·운영 모두 올라가 있다.
- Apple App ID `kr.gole.app` 에 Push Notifications 가 켜져 있다.
- 운영 env 에 `FCM_ENABLED=true`·`FCM_PROJECT_ID=gole-prod`·`FCM_CREDENTIALS_BASE64` 가 필요하다.
  발송 계정은 최소 권한 역할(`cloudmessaging.messages.create`)의 `gole-fcm-sender`.
- 네이티브 변경이라 TestFlight 새 빌드가 필요하다(`expo-updates` 없음).

## Tasks

- [x] W1 웹 토큰 읽기·구독(shared/lib) + 등록·해제(app 셸)
- [x] M1 앱 토큰 메모이즈·WebView 주입·포그라운드 배너·탭 link 보강
- [x] S1 서버 기본 알림음 + 본문 단위 테스트
- [ ] V1 운영 FCM env 반영(Control → Secret Sync)
- [ ] V2 TestFlight 새 빌드 → 실기기에서 권한 허용·등록·수신·탭 이동 확인
