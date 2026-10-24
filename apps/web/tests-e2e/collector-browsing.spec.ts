import { expect, test } from "@playwright/test";

const externalUrl = process.env.E2E_BASE_URL;
const remote =
  externalUrl !== undefined && !["localhost", "127.0.0.1"].includes(new URL(externalUrl).hostname);
const set = {
  setNumber: "10307",
  name: "에펠탑",
  theme: "Icons",
  pieceCount: 10001,
  releaseYear: 2022,
  retirementStatus: "active",
  imageUrl: null,
};
const catalog = "**/api/v1/catalog/sets?query=*";

test.describe("수집가의 탐색 도구", () => {
  test.skip(remote, "카탈로그 응답 재현과 로컬 읽기 검증");

  test("키보드로 자동완성을 선택해도 가격·상태·카테고리·정렬을 보존한다", async ({ page }) => {
    await page.route(catalog, (route) => route.fulfill({ json: [set] }));
    await page.goto(
      "/search?category=set&condition=new_sealed&minPrice=10000&maxPrice=1000000&sort=price_asc",
    );
    const query = page.getByRole("combobox", { name: "검색어, 세트 번호" });
    await query.fill("에펠");
    await expect(page.getByRole("option", { name: /#10307 에펠탑/ })).toBeVisible();
    await query.press("ArrowDown");
    await expect(page.getByRole("option", { name: /#10307 에펠탑/ })).toHaveAttribute(
      "aria-selected",
      "true",
    );
    await query.press("Enter");
    await expect(page).toHaveURL(/query=/);
    const params = new URL(page.url()).searchParams;
    expect(Object.fromEntries(params)).toEqual({
      query: "에펠탑",
      category: "set",
      condition: "new_sealed",
      minPrice: "10000",
      maxPrice: "1000000",
      sort: "price_asc",
    });
    await expect(query).toHaveValue("에펠탑");
  });

  test("가격 범위를 잘못 입력하면 이동하지 않고 수정할 입력으로 안내한다", async ({ page }) => {
    await page.goto("/search");
    await page.getByLabel("최소가").fill("30000");
    await page.getByLabel("최대가").fill("10000");
    await page.getByRole("button", { name: "검색", exact: true }).click();
    await expect(page).toHaveURL(/\/search$/);
    await expect(page.getByRole("alert").filter({ hasText: "최대가는 최소가보다" })).toBeVisible();
    await expect(page.getByLabel("최대가")).toBeFocused();
    await expect(page.getByLabel("최대가")).toHaveAttribute("aria-invalid", "true");
    await page.getByLabel("최대가").fill("50000");
    await expect(page.getByLabel("최대가")).toHaveAttribute("aria-invalid", "false");
    await page.getByRole("button", { name: "검색", exact: true }).click();
    await expect(page).toHaveURL(/minPrice=30000&maxPrice=50000/);
  });

  for (const amount of ["1.5", "1e6", "-1", "9007199254740992"]) {
    test(`직접 URL의 잘못된 금액 ${amount}도 빈 검색 결과로 위장하지 않는다`, async ({ page }) => {
      await page.goto(`/search?minPrice=${amount}`);
      await expect(page.getByRole("heading", { name: "가격 범위를 확인해 주세요" })).toBeVisible();
      await expect(page.getByLabel("최소가")).toHaveAttribute("aria-invalid", "true");
      await expect(page.getByTestId("listing-card")).toHaveCount(0);
      await expect(page.getByRole("heading", { name: "조건에 맞는 상품이 없어요" })).toHaveCount(0);
    });
  }

  test("필터 하나 제거·뒤로 가기·전체 해제가 검색어와 초안을 일치시킨다", async ({ page }) => {
    await page.goto("/search?query=10307&category=set&minPrice=10000&sort=price_asc");
    await page.getByRole("link", { name: "10,000원 이상 필터 빼기" }).click();
    await expect(page).not.toHaveURL(/minPrice=/);
    await expect(page.getByLabel("최소가")).toHaveValue("");
    await page.goBack();
    await expect(page.getByLabel("최소가")).toHaveValue("10000");
    await page.getByRole("link", { name: "필터 초기화", exact: true }).click();
    await expect(page).toHaveURL(/\/search\?query=10307$/);
    await expect(page.getByRole("combobox", { name: "검색어, 세트 번호" })).toHaveValue("10307");
    await expect(page.getByRole("list", { name: "적용된 필터" })).toHaveCount(0);
  });

  test("Escape로 닫은 자동완성은 늦은 응답에도 다시 열리지 않는다", async ({ page }) => {
    let release!: () => void;
    let started!: () => void;
    const held = new Promise<void>((resolve) => {
      release = resolve;
    });
    const requested = new Promise<void>((resolve) => {
      started = resolve;
    });
    await page.route(catalog, async (route) => {
      started();
      await held;
      await route.fulfill({ json: [set] });
    });
    await page.goto("/search");
    const query = page.getByRole("combobox", { name: "검색어, 세트 번호" });
    await query.fill("에펠");
    await requested;
    await query.press("Escape");
    const response = page.waitForResponse((res) =>
      res.url().includes("/api/v1/catalog/sets?query="),
    );
    release();
    await response;
    await expect(query).toHaveAttribute("aria-expanded", "false");
    await expect(page.getByRole("listbox", { name: "추천 세트" })).toHaveCount(0);
    await expect(query).toBeFocused();
  });

  test("추천 세트 조회 실패 후에도 일반 상품 검색은 작동한다", async ({ page }) => {
    await page.route(catalog, (route) =>
      route.fulfill({ status: 503, json: { code: "UNAVAILABLE", message: "재현용 오류" } }),
    );
    await page.goto("/search");
    const query = page.getByRole("combobox", { name: "검색어, 세트 번호" });
    const response = page.waitForResponse((res) =>
      res.url().includes("/api/v1/catalog/sets?query="),
    );
    await query.fill("10307");
    await response;
    await query.press("Enter");
    await expect(page).toHaveURL(/query=10307/);
    await expect(page.getByRole("heading", { name: "상품 탐색" })).toBeVisible();
  });

  test("밝은 폼과 어두운 푸터에서 키보드 초점 대비를 유지한다", async ({ page }) => {
    await page.goto("/search");
    const targets = [
      page.getByRole("button", { name: "검색", exact: true }),
      page.getByRole("contentinfo").getByRole("link").first(),
    ];
    for (const target of targets) {
      await target.focus();
      await page.keyboard.press("Tab");
      await page.keyboard.press("Shift+Tab");
      await expect(target).toBeFocused();
      const contrast = await target.evaluate((element) => {
        const luminance = (color: string) => {
          const channels = color
            .match(/[\d.]+/g)!
            .slice(0, 3)
            .map(Number)
            .map((channel) => {
              const value = channel / 255;
              return value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4;
            });
          return channels[0]! * 0.2126 + channels[1]! * 0.7152 + channels[2]! * 0.0722;
        };
        const surface = element.closest("footer, form")!;
        const ring = luminance(getComputedStyle(element).outlineColor);
        const background = luminance(getComputedStyle(surface).backgroundColor);
        return (Math.max(ring, background) + 0.05) / (Math.min(ring, background) + 0.05);
      });
      expect(contrast).toBeGreaterThanOrEqual(3);
    }
  });

  for (const width of [320, 375, 414, 768, 1280]) {
    test(`${width}px에서 필터·초점·상품 비교에 가로 넘침이 없다`, async ({ page }) => {
      await page.setViewportSize({ width, height: 900 });
      await page.goto("/search?category=set&minPrice=10000");
      if (width < 640) await page.getByRole("button", { name: /필터\s*2/ }).click();
      const input = page.getByLabel("최대가");
      await input.focus();
      await page.keyboard.press("Tab");
      await page.keyboard.press("Shift+Tab");
      const metrics = await input.evaluate((element) => ({
        height: element.getBoundingClientRect().height,
        outline: getComputedStyle(element).outlineWidth,
      }));
      expect(metrics.height).toBeGreaterThanOrEqual(44);
      expect(metrics.outline).toBe("2px");
      const submit = await page.getByRole("button", { name: "검색", exact: true }).boundingBox();
      expect(submit!.height).toBeGreaterThanOrEqual(44);
      expect(
        await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth),
      ).toBeLessThanOrEqual(1);
      await page.screenshot({
        path: test.info().outputPath(`search-${width}.png`),
        fullPage: true,
      });
    });
  }
});
