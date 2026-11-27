import { expect, test } from "@playwright/test";

const externalUrl = process.env.E2E_BASE_URL;
const remote =
  externalUrl !== undefined && !["localhost", "127.0.0.1"].includes(new URL(externalUrl).hostname);
const apiBase = remote
  ? externalUrl
  : (process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080");

test("세트 상세의 판매 안내는 공개 준비 상태와 일치하며 구매 희망 등록은 유지한다", async ({
  page,
  request,
}) => {
  const main = page.getByRole("main");
  const response = await request.get(`${apiBase}/api/v1/config/launch`);
  expect(response.ok()).toBeTruthy();
  const launch = (await response.json()) as { sellerIdentityVerificationReady: boolean };
  await page.goto("/sets/10307");
  await expect(page.getByRole("heading", { level: 1 })).toContainText("10307");
  if (launch.sellerIdentityVerificationReady) {
    await expect(page.getByRole("link", { name: "이 세트 팔기" }).first()).toHaveAttribute(
      "href",
      "/sell?setNumber=10307",
    );
    await expect(main.getByText("신규 판매 등록 준비 중", { exact: true })).toHaveCount(0);
  } else {
    await expect(page.getByRole("link", { name: "이 세트 팔기" })).toHaveCount(0);
    await expect(main.getByText("신규 판매 등록 준비 중", { exact: true })).toBeVisible();
    await expect(page.getByRole("link", { name: "운영 문의", exact: true })).toHaveAttribute(
      "href",
      "/chat?compose=support&category=PRODUCT_FEEDBACK",
    );
    await expect(main.getByText("최고 구매 희망가", { exact: true })).toHaveCount(5);
    await expect(main.getByText("지금 팔면 받는 값", { exact: true })).toHaveCount(0);
    await expect(main.getByText(/입찰 기간 안에 거래가 열리지 않으면/)).toBeVisible();
  }
  await expect(page.getByRole("link", { name: "로그인하고 입찰하기" })).toHaveAttribute(
    "href",
    "/login?returnTo=%2Fsets%2F10307",
  );
});

test("세트에서 입찰 로그인으로 이동해도 돌아올 세트 주소를 유지한다", async ({ page }) => {
  await page.goto("/sets/10307");
  await page.getByRole("link", { name: "로그인하고 입찰하기" }).click();
  await expect(page).toHaveURL(/\/login\?returnTo=%2Fsets%2F10307$/);
  await expect(page.getByRole("heading", { name: "로그인", exact: true })).toBeVisible();
});

test("세트 매물 조회 실패를 빈 목록으로 숨기지 않는다", async ({ page, request }) => {
  const response = await request.get(`${apiBase}/api/v1/listings?setNumber=10307`);
  await page.goto("/sets/10307");
  const error = page.getByRole("heading", { name: "세트 매물을 불러오지 못했어요" });
  if (!response.ok()) {
    await expect(error).toBeVisible();
    await expect(page.getByText("확인 불가", { exact: true })).toBeVisible();
    await expect(
      page.getByText("아직 공개된 에펠탑 매물이 없습니다.", { exact: true }),
    ).toHaveCount(0);
    await expect(page.getByRole("button", { name: "다시 불러오기" })).toBeVisible();
  } else {
    await expect(error).toHaveCount(0);
    const listings = (await response.json()) as unknown[];
    if (listings.length === 0) {
      await expect(
        page.getByText("아직 공개된 에펠탑 매물이 없습니다.", { exact: true }),
      ).toBeVisible();
    }
  }
});

test("세트 매물 조회가 반복 실패해도 0건으로 표시하지 않고 정상 빈 결과로 복구한다", async ({
  page,
}) => {
  test.skip(process.env.E2E_SEARCH_FIXTURE !== "1" || remote, "전용 읽기 서버에서 실행");
  // 세트마다 다른 요청 주소를 써서 이전 실행의 정상 응답 캐시와 섞이지 않게 한다.
  const setNumber = String(9_000_000 + Math.floor(Math.random() * 1_000_000));
  await page.setViewportSize({ width: 375, height: 900 });
  await page.goto(`/sets/${setNumber}`);
  const main = page.getByRole("main");
  const error = main.getByRole("heading", { name: "세트 매물을 불러오지 못했어요" });
  const count = main.getByText("판매 중", { exact: true }).locator("..").locator("dd");
  const empty = main.getByText("아직 공개된 조회 복구 검증 세트 매물이 없습니다.", {
    exact: true,
  });
  const retry = main.getByRole("button", { name: "다시 불러오기", exact: true });
  await expect(error).toBeVisible();
  await expect(count).toHaveText("확인 불가");
  await expect(empty).toHaveCount(0);

  const retried = page.waitForResponse(
    (response) => new URL(response.url()).pathname === `/sets/${setNumber}`,
  );
  await retry.click();
  await retried;
  await expect(retry).toBeEnabled();
  await expect(error).toBeVisible();
  await expect(count).toHaveText("확인 불가");
  await expect(empty).toHaveCount(0);

  await retry.click();
  await expect(error).toHaveCount(0);
  await expect(count).toHaveText("0건");
  await expect(empty).toBeVisible();
  await expect(page).toHaveURL(new RegExp(`/sets/${setNumber}$`));
});
