import { expect, test, type Page } from "@playwright/test";

const externalBaseUrl = process.env.E2E_BASE_URL;
const targetsRemoteHost =
  externalBaseUrl !== undefined &&
  !["localhost", "127.0.0.1"].includes(new URL(externalBaseUrl).hostname);
const VIEWER = "user-block-viewer";
const SELLER = "user-block-seller";
const launch = {
  stage: 1,
  tradeMode: "DIRECT_CHAT",
  features: { payments: false, reviews: true, partnerPayout: false },
  sellerIdentityVerificationReady: true,
  emailAuthenticationAvailable: true,
  updatedAt: null,
};

async function mockSession(page: Page, accountId: string | null): Promise<void> {
  if (accountId !== null) {
    await page.addInitScript((id) => {
      window.localStorage.setItem(
        "gole.session",
        JSON.stringify({ accountId: id, sessionToken: "", role: "USER" }),
      );
    }, accountId);
    await page.route("**/api/v1/accounts/me", (route) =>
      route.fulfill({ json: { accountId, email: "block@gole.test", role: "USER" } }),
    );
    await page.route("**/api/v1/accounts/me/onboarding", (route) =>
      route.fulfill({ json: { required: false, legacyExempt: true, phoneCompleted: true } }),
    );
    await page.route(`**/api/v1/users/${accountId}/notifications/unread-count`, (route) =>
      route.fulfill({ json: { unreadCount: 0 } }),
    );
    await page.route(`**/api/v1/users/${accountId}/following`, (route) =>
      route.fulfill({ json: [] }),
    );
  }
}

async function mockShop(page: Page, sellerId: string): Promise<void> {
  await page.route("**/api/v1/config/launch", (route) => route.fulfill({ json: launch }));
  await page.route(`**/api/v1/shops/${sellerId}`, (route) => route.fulfill({ json: [] }));
  await page.route(`**/api/v1/sellers/${sellerId}/rating`, (route) =>
    route.fulfill({ json: { sellerId, average: 0, count: 0 } }),
  );
  await page.route(`**/api/v1/sellers/${sellerId}/reviews`, (route) => route.fulfill({ json: [] }));
  await page.route("**/api/v1/chat/social/blocks", (route) => route.fulfill({ json: [] }));
}

test.describe("사용자 차단 — 셀러샵", () => {
  test.skip(targetsRemoteHost, "로컬 프론트 전용 합성 응답 검증");

  test("다른 판매자 샵에서 확인 모달을 거쳐 차단하면 차단함과 관리 링크가 보인다", async ({
    page,
  }) => {
    await mockSession(page, VIEWER);
    await mockShop(page, SELLER);
    let blockCalls = 0;
    await page.route(`**/api/v1/chat/social/blocks/${SELLER}`, (route) => {
      blockCalls += 1;
      return route.fulfill({ status: 204 });
    });

    await page.goto(`/shops/${SELLER}`);
    await expect(page.getByRole("button", { name: "팔로우", exact: true })).toBeVisible();
    await page.getByRole("button", { name: "차단", exact: true }).click();
    const dialog = page.getByRole("alertdialog", { name: "이 사용자를 차단할까요?" });
    await expect(dialog).toBeVisible();
    await dialog.getByRole("button", { name: "차단", exact: true }).click();

    await expect(page.getByText("차단함", { exact: true })).toBeVisible();
    await expect(page.getByRole("link", { name: "차단 관리" })).toHaveAttribute(
      "href",
      "/profile/blocks",
    );
    await expect(page.getByRole("button", { name: /^팔로(우|잉)$/ })).toHaveCount(0);
    await expect(page.getByRole("link", { name: "1:1 대화" })).toHaveCount(0);
    expect(blockCalls).toBe(1);
  });

  test("이미 차단한 판매자 샵은 처음부터 차단함만 보이고 다시 차단하지 않는다", async ({
    page,
  }) => {
    await mockSession(page, VIEWER);
    await mockShop(page, SELLER);
    await page.route("**/api/v1/chat/social/blocks", (route) => route.fulfill({ json: [SELLER] }));
    let blockCalls = 0;
    await page.route(`**/api/v1/chat/social/blocks/${SELLER}`, (route) => {
      blockCalls += 1;
      return route.fulfill({ status: 204 });
    });

    await page.goto(`/shops/${SELLER}`);
    await expect(page.getByText("차단함", { exact: true })).toBeVisible();
    await expect(page.getByRole("button", { name: "차단", exact: true })).toHaveCount(0);
    await expect(page.getByRole("button", { name: /^팔로(우|잉)$/ })).toHaveCount(0);
    await expect(page.getByRole("link", { name: "1:1 대화" })).toHaveCount(0);
    expect(blockCalls).toBe(0);
  });

  test("본인 샵에는 차단 버튼이 없다", async ({ page }) => {
    await mockSession(page, VIEWER);
    await mockShop(page, VIEWER);

    await page.goto(`/shops/${VIEWER}`);
    await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
    await expect(page.getByRole("button", { name: "차단", exact: true })).toHaveCount(0);
  });

  test("비로그인에는 차단 버튼이 없다", async ({ page }) => {
    await mockSession(page, null);
    await mockShop(page, SELLER);

    await page.goto(`/shops/${SELLER}`);
    await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
    await expect(page.getByRole("button", { name: "차단", exact: true })).toHaveCount(0);
  });
});

const BLOCKED = "blocked-1";

/** 차단 목록 API를 흉내 낸다. 해제하면 목록에서 빠지고, 조회·해제 실패를 주문할 수 있다. */
async function mockBlockedUsers(
  page: Page,
  options: { listFailures?: number; unblockFails?: boolean } = {},
): Promise<{ unblockCalls: number }> {
  const state = {
    blocked: [BLOCKED],
    listFailuresLeft: options.listFailures ?? 0,
    unblockCalls: 0,
  };
  await page.route("**/api/v1/chat/social/blocks", (route) => {
    if (route.request().method() !== "GET") return route.fallback();
    if (state.listFailuresLeft > 0) {
      state.listFailuresLeft -= 1;
      return route.fulfill({ status: 500, json: { code: "INTERNAL_ERROR", message: "서버 오류" } });
    }
    return route.fulfill({ json: state.blocked });
  });
  await page.route(`**/api/v1/chat/social/blocks/${BLOCKED}`, (route) => {
    state.unblockCalls += 1;
    if (options.unblockFails) {
      return route.fulfill({ status: 500, json: { code: "INTERNAL_ERROR", message: "서버 오류" } });
    }
    state.blocked = [];
    return route.fulfill({ status: 204 });
  });
  await page.route("**/api/v1/accounts/public-profiles*", (route) =>
    route.fulfill({ json: [{ accountId: BLOCKED, nickname: "괴롭힘곰" }] }),
  );
  return state;
}

/**
 * 내 정보 화면이 세션과 함께 부르는 조회를 막는다. 알림 폴링·온보딩 등이 실제 백엔드에서 401을 받으면
 * 전역 stale-session 정리가 먼저 돌아 화면이 로그인 안내로 바뀐다(`profile.spec.ts`의 같은 이유).
 */
async function mockProfileShell(page: Page, accountId: string): Promise<void> {
  await page.route(`**/api/v1/users/${accountId}/notifications/unread-count`, (route) =>
    route.fulfill({ json: { unreadCount: 0 } }),
  );
  await page.route("**/api/v1/accounts/me/onboarding", (route) =>
    route.fulfill({
      json: {
        required: false,
        legacyExempt: true,
        nicknameCompleted: true,
        nickname: "차단테스트",
        phoneCompleted: true,
        maskedPhoneNumber: "010-****-0000",
        interestTagsCompleted: true,
        interestTags: [],
        privacyConsented: true,
        marketingConsented: false,
      },
    }),
  );
  await page.route("**/api/v1/accounts/me/third-party-provision-consents/current", (route) =>
    route.fulfill({
      json: { noticeVersion: "2026-09-04", consented: false, lastDecisionAt: null },
    }),
  );
  await page.route("**/api/v1/orders?buyerId=**", (route) => route.fulfill({ json: [] }));
  await page.route("**/api/v1/orders/sales", (route) => route.fulfill({ json: [] }));
  await page.route("**/api/v1/orders/settlements", (route) => route.fulfill({ json: [] }));
  await page.route("**/api/v1/listings/mine**", (route) => route.fulfill({ json: [] }));
}

test.describe("사용자 차단 — 차단 관리 화면", () => {
  test.skip(targetsRemoteHost, "로컬 프론트 전용 합성 응답 검증");

  test("차단한 사용자는 닉네임으로 보이고, 해제하면 목록에서 사라진다", async ({ page }) => {
    await mockSession(page, VIEWER);
    const state = await mockBlockedUsers(page);

    await page.goto("/profile/blocks");
    await expect(page.getByRole("heading", { level: 1, name: "차단 관리" })).toBeVisible();
    await expect(page.getByText("괴롭힘곰", { exact: true })).toBeVisible();
    await expect(page.getByText(BLOCKED)).toHaveCount(0);

    await page.getByRole("button", { name: "차단 해제", exact: true }).click();
    await expect(page.getByText("차단한 사용자가 없어요.", { exact: true })).toBeVisible();
    await expect(page.getByText("괴롭힘곰", { exact: true })).toHaveCount(0);
    expect(state.unblockCalls).toBe(1);
  });

  test("목록 조회가 실패하면 오류를 보이고, 다시 시도하면 목록을 보인다", async ({ page }) => {
    await mockSession(page, VIEWER);
    await mockBlockedUsers(page, { listFailures: 1 });

    await page.goto("/profile/blocks");
    await expect(page.getByText("차단 목록을 불러오지 못했어요.", { exact: true })).toBeVisible();
    await page.getByRole("button", { name: "다시 시도", exact: true }).click();
    await expect(page.getByText("괴롭힘곰", { exact: true })).toBeVisible();
  });

  test("차단 해제가 실패하면 그 행에 안내를 보이고 행은 남는다", async ({ page }) => {
    await mockSession(page, VIEWER);
    await mockBlockedUsers(page, { unblockFails: true });

    await page.goto("/profile/blocks");
    await page.getByRole("button", { name: "차단 해제", exact: true }).click();
    await expect(
      page.getByText("해제하지 못했어요. 다시 시도해 주세요.", { exact: true }),
    ).toBeVisible();
    await expect(page.getByText("괴롭힘곰", { exact: true })).toBeVisible();
  });

  test("비로그인이면 로그인 안내를 보인다", async ({ page }) => {
    await mockSession(page, null);

    await page.goto("/profile/blocks");
    await expect(page.getByRole("heading", { level: 1, name: "차단 관리" })).toBeVisible();
    await expect(page.getByRole("link", { name: "로그인하러 가기" })).toHaveAttribute(
      "href",
      "/login?returnTo=%2Fprofile%2Fblocks",
    );
  });

  test("내 정보에 차단 관리 화면으로 가는 링크가 있다", async ({ page }) => {
    await mockSession(page, VIEWER);
    await mockProfileShell(page, VIEWER);

    // 차단 관리 화면 자체는 위 테스트들이 다룬다. 여기서는 내 정보의 링크 대상만 본다.
    await page.goto("/profile");
    await expect(page.getByRole("link", { name: "차단 관리", exact: true })).toHaveAttribute(
      "href",
      "/profile/blocks",
    );
  });
});
