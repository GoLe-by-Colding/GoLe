import { apiRequest } from "@shared/api";
import type { Completeness, ItemCondition, Listing, ListingCategory } from "../model/types";

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
  readonly category: ListingCategory;
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
      category: input.category,
    },
  });
}

/** 활성 리스팅 목록. 항상 최신을 반영하도록 캐시하지 않는다. */
export function fetchActiveListings(signal?: AbortSignal): Promise<readonly Listing[]> {
  return apiRequest<readonly Listing[]>("/api/v1/listings", {
    cache: "no-store",
    ...(signal === undefined ? {} : { signal }),
  });
}

/**
 * 로그인 계정이 등록한 매물 전체(상태 무관, 최신순).
 *
 * 검색 API는 활성 매물만 돌려주므로 "내 매물"을 그걸로 만들면 판매완료·예약중이 빠지고,
 * 전체를 받아 클라이언트에서 거르면 매물이 늘수록 느려진다. 대상 셀러는 쿼리 파라미터가
 * 아니라 서버가 세션에서 정한다.
 */
export function fetchMyListings(signal?: AbortSignal): Promise<readonly Listing[]> {
  return apiRequest<readonly Listing[]>("/api/v1/listings/mine", {
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
  readonly category?: ListingCategory;
  /** 카탈로그 세트번호. 세트 상세 페이지에서 해당 세트 매물만 모을 때 쓴다. */
  readonly setNumber?: string;
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
  if (params.category) {
    qs.set("category", params.category);
  }
  if (params.setNumber) {
    qs.set("setNumber", params.setNumber);
  }
  const suffix = qs.toString().length > 0 ? `?${qs.toString()}` : "";
  return apiRequest<readonly Listing[]>(`/api/v1/listings${suffix}`, {
    cache: "no-store",
    ...(signal === undefined ? {} : { signal }),
  });
}

/**
 * 특정 카탈로그 세트의 활성 매물. 세트 상세 페이지(SSR)용.
 * 색인 대상 페이지라 짧게 캐시해 크롤러 트래픽에 매번 DB를 때리지 않게 한다.
 */
export function fetchListingsBySet(setNumber: string): Promise<readonly Listing[]> {
  return apiRequest<readonly Listing[]>(
    `/api/v1/listings?setNumber=${encodeURIComponent(setNumber)}`,
    { next: { revalidate: 300 } },
  );
}

export function fetchListingById(listingId: string, signal?: AbortSignal): Promise<Listing> {
  return apiRequest<Listing>(`/api/v1/listings/${listingId}`, {
    ...(signal === undefined ? {} : { signal }),
  });
}

export interface ListingCommentItem {
  readonly id: string;
  readonly authorId: string;
  readonly content: string;
  readonly createdAt: string;
}

export function fetchListingComments(listingId: string): Promise<readonly ListingCommentItem[]> {
  return apiRequest<readonly ListingCommentItem[]>(`/api/v1/listings/${listingId}/comments`, {
    cache: "no-store",
  });
}

export function postListingComment(
  listingId: string,
  authorId: string,
  content: string,
): Promise<ListingCommentItem> {
  return apiRequest<ListingCommentItem>(`/api/v1/listings/${listingId}/comments`, {
    method: "POST",
    body: { authorId, content },
  });
}
