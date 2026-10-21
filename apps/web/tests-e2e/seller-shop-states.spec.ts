import { expect, test } from "@playwright/test";

const sellerId = "seller-state-test";
const launch = {
  stage: 1,
  tradeMode: "DIRECT_CHAT",
  features: { payments: false, reviews: true, partnerPayout: false },
  sellerIdentityVerificationReady: true,
  emailAuthenticationAvailable: true,
  updatedAt: null,
};

test("샵 조회 실패는 빈 샵과 구분하고, 후기 실패에도 상품을 보여 준다", async ({ page }) => {
  let shopAvailable = false;
  await page.route("**/api/v1/config/launch", (route) => route.fulfill({ json: launch }));
  await page.route(`**/api/v1/shops/${sellerId}`, (route) => {
    return route.fulfill(
      !shopAvailable
        ? { status: 503, json: { code: "UNAVAILABLE", message: "일시적인 오류" } }
        : {
            json: [
              {
                id: "listing-state-test",
                sellerId,
                title: "다시 불러온 세트",
                price: 10000,
                condition: "used_good",
                catalogSetNumber: null,
                category: "set",
                status: "active",
                photoUrls: [],
                createdAt: "2026-10-18T00:00:00Z",
              },
            ],
          },
    );
  });
  await page.route(`**/api/v1/sellers/${sellerId}/rating`, (route) =>
    route.fulfill({ status: 503, json: { code: "UNAVAILABLE", message: "일시적인 오류" } }),
  );
  await page.route(`**/api/v1/sellers/${sellerId}/reviews`, (route) => route.fulfill({ json: [] }));

  await page.goto(`/shops/${sellerId}`);
  await expect(page.getByText("상품 목록을 불러오지 못했어요.")).toBeVisible();
  await expect(page.getByText("판매 중인 상품이 없습니다.")).toHaveCount(0);
  shopAvailable = true;
  await page.getByRole("button", { name: "다시 시도" }).first().click();
  await expect(page.getByText("다시 불러온 세트")).toBeVisible();
  await expect(page.getByText("후기를 불러오지 못했어요.")).toBeVisible();
  await expect(page.getByText("후기 없음")).toHaveCount(0);
});

test("성공한 빈 샵은 빈 상태로 표시한다", async ({ page }) => {
  await page.route("**/api/v1/config/launch", (route) =>
    route.fulfill({ json: { ...launch, features: { ...launch.features, reviews: false } } }),
  );
  await page.route(`**/api/v1/shops/${sellerId}`, (route) => route.fulfill({ json: [] }));

  await page.goto(`/shops/${sellerId}`);
  await expect(page.getByText("판매 중인 상품이 없습니다.")).toBeVisible();
  await expect(page.getByText("상품 목록을 불러오지 못했어요.")).toHaveCount(0);
});
