"use client";

import { createContext, useContext, useEffect, useState, type ReactNode } from "react";
import {
  DEFAULT_MASCOT,
  fetchActiveMascot,
  MASCOT_PUBLISHED_EVENT,
  type ActiveMascot,
  type MascotArtwork,
} from "@gole/core/mascot";

// Provider 밖(global-error 처럼 RootLayout 을 대신하는 화면)에서는 기본 고래를 그린다.
const MascotContext = createContext<MascotArtwork>(DEFAULT_MASCOT);

/**
 * 사이트에 적용된 마스코트를 아래 모든 `Logo`에 건넨다. (mascot-assets R4)
 *
 * <p>첫 값은 서버가 그린 HTML 과 같은 `initial`이라 깜빡이지 않는다. 같은 탭의 관리자 화면이 적용에 성공하면
 * 이벤트를 받아 캐시 없이 다시 받는다. 늦게 온 이전 응답이 최신 값을 덮지 않게 순서를 센다.
 */
export function MascotProvider({
  initial,
  children,
}: {
  readonly initial: ActiveMascot;
  readonly children: ReactNode;
}) {
  const [active, setActive] = useState(initial);

  useEffect(() => {
    let alive = true;
    let generation = 0;
    const refresh = () => {
      const requested = ++generation;
      void fetchActiveMascot().then((next) => {
        if (alive && requested === generation) setActive(next);
      });
    };
    window.addEventListener(MASCOT_PUBLISHED_EVENT, refresh);
    return () => {
      alive = false;
      window.removeEventListener(MASCOT_PUBLISHED_EVENT, refresh);
    };
  }, []);

  return <MascotContext.Provider value={active.asset}>{children}</MascotContext.Provider>;
}

/** 지금 사이트가 그릴 마스코트. */
export function useMascot(): MascotArtwork {
  return useContext(MascotContext);
}
