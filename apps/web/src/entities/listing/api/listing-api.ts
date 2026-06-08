import { apiRequest } from "@shared/api";
import type { Completeness, ItemCondition, Listing } from "../model/types";

export interface CreateListingInput {
  readonly sellerId: string;
  readonly title: string;
  readonly description: string;
  readonly price: number;
  readonly condition: ItemCondition;
  readonly completeness: Completeness;
  readonly hasBox: boolean;
  readonly hasManual: boolean;
  readonly hasMissingParts: boolean;
  readonly missingPartsNote: string;
  readonly defectsNote: string;
  readonly photoUrls: readonly string[];
  readonly catalogSetNumber: string | null;
}

/** 리스팅 생성. 백엔드는 condition/completeness를 대문자 enum으로 받는다. */
export function createListing(input: CreateListingInput): Promise<Listing> {
  return apiRequest<Listing>("/api/v1/listings", {
    method: "POST",
    body: {
      sellerId: input.sellerId,
      title: input.title,
      description: input.description,
      price: input.price,
      condition: input.condition.toUpperCase(),
      completeness: input.completeness.toUpperCase(),
      hasBox: input.hasBox,
      hasManual: input.hasManual,
      hasMissingParts: input.hasMissingParts,
      missingPartsNote: input.missingPartsNote,
      defectsNote: input.defectsNote,
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

export type ListingSort = "newest" | "price_asc" | "price_desc";

export interface SearchListingsParams {
  readonly query?: string;
  readonly condition?: ItemCondition;
  readonly minPrice?: number;
  readonly maxPrice?: number;
  readonly sort?: ListingSort;
}

/** 활성 리스팅 검색/필터/정렬. 백엔드는 enum을 대문자로 받는다. (요구사항 14) */
export function searchListings(
  params: SearchListingsParams,
  signal?: AbortSignal,
): Promise<readonly Listing[]> {
  const qs = new URLSearchParams();
  if (params.query) {
    qs.set("query", params.query);
  }
  if (params.condition) {
    qs.set("condition", params.condition.toUpperCase());
  }
  if (params.minPrice !== undefined) {
    qs.set("minPrice", String(params.minPrice));
  }
  if (params.maxPrice !== undefined) {
    qs.set("maxPrice", String(params.maxPrice));
  }
  if (params.sort) {
    qs.set("sort", params.sort.toUpperCase());
  }
  const suffix = qs.toString().length > 0 ? `?${qs.toString()}` : "";
  return apiRequest<readonly Listing[]>(`/api/v1/listings${suffix}`, {
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
