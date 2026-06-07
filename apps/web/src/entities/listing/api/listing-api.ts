import { apiRequest } from "@shared/api";
import type { ItemCondition, Listing } from "../model/types";

export interface CreateListingInput {
  readonly sellerId: string;
  readonly title: string;
  readonly description: string;
  readonly price: number;
  readonly condition: ItemCondition;
  readonly photoUrls: readonly string[];
  readonly catalogSetNumber: string | null;
}

/** 리스팅 생성. 백엔드는 condition을 대문자 enum으로 받는다. */
export function createListing(input: CreateListingInput): Promise<Listing> {
  return apiRequest<Listing>("/api/v1/listings", {
    method: "POST",
    body: {
      sellerId: input.sellerId,
      title: input.title,
      description: input.description,
      price: input.price,
      condition: input.condition.toUpperCase(),
      photoUrls: input.photoUrls,
      catalogSetNumber: input.catalogSetNumber,
    },
  });
}

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
