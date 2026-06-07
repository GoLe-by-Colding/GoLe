import { test, expect } from "@playwright/test";

// 백엔드(MongoDB replica set 포함) 기동 필요. 구매 → 결제 → 구매확정 플로우 검증.
test.describe("Purchase flow", () => {
  test.beforeEach(async ({ page }) => {
    await page.addInitScript(() => {
      window.localStorage.setItem(
        "gole.session",
        JSON.stringify({ accountId: "e2e-buyer", sessionToken: "t" }),
      );
    });
  });

  test("상품 등록 → 구매 → 결제 → 구매확정", async ({ page }) => {
    // 1) 셀러가 상품 등록
    await page.goto("/sell");
    const title = `구매플로우 ${Date.now()}`;
    await page.getByLabel("제목").fill(title);
    await page.getByLabel("설명").fill("E2E");
    await page.getByLabel("가격 (원)").fill("99000");
    await page.getByLabel("대표 이미지 URL").fill("https://placehold.co/600x400");
    await page.getByRole("button", { name: "상품 등록" }).click();
    await expect(page).toHaveURL(/\/listings\/.+/);

    // 2) 구매하기 → 주문 생성 → 체크아웃 이동
    await page.getByRole("button", { name: "구매하기" }).click();
    await expect(page).toHaveURL(/\/orders\/.+/);
    await expect(page.getByTestId("order-status")).toHaveText("결제 대기");

    // 3) 결제 → 에스크로 보관
    await page.getByRole("button", { name: "결제하기" }).click();
    await expect(page.getByTestId("order-status")).toHaveText("결제 완료(에스크로 보관)");

    // 4) 구매 확정 → 거래 완료
    await page.getByRole("button", { name: "구매 확정" }).click();
    await expect(page.getByTestId("order-status")).toHaveText("거래 완료");
  });
});
