/**
 * 앱(WebView) 탭 안에서 다른 탭의 화면으로 가는 링크를 앱에 넘기는 규칙.
 *
 * 앱은 하단 탭마다 WebView가 하나다. 홈 탭 WebView 안에서 `/search`로 가면 선택된 탭(홈)과 화면(검색)이
 * 어긋나므로, 다른 탭의 뿌리 경로로 가는 링크는 이동하지 않고 앱에 `{type, path}` 메시지 하나만 보낸다.
 * 앱은 원점·모양·경로를 다시 검증한 뒤 그 탭을 연다(apps/mobile `views/web/model/navigation.ts`).
 * 토큰·쿠키·스크립트는 싣지 않는다.
 */
export type AppTab = "home" | "search" | "sell" | "chat" | "me";

/** 앱과 약속한 유일한 메시지 종류. 이름을 바꾸면 앱도 같이 바꿔야 한다. */
export const APP_NAVIGATE_MESSAGE = "gole:navigate";

/** 탭이 책임지는 웹 경로의 뿌리. 앱 `TAB_ROOTS`와 같은 표다 — 고치면 같이 고친다. */
const TAB_ROOTS: readonly (readonly [AppTab, string])[] = [
  ["search", "/search"],
  ["sell", "/sell"],
  ["chat", "/chat"],
  ["me", "/profile"],
];

const APP_TABS: readonly AppTab[] = ["home", "search", "sell", "chat", "me"];
const GLOBAL_KEY = "__GOLE_APP_TAB__";

export function appTabForPath(pathname: string): AppTab | null {
  if (pathname === "/") return "home";
  for (const [tab, root] of TAB_ROOTS) {
    if (pathname === root || pathname.startsWith(`${root}/`)) return tab;
  }
  return null;
}

/**
 * 앱이 이 페이지를 어느 탭에 띄웠는지. 앱 밖 브라우저는 `undefined`, 탭이 아닌 앱 화면(알림 상세)은 `null`.
 * 값은 앱이 페이지 로드 전에 JSON 상수로만 넣는다.
 */
export function readAppTab(): AppTab | null | undefined {
  if (typeof window === "undefined") return undefined;
  const scope = window as unknown as Record<string, unknown>;
  if (!(GLOBAL_KEY in scope)) return undefined;
  const value = scope[GLOBAL_KEY];
  return APP_TABS.find((tab) => tab === value) ?? null;
}

/**
 * 링크가 다른 탭의 화면을 가리키면 앱에 보낼 메시지(JSON 문자열), 아니면 null.
 * 같은 원점의 탭 뿌리 경로만 넘기고 query·hash는 그대로 싣는다. 지금 탭 안의 이동과
 * 상세·시세·커뮤니티 같은 탭 밖 경로는 웹이 평소처럼 연다.
 */
export function appTabNavigation(
  href: string,
  pageUrl: string,
  currentTab: AppTab | null,
): string | null {
  try {
    const page = new URL(pageUrl);
    const url = new URL(href, page);
    if (url.origin !== page.origin) return null;
    const tab = appTabForPath(url.pathname);
    if (tab === null || tab === currentTab) return null;
    return JSON.stringify({
      type: APP_NAVIGATE_MESSAGE,
      path: `${url.pathname}${url.search}${url.hash}`,
    });
  } catch {
    return null;
  }
}
