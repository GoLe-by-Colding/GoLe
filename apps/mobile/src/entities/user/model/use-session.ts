import { useCallback, useSyncExternalStore } from "react";
import { logout } from "@gole/core/user";
import type { Session } from "@gole/core/user";
import { clearSession, getSession, subscribeSession } from "@/shared/api";
import { unregisterDeviceToken } from "@gole/core/notification";
import { getDevicePushTokenSilently } from "../lib/device-token-hint";

export interface UseSessionResult {
  readonly session: Session | null;
  readonly signOut: () => Promise<void>;
}

/**
 * 세션 구독 훅. 저장소를 외부 스토어로 구독해 로그인·로그아웃이 모든 화면에 즉시 반영된다.
 *
 * 웹은 localStorage와 DOM 이벤트를 구독하지만, 앱은 SecureStore를 요청마다 읽을 수 없어
 * 메모리 캐시를 진실의 원천으로 삼는다.
 */
export function useSession(): UseSessionResult {
  const session = useSyncExternalStore(subscribeSession, getSession, getSession);

  const signOut = useCallback(async () => {
    const current = getSession();

    // 단말 토큰을 먼저 지운다. 세션이 사라진 뒤에는 인증이 필요한 이 요청을 보낼 수 없고,
    // 남겨두면 로그아웃한 기기로 다음 사용자의 알림이 간다.
    const pushToken = await getDevicePushTokenSilently();
    if (pushToken !== null) {
      await unregisterDeviceToken(pushToken).catch(() => undefined);
    }

    if (current !== null) {
      // 서버 세션도 폐기(best-effort). 실패해도 로컬은 정리한다.
      await logout(current.sessionToken).catch(() => undefined);
    }
    await clearSession();
  }, []);

  return { session, signOut };
}
