import { apiRequest } from "@shared/api";
import type { Listing } from "../model/types";

/** 활성 리스팅 목록. 항상 최신을 반영하도록 캐시하지 않는다. */
export function fetchActiveListings(
  signal?: AbortSignal,
): Promise<readonly Listing[]> {
  return apiRequest<readonly Listing[]>("/api/v1/listings", {
    cache: "no-store",
    ...(signal === undefined ? {} : { signal }),
  });
}

export function fetchListingById(
  listingId: string,
  signal?: AbortSignal,
): Promise<Listing> {
  return apiRequest<Listing>(`/api/v1/listings/${listingId}`, {
    ...(signal === undefined ? {} : { signal }),
  });
}
