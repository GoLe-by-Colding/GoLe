"use client";

import { useSyncExternalStore } from "react";

/** 쿨다운 표시는 분 단위라 15초마다 갱신하면 충분하다. */
const TICK_MS = 15_000;

function subscribe(onTick: () => void): () => void {
  const timer = window.setInterval(onTick, TICK_MS);
  return () => window.clearInterval(timer);
}

/**
 * 같은 렌더 안에서 여러 번 불려도 같은 값을 돌려줘야 하므로 틱 단위로 내린다.
 * `Date.now()`를 그대로 돌려주면 매 호출마다 값이 달라 React가 무한 갱신으로 본다.
 */
function getSnapshot(): number {
  return Math.floor(Date.now() / TICK_MS) * TICK_MS;
}

function getServerSnapshot(): null {
  return null;
}

/** 현재 시각(ms). 서버 렌더와 하이드레이션 중에는 `null`이다 — 시각에 기댄 표시는 그 뒤에 그린다. */
export function useClock(): number | null {
  return useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot);
}
