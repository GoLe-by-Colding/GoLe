/**
 * 가격 제안 규칙 중 화면이 계산해야 하는 부분. 판정의 정본은 서버다 — 여기서는 제출 전에 틀린
 * 입력을 짚고, 서버가 내려준 값을 사람이 읽을 형태로 바꾸는 것만 한다.
 */
import type { OfferStatus, PriceOffer } from "./types";

const STATUS_LABEL: Record<OfferStatus, string> = {
  pending: "응답 대기",
  accepted: "수락됨",
  declined: "거절됨",
  withdrawn: "철회됨",
  expired: "만료됨",
};

export function offerStatusLabel(status: OfferStatus): string {
  return STATUS_LABEL[status];
}

/** 아직 끝나지 않은 제안(대기·수락). 판매자는 거절, 구매자는 철회할 수 있다(O8·O9). */
export function isOfferOpen(offer: Pick<PriceOffer, "status">): boolean {
  return offer.status === "pending" || offer.status === "accepted";
}

/**
 * 지금 주문에 실을 수 있는 수락 제안인지. 서버 응답의 `accepted`는 읽은 시점 기준이라,
 * 화면이 떠 있는 동안 만료 시각이 지났으면 여기서 걸러 "제안가로 구매"를 내리지 않는다.
 */
export function isOfferUsable(offer: PriceOffer, nowMs: number): boolean {
  if (offer.status !== "accepted") return false;
  const expiresAt = Date.parse(offer.expiresAt);
  return Number.isNaN(expiresAt) || expiresAt > nowMs;
}

/**
 * 이 구매자가 이 매물에 쓸 수 있는 가장 최근 수락 제안. 채팅 제안과 입찰 체결 제안을 구분하지 않는다
 * (buy-bids: "구매 쪽은 제안·입찰 출신을 구분하지 않는다").
 */
export function findUsableAcceptedOffer(
  offers: readonly PriceOffer[],
  listingId: string,
  buyerId: string,
  nowMs: number,
): PriceOffer | null {
  let latest: PriceOffer | null = null;
  for (const offer of offers) {
    if (offer.listingId !== listingId || offer.buyerId !== buyerId) continue;
    if (!isOfferUsable(offer, nowMs)) continue;
    if (latest === null || Date.parse(offer.createdAt) > Date.parse(latest.createdAt)) {
      latest = offer;
    }
  }
  return latest;
}

/**
 * 제안으로 주문할 때 실제로 결제할 금액. 서버는 `min(제안가, 예약 시점 매물가)`로 정한다(O17) —
 * 판매자가 그 사이 더 내렸거나, 입찰 체결 제안이 매물가보다 높을 수 있다(O21).
 */
export function offerOrderAmount(offerPrice: number, listingPrice: number): number {
  return Math.min(offerPrice, listingPrice);
}

/** 매물가 대비 할인율(%, 반올림). 매물가 이상이거나 계산할 수 없으면 `null`. */
export function offerDiscountPercent(listingPrice: number, offerPrice: number): number | null {
  if (!Number.isFinite(listingPrice) || !Number.isFinite(offerPrice) || listingPrice <= 0) {
    return null;
  }
  if (offerPrice >= listingPrice) return null;
  return Math.round(((listingPrice - offerPrice) / listingPrice) * 100);
}

/**
 * 제출 전 가격 검사. 서버 규칙 `0 < price < 매물가`(O4)와 같다. 매물가를 모르면 하한만 본다.
 * 문제가 없으면 `undefined`.
 */
export function validateOfferPrice(price: number, listingPrice: number | null): string | undefined {
  if (!Number.isInteger(price) || price <= 0) {
    return "제안할 금액을 원 단위 숫자로 입력해 주세요.";
  }
  if (listingPrice !== null && price >= listingPrice) {
    return "판매가보다 낮은 금액만 제안할 수 있어요.";
  }
  return undefined;
}

/**
 * 만료까지 남은 시간을 "3시간 남음"·"25분 남음"으로 쓴다. 지났으면 `null`.
 * 하루를 넘으면 "2일 남음"처럼 날짜 단위로 줄인다.
 */
export function offerTimeLeftLabel(expiresAt: string, nowMs: number): string | null {
  const at = Date.parse(expiresAt);
  if (Number.isNaN(at)) return null;
  const remaining = at - nowMs;
  if (remaining <= 0) return null;
  const minutes = Math.max(1, Math.round(remaining / 60_000));
  if (minutes < 60) return `${minutes}분 남음`;
  const hours = Math.floor(minutes / 60);
  if (hours < 48) return `${hours}시간 남음`;
  return `${Math.floor(hours / 24)}일 남음`;
}
