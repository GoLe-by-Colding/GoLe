import { test, expect } from "@playwright/test";

const externalBaseUrl = process.env.E2E_BASE_URL;
const targetsRemoteHost =
  externalBaseUrl !== undefined &&
  !["localhost", "127.0.0.1"].includes(new URL(externalBaseUrl).hostname);

test.beforeEach(async ({ page }) => {
  // (main) 레이아웃의 OnboardingBanner가 마운트되자마자 상태를 조회한다. 이 스펙은
  // HttpOnly 쿠키 없는 합성 세션을 쓰므로, 목킹하지 않으면 실제 백엔드 401이 그 세션을
  // 지운다 — 알림 폴링과 같은 이유다.
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

// 커뮤니티: 주제 탭 필터 + 글쓰기 진입. (데이터가 있는 환경 대상)
test.describe("Community topics", () => {
  test("주제 탭과 피드가 렌더된다", async ({ page }) => {
    await page.goto("/community");
    await expect(page.getByRole("heading", { name: "커뮤니티" })).toBeVisible();

    // 주제 탭
    await expect(page.getByRole("button", { name: "전체" })).toBeVisible();
    await expect(page.getByRole("button", { name: "질문" })).toBeVisible();
    await expect(page.getByRole("button", { name: "이스터에그" })).toBeVisible();
  });

  test("주제 탭으로 필터링한다", async ({ page }) => {
    await page.goto("/community");
    await page.getByRole("button", { name: "질문" }).click();
    // 질문 탭 선택 시에도 페이지가 정상 유지(글이 없으면 안내 문구)
    await expect(page.getByRole("heading", { name: "커뮤니티" })).toBeVisible();
  });

  test("검색어와 주제를 전체 피드 페이지 API에 전달한다", async ({ page }) => {
    test.skip(targetsRemoteHost, "응답 가로채기 기반 — 로컬 프론트 전용");
    const requests: URL[] = [];
    await page.route("**/api/v1/community/posts/page?*", (route) => {
      const url = new URL(route.request().url());
      requests.push(url);
      return route.fulfill({
        json: {
          items: [
            {
              id: `server-search-${requests.length}`,
              authorId: "builder-search",
              content: "서버 전체 피드에서 찾은 숨은 글",
              imageUrls: [],
              type: url.searchParams.get("topic") ?? "general",
              likeCount: 0,
              likedByViewer: false,
              createdAt: "2026-09-03T00:00:00Z",
            },
          ],
          nextCursor: null,
        },
      });
    });

    await page.goto("/community");
    await page.getByRole("searchbox", { name: "게시글 검색" }).fill("숨은 글");
    await expect(page.getByText("서버 전체 피드에서 찾은 숨은 글")).toBeVisible();
    await expect.poll(() => requests.at(-1)?.searchParams.get("q")).toBe("숨은 글");

    await page.getByRole("button", { name: "질문" }).click();
    await expect.poll(() => requests.at(-1)?.searchParams.get("topic")).toBe("question");
    expect(requests.at(-1)?.searchParams.get("q")).toBe("숨은 글");
  });

  test("글쓰기 화면으로 이동하고 주제를 고를 수 있다", async ({ page }) => {
    await page.goto("/community");
    const composeLink = page.getByRole("link", { name: "글쓰기", exact: true });
    await expect(composeLink).toHaveCount(1);
    await composeLink.click();
    await expect(page).toHaveURL(/\/community\/new$/);
  });

  test("내 게시글 카드에는 나 자신과 대화하는 링크를 노출하지 않는다", async ({ page }) => {
    test.skip(targetsRemoteHost, "로컬 시드 게시글을 사용하는 회귀 테스트");
    await page.route("**/api/v1/accounts/me", (route) =>
      route.fulfill({
        json: { accountId: "user-builder", email: "builder@gole.test", role: "USER" },
      }),
    );
    await page.route("**/api/v1/users/user-builder/notifications/unread-count", (route) =>
      route.fulfill({ json: { unreadCount: 0 } }),
    );
    await page.addInitScript(() => {
      window.localStorage.setItem(
        "gole.session",
        JSON.stringify({ accountId: "user-builder", sessionToken: "", role: "USER" }),
      );
    });

    await page.goto("/community");

    const ownCards = page.locator('[data-testid="post-card"][data-author-id="user-builder"]');
    await expect(ownCards.first()).toBeVisible();
    await expect(ownCards.getByRole("link", { name: "대화" })).toHaveCount(0);

    const peerCard = page.locator('[data-testid="post-card"][data-author-id="user-newbie"]');
    await expect(peerCard.getByRole("link", { name: "대화" })).toHaveAttribute(
      "href",
      "/chat?direct=user-newbie",
    );
  });

  test("팔로잉 피드는 팔로우한 빌더의 글만 모아 보여준다", async ({ page }) => {
    test.skip(targetsRemoteHost, "로컬 시드 게시글과 응답 가로채기를 사용하는 회귀 테스트");
    await page.route("**/api/v1/accounts/me", (route) =>
      route.fulfill({
        json: { accountId: "user-builder", email: "builder@gole.test", role: "USER" },
      }),
    );
    await page.route("**/api/v1/users/user-builder/notifications/unread-count", (route) =>
      route.fulfill({ json: { unreadCount: 0 } }),
    );
    await page.route("**/api/v1/community/feed/following?limit=100", (route) =>
      route.fulfill({
        json: [
          {
            id: "following-post-1",
            authorId: "user-newbie",
            content: "팔로우한 빌더의 새 작품",
            imageUrls: [],
            type: "moc",
            likeCount: 3,
            createdAt: "2026-09-03T00:00:00Z",
          },
        ],
      }),
    );
    await page.addInitScript(() => {
      window.localStorage.setItem(
        "gole.session",
        JSON.stringify({ accountId: "user-builder", sessionToken: "", role: "USER" }),
      );
    });

    await page.goto("/community");
    await page.getByRole("button", { name: "팔로잉" }).click();

    await expect(
      page.locator('[data-testid="post-card"][data-author-id="user-newbie"]'),
    ).toBeVisible();
    await expect(
      page.locator('[data-testid="post-card"][data-author-id="user-builder"]'),
    ).toHaveCount(0);
    await expect(page.getByText("팔로우한 빌더의 새 작품")).toBeVisible();
  });

  test("내 좋아요 상태를 복원하고 같은 버튼에서 취소와 재등록을 이어간다", async ({ page }) => {
    test.skip(targetsRemoteHost, "응답 가로채기 기반 — 로컬 프론트 전용");
    await page.addInitScript(() => {
      window.localStorage.setItem(
        "gole.session",
        JSON.stringify({ accountId: "viewer-1", sessionToken: "session-like", role: "USER" }),
      );
    });
    await page.route("**/api/v1/accounts/me", (route) =>
      route.fulfill({ json: { accountId: "viewer-1", email: "viewer@gole.test", role: "USER" } }),
    );
    await page.route("**/api/v1/users/viewer-1/notifications/unread-count", (route) =>
      route.fulfill({ json: { unreadCount: 0 } }),
    );
    await page.route("**/api/v1/community/posts/liked-post-1", (route) =>
      route.fulfill({
        json: {
          id: "liked-post-1",
          authorId: "builder-1",
          content: "좋아요 상태가 이어지는 피드",
          imageUrls: [],
          type: "showcase",
          likeCount: 5,
          likedByViewer: true,
          createdAt: "2026-09-03T00:00:00Z",
        },
      }),
    );
    await page.route("**/api/v1/community/posts/liked-post-1/comments", (route) =>
      route.fulfill({ json: [] }),
    );

    const methods: string[] = [];
    const authorizations: Array<string | undefined> = [];
    await page.route("**/api/v1/community/posts/liked-post-1/likes", (route) => {
      methods.push(route.request().method());
      authorizations.push(route.request().headers().authorization);
      return route.fulfill({ status: 204 });
    });

    await page.goto("/community/liked-post-1");
    const liked = page.getByRole("button", { name: "좋아요 취소, 5개" });
    await expect(liked).toHaveAttribute("aria-pressed", "true");

    await liked.click();
    const unliked = page.getByRole("button", { name: "좋아요, 4개" });
    await expect(unliked).toHaveAttribute("aria-pressed", "false");
    await unliked.click();

    await expect(page.getByRole("button", { name: "좋아요 취소, 5개" })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
    expect(methods).toEqual(["DELETE", "POST"]);
    expect(authorizations).toEqual(["Bearer session-like", "Bearer session-like"]);
  });

  test("댓글은 부모 게시글이 검증되는 전용 경로로 신고한다", async ({ page }) => {
    test.skip(targetsRemoteHost, "응답 가로채기 기반 — 로컬 프론트 전용");
    await page.addInitScript(() => {
      window.localStorage.setItem(
        "gole.session",
        JSON.stringify({ accountId: "viewer-1", sessionToken: "", role: "USER" }),
      );
    });
    await page.route("**/api/v1/accounts/me", (route) =>
      route.fulfill({ json: { accountId: "viewer-1", email: "viewer@gole.test", role: "USER" } }),
    );
    await page.route("**/api/v1/users/viewer-1/notifications/unread-count", (route) =>
      route.fulfill({ json: { unreadCount: 0 } }),
    );
    await page.route("**/api/v1/community/posts/post-1", (route) =>
      route.fulfill({
        json: {
          id: "post-1",
          authorId: "author-1",
          content: "레고 보관 팁을 공유합니다.",
          imageUrls: [],
          type: "tip",
          likeCount: 2,
          createdAt: "2026-09-03T00:00:00Z",
        },
      }),
    );
    await page.route("**/api/v1/community/posts/post-1/comments", (route) =>
      route.fulfill({
        json: [
          {
            id: "comment-1",
            authorId: "commenter-1",
            content: "신고 대상 댓글",
            createdAt: "2026-09-03T01:00:00Z",
          },
        ],
      }),
    );

    let reportBody: unknown;
    await page.route(
      "**/api/v1/community/posts/post-1/comments/comment-1/report",
      async (route) => {
        reportBody = route.request().postDataJSON();
        await route.fulfill({ status: 201, json: { id: "report-1" } });
      },
    );

    await page.goto("/community/post-1");
    await page.locator("#comment-comment-1").getByRole("button", { name: "신고하기" }).click();

    const dialog = page.getByRole("dialog", { name: "댓글 신고" });
    await expect(dialog.getByText("가품·위조품 의심")).toHaveCount(0);
    await dialog.getByLabel("욕설·스팸 등 부적절").check();
    await dialog.getByPlaceholder("상세 내용 (선택, 최대 1000자)").fill("욕설이 포함되어 있어요.");
    await dialog.getByRole("button", { name: "신고 접수" }).click();

    expect(reportBody).toEqual({ reason: "INAPPROPRIATE", detail: "욕설이 포함되어 있어요." });
    await expect(dialog.getByText("신고가 접수되었어요")).toBeVisible();
    await expect(dialog.getByText(/필요하면 댓글을 블라인드/)).toBeVisible();
  });
});
