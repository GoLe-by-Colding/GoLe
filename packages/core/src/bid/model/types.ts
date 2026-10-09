/**
 * 구매 입찰 도메인 타입. 백엔드 `BidDtos`와 대응. (buy-bids D1~D7)
 */

/**
 * 입찰 상태 키. 매물 상태 등급과 같은 다섯 값이다(D1). 서버가 자체 enum으로 두듯 여기서도
 * 매물 슬라이스를 가져오지 않고 따로 정의한다 — 문자열이 같으므로 매물 등급 라벨을 그대로 쓸 수 있다.
 */
export type BidCondition = "new_sealed" | "like_new" | "used_good" | "used_fair" | "damaged";

/** 좋은 상태 → 나쁜 상태. 호가창도 이 순서로 온다(D6). */
export const BID_CONDITIONS: readonly BidCondition[] = [
  "new_sealed",
  "like_new",
  "used_good",
  "used_fair",
  "damaged",
];

/** 만료를 반영한 유효 상태(D9). */
export type BidStatus = "active" | "canceled" | "filled" | "expired";

/** 입찰 기간(일). 서버가 이 셋만 받는다(D2). */
export type BidDurationDays = 7 | 30 | 60;

export const BID_DURATIONS: readonly BidDurationDays[] = [7, 30, 60];

export interface Bid {
  readonly id: string;
  readonly setNumber: string;
  readonly condition: BidCondition;
  readonly price: number;
  readonly durationDays: number;
  readonly status: BidStatus;
  /** 처음 건 시각. 같은 세트·상태로 다시 걸면(갱신) 이 값은 그대로다(D3). */
  readonly createdAt: string;
  /** 마지막으로 가격·기간을 건 시각. 처음에는 `createdAt`과 같다. 만료는 여기서 센다. */
  readonly placedAt: string;
  readonly expiresAt: string;
  /** 체결된 매물. 체결 전이면 `null`. */
  readonly filledListingId: string | null;
  /** 체결로 생긴 수락 제안. 체결 전이면 `null`. */
  readonly offerId: string | null;
}

export interface PlaceBidInput {
  readonly setNumber: string;
  readonly condition: BidCondition;
  readonly price: number;
  /** 없으면 서버 기본 30일. */
  readonly durationDays?: BidDurationDays;
}

/** 호가 한 단. 같은 가격의 입찰 수. */
export interface BidBookLevel {
  readonly price: number;
  readonly count: number;
}

export interface BidBookCondition {
  readonly condition: BidCondition;
  /** 최고 입찰가 = 지금 팔면 받는 값. 입찰이 없으면 `null`. */
  readonly highestPrice: number | null;
  readonly bidCount: number;
  /** 가격 내림차순 상위 5단. */
  readonly levels: readonly BidBookLevel[];
}

/** 공개 호가창. 입찰자 식별자는 담지 않는다(D6). */
export interface BidBook {
  readonly setNumber: string;
  readonly conditions: readonly BidBookCondition[];
}

/** 판매자 즉시 판매 결과(D7). 입찰자에게 이 가격의 수락 제안(`offerId`)이 생긴다. */
export interface FillBidResult {
  readonly bidPrice: number;
  readonly offerId: string;
  readonly listingId: string;
}

/** 서버 `BidErrors`와 같은 문자열. 화면이 사유별 문구를 고를 때 쓴다. */
export const BID_ERROR = {
  SET_NOT_FOUND: "BID_SET_NOT_FOUND",
  PRICE_INVALID: "BID_PRICE_INVALID",
  DURATION_INVALID: "BID_DURATION_INVALID",
  CONDITION_INVALID: "BID_CONDITION_INVALID",
  LIMIT_EXCEEDED: "BID_LIMIT_EXCEEDED",
  ACCESS_DENIED: "BID_ACCESS_DENIED",
  NOT_ACTIVE: "BID_NOT_ACTIVE",
  LISTING_MISMATCH: "BID_LISTING_MISMATCH",
  /** 취소할 입찰이 없을 때(404)와 즉시 판매할 입찰이 없을 때(409)가 같은 코드다. */
  NOT_FOUND: "BID_NOT_FOUND",
  LISTING_ACCESS_DENIED: "LISTING_ACCESS_DENIED",
  /** 이 매물로 이미 체결한 입찰의 제안이 아직 진행 중이다. 매물 하나는 입찰 하나만 받는다. */
  LISTING_ALREADY_FILLED: "BID_LISTING_ALREADY_FILLED",
} as const;

export type BidErrorCode = (typeof BID_ERROR)[keyof typeof BID_ERROR];
