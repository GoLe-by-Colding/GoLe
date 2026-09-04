import { expect, test, type Page } from "@playwright/test";

const apiBaseUrl = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8090";
const externalBaseUrl = process.env.E2E_BASE_URL;
const targetsRemoteHost =
  externalBaseUrl !== undefined &&
  !["localhost", "127.0.0.1"].includes(new URL(externalBaseUrl).hostname);

const launchConfig = {
  stage: 0,
  tradeMode: "DIRECT_CHAT",
  features: { payments: false, reviews: false, partnerPayout: false },
  sellerIdentityVerificationReady: true,
  updatedAt: null,
};

async function seedSession(page: Page, accountId: string): Promise<void> {
  await page.addInitScript((id) => {
    window.localStorage.setItem(
      "gole.session",
      JSON.stringify({ accountId: id, sessionToken: "", role: "USER" }),
    );
  }, accountId);
  await page.route("**/api/v1/accounts/me", (route) =>
    route.fulfill({ json: { accountId, email: "e2e@gole.test", role: "USER" } }),
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
  await page.route(`**/api/v1/users/${accountId}/notifications/unread-count`, (route) =>
    route.fulfill({ json: { unreadCount: 0 } }),
  );
}

async function mockShop(page: Page, sellerId: string): Promise<void> {
  await page.route(`**/api/v1/shops/${sellerId}`, (route) => route.fulfill({ json: [] }));
  await page.route("**/api/v1/config/launch", (route) => route.fulfill({ json: launchConfig }));
}

test.describe("로그인 필요 액션", () => {
  test("보호된 화면 CTA는 로그인 뒤 원래 화면으로 돌아온다", async ({ page }) => {
    await page.route("**/api/v1/config/launch", (route) => route.fulfill({ json: launchConfig }));
    const cases = [
      ["/sell", "/login?returnTo=%2Fsell"],
      ["/community/new", "/login?returnTo=%2Fcommunity%2Fnew"],
      ["/notifications", "/login?returnTo=%2Fnotifications"],
    ] as const;

    for (const [path, expectedHref] of cases) {
      await page.goto(path);
      await expect(page.getByRole("link", { name: "로그인하러 가기" })).toHaveAttribute(
        "href",
        expectedHref,
      );
    }
  });

  test("헤더 로그인도 현재 검색 조건을 보존한다", async ({ page }) => {
    await page.goto("/privacy?source=header");
    await page.getByRole("button", { name: "로그인", exact: true }).click();

    await expect(page).toHaveURL(`/login?returnTo=${encodeURIComponent("/privacy?source=header")}`);
  });
});

test.describe("동적 콘텐츠 경로", () => {
  test("삭제된 게시글에서 커뮤니티 목록으로 복구한다", async ({ page }) => {
    await page.route("**/api/v1/community/posts/missing-post", (route) =>
      route.fulfill({ status: 404, json: { code: "POST_NOT_FOUND", message: "not found" } }),
    );

    await page.goto("/community/missing-post");

    await expect(page.getByText("게시글을 찾을 수 없어요", { exact: true })).toBeVisible();
    await expect(page.getByRole("link", { name: "커뮤니티로 돌아가기" })).toHaveAttribute(
      "href",
      "/community",
    );
  });

  test("게시글과 댓글의 일시 오류를 같은 화면에서 다시 불러온다", async ({ page }) => {
    let postAvailable = false;
    let commentsAvailable = false;
    await page.route("**/api/v1/community/posts/retry-post", (route) => {
      return postAvailable
        ? route.fulfill({
            json: {
              id: "retry-post",
              authorId: "retry-builder",
              content: "다시 연결된 브릭 이야기",
              imageUrls: [],
              type: "tip",
              likeCount: 0,
              likedByViewer: false,
              createdAt: "2026-09-04T00:00:00Z",
            },
          })
        : route.fulfill({ status: 503, json: { code: "TEMPORARY", message: "temporary" } });
    });
    await page.route("**/api/v1/community/posts/retry-post/comments", (route) => {
      return commentsAvailable
        ? route.fulfill({
            json: [
              {
                id: "retry-comment",
                authorId: "comment-builder",
                content: "댓글도 다시 연결됐어요",
                createdAt: "2026-09-04T00:01:00Z",
              },
            ],
          })
        : route.fulfill({ status: 503, json: { code: "TEMPORARY", message: "temporary" } });
    });

    await page.goto("/community/retry-post");
    await expect(page.getByText("게시글을 불러오지 못했어요", { exact: true })).toBeVisible();
    postAvailable = true;
    await page.getByRole("button", { name: "다시 시도", exact: true }).click();
    await expect(page.getByText("다시 연결된 브릭 이야기", { exact: true })).toBeVisible();
    await expect(page.getByRole("button", { name: "댓글 다시 확인" })).toBeVisible();
    commentsAvailable = true;
    await page.getByRole("button", { name: "댓글 다시 확인" }).click();
    await expect(page.getByText("댓글도 다시 연결됐어요", { exact: true })).toBeVisible();
  });

  test("알림은 허용된 내부 경로만 링크로 만들고 빈 목록도 다음 행동을 제공한다", async ({
    page,
  }) => {
    const accountId = "notification-path-e2e";
    await seedSession(page, accountId);
    let readId: string | null = null;
    await page.route(`**/api/v1/users/${accountId}/notifications`, (route) =>
      route.fulfill({
        json: [
          {
            id: "safe-notification",
            type: "COMMENT",
            message: "내 글에 새 댓글이 달렸어요",
            link: "/community/post-1",
            read: false,
            createdAt: "2026-09-04T00:00:00Z",
          },
          {
            id: "unsafe-notification",
            type: "GENERAL",
            message: "잘못된 외부 링크",
            link: "https://example.invalid/phishing",
            read: false,
            createdAt: "2026-09-04T00:01:00Z",
          },
        ],
      }),
    );
    await page.route(`**/api/v1/users/${accountId}/notifications/*/read`, (route) => {
      readId = new URL(route.request().url()).pathname.split("/").at(-2) ?? null;
      return route.fulfill({ status: 204, body: "" });
    });

    await page.goto("/notifications");

    await expect(page.getByRole("link", { name: /내 글에 새 댓글/ })).toHaveAttribute(
      "href",
      "/community/post-1",
    );
    const unsafeRow = page.getByRole("listitem").filter({ hasText: "잘못된 외부 링크" });
    await expect(unsafeRow.getByRole("link")).toHaveCount(0);
    await unsafeRow.getByRole("button").click();
    expect(readId).toBe("unsafe-notification");
    await expect(unsafeRow.getByRole("button")).toHaveCount(0);

    await page.unroute(`**/api/v1/users/${accountId}/notifications`);
    await page.route(`**/api/v1/users/${accountId}/notifications`, (route) =>
      route.fulfill({ json: [] }),
    );
    await page.reload();
    await expect(page.getByRole("link", { name: "커뮤니티 둘러보기" })).toHaveAttribute(
      "href",
      "/community",
    );
  });
});

test.describe("위시리스트 연결", () => {
  test("저장 상태를 불러오고 실제 DELETE와 POST로 토글한다", async ({ page }) => {
    test.skip(targetsRemoteHost, "로컬 시드 매물과 요청 가로채기를 사용하는 회귀 테스트");

    const response = await page.request.get(`${apiBaseUrl}/api/v1/listings`);
    expect(response.ok()).toBeTruthy();
    const listings = (await response.json()) as Array<{
      id: string;
      catalogSetNumber: string | null;
    }>;
    const listing = listings.find((item) => item.catalogSetNumber !== null);
    expect(listing, "카탈로그 세트 번호가 있는 시드 매물이 필요합니다").toBeDefined();

    const accountId = "wishlist-e2e";
    const targetId = listing!.catalogSetNumber!;
    let saved = true;
    let deleteCount = 0;
    let postCount = 0;
    await seedSession(page, accountId);
    await page.route(`**/api/v1/users/${accountId}/wishlist**`, async (route) => {
      const method = route.request().method();
      if (method === "GET") {
        await route.fulfill({
          json: saved ? [{ targetType: "catalog_set", targetId }] : [],
        });
        return;
      }
      if (method === "DELETE") {
        const url = new URL(route.request().url());
        expect(url.searchParams.get("targetType")).toBe("CATALOG_SET");
        expect(url.searchParams.get("targetId")).toBe(targetId);
        saved = false;
        deleteCount += 1;
        await route.fulfill({ status: 204, body: "" });
        return;
      }
      const body = route.request().postDataJSON() as {
        targetType: string;
        targetId: string;
      };
      expect(body).toEqual({ targetType: "CATALOG_SET", targetId });
      saved = true;
      postCount += 1;
      await route.fulfill({ status: 204, body: "" });
    });

    await page.goto(`/listings/${listing!.id}`);
    await page.getByRole("button", { name: "위시 빼기" }).click();
    await expect(page.getByRole("button", { name: "위시 담기" })).toBeEnabled();
    expect(deleteCount).toBe(1);

    await page.getByRole("button", { name: "위시 담기" }).click();
    await expect(page.getByRole("button", { name: "위시 빼기" })).toBeEnabled();
    expect(postCount).toBe(1);
  });
});

test.describe("판매자 팔로우", () => {
  test("자기 판매자 화면에서는 팔로우 액션을 노출하지 않는다", async ({ page }) => {
    const accountId = "seller-self-e2e";
    await seedSession(page, accountId);
    await mockShop(page, accountId);

    await page.goto(`/shops/${accountId}`);

    await expect(page.getByRole("heading", { name: /님의 샵/ })).toBeVisible();
    await expect(page.getByRole("button", { name: /팔로우|팔로잉/ })).toHaveCount(0);
  });

  test("팔로우 조회와 변경 실패를 표시하고 다시 확인할 수 있다", async ({ page }) => {
    const accountId = "viewer-follow-e2e";
    const sellerId = "seller-follow-e2e";
    let followingReads = 0;
    await seedSession(page, accountId);
    await mockShop(page, sellerId);
    await page.route(`**/api/v1/users/${accountId}/following`, (route) => {
      if (route.request().method() === "POST") {
        return route.fulfill({ status: 503, json: { code: "TEMPORARY", message: "temporary" } });
      }
      followingReads += 1;
      return followingReads === 1
        ? route.fulfill({ status: 503, json: { code: "TEMPORARY", message: "temporary" } })
        : route.fulfill({ json: [] });
    });

    await page.goto(`/shops/${sellerId}`);
    await expect(page.getByText("팔로우 상태를 확인하지 못했습니다.")).toBeVisible();

    await page.getByRole("button", { name: "다시 확인" }).click();
    await expect(page.getByRole("button", { name: "팔로우", exact: true })).toBeEnabled();

    await page.getByRole("button", { name: "팔로우", exact: true }).click();
    await expect(page.getByText("팔로우를 변경하지 못했습니다.")).toBeVisible();
  });
});
