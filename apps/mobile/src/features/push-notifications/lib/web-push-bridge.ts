import type { DevicePushToken } from "./device-push-token";

/**
 * WebView 에 단말 푸시 토큰을 건네는 스크립트. 웹이 `window.__GOLE_APP_PUSH__` 를 읽고
 * `gole:app-push-token` 이벤트를 듣는다(apps/web `shared/lib/app-push-token.ts`).
 *
 * 값은 JSON 으로만 싣는다 — 토큰 문자열을 이어 붙여 코드를 만들지 않는다. 등록은 로그인한 웹이
 * 자기 세션으로 한다. 네이티브는 세션을 모른다.
 */
export function webPushTokenScript(token: DevicePushToken): string {
  const payload = JSON.stringify({ token: token.token, platform: token.platform });
  return `window.__GOLE_APP_PUSH__=${payload};window.dispatchEvent(new Event("gole:app-push-token"));true;`;
}
