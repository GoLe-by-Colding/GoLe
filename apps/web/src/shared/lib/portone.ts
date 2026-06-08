import { env } from "@shared/config";

/**
 * 포트원(PortOne) V2 브라우저 결제 연동.
 *
 * npm 의존성 없이 공식 CDN SDK(window.PortOne)를 동적 로드한다.
 * 환경변수(NEXT_PUBLIC_PORTONE_STORE_ID / NEXT_PUBLIC_PORTONE_CHANNEL_KEY)가 모두 설정된 경우에만 활성.
 * 결제는 브라우저에서 수행하고 서버가 결과를 검증한다(verify-on-server). paymentId 는 주문 id를 사용한다.
 */

const SDK_URL = "https://cdn.portone.io/v2/browser-sdk.js";

interface PortOnePaymentResult {
  readonly code?: string;
  readonly message?: string;
  readonly paymentId?: string;
  readonly txId?: string;
}

interface PortOneSdk {
  requestPayment(request: Record<string, unknown>): Promise<PortOnePaymentResult>;
}

declare global {
  interface Window {
    PortOne?: PortOneSdk;
  }
}

export function isPortOneEnabled(): boolean {
  return env.portOneStoreId.length > 0 && env.portOneChannelKey.length > 0;
}

async function loadSdk(): Promise<PortOneSdk> {
  if (typeof window === "undefined") {
    throw new Error("PortOne SDK는 브라우저에서만 사용할 수 있습니다.");
  }
  if (window.PortOne) {
    return window.PortOne;
  }
  await new Promise<void>((resolve, reject) => {
    const existing = document.querySelector<HTMLScriptElement>(`script[src="${SDK_URL}"]`);
    if (existing) {
      existing.addEventListener("load", () => resolve());
      existing.addEventListener("error", () => reject(new Error("PortOne SDK 로드 실패")));
      return;
    }
    const script = document.createElement("script");
    script.src = SDK_URL;
    script.onload = () => resolve();
    script.onerror = () => reject(new Error("PortOne SDK 로드 실패"));
    document.head.appendChild(script);
  });
  if (!window.PortOne) {
    throw new Error("PortOne SDK 초기화 실패");
  }
  return window.PortOne;
}

export interface PortOnePayParams {
  readonly paymentId: string;
  readonly orderName: string;
  readonly totalAmount: number;
}

/**
 * 결제창을 띄우고 결제를 요청한다. 성공 시 resolve, 사용자가 취소하거나 실패하면 throw.
 * 결제 성공 후에는 반드시 서버 검증(payOrder)을 호출해야 한다.
 */
export async function requestPortOnePayment(params: PortOnePayParams): Promise<void> {
  const sdk = await loadSdk();
  const result = await sdk.requestPayment({
    storeId: env.portOneStoreId,
    channelKey: env.portOneChannelKey,
    paymentId: params.paymentId,
    orderName: params.orderName,
    totalAmount: params.totalAmount,
    currency: "CURRENCY_KRW",
    payMethod: "CARD",
  });
  if (result.code !== undefined) {
    throw new Error(result.message ?? "결제에 실패했습니다.");
  }
}
