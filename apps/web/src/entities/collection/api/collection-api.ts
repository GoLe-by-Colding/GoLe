import { apiRequest } from "@shared/api";
import type { CollectionItem, OwnershipStatus } from "../model/types";

const BASE = "/api/v1/collections";

export function fetchCollection(
  userId: string,
  signal?: AbortSignal,
): Promise<readonly CollectionItem[]> {
  return apiRequest<readonly CollectionItem[]>(`${BASE}/${userId}/items`, {
    cache: "no-store",
    ...(signal === undefined ? {} : { signal }),
  });
}

export function fetchOwnedEstimate(
  userId: string,
  signal?: AbortSignal,
): Promise<number> {
  return apiRequest<{ readonly ownedEstimatedValue: number }>(
    `${BASE}/${userId}/estimate`,
    { cache: "no-store", ...(signal === undefined ? {} : { signal }) },
  ).then((r) => r.ownedEstimatedValue);
}

export function addCollectionItem(
  userId: string,
  setNumber: string,
  status: OwnershipStatus,
): Promise<CollectionItem> {
  return apiRequest<CollectionItem>(`${BASE}/items`, {
    method: "POST",
    body: { userId, setNumber, status: status.toUpperCase() },
  });
}

export function removeCollectionItem(itemId: string, userId: string): Promise<void> {
  const qs = new URLSearchParams({ userId });
  return apiRequest<void>(`${BASE}/items/${itemId}?${qs.toString()}`, {
    method: "DELETE",
  });
}
