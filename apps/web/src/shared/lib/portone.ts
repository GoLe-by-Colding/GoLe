import * as PortOne from "@portone/browser-sdk/v2";
import type { PaymentRequest, PaymentResponse } from "@portone/browser-sdk/v2";
import { env } from "@shared/config";

/** PortOne V2 카카오페이 오류. 사용자 취소와 실제 장애를 UI에서 구분한다. */
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
    // 없다. 카카오페이는 창을 닫으면 "사용자가 프로세스를 중단하였습니다"를 주는데, cancel·취소만
    // 보던 탓에 취소로 분류되지 않아 원문이 그대로 화면에 노출됐다.
    // 빗나가도 호출자가 "주문은 보존된다"는 안내는 하도록 되어 있다.
    this.userCancelled = /cancel|취소|중단/i.test(reason);
  }
}

export interface PortOnePayParams {
  readonly paymentId: string;
  readonly orderName: string;
  readonly totalAmount: number;
}

export function isPortOneEnabled(): boolean {
  return env.paymentMode !== "stub" && getPortOneConfigurationError() === undefined;
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
 * 카카오페이 직연동용 요청을 한 곳에서 만든다.
 * 카카오페이는 EASY_PAY/KRW만 사용하며, PC는 iframe·모바일은 redirect로 복귀한다.
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
  if (!Number.isSafeInteger(params.totalAmount) || params.totalAmount <= 0) {
    throw new Error("결제 금액이 올바르지 않습니다.");
  }

  const redirectUrl = new URL("/payments/portone/return", origin);
  return {
    storeId: env.portOneStoreId,
    channelKey: env.portOneChannelKey,
    paymentId: params.paymentId,
    orderName: params.orderName,
    totalAmount: params.totalAmount,
    currency: "KRW",
    payMethod: "EASY_PAY",
    locale: "KO_KR",
    windowType: { pc: "IFRAME", mobile: "REDIRECTION" },
    redirectUrl: redirectUrl.toString(),
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
