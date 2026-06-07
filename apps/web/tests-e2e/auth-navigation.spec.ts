import { test, expect } from "@playwright/test";

// 백엔드 없이 검증 가능한 인증 화면 구성/내비게이션 테스트.
test.describe("Auth navigation", () => {
  test("회원가입 화면이 렌더되고 폼이 보인다", async ({ page }) => {
    await page.goto("/signup");
    await expect(page.getByRole("heading", { name: "회원가입" })).toBeVisible();
    await expect(page.getByRole("button", { name: "가입하기" })).toBeVisible();
  });

  test("로그인 ↔ 회원가입 링크로 이동한다", async ({ page }) => {
    await page.goto("/login");
    await expect(page.getByRole("heading", { name: "로그인" })).toBeVisible();

    await page.getByRole("link", { name: "회원가입" }).click();
    await expect(page).toHaveURL(/\/signup$/);

    await page.getByRole("link", { name: "로그인" }).click();
    await expect(page).toHaveURL(/\/login$/);
  });

  test("가입 후 이메일 인증 화면으로 이동한다", async ({ page }) => {
    await page.goto("/verify?email=tester@gole.com");
    await expect(page.getByRole("heading", { name: "이메일 인증" })).toBeVisible();
    await expect(page.getByRole("button", { name: "인증하기" })).toBeVisible();
  });
});
