import { test, expect } from "@playwright/test";

test.describe("Home", () => {
  test("홈 화면이 렌더되고 추천 세트 카드가 보인다", async ({ page }) => {
    await page.goto("/");

    await expect(
      page.getByRole("heading", { name: "GoLe — 레고 중고거래 플랫폼" }),
    ).toBeVisible();

    const card = page.getByTestId("lego-set-card");
    await expect(card).toBeVisible();
    await expect(card).toContainText("Eiffel Tower");
    await expect(card).toContainText("#10307");
  });
});
