import { useEffect } from "react";
import * as Notifications from "expo-notifications";
import { useRouter } from "expo-router";
import { registerDeviceToken } from "@gole/core/notification";
import { getDevicePushToken } from "../lib/device-push-token";
import { notificationRoute } from "@/shared/lib";

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
      const link = notificationRoute(notificationLink(response));
      if (link !== null) {
        // 앱 내부 경로만 따른다. 외부 URL을 그대로 열면 푸시가 피싱 통로가 된다.
        router.push(link as Parameters<typeof router.push>[0]);
      }
    });
    return () => subscription.remove();
  }, [router]);
}

/**
 * 앱이 열려 있을 때도 알림 배너를 띄운다. 기본값은 포그라운드 알림을 숨겨서, 앱을 보고 있는 동안 온
 * 단종·새 매물 알림을 놓친다. 앱 시작 시 한 번 부른다.
 */
export function configureForegroundNotifications(): void {
  Notifications.setNotificationHandler({
    handleNotification: async () => ({
      shouldShowBanner: true,
      shouldShowList: true,
      shouldPlaySound: true,
      shouldSetBadge: false,
    }),
  });
}

/**
 * 탭한 알림의 link. FCM 이 보낸 data 는 플랫폼마다 다른 곳에 실려 온다 — iOS 는 APNs userInfo
 * (`trigger.payload`), Android 는 Firebase RemoteMessage(`trigger.remoteMessage.data`). Expo 형식
 * 푸시는 `content.data` 에 온다. 먼저 찾은 값을 쓰고, 검증은 notificationRoute 가 한다.
 */
function notificationLink(response: Notifications.NotificationResponse): unknown {
  const { content, trigger } = response.notification.request;
  const fromContent: unknown = content.data?.["link"];
  if (fromContent !== undefined) return fromContent;
  const push = trigger as {
    readonly payload?: Record<string, unknown>;
    readonly remoteMessage?: { readonly data?: Record<string, unknown> };
  } | null;
  return push?.payload?.["link"] ?? push?.remoteMessage?.data?.["link"];
}
