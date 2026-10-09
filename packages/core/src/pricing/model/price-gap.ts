import type { ConditionValuation, PriceSnapshot, SetCondition } from "./types";
import { priceEvidenceWarning } from "./types";

/**
 * 매물 판매가를 같은 상태의 추정 시세 옆에 놓기 위한 값.
 *
 * 상세 화면의 시세 근거는 판매가에서 화면 두세 장 아래에 있어, 구매자가 "이 가격이 적당한가"를 판매가 옆에서
 * 판단할 수 없었다. 여기서는 판매가와 같은 등급의 추정 시세를 나란히 놓을 수 있을 때만 값을 만든다.
 *
 * - 확정 시세(ESTABLISHED)이고 그 등급의 추정이 실제 체결 표본(`grade`·`group`)에서 나왔을 때만 만든다.
 *   표본 없이 감가 모델로만 낸 추정(`model`)과 비교하면 근거 없는 판정이 되므로 만들지 않는다.
 * - 출처 경고(데모 포함·직거래 참고 등)는 그대로 들고 다녀 화면이 숨기지 않게 한다.
 */
export interface ListingPriceGap {
  readonly condition: SetCondition;
  readonly fairPrice: number;
  readonly basis: ConditionValuation["basis"];
  readonly sampleCount: number;
  /** (판매가 − 추정 시세) / 추정 시세. 양수면 판매가가 추정 시세보다 높다. */
  readonly ratio: number;
  readonly evidenceWarning: string | null;
}

/** 이 비율 안의 차이는 "비슷"으로 부른다. 추정 시세도 표본에 따라 흔들리므로 1~2% 차이를 높다·낮다고 하지 않는다. */
export const SIMILAR_PRICE_RATIO = 0.03;

export function listingPriceGap(
  price: number,
  condition: SetCondition,
  snapshot: PriceSnapshot | null,
): ListingPriceGap | null {
  if (snapshot === null || snapshot.state !== "ESTABLISHED" || !snapshot.valuation?.hasData) {
    return null;
  }
  const valuation = snapshot.valuation.conditions.find((item) => item.condition === condition);
  if (valuation === undefined || valuation.basis === "model") return null;
  if (!(valuation.fairPrice > 0) || !(price > 0)) return null;
  return {
    condition,
    fairPrice: valuation.fairPrice,
    basis: valuation.basis,
    sampleCount: valuation.sampleCount,
    ratio: (price - valuation.fairPrice) / valuation.fairPrice,
    evidenceWarning: priceEvidenceWarning(snapshot.provenance),
  };
}

/**
 * 판매가가 추정 시세와 얼마나 다른지 짧게 — "판매가 17.5% 높음" / "판매가 5.3% 낮음" / "시세와 비슷".
 * 목록 카드처럼 주어가 다른 자리에서는 `lead`를 바꾼다(예: "추정 시세보다 5.3% 낮음").
 */
export function priceGapLabel(ratio: number, lead = "판매가"): string {
  if (Math.abs(ratio) < SIMILAR_PRICE_RATIO) return "시세와 비슷";
  const percent = Math.round(Math.abs(ratio) * 1000) / 10;
  return `${lead} ${percent.toLocaleString("ko-KR")}% ${ratio > 0 ? "높음" : "낮음"}`;
}
