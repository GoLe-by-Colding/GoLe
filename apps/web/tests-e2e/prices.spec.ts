import { test, expect } from "@playwright/test";

// 시세 페이지: 차트·기간 탭·상태별(감가/매수매도) 테이블·정렬. (데이터가 있는 환경 대상)
test.describe("Prices (KREAM-style)", () => {
  test("시세 차트와 상태별 시세 테이블이 보인다", async ({ page }) => {
    await page.goto("/prices");
    await expect(page.getByRole("heading", { name: "시세" })).toBeVisible();

    // 인터랙티브 차트
    await expect(page.getByRole("img", { name: "시세 추이 차트" }).first()).toBeVisible();

    // API가 주는 상대 미디어 URL도 로컬/운영 환경의 공개 원점에서 정상 로드되어야 한다.
    const catalogImages = page.locator('img[src*="/api/v1/media/catalog/"]');
    await expect(catalogImages.first()).toBeVisible();
    await expect(page.locator('[data-image-fallback="true"]')).toHaveCount(0);

    // 상태별 시세(즉시판매/즉시구매) 헤더
    await expect(page.getByText("즉시판매").first()).toBeVisible();
    await expect(page.getByText("즉시구매").first()).toBeVisible();
    await expect(page.getByText("미개봉 새상품").first()).toBeVisible();
  });

  test("기간 탭을 전환할 수 있다", async ({ page }) => {
    await page.goto("/prices");
    await page.getByRole("button", { name: "1개월" }).click();
    await expect(page.getByRole("img", { name: "시세 추이 차트" }).first()).toBeVisible();
  });

  test("정렬을 변경할 수 있다", async ({ page }) => {
    await page.goto("/prices");
    await page.getByLabel("정렬").selectOption("recent");
    // 목록이 여전히 렌더된다(첫 세트 버튼 존재)
    await expect(page.getByRole("img", { name: "시세 추이 차트" }).first()).toBeVisible();
  });

  test("홈에서 전달한 세트를 바로 선택하고 목록 선택을 URL에 반영한다", async ({ page }) => {
    await page.goto("/");
    await page.locator('a[href="/prices?set=75313"]').last().click();
    await expect(page).toHaveURL(/\/prices\?set=75313$/);
    await expect(page.getByText("#75313 · Star Wars", { exact: true })).toBeVisible();

    await page.getByRole("button", { name: /타이타닉/ }).click();
    await expect(page).toHaveURL(/\/prices\?set=10294$/);
    await expect(page.getByText("#10294 · Icons", { exact: true })).toBeVisible();
  });
});
