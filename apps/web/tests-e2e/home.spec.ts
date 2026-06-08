import { test, expect } from "@playwright/test";

test.describe("Home", () => {
  test("홈 히어로와 추천 세트가 렌더된다", async ({ page }) => {
    await page.goto("/");

    // 히어로 헤드라인(브랜드 카피)
    await expect(page.getByRole("heading", { name: /깔끔하게/ })).toBeVisible();
    await expect(page.getByRole("heading", { name: "오늘의 추천 세트" })).toBeVisible();

    // 시드된 추천 세트 카드가 최소 1개 보이고, 대표 세트(에펠탑/#10307)가 노출된다
    await expect(page.getByTestId("lego-set-card").first()).toBeVisible();
    await expect(page.getByText("에펠탑").first()).toBeVisible();
    await expect(page.getByText("#10307").first()).toBeVisible();
  });
});
