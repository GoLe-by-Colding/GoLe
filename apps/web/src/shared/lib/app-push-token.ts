/**
 * 앱(WebView)이 웹에 건네는 단말 푸시 토큰.
 *
 * 앱은 하단 탭만 네이티브이고 본문과 로그인은 웹이 가진다. 네이티브는 세션을 모르므로 토큰만
 * `window.__GOLE_APP_PUSH__` 에 실어 두고 이벤트로 알린다. 서버 등록은 로그인한 웹이 자기 세션으로
 * 한다 — 네이티브가 계정을 추측해 등록하면 다른 계정의 알림이 이 단말로 올 수 있다.
 */
export interface AppPushToken {
  readonly token: string;
  readonly platform: "IOS" | "ANDROID";
}

/** 앱이 토큰을 새로 주입했을 때 window 에 쏘는 이벤트. 이름을 바꾸면 앱도 같이 바꿔야 한다. */
export const APP_PUSH_TOKEN_EVENT = "gole:app-push-token";

const GLOBAL_KEY = "__GOLE_APP_PUSH__";
const MAX_TOKEN_LENGTH = 4096;

/** 브라우저(앱 밖)에서는 항상 null 이다. */
export function readAppPushToken(): AppPushToken | null {
  if (typeof window === "undefined") return null;
  return parseAppPushToken((window as unknown as Record<string, unknown>)[GLOBAL_KEY]);
}

export function subscribeAppPushToken(onChange: (token: AppPushToken | null) => void): () => void {
  if (typeof window === "undefined") return () => undefined;
  const listener = () => onChange(readAppPushToken());
  window.addEventListener(APP_PUSH_TOKEN_EVENT, listener);
  return () => window.removeEventListener(APP_PUSH_TOKEN_EVENT, listener);
}

function parseAppPushToken(value: unknown): AppPushToken | null {
  if (typeof value !== "object" || value === null) return null;
  const { token, platform } = value as { token?: unknown; platform?: unknown };
  if (typeof token !== "string" || token.length === 0 || token.length > MAX_TOKEN_LENGTH)
    return null;
  if (platform !== "IOS" && platform !== "ANDROID") return null;
  return { token, platform };
}
