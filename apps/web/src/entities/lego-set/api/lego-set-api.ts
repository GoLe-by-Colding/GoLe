import { apiRequest, type RequestOptions } from "@shared/api";
import type { LegoSet } from "../model/types";

// exactOptionalPropertyTypes 환경에서 undefined를 명시적으로 넣지 않도록
// signal이 있을 때만 옵션에 포함한다.
function withSignal(signal: AbortSignal | undefined): RequestOptions {
  return signal === undefined ? {} : { signal };
}

export function fetchLegoSetByNumber(setNumber: string, signal?: AbortSignal): Promise<LegoSet> {
  return apiRequest<LegoSet>(`/api/v1/catalog/sets/${setNumber}`, withSignal(signal));
}

/**
 * 세트 상세(SSR·색인 대상)용 조회. 크롤러 요청마다 백엔드를 때리지 않도록 짧게 캐시한다.
 * 카탈로그는 거의 변하지 않으므로 1시간이면 충분하다.
 */
export function fetchLegoSetForPage(setNumber: string): Promise<LegoSet> {
  return apiRequest<LegoSet>(`/api/v1/catalog/sets/${encodeURIComponent(setNumber)}`, {
    next: { revalidate: 3600 },
  });
}

export function searchLegoSets(query: string, signal?: AbortSignal): Promise<readonly LegoSet[]> {
  const encoded = encodeURIComponent(query);
  return apiRequest<readonly LegoSet[]>(
    `/api/v1/catalog/sets?query=${encoded}`,
    withSignal(signal),
  );
}

/** 홈 추천 세트. 항상 최신을 반영하도록 캐시하지 않는다. */
export function fetchFeaturedLegoSets(signal?: AbortSignal): Promise<readonly LegoSet[]> {
  return apiRequest<readonly LegoSet[]>("/api/v1/catalog/sets/featured", {
    ...withSignal(signal),
    cache: "no-store",
  });
}
