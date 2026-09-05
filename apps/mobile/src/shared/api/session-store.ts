import * as SecureStore from "expo-secure-store";
import type { SessionStore } from "@gole/core";

/** Keychain(iOS)·Keystore(Android)에 저장되는 키. 평문 파일에 두지 않는다. */
const SESSION_KEY = "gole.session.token";

/**
 * 메모리 캐시. 코어의 {@link SessionStore}가 동기라 SecureStore를 요청마다 읽을 수 없다.
 * 인터페이스를 비동기로 바꾸면 apiRequest와 모든 호출부가 전염되므로, 그 비용을 여기서 진다.
 */
let cachedToken: string | null = null;

/** 앱 시작 시 1회. 이 Promise가 끝나기 전에는 보호 API를 호출하지 않는다. */
export async function restoreSession(): Promise<void> {
  try {
    cachedToken = await SecureStore.getItemAsync(SESSION_KEY);
  } catch {
    // 키체인 접근 실패는 비로그인과 같게 다룬다. 앱이 뜨지 못하는 것보다 낫다.
    cachedToken = null;
  }
}

export async function saveSessionToken(token: string): Promise<void> {
  cachedToken = token;
  await SecureStore.setItemAsync(SESSION_KEY, token);
}

export async function clearSessionToken(): Promise<void> {
  cachedToken = null;
  await SecureStore.deleteItemAsync(SESSION_KEY);
}

export function currentSessionToken(): string | null {
  return cachedToken;
}

export const nativeSessionStore: SessionStore = {
  readAuthorizationHeader: () =>
    cachedToken === null || cachedToken.length === 0
      ? {}
      : { Authorization: `Bearer ${cachedToken}` },

  /**
   * 서버가 세션 무효를 확정했을 때 호출된다. 캐시는 즉시 비우고 저장소 삭제는 기다리지 않는다 —
   * 이 함수는 요청 실패 처리 경로에서 동기로 불린다.
   */
  clear: () => {
    cachedToken = null;
    void SecureStore.deleteItemAsync(SESSION_KEY).catch(() => undefined);
  },
};
