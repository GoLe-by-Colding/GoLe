/**
 * 포트원(PortOne) V2 결제 요청 페이로드 조립.
 *
 * 의존성이 없는 순수 모듈이다. 브라우저 SDK 로드와 실제 호출은 `portone.ts`가 맡고,
 * 여기서는 "무엇을 보낼지"만 결정한다. 결제 계정 없이 검증할 수 있는 유일한 구간이라
 * 이 경계를 일부러 분리했다.
 */

/** 사용자가 고를 수 있는 결제수단. 늘리려면 여기와 라벨·채널 키에 한 줄씩 추가한다. */
export type PaymentMethodChoice = "CARD" | "KAKAOPAY";

/** 결제수단 선택지의 화면 표기. */
export const PAYMENT_CHOICE_LABEL: Readonly<Record<PaymentMethodChoice, string>> = {
  CARD: "카드",
  KAKAOPAY: "카카오페이",
};

export const PAYMENT_CHOICES: readonly PaymentMethodChoice[] = ["CARD", "KAKAOPAY"];

/**
 * 결제수단별 채널 키.
 *
 * <p>포트원은 <b>PG사마다 채널을 따로</b> 만든다(카드=KG이니시스, 카카오페이=카카오페이).
 * 그래서 결제수단을 고른다는 것은 곧 채널을 고른다는 뜻이고, 채널 키 하나로 모든 수단을
 * 처리할 수 없다. 설정되지 않은 수단은 빈 문자열이다.
 */
export type PortOneChannelKeys = Readonly<Record<PaymentMethodChoice, string>>;

export interface PortOnePayParams {
  readonly paymentId: string;
  readonly orderName: string;
  readonly totalAmount: number;
  readonly method: PaymentMethodChoice;
  /**
   * 모바일 결제창에서 돌아올 주소.
   *
   * 모바일은 결제창이 별도 페이지로 이동했다가 복귀하는 방식이라 이 값이 없으면
   * 결제를 마친 사용자가 빈 화면에 남는다. 데스크톱에서만 테스트하면 안 보인다.
   */
  readonly redirectUrl: string;
}

export interface PortOneClientConfig {
  readonly storeId: string;
  readonly channelKeys: PortOneChannelKeys;
}

/**
 * 채널 키가 설정된 결제수단만 남긴다.
 *
 * <p>채널이 없는 수단을 선택지에 띄우면 사용자가 고를 수는 있지만 결제창이 열리지 않는다.
 * 고를 수 없는 것은 보여주지 않는다.
 */
export function availablePaymentChoices(
  channelKeys: PortOneChannelKeys,
): readonly PaymentMethodChoice[] {
  return PAYMENT_CHOICES.filter((choice) => channelKeys[choice].length > 0);
}

/** 결제수단 선택을 포트원 V2 요청 형태로 옮긴다. */
export function buildPortOneRequest(
  params: PortOnePayParams,
  config: PortOneClientConfig,
): Record<string, unknown> {
  const channelKey = config.channelKeys[params.method];
  if (channelKey.length === 0) {
    // 빈 채널 키를 그대로 넘기면 포트원이 알아보기 힘든 오류를 돌려준다. 이건 결제 실패가
    // 아니라 설정 누락이므로, 원인이 드러나는 자리에서 끊는다.
    throw new Error(`${PAYMENT_CHOICE_LABEL[params.method]} 결제 채널이 설정되지 않았습니다.`);
  }

  const base: Record<string, unknown> = {
    storeId: config.storeId,
    channelKey,
    paymentId: params.paymentId,
    orderName: params.orderName,
    totalAmount: params.totalAmount,
    currency: "CURRENCY_KRW",
    redirectUrl: params.redirectUrl,
  };

  // 간편결제 사업자(easyPayProvider)는 싣지 않는다. 카카오페이는 PG사 자체가 간편결제사라
  // 전용 채널이 이미 사업자를 결정하고, 포트원도 이 값을 무시한다. 여러 사업자를 한 채널로
  // 묶는 PG(예: 토스페이먼츠)로 옮길 때만 다시 필요해진다.
  return { ...base, payMethod: params.method === "CARD" ? "CARD" : "EASY_PAY" };
}
