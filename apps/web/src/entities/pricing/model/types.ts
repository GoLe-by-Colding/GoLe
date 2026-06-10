/**
 * 시세 도메인 타입. 백엔드 PricingResponses와 대응.
 */
export interface PriceStatistics {
  readonly setNumber: string;
  readonly hasData: boolean;
  readonly latestPrice: number | null;
  readonly highestPrice: number | null;
  readonly lowestPrice: number | null;
  readonly transactionCount: number;
}

export interface PricePoint {
  readonly price: number;
  readonly quantity: number;
  readonly executedAt: string;
}

export type SetCondition = "new_sealed" | "used_complete" | "used_incomplete";

export interface ConditionValuation {
  readonly condition: SetCondition;
  readonly depreciationPct: number;
  readonly fairPrice: number;
  readonly sellPrice: number;
  readonly buyPrice: number;
}

export interface PriceValuation {
  readonly setNumber: string;
  readonly hasData: boolean;
  readonly marketPrice: number | null;
  readonly conditions: readonly ConditionValuation[];
}

export const CONDITION_LABEL: Record<SetCondition, string> = {
  new_sealed: "미개봉 새상품",
  used_complete: "중고 · 풀세트",
  used_incomplete: "중고 · 부품빠짐",
};

export interface TrendingSet {
  readonly setNumber: string;
  readonly name: string;
  readonly imageUrl: string | null;
  readonly tradeCount: number;
  readonly averagePrice: number;
}
