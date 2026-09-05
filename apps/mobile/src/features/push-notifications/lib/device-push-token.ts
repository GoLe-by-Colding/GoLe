import * as Device from "expo-device";
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
 * <b>Expo 푸시 토큰이 아니라 네이티브 토큰(FCM)을 쓴다.</b> 백엔드가 FCM HTTP v1으로 직접
 * 보내므로 Expo 푸시 서비스를 경유할 이유가 없고, 경유하면 장애 지점이 하나 더 생긴다.
 *
 * <b>시뮬레이터·에뮬레이터에서는 항상 `null`이다.</b> 푸시 토큰은 실기기에만 발급된다.
 */
export async function getDevicePushToken(): Promise<DevicePushToken | null> {
  if (!Device.isDevice) {
    return null;
  }

  const existing = await Notifications.getPermissionsAsync();
  const granted =
    existing.granted ||
    (existing.canAskAgain && (await Notifications.requestPermissionsAsync()).granted);
  if (!granted) {
    return null;
  }

  try {
    const devicePushToken = await Notifications.getDevicePushTokenAsync();
    const platform: DevicePlatform = Platform.OS === "ios" ? "IOS" : "ANDROID";
    return typeof devicePushToken.data === "string"
      ? { token: devicePushToken.data, platform }
      : null;
  } catch {
    // 개발 빌드가 아니거나(Expo Go) Firebase 설정이 없으면 여기서 실패한다.
    // 푸시가 없다고 앱이 못 뜰 이유는 없다.
    return null;
  }
}
