"use client";

import { useCallback, useEffect, useState } from "react";
import { fetchMe } from "@gole/core/user";
import { ApiError } from "@shared/api";
import { useSession } from "./use-session";

/**
 * 관리자 권한 판정 상태.
 *
 * `localStorage`의 role은 사용자가 직접 바꿀 수 있으므로 **판정 근거로 쓰지 않는다**.
 * 항상 서버(`GET /api/v1/accounts/me`)가 인증 토큰(HttpOnly 쿠키 또는 Bearer)으로
 * 확인해 준 결과만 신뢰하고, 확인 전·확인 실패는 모두 닫힌 쪽으로 떨어진다.
 */
export type AdminAccess = "checking" | "granted" | "unauthenticated" | "forbidden" | "error";

export interface UseAdminAccessOptions {
  /**
   * `false`면 서버에 묻지 않고 닫힌 쪽(`forbidden`)에 둔다. 모든 화면에 깔리는 관리자 바처럼
   * 일반 사용자에게 매 화면 `/me`를 부르지 않으려는 곳에서 로컬 role을 "물어볼지"의 힌트로만 쓴다.
   */
  readonly enabled?: boolean;
}

export interface UseAdminAccessResult {
  readonly access: AdminAccess;
  /** `error`에서 서버 확인을 다시 한다. */
  readonly retry: () => void;
}

/**
 * 지금 세션이 서버가 확인한 ADMIN인지. 콘솔 셸과 관리자 바가 같은 판정을 쓴다 —
 * 한쪽만 서버 확인으로 고치고 다른 쪽이 로컬 role을 믿던 결함이 다시 생기지 않게 한곳에 둔다.
 */
export function useAdminAccess({
  enabled = true,
}: UseAdminAccessOptions = {}): UseAdminAccessResult {
  const { session } = useSession();
  const accountId = session?.accountId ?? null;
  const token = session?.sessionToken ?? "";
  // 확인 결과를 확인 대상(계정 + 토큰)과 함께 보관해, 세션이 바뀌면 즉시 무효가 되게 한다.
  const identity = accountId === null ? null : JSON.stringify([accountId, token]);
  const [verified, setVerified] = useState<{
    readonly identity: string;
    readonly access: Exclude<AdminAccess, "checking">;
  } | null>(null);
  const [attempt, setAttempt] = useState(0);

  const access: AdminAccess =
    identity === null
      ? "unauthenticated"
      : !enabled
        ? "forbidden"
        : verified !== null && verified.identity === identity
          ? verified.access
          : "checking";

  // 서버 권한 확인. 로컬 세션이 아예 없거나 묻지 않기로 했으면 요청 자체를 하지 않는다.
  useEffect(() => {
    if (identity === null || !enabled) {
      return;
    }
    let active = true;
    const settle = (result: Exclude<AdminAccess, "checking">): void => {
      if (active) {
        setVerified({ identity, access: result });
      }
    };
    void fetchMe(token)
      .then((me) => settle(me.role === "ADMIN" ? "granted" : "forbidden"))
      .catch((cause: unknown) => {
        if (cause instanceof ApiError && cause.status === 401) {
          settle("unauthenticated");
          return;
        }
        if (cause instanceof ApiError && cause.status === 403) {
          settle("forbidden");
          return;
        }
        // 확인 자체가 실패하면 열어주지 않는다(fail closed).
        settle("error");
      });
    return () => {
      active = false;
    };
  }, [identity, token, enabled, attempt]);

  const retry = useCallback(() => {
    setVerified(null);
    setAttempt((value) => value + 1);
  }, []);

  return { access, retry };
}
