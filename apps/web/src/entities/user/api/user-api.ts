import { apiRequest } from "@shared/api";
import type { RegisterResult, Session } from "../model/types";

export function registerAccount(
  email: string,
  password: string,
): Promise<RegisterResult> {
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

/** provider 동의 화면 URL을 받아온다. */
export function fetchSocialAuthorizeUrl(
  provider: string,
  redirectUri: string,
  state: string,
): Promise<{ readonly url: string }> {
  const query = new URLSearchParams({ redirectUri, state }).toString();
  return apiRequest<{ readonly url: string }>(
    `${OAUTH_BASE}/${provider}/authorize-url?${query}`,
    { cache: "no-store" },
  );
}

/** OAuth code를 교환해 세션을 발급받는다. */
export function socialCallback(
  provider: string,
  code: string,
  redirectUri: string,
): Promise<Session> {
  return apiRequest<Session>(`${OAUTH_BASE}/${provider}/callback`, {
    method: "POST",
    body: { code, redirectUri },
  });
}
