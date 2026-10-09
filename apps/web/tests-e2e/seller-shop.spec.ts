import { test, expect } from "@playwright/test";

const externalBaseUrl = process.env.E2E_BASE_URL;
const apiBaseUrl = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";
const targetsRemoteHost =
  externalBaseUrl !== undefined &&
  !["localhost", "127.0.0.1"].includes(new URL(externalBaseUrl).hostname);

// 판매자 샵의 매물 카드는 검색 카드와 같은 위계(사진·세트 번호·제목·가격·상태)로 보이고 휴대폰에서 2열이다.
test.describe("판매자 샵", () => {
  test.skip(targetsRemoteHost, "로컬 API의 매물 목록으로 판매자를 고르는 회귀 테스트");

  test("판매 중인 매물을 사진·상태가 있는 카드로 휴대폰 2열에 보인다", async ({ page }) => {
    const response = await page.request.get(`${apiBaseUrl}/api/v1/listings`);
    expect(response.ok()).toBeTruthy();
    const listings = (await response.json()) as Array<{ sellerId: string; status: string }>;
    const sellerId = listings.find((item) => item.status === "active")?.sellerId;
    expect(sellerId, "판매 중인 매물이 하나 이상 필요합니다").toBeTruthy();

    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto(`/shops/${encodeURIComponent(sellerId ?? "")}`);

    const cards = page.getByTestId("shop-listing-card");
    await expect(cards.first()).toBeVisible();
    // 사진이 없어도 대체 이미지 자리에 매물 이름이 대체 텍스트로 붙는다.
    await expect(cards.first().getByRole("img")).toHaveCount(1);
    await expect(cards.first().getByText(/미개봉|거의 새것|중고|하자/)).toBeVisible();

    const columns = await page.evaluate(() => {
      const grid = document
        .querySelector('[data-testid="shop-listing-card"]')
        ?.closest("a")?.parentElement;
      return grid ? getComputedStyle(grid).gridTemplateColumns.split(" ").length : 0;
    });
    expect(columns).toBe(2);
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(390);
  });
});
