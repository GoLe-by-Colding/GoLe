import { expect, test, type Page } from "@playwright/test";

async function openSecurityPage(page: Page, passwordChangeAvailable: boolean): Promise<void> {
  await page.addInitScript(() => {
    window.localStorage.setItem(
      "gole.session",
      JSON.stringify({
        accountId: "account-1",
        sessionToken: "",
        role: "USER",
        onboardingRequired: false,
      }),
    );
  });
  await page.route("**/api/v1/accounts/me", (route) =>
    route.fulfill({
      json: {
        accountId: "account-1",
        email: "member@gole.test",
        role: "USER",
        onboardingRequired: false,
        nickname: null,
        passwordChangeAvailable,
      },
    }),
  );
  await page.route(/\/api\/v1\/users\/[^/]+\/notifications\/unread-count(?:\?.*)?$/, (route) =>
    route.fulfill({ json: { unreadCount: 0 } }),
  );
  await page.route("**/api/v1/accounts/me/onboarding", (route) =>
    route.fulfill({ json: { required: false, legacyExempt: true } }),
  );
  await page.route("**/api/v1/accounts/me/third-party-provision-consents/current", (route) =>
    route.fulfill({
      json: { noticeVersion: "2026-09-04", consented: false, lastDecisionAt: null },
    }),
  );
  await page.route("**/api/v1/config/launch", (route) =>
    route.fulfill({
      json: {
        stage: 2,
        tradeMode: "MANUAL_SETTLEMENT",
        features: { payments: true, reviews: true, partnerPayout: false },
        sellerIdentityVerificationReady: true,
        emailAuthenticationAvailable: true,
        updatedAt: null,
      },
    }),
  );
  await page.goto("/profile/security");
}

test("소셜 전용 계정은 현재 비밀번호 변경 폼을 볼 수 없다", async ({ page }) => {
  await openSecurityPage(page, false);

  await expect(
    page.getByText("소셜 로그인으로 만든 계정에는 변경할 비밀번호가 없습니다."),
  ).toBeVisible();
  await expect(page.getByLabel("현재 비밀번호")).toHaveCount(0);
  await expect(page.getByRole("button", { name: "비밀번호 변경" })).toHaveCount(0);
});

test("이메일 비밀번호 계정에는 변경 폼을 보여준다", async ({ page }) => {
  await openSecurityPage(page, true);

  await expect(page.getByLabel("현재 비밀번호")).toBeVisible();
  await expect(page.getByRole("button", { name: "비밀번호 변경" })).toBeVisible();
});
