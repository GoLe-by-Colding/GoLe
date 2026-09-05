import { apiRequest } from "../../runtime";
import type {
  CurrentSignupPolicy,
  Me,
  RegisterResult,
  Session,
  SignupPolicyAcceptance,
} from "../model/types";

export function fetchCurrentSignupPolicy(signal?: AbortSignal): Promise<CurrentSignupPolicy> {
  return apiRequest<CurrentSignupPolicy>("/api/v1/policies/current", {
    cache: "no-store",
    ...(signal === undefined ? {} : { signal }),
  });
}

export function registerAccount(
  email: string,
  password: string,
  policyAcceptance: SignupPolicyAcceptance,
): Promise<RegisterResult> {
  return apiRequest<RegisterResult>("/api/v1/accounts", {
    method: "POST",
    body: { email, password, ...policyAcceptance },
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

export interface RefreshSessionResult extends Session {
  readonly rotated: boolean;
}

/** 유효한 쿠키 세션의 유휴 수명을 갱신하고, 회전 주기가 지났으면 서버 토큰을 교체한다. */
export function refreshSession(): Promise<RefreshSessionResult> {
  return apiRequest<RefreshSessionResult>("/api/v1/accounts/sessions/refresh", {
    method: "POST",
  });
}

/** 로그인한 계정의 비밀번호를 바꾸고 서버의 모든 세션을 폐기한다. */
export function changePassword(currentPassword: string, newPassword: string): Promise<void> {
  return apiRequest<void>("/api/v1/accounts/password", {
    method: "PUT",
    body: { currentPassword, newPassword },
  });
}

/** 계정 존재 여부와 무관하게 같은 응답을 받는다. */
export function requestPasswordReset(email: string): Promise<void> {
  return apiRequest<void>("/api/v1/accounts/password-reset", {
    method: "POST",
    body: { email },
  });
}

export function confirmPasswordReset(
  email: string,
  code: string,
  newPassword: string,
): Promise<void> {
  return apiRequest<void>("/api/v1/accounts/password-reset/confirmation", {
    method: "POST",
    body: { email, code, newPassword },
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
  signupPolicyAcceptance?: SignupPolicyAcceptance,
): Promise<{ readonly url: string }> {
  const query = new URLSearchParams({ redirectUri });
  if (signupPolicyAcceptance !== undefined) {
    query.set("termsVersion", signupPolicyAcceptance.termsVersion);
    query.set("privacyVersion", signupPolicyAcceptance.privacyVersion);
    query.set("termsAccepted", String(signupPolicyAcceptance.termsAccepted));
    query.set("privacyAcknowledged", String(signupPolicyAcceptance.privacyAcknowledged));
    query.set("minimumAgeConfirmed", String(signupPolicyAcceptance.minimumAgeConfirmed));
  }
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
