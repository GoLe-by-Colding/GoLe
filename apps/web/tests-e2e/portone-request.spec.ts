import { expect, test } from "@playwright/test";
import {
  buildPortOnePaymentRequest,
  isCardPaymentAvailable,
  isPortOneEnabled,
} from "../src/shared/lib/portone";

const CARD_CUSTOMER = {
  fullName: "홍길동",
  email: "buyer@example.com",
  phoneNumber: "010-1234-5678",
} as const;

test.describe("PortOne request contract", () => {
  test("uses the configured test channel, EASY_PAY, KRW and a mobile return URL", () => {
    expect(isPortOneEnabled()).toBe(true);

    const request = buildPortOnePaymentRequest(
      {
        paymentId: "order-123",
        orderName: "GoLe 주문 order-12",
        totalAmount: 49_900,
        method: "kakaopay",
      },
      "https://gole.example",
    );

    expect(request).toMatchObject({
      storeId: "store-test",
      channelKey: "channel-kakaopay-test",
      paymentId: "order-123",
      totalAmount: 49_900,
      currency: "KRW",
      payMethod: "EASY_PAY",
      locale: "KO_KR",
      windowType: { pc: "IFRAME", mobile: "REDIRECTION" },
      redirectUrl: "https://gole.example/payments/portone/return",
    });
    expect(request.isEscrow).toBeUndefined();
    // 카카오페이는 구매자 정보를 요구하지 않는다. 보내지 않는 편이 낫다.
    expect(request.customer).toBeUndefined();
  });

  test("uses the card channel, CARD and the buyer identity KG Inicis requires", () => {
    expect(isCardPaymentAvailable()).toBe(true);

    const request = buildPortOnePaymentRequest(
      {
        paymentId: "order-card",
        orderName: "GoLe 주문 order-ca",
        totalAmount: 49_900,
        method: "card",
        customer: CARD_CUSTOMER,
      },
      "https://gole.example",
    );

    expect(request).toMatchObject({
      storeId: "store-test",
      channelKey: "channel-card-test",
      paymentId: "order-card",
      currency: "KRW",
      payMethod: "CARD",
      customer: CARD_CUSTOMER,
    });
  });

  /** 결제창에서 PG 오류로 튕기기 전에, 무엇이 빠졌는지 말할 수 있는 자리에서 막는다. */
  test("refuses to open a card payment window without the buyer identity", () => {
    expect(() =>
      buildPortOnePaymentRequest(
        {
          paymentId: "order-card",
          orderName: "GoLe 주문",
          totalAmount: 49_900,
          method: "card",
        },
        "https://gole.example",
      ),
    ).toThrow("이름·이메일·연락처");

    expect(() =>
      buildPortOnePaymentRequest(
        {
          paymentId: "order-card",
          orderName: "GoLe 주문",
          totalAmount: 49_900,
          method: "card",
          customer: { ...CARD_CUSTOMER, fullName: "  " },
        },
        "https://gole.example",
      ),
    ).toThrow("이름");
  });

  test("rejects non-positive or fractional KRW amounts", () => {
    expect(() =>
      buildPortOnePaymentRequest(
        { paymentId: "order-123", orderName: "GoLe 주문", totalAmount: 0, method: "kakaopay" },
        "https://gole.example",
      ),
    ).toThrow("결제 금액");
    expect(() =>
      buildPortOnePaymentRequest(
        { paymentId: "order-123", orderName: "GoLe 주문", totalAmount: 10.5, method: "kakaopay" },
        "https://gole.example",
      ),
    ).toThrow("결제 금액");
  });

  test("mobile return verifies the payment on the server", async ({ page }) => {
    let verificationCalls = 0;
    await page.route("**/api/v1/orders/order-redirect/payment", async (route) => {
      verificationCalls += 1;
      await route.fulfill({ status: 200, contentType: "application/json", body: "{}" });
    });

    await page.goto("/payments/portone/return?paymentId=order-redirect");
    await expect(page.getByRole("heading", { name: "결제 확인 완료" })).toBeVisible();
    await expect(page.getByText("PortOne 원장을 서버에서 다시 확인합니다.")).toBeVisible();
    expect(verificationCalls).toBe(1);
  });

  test("mobile cancellation does not ask the server to approve a payment", async ({ page }) => {
    let verificationCalls = 0;
    await page.route("**/api/v1/orders/**/payment", async (route) => {
      verificationCalls += 1;
      await route.abort();
    });

    await page.goto(
      "/payments/portone/return?paymentId=order-cancelled&code=PAY_PROCESS_CANCELED&message=사용자%20취소",
    );
    await expect(page.getByRole("heading", { name: "결제 취소" })).toBeVisible();
    expect(verificationCalls).toBe(0);
  });
});
