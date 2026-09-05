import { configureCore, isCoreConfigured, setSessionStore } from "@gole/core";
import { browserSessionStore } from "@shared/api/session-auth";
import { env } from "./env";

/**
 * 코어 부트스트랩. 환경설정과 브라우저 세션 저장소를 주입한다.
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
}

bootstrapCore();
