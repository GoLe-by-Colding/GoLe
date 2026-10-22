import { APP_PAYMENT_RESULT, APP_PAYMENT_VERSION, type AppPaymentResult } from "@gole/core/lib";

export const paymentBridgeScript = `window.__GOLE_PAYMENT_BRIDGE_VERSION__=${APP_PAYMENT_VERSION};true;`;

/** 원래 주문 문서에서만 결과를 알린다. 결과는 승인 토큰이 아니며 웹이 서버에 재확인한다. */
export function paymentResultScript(result: AppPaymentResult, origin: string): string {
  const detail = JSON.stringify(result).replace(/</g, "\\u003c");
  return `if(window.location.origin===${JSON.stringify(origin)}&&window.location.pathname===${JSON.stringify(`/orders/${result.paymentId}`)}){window.dispatchEvent(new CustomEvent(${JSON.stringify(APP_PAYMENT_RESULT)},{detail:${detail}}));}true;`;
}
