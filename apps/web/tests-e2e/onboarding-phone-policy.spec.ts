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

async function sellerReady(page: Page, ready = true) {
  await page.route("**/api/v1/config/launch", (route) =>
    route.fulfill({
      json: {
        stage: 1,
        tradeMode: "DIRECT_CHAT",
        updatedAt: null,
        sellerIdentityVerificationReady: ready,
        emailAuthenticationAvailable: true,
        features: { payments: false, reviews: false, partnerPayout: false },
      },
    }),
  );
}

test("세트에서 판매를 시작하면 로그인 링크에도 세트 번호를 보존한다", async ({ page }) => {
  await sellerReady(page);
  await page.goto("/sell?setNumber=10307");
  await expect(page.getByRole("link", { name: "로그인하러 가기" })).toHaveAttribute(
    "href",
    "/login?returnTo=%2Fsell%3FsetNumber%3D10307",
  );
});

test("일반 가입 전화인증이 선택이어도 판매자는 인증하고 원래 세트로 돌아온다", async ({ page }) => {
  await sellerReady(page);
  let verified = false;
  await page.route("**/api/v1/accounts/me/onboarding", (route) =>
    route.fulfill({
      json: {
        required: false,
        legacyExempt: false,
        nicknameCompleted: true,
        nickname: "판매자",
        phoneVerificationRequired: false,
        phoneCompleted: verified,
        maskedPhoneNumber: null,
        interestTagsCompleted: true,
        interestTags: ["technic"],
        privacyConsented: true,
        marketingConsented: false,
      },
    }),
  );
  await page.route("**/api/v1/accounts/me/onboarding/phone/verification", (route) =>
    route.fulfill({ json: { maskedPhoneNumber: "010-****-1234", expiresInSeconds: 300 } }),
  );
  await page.route("**/api/v1/accounts/me/onboarding/phone/verification/confirm", (route) => {
    expect(route.request().postDataJSON()).toEqual({ phoneNumber: "01011111234", code: "123456" });
    verified = true;
    return route.fulfill({ status: 204 });
  });
  await page.goto("/onboarding?purpose=seller&returnTo=%2Fsell%3FsetNumber%3D10307");
  await expect(page.getByRole("heading", { name: "휴대폰 번호를 인증해 주세요" })).toBeVisible();
  await page.getByLabel("휴대폰 번호", { exact: true }).fill("01011111234");
  await page.getByRole("button", { name: "인증 코드 받기", exact: true }).click();
  await expect(page.getByRole("button", { name: "인증하고 다음" })).toBeDisabled();
  await page.getByLabel("인증 코드", { exact: true }).fill("123456");
  await page.getByRole("button", { name: "인증하고 다음" }).click();
  await expect(page).toHaveURL(/\/sell\?setNumber=10307$/);
  expect(verified).toBe(true);
});

test("판매자 인증 준비 전에는 쿼리만 바꿔 전화 인증을 열 수 없다", async ({ page }) => {
  await sellerReady(page, false);
  await mockOnboardingStatus(page, false);
  await page.goto("/onboarding?purpose=seller&returnTo=%2Fsell");
  await expect(page.getByRole("heading", { name: "판매자 인증 준비 중" })).toBeVisible();
  await expect(page.getByRole("button", { name: "인증 코드 받기" })).toHaveCount(0);
});

test("코드 발송 뒤 번호를 바꾸면 이전 번호의 코드 입력을 폐기한다", async ({ page }) => {
  await mockOnboardingStatus(page, true);
  await page.route("**/api/v1/accounts/me/onboarding/phone/verification", async (route) => {
    expect(route.request().postDataJSON()).toEqual({ phoneNumber: "01012345678" });
    await route.fulfill({ json: { maskedPhoneNumber: "010-****-5678", expiresInSeconds: 300 } });
  });

  await page.goto("/onboarding");
  await page.getByRole("textbox", { name: "휴대폰 번호" }).fill("010-1234-5678");
  await page.getByRole("button", { name: "인증 코드 받기" }).click();
  await expect(page.getByRole("textbox", { name: "인증 코드" })).toBeVisible();

  await page.getByRole("textbox", { name: "휴대폰 번호" }).fill("010-9876-5432");

  await expect(page.getByRole("textbox", { name: "인증 코드" })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "인증하고 다음" })).toHaveCount(0);
  await expect(page.getByText("010-****-5678로 카카오톡 인증 코드를 보냈습니다.")).toHaveCount(0);
});
