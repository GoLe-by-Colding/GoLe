# 설계

백엔드 FcmPushSenderAdapter 계약을 유지한다. iOS에서만 React Native Firebase Messaging getToken을 사용하며 Expo Go에서는 네이티브 모듈을 import하지 않는다. Android는 expo-notifications의 FCM 토큰을 유지한다. 권한 요청 실패도 null로 환원한다.

Expo 설정에 Firebase app/messaging 플러그인과 iOS static framework 설정을 추가한다. 기존 Google 서비스 설정 파일을 사용하되 값은 문서/로그에 복사하지 않는다. 별도 네이티브 빌드가 필요하다.

근거: https://docs.expo.dev/versions/v57.0.0/sdk/notifications/ 와 https://rnfirebase.io/messaging/server-integration . getDevicePushTokenAsync는 iOS에서 APNs, Android에서 FCM을 반환한다.
