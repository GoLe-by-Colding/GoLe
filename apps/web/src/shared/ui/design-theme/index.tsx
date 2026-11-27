"use client";
import "@shared/config/bootstrap";
import { useEffect } from "react";
import { fetchPublishedDesign } from "@gole/core/design";
/** CSS 기본값으로 즉시 렌더링하고 수화 후 검증된 게시 설정을 불러온다. */
export function DesignTheme() {
  useEffect(() => {
    let active = true;
    let generation = 0;
    const refresh = () => {
      const requested = ++generation;
      void fetchPublishedDesign().then((tokens) => {
        if (active && requested === generation)
          Object.entries(tokens).forEach(([key, value]) =>
            document.documentElement.style.setProperty(key, value),
          );
      });
    };
    refresh();
    window.addEventListener("gole:design-published", refresh);
    window.addEventListener("focus", refresh);
    return () => {
      active = false;
      window.removeEventListener("gole:design-published", refresh);
      window.removeEventListener("focus", refresh);
    };
  }, []);
  return null;
}
