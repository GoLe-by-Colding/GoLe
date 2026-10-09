import type { ConditionValuation, PriceSnapshot, SetCondition } from "./types";
import { priceEvidenceWarning, valuationBasisLabel } from "./types";

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

/** 판매가를 세트 시세와 견줄 수 있는지 볼 때 필요한 매물의 최소 형태. 웹·앱의 매물 타입이 그대로 들어온다. */
export interface PriceComparableListing {
  readonly category: string;
  readonly status: string;
  readonly catalogSetNumber: string | null;
}

/**
 * 이 매물의 판매가를 어느 세트의 시세와 견줄 수 있는지 — 견줄 수 없으면 `null`.
 *
 * 세트 시세는 세트 한 벌(`set` 카테고리)의 거래에서 나온다. 미니피규어·부품·MOC 매물도 출처 세트 번호를 달 수
 * 있지만, 그 가격을 세트 한 벌의 추정 시세와 견주면 "추정 시세보다 95% 낮음" 같은 엉뚱한 판정이 된다.
 * 거래가 끝난 매물(판매 완료·숨김)도 지금 살 수 있는 가격이 아니므로 견주지 않는다.
 */
export function priceComparableSetNumber(listing: PriceComparableListing): string | null {
  if (listing.category !== "set") return null;
  if (listing.status !== "active" && listing.status !== "reserved") return null;
  return listing.catalogSetNumber;
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

/**
 * 비교의 근거 한 줄 — "동일 상태 체결 19건 · 데모 포함". 근거 이름은 시세 영역과 같은 문구를 그대로 쓴다.
 * 이름 뒤에 "기준"을 덧붙이지 않는다 — 유사 등급 근거는 이미 "유사 등급 N건 기준"이라 "기준 기준"이 된다.
 */
export function priceGapBasisCaption(gap: ListingPriceGap): string {
  return [valuationBasisLabel(gap.basis, gap.sampleCount), gap.evidenceWarning]
    .filter((part): part is string => part !== null)
    .join(" · ");
}
