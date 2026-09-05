import { ApiError } from "./http-client";

/**
 * 서버측 온보딩 게이트(onboarding D5, R13) 대응.
 *
 * 매물등록·주문생성·채팅시작은 <b>서버가</b> 막는다 — 클라이언트만 믿고 게이트를 걸었다가 실제
 * 우회 사고가 났던 전례가 있어서다. 그래서 클라이언트는 차단을 판정하지 않고, 서버가 내려준
 * 403을 받아 사용자를 온보딩으로 안내하기만 한다.
 *
 * <b>안내하는 방법은 플랫폼마다 다르다.</b> 웹은 `window.location`으로 이동하고 앱은 라우터를
 * 쓴다. 코어는 어느 쪽도 알지 않으므로 세션 저장소와 같은 방식으로 주입받는다.
 */

/** 서버 가드가 거부할 때 쓰는 에러 코드(R9). */
export const ONBOARDING_REQUIRED_CODE = "ONBOARDING_REQUIRED";

export type OnboardingRequiredHandler = () => void;

const NOOP: OnboardingRequiredHandler = () => undefined;

let handler: OnboardingRequiredHandler = NOOP;

export function setOnboardingRequiredHandler(next: OnboardingRequiredHandler): void {
  handler = next;
}

export function getOnboardingRequiredHandler(): OnboardingRequiredHandler {
  return handler;
}

/** 테스트에서 주입을 되돌린다. */
export function resetOnboardingRequiredHandlerForTest(): void {
  handler = NOOP;
}

/** 서버 온보딩 가드(R9)가 거부한 응답인지 판정한다. */
export function isOnboardingRequiredError(error: unknown): boolean {
  return (
    error instanceof ApiError && error.status === 403 && error.code === ONBOARDING_REQUIRED_CODE
  );
}
