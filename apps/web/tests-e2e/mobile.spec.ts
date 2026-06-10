import { test, expect, type Page } from "@playwright/test";

/** 가로 오버플로우(가로 스크롤) 픽셀 수. 1px 이하면 정상으로 본다. */
async function horizontalOverflow(page: Page): Promise<number> {
  return page.evaluate(
    () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
  );
}

// 모바일 뷰포트(Pixel 5)에서의 반응형/내비게이션 검증.
test.describe("Mobile responsive", () => {
  const pages = ["/", "/search", "/prices", "/community", "/login"];

  for (const path of pages) {
    test(`모바일에서 가로 스크롤 없이 ${path} 가 렌더된다`, async ({ page }) => {
      await page.goto(path, { waitUntil: "load" });
      // 레이아웃 안정화 대기(폰트/이미지)
      await page.waitForTimeout(300);
      expect(await horizontalOverflow(page)).toBeLessThanOrEqual(1);
    });
  }

  test("모바일 햄버거 메뉴로 내비게이션한다", async ({ page }) => {
    await page.goto("/");

    const burger = page.getByRole("button", { name: "메뉴 열기" });
    await expect(burger).toBeVisible();

    await burger.click();
    const menu = page.locator("#mobile-menu");
    await expect(menu).toBeVisible();

    await menu.getByRole("link", { name: "시세" }).click();
    await expect(page).toHaveURL(/\/prices$/);
  });

  test("모바일에서 데스크톱 인라인 네비는 숨겨진다", async ({ page }) => {
    await page.goto("/");
    // 데스크톱 nav 링크(헤더의 '탐색')는 max-sm에서 hidden
    await expect(page.getByRole("button", { name: "메뉴 열기" })).toBeVisible();
  });
});
