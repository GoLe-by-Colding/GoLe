import { apiRequest } from "@shared/api";
import type { ListingSummary, WishlistEntry, WishlistTargetType } from "../model/types";

const noStore = (signal?: AbortSignal) => ({
  cache: "no-store" as RequestCache,
  ...(signal === undefined ? {} : { signal }),
});

export function fetchSellerShop(
  sellerId: string,
  signal?: AbortSignal,
): Promise<readonly ListingSummary[]> {
  return apiRequest<readonly ListingSummary[]>(`/api/v1/shops/${sellerId}`, noStore(signal));
}

export function fetchFollowing(userId: string, signal?: AbortSignal): Promise<readonly string[]> {
  return apiRequest<readonly string[]>(`/api/v1/users/${userId}/following`, noStore(signal));
}

export function followSeller(userId: string, sellerId: string): Promise<void> {
  return apiRequest<void>(`/api/v1/users/${userId}/following`, {
    method: "POST",
    body: { sellerId },
  });
}

export function unfollowSeller(userId: string, sellerId: string): Promise<void> {
  return apiRequest<void>(`/api/v1/users/${userId}/following/${sellerId}`, {
    method: "DELETE",
  });
}

export function fetchWishlist(
  userId: string,
  signal?: AbortSignal,
): Promise<readonly WishlistEntry[]> {
  return apiRequest<readonly WishlistEntry[]>(`/api/v1/users/${userId}/wishlist`, noStore(signal));
}

export function addWishlist(
  userId: string,
  targetType: WishlistTargetType,
  targetId: string,
): Promise<void> {
  return apiRequest<void>(`/api/v1/users/${userId}/wishlist`, {
    method: "POST",
    body: { targetType: targetType.toUpperCase(), targetId },
  });
}

export function removeWishlist(
  userId: string,
  targetType: WishlistTargetType,
  targetId: string,
): Promise<void> {
  const qs = new URLSearchParams({ targetType: targetType.toUpperCase(), targetId });
  return apiRequest<void>(`/api/v1/users/${userId}/wishlist?${qs.toString()}`, {
    method: "DELETE",
  });
}
