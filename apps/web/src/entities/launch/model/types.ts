export type LaunchStage = 0 | 1 | 2 | 3;
export type TradeMode = "DIRECT_CHAT" | "MANUAL_SETTLEMENT" | "PARTNER_PAYOUT";

export interface LaunchFeatures {
  readonly payments: boolean;
  readonly reviews: boolean;
  readonly partnerPayout: boolean;
}

export interface LaunchConfig {
  readonly stage: LaunchStage;
  readonly tradeMode: TradeMode;
  readonly features: LaunchFeatures;
  /** 배포 래치와 판매자 확인 절차가 모두 준비됐을 때만 true. 누락은 false로 해석한다. */
  readonly sellerIdentityVerificationReady: boolean;
  readonly updatedAt: string | null;
}

const TRADE_MODE_BY_STAGE = {
  0: "DIRECT_CHAT",
  1: "DIRECT_CHAT",
  2: "MANUAL_SETTLEMENT",
  3: "PARTNER_PAYOUT",
} as const satisfies Record<LaunchStage, TradeMode>;

/**
 * 설정 API가 잠시 실패해도 결제 기능을 추측해서 열지 않는다.
 * 커뮤니티·채팅만 가능한 Stage 0이 유일한 안전 기본값이다.
 */
export const SAFE_LAUNCH_CONFIG: LaunchConfig = Object.freeze({
  stage: 0,
  tradeMode: "DIRECT_CHAT",
  features: Object.freeze({
    payments: false,
    reviews: false,
    partnerPayout: false,
  }),
  sellerIdentityVerificationReady: false,
  updatedAt: null,
});

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function isLaunchStage(value: unknown): value is LaunchStage {
  return value === 0 || value === 1 || value === 2 || value === 3;
}

function isTradeMode(value: unknown): value is TradeMode {
  return value === "DIRECT_CHAT" || value === "MANUAL_SETTLEMENT" || value === "PARTNER_PAYOUT";
}

function isUpdatedAt(value: unknown): value is string | null {
  return value === null || (typeof value === "string" && Number.isFinite(Date.parse(value)));
}

function isSafeFeatureCombination(stage: LaunchStage, features: LaunchFeatures): boolean {
  // 직거래 단계에서 결제가 열리거나, 지급대행 계약 전 자동지급이 열리면 돈의 흐름이
  // 화면과 서버 사이에서 달라진다. 자동지급은 결제가 함께 열려 있을 때만 의미가 있다.
  if (stage < 2 && features.payments) return false;
  if (stage < 3 && features.partnerPayout) return false;
  if (features.partnerPayout && !features.payments) return false;
  return true;
}

/**
 * 신뢰할 수 없는 공개 API 응답을 프론트 계약으로 좁힌다.
 *
 * 기능 override는 유효한 운영 수단이므로 단계 기본값과 다르다는 이유만으로 거부하지 않는다.
 * 대신 단계에서 파생되는 거래 모델과 실제로 돈을 열어 버리는 모순만 fail-closed 처리한다.
 */
export function parseLaunchConfig(value: unknown): LaunchConfig {
  if (!isRecord(value) || !isLaunchStage(value.stage) || !isTradeMode(value.tradeMode)) {
    return SAFE_LAUNCH_CONFIG;
  }

  if (value.tradeMode !== TRADE_MODE_BY_STAGE[value.stage] || !isRecord(value.features)) {
    return SAFE_LAUNCH_CONFIG;
  }

  const { payments, reviews, partnerPayout } = value.features;
  if (
    typeof payments !== "boolean" ||
    typeof reviews !== "boolean" ||
    typeof partnerPayout !== "boolean" ||
    !isUpdatedAt(value.updatedAt)
  ) {
    return SAFE_LAUNCH_CONFIG;
  }

  const features: LaunchFeatures = Object.freeze({ payments, reviews, partnerPayout });
  if (!isSafeFeatureCombination(value.stage, features)) {
    return SAFE_LAUNCH_CONFIG;
  }

  return Object.freeze({
    stage: value.stage,
    tradeMode: value.tradeMode,
    features,
    // 롤링 배포 중 구버전 API 응답의 필드 누락도 판매 개방으로 추측하지 않는다.
    sellerIdentityVerificationReady: value.sellerIdentityVerificationReady === true,
    updatedAt: value.updatedAt,
  });
}
