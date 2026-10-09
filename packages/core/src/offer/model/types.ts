/**
 * 가격 제안(네고) 도메인 타입. 백엔드 `OfferResponse`와 1:1 대응. (price-offer O15)
 */

/**
 * 유효 상태. 서버가 읽을 때 만료를 계산해 내려주므로 `expired`도 응답에 그대로 온다(O12).
 * 다만 화면에 오래 떠 있는 동안 만료 시각이 지날 수 있어 {@link isOfferUsable}이 한 번 더 본다.
 */
export type OfferStatus = "pending" | "accepted" | "declined" | "withdrawn" | "expired";

/** 제안이 생긴 경로. 입찰 체결로 생긴 제안(`bid`)은 대화방이 없다. */
export type OfferOrigin = "chat" | "bid";

export interface PriceOffer {
  readonly id: string;
  readonly listingId: string;
  /** 입찰에서 온 제안은 `null`. */
  readonly roomId: string | null;
  readonly buyerId: string;
  readonly sellerId: string;
  readonly price: number;
  /** 제안 시점의 매물가. 할인율 표시에 쓴다. */
  readonly listingPriceAtOffer: number;
  readonly origin: OfferOrigin;
  readonly status: OfferStatus;
  readonly createdAt: string;
  /** 대기 중이면 `null`. */
  readonly respondedAt: string | null;
  readonly expiresAt: string;
}

/**
 * 서버가 내려주는 오류 코드. 화면이 사유별 문구를 고를 때 쓴다. 서버 `OfferErrors`와 같은 문자열이다.
 *
 * `NOT_USABLE`은 제안 API가 아니라 주문 생성(`POST /api/v1/orders`)이 돌려준다 — 실어 보낸 `offerId`가
 * 이 매물·이 구매자의 유효한 수락 제안이 아닐 때다(O16).
 */
export const OFFER_ERROR = {
  ROOM_NOT_LISTING: "OFFER_ROOM_NOT_LISTING",
  BUYER_ONLY: "OFFER_BUYER_ONLY",
  LISTING_UNAVAILABLE: "OFFER_LISTING_UNAVAILABLE",
  PRICE_INVALID: "OFFER_PRICE_INVALID",
  ALREADY_PENDING: "OFFER_ALREADY_PENDING",
  RATE_LIMITED: "OFFER_RATE_LIMITED",
  NOT_PENDING: "OFFER_NOT_PENDING",
  NOT_OPEN: "OFFER_NOT_OPEN",
  ACCESS_DENIED: "OFFER_ACCESS_DENIED",
  NOT_FOUND: "OFFER_NOT_FOUND",
  QUERY_REQUIRED: "OFFER_QUERY_REQUIRED",
  NOT_USABLE: "OFFER_NOT_USABLE",
} as const;

export type OfferErrorCode = (typeof OFFER_ERROR)[keyof typeof OFFER_ERROR];
