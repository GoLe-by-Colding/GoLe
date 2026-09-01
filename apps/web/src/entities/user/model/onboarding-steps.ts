import type { OnboardingStatus } from "./types";

/**
 * 온보딩 단계 판정. 배열 순서가 곧 위저드 진행 순서다.
 *
 * 완료 여부는 응답의 `required`가 아니라 **단계 플래그 네 개에서 직접** 파생시킨다.
 * `required`는 legacyExempt 계정에서 면제를 반영해 false로 내려올 수 있어서, 그 값에
 * 기대면 "면제 계정이 자발적으로 완성하러 들어온" 경우를 완료로 오인한다.
 */
export type OnboardingStep = "nickname" | "phone" | "interestTags" | "consent";

export const ONBOARDING_STEPS: readonly OnboardingStep[] = [
  "nickname",
  "phone",
  "interestTags",
  "consent",
];

/** 상태 응답에서 해당 단계의 완료 여부를 읽는다. */
export function isStepCompleted(status: OnboardingStatus, step: OnboardingStep): boolean {
  switch (step) {
    case "nickname":
      return status.nicknameCompleted;
    case "phone":
      return status.phoneCompleted;
    case "interestTags":
      return status.interestTagsCompleted;
    case "consent":
      // 동의 단계에는 완료 플래그가 따로 없다. 개인정보 동의만 필수라 이 값이 곧 완료
      // 여부이고, 선택 항목인 마케팅 동의는 판정에 넣지 않는다.
      return status.privacyConsented;
  }
}

/**
 * 아직 끝나지 않은 첫 단계를 돌려준다(R11). 전부 끝났으면 null.
 *
 * 각 단계가 성공 즉시 서버에 저장되므로(D1) 이탈 후 다시 들어와도 같은 지점에서 재개된다.
 */
export function nextIncompleteStep(status: OnboardingStatus): OnboardingStep | null {
  return ONBOARDING_STEPS.find((step) => !isStepCompleted(status, step)) ?? null;
}

/** 네 단계가 모두 끝났는지. 면제(legacyExempt) 여부와는 무관한 순수 판정이다. */
export function isOnboardingComplete(status: OnboardingStatus): boolean {
  return nextIncompleteStep(status) === null;
}

/** 한 단계를 완료 처리한 새 상태를 만든다. 재조회 없이 다음 단계로 넘어가기 위한 것이다. */
export function withStepCompleted(
  status: OnboardingStatus,
  step: OnboardingStep,
): OnboardingStatus {
  switch (step) {
    case "nickname":
      return { ...status, nicknameCompleted: true };
    case "phone":
      return { ...status, phoneCompleted: true };
    case "interestTags":
      return { ...status, interestTagsCompleted: true };
    case "consent":
      return { ...status, privacyConsented: true };
  }
}
