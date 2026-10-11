import { expect, test, type Page } from "@playwright/test";

const ACCOUNT_ID = "trend-user";

async function mockCollection(page: Page): Promise<void> {
  await page.addInitScript((accountId) => {
    window.localStorage.setItem(
      "gole.session",
      JSON.stringify({ accountId, sessionToken: "", role: "USER" }),
    );
  }, ACCOUNT_ID);
  // 합성 세션이 헤더 폴링·온보딩 배너의 실제 401로 지워지지 않게 격리한다.
  await page.route(/\/api\/v1\/users\/[^/]+\/notifications\/unread-count(?:\?.*)?$/, (route) =>
    route.fulfill({ json: { unreadCount: 0 } }),
  );
  await page.route("**/api/v1/accounts/me/onboarding", (route) =>
    route.fulfill({
      json: {
        required: false,
        legacyExempt: true,
        nicknameCompleted: true,
        nickname: "e2e",
        phoneCompleted: true,
        maskedPhoneNumber: "010-****-0000",
        interestTagsCompleted: true,
        interestTags: [],
        privacyConsented: true,
        marketingConsented: false,
      },
    }),
  );
  await page.route(`**/api/v1/collections/${ACCOUNT_ID}/items`, (route) =>
    route.fulfill({
      json: [
        { id: "item-1", setNumber: "10307", status: "owned", createdAt: "2026-09-01T00:00:00Z" },
      ],
    }),
  );
  await page.route(`**/api/v1/collections/${ACCOUNT_ID}/estimate`, (route) =>
    route.fulfill({ json: { ownedEstimatedValue: 104_000 } }),
  );
}

test.describe("컬렉션 자산 추이", () => {
  test("기간 첫 점 대비 증감과 시세 반영 비율을 보여주고 기간을 바꿔 다시 부른다", async ({
    page,
  }) => {
    await mockCollection(page);
    const requestedDays: string[] = [];
    await page.route(`**/api/v1/collections/${ACCOUNT_ID}/value-history**`, (route) => {
      requestedDays.push(new URL(route.request().url()).searchParams.get("days") ?? "");
      return route.fulfill({
        json: {
          points: [
            { date: "2026-08-01", ownedValue: 100_000, ownedCount: 3, pricedCount: 2 },
            { date: "2026-09-01", ownedValue: 98_000, ownedCount: 3, pricedCount: 2 },
            { date: "2026-10-06", ownedValue: 104_000, ownedCount: 3, pricedCount: 2 },
          ],
        },
      });
    });

    await page.goto("/collection");

    await expect(page.getByRole("heading", { name: "내 레고 자산 추이" })).toBeVisible();
    await expect(page.getByText("▲ ₩4,000 (4.0%)")).toBeVisible();
    await expect(page.getByText("26.08.01 대비 · 최근 90일")).toBeVisible();
    await expect(page.getByText(/시세가 잡힌 세트 2\/3개 기준/)).toBeVisible();
    // 개발 모드 StrictMode가 effect를 두 번 돌려 같은 기간 요청이 겹칠 수 있다. 값만 본다.
    expect(new Set(requestedDays)).toEqual(new Set(["90"]));

    await page.getByRole("button", { name: "30일" }).click();
    await expect(page.getByRole("button", { name: "30일" })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
    await expect.poll(() => requestedDays.at(-1)).toBe("30");
    await expect(page.getByText("26.08.01 대비 · 최근 30일")).toBeVisible();
  });

  test("점이 하나뿐이면 내일부터 쌓인다고 안내한다", async ({ page }) => {
    await mockCollection(page);
    await page.route(`**/api/v1/collections/${ACCOUNT_ID}/value-history**`, (route) =>
      route.fulfill({
        json: {
          points: [{ date: "2026-10-06", ownedValue: 104_000, ownedCount: 1, pricedCount: 1 }],
        },
      }),
    );

    await page.goto("/collection");

    await expect(page.getByText("내일부터 추이가 쌓여요")).toBeVisible();
    await expect(page.getByText(/시세가 잡힌 세트/)).toHaveCount(0);
  });

  test("추이 조회가 실패해도 컬렉션 목록은 그대로 쓴다", async ({ page }) => {
    await mockCollection(page);
    let failing = true;
    await page.route(`**/api/v1/collections/${ACCOUNT_ID}/value-history**`, (route) =>
      failing
        ? route.fulfill({ status: 500, json: { code: "INTERNAL", message: "일시적인 오류" } })
        : route.fulfill({ json: { points: [] } }),
    );

    await page.goto("/collection");

    await expect(page.getByText("추이를 불러오지 못했어요. 일시적인 오류")).toBeVisible();
    await expect(page.getByText("#10307", { exact: true })).toBeVisible();
    await expect(page.getByText("₩104,000")).toBeVisible();

    failing = false;
    await page.getByRole("button", { name: "다시 시도" }).click();
    await expect(page.getByText("내일부터 추이가 쌓여요")).toBeVisible();
  });
});
