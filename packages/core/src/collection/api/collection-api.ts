import { apiRequest } from "../../runtime";
import type { CollectionItem, CollectionValuePoint, OwnershipStatus } from "../model/types";

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

export function fetchOwnedEstimate(userId: string, signal?: AbortSignal): Promise<number> {
  return apiRequest<{ readonly ownedEstimatedValue: number }>(`${BASE}/${userId}/estimate`, {
    cache: "no-store",
    ...(signal === undefined ? {} : { signal }),
  }).then((r) => r.ownedEstimatedValue);
}

/**
 * 보유 추정가 일별 추이(날짜 오름차순). `days`는 서버가 1~365로 받는다(기본 90).
 * 서버가 조회 시점에 오늘 점을 먼저 갱신하므로, 보유 세트가 있으면 첫 조회부터 점 하나는 온다.
 */
export function fetchCollectionValueHistory(
  userId: string,
  days: number,
  signal?: AbortSignal,
): Promise<readonly CollectionValuePoint[]> {
  const qs = new URLSearchParams({ days: String(days) });
  return apiRequest<{ readonly points: readonly CollectionValuePoint[] }>(
    `${BASE}/${userId}/value-history?${qs.toString()}`,
    {
      cache: "no-store",
      ...(signal === undefined ? {} : { signal }),
    },
  ).then((r) => r.points);
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
