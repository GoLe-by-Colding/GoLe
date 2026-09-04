export type {
  Session,
  RegisterResult,
  Me,
  OnboardingStatus,
  InterestTag,
  CurrentSignupPolicy,
  SignupPolicyAcceptance,
} from "./model/types";
export type { PhoneVerificationRequestResult } from "./api/user-api";
export type { OnboardingStep } from "./model/onboarding-steps";
export {
  ONBOARDING_STEPS,
  isStepCompleted,
  nextIncompleteStep,
  isOnboardingComplete,
  withStepCompleted,
} from "./model/onboarding-steps";
export { INTEREST_TAG_MIN, INTEREST_TAG_MAX } from "./model/types";
export { saveSession, loadSession, clearSession } from "./model/session-store";
export { useSession } from "./model/use-session";
export type { UseSessionResult } from "./model/use-session";
export {
  fetchCurrentSignupPolicy,
  registerAccount,
  verifyEmail,
  resendVerificationEmail,
  signIn,
} from "./api/user-api";
export { changePassword, requestPasswordReset, confirmPasswordReset } from "./api/user-api";
export { fetchSocialProviders, fetchSocialAuthorizeUrl, socialCallback } from "./api/user-api";
export type { SocialCallbackResult } from "./api/user-api";
export { logout, refreshSession } from "./api/user-api";
export type { RefreshSessionResult } from "./api/user-api";
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
