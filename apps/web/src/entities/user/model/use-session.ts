"use client";

import { useCallback, useEffect, useSyncExternalStore } from "react";
import {
  clearSession,
  getServerSessionSnapshot,
  loadSession,
  saveSession,
  SESSION_REFRESH_INTERVAL_MS,
  subscribeSession,
} from "./session-store";
import { logout, refreshSession } from "@gole/core/user";
import type { Session } from "@gole/core/user";

export interface UseSessionResult {
  readonly session: Session | null;
  readonly signOut: () => void;
}

let refreshInFlight: Promise<void> | null = null;

function refreshBrowserSession(): Promise<void> {
  if (refreshInFlight !== null) return refreshInFlight;
  refreshInFlight = refreshSession()
    .then((refreshed) => {
      saveSession({
        accountId: refreshed.accountId,
        sessionToken: "",
        role: refreshed.role,
        refreshAfter: Date.now() + SESSION_REFRESH_INTERVAL_MS,
      });
    })
    .catch(() => undefined)
    .finally(() => {
      refreshInFlight = null;
    });
  return refreshInFlight;
}

/**
 * 클라이언트 세션 구독 훅. 외부 저장소(localStorage)를 useSyncExternalStore로 구독해
 * 같은 탭/다른 탭/포커스 복귀 시 일관되게 동기화한다.
 */
export function useSession(): UseSessionResult {
  const session = useSyncExternalStore(subscribeSession, loadSession, getServerSessionSnapshot);

  useEffect(() => {
    if (session?.refreshAfter === undefined) return;
    const delay = Math.max(0, session.refreshAfter - Date.now());
    const timer = window.setTimeout(() => {
      void refreshBrowserSession();
    }, delay);
    return () => window.clearTimeout(timer);
  }, [session?.accountId, session?.refreshAfter]);

  const signOut = useCallback(() => {
    const current = loadSession();
    if (current !== null) {
      // 서버측 세션도 폐기(best-effort). 실패해도 로컬은 정리한다.
      void logout(current.sessionToken).catch(() => undefined);
    }
    clearSession();
  }, []);

  return { session, signOut };
}
