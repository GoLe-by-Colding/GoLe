import { test, expect, type Locator } from "@playwright/test";

/** 하이드레이션 전 클릭은 아무 일도 하지 않으므로 React 이벤트가 붙은 뒤에 누른다. */
async function waitForHydration(target: Locator): Promise<void> {
  await expect
    .poll(
      () =>
        target.evaluate((element) =>
          Object.keys(element).some((key) => key.startsWith("__reactProps$")),
        ),
      { message: "하이드레이션(React 이벤트 연결) 대기", timeout: 15_000 },
    )
    .toBe(true);
}

/** 링크가 티커의 잘린 영역(스크롤 창) 안에 온전히 보이는지. */
async function insideTicker(link: Locator, frame: Locator): Promise<boolean> {
  const [box, view] = await Promise.all([link.boundingBox(), frame.boundingBox()]);
  if (box === null || view === null) return false;
  return box.x >= view.x - 1 && box.x + box.width <= view.x + view.width + 1;
}

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

  // 흐르는 띠는 멈출 수 있어야 한다(WCAG 2.2.2). hover가 없는 터치·키보드에도 정지 수단이 있어야 한다.
  test.describe("시세 티커", () => {
    test("멈춤 버튼으로 흐름을 끊으면 넘겨 보는 목록이 되고, 재생하면 다시 흐른다", async ({
      page,
    }) => {
      await page.goto("/");
      const ticker = page.getByRole("region", { name: "인기 세트 시세" });
      const track = ticker.getByRole("link").first().locator("..");
      const scroller = track.locator("..");
      const animation = () => track.evaluate((element) => getComputedStyle(element).animationName);
      await expect.poll(animation).toBe("market-ticker");

      const stop = ticker.getByRole("button", { name: "시세 티커 멈춤" });
      await waitForHydration(stop);
      await stop.click();

      await expect(ticker.getByRole("button", { name: "시세 티커 재생" })).toBeVisible();
      await expect.poll(animation).toBe("none");
      await expect
        .poll(() => scroller.evaluate((element) => getComputedStyle(element).overflowX))
        .toBe("auto");
      // 순환용 사본(두 번째 벌)은 숨겨 같은 세트가 두 번 보이지 않는다.
      await expect(track.locator('a[aria-hidden="true"]').first()).toBeHidden();
      expect(await insideTicker(ticker.getByRole("link").first(), scroller)).toBe(true);

      await ticker.getByRole("button", { name: "시세 티커 재생" }).click();
      await expect(ticker.getByRole("button", { name: "시세 티커 멈춤" })).toBeVisible();
      await expect.poll(animation).toBe("market-ticker");
    });

    test("키보드로 뒤에서 들어와도 포커스한 링크가 흐르지 않고 띠 안에 보인다", async ({
      page,
    }) => {
      await page.goto("/");
      const ticker = page.getByRole("region", { name: "인기 세트 시세" });
      const links = ticker.getByRole("link");
      const track = links.first().locator("..");
      const scroller = track.locator("..");
      // count()는 기다리지 않는다 — 스트리밍된 본문이 붙은 뒤에 센다.
      await expect(links.first()).toBeVisible();
      const count = await links.count();
      expect(count).toBeGreaterThan(1);

      // 멈춤 버튼에서 Shift+Tab — 흐르던 띠의 마지막 링크로 키보드 포커스가 들어간다.
      const stop = ticker.getByRole("button", { name: "시세 티커 멈춤" });
      await waitForHydration(stop);
      await stop.focus();
      for (let index = count - 1; index >= Math.max(0, count - 4); index -= 1) {
        await page.keyboard.press("Shift+Tab");
        const focused = links.nth(index);
        await expect(focused).toBeFocused();
        await expect
          .poll(() => track.evaluate((element) => getComputedStyle(element).animationName))
          .toBe("none");
        await expect.poll(() => insideTicker(focused, scroller)).toBe(true);
      }
    });

    test("움직임 줄이기 설정이면 흐르지 않고 멈춤 버튼도 두지 않는다", async ({ page }) => {
      await page.emulateMedia({ reducedMotion: "reduce" });
      await page.goto("/");
      const ticker = page.getByRole("region", { name: "인기 세트 시세" });
      const track = ticker.getByRole("link").first().locator("..");

      await expect
        .poll(() => track.evaluate((element) => getComputedStyle(element).animationName))
        .toBe("none");
      await expect(ticker.getByRole("button", { name: "시세 티커 멈춤" })).toBeHidden();
      await expect(track.locator('a[aria-hidden="true"]').first()).toBeHidden();
    });
  });
});
