import { test, expect } from "@playwright/test";
// 시세 기간 필터는 웹·앱 공유 코어에 있다(@gole/core). 파사드가 아니라 원본을 직접 가져와
// 이 단위 검증이 재수출 계층을 거치지 않게 한다.
import { filterPricePointsByPeriod, type PricePoint } from "@gole/core/pricing";

test("기간 필터는 0~1건이어도 전체 데이터로 되돌아가지 않는다", () => {
  const now = Date.parse("2026-08-30T00:00:00Z");
  const point = (executedAt: string, price: number): PricePoint => ({
    executedAt,
    price,
    quantity: 1,
    source: "platform_payment",
    condition: "new_sealed",
  });
  const points = [
    point("2025-01-01T00:00:00Z", 100),
    point("2026-08-20T00:00:00Z", 200),
  ];

  expect(filterPricePointsByPeriod(points, 31, now)).toEqual([points[1]]);
  expect(filterPricePointsByPeriod(points, 1, now)).toEqual([]);
});

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

    // 홈 상단 시세 티커는 무한 마퀴(animate-market-ticker)라 클릭이 "요소가 멈출 때까지"
    // 대기에 걸린다. 정적인 "지금 뜨는 세트" 목록에서 집는다.
    const trending = page
      .locator("section")
      .filter({ has: page.getByRole("heading", { name: "지금 뜨는 세트" }) });
    const trendingLink = trending.locator('a[href^="/prices?set="]').first();
    const initialSet = new URL(
      (await trendingLink.getAttribute("href"))!,
      "http://localhost",
    ).searchParams.get("set")!;

    await trendingLink.click();
    await expect(page).toHaveURL(new RegExp(`/prices\\?set=${initialSet}$`));
    // 홈의 "지금 뜨는 세트"는 거래량 기준이라 추천(featured) 목록과 다르다. 추천이 아닌
    // 세트로 들어와도 요청한 세트가 그대로 보여야 한다(조용히 다른 세트를 보여주면 안 된다).
    await expect(page.getByText(new RegExp(`^#${initialSet} ·`))).toBeVisible();

    // 목록에서 다른 세트를 고르면 URL이 따라온다(세트 목록만 ol 안의 aria-pressed 버튼).
    await page.locator('ol button[aria-pressed="false"]').first().click();
    await expect(page).toHaveURL(/\/prices\?set=[^&]+$/);
    const pickedSet = new URL(page.url()).searchParams.get("set")!;
    expect(pickedSet).not.toBe(initialSet);
    await expect(page.getByText(new RegExp(`^#${pickedSet} ·`))).toBeVisible();
  });
});
