import { apiRequest } from "@shared/api";
import type { Me, RegisterResult, Session } from "../model/types";

export function registerAccount(email: string, password: string): Promise<RegisterResult> {
  return apiRequest<RegisterResult>("/api/v1/accounts", {
    method: "POST",
    body: { email, password },
  });
}

export function verifyEmail(email: string, code: string): Promise<void> {
  return apiRequest<void>("/api/v1/accounts/verification", {
    method: "POST",
    body: { email, code },
  });
}

export function resendVerificationEmail(email: string): Promise<void> {
  return apiRequest<void>("/api/v1/accounts/verification/resend", {
    method: "POST",
    body: { email },
  });
}

export function signIn(email: string, password: string): Promise<Session> {
  return apiRequest<Session>("/api/v1/accounts/sessions", {
    method: "POST",
    body: { email, password },
  });
}

const OAUTH_BASE = "/api/v1/auth/oauth";

/** 설정(활성)된 소셜 provider 키 목록(google/kakao/naver). */
export function fetchSocialProviders(signal?: AbortSignal): Promise<readonly string[]> {
  return apiRequest<readonly string[]>(`${OAUTH_BASE}/providers`, {
    cache: "no-store",
    ...(signal === undefined ? {} : { signal }),
  });
}

/** provider 동의 화면 URL을 받아온다. state는 서버가 발급한다. */
export function fetchSocialAuthorizeUrl(
  provider: string,
  redirectUri: string,
): Promise<{ readonly url: string }> {
  const query = new URLSearchParams({ redirectUri }).toString();
  return apiRequest<{ readonly url: string }>(`${OAUTH_BASE}/${provider}/authorize-url?${query}`, {
    cache: "no-store",
  });
}

/** OAuth code를 교환해 세션을 발급받는다. state는 서버가 검증한다(CSRF).
 *  신규 계정 여부(newAccount)를 함께 반환해 콜백에서 온보딩 분기에 사용한다. */
export async function socialCallback(
  provider: string,
  code: string,
  redirectUri: string,
  state: string,
): Promise<SocialCallbackResult> {
  const res = await apiRequest<SocialCallbackResponse>(`${OAUTH_BASE}/${provider}/callback`, {
    method: "POST",
    body: { code, redirectUri, state },
  });
  return {
    session: { accountId: res.accountId, sessionToken: res.sessionToken, role: res.role },
    newAccount: res.newAccount,
  };
}

interface SocialCallbackResponse {
  readonly accountId: string;
  readonly sessionToken: string;
  readonly role: "USER" | "ADMIN";
  readonly newAccount: boolean;
}

export interface SocialCallbackResult {
  readonly session: Session;
  readonly newAccount: boolean;
}

/** 로그아웃: HttpOnly 쿠키 또는 외부 API용 Bearer 세션을 서버에서 폐기한다. */
export function logout(sessionToken: string): Promise<void> {
  return apiRequest<void>("/api/v1/accounts/sessions", {
    method: "DELETE",
    headers: sessionToken.length > 0 ? { Authorization: `Bearer ${sessionToken}` } : {},
  });
}

/** 현재 로그인 사용자 정보(이메일/권한)를 조회한다. */
export function fetchMe(sessionToken: string): Promise<Me> {
  return apiRequest<Me>("/api/v1/accounts/me", {
    cache: "no-store",
    headers: sessionToken.length > 0 ? { Authorization: `Bearer ${sessionToken}` } : {},
  });
}
