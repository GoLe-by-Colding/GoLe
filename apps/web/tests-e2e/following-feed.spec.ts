import { expect, test } from "@playwright/test";

test.describe("Following feed", () => {
  test.beforeEach(async ({ page }) => {
    // (main) 레이아웃의 OnboardingBanner가 마운트되자마자 상태를 조회한다. 이 스펙은
    // HttpOnly 쿠키 없는 합성 세션을 쓰므로, 목킹하지 않으면 실제 백엔드 401이 그
    // 세션을 지운다.
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
  });

  test("비로그인 사용자는 원래 피드로 돌아오는 로그인 동선을 본다", async ({ page }) => {
    await page.goto("/feed");

    await expect(page.getByRole("heading", { name: "팔로잉 피드", exact: true })).toBeVisible();
    await expect(page.getByRole("link", { name: "로그인하고 피드 보기" })).toHaveAttribute(
      "href",
      "/login?returnTo=%2Ffeed",
    );
  });

  test("팔로우한 판매자의 새 매물과 빌더의 새 글을 한곳에서 이어본다", async ({ page }) => {
    await page.route("**/api/v1/accounts/me", (route) =>
      route.fulfill({
        json: { accountId: "user-feed", email: "feed@gole.test", role: "USER" },
      }),
    );
    await page.route("**/api/v1/users/user-feed/notifications/unread-count", (route) =>
      route.fulfill({ json: { unreadCount: 0 } }),
    );
    await page.route("**/api/v1/users/user-feed/following", (route) =>
      route.fulfill({ json: ["seller-brick"] }),
    );
    await page.route("**/api/v1/users/user-feed/feed?limit=36", (route) =>
      route.fulfill({
        json: [
          {
            id: "listing-followed",
            sellerId: "seller-brick",
            title: "팔로우한 판매자의 새 우주선",
            price: 129000,
            condition: "used_good",
            catalogSetNumber: "10497",
            category: "set",
            status: "active",
            photoUrls: ["/api/v1/media/catalog/10497.svg"],
            createdAt: "2026-09-03T00:00:00Z",
          },
        ],
      }),
    );
    await page.route("**/api/v1/community/feed/following?limit=100", (route) =>
      route.fulfill({
        json: [
          {
            id: "post-followed",
            authorId: "seller-brick",
            content: "오늘 완성한 우주선 MOC를 소개해요",
            imageUrls: [],
            type: "moc",
            likeCount: 4,
            createdAt: "2026-09-03T01:00:00Z",
          },
        ],
      }),
    );
    await page.route("**/api/v1/listings", (route) => route.fulfill({ json: [] }));
    await page.route("**/api/v1/community/posts?limit=6", (route) => route.fulfill({ json: [] }));
    await page.addInitScript(() => {
      window.localStorage.setItem(
        "gole.session",
        JSON.stringify({ accountId: "user-feed", sessionToken: "", role: "USER" }),
      );
    });

    await page.goto("/feed");

    await expect(page.getByRole("heading", { name: "팔로잉 피드", exact: true })).toBeVisible();
    await expect(page.getByText("1명", { exact: true })).toBeVisible();
    await expect(page.getByText("팔로우한 판매자의 새 우주선")).toBeVisible();
    await expect(page.getByText("오늘 완성한 우주선 MOC를 소개해요")).toBeVisible();
    await expect(page.getByRole("link", { name: "대화", exact: true }).first()).toHaveAttribute(
      "href",
      "/listings/listing-followed?chat=1",
    );

    await page.getByRole("tab", { name: "새 이야기" }).click();
    await expect(page.getByText("오늘 완성한 우주선 MOC를 소개해요")).toBeVisible();
    await expect(page.getByText("팔로우한 판매자의 새 우주선")).toHaveCount(0);
  });

  test("첫 팔로우 전에도 최근 매물과 이야기를 발견할 수 있다", async ({ page }) => {
    await page.route("**/api/v1/accounts/me", (route) =>
      route.fulfill({
        json: { accountId: "user-new", email: "new@gole.test", role: "USER" },
      }),
    );
    await page.route("**/api/v1/users/user-new/notifications/unread-count", (route) =>
      route.fulfill({ json: { unreadCount: 0 } }),
    );
    await page.route("**/api/v1/users/user-new/following", (route) => route.fulfill({ json: [] }));
    await page.route("**/api/v1/users/user-new/feed?limit=36", (route) =>
      route.fulfill({ json: [] }),
    );
    await page.route("**/api/v1/community/feed/following?limit=100", (route) =>
      route.fulfill({ json: [] }),
    );
    await page.route("**/api/v1/listings", (route) =>
      route.fulfill({
        json: [
          {
            id: "listing-suggested",
            sellerId: "seller-ocean",
            title: "처음 발견한 심해 탐사선",
            price: 88000,
            condition: "like_new",
            completeness: "full_box",
            hasBox: true,
            hasManual: true,
            hasMissingParts: false,
            missingPartsNote: "",
            defectsNote: "",
            description: "",
            catalogSetNumber: "60379",
            category: "set",
            status: "active",
            photoUrls: [],
            createdAt: "2026-09-03T00:00:00Z",
          },
        ],
      }),
    );
    await page.route("**/api/v1/community/posts?limit=6", (route) =>
      route.fulfill({
        json: [
          {
            id: "post-suggested",
            authorId: "builder-wave",
            content: "바닷속 기지를 브릭으로 완성했어요",
            imageUrls: [],
            type: "showcase",
            likeCount: 7,
            likedByViewer: false,
            createdAt: "2026-09-03T01:00:00Z",
          },
        ],
      }),
    );
    await page.addInitScript(() => {
      window.localStorage.setItem(
        "gole.session",
        JSON.stringify({ accountId: "user-new", sessionToken: "", role: "USER" }),
      );
    });

    await page.goto("/feed");

    await expect(page.getByText(/아직 팔로우 소식이 비어 있어/)).toBeVisible();
    await expect(page.getByRole("heading", { name: "먼저 만나볼 사람들" })).toBeVisible();
    await expect(page.getByRole("heading", { name: "처음 만나볼 새 매물" })).toBeVisible();
    await expect(page.getByText("처음 발견한 심해 탐사선")).toBeVisible();
    await expect(page.getByRole("heading", { name: "지금 둘러볼 이야기" })).toBeVisible();
    await expect(page.getByText("바닷속 기지를 브릭으로 완성했어요")).toBeVisible();
  });
});
