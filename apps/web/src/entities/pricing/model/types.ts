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

export interface TrendingSet {
  readonly setNumber: string;
  readonly name: string;
  readonly imageUrl: string | null;
  readonly tradeCount: number;
  readonly averagePrice: number;
}
