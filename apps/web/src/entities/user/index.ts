/**
 * `user` 엔티티 파사드.
 *
 * 모델·API는 `@gole/core/user`에 있다(웹·앱 공유). 여기서는 그것을 그대로 다시 내보내고,
 * 이 슬라이스의 웹 전용 부분만 덧붙인다. 상위 레이어는 이 경로를 계속 그대로 쓴다.
 */
export * from "@gole/core/user";
export type { OnboardingStep } from "./model/onboarding-steps";
export {
  ONBOARDING_STEPS,
  onboardingSteps,
  isStepCompleted,
  nextIncompleteStep,
  isOnboardingComplete,
  withStepCompleted,
} from "./model/onboarding-steps";
export { saveSession, loadSession, clearSession } from "./model/session-store";
export { clearAccountBrowserStorage } from "./model/account-browser-storage";
export { useSession } from "./model/use-session";
export type { UseSessionResult } from "./model/use-session";
export {
  isThirdPartyProvisionConsentCancelledError,
  isThirdPartyProvisionConsentRequiredError,
  THIRD_PARTY_PROVISION_CONSENT_REQUIRED_CODE,
  THIRD_PARTY_PROVISION_VERSION_STALE_CODE,
  useThirdPartyProvisionConsent,
} from "./model/use-third-party-provision-consent";
export type {
  ThirdPartyProvisionConsentDialogController,
  UseThirdPartyProvisionConsentResult,
} from "./model/use-third-party-provision-consent";
export { ThirdPartyProvisionConsentDialog } from "./ui/third-party-provision-consent-dialog";
export type { ThirdPartyProvisionConsentDialogProps } from "./ui/third-party-provision-consent-dialog";
export { ThirdPartyProvisionNotice } from "./ui/third-party-provision-notice";
export type { ThirdPartyProvisionNoticeProps } from "./ui/third-party-provision-notice";
export {
  validateNickname,
  validatePhoneNumber,
  normalizePhoneNumber,
} from "./lib/onboarding-rules";
