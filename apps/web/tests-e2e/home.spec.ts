import { test, expect } from "@playwright/test";

test.describe("Home", () => {
  test("브릭 브랜드 메타데이터와 히어로가 렌더된다", async ({ page }) => {
    await page.goto("/");

    await expect(page).toHaveTitle("GoLe — 브릭 중고거래 플랫폼");
    await expect(page.locator('meta[property="og:title"]')).toHaveAttribute(
      "content",
      "GoLe — 브릭 중고거래 플랫폼",
    );
    await expect(page.locator('meta[property="og:image:alt"]')).toHaveAttribute(
      "content",
      "GoLe — Brick Marketplace",
    );

    // 히어로 헤드라인(브랜드 카피)
    await expect(page.getByRole("heading", { name: "브릭을 가장 합리적으로" })).toBeVisible();
  });

  test("지금 뜨는 세트의 평균이 모든 상태의 체결가 평균임을 밝힌다", async ({ page }) => {
    await page.goto("/");

    const trending = page
      .locator("section")
      .filter({ has: page.getByRole("heading", { name: "지금 뜨는 세트" }) });
    // 인기 집계 평균은 상태를 가리지 않는다 — 세트 상세의 미개봉 체결가·매물의 같은 상태 추정 시세와 다른 값이다.
    await expect(
      trending.getByText("미개봉·중고를 합친 모든 상태의 체결가 평균", { exact: false }),
    ).toBeVisible();
  });

  test("추천 세트가 렌더된다", async ({ page }) => {
    await page.goto("/");

    await expect(page.getByRole("heading", { name: "오늘의 추천" })).toBeVisible();

    // 시드된 추천 세트 카드가 최소 1개 보이고, 대표 세트(에펠탑/#10307)가 노출된다
    await expect(page.getByTestId("lego-set-card").first()).toBeVisible();
    await expect(page.getByText("에펠탑").first()).toBeVisible();
    await expect(page.getByText("#10307").first()).toBeVisible();
  });

  test("추천 세트 카드가 세트 상세로 이어진다", async ({ page }) => {
    await page.goto("/");

    const card = page.getByTestId("lego-set-card").first();
    const detailLink = card.getByRole("link", { name: /세트 상세 보기/ });
    const href = await detailLink.getAttribute("href");

    expect(href).toMatch(/^\/sets\/[^/]+$/);
    await detailLink.click();
    await expect(page).toHaveURL(new RegExp(`${href!.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}$`));
  });

  test("히어로 CTA로 탐색/시세로 이동한다", async ({ page }) => {
    await page.goto("/");
    await page.getByRole("link", { name: "상품 둘러보기" }).click();
    await expect(page).toHaveURL(/\/search$/);
  });
});
