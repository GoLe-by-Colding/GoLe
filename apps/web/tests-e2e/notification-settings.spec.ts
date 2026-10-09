import { expect, test, type Page } from "@playwright/test";

const ACCOUNT_ID = "notification-pref-e2e";
const PREFERENCES_PATH = `**/api/v1/users/${ACCOUNT_ID}/notification-preferences`;

type CategoryKey = "trade" | "offer" | "watch" | "community";

interface Category {
  readonly key: CategoryKey;
  readonly label: string;
  readonly enabled: boolean;
  readonly mandatory: boolean;
}

const INITIAL: readonly Category[] = [
  { key: "trade", label: "거래 진행", enabled: true, mandatory: true },
  { key: "offer", label: "가격 제안·입찰", enabled: true, mandatory: false },
  { key: "watch", label: "찜·관심 세트", enabled: true, mandatory: false },
  { key: "community", label: "커뮤니티·부품 요청", enabled: false, mandatory: false },
];

async function seedSession(page: Page): Promise<void> {
  await page.addInitScript((accountId) => {
    window.localStorage.setItem(
      "gole.session",
      JSON.stringify({ accountId, sessionToken: "", role: "USER" }),
    );
  }, ACCOUNT_ID);
  // 합성 세션이 헤더 폴링·온보딩 배너의 실제 401로 지워지지 않게 격리한다.
  await page.route(/\/api\/v1\/users\/[^/]+\/notifications\/unread-count(?:\?.*)?$/, (route) =>
    route.fulfill({ json: { unreadCount: 0 } }),
  );
  await page.route("**/api/v1/accounts/me/onboarding", (route) =>
    route.fulfill({
      json: {
        required: false,
        legacyExempt: true,
        nicknameCompleted: true,
        nickname: "e2e",
        phoneCompleted: true,
        maskedPhoneNumber: "010-****-0000",
        interestTagsCompleted: true,
        interestTags: [],
        privacyConsented: true,
        marketingConsented: false,
      },
    }),
  );
}

test.describe("알림 수신 설정", () => {
  test("비로그인은 로그인 뒤 설정 화면으로 돌아온다", async ({ page }) => {
    await page.goto("/profile/notifications");
    await expect(page.getByRole("link", { name: "로그인하러 가기" })).toHaveAttribute(
      "href",
      "/login?returnTo=%2Fprofile%2Fnotifications",
    );
  });

  test("분류 스위치는 바로 바뀌고 실패하면 되돌아가며 거래 진행은 잠겨 있다", async ({ page }) => {
    await seedSession(page);
    let state: Category[] = INITIAL.map((category) => ({ ...category }));
    const putBodies: unknown[] = [];
    let failNextPut = false;

    await page.route(PREFERENCES_PATH, async (route) => {
      const request = route.request();
      if (request.method() === "GET") {
        await route.fulfill({ json: { categories: state } });
        return;
      }
      const body = request.postDataJSON() as { enabled: Partial<Record<CategoryKey, boolean>> };
      putBodies.push(body);
      if (failNextPut) {
        failNextPut = false;
        await route.fulfill({
          status: 500,
          json: { code: "INTERNAL", message: "잠시 후 다시 시도해 주세요." },
        });
        return;
      }
      state = state.map((category) => {
        const next = body.enabled[category.key];
        return next === undefined ? category : { ...category, enabled: next };
      });
      await route.fulfill({ json: { categories: state } });
    });

    await page.goto("/profile/notifications");

    const trade = page.getByRole("switch", { name: "거래 진행" });
    await expect(trade).toBeChecked();
    await expect(trade).toBeDisabled();
    await expect(page.getByText("돈과 물건이 오가는 진행 알림이라 끌 수 없어요.")).toBeVisible();

    const offer = page.getByRole("switch", { name: "가격 제안·입찰" });
    await expect(offer).toBeChecked();
    await offer.click();
    await expect(offer).not.toBeChecked();
    await expect.poll(() => putBodies).toEqual([{ enabled: { offer: false } }]);

    const community = page.getByRole("switch", { name: "커뮤니티·부품 요청" });
    await expect(community).not.toBeChecked();
    failNextPut = true;
    await community.click();
    await expect(
      page.getByRole("alert").filter({ hasText: "커뮤니티·부품 요청 설정을 저장하지 못했어요" }),
    ).toBeVisible();
    await expect(community).not.toBeChecked();
    expect(putBodies).toHaveLength(2);
    expect(putBodies[1]).toEqual({ enabled: { community: true } });

    await page.reload();
    await expect(page.getByRole("switch", { name: "가격 제안·입찰" })).not.toBeChecked();
  });

  test("알림함 머리에서 설정 화면으로 들어간다", async ({ page }) => {
    await seedSession(page);
    await page.route(`**/api/v1/users/${ACCOUNT_ID}/notifications`, (route) =>
      route.fulfill({ json: [] }),
    );
    await page.goto("/notifications");
    await expect(page.getByRole("link", { name: "설정", exact: true })).toHaveAttribute(
      "href",
      "/profile/notifications",
    );
  });
});
