import { test, expect } from "@playwright/test";

// 탐색 → 카테고리 필터 → 상세(갤러리 + 시세) 흐름. (데이터가 있는 환경 대상)
test.describe("Search & listing detail", () => {
  test("탐색 페이지에 필터(카테고리 포함)와 목록이 보인다", async ({ page }) => {
    await page.goto("/search");
    await expect(page.getByRole("heading", { name: "상품 탐색" })).toBeVisible();
    await expect(page.getByLabel("카테고리")).toBeVisible();
    await expect(page.getByTestId("listing-card").first()).toBeVisible();
  });

  test("카테고리로 필터링한다", async ({ page }) => {
    await page.goto("/search");
    await page.getByLabel("카테고리").selectOption("parts");
    await page.getByRole("button", { name: "검색" }).click();
    await expect(page).toHaveURL(/category=parts/);
  });

  test("매물 카드를 클릭하면 상세(갤러리·설명)가 보인다", async ({ page }) => {
    await page.goto("/search");
    await page.getByTestId("listing-card").first().click();
    await expect(page).toHaveURL(/\/listings\//);
    await expect(page.getByText("상품 설명")).toBeVisible();
  });
});
