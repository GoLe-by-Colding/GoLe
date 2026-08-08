import { apiRequest } from "@shared/api";
import type { PricePoint, PriceStatistics, PriceValuation, TrendingSet } from "../model/types";

const BASE = "/api/v1/pricing/sets";

export function fetchPriceStatistics(
  setNumber: string,
  signal?: AbortSignal,
): Promise<PriceStatistics> {
  return apiRequest<PriceStatistics>(`${BASE}/${setNumber}/statistics`, {
    cache: "no-store",
    ...(signal === undefined ? {} : { signal }),
  });
}

/** 세트 상세(SSR·색인 대상)용 시세 통계. 체결 이력은 자주 바뀌므로 5분 캐시. */
export function fetchPriceStatisticsForPage(setNumber: string): Promise<PriceStatistics> {
  return apiRequest<PriceStatistics>(
    `${BASE}/${encodeURIComponent(setNumber)}/statistics`,
    { next: { revalidate: 300 } },
  );
}

export function fetchPriceChart(
  setNumber: string,
  signal?: AbortSignal,
): Promise<readonly PricePoint[]> {
  return apiRequest<readonly PricePoint[]>(`${BASE}/${setNumber}/chart`, {
    cache: "no-store",
    ...(signal === undefined ? {} : { signal }),
  });
}

export function fetchPriceHistory(
  setNumber: string,
  signal?: AbortSignal,
): Promise<readonly PricePoint[]> {
  return apiRequest<readonly PricePoint[]>(`${BASE}/${setNumber}/history`, {
    cache: "no-store",
    ...(signal === undefined ? {} : { signal }),
  });
}

export function fetchPriceValuation(
  setNumber: string,
  signal?: AbortSignal,
): Promise<PriceValuation> {
  return apiRequest<PriceValuation>(`${BASE}/${setNumber}/valuation`, {
    cache: "no-store",
    ...(signal === undefined ? {} : { signal }),
  });
}

export function fetchTrendingSets(
  limit = 8,
  signal?: AbortSignal,
): Promise<readonly TrendingSet[]> {
  return apiRequest<readonly TrendingSet[]>(`/api/v1/pricing/trending?limit=${limit}`, {
    cache: "no-store",
    ...(signal === undefined ? {} : { signal }),
  });
}
