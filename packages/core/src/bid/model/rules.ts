/**
 * 구매 입찰 규칙 중 화면이 계산해야 하는 부분. 서버(`Bid`·`BidBook`)와 같은 값이어야 한다.
 * 판정의 정본은 서버다 — 여기서는 제출 전에 틀린 입력을 짚는 것뿐이다.
 */
import type { Bid, BidBook, BidBookCondition, BidCondition, BidStatus } from "./types";

export const BID_RULES = {
  minPrice: 1,
  maxPrice: 100_000_000,
  defaultDurationDays: 30,
  /** 진행 중 입찰 상한(D3). */
  maxActiveBids: 30,
} as const;

const STATUS_LABEL: Record<BidStatus, string> = {
  active: "진행 중",
  canceled: "취소됨",
  filled: "체결됨",
  expired: "만료됨",
};

export function bidStatusLabel(status: BidStatus): string {
  return STATUS_LABEL[status];
}

/** 문제가 없으면 `undefined`. 서버 D2 규칙과 같다. */
export function validateBidPrice(price: number): string | undefined {
  if (!Number.isInteger(price)) {
    return "입찰가를 원 단위 숫자로 입력해 주세요.";
  }
  if (price < BID_RULES.minPrice || price > BID_RULES.maxPrice) {
    return "입찰가는 1원 이상 1억 원 이하로 입력해 주세요.";
  }
  return undefined;
}

/** 같은 세트·상태의 진행 중 입찰을 다시 걸어 가격·기간만 바뀐 응답인지(D3). */
export function isBidRenewal(bid: Pick<Bid, "createdAt" | "placedAt">): boolean {
  return bid.createdAt !== bid.placedAt;
}

/** 호가창에서 한 상태의 줄. 서버가 다섯 상태를 다 주지만 빠져 있어도 빈 줄로 메운다. */
export function bidBookCondition(book: BidBook, condition: BidCondition): BidBookCondition {
  return (
    book.conditions.find((row) => row.condition === condition) ?? {
      condition,
      highestPrice: null,
      bidCount: 0,
      levels: [],
    }
  );
}

/** 호가창에 걸린 진행 중 입찰 수 합계. */
export function bidBookTotal(book: BidBook): number {
  return book.conditions.reduce((sum, row) => sum + row.bidCount, 0);
}
