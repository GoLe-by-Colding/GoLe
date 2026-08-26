import { test, expect, type Page } from "@playwright/test";

/**
 * 내 정보 화면이 실제로 데이터를 채우는지 본다.
 *
 * 이 화면은 "영구히 불러오는 중"으로 조용히 멈춘 적이 있다. 세 요청이 아예 나가지 않았는데
 * 화면에는 스켈레톤과 "불러오는 중..."만 있어서 오류로도 보이지 않았다. mobile.spec은 이
 * 경로를 방문하지만 가로 스크롤만 재기 때문에 아무것도 안 뜬 화면에서도 통과한다.
 *
 * <b>세션을 `saveSession`이 저장하는 모양 그대로 심는 것이 이 파일의 핵심이다.</b>
 * 앱은 토큰을 로컬 저장소에 두지 않고(인증은 HttpOnly 쿠키) 빈 문자열로 저장한다. 반면
 * support/e2e-session의 signInAs는 실제 토큰을 채워 넣으므로, 그걸 쓰면 "토큰이 비었다"는
 * 조건 자체가 재현되지 않아 회귀를 놓친다. 실제로 그렇게 짰다가 버그를 되살려도 통과했다.
 */

/** 앱이 새로고침 이후 실제로 갖고 있는 세션: 토큰은 빈 문자열이다. */
async function seedCookieSession(page: Page): Promise<void> {
  await page.addInitScript(() => {
    window.localStorage.setItem(
      "gole.session",
      JSON.stringify({ accountId: "acc-1", sessionToken: "", role: "USER" }),
    );
  });
}

/** 쿠키로 인증되는 정상 응답을 흉내 낸다. 백엔드 없이 화면 계약만 본다. */
async function mockProfileApis(page: Page): Promise<void> {
  await page.route("**/api/v1/accounts/me", (route) =>
    route.fulfill({ json: { accountId: "acc-1", email: "seller@gole.test", role: "USER" } }),
  );
  await page.route("**/api/v1/orders**", (route) =>
    route.fulfill({
      json: [{ id: "ORD-PROFILE-1", status: "completed", amount: 280000 }],
    }),
  );
  await page.route("**/api/v1/listings/mine**", (route) =>
    route.fulfill({
      json: [
        {
          id: "listing-sold-1",
          sellerId: "acc-1",
          title: "에펠탑 10307",
          price: 280000,
          status: "sold",
          photoUrls: [],
        },
      ],
    }),
  );
}

test.describe("내 정보", () => {
  test.skip(!!process.env.E2E_BASE_URL, "응답 가로채기 기반 — 로컬 프론트 전용");

  test.beforeEach(async ({ page }) => {
    await seedCookieSession(page);
    await mockProfileApis(page);
  });

  test("빈 토큰 세션에서도 이메일이 로딩 상태에 멈추지 않고 채워진다", async ({ page }) => {
    await page.goto("/profile");

    // UUID 조각이 아니라 이메일이 보여야 한다.
    await expect(page.getByRole("heading", { name: "seller@gole.test" })).toBeVisible();
    await expect(page.getByText("불러오는 중")).toHaveCount(0);
    await expect(page.getByText("불러오지 못했어요")).toHaveCount(0);
  });

  test("내 매물 탭은 판매완료 매물까지 보여준다", async ({ page }) => {
    await page.goto("/profile");
    await page.getByRole("button", { name: "내 매물" }).click();

    await expect(page.getByRole("link", { name: /에펠탑 10307/ })).toBeVisible();
    // 검색 API는 활성 매물만 준다. 판매완료가 보인다는 건 전용 조회를 타고 있다는 뜻이다.
    await expect(page.getByText("판매완료")).toBeVisible();
  });

  test("구매 내역 탭이 스켈레톤에 머무르지 않는다", async ({ page }) => {
    await page.goto("/profile");
    await page.getByRole("button", { name: "구매 내역" }).click();

    await expect(page.getByRole("link", { name: /ORD-PROF/ })).toBeVisible();
  });

  test("조회에 실패하면 로딩이 아니라 실패라고 말하고 다시 시도를 준다", async ({ page }) => {
    // beforeEach가 심어 둔 정상 응답을 실패로 덮어쓴다(나중에 등록한 라우트가 이긴다).
    await page.route("**/api/v1/accounts/me", (route) =>
      route.fulfill({ status: 500, json: { code: "BOOM", message: "실패" } }),
    );

    await page.goto("/profile");

    await expect(page.getByText("불러오지 못했어요")).toBeVisible();
    await expect(page.getByText("불러오는 중")).toHaveCount(0);
    await expect(page.getByRole("button", { name: "다시 시도" })).toBeVisible();
  });
});
