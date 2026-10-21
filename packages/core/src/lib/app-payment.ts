/** RN과 웹 사이의 결제창 요청 계약. 인증·승인 권한은 이 메시지에 포함하지 않는다. */
export const APP_PAYMENT_VERSION = 1;
export const APP_PAYMENT_REQUEST = "gole:payment";
export const APP_PAYMENT_RESULT = "gole:payment-result";

export interface AppPaymentRequest {
  readonly type: typeof APP_PAYMENT_REQUEST;
  readonly version: typeof APP_PAYMENT_VERSION;
  readonly requestId: string;
  readonly payment: {
    readonly storeId: string;
    readonly channelKey: string;
    readonly paymentId: string;
    readonly orderName: string;
    readonly totalAmount: number;
    readonly currency: "KRW";
    readonly payMethod: "CARD" | "EASY_PAY";
    readonly customer?: {
      readonly fullName: string;
      readonly email: string;
      readonly phoneNumber: string;
    };
  };
}

export interface AppPaymentResult {
  readonly requestId: string;
  readonly paymentId: string;
  readonly code?: string;
  readonly message?: string;
}

function record(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function onlyKeys(value: Record<string, unknown>, keys: readonly string[]): boolean {
  return Object.keys(value).every((key) => keys.includes(key));
}

/** SDK가 요청을 HTML script 안에 직렬화하므로 HTML 구분자와 제어 문자를 받지 않는다. */
function text(value: unknown, max = 200): value is string {
  return (
    typeof value === "string" &&
    value.trim().length > 0 &&
    value.length <= max &&
    !/[<>\u0000-\u001f\u2028\u2029]/.test(value)
  );
}

export function isPaymentId(value: unknown): value is string {
  return typeof value === "string" && /^[a-zA-Z0-9_-]{1,100}$/.test(value);
}

/** 신뢰 원점의 해당 주문 상세에서 보낸 고정된 요청만 받는다. 임의 URL·스크립트는 전달하지 않는다. */
export function parseAppPaymentRequest(
  data: unknown,
  sourceUrl: string,
  origin: string,
): AppPaymentRequest | null {
  if (typeof data !== "string" || data.length > 4096) return null;
  try {
    const source = new URL(sourceUrl);
    if (source.origin !== origin || source.username || source.password) return null;
    const request: unknown = JSON.parse(data);
    if (
      !record(request) ||
      !onlyKeys(request, ["type", "version", "requestId", "payment"]) ||
      request.type !== APP_PAYMENT_REQUEST ||
      request.version !== APP_PAYMENT_VERSION ||
      !isPaymentId(request.requestId)
    )
      return null;
    const p = request.payment;
    if (
      !record(p) ||
      !onlyKeys(p, [
        "storeId",
        "channelKey",
        "paymentId",
        "orderName",
        "totalAmount",
        "currency",
        "payMethod",
        "customer",
      ])
    )
      return null;
    if (
      !isPaymentId(p.paymentId) ||
      source.pathname !== `/orders/${p.paymentId}` ||
      !text(p.storeId) ||
      !text(p.channelKey) ||
      !text(p.orderName, 256)
    )
      return null;
    if (
      typeof p.totalAmount !== "number" ||
      !Number.isSafeInteger(p.totalAmount) ||
      p.totalAmount <= 0 ||
      p.totalAmount > 1_000_000_000 ||
      p.currency !== "KRW" ||
      (p.payMethod !== "CARD" && p.payMethod !== "EASY_PAY")
    )
      return null;
    if (p.customer !== undefined) {
      const c = p.customer;
      if (
        !record(c) ||
        !onlyKeys(c, ["fullName", "email", "phoneNumber"]) ||
        !text(c.fullName, 100) ||
        !text(c.email, 254) ||
        !text(c.phoneNumber, 30)
      )
        return null;
    } else if (p.payMethod === "CARD") return null;
    return request as unknown as AppPaymentRequest;
  } catch {
    return null;
  }
}

export function isAppPaymentResult(value: unknown): value is AppPaymentResult {
  return (
    record(value) &&
    onlyKeys(value, ["requestId", "paymentId", "code", "message"]) &&
    isPaymentId(value.requestId) &&
    isPaymentId(value.paymentId) &&
    (value.code === undefined || (typeof value.code === "string" && value.code.length <= 100)) &&
    (value.message === undefined ||
      (typeof value.message === "string" && value.message.length <= 500))
  );
}
