import type { PaymentRequest } from "@portone/browser-sdk/v2";
import {
  APP_PAYMENT_REQUEST,
  APP_PAYMENT_RESULT,
  APP_PAYMENT_VERSION,
  isAppPaymentResult,
  parseAppPaymentRequest,
  type AppPaymentResult,
} from "@gole/core/lib";

interface PaymentWindow extends Window {
  readonly __GOLE_PAYMENT_BRIDGE_VERSION__?: number;
  readonly ReactNativeWebView?: { postMessage: (message: string) => void };
}

/** 일반 웹은 브라우저 SDK를 쓰고, 앱만 기기 결제창을 요청한다. 구버전 앱을 외부 브라우저로 보내지 않는다. */
export function usesAppPayment(): boolean {
  return (
    /\bGoLeApp\//.test(window.navigator.userAgent) ||
    (window as PaymentWindow).__GOLE_PAYMENT_BRIDGE_VERSION__ !== undefined
  );
}

export function requestAppPayment(payment: PaymentRequest): Promise<AppPaymentResult> {
  const host = window as PaymentWindow;
  const bridge = host.ReactNativeWebView;
  if (host.__GOLE_PAYMENT_BRIDGE_VERSION__ !== APP_PAYMENT_VERSION || bridge === undefined) {
    return Promise.reject(new Error("앱을 최신 버전으로 업데이트한 뒤 결제를 다시 시도해 주세요."));
  }
  // Android 로컬 WebView(10.0.2.2의 HTTP)에는 secure-context 전용 randomUUID가 없다.
  const requestId = Array.from(crypto.getRandomValues(new Uint8Array(16)), (byte) =>
    byte.toString(16).padStart(2, "0"),
  ).join("");
  const raw = JSON.stringify({
    type: APP_PAYMENT_REQUEST,
    version: APP_PAYMENT_VERSION,
    requestId,
    payment: {
      storeId: payment.storeId,
      channelKey: payment.channelKey,
      paymentId: payment.paymentId,
      orderName: payment.orderName,
      totalAmount: payment.totalAmount,
      currency: payment.currency,
      payMethod: payment.payMethod,
      ...(payment.customer === undefined ? {} : { customer: payment.customer }),
    },
  });
  if (parseAppPaymentRequest(raw, window.location.href, window.location.origin) === null) {
    return Promise.reject(
      new Error("결제 정보를 확인할 수 없습니다. 주문 상세에서 고객지원에 문의해 주세요."),
    );
  }
  return new Promise((resolve, reject) => {
    const cleanup = () => {
      window.clearTimeout(timer);
      window.removeEventListener(APP_PAYMENT_RESULT, receive);
      window.removeEventListener("pagehide", leave);
    };
    const receive = (event: Event) => {
      const value: unknown = (event as CustomEvent<unknown>).detail;
      if (!isAppPaymentResult(value) || value.requestId !== requestId) return;
      cleanup();
      resolve(value);
    };
    const leave = () => {
      cleanup();
      reject(new Error("결제 화면을 나갔습니다. 주문 상태를 확인해 주세요."));
    };
    const timer = window.setTimeout(
      () => {
        cleanup();
        reject(
          new Error(
            "결제창 응답을 기다리는 시간이 지났습니다. 다시 결제하기 전에 주문 상태를 확인해 주세요.",
          ),
        );
      },
      15 * 60 * 1000,
    );
    window.addEventListener(APP_PAYMENT_RESULT, receive);
    window.addEventListener("pagehide", leave);
    try {
      bridge.postMessage(raw);
    } catch {
      cleanup();
      reject(new Error("앱 결제창을 열지 못했습니다. 잠시 후 다시 시도해 주세요."));
    }
  });
}
