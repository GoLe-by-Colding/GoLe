import * as SecureStore from "expo-secure-store";
import type { SessionStore } from "@gole/core";
import type { Session } from "@gole/core/user";

/** Keychain(iOS)·Keystore(Android)에 저장되는 키. 평문 파일에 두지 않는다. (R3.2) */
const SESSION_KEY = "gole.session";

/**
 * 메모리 캐시. 코어의 {@link SessionStore}가 동기라 SecureStore를 요청마다 읽을 수 없다.
 * 인터페이스를 비동기로 바꾸면 apiRequest와 모든 호출부가 전염되므로, 그 비용을 여기서 진다.
 */
let cached: Session | null = null;

/** useSyncExternalStore 구독자. 로그인·로그아웃이 화면에 즉시 반영되게 한다. */
const listeners = new Set<() => void>();

function emit(): void {
  for (const listener of listeners) {
    listener();
  }
}

/** 앱 시작 시 1회. 이 Promise가 끝나기 전에는 보호 API를 호출하지 않는다. */
export async function restoreSession(): Promise<void> {
  try {
    const raw = await SecureStore.getItemAsync(SESSION_KEY);
    cached = raw === null ? null : (JSON.parse(raw) as Session);
  } catch {
    // 키체인 접근 실패·손상된 값은 비로그인과 같게 다룬다. 앱이 뜨지 못하는 것보다 낫다.
    cached = null;
  }
  emit();
}

export function getSession(): Session | null {
  return cached;
}

export function subscribeSession(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export async function saveSession(session: Session): Promise<void> {
  cached = session;
  emit();
  await SecureStore.setItemAsync(SESSION_KEY, JSON.stringify(session));
}

export async function clearSession(): Promise<void> {
  cached = null;
  emit();
  await SecureStore.deleteItemAsync(SESSION_KEY);
}

export const nativeSessionStore: SessionStore = {
  readAuthorizationHeader: () => {
    const token = cached?.sessionToken ?? "";
    return token.length === 0 ? {} : { Authorization: `Bearer ${token}` };
  },

  /**
   * 서버가 401 INVALID_SESSION으로 세션 무효를 확정했을 때 코어가 호출한다. (R3.4)
   *
   * 캐시는 즉시 비우고 저장소 삭제는 기다리지 않는다 — 이 함수는 요청 실패 처리 경로에서
   * 동기로 불린다. 화면은 구독으로 곧바로 로그인 상태를 잃는다.
   */
  clear: () => {
    cached = null;
    emit();
    void SecureStore.deleteItemAsync(SESSION_KEY).catch(() => undefined);
  },
};
