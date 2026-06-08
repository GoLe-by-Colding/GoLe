import { test, expect } from "@playwright/test";

// 모바일 뷰포트(Pixel 5)에서의 반응형/내비게이션 검증.
test.describe("Mobile responsive", () => {
  test("모바일에서 가로 스크롤 없이 홈이 렌더된다", async ({ page }) => {
    await page.goto("/");
    await expect(page.getByRole("heading", { name: /깔끔하게/ })).toBeVisible();

    // 가로 오버플로우(가로 스크롤) 없음 검증
    const overflow = await page.evaluate(
      () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
    );
    expect(overflow).toBeLessThanOrEqual(1);
  });

  test("모바일 햄버거 메뉴로 내비게이션한다", async ({ page }) => {
    await page.goto("/");

    // 데스크톱 nav 는 모바일에서 숨겨져 있고, 햄버거가 보인다
    const burger = page.getByRole("button", { name: "메뉴 열기" });
    await expect(burger).toBeVisible();

    await burger.click();
    const menu = page.locator("#mobile-menu");
    await expect(menu).toBeVisible();

    await menu.getByRole("link", { name: "시세" }).click();
    await expect(page).toHaveURL(/\/prices$/);
  });
});
