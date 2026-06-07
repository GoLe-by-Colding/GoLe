import { test, expect } from "@playwright/test";

// 백엔드(MongoDB 포함)가 떠 있어야 통과하는 풀 플로우 E2E.
// 세션을 주입한 뒤 /sell에서 상품을 등록하고 상세로 이동하는지 검증한다.
test.describe("Create listing", () => {
  test.beforeEach(async ({ page }) => {
    await page.addInitScript(() => {
      window.localStorage.setItem(
        "gole.session",
        JSON.stringify({ accountId: "e2e-seller", sessionToken: "e2e-token" }),
      );
    });
  });

  test("로그인 셀러가 상품을 등록하면 상세로 이동한다", async ({ page }) => {
    await page.goto("/sell");

    const title = `E2E 테스트 세트 ${Date.now()}`;
    await page.getByLabel("제목").fill(title);
    await page.getByLabel("설명").fill("E2E 자동 등록 상품");
    await page.getByLabel("가격 (원)").fill("12345");
    await page.getByLabel("대표 이미지 URL").fill("https://placehold.co/600x400?text=E2E");

    await page.getByRole("button", { name: "상품 등록" }).click();

    await expect(page).toHaveURL(/\/listings\/.+/);
    await expect(page.getByRole("heading", { name: title })).toBeVisible();
    await expect(page.getByText("₩12,345")).toBeVisible();
  });
});
