import { isSafeAppPath } from "@/shared/lib";

/** 실행 원점과 탐색 정책. 토큰이나 임의 스크립트는 다루지 않는다. */
export function resolveWebOrigin(
  raw: string | undefined,
  development: boolean,
  android: boolean,
): string {
  const fallback = development
    ? android
      ? "http://10.0.2.2:3000"
      : "http://localhost:3000"
    : "https://gole.co.kr";
  const url = new URL(raw || fallback);
  if (
    url.username ||
    url.password ||
    (url.protocol !== "https:" && !(development && url.protocol === "http:"))
  ) {
    throw new Error("웹 주소는 배포에서 HTTPS, 개발에서 HTTP/HTTPS 원점이어야 합니다.");
  }
  return url.origin;
}

export function navigationTarget(raw: string, origin: string): "internal" | "external" | "blocked" {
  try {
    const url = new URL(raw);
    if (url.username || url.password) return "blocked";
    if (url.origin === origin && ["https:", "http:"].includes(url.protocol)) return "internal";
    if (["https:", "http:", "mailto:", "tel:"].includes(url.protocol)) return "external";
  } catch {
    /* 파싱 실패는 차단한다. */
  }
  return "blocked";
}

/** RN 하단 탭. 탭마다 WebView 하나가 아래 웹 경로를 책임진다. */
export type AppTab = "home" | "search" | "sell" | "chat" | "me";

/** 탭이 책임지는 웹 경로의 뿌리. 웹 `shared/lib/app-tab-link.ts`와 같은 표다 — 고치면 같이 고친다. */
export const TAB_PATH = {
  home: "/",
  search: "/search",
  sell: "/sell",
  chat: "/chat",
  me: "/profile",
} as const satisfies Record<AppTab, string>;

/** 웹 경로가 어느 탭의 화면인지. 상세·시세·커뮤니티처럼 탭 뿌리가 아닌 경로는 지금 탭에서 연다. */
export function tabForPath(pathname: string): AppTab | null {
  if (pathname === TAB_PATH.home) return "home";
  for (const tab of ["search", "sell", "chat", "me"] as const) {
    const root = TAB_PATH[tab];
    if (pathname === root || pathname.startsWith(`${root}/`)) return tab;
  }
  return null;
}

/** 탭이 어느 expo-router 경로인지. `(tabs)/me.tsx`가 웹 `/profile`을 연다. */
export const TAB_ROUTE = {
  home: "/",
  search: "/search",
  sell: "/sell",
  chat: "/chat",
  me: "/me",
} as const satisfies Record<AppTab, string>;

export interface TabRequest {
  readonly tab: AppTab;
  /** 원점을 뺀 경로 + query + hash. */
  readonly path: string;
}

/** 신뢰 원점의 탭 경로만 요청으로 만든다. query·hash는 보존한다. */
export function tabRequest(raw: string, origin: string): TabRequest | null {
  try {
    const url = new URL(raw, origin);
    if (url.origin !== origin || url.username || url.password) return null;
    const path = `${url.pathname}${url.search}${url.hash}`;
    if (!isSafeAppPath(path)) return null;
    const tab = tabForPath(url.pathname);
    return tab === null ? null : { tab, path };
  } catch {
    return null;
  }
}

/** 웹이 탭 이동을 부탁할 때 보내는 유일한 메시지 종류. 다른 모양은 모두 버린다. */
export const APP_NAVIGATE_MESSAGE = "gole:navigate";

/**
 * 웹 링크가 다른 탭의 화면을 가리킬 때 웹이 보낸 메시지를 검증한다.
 * 보낸 페이지가 정확히 신뢰 원점이어야 하고, 본문은 `{type, path}` 두 키뿐이며, path는 상대 경로라야 한다.
 * 명령·스크립트·토큰은 받지 않는다 — 탭 전환과 그 탭의 경로만 정한다.
 */
export function appNavigationMessage(
  data: unknown,
  sourceUrl: string,
  origin: string,
): TabRequest | null {
  if (typeof data !== "string" || data.length > 4096) return null;
  try {
    if (new URL(sourceUrl).origin !== origin) return null;
    const message: unknown = JSON.parse(data);
    if (typeof message !== "object" || message === null || Array.isArray(message)) return null;
    const keys = Object.keys(message).sort();
    if (keys.length !== 2 || keys[0] !== "path" || keys[1] !== "type") return null;
    const { type, path } = message as { type: unknown; path: unknown };
    if (type !== APP_NAVIGATE_MESSAGE || !isSafeAppPath(path)) return null;
    return tabRequest(path, origin);
  } catch {
    return null;
  }
}

/**
 * 탭에 새 목적지가 왔을 때 WebView를 옮길지. 작성 중인 폼을 지우지 않도록, 뿌리만 가리키는 요청
 * (`/sell` 같은 탭 버튼 성격의 링크)은 그 탭이 이미 자기 영역에 있으면 옮기지 않는다.
 * query·hash가 붙은 구체적 목적지(`/search?query=…`, `/chat?room=…`)는 지금 위치와 다를 때만 옮긴다.
 */
export function shouldMoveTab(target: string, current: string, tab: AppTab): boolean {
  if (target === current) return false;
  const currentPath = current.split(/[?#]/, 1)[0] ?? "";
  return !(target === TAB_PATH[tab] && tabForPath(currentPath) === tab);
}

/** WebView가 어느 탭 안에 떠 있는지 웹에 알리는 상수 주입. 탭이 아닌 화면은 null이다. */
export function appTabScript(tab: AppTab | null): string {
  return `window.__GOLE_APP_TAB__=${JSON.stringify(tab)};true;`;
}
