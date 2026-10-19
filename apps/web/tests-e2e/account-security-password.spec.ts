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
