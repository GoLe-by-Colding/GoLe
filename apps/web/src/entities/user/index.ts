export type { Session, RegisterResult, Me, OnboardingStatus, InterestTag } from "./model/types";
export { INTEREST_TAG_MIN, INTEREST_TAG_MAX } from "./model/types";
export { saveSession, loadSession, clearSession } from "./model/session-store";
export { useSession } from "./model/use-session";
export type { UseSessionResult } from "./model/use-session";
export { registerAccount, verifyEmail, resendVerificationEmail, signIn } from "./api/user-api";
export { fetchSocialProviders, fetchSocialAuthorizeUrl, socialCallback } from "./api/user-api";
export type { SocialCallbackResult } from "./api/user-api";
export { logout } from "./api/user-api";
export { fetchMe } from "./api/user-api";
export {
  fetchOnboardingStatus,
  fetchInterestTags,
  setNickname,
  requestPhoneVerification,
  confirmPhoneVerification,
  setInterestTags,
  submitOnboardingConsent,
} from "./api/user-api";
export {
  validateNickname,
  validatePhoneNumber,
  normalizePhoneNumber,
} from "./lib/onboarding-rules";
