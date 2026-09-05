import * as Device from "expo-device";
import * as Notifications from "expo-notifications";

/**
 * 이미 발급된 OS 푸시 토큰을 조용히 읽는다. 권한을 <b>새로 요청하지 않는다</b>.
 *
 * 로그아웃 경로에서만 쓴다 — 그 순간에 권한 팝업을 띄우는 것은 뜬금없고, 권한이 없으면
 * 애초에 등록된 토큰도 없다.
 */
export async function getDevicePushTokenSilently(): Promise<string | null> {
  if (!Device.isDevice) {
    return null;
  }
  try {
    const permissions = await Notifications.getPermissionsAsync();
    if (!permissions.granted) {
      return null;
    }
    const devicePushToken = await Notifications.getDevicePushTokenAsync();
    return typeof devicePushToken.data === "string" ? devicePushToken.data : null;
  } catch {
    return null;
  }
}
