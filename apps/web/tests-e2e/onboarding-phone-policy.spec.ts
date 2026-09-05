import { expect, test, type Page } from "@playwright/test";

async function mockOnboardingStatus(page: Page, phoneVerificationRequired: boolean): Promise<void> {
  await page.route("**/api/v1/accounts/me/onboarding", (route) =>
    route.fulfill({
      json: {
        required: true,
        legacyExempt: false,
        nicknameCompleted: true,
        nickname: "브릭러버",
        phoneVerificationRequired,
        phoneCompleted: false,
        maskedPhoneNumber: null,
        interestTagsCompleted: true,
        interestTags: ["technic"],
        privacyConsented: false,
        marketingConsented: false,
      },
    }),
  );
}

test("전화 인증이 선택인 운영 정책에서는 OTP 단계를 건너뛰고 3단계 진행률을 표시한다", async ({
  page,
}) => {
  await mockOnboardingStatus(page, false);

  await page.goto("/onboarding");

  await expect(page.getByRole("heading", { name: "약관에 동의해 주세요" })).toBeVisible();
  await expect(page.getByText("3/3 단계")).toBeVisible();
  await expect(page.getByRole("list", { name: "전체 3단계 중 3단계" })).toBeVisible();
  await expect(page.getByText("휴대폰 번호를 인증해 주세요")).toHaveCount(0);
  await expect(page.getByText("수집·이용 목적:", { exact: false })).toBeVisible();
  await expect(page.getByText("보유기간:", { exact: false })).toBeVisible();
  await expect(page.getByText("거부권·불이익:", { exact: false })).toBeVisible();
  await expect(page.getByText(/거부해도 서비스 이용에 불이익이 없습니다/)).toBeVisible();
});

test("로컬 호환 정책에서는 미완료 전화 인증을 4단계 중 두 번째 단계로 유지한다", async ({
  page,
}) => {
  await mockOnboardingStatus(page, true);

  await page.goto("/onboarding");

  await expect(page.getByRole("heading", { name: "휴대폰 번호를 인증해 주세요" })).toBeVisible();
  await expect(page.getByText("2/4 단계")).toBeVisible();
  await expect(page.getByRole("list", { name: "전체 4단계 중 2단계" })).toBeVisible();
});
