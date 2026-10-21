import { expect, test, type Page } from "@playwright/test";
import { E2E_BUYER, E2E_SELLER, signInAs, switchTo } from "./support/e2e-session";

const api = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

/** 실제 격리 API·Mongo·MinIO를 사용하는 거래 왕복. PG는 E2E 스텁이다. */
async function createOrder(page: Page) {
  await signInAs(page, E2E_SELLER);
  await page.goto("/sell");
  await page.getByLabel("제목").fill(`거래 완결 회귀 ${Date.now()}`);
  await page.getByLabel("설명", { exact: true }).fill("격리 E2E 거래 검증");
  await page.getByLabel("가격 (원)").fill("19900");
  await page.getByLabel("상품 이미지").setInputFiles({
    name: "e2e.png",
    mimeType: "image/png",
    buffer: Buffer.from(
      "iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAEUlEQVR4XmPQqLizBYQZYAwASsIIwRFEXsMAAAAASUVORK5CYII=",
      "base64",
    ),
  });
  await expect(page.getByRole("img", { name: /상품 이미지 1/ })).toBeVisible();
  await page.getByRole("button", { name: "상품 등록", exact: true }).click();
  await expect(page).toHaveURL(/\/listings\/[^/]+$/);
  const listingPath = new URL(page.url()).pathname;
  const listingId = listingPath.split("/").at(-1)!;
  await switchTo(page, E2E_BUYER);
  await page.reload();
  await page.getByRole("button", { name: "구매하기", exact: true }).click();
  await page.getByLabel("CS 연락처").fill("010-1234-5678");
  await page.getByRole("button", { name: "주문하기", exact: true }).click();
  await expect(page).toHaveURL(/\/orders\/[^/]+$/);
  const orderPath = new URL(page.url()).pathname;
  const orderId = orderPath.split("/").at(-1)!;
  await expect(page.getByTestId("order-status")).toHaveText("결제 대기");
  await test.info().attach("거래 경로", {
    body: JSON.stringify({ listingPath, orderPath }),
    contentType: "application/json",
  });
  return { listingPath, listingId, orderPath, orderId };
}

test.describe("거래 완료 실제 API 왕복", () => {
  test.skip(
    process.env.E2E_WITH_BACKEND !== "1" || !!process.env.E2E_BASE_URL,
    "격리 로컬 백엔드 전용 — 실제 거래 데이터를 생성함",
  );

  test("예약 중 표시 → 구매자 취소 → 재주문과 중복 취소의 예약 보호", async ({ page }) => {
    const { listingPath, listingId, orderPath, orderId } = await createOrder(page);
    await page.goto(listingPath);
    await expect(page.getByRole("button", { name: "예약 중", exact: true })).toBeDisabled();
    await expect(page.getByText("거래완료", { exact: true })).toHaveCount(0);
    await expect(page.getByRole("link", { name: "내 거래 내역" })).toBeVisible();
    const denied = await page.request.post(`${api}/api/v1/orders/${orderId}/cancellation`, {
      headers: { Authorization: `Bearer ${E2E_SELLER.sessionToken}` },
    });
    expect(denied.status()).toBe(403);
    await page.goto(orderPath);
    await page.getByRole("button", { name: "주문 취소", exact: true }).click();
    await page
      .getByRole("alertdialog")
      .getByRole("button", { name: "주문 취소", exact: true })
      .click();
    await expect(page.getByTestId("order-status")).toHaveText("주문 취소");
    const restored = await page.request.get(`${api}/api/v1/listings/${listingId}`);
    expect(await restored.json()).toMatchObject({ status: "active" });
    await page.getByRole("link", { name: "매물 다시 보기" }).click();
    await page.getByRole("button", { name: "구매하기", exact: true }).click();
    await page.getByLabel("CS 연락처").fill("010-1234-5678");
    await page.getByRole("button", { name: "주문하기", exact: true }).click();
    await expect(page).toHaveURL(/\/orders\/[^/]+$/);
    expect(new URL(page.url()).pathname).not.toBe(orderPath);
    const repeated = await page.request.post(`${api}/api/v1/orders/${orderId}/cancellation`, {
      headers: { Authorization: `Bearer ${E2E_BUYER.sessionToken}` },
    });
    expect(repeated.ok()).toBeTruthy();
    const stillReserved = await page.request.get(`${api}/api/v1/listings/${listingId}`);
    expect(await stillReserved.json()).toMatchObject({ status: "reserved" });
    await page.getByRole("button", { name: "주문 취소", exact: true }).click();
    await page
      .getByRole("alertdialog")
      .getByRole("button", { name: "주문 취소", exact: true })
      .click();
    await expect(page.getByTestId("order-status")).toHaveText("주문 취소");
  });

  test("결제 → 판매자 발송 → 구매확정 → 후기와 판매자 배송 조회", async ({ page }) => {
    const { orderPath } = await createOrder(page);
    await page.getByRole("button", { name: "결제하기", exact: true }).click();
    await expect(page.getByTestId("order-status")).toHaveText("결제 완료(정산 대기)");
    await switchTo(page, E2E_SELLER);
    await page.reload();
    await page.getByLabel("송장번호").fill("123456789012");
    await page.getByLabel("CS 연락처", { exact: true }).fill("010-8765-4321");
    await page.getByRole("button", { name: "운송장 등록", exact: true }).click();
    await expect(page.getByTestId("shipment-tracker")).toContainText("123456789012");
    await switchTo(page, E2E_BUYER);
    await page.reload();
    await expect(page.getByTestId("shipment-tracker")).toContainText("123456789012");
    await expect(page.getByRole("button", { name: "환불", exact: true })).toHaveCount(0);
    await page.getByRole("button", { name: "구매 확정", exact: true }).click();
    await page.getByRole("alertdialog").getByRole("button", { name: "상품을 받았어요" }).click();
    await expect(page.getByTestId("order-status")).toHaveText("거래 완료");
    await page.getByLabel("후기", { exact: true }).fill("안내한 구성 그대로 받았어요.");
    await page.getByRole("button", { name: "후기 등록", exact: true }).click();
    await expect(page.getByText("후기가 등록되었습니다. 감사합니다!")).toBeVisible();
    await switchTo(page, E2E_SELLER);
    await page.goto(orderPath);
    await expect(page.getByTestId("shipment-tracker")).toContainText("123456789012");
    await expect(page.getByRole("button", { name: "운송장 등록", exact: true })).toHaveCount(0);
  });

  test("결제 후 발송 전 환불하면 매물 예약이 복원된다", async ({ page }) => {
    const { listingId } = await createOrder(page);
    await page.getByRole("button", { name: "결제하기", exact: true }).click();
    await expect(page.getByTestId("order-status")).toHaveText("결제 완료(정산 대기)");
    await page.getByRole("button", { name: "환불", exact: true }).click();
    await page.getByRole("alertdialog").getByRole("button", { name: "환불 요청" }).click();
    await expect(page.getByTestId("order-status")).toHaveText("환불됨");
    const restored = await page.request.get(`${api}/api/v1/listings/${listingId}`);
    expect(await restored.json()).toMatchObject({ status: "active" });
  });
});
