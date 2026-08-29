export interface SellerFeePolicy {
  /** 0~1 사이의 판매 수수료율. 예: 0.05 = 5%. */
  readonly rate: number;
  /** 최소 수수료(원). 0이면 하한 없음. */
  readonly minFee: number;
  /** 최대 수수료(원). 0이면 상한 없음. */
  readonly maxFee: number;
}

export interface SellerPayoutEstimate {
  readonly grossAmount: number;
  readonly fee: number;
  readonly payout: number;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function isNonNegativeSafeInteger(value: unknown): value is number {
  return typeof value === "number" && Number.isSafeInteger(value) && value >= 0;
}

/** 공개 API 응답을 금액 계산에 써도 되는 정책으로 검증한다. */
export function parseSellerFeePolicy(value: unknown): SellerFeePolicy {
  if (!isRecord(value)) {
    throw new Error("판매 수수료 정책 응답 형식이 올바르지 않습니다.");
  }

  const { rate, minFee, maxFee } = value;
  if (
    typeof rate !== "number" ||
    !Number.isFinite(rate) ||
    rate < 0 ||
    rate > 1 ||
    !isNonNegativeSafeInteger(minFee) ||
    !isNonNegativeSafeInteger(maxFee) ||
    (maxFee > 0 && minFee > maxFee)
  ) {
    throw new Error("판매 수수료 정책 값이 올바르지 않습니다.");
  }

  return Object.freeze({ rate, minFee, maxFee });
}

/**
 * 백엔드 FeePolicy와 같은 순서로 예상 정산액을 계산한다.
 *
 * 반올림 -> 최소 수수료 -> 최대 수수료(0이면 미적용) -> 거래액 한도 순서다.
 */
export function calculateSellerPayout(
  grossAmount: number,
  policy: SellerFeePolicy,
): SellerPayoutEstimate {
  if (!Number.isSafeInteger(grossAmount) || grossAmount < 0) {
    throw new Error("판매 금액은 0 이상의 원 단위 정수여야 합니다.");
  }

  if (grossAmount === 0) {
    return { grossAmount, fee: 0, payout: 0 };
  }

  let fee = Math.round(grossAmount * policy.rate);
  fee = Math.max(fee, policy.minFee);
  if (policy.maxFee > 0) {
    fee = Math.min(fee, policy.maxFee);
  }
  fee = Math.min(fee, grossAmount);

  return { grossAmount, fee, payout: grossAmount - fee };
}
