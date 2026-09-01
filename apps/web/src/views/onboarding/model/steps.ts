import type { OnboardingStep } from "@entities/user";

/**
 * 단계별 화면 문구. 단계 판정 자체는 entities/user가 갖는다 —
 * 어떤 계정 필드가 채워져야 완료인지는 화면이 아니라 도메인 지식이고,
 * 배너 위젯도 같은 판정을 써야 하기 때문이다.
 */
const STEP_TITLE: Record<OnboardingStep, string> = {
  nickname: "닉네임을 정해 주세요",
  phone: "휴대폰 번호를 인증해 주세요",
  interestTags: "관심 있는 테마를 골라 주세요",
  consent: "약관에 동의해 주세요",
};

export function stepTitle(step: OnboardingStep): string {
  return STEP_TITLE[step];
}
