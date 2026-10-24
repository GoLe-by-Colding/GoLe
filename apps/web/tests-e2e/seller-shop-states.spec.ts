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

test("후기 재시도는 설정을 다시 조회하지 않고 실패 후에도 다시 복구할 수 있다", async ({
  page,
}) => {
  let configUnavailable = false;
  let configRequests = 0;
  let ratingAvailable = false;
  let ratingRequests = 0;
  await page.route("**/api/v1/config/launch", (route) => {
    configRequests += 1;
    return route.fulfill(
      configUnavailable ? { status: 503, json: { code: "UNAVAILABLE" } } : { json: launch },
    );
  });
  await page.route(`**/api/v1/shops/${sellerId}`, (route) => route.fulfill({ json: [] }));
  await page.route(`**/api/v1/sellers/${sellerId}/rating`, (route) => {
    ratingRequests += 1;
    return route.fulfill(
      ratingAvailable
        ? { json: { sellerId, average: 0, count: 0 } }
        : { status: 503, json: { code: "UNAVAILABLE" } },
    );
  });
  await page.route(`**/api/v1/sellers/${sellerId}/reviews`, (route) => route.fulfill({ json: [] }));

  await page.goto(`/shops/${sellerId}`);
  const main = page.getByRole("main");
  const error = main.getByText("후기를 불러오지 못했어요.", { exact: true });
  const retry = main.getByRole("button", { name: "다시 시도", exact: true });
  await expect(error).toBeVisible();
  const initialConfigRequests = configRequests;
  const initialRatingRequests = ratingRequests;
  configUnavailable = true;

  await retry.click();
  await expect.poll(() => ratingRequests).toBeGreaterThan(initialRatingRequests);
  await expect(error).toBeVisible();
  await expect(retry).toBeVisible();
  expect(configRequests).toBe(initialConfigRequests);

  ratingAvailable = true;
  await retry.click();
  await expect(main.getByText("아직 등록된 후기가 없습니다.", { exact: true })).toBeVisible();
  await expect(error).toHaveCount(0);
  expect(configRequests).toBe(initialConfigRequests);
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
