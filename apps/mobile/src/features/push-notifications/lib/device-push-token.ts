import * as Device from "expo-device";
import Constants, { ExecutionEnvironment } from "expo-constants";
import * as Notifications from "expo-notifications";
import { Platform } from "react-native";
import type { DevicePlatform } from "@gole/core/notification";

export interface DevicePushToken {
  readonly token: string;
  readonly platform: DevicePlatform;
}

/**
 * OS 푸시 토큰을 얻는다. 권한이 없거나 받을 수 없으면 `null`이다 — 던지지 않는다.
 *
 * 백엔드는 FCM HTTP v1 등록 토큰만 받는다. Expo의 iOS 네이티브 토큰은 APNs이므로
 * iOS에서는 Firebase Messaging에서 FCM 등록 토큰을 따로 얻는다.
 *
 * 현재 앱 정책상 실기기 개발/배포 빌드에서만 등록한다. Expo Go에서는 모듈을 로드하지 않는다.
 */
export async function getDevicePushToken(): Promise<DevicePushToken | null> {
  if (
    !Device.isDevice ||
    Platform.OS === "web" ||
    Constants.executionEnvironment === ExecutionEnvironment.StoreClient
  ) {
    return null;
  }

  try {
    const existing = await Notifications.getPermissionsAsync();
    const granted =
      existing.granted ||
      (existing.canAskAgain && (await Notifications.requestPermissionsAsync()).granted);
    if (!granted) return null;

    if (Platform.OS === "ios") {
      const { getMessaging, getToken, registerDeviceForRemoteMessages } =
        await import("@react-native-firebase/messaging");
      const messaging = getMessaging();
      if (!messaging.isDeviceRegisteredForRemoteMessages) {
        await registerDeviceForRemoteMessages(messaging);
      }
      const token = await getToken(messaging);
      return token.trim() ? { token, platform: "IOS" } : null;
    }

    const devicePushToken = await Notifications.getDevicePushTokenAsync();
    return typeof devicePushToken.data === "string" && devicePushToken.data.trim()
      ? { token: devicePushToken.data, platform: "ANDROID" }
      : null;
  } catch {
    // 개발 빌드가 아니거나(Expo Go) Firebase 설정이 없으면 여기서 실패한다.
    // 푸시가 없다고 앱이 못 뜰 이유는 없다.
    return null;
  }
}
