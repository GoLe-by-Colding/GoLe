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

export type SetCondition = "new_sealed" | "like_new" | "used_good" | "used_fair" | "damaged";

/**
 * 공정가를 무엇에 근거해 냈는지. 값이 아니라 근거의 강도다.
 *
 * - `grade` 해당 등급 실거래가 충분 → 그 중앙값
 * - `group` 등급 표본 부족 → 같은 집계 그룹 실거래를 앵커로 환산
 * - `model` 표본 없음 → 미개봉 시세 × 감가 계수
 */
export type ValuationBasis = "grade" | "group" | "model";

export interface ConditionValuation {
  readonly condition: SetCondition;
  readonly basis: ValuationBasis;
  readonly depreciationPct: number;
  readonly fairPrice: number;
  readonly sellPrice: number;
  readonly buyPrice: number;
  readonly sampleCount: number;
  /** `basis !== "model"`. 기존 클라이언트 호환 필드. 새 코드는 `basis`를 쓴다. */
  readonly basedOnRealData: boolean;
}

export interface PriceValuation {
  readonly setNumber: string;
  readonly hasData: boolean;
  readonly marketPrice: number | null;
  readonly conditions: readonly ConditionValuation[];
}

export const CONDITION_LABEL: Record<SetCondition, string> = {
  new_sealed: "미개봉 새상품",
  like_new: "거의 새것",
  used_good: "중고 · 양호",
  used_fair: "중고 · 사용감",
  damaged: "하자 있음",
};

/**
 * 근거를 사람 말로. 숫자만 보여주고 근거를 감추면 표본 1건 추정과 실거래 50건이 같아 보인다.
 *
 * @param sampleCount 공정가 산출에 실제로 쓰인 체결 건수
 */
export function valuationBasisLabel(basis: ValuationBasis, sampleCount: number): string {
  switch (basis) {
    case "grade":
      return `실거래 ${sampleCount}건`;
    case "group":
      return `유사 등급 ${sampleCount}건 기준`;
    case "model":
      return "추정";
  }
}

/** 근거 강도에 따른 텍스트 색. 강할수록 또렷하게. */
export function valuationBasisTone(basis: ValuationBasis): string {
  switch (basis) {
    case "grade":
      return "text-success";
    case "group":
      return "text-neutral-500";
    case "model":
      return "text-neutral-400";
  }
}

export interface TrendingSet {
  readonly setNumber: string;
  readonly name: string;
  readonly imageUrl: string | null;
  readonly tradeCount: number;
  readonly averagePrice: number;
}
