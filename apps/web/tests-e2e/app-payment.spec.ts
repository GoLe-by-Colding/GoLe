import { expect, test } from "@playwright/test";
import {
  APP_PAYMENT_REQUEST,
  APP_PAYMENT_RESULT,
  parseAppPaymentRequest,
  type AppPaymentRequest,
} from "@gole/core/lib";
import { requestAppPayment } from "../src/shared/lib/app-payment";
import { requestPortOnePayment, PortOnePaymentError } from "../src/shared/lib/portone";
import { paymentResultScript } from "../../mobile/src/views/web/model/payment";
import { runInNewContext } from "node:vm";

const origin = "https://gole.example";
const valid: AppPaymentRequest = {
  type: APP_PAYMENT_REQUEST,
  version: 1,
  requestId: "request-1",
  payment: {
    storeId: "store-test",
    channelKey: "channel-test",
    paymentId: "order-123",
    orderName: "GoLe 주문",
    totalAmount: 49900,
    currency: "KRW",
    payMethod: "EASY_PAY",
  },
};

test("앱 결제는 해당 주문의 신뢰 원점과 검증된 원화 요청만 받는다", () => {
  const data = JSON.stringify(valid);
  expect(parseAppPaymentRequest(data, `${origin}/orders/order-123`, origin)).toEqual(valid);
  for (const url of [
    `${origin}.evil.test/orders/order-123`,
    `${origin}/orders/other`,
    `${origin}/search`,
    `https://user@${origin.slice(8)}/orders/order-123`,
  ]) {
    expect(parseAppPaymentRequest(data, url, origin)).toBeNull();
  }
  for (const changes of [
    { totalAmount: -1 },
    { totalAmount: 1.5 },
    { totalAmount: 1e12 },
    { currency: "USD" },
    { payMethod: "VIRTUAL_ACCOUNT" },
    { payMethod: "CARD" },
    { orderName: "</script><script>evil()</script>" },
    { redirectUrl: "https://evil.test" },
    {
      customer: {
        fullName: "이름",
        email: "a@b.c",
        phoneNumber: "01011112222",
        token: "forbidden",
      },
    },
  ]) {
    expect(
      parseAppPaymentRequest(
        JSON.stringify({ ...valid, payment: { ...valid.payment, ...changes } }),
        `${origin}/orders/order-123`,
        origin,
      ),
    ).toBeNull();
  }
  expect(
    parseAppPaymentRequest(
      JSON.stringify({ ...valid, version: 2 }),
      `${origin}/orders/order-123`,
      origin,
    ),
  ).toBeNull();
});

function fakeHost(postMessage: (raw: string) => void, version = 1) {
  const previous = Object.getOwnPropertyDescriptor(globalThis, "window");
  const host = Object.assign(new EventTarget(), {
    location: { origin, pathname: "/orders/order-123", href: `${origin}/orders/order-123` },
    navigator: { userAgent: "GoLeApp/1.0" },
    __GOLE_PAYMENT_BRIDGE_VERSION__: version,
    ReactNativeWebView: { postMessage },
    setTimeout: globalThis.setTimeout,
    clearTimeout: globalThis.clearTimeout,
  });
  Object.defineProperty(globalThis, "window", { configurable: true, value: host });
  return {
    host,
    restore() {
      if (previous) Object.defineProperty(globalThis, "window", previous);
      else Reflect.deleteProperty(globalThis, "window");
    },
  };
}
function respond(host: EventTarget, detail: unknown) {
  const event = new Event(APP_PAYMENT_RESULT);
  Object.defineProperty(event, "detail", { value: detail });
  host.dispatchEvent(event);
}

test("다른 결제 요청의 응답은 무시하고 일치하는 요청만 완료한다", async () => {
  let sent: AppPaymentRequest | null = null;
  const { host, restore } = fakeHost((raw) => {
    sent = JSON.parse(raw) as AppPaymentRequest;
  });
  try {
    let finished = false;
    const pending = requestAppPayment(valid.payment).then(() => {
      finished = true;
    });
    expect(sent).not.toBeNull();
    respond(host, { requestId: "another", paymentId: "order-123" });
    await Promise.resolve();
    expect(finished).toBe(false);
    respond(host, { requestId: sent!.requestId, paymentId: "order-123" });
    await pending;
    expect(finished).toBe(true);
  } finally {
    restore();
  }
});

test("HTTP 개발 WebView에서도 randomUUID 없이 결제 요청을 구분한다", async () => {
  const descriptor = Object.getOwnPropertyDescriptor(globalThis, "crypto");
  const getRandomValues = crypto.getRandomValues.bind(crypto);
  Object.defineProperty(globalThis, "crypto", {
    configurable: true,
    value: { getRandomValues },
  });
  let sent: AppPaymentRequest | null = null;
  const { host, restore } = fakeHost((raw) => {
    sent = JSON.parse(raw) as AppPaymentRequest;
  });
  try {
    const pending = requestAppPayment(valid.payment);
    expect(sent!.requestId).toMatch(/^[a-f0-9]{32}$/);
    respond(host, { requestId: sent!.requestId, paymentId: "order-123" });
    await expect(pending).resolves.toMatchObject({ paymentId: "order-123" });
  } finally {
    restore();
    if (descriptor) Object.defineProperty(globalThis, "crypto", descriptor);
    else Reflect.deleteProperty(globalThis, "crypto");
  }
});

test("앱 결제 취소와 잘못된 주문번호를 결제 성공으로 반환하지 않는다", async () => {
  let sent: AppPaymentRequest | null = null;
  const { host, restore } = fakeHost((raw) => {
    sent = JSON.parse(raw) as AppPaymentRequest;
  });
  const params = {
    paymentId: "order-123",
    orderName: "주문",
    totalAmount: 49900,
    method: "kakaopay" as const,
  };
  try {
    const cancelled = requestPortOnePayment(params);
    respond(host, {
      requestId: sent!.requestId,
      paymentId: "order-123",
      code: "PAYMENT_CANCELLED",
      message: "사용자 취소",
    });
    await expect(cancelled).rejects.toMatchObject({
      name: "PortOnePaymentError",
      userCancelled: true,
    });
    const mismatched = requestPortOnePayment(params);
    respond(host, { requestId: sent!.requestId, paymentId: "order-other" });
    await expect(mismatched).rejects.toThrow("주문번호가 일치하지 않습니다");
    expect(
      new PortOnePaymentError({ code: "PG_FAIL", message: "사용자가 프로세스를 중단하였습니다" })
        .userCancelled,
    ).toBe(true);
  } finally {
    restore();
  }
});

test("구버전 앱은 외부 결제창 대신 업데이트 안내를 반환한다", async () => {
  const { restore } = fakeHost(() => {
    throw new Error("호출되면 안 됨");
  }, 0);
  try {
    await expect(requestAppPayment(valid.payment)).rejects.toThrow("최신 버전");
  } finally {
    restore();
  }
});

test("늦은 네이티브 결과는 다른 원점이나 다른 주문 문서에 주입하지 않는다", () => {
  for (const location of [
    { origin, pathname: "/orders/order-123" },
    { origin: "https://evil.test", pathname: "/orders/order-123" },
    { origin, pathname: "/orders/order-other" },
  ]) {
    let count = 0;
    runInNewContext(
      paymentResultScript(
        { requestId: "request-1", paymentId: "order-123", message: "</script>" },
        origin,
      ),
      {
        window: {
          location,
          dispatchEvent() {
            count++;
          },
        },
        CustomEvent: class {},
      },
    );
    expect(count).toBe(
      location.origin === origin && location.pathname === "/orders/order-123" ? 1 : 0,
    );
  }
});
