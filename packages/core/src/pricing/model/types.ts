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
  readonly source: PriceTransactionSource;
  readonly condition: SetCondition;
}

export type MarketDataState = "EMPTY" | "OBSERVATIONS_ONLY" | "ESTABLISHED";

export type PriceTransactionSource =
  | "platform_payment"
  | "platform_test"
  | "direct_trade"
  | "demo_seed"
  | "legacy_unverified";

export type PriceProvenanceMode =
  | "NONE"
  | "FIRST_PARTY"
  | "DIRECT_TRADE"
  | "DEMO"
  | "LEGACY_UNVERIFIED"
  | "MIXED";

export interface PriceProvenance {
  readonly mode: PriceProvenanceMode;
  readonly includedSources: readonly PriceTransactionSource[];
  readonly demo: boolean;
}

/** 공개 화면에서 1차 결제 증빙이 아닌 표본을 숨기지 않기 위한 경고 문구. */
export function priceEvidenceWarning(provenance: PriceProvenance): string | null {
  if (provenance.demo) return "데모 포함";
  switch (provenance.mode) {
    case "DIRECT_TRADE":
      return "직거래 참고";
    case "LEGACY_UNVERIFIED":
      return "출처 확인 전";
    case "MIXED":
      return "혼합 출처 · 참고용";
    case "NONE":
    case "FIRST_PARTY":
    case "DEMO":
      return provenance.mode === "DEMO" ? "데모 데이터" : null;
  }
}

export type SetCondition = "new_sealed" | "like_new" | "used_good" | "used_fair" | "damaged";

/**
 * 공정가를 무엇에 근거해 냈는지. 값이 아니라 근거의 강도다.
 *
 * - `grade` 해당 등급 체결 표본이 충분 → 그 중앙값
 * - `group` 등급 표본 부족 → 같은 집계 그룹 체결을 앵커로 환산
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

/** 표본 단계·실제 관측·파생 통계를 한 응답의 동일한 증빙 집합으로 묶는다. */
export interface PriceSnapshot {
  readonly setNumber: string;
  readonly state: MarketDataState;
  readonly minimumSamples: number;
  readonly sampleCount: number;
  /** 최신 체결부터 정렬된 관측 내역. 차트는 이 목록을 뒤집어 동일 snapshot을 사용한다. */
  readonly observations: readonly PricePoint[];
  readonly statistics: PriceStatistics | null;
  readonly valuation: PriceValuation | null;
  readonly provenance: PriceProvenance;
}

export const CONDITION_LABEL: Record<SetCondition, string> = {
  new_sealed: "미개봉 새상품",
  like_new: "거의 새것",
  used_good: "중고 · 양호",
  used_fair: "중고 · 사용감",
  damaged: "하자 있음",
};

/**
 * 근거를 사람 말로. 출처 검증 여부는 snapshot provenance가 별도로 표시하므로 이 문구는
 * 관측된 체결 표본의 범위만 설명하고 `실거래`라고 단정하지 않는다.
 *
 * @param sampleCount 공정가 산출에 실제로 쓰인 체결 건수
 */
export function valuationBasisLabel(basis: ValuationBasis, sampleCount: number): string {
  switch (basis) {
    case "grade":
      return `동일 상태 체결 ${sampleCount}건`;
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
