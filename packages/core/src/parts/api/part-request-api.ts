import { apiRequest } from "../../runtime";
import type { CreatePartRequestInput, PartRequest, PartRequestQuery } from "../model/types";

const BASE = "/api/v1/part-requests";

function withSignal(signal: AbortSignal | undefined): { readonly signal?: AbortSignal } {
  return signal === undefined ? {} : { signal };
}

/**
 * 부품 요청을 올린다. 세트가 있으면 서버가 그 세트 보유자에게 알림을 보낸다(W8).
 * 온보딩 미완료면 서버가 403 `ONBOARDING_REQUIRED`로 막고 공용 래퍼가 온보딩으로 안내한다.
 */
export function createPartRequest(input: CreatePartRequestInput): Promise<PartRequest> {
  const setNumber = input.setNumber?.trim() ?? "";
  return apiRequest<PartRequest>(BASE, {
    method: "POST",
    body: {
      setNumber: setNumber.length === 0 ? null : setNumber,
      items: input.items.map((item) => ({
        partNumber: item.partNumber.trim(),
        colorName: item.colorName.trim(),
        quantity: item.quantity,
      })),
      note: (input.note ?? "").trim(),
    },
  });
}

function listPath(query: PartRequestQuery): string {
  const qs = new URLSearchParams();
  const setNumber = query.setNumber?.trim() ?? "";
  if (setNumber.length > 0) qs.set("setNumber", setNumber);
  if (query.status !== undefined) qs.set("status", query.status);
  if (query.limit !== undefined) qs.set("limit", String(query.limit));
  const suffix = qs.toString();
  return suffix.length === 0 ? BASE : `${BASE}?${suffix}`;
}

/** 공개 게시판. 최신순, `status` 기본 `open`, `limit` 1~50(기본 20). */
export function fetchPartRequests(
  query: PartRequestQuery = {},
  signal?: AbortSignal,
): Promise<readonly PartRequest[]> {
  return apiRequest<readonly PartRequest[]>(listPath(query), {
    cache: "no-store",
    ...withSignal(signal),
  });
}

/**
 * 서버 렌더 화면(세트 상세)용 게시판 조회. 세트 상세는 재검증(ISR)으로 그려지므로 `no-store`를
 * 쓰면 페이지 전체가 매 요청 렌더로 바뀐다. "부품 요청 n건"은 1분 늦어도 되므로 짧게 재검증한다.
 */
export function fetchPartRequestsForPage(
  query: PartRequestQuery = {},
): Promise<readonly PartRequest[]> {
  return apiRequest<readonly PartRequest[]>(listPath(query), { next: { revalidate: 60 } });
}

/** 내 요청(상태 무관) 최신순 최대 50건. 세션 필요. */
export function fetchMyPartRequests(signal?: AbortSignal): Promise<readonly PartRequest[]> {
  return apiRequest<readonly PartRequest[]>(`${BASE}/mine`, {
    cache: "no-store",
    ...withSignal(signal),
  });
}

/** 공개 단건 조회. 없으면 404 `PART_REQUEST_NOT_FOUND`. */
export function fetchPartRequest(id: string, signal?: AbortSignal): Promise<PartRequest> {
  return apiRequest<PartRequest>(`${BASE}/${encodeURIComponent(id)}`, {
    cache: "no-store",
    ...withSignal(signal),
  });
}

/** 작성자만 마감한다. 이미 마감됐으면 409 `PART_REQUEST_NOT_OPEN`. */
export function closePartRequest(id: string): Promise<PartRequest> {
  return apiRequest<PartRequest>(`${BASE}/${encodeURIComponent(id)}/close`, { method: "POST" });
}

/** 작성자만 지운다. 성공하면 204. */
export function deletePartRequest(id: string): Promise<void> {
  return apiRequest<void>(`${BASE}/${encodeURIComponent(id)}`, { method: "DELETE" });
}
