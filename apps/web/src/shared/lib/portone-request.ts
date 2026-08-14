/**
 * 포트원(PortOne) V2 결제 요청 페이로드 조립.
 *
 * 의존성이 없는 순수 모듈이다. 브라우저 SDK 로드와 실제 호출은 `portone.ts`가 맡고,
 * 여기서는 "무엇을 보낼지"만 결정한다. 결제 계정 없이 검증할 수 있는 유일한 구간이라
 * 이 경계를 일부러 분리했다.
 */

/** 사용자가 고를 수 있는 결제수단. 늘리려면 EASY_PAY_PROVIDER에 한 줄 추가하면 된다. */
export type PaymentMethodChoice = "CARD" | "KAKAOPAY";

/**
 * 간편결제 사업자 식별자.
 *
 * ⚠️ 포트원 콘솔에서 채널을 만들 때 표기가 다르면 <b>여기만</b> 고치면 된다.
 * 실제 채널 없이는 확인할 수 없어 문서 표기를 따랐다.
 */
export const EASY_PAY_PROVIDER = {
  KAKAOPAY: "KAKAOPAY",
} as const;

/** 결제수단 선택지의 화면 표기. */
export const PAYMENT_CHOICE_LABEL: Readonly<Record<PaymentMethodChoice, string>> = {
  CARD: "카드",
  KAKAOPAY: "카카오페이",
};

export const PAYMENT_CHOICES: readonly PaymentMethodChoice[] = ["CARD", "KAKAOPAY"];

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
  readonly channelKey: string;
}

/** 결제수단 선택을 포트원 V2 요청 형태로 옮긴다. */
export function buildPortOneRequest(
  params: PortOnePayParams,
  config: PortOneClientConfig,
): Record<string, unknown> {
  const base: Record<string, unknown> = {
    storeId: config.storeId,
    channelKey: config.channelKey,
    paymentId: params.paymentId,
    orderName: params.orderName,
    totalAmount: params.totalAmount,
    currency: "CURRENCY_KRW",
    redirectUrl: params.redirectUrl,
  };

  if (params.method === "CARD") {
    return { ...base, payMethod: "CARD" };
  }

  // 간편결제는 payMethod만으로 부족하다. 사업자를 지정하지 않으면 결제창이 카드로
  // 뜨거나 사업자 선택 단계가 한 번 더 끼어든다.
  return {
    ...base,
    payMethod: "EASY_PAY",
    easyPay: { easyPayProvider: EASY_PAY_PROVIDER[params.method] },
  };
}
