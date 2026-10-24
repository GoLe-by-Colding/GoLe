import { expect, test } from "@playwright/test";

// PG 결제 자체가 아닌, 서버 주문 상태에 대한 복귀 화면의 계약을 검증한다.
for (const [status, heading] of [
  ["cancelled", "주문 취소"],
  ["payment_review", "결제 검토 중"],
  ["payment_pending", "결제 확인 중"],
  ["refund_pending", "환불 처리 중"],
  ["refunded", "환불 완료"],
  ["disputed", "거래 확인 중"],
  ["payment_failed", "결제 미완료"],
  ["completed", "거래 완료"],
] as const) {
  test(`서버의 ${status} 주문을 결제 성공으로 표시하지 않는다`, async ({ page }) => {
    let approvals = 0;
    await page.route("**/api/v1/orders/order-state", (route) =>
      route.fulfill({ json: { id: "order-state", status } }),
    );
    await page.route("**/api/v1/orders/order-state/payment", (route) => {
      approvals++;
      return route.fulfill({ json: { id: "order-state", status } });
    });
    await page.goto("/payments/portone/return?paymentId=order-state");
    await expect(page.getByRole("heading", { name: heading, exact: true })).toBeVisible();
    if (status === "payment_pending")
      await expect(page.getByRole("button", { name: "결제 상태 다시 확인" })).toBeVisible();
    await expect(page.getByRole("heading", { name: "결제 확인 완료", exact: true })).toHaveCount(0);
    expect(approvals).toBe(status === "payment_pending" || status === "payment_review" ? 1 : 0);
  });
}

test("일시적인 조회 실패 후 결제 상태를 다시 확인할 수 있다", async ({ page }) => {
  let reads = 0;
  await page.route("**/api/v1/orders/order-retry", (route) => {
    reads++;
    return reads === 1
      ? route.fulfill({ status: 503, json: { code: "UNAVAILABLE", message: "잠시 후 재시도" } })
      : route.fulfill({ json: { id: "order-retry", status: "funds_held" } });
  });
  await page.goto("/payments/portone/return?paymentId=order-retry");
  await page.getByRole("button", { name: "결제 상태 다시 확인" }).click();
  await expect(page.getByRole("heading", { name: "결제 확인 완료" })).toBeVisible();
  expect(reads).toBe(2);
});

test("만료된 로그인은 결제 복귀 경로를 보존한다", async ({ page }) => {
  await page.route("**/api/v1/orders/order-login", (route) =>
    route.fulfill({
      status: 401,
      json: { code: "INVALID_SESSION", message: "로그인이 필요합니다" },
    }),
  );
  await page.goto("/payments/portone/return?paymentId=order-login");
  await expect(page.getByRole("link", { name: "로그인하고 확인하기" })).toHaveAttribute(
    "href",
    "/login?returnTo=%2Fpayments%2Fportone%2Freturn%3FpaymentId%3Dorder-login",
  );
});

test("카카오페이 중단과 잘못된 주문번호는 승인 요청을 보내지 않는다", async ({ page }) => {
  const requests: string[] = [];
  await page.route("**/api/v1/orders/**", (route) => {
    requests.push(route.request().url());
    return route.abort();
  });
  await page.goto(
    "/payments/portone/return?paymentId=order-stop&code=PG_ERROR&message=사용자가%20프로세스를%20중단하였습니다",
  );
  await expect(page.getByRole("heading", { name: "결제 취소" })).toBeVisible();
  await page.goto("/payments/portone/return?paymentId=..%2Fbad");
  await expect(page.getByRole("heading", { name: "주문번호 확인 필요" })).toBeVisible();
  await expect(page.getByRole("link", { name: "내 주문 확인" })).toHaveAttribute(
    "href",
    "/profile",
  );
  expect(requests).toEqual([]);
});
