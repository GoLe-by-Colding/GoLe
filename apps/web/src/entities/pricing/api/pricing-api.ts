import { apiRequest } from "@shared/api";
import type { PricePoint, PriceStatistics } from "../model/types";

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
