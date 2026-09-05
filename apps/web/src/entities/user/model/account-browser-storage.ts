import { clearStoredSession } from "@shared/api";

const ACCOUNT_LOCAL_STORAGE_KEYS = ["gole.buyer-phone", "gole.buyer-name"] as const;
const PAYMENT_ATTEMPT_KEY_PREFIX = "gole.order.payment-attempted:";

/**
 * 탈퇴가 접수된 계정의 브라우저 잔존 데이터를 정리한다.
 *
 * 다른 서비스·화면의 설정까지 지우지 않도록 전체 storage를 비우지 않고, 계정 메타데이터와
 * 구매 과정에서 사용자가 직접 입력한 값 및 결제 시도 표식만 정확히 제거한다.
 */
export function clearAccountBrowserStorage(): void {
  if (typeof window === "undefined") return;

  try {
    clearStoredSession();
  } catch {
    // 브라우저 정책이 storage 접근을 막아도 나머지 저장소 정리는 각각 시도한다.
  }

  for (const key of ACCOUNT_LOCAL_STORAGE_KEYS) {
    try {
      window.localStorage.removeItem(key);
    } catch {
      // 한 키의 실패가 다른 키 정리를 막지 않게 한다.
    }
  }

  try {
    const paymentAttemptKeys: string[] = [];
    for (let index = 0; index < window.sessionStorage.length; index += 1) {
      const key = window.sessionStorage.key(index);
      if (key?.startsWith(PAYMENT_ATTEMPT_KEY_PREFIX)) paymentAttemptKeys.push(key);
    }
    for (const key of paymentAttemptKeys) window.sessionStorage.removeItem(key);
  } catch {
    // 프라이버시 모드 등에서 sessionStorage 접근이 제한될 수 있다.
  }
}
