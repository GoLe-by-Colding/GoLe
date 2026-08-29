import { expect, test, type Page, type Route } from "@playwright/test";

type TestOrderStatus =
  | "payment_pending"
  | "payment_review"
  | "payment_failed"
  | "funds_held"
  | "completed"
  | "refund_pending"
  | "refunded";

function order(status: TestOrderStatus, id = "order-ux", listingId = "listing-ux") {
  return {
    id,
    listingId,
    buyerId: "buyer-ux",
    sellerId: "seller-ux",
    catalogSetNumber: "10307",
    amount: 49_900,
    status,
    createdAt: "2026-08-09T12:00:00Z",
    history: [{ status, occurredAt: "2026-08-09T12:00:00Z" }],
  };
}

async function fulfillOrder(route: Route, status: TestOrderStatus, id = "order-ux") {
  await route.fulfill({
    status: 200,
    contentType: "application/json",
    body: JSON.stringify(order(status, id)),
  });
}

async function openOrder(page: Page, id: string, status: TestOrderStatus) {
  await page.route(`**/api/v1/orders/${id}`, (route) => fulfillOrder(route, status, id));
  await page.goto(`/orders/${id}`);
  await expect(page.getByTestId("order-status")).toBeVisible();
}

async function mockPaymentsOpen(page: Page) {
  await page.route("**/api/v1/config/launch", (route) =>
    route.fulfill({
      json: {
        stage: 2,
        tradeMode: "MANUAL_SETTLEMENT",
        features: { payments: true, reviews: true, partnerPayout: false },
        updatedAt: "2026-08-09T00:00:00Z",
      },
    }),
  );
}

test.describe("Order detail recovery UX", () => {
  test.beforeEach(async ({ page }) => {
    await mockPaymentsOpen(page);
  });

  /**
   * 결제 시도 전에는 "기다리고 있어요"가 사실이 아니다. 그 문구는 결제를 마친 사람에게
   * "다시 결제하지 말라"고 말하기 위한 것이라, 시작도 안 한 사람에게 보이면 오해를 만든다.
   */
  test("payment pending before any attempt tells the buyer to start, not to wait", async ({
    page,
  }) => {
    await openOrder(page, "order-untouched", "payment_pending");

    await expect(page.getByText("아직 결제하지 않았어요")).toBeVisible();
    await expect(page.getByText("카카오페이 결제를 기다리고 있어요")).toBeHidden();
    await expect(page.getByRole("button", { name: "결제하기" })).toBeVisible();
  });

  test("payment review can be refreshed without opening another payment", async ({ page }) => {
    let reads = 0;
    let reconciliationCompleted = false;
    await page.route("**/api/v1/orders/order-review", async (route) => {
      reads += 1;
      await fulfillOrder(
        route,
        reconciliationCompleted ? "funds_held" : "payment_review",
        "order-review",
      );
    });

    await page.goto("/orders/order-review");
    await expect(page.getByText("운영팀이 결제를 확인하고 있어요", { exact: true })).toBeVisible();
    await expect(page.getByRole("link", { name: "운영팀 문의" })).toHaveAttribute(
      "href",
      "/chat?compose=support",
    );

    reconciliationCompleted = true;
    await page.getByRole("button", { name: "상태 다시 확인" }).click();
    await expect(page.getByTestId("order-status")).toHaveText("결제 완료(정산 대기)");
    await expect(page.getByRole("button", { name: "구매 확정" })).toBeVisible();
    expect(reads).toBeGreaterThanOrEqual(2);
  });

  test("payment review is automatically refreshed on the bounded polling interval", async ({
    page,
  }) => {
    let reads = 0;
    let reconciliationCompleted = false;
    await page.route("**/api/v1/orders/order-auto-refresh", async (route) => {
      reads += 1;
      await fulfillOrder(
        route,
        reconciliationCompleted ? "funds_held" : "payment_review",
        "order-auto-refresh",
      );
    });
    await page.clock.install();

    await page.goto("/orders/order-auto-refresh");
    await expect(page.getByTestId("order-status")).toHaveText("결제 확인 필요");
    reconciliationCompleted = true;
    await page.clock.fastForward(5_000);

    await expect(page.getByTestId("order-status")).toHaveText("결제 완료(정산 대기)");
    expect(reads).toBeGreaterThanOrEqual(2);
  });

  test("purchase completion requires an explicit confirmation", async ({ page }) => {
    let completionCalls = 0;
    await page.route("**/api/v1/orders/order-complete", (route) =>
      fulfillOrder(route, "funds_held", "order-complete"),
    );
    await page.route("**/api/v1/orders/order-complete/completion", async (route) => {
      completionCalls += 1;
      await fulfillOrder(route, "completed", "order-complete");
    });

    await page.goto("/orders/order-complete");
    await page.getByRole("button", { name: "구매 확정" }).click();

    const dialog = page.getByRole("dialog", { name: "구매를 확정할까요?" });
    await expect(dialog).toBeVisible();
    await expect(dialog.getByText("판매자 정산이 시작되어 되돌리기 어렵습니다.")).toBeVisible();
    expect(completionCalls).toBe(0);

    await dialog.getByRole("button", { name: "돌아가기" }).click();
    await expect(dialog).toBeHidden();
    expect(completionCalls).toBe(0);

    await page.getByRole("button", { name: "구매 확정" }).click();
    await page.getByRole("dialog").getByRole("button", { name: "상품을 받았어요" }).click();
    await expect(page.getByTestId("order-status")).toHaveText("거래 완료");
    expect(completionCalls).toBe(1);
  });

  test("refund requires confirmation and exposes the asynchronous state", async ({ page }) => {
    let refundCalls = 0;
    await page.route("**/api/v1/orders/order-refund", (route) =>
      fulfillOrder(route, "funds_held", "order-refund"),
    );
    await page.route("**/api/v1/orders/order-refund/refund", async (route) => {
      refundCalls += 1;
      await fulfillOrder(route, "refund_pending", "order-refund");
    });

    await page.goto("/orders/order-refund");
    await page.getByRole("button", { name: "환불", exact: true }).click();

    const dialog = page.getByRole("dialog", { name: "환불을 요청할까요?" });
    await expect(dialog).toBeVisible();
    expect(refundCalls).toBe(0);
    await dialog.getByRole("button", { name: "환불 요청" }).click();

    await expect(page.getByTestId("order-status")).toHaveText("환불 처리 중");
    await expect(page.getByText("환불을 처리하고 있어요", { exact: true })).toBeVisible();
    await expect(page.getByRole("button", { name: "상태 다시 확인" })).toBeVisible();
    expect(refundCalls).toBe(1);
  });

  test("failed payment offers a clear route back to the listing and support", async ({ page }) => {
    await openOrder(page, "order-failed", "payment_failed");

    await expect(page.getByText("결제를 완료하지 못했어요", { exact: true })).toBeVisible();
    await expect(page.getByRole("link", { name: "매물 다시 보기" })).toHaveAttribute(
      "href",
      "/listings/listing-ux",
    );
    await expect(page.getByRole("link", { name: "운영팀 문의" })).toHaveAttribute(
      "href",
      "/chat?compose=support",
    );
    await expect(page.getByRole("button", { name: "결제하기" })).toHaveCount(0);
  });
});
