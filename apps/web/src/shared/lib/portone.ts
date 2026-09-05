import * as PortOne from "@portone/browser-sdk/v2";
import type { PaymentRequest, PaymentResponse } from "@portone/browser-sdk/v2";
import {
  requireCardCustomer,
  requireValidAmount,
  resolveChannel,
  type PortOneCustomer,
  type PortOneMethod,
} from "@gole/core";
import { env } from "@shared/config";

export type { PortOneCustomer, PortOneMethod };

/** PortOne V2 결제 오류. 사용자 취소와 실제 장애를 UI에서 구분한다. */
export class PortOnePaymentError extends Error {
  readonly code: string | undefined;
  readonly pgCode: string | undefined;
  readonly pgMessage: string | undefined;
  readonly userCancelled: boolean;

  constructor(response: Pick<PaymentResponse, "code" | "message" | "pgCode" | "pgMessage">) {
    super(response.message ?? response.pgMessage ?? "결제를 완료하지 못했습니다.");
    this.name = "PortOnePaymentError";
    this.code = response.code;
    this.pgCode = response.pgCode;
    this.pgMessage = response.pgMessage;
    const reason = `${response.code ?? ""} ${response.message ?? ""} ${response.pgMessage ?? ""}`;
    // 문구 기반 판정이다. SDK가 code를 열거형 없이 string으로만 두기 때문에 코드 목록에 의존할 수
    // 없다. 카카오페이는 창을 닫으면 "사용자가 프로세스를 중단하였습니다"를, 이니시스는
    // "사용자가 결제를 취소하였습니다"를 주는데, cancel·취소만
    // 보던 탓에 취소로 분류되지 않아 원문이 그대로 화면에 노출됐다.
    // 빗나가도 호출자가 "주문은 보존된다"는 안내는 하도록 되어 있다.
    this.userCancelled = /cancel|취소|중단/i.test(reason);
  }
}

export interface PortOnePayParams {
  readonly paymentId: string;
  readonly orderName: string;
  readonly totalAmount: number;
  readonly method: PortOneMethod;
  /** method가 "card"이면 필수. 없으면 결제창을 열지 않고 던진다. */
  readonly customer?: PortOneCustomer;
}

export function isPortOneEnabled(): boolean {
  return env.paymentMode !== "stub" && getPortOneConfigurationError() === undefined;
}

/**
 * 카드 결제를 노출해도 되는지. 채널 키가 없으면 카드는 닫힌 것이다 — 오류가 아니다.
 * 서버도 같은 상태에서 카드 원장을 승인하지 않으므로 화면과 검증이 함께 닫힌다.
 */
export function isCardPaymentAvailable(): boolean {
  return isPortOneEnabled() && env.portOneCardChannelKey.length > 0;
}

/** 공개 브라우저 설정 누락을 조용히 스텁 결제로 우회하지 않고 화면에 드러낸다. */
export function getPortOneConfigurationError(): string | undefined {
  if (env.paymentMode === "stub") {
    return undefined;
  }
  if (env.portOneStoreId.length === 0) {
    return "결제 상점 설정이 누락되었습니다. 잠시 후 다시 시도해 주세요.";
  }
  if (env.portOneChannelKey.length === 0) {
    return "카카오페이 결제 채널 설정이 누락되었습니다. 잠시 후 다시 시도해 주세요.";
  }
  return undefined;
}

/**
 * 결제 요청을 한 곳에서 만든다. 모든 결제는 KRW이며 PC는 iframe·모바일은 redirect로 복귀한다.
 *
 * 채널 키와 payMethod를 정하는 규칙, 금액·구매자 정보 검증은 <b>`@gole/core`가 갖는다</b> —
 * 서버가 그 짝을 검증하므로 앱과 웹이 갈라지면 결제가 전부 수동 검토로 떨어진다.
 * 여기 남은 것은 브라우저 SDK에만 있는 것(창 유형·복귀 URL)뿐이다.
 */
export function buildPortOnePaymentRequest(
  params: PortOnePayParams,
  origin = typeof window === "undefined" ? undefined : window.location.origin,
): PaymentRequest {
  if (origin === undefined) {
    throw new Error("PortOne SDK는 브라우저에서만 사용할 수 있습니다.");
  }
  const configurationError = getPortOneConfigurationError();
  if (configurationError !== undefined) {
    throw new Error(configurationError);
  }
  const totalAmount = requireValidAmount(params.totalAmount);
  const { channelKey, payMethod } = resolveChannel(params.method, {
    kakaopay: env.portOneChannelKey,
    card: env.portOneCardChannelKey,
  });

  const redirectUrl = new URL("/payments/portone/return", origin);
  return {
    storeId: env.portOneStoreId,
    channelKey,
    paymentId: params.paymentId,
    orderName: params.orderName,
    totalAmount,
    currency: "KRW",
    payMethod,
    locale: "KO_KR",
    windowType: { pc: "IFRAME", mobile: "REDIRECTION" },
    redirectUrl: redirectUrl.toString(),
    ...(params.method === "card" ? { customer: requireCardCustomer(params.customer) } : {}),
  };
}

/** 결제창 성공 응답 뒤에도 반드시 서버의 PortOne 원장 검증을 거쳐야 한다. */
export async function requestPortOnePayment(params: PortOnePayParams): Promise<void> {
  const result = await PortOne.requestPayment(buildPortOnePaymentRequest(params));
  if (result === undefined) {
    throw new Error(
      "결제창 응답을 확인하지 못했습니다. 중복 결제하지 말고 주문 상태를 확인해 주세요.",
    );
  }
  if (result.code !== undefined) {
    throw new PortOnePaymentError(result);
  }
  if (result.paymentId !== params.paymentId) {
    throw new Error(
      "결제 응답의 주문번호가 일치하지 않습니다. 중복 결제하지 말고 고객지원에 문의해 주세요.",
    );
  }
}
