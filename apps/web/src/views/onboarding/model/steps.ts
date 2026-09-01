import type { OnboardingStatus } from "@entities/user";

/** 온보딩 단계. 순서가 곧 위저드 진행 순서다. */
export type OnboardingStep = "nickname" | "phone" | "interestTags" | "consent";

export const ONBOARDING_STEPS: readonly OnboardingStep[] = [
  "nickname",
  "phone",
  "interestTags",
  "consent",
];

const STEP_TITLE: Record<OnboardingStep, string> = {
  nickname: "닉네임을 정해 주세요",
  phone: "휴대폰 번호를 인증해 주세요",
  interestTags: "관심 있는 테마를 골라 주세요",
  consent: "약관에 동의해 주세요",
};

export function stepTitle(step: OnboardingStep): string {
  return STEP_TITLE[step];
}

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
      return status.consentCompleted;
  }
}

/**
 * 아직 끝나지 않은 첫 단계를 돌려준다(R11). 전부 끝났으면 null.
 *
 * 완료 여부는 서버가 계정 필드 유무로 파생시킨 값이므로(D1), 이탈 후 다시 들어와도
 * 같은 응답으로 같은 지점에서 재개된다.
 */
export function nextIncompleteStep(status: OnboardingStatus): OnboardingStep | null {
  return ONBOARDING_STEPS.find((step) => !isStepCompleted(status, step)) ?? null;
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
      return { ...status, consentCompleted: true };
  }
}
