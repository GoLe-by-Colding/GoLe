import { test, expect } from "@playwright/test";

// 백엔드 없이 검증 가능한 통합 인증 화면 구성/내비게이션 테스트.
test.describe("Auth navigation", () => {
  test("로그인 화면에 로컬 폼 + 소셜 4종 진입이 보인다", async ({ page }) => {
    await page.goto("/login");
    await expect(page.getByRole("heading", { name: "로그인" })).toBeVisible();
    // 로컬 폼
    await expect(page.getByRole("button", { name: "로그인" })).toBeVisible();
    // 소셜(미설정이면 '준비 중'으로 비활성이지만 버튼 자체는 노출)
    await expect(page.getByRole("button", { name: /Google/ })).toBeVisible();
    await expect(page.getByRole("button", { name: /카카오/ })).toBeVisible();
    await expect(page.getByRole("button", { name: /네이버/ })).toBeVisible();
  });

  test("로그인 ↔ 회원가입 탭으로 전환한다", async ({ page }) => {
    await page.goto("/login");
    await page.getByRole("tab", { name: "회원가입" }).click();
    await expect(page).toHaveURL(/\/signup$/);
    await expect(page.getByRole("button", { name: "가입하기" })).toBeVisible();

    await page.getByRole("tab", { name: "로그인" }).click();
    await expect(page).toHaveURL(/\/login$/);
  });

  test("인증 화면에서 뒤로가기 버튼이 보인다", async ({ page }) => {
    await page.goto("/login");
    await expect(page.getByRole("button", { name: "뒤로 가기" })).toBeVisible();
  });

  test("이메일 인증 화면이 렌더된다", async ({ page }) => {
    await page.goto("/verify?email=tester@gole.com");
    await expect(page.getByRole("heading", { name: "이메일 인증" })).toBeVisible();
    await expect(page.getByRole("button", { name: "인증하기" })).toBeVisible();
  });
});
