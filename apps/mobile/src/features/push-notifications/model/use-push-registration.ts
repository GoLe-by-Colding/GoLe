import { useEffect } from "react";
import * as Notifications from "expo-notifications";
import { useRouter } from "expo-router";
import { registerDeviceToken } from "@gole/core/notification";
import { getDevicePushToken } from "../lib/device-push-token";

/**
 * 로그인 상태에서 단말 토큰을 등록하고, 푸시를 탭했을 때 해당 화면으로 보낸다. (R8.1, R8.4)
 *
 * 등록 실패를 화면에 드러내지 않는다 — 사용자가 할 수 있는 일이 없고, 인앱 알림은 그대로 온다.
 */
export function usePushRegistration(isSignedIn: boolean): void {
  const router = useRouter();

  useEffect(() => {
    if (!isSignedIn) {
      return;
    }
    let active = true;

    void (async () => {
      const pushToken = await getDevicePushToken();
      if (!active || pushToken === null) {
        return;
      }
      try {
        await registerDeviceToken(pushToken.token, pushToken.platform);
      } catch {
        // 다음 실행에서 다시 시도한다. 토큰 등록은 멱등하다.
      }
    })();

    return () => {
      active = false;
    };
  }, [isSignedIn]);

  useEffect(() => {
    // 백엔드가 link를 data 페이로드로 싣는다. notification이 아니라 data여야
    // 포그라운드·백그라운드 어느 상태에서 받아도 같은 값을 읽을 수 있다.
    const subscription = Notifications.addNotificationResponseReceivedListener((response) => {
      const link = response.notification.request.content.data?.["link"];
      if (typeof link === "string" && link.startsWith("/")) {
        // 앱 내부 경로만 따른다. 외부 URL을 그대로 열면 푸시가 피싱 통로가 된다.
        router.push(link as Parameters<typeof router.push>[0]);
      }
    });
    return () => subscription.remove();
  }, [router]);
}
