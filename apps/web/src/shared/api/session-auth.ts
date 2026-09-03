export const SESSION_STORAGE_KEY = "gole.session";
export const SESSION_CHANGE_EVENT = "gole:session-change";

export function readSessionAuthorization(): Readonly<Record<string, string>> {
  if (typeof window === "undefined") return {};
  try {
    const raw = window.localStorage.getItem(SESSION_STORAGE_KEY);
    const session = raw === null ? null : (JSON.parse(raw) as { sessionToken?: unknown });
    return typeof session?.sessionToken === "string" && session.sessionToken.length > 0
      ? { Authorization: `Bearer ${session.sessionToken}` }
      : {};
  } catch {
    return {};
  }
}

/**
 * 서버가 401로 세션 무효를 확정하면 화면 복원용 메타데이터도 함께 폐기한다.
 *
 * 인증 토큰은 HttpOnly 쿠키에만 있으므로 쿠키·Redis 세션이 만료된 뒤에도 이 메타데이터만
 * 남을 수 있다. 그대로 두면 헤더는 로그인 상태인데 모든 보호 API가 실패하는 모순이 생긴다.
 */
export function clearStoredSession(): void {
  if (typeof window === "undefined") return;
  const hadSession = window.localStorage.getItem(SESSION_STORAGE_KEY) !== null;
  window.localStorage.removeItem(SESSION_STORAGE_KEY);
  if (hadSession) {
    window.dispatchEvent(new Event(SESSION_CHANGE_EVENT));
  }
}
