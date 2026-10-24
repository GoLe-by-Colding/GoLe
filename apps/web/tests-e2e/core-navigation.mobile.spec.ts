import { expect, test } from "@playwright/test";

for (const width of [320, 375, 414, 768]) {
  test(`${width}px 모바일 메뉴가 주요 동선을 열고 내용을 가리지 않는다`, async ({ page }) => {
    await page.setViewportSize({ width, height: 852 });
    await page.goto("/search");
    const menu = page.getByRole("navigation", { name: "모바일 주요 메뉴" });
    await expect(menu).toBeVisible();
    await expect(menu.getByRole("link")).toHaveCount(5);
    await expect(menu.getByRole("link", { name: "검색", exact: true })).toHaveAttribute(
      "aria-current",
      "page",
    );
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth - innerWidth),
    ).toBeLessThanOrEqual(1);
    await menu.getByRole("link", { name: "판매", exact: true }).click();
    await expect(page).toHaveURL(/\/sell$/);
    await expect(menu.getByRole("link", { name: "판매", exact: true })).toHaveAttribute(
      "aria-current",
      "page",
    );
    await expect(page.getByRole("heading", { name: "상품 등록", exact: true })).toBeVisible();
    await expect
      .poll(async () => {
        await page.evaluate(() =>
          window.scrollTo({ top: document.documentElement.scrollHeight, behavior: "instant" }),
        );
        const footer = await page.locator("footer").boundingBox();
        const nav = await menu.boundingBox();
        if (footer === null || nav === null) return Infinity;
        return footer.y + footer.height - nav.y;
      })
      .toBeLessThanOrEqual(1);
  });
}

test("RN 앱에서는 웹 하단 메뉴를 중복 렌더링하지 않는다", async ({ browser, baseURL }) => {
  const context = await browser.newContext({
    baseURL,
    viewport: { width: 393, height: 852 },
    userAgent: "Mozilla/5.0 GoLeApp/1.0",
  });
  try {
    const page = await context.newPage();
    await page.goto("/search");
    await expect(page.getByRole("heading", { name: "상품 탐색" })).toBeVisible();
    await expect(page.getByRole("navigation", { name: "모바일 주요 메뉴" })).toHaveCount(0);
  } finally {
    await context.close();
  }
});
