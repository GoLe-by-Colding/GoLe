import { apiRequest, type RequestOptions } from "@shared/api";
import type { LegoSet } from "../model/types";

// exactOptionalPropertyTypes 환경에서 undefined를 명시적으로 넣지 않도록
// signal이 있을 때만 옵션에 포함한다.
function withSignal(signal: AbortSignal | undefined): RequestOptions {
  return signal === undefined ? {} : { signal };
}

export function fetchLegoSetByNumber(
  setNumber: string,
  signal?: AbortSignal,
): Promise<LegoSet> {
  return apiRequest<LegoSet>(
    `/api/v1/catalog/sets/${setNumber}`,
    withSignal(signal),
  );
}

export function searchLegoSets(
  query: string,
  signal?: AbortSignal,
): Promise<readonly LegoSet[]> {
  const encoded = encodeURIComponent(query);
  return apiRequest<readonly LegoSet[]>(
    `/api/v1/catalog/sets?query=${encoded}`,
    withSignal(signal),
  );
}
