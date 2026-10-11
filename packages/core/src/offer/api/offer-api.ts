import { apiRequest } from "../../runtime";
import type { PriceOffer } from "../model/types";

const BASE = "/api/v1/offers";

function withSignal(signal: AbortSignal | undefined): { readonly signal?: AbortSignal } {
  return signal === undefined ? {} : { signal };
}

/**
 * 매물 대화방의 구매자가 가격을 제안한다(O1). 방에 제안 메시지가 남고 판매자에게 알림이 간다.
 *
 * 채팅 메시지 전송과 같은 제3자 제공 동의를 요구하므로(O2) 호출부가 동의 래퍼(`CHAT_MESSAGE`)로 감싼다.
 */
export function makeOffer(roomId: string, price: number): Promise<PriceOffer> {
  return apiRequest<PriceOffer>(BASE, { method: "POST", body: { roomId, price } });
}

/** 판매자만. 수락하면 만료가 수락 시각 + 72시간(기본)으로 다시 잡힌다(O7). */
export function acceptOffer(offerId: string): Promise<PriceOffer> {
  return apiRequest<PriceOffer>(`${BASE}/${encodeURIComponent(offerId)}/accept`, {
    method: "POST",
  });
}

/** 판매자만. 대기 제안은 거절, 수락한 제안은 수락 취소가 된다(O8). */
export function declineOffer(offerId: string): Promise<PriceOffer> {
  return apiRequest<PriceOffer>(`${BASE}/${encodeURIComponent(offerId)}/decline`, {
    method: "POST",
  });
}

/** 구매자만. 대기·수락 제안을 거둔다(O9). */
export function withdrawOffer(offerId: string): Promise<PriceOffer> {
  return apiRequest<PriceOffer>(`${BASE}/${encodeURIComponent(offerId)}/withdraw`, {
    method: "POST",
  });
}

/** 그 방의 제안. 방 참여자만, 최신순 최대 50건(O13). 세션 필요. 입찰 체결 제안(방 없음)은 빠진다. */
export function fetchRoomOffers(
  roomId: string,
  signal?: AbortSignal,
): Promise<readonly PriceOffer[]> {
  return apiRequest<readonly PriceOffer[]>(`${BASE}?roomId=${encodeURIComponent(roomId)}`, {
    cache: "no-store",
    ...withSignal(signal),
  });
}

/**
 * 그 매물의 제안. 판매자면 전부, 아니면 내 제안만 — 입찰 체결 제안도 함께 온다(O14). 최신순 최대 50건.
 * 세션 필요.
 */
export function fetchListingOffers(
  listingId: string,
  signal?: AbortSignal,
): Promise<readonly PriceOffer[]> {
  return apiRequest<readonly PriceOffer[]>(`${BASE}?listingId=${encodeURIComponent(listingId)}`, {
    cache: "no-store",
    ...withSignal(signal),
  });
}
