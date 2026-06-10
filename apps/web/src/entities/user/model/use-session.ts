"use client";

import { useCallback, useSyncExternalStore } from "react";
import {
  clearSession,
  getServerSessionSnapshot,
  loadSession,
  subscribeSession,
} from "./session-store";
import { logout } from "../api/user-api";
import type { Session } from "./types";

export interface UseSessionResult {
  readonly session: Session | null;
  readonly signOut: () => void;
}

/**
 * 클라이언트 세션 구독 훅. 외부 저장소(localStorage)를 useSyncExternalStore로 구독해
 * 같은 탭/다른 탭/포커스 복귀 시 일관되게 동기화한다.
 */
export function useSession(): UseSessionResult {
  const session = useSyncExternalStore(subscribeSession, loadSession, getServerSessionSnapshot);

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
