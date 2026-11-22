import { expect, test, type Page } from "@playwright/test";

const externalBaseUrl = process.env.E2E_BASE_URL;
const targetsRemoteHost =
  externalBaseUrl !== undefined &&
  !["localhost", "127.0.0.1"].includes(new URL(externalBaseUrl).hostname);
const VIEWER = "user-block-viewer";
const SELLER = "user-block-seller";
const launch = {
  stage: 1,
  tradeMode: "DIRECT_CHAT",
  features: { payments: false, reviews: true, partnerPayout: false },
  sellerIdentityVerificationReady: true,
  emailAuthenticationAvailable: true,
  updatedAt: null,
};

async function mockSession(page: Page, accountId: string | null): Promise<void> {
  if (accountId !== null) {
    await page.addInitScript((id) => {
      window.localStorage.setItem(
        "gole.session",
        JSON.stringify({ accountId: id, sessionToken: "", role: "USER" }),
      );
    }, accountId);
    await page.route("**/api/v1/accounts/me", (route) =>
      route.fulfill({ json: { accountId, email: "block@gole.test", role: "USER" } }),
    );
    await page.route("**/api/v1/accounts/me/onboarding", (route) =>
      route.fulfill({ json: { required: false, legacyExempt: true, phoneCompleted: true } }),
    );
    await page.route(`**/api/v1/users/${accountId}/notifications/unread-count`, (route) =>
      route.fulfill({ json: { unreadCount: 0 } }),
    );
    await page.route(`**/api/v1/users/${accountId}/following`, (route) =>
      route.fulfill({ json: [] }),
    );
  }
}

async function mockShop(page: Page, sellerId: string): Promise<void> {
  await page.route("**/api/v1/config/launch", (route) => route.fulfill({ json: launch }));
  await page.route(`**/api/v1/shops/${sellerId}`, (route) => route.fulfill({ json: [] }));
  await page.route(`**/api/v1/sellers/${sellerId}/rating`, (route) =>
    route.fulfill({ json: { sellerId, average: 0, count: 0 } }),
  );
  await page.route(`**/api/v1/sellers/${sellerId}/reviews`, (route) => route.fulfill({ json: [] }));
}

test.describe("사용자 차단 — 셀러샵", () => {
  test.skip(targetsRemoteHost, "로컬 프론트 전용 합성 응답 검증");

  test("다른 판매자 샵에서 확인 모달을 거쳐 차단하면 차단함과 관리 링크가 보인다", async ({
    page,
  }) => {
    await mockSession(page, VIEWER);
    await mockShop(page, SELLER);
    let blockCalls = 0;
    await page.route(`**/api/v1/chat/social/blocks/${SELLER}`, (route) => {
      blockCalls += 1;
      return route.fulfill({ status: 204 });
    });

    await page.goto(`/shops/${SELLER}`);
    await page.getByRole("button", { name: "차단", exact: true }).click();
    const dialog = page.getByRole("alertdialog", { name: "이 사용자를 차단할까요?" });
    await expect(dialog).toBeVisible();
    await dialog.getByRole("button", { name: "차단", exact: true }).click();

    await expect(page.getByText("차단함", { exact: true })).toBeVisible();
    await expect(page.getByRole("link", { name: "차단 관리" })).toHaveAttribute(
      "href",
      "/profile/blocks",
    );
    expect(blockCalls).toBe(1);
  });

  test("본인 샵에는 차단 버튼이 없다", async ({ page }) => {
    await mockSession(page, VIEWER);
    await mockShop(page, VIEWER);

    await page.goto(`/shops/${VIEWER}`);
    await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
    await expect(page.getByRole("button", { name: "차단", exact: true })).toHaveCount(0);
  });

  test("비로그인에는 차단 버튼이 없다", async ({ page }) => {
    await mockSession(page, null);
    await mockShop(page, SELLER);

    await page.goto(`/shops/${SELLER}`);
    await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
    await expect(page.getByRole("button", { name: "차단", exact: true })).toHaveCount(0);
  });
});
