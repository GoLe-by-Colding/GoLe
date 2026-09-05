import { configureCore, isCoreConfigured, setSessionStore } from "@gole/core";
import { nativeSessionStore, restoreSession } from "@/shared/api/session-store";
import { env } from "./env";

/**
 * 코어 부트스트랩. 웹과 달리 <b>비동기</b>다 — SecureStore에서 세션을 한 번 읽어 메모리에
 * 올린 뒤에야 인증 헤더를 만들 수 있기 때문이다. 이 Promise가 끝나기 전에는 화면을 띄우지 않는다.
 */
export async function bootstrapCore(): Promise<void> {
  if (!isCoreConfigured()) {
    configureCore({ apiBaseUrl: env.apiBaseUrl });
    setSessionStore(nativeSessionStore);
  }
  await restoreSession();
}
