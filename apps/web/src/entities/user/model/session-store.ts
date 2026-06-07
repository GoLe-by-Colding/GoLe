import type { Session } from "./types";

/**
 * 클라이언트 세션 저장소. 현재는 localStorage 기반(추후 httpOnly 쿠키로 강화 가능).
 * SSR 환경에서 안전하도록 window 가드를 둔다.
 */
const STORAGE_KEY = "gole.session";

export function saveSession(session: Session): void {
  if (typeof window === "undefined") {
    return;
  }
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
}

export function loadSession(): Session | null {
  if (typeof window === "undefined") {
    return null;
  }
  const raw = window.localStorage.getItem(STORAGE_KEY);
  if (raw === null) {
    return null;
  }
  try {
    return JSON.parse(raw) as Session;
  } catch {
    return null;
  }
}

export function clearSession(): void {
  if (typeof window === "undefined") {
    return;
  }
  window.localStorage.removeItem(STORAGE_KEY);
}
