import { expect, test, type Page } from "@playwright/test";

interface SavedCollectionItem {
  readonly id: string;
  readonly setNumber: string;
  readonly status: "wanted";
  readonly createdAt: string;
}

async function mockSignedInShell(page: Page): Promise<void> {
  await page.addInitScript(() => {
    window.localStorage.setItem(
      "gole.session",
      JSON.stringify({
        accountId: "loop-user",
        sessionToken: "",
        role: "USER",
        onboardingRequired: false,
      }),
    );
    Object.defineProperty(navigator, "share", {
      configurable: true,
      value: async (data: ShareData) => {
        window.localStorage.setItem("e2e.last-shared-url", String(data.url));
      },
    });
  });
  await page.route(/\/api\/v1\/users\/[^/]+\/notifications\/unread-count(?:\?.*)?$/, (route) =>
    route.fulfill({ json: { unreadCount: 0 } }),
  );
  await page.route("**/api/v1/accounts/me/onboarding", (route) =>
    route.fulfill({
      json: {
        required: false,
        legacyExempt: false,
        nicknameCompleted: true,
        nickname: "loop-user",
        phoneCompleted: true,
        maskedPhoneNumber: "010-****-0000",
        interestTagsCompleted: true,
        interestTags: [],
        privacyConsented: true,
        marketingConsented: false,
      },
    }),
  );
}

test.describe("시세 → 위시 → 컬렉션 콘텐츠 루프", () => {
  test("비로그인 저장 CTA가 선택한 세트를 로그인 뒤 복귀 경로로 남긴다", async ({ page }) => {
    await page.goto("/prices");

    const relatedSetHref = await page
      .getByRole("link", { name: "세트·매물 보기" })
      .getAttribute("href");
    expect(relatedSetHref).toMatch(/^\/sets\/[^#]+#set-listings-heading$/);
    const selectedSet = relatedSetHref!.split("/")[2]!.split("#")[0]!;

    await page.getByRole("button", { name: "로그인하고 갖고 싶어요" }).click();

    await expect(page).toHaveURL(/\/login\?returnTo=/);
    const returnTo = new URL(page.url()).searchParams.get("returnTo");
    expect(returnTo).toBe(`/prices?set=${selectedSet}`);
  });

  test("선택 세트를 저장·공유하고 컬렉션에서 관련 시세와 매물로 다시 간다", async ({ page }) => {
    await mockSignedInShell(page);
    let savedItems: readonly SavedCollectionItem[] = [];
    let postedBody: Record<string, unknown> | null = null;

    await page.route("**/api/v1/collections/loop-user/items", (route) =>
      route.fulfill({ json: savedItems }),
    );
    await page.route("**/api/v1/collections/items", async (route) => {
      postedBody = route.request().postDataJSON() as Record<string, unknown>;
      const item: SavedCollectionItem = {
        id: "wanted-from-price",
        setNumber: String(postedBody["setNumber"]),
        status: "wanted",
        createdAt: "2026-09-04T00:00:00Z",
      };
      savedItems = [item];
      await route.fulfill({ status: 201, json: item });
    });
    await page.route("**/api/v1/collections/loop-user/estimate", (route) =>
      route.fulfill({ json: { ownedEstimatedValue: 0 } }),
    );

    await page.goto("/prices");
    const relatedSetLink = page.getByRole("link", { name: "세트·매물 보기" });
    const relatedSetHref = await relatedSetLink.getAttribute("href");
    expect(relatedSetHref).toMatch(/^\/sets\/[^#]+#set-listings-heading$/);
    const selectedSet = relatedSetHref!.split("/")[2]!.split("#")[0]!;

    await page.getByRole("button", { name: "갖고 싶어요", exact: true }).click();

    await expect(page.getByRole("status")).toHaveText("위시에 저장했어요.");
    await expect(page.getByRole("button", { name: "위시에 저장됨" })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
    await expect
      .poll(() => postedBody)
      .toEqual({
        userId: "loop-user",
        setNumber: selectedSet,
        status: "WANTED",
      });

    await page.getByRole("button", { name: "공유" }).click();
    await expect(page.getByRole("status")).toHaveText("공유 창을 열었어요.");
    await expect
      .poll(() => page.evaluate(() => window.localStorage.getItem("e2e.last-shared-url")))
      .toBe(`${new URL(page.url()).origin}/prices?set=${selectedSet}`);

    await page.getByRole("link", { name: "내 컬렉션 보기" }).click();
    await expect(page).toHaveURL(/\/collection$/);
    await expect(page.getByText(`#${selectedSet}`, { exact: true })).toBeVisible();
    await expect(page.getByRole("link", { name: "시세 보기" })).toHaveAttribute(
      "href",
      `/prices?set=${selectedSet}`,
    );
    await expect(page.getByRole("link", { name: "매물 보기" })).toHaveAttribute(
      "href",
      `/sets/${selectedSet}#set-listings-heading`,
    );

    await page.getByRole("link", { name: "매물 보기" }).click();
    await expect(page).toHaveURL(new RegExp(`/sets/${selectedSet}#set-listings-heading$`));
    await expect(page.getByRole("heading", { name: /중고 매물/ })).toBeVisible();
  });
});
