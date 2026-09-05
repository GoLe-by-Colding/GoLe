import type { Session } from "@gole/core/user";
import { clearStoredSession, SESSION_CHANGE_EVENT, SESSION_STORAGE_KEY } from "@shared/api";

export const SESSION_REFRESH_INTERVAL_MS = 12 * 60 * 60 * 1_000;

/**
 * 클라이언트에는 계정 ID·권한 등 화면 상태만 저장한다.
 * 인증 토큰은 서버가 발급한 HttpOnly 쿠키에만 보관해 JavaScript에서 읽을 수 없게 한다.
 * useSyncExternalStore와 연동되도록 subscribe/getSnapshot을 제공하고,
 * 같은 탭에서도 변경이 전파되도록 커스텀 이벤트를 발행한다.
 */
// 스냅샷 캐시: 동일 raw 문자열이면 같은 객체 참조를 반환해 무한 렌더를 방지한다.
let cachedRaw: string | null = null;
let cachedSession: Session | null = null;

function emitChange(): void {
  window.dispatchEvent(new Event(SESSION_CHANGE_EVENT));
}

export function saveSession(session: Session): void {
  if (typeof window === "undefined") {
    return;
  }
  // 인증 토큰은 HttpOnly 쿠키에만 보관한다. 로컬 저장소에는 화면 복원에 필요한 비민감 메타데이터만 둔다.
  window.localStorage.setItem(
    SESSION_STORAGE_KEY,
    JSON.stringify({
      ...session,
      sessionToken: "",
      refreshAfter: session.refreshAfter ?? Date.now() + SESSION_REFRESH_INTERVAL_MS,
    }),
  );
  emitChange();
}

export function clearSession(): void {
  if (typeof window === "undefined") {
    return;
  }
  clearStoredSession();
}

export function loadSession(): Session | null {
  if (typeof window === "undefined") {
    return null;
  }
  const raw = window.localStorage.getItem(SESSION_STORAGE_KEY);
  if (raw === cachedRaw) {
    return cachedSession;
  }
  cachedRaw = raw;
  if (raw === null) {
    cachedSession = null;
    return null;
  }
  try {
    cachedSession = JSON.parse(raw) as Session;
  } catch {
    cachedSession = null;
  }
  return cachedSession;
}

/** useSyncExternalStore용 구독: 같은 탭(커스텀 이벤트)·다른 탭(storage)·포커스 복귀를 감지. */
export function subscribeSession(onChange: () => void): () => void {
  if (typeof window === "undefined") {
    return () => undefined;
  }
  window.addEventListener(SESSION_CHANGE_EVENT, onChange);
  window.addEventListener("storage", onChange);
  window.addEventListener("focus", onChange);
  return () => {
    window.removeEventListener(SESSION_CHANGE_EVENT, onChange);
    window.removeEventListener("storage", onChange);
    window.removeEventListener("focus", onChange);
  };
}

/** SSR 스냅샷: 서버에서는 항상 비로그인. */
export function getServerSessionSnapshot(): Session | null {
  return null;
}
