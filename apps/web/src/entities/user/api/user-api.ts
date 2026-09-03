import { apiRequest } from "@shared/api";
import type {
  CurrentSignupPolicy,
  InterestTag,
  Me,
  OnboardingStatus,
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
    session: {
      accountId: res.accountId,
      sessionToken: res.sessionToken,
      role: res.role,
      onboardingRequired: res.onboardingRequired,
    },
    newAccount: res.newAccount,
  };
}

interface SocialCallbackResponse {
  readonly accountId: string;
  readonly sessionToken: string;
  readonly role: "USER" | "ADMIN";
  readonly newAccount: boolean;
  readonly onboardingRequired: boolean;
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

// --- 최초 로그인 온보딩 (onboarding R2~R7) ---------------------------------
// 각 단계는 성공 즉시 서버에 영속화된다(D1). 중간 이탈해도 다음 진입 때
// fetchOnboardingStatus가 알려주는 미완료 단계부터 재개한다.

const ONBOARDING_BASE = "/api/v1/accounts/me/onboarding";

/** 단계별 완료 여부·강제 대상 여부를 조회한다(R2). 재개 판단의 유일한 근거다. */
export function fetchOnboardingStatus(signal?: AbortSignal): Promise<OnboardingStatus> {
  return apiRequest<OnboardingStatus>(ONBOARDING_BASE, {
    cache: "no-store",
    ...(signal === undefined ? {} : { signal }),
  });
}

/**
 * 선택 가능한 관심 태그 목록(D8의 curated 상수).
 *
 * 경로의 `account`가 단수인 것은 스펙(D8) 표기를 그대로 따른 것이다 —
 * 나머지 온보딩 엔드포인트는 복수(`accounts`)다.
 */
export async function fetchInterestTags(signal?: AbortSignal): Promise<readonly InterestTag[]> {
  // 최상위가 배열이 아니라 객체다 — 나중에 필드를 덧붙일 수 있게 서버가 감싸서 준다.
  const res = await apiRequest<{ readonly tags: readonly InterestTag[] }>(
    "/api/v1/account/interest-tags",
    {
      cache: "no-store",
      ...(signal === undefined ? {} : { signal }),
    },
  );
  return res.tags;
}

/** 닉네임 설정(R3). 2~12자·한글/영문/숫자·중복 불가는 서버가 최종 판정한다(D9). */
export function setNickname(nickname: string): Promise<void> {
  return apiRequest<void>(`${ONBOARDING_BASE}/nickname`, {
    method: "PUT",
    body: { nickname },
  });
}

/** 인증코드 발송 결과(R4). 서버가 마스킹한 번호와 코드 유효시간을 돌려준다. */
export interface PhoneVerificationRequestResult {
  readonly maskedPhoneNumber: string;
  readonly expiresInSeconds: number;
}

/** 전화번호 인증코드 발송 요청(R4). 쿨다운·일일 한도·번호 중복은 서버가 판정한다. */
export function requestPhoneVerification(
  phoneNumber: string,
): Promise<PhoneVerificationRequestResult> {
  return apiRequest<PhoneVerificationRequestResult>(`${ONBOARDING_BASE}/phone/verification`, {
    method: "POST",
    body: { phoneNumber },
  });
}

/**
 * 전화번호 인증코드 확인(R5). 성공 시 서버가 phoneVerifiedAt을 저장한다.
 *
 * 번호는 서버가 발송 시점에 세션과 함께 들고 있으므로 코드만 보낸다.
 */
export function confirmPhoneVerification(code: string): Promise<void> {
  return apiRequest<void>(`${ONBOARDING_BASE}/phone/verification/confirm`, {
    method: "POST",
    body: { code },
  });
}

/** 관심 태그 선택(R6). 값은 label이 아니라 key다. 1~5개 범위는 서버가 재검증한다. */
export function setInterestTags(tags: readonly string[]): Promise<void> {
  return apiRequest<void>(`${ONBOARDING_BASE}/interest-tags`, {
    method: "PUT",
    body: { tags: [...tags] },
  });
}

/** 약관 동의 제출(R7). 개인정보 동의는 필수이고 false면 서버가 거부한다. */
export function submitOnboardingConsent(
  privacyConsented: boolean,
  marketingConsented: boolean,
): Promise<void> {
  return apiRequest<void>(`${ONBOARDING_BASE}/consent`, {
    method: "POST",
    body: { privacyConsented, marketingConsented },
  });
}
