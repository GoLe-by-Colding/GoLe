"use client";

import { useEffect } from "react";
import { appTabNavigation, readAppTab } from "@shared/lib";

interface AppBridge {
  readonly postMessage?: (message: string) => void;
}

/**
 * 화면에 그리는 것은 없다. 앱(WebView) 안에서 다른 탭의 화면으로 가는 링크를 그 탭으로 넘긴다.
 *
 * 앱 밖 브라우저나 이 규칙을 모르는 이전 앱 버전에서는 아무것도 하지 않는다(`ReactNativeWebView`와
 * 앱이 넣는 탭 표시가 둘 다 있어야 켜진다). 링크 이동만 막고 클릭 이벤트 자체는 그대로 흘려보내므로,
 * 링크를 누르면 메뉴를 닫는 식의 다른 처리는 평소처럼 돈다. Next `Link`는 `defaultPrevented`를 보고 멈춘다.
 * 프로그램 이동(`router.push`)은 앱이 주소 변화를 보고 따로 맞춘다.
 */
export function AppTabLinks(): null {
  useEffect(() => {
    const bridge = (window as unknown as { ReactNativeWebView?: AppBridge }).ReactNativeWebView;
    const currentTab = readAppTab();
    if (currentTab === undefined || typeof bridge?.postMessage !== "function") return;
    const post = bridge.postMessage.bind(bridge);

    const onClick = (event: MouseEvent) => {
      if (event.defaultPrevented || event.button !== 0) return;
      if (event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
      const anchor = event.target instanceof Element ? event.target.closest("a[href]") : null;
      if (!(anchor instanceof HTMLAnchorElement)) return;
      if ((anchor.target !== "" && anchor.target !== "_self") || anchor.hasAttribute("download"))
        return;
      const message = appTabNavigation(anchor.href, window.location.href, currentTab);
      if (message === null) return;
      event.preventDefault();
      post(message);
    };
    // 캡처 단계에서 먼저 본다 — Next `Link`가 클라이언트 이동을 시작하기 전에 막아야 한다.
    window.addEventListener("click", onClick, true);
    return () => window.removeEventListener("click", onClick, true);
  }, []);
  return null;
}
