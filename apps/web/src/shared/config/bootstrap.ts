import {
  configureCore,
  isCoreConfigured,
  setOnboardingRequiredHandler,
  setSessionStore,
} from "@gole/core";
import { redirectToOnboarding } from "@shared/api/onboarding-guard";
import { browserSessionStore } from "@shared/api/session-auth";
import { env } from "./env";

/**
 * 코어 부트스트랩. 환경설정·브라우저 세션 저장소·온보딩 이동을 주입한다.
 *
 * <b>서버·클라이언트 양쪽 진입점에서 불러야 한다.</b> Next는 두 모듈 그래프를 따로 만들므로
 * 한쪽만 걸면 다른 쪽이 미설정으로 남는다. 코어가 설정을 호출 시점에 읽기 때문에 import 순서는
 * 문제가 되지 않고, "첫 요청보다 먼저"만 지키면 된다.
 */
export function bootstrapCore(): void {
  if (isCoreConfigured()) {
    return;
  }
  configureCore({ apiBaseUrl: env.apiBaseUrl });
  setSessionStore(browserSessionStore);
  // 서버 온보딩 가드(403)에 걸렸을 때의 이동 방법은 플랫폼마다 다르다.
  // 코어는 판정만 하고, 웹은 location으로 옮긴다.
  setOnboardingRequiredHandler(redirectToOnboarding);
}

bootstrapCore();
