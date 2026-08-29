import type { PricePoint } from "./types";

const DAY_MS = 24 * 60 * 60 * 1000;

/** 선택 기간의 실제 체결만 돌려준다. 표본이 적다고 전체 기간으로 몰래 되돌리지 않는다. */
export function filterPricePointsByPeriod(
  points: readonly PricePoint[],
  days: number,
  now = Date.now(),
): readonly PricePoint[] {
  if (days === Number.MAX_SAFE_INTEGER) {
    return points;
  }
  const cutoff = now - days * DAY_MS;
  return points.filter((point) => new Date(point.executedAt).getTime() >= cutoff);
}
