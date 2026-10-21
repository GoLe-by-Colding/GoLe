import { expect, test, type Page } from "@playwright/test";
import { E2E_SELLER, signInAs } from "./support/e2e-session";

const externalBaseUrl = process.env.E2E_BASE_URL;
const apiBaseUrl = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";
const targetsRemoteHost =
  externalBaseUrl !== undefined &&
  !["localhost", "127.0.0.1"].includes(new URL(externalBaseUrl).hostname);
const VIEWER = "community-flow-viewer";
const IMAGE_KEYS = ["community-flow-first.png", "community-flow-second.png"];
const IMAGE_URLS = IMAGE_KEYS.map((key) => `/api/v1/media/${key}`);
const PNG = Buffer.from(
  "iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAEUlEQVR4XmPQqLizBYQZYAwASsIIwRFEXsMAAAAASUVORK5CYII=",
  "base64",
);

function postFixture(id: string, content = "사진 두 장을 올린 조립 기록") {
  return {
    id,
    authorId: VIEWER,
    content,
    imageKeys: IMAGE_KEYS,
    imageUrls: IMAGE_URLS,
    type: "general",
    likeCount: 0,
    likedByViewer: false,
    createdAt: "2026-10-21T00:00:00Z",
  };
}

async function existingPostId(page: Page): Promise<string> {
  // 상세의 Proxy·서버 메타데이터는 브라우저 가로채기 밖이므로 실제 공개 ID로 진입한다.
  const response = await page.request.get(`${apiBaseUrl}/api/v1/community/posts?limit=1`);
  expect(response.ok(), "SSR에 사용할 공개 시드 게시글 조회").toBeTruthy();
  const posts = (await response.json()) as Array<{ id: string }>;
  expect(posts.length, "공개 시드 게시글이 필요합니다").toBeGreaterThan(0);
  return posts[0]!.id;
}

async function mockViewer(page: Page): Promise<void> {
  await page.addInitScript((accountId) => {
    window.localStorage.setItem(
      "gole.session",
      JSON.stringify({ accountId, sessionToken: "", role: "USER" }),
    );
  }, VIEWER);
  await page.route("**/api/v1/accounts/me", (route) =>
    route.fulfill({ json: { accountId: VIEWER, email: "flow@gole.test", role: "USER" } }),
  );
  await page.route("**/api/v1/accounts/me/onboarding", (route) =>
    route.fulfill({ json: { required: false, legacyExempt: true, phoneCompleted: true } }),
  );
  await page.route(`**/api/v1/users/${VIEWER}/notifications/unread-count`, (route) =>
    route.fulfill({ json: { unreadCount: 0 } }),
  );
  await page.route("**/api/v1/media/community-flow-*.png", (route) =>
    route.fulfill({ contentType: "image/png", body: PNG }),
  );
}

test.describe("커뮤니티 흐름 — 응답 가로채기 회귀", () => {
  test.skip(targetsRemoteHost, "로컬 프론트 전용 합성 응답 검증");

  test.beforeEach(async ({ page }) => {
    await mockViewer(page);
  });

  test("사진을 두 장 올린 글의 상세에서 모든 사진을 순서대로 읽는다", async ({ page }) => {
    const postId = await existingPostId(page);
    await page.route(`**/api/v1/community/posts/${postId}`, (route) =>
      route.fulfill({ json: postFixture(postId) }),
    );
    await page.route(`**/api/v1/community/posts/${postId}/comments`, (route) =>
      route.fulfill({ json: [] }),
    );

    await page.goto(`/community/${postId}`);
    const photos = page.getByRole("list", { name: "게시글 사진" }).getByRole("img");
    await expect(photos).toHaveCount(2);
    await expect(photos.nth(0)).toHaveAttribute("src", IMAGE_URLS[0]!);
    await expect(photos.nth(1)).toHaveAttribute("src", IMAGE_URLS[1]!);
    await expect(photos.nth(1)).toHaveAttribute("alt", "게시글 사진 2 / 2");
  });

  test("2000자를 넘는 본문을 수정하고 기존 사진 키를 보존한다", async ({ page }) => {
    const postId = await existingPostId(page);
    let post = postFixture(postId, "조립".repeat(1200));
    let editBody: unknown;
    await page.route(`**/api/v1/community/posts/${postId}`, (route) => {
      if (route.request().method() === "PUT") {
        const body = route.request().postDataJSON() as { content: string };
        editBody = body;
        post = { ...post, content: body.content };
      }
      return route.fulfill({ json: post });
    });
    await page.route(`**/api/v1/community/posts/${postId}/comments`, (route) =>
      route.fulfill({ json: [] }),
    );

    await page.goto(`/community/${postId}`);
    await page.getByRole("button", { name: "수정", exact: true }).click();
    const content = `${"수정".repeat(1300)} 완료`;
    const editor = page.locator("textarea");
    await editor.fill(content);
    await expect(editor).toHaveValue(content);
    await page.getByRole("button", { name: "저장", exact: true }).click();
    await expect(editor).toHaveCount(0);
    expect(editBody).toEqual({ requesterId: VIEWER, content, imageKeys: IMAGE_KEYS });
    await expect(page.getByText(content, { exact: true })).toBeVisible();
  });

  test("공백 본문은 전송하지 않고 5000자 본문을 게시한다", async ({ page }) => {
    const postId = await existingPostId(page);
    const submissions: Array<{ content: string; topic: string }> = [];
    await page.route("**/api/v1/community/posts", (route) => {
      const body = route.request().postDataJSON() as { content: string; topic: string };
      submissions.push(body);
      return route.fulfill({ status: 201, json: postFixture(postId, body.content) });
    });
    await page.route(`**/api/v1/community/posts/${postId}`, (route) =>
      route.fulfill({ json: postFixture(postId, submissions.at(-1)?.content) }),
    );
    await page.route(`**/api/v1/community/posts/${postId}/comments`, (route) =>
      route.fulfill({ json: [] }),
    );

    await page.goto("/community/new");
    await page.getByLabel("내용", { exact: true }).fill("   ");
    await page.getByRole("button", { name: "게시하기" }).click();
    await expect(
      page.getByRole("alert").filter({ hasText: "내용을 입력해 주세요." }),
    ).toBeVisible();
    expect(submissions).toHaveLength(0);
    const content = "조립".repeat(2500);
    await page.getByLabel("내용", { exact: true }).fill(content);
    await page.getByLabel("주제", { exact: true }).selectOption("tip");
    await page.getByRole("button", { name: "게시하기" }).click();
    await expect(page).toHaveURL(new RegExp(`/community/${postId}$`));
    expect(submissions).toHaveLength(1);
    expect(submissions[0]).toMatchObject({ content, topic: "tip" });
  });

  test("느린 댓글 저장은 한 번만 전송하고 실패 후 재시도 결과를 즉시 표시한다", async ({
    page,
  }) => {
    const postId = await existingPostId(page);
    let postCount = 0;
    let fail = true;
    let release: () => void = () => undefined;
    const responseGate = new Promise<void>((resolve) => {
      release = resolve;
    });
    await page.route(`**/api/v1/community/posts/${postId}`, (route) =>
      route.fulfill({ json: postFixture(postId) }),
    );
    await page.route(`**/api/v1/community/posts/${postId}/comments`, async (route) => {
      if (route.request().method() === "GET") {
        // 등록 후 후속 조회가 실패해도 생성 응답으로 댓글을 표시해야 한다.
        return postCount === 0
          ? route.fulfill({ json: [] })
          : route.fulfill({ status: 503, json: { code: "UNAVAILABLE", message: "조회 지연" } });
      }
      postCount += 1;
      await responseGate;
      return fail
        ? route.fulfill({ status: 503, json: { code: "UNAVAILABLE", message: "등록 지연" } })
        : route.fulfill({
            status: 201,
            json: {
              id: "saved-comment",
              authorId: VIEWER,
              content: "완성한 작품이 멋져요",
              createdAt: "2026-10-21T00:01:00Z",
            },
          });
    });

    await page.goto(`/community/${postId}`);
    const input = page.getByPlaceholder("댓글을 입력하세요");
    await input.fill("완성한 작품이 멋져요");
    const form = page.locator("form").filter({ has: input });
    try {
      // 같은 이벤트 루프의 연속 제출도 React 재렌더 전에 잠가야 한다.
      await form.evaluate((element) => {
        const target = element as HTMLFormElement;
        target.requestSubmit();
        target.requestSubmit();
      });
      await expect(input).toBeDisabled();
      await expect.poll(() => postCount).toBe(1);
    } finally {
      release();
    }
    await expect(page.getByText("등록 지연", { exact: true })).toBeVisible();
    await expect(input).toBeEnabled();
    await expect(input).toHaveValue("완성한 작품이 멋져요");

    fail = false;
    await page.getByRole("button", { name: "등록", exact: true }).click();
    await expect(page.locator("#comment-saved-comment")).toContainText("완성한 작품이 멋져요");
    await expect(input).toHaveValue("");
    expect(postCount).toBe(2);
  });
});

test.describe("커뮤니티 흐름 — 실제 격리 API", () => {
  test.skip(
    process.env.E2E_WITH_BACKEND !== "1" || targetsRemoteHost,
    "격리 로컬 API와 시드 세션이 필요한 쓰기 검증",
  );

  test("사진 글 작성부터 본문 수정·댓글 등록·삭제까지 저장 결과가 이어진다", async ({ page }) => {
    const headers = { Authorization: `Bearer ${E2E_SELLER.sessionToken}` };
    let createdId: string | undefined;
    const content = `E2E 커뮤니티 흐름 ${Date.now()}`;
    try {
      await signInAs(page, E2E_SELLER);
      await page.goto("/community/new");
      await page.getByLabel("내용", { exact: true }).fill(content);
      await page.getByLabel("주제", { exact: true }).selectOption("tip");
      await page.getByLabel("이미지 (선택)", { exact: true }).setInputFiles([
        { name: "first.png", mimeType: "image/png", buffer: PNG },
        { name: "second.png", mimeType: "image/png", buffer: PNG },
      ]);
      await expect(page.getByRole("button", { name: "이미지 2 삭제" })).toBeVisible();
      const published = page.waitForResponse(
        (response) =>
          response.url() === `${apiBaseUrl}/api/v1/community/posts` &&
          response.request().method() === "POST",
      );
      await page.getByRole("button", { name: "게시하기" }).click();
      const response = await published;
      expect(response.status()).toBe(201);
      const created = (await response.json()) as { id: string; imageKeys: string[] };
      createdId = created.id;
      await expect(page).toHaveURL(new RegExp(`/community/${created.id}$`));
      await expect(page.getByRole("list", { name: "게시글 사진" }).getByRole("img")).toHaveCount(2);

      await page.getByRole("button", { name: "수정", exact: true }).click();
      const edited = `${content}\n${"수정한 조립 기록 ".repeat(250)}`;
      await page.getByLabel("게시글 내용 수정").fill(edited);
      await page.getByRole("button", { name: "저장", exact: true }).click();
      await expect(page.getByLabel("게시글 내용 수정")).toHaveCount(0);
      const savedResponse = await page.request.get(
        `${apiBaseUrl}/api/v1/community/posts/${created.id}`,
        { headers },
      );
      expect(savedResponse.ok()).toBeTruthy();
      expect(await savedResponse.json()).toMatchObject({
        content: edited.trim(),
        imageKeys: created.imageKeys,
        type: "tip",
      });

      await page.getByPlaceholder("댓글을 입력하세요").fill("실제 API에 저장한 댓글");
      await page.getByRole("button", { name: "등록", exact: true }).click();
      const comment = page
        .locator('li[id^="comment-"]')
        .filter({ hasText: "실제 API에 저장한 댓글" });
      await expect(comment).toBeVisible();
      await page.reload();
      await expect(comment).toBeVisible();
      await comment.getByRole("button", { name: "삭제", exact: true }).click();
      await page
        .getByRole("alertdialog", { name: "댓글 삭제" })
        .getByRole("button", { name: "삭제", exact: true })
        .click();
      await expect(comment).toHaveCount(0);
      const comments = await page.request.get(
        `${apiBaseUrl}/api/v1/community/posts/${created.id}/comments`,
        { headers },
      );
      expect(comments.ok()).toBeTruthy();
      expect(await comments.json()).toEqual([]);

      await page.getByRole("button", { name: "삭제", exact: true }).click();
      await page
        .getByRole("alertdialog", { name: "게시글 삭제" })
        .getByRole("button", { name: "삭제", exact: true })
        .click();
      await expect(page).toHaveURL(/\/community$/);
      const deleted = await page.request.get(`${apiBaseUrl}/api/v1/community/posts/${created.id}`, {
        headers,
      });
      expect(deleted.status()).toBe(404);
      createdId = undefined;
    } finally {
      if (createdId !== undefined) {
        const cleanup = await page.request.delete(
          `${apiBaseUrl}/api/v1/community/posts/${createdId}`,
          { headers },
        );
        expect([204, 404]).toContain(cleanup.status());
      }
    }
  });
});
