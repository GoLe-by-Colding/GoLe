import { test, expect, type Page } from "@playwright/test";

/**
 * 브라우저 기본 대화상자 대신 앱 공통 모달. (browser-dialogs R1·R2)
 *
 * 합성 세션과 API 응답(fixture)으로 취소 시 요청 0건, 확인 시 1건, 요청 ID 불일치 거부, Escape·초점 복귀를 본다.
 * 브라우저 기본 대화상자가 한 번이라도 뜨면 실패한다.
 */

const externalBaseUrl = process.env.E2E_BASE_URL;
const apiBaseUrl = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";
const targetsRemoteHost =
  externalBaseUrl !== undefined &&
  !/^https?:\/\/(localhost|127\.0\.0\.1)(:\d+)?/.test(externalBaseUrl);

/** 기본 대화상자가 뜨면 닫고 기록한다 — 테스트 끝에 0건이어야 한다. */
function recordNativeDialogs(page: Page): string[] {
  const messages: string[] = [];
  page.on("dialog", (dialog) => {
    messages.push(dialog.message());
    void dialog.dismiss();
  });
  return messages;
}

async function signInAs(page: Page, account: { accountId: string; role: "USER" | "ADMIN" }) {
  await page.addInitScript(
    (value) => {
      window.localStorage.setItem("gole.session", value);
    },
    JSON.stringify({
      ...account,
      email: `${account.accountId}@gole.test`,
      sessionToken: "test-token",
    }),
  );
  await page.route("**/api/v1/accounts/me", (route) =>
    route.fulfill({ json: { ...account, email: `${account.accountId}@gole.test` } }),
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
  await page.route(/\/api\/v1\/users\/[^/]+\/notifications\/unread-count(?:\?.*)?$/, (route) =>
    route.fulfill({ json: { unreadCount: 0 } }),
  );
  await page.route("**/api/admin/overview", (route) => route.fulfill({ json: { counts: {} } }));
}

const REQUEST_ID = "adr-7f3c2a10";

function deletionRow(holdReason: string | null) {
  return {
    requestId: REQUEST_ID,
    status: "READY",
    blockers: [],
    holdReason,
    requestedAt: "2026-10-10T00:00:00Z",
    updatedAt: "2026-10-10T00:00:00Z",
    completedAt: null,
    deletionCounts: {},
  };
}

test.describe("앱 공통 모달", () => {
  test("회원 탈퇴 보존 중지는 요청 ID가 정확히 같아야 하고, 사유는 허용 코드 중에서만 고른다", async ({
    page,
  }) => {
    const native = recordNativeDialogs(page);
    await signInAs(page, { accountId: "admin-1", role: "ADMIN" });
    let holdReason: string | null = null;
    const holds: unknown[] = [];
    const completes: string[] = [];
    await page.route("**/api/admin/account-deletion-requests**", (route) => {
      const url = new URL(route.request().url());
      if (url.pathname.endsWith("/hold")) {
        holds.push(route.request().postDataJSON());
        holdReason = (route.request().postDataJSON() as { reasonCode: string }).reasonCode;
        return route.fulfill({ json: deletionRow(holdReason) });
      }
      if (url.pathname.endsWith("/complete")) {
        completes.push(url.pathname);
        return route.fulfill({ json: deletionRow(holdReason) });
      }
      return route.fulfill({ json: [deletionRow(holdReason)] });
    });

    await page.goto("/admin/account-deletions");
    const hold = page.getByRole("button", { name: "보존 중지", exact: true });
    await hold.click();

    const idDialog = page.getByRole("dialog", { name: "보존 중지" });
    await expect(idDialog).toContainText(REQUEST_ID);
    const idInput = idDialog.getByRole("textbox", { name: "요청 ID" });
    await expect(idInput).toBeFocused();
    // 다른 값은 모달 안에서 거부되고 닫히지 않는다.
    await idInput.fill(`${REQUEST_ID}x`);
    await idInput.press("Enter");
    await expect(idDialog.getByRole("alert")).toHaveText("요청 ID가 일치하지 않습니다.");
    await expect(idDialog).toBeVisible();
    // Escape 는 취소다 — 요청 없이 닫히고 연 버튼으로 초점이 돌아온다.
    await page.keyboard.press("Escape");
    await expect(idDialog).toHaveCount(0);
    await expect(hold).toBeFocused();
    expect(holds).toEqual([]);

    // 요청 ID 확인 뒤 이어서 뜬 사유 모달을 Escape 로 닫아도 처음 연 버튼으로 초점이 돌아온다.
    await hold.click();
    await page.getByRole("dialog", { name: "보존 중지" }).getByRole("textbox").fill(REQUEST_ID);
    await page
      .getByRole("dialog", { name: "보존 중지" })
      .getByRole("button", { name: "다음" })
      .click();
    await expect(page.getByRole("dialog", { name: "보존 사유" })).toBeVisible();
    await page.keyboard.press("Escape");
    await expect(page.getByRole("dialog", { name: "보존 사유" })).toHaveCount(0);
    await expect(hold).toBeFocused();
    expect(holds).toEqual([]);

    await hold.click();
    await page.getByRole("dialog", { name: "보존 중지" }).getByRole("textbox").fill(REQUEST_ID);
    await page
      .getByRole("dialog", { name: "보존 중지" })
      .getByRole("button", { name: "다음" })
      .click();

    const reasonDialog = page.getByRole("dialog", { name: "보존 사유" });
    await expect(reasonDialog.getByRole("radio")).toHaveCount(3);
    const defaultReason = reasonDialog.getByRole("radio", { name: /DISPUTE_OR_CLAIM/ });
    await expect(defaultReason).toBeChecked();
    // 라디오 묶음은 Tab 한 칸이다. 첫 칸이 아닌 기본 선택에서도 Shift+Tab 은 모달 안에서 돈다.
    await expect(defaultReason).toBeFocused();
    await page.keyboard.press("Shift+Tab");
    await expect(reasonDialog.getByRole("button", { name: "보존 중지" })).toBeFocused();
    await page.keyboard.press("Tab");
    await expect(defaultReason).toBeFocused();
    await reasonDialog.getByRole("radio", { name: /LEGAL_OBLIGATION/ }).check();
    await reasonDialog.getByRole("button", { name: "보존 중지" }).click();

    await expect
      .poll(() => holds)
      .toEqual([{ confirmation: REQUEST_ID, reasonCode: "LEGAL_OBLIGATION" }]);
    await expect(page.getByRole("cell", { name: "LEGAL_OBLIGATION" })).toBeVisible();

    // 연계 파기는 요청 ID 확인 뒤 검토 문구 확인을 한 번 더 받는다. 마지막에 취소하면 요청이 없다.
    const complete = page.getByRole("button", { name: "연계 파기" });
    await complete.click();
    await page.getByRole("dialog", { name: "연계 파기" }).getByRole("textbox").fill(REQUEST_ID);
    await page
      .getByRole("dialog", { name: "연계 파기" })
      .getByRole("button", { name: "다음" })
      .click();
    const review = page.getByRole("alertdialog", { name: "연계 파기" });
    await expect(review).toContainText(
      "진행 거래·정산·분쟁·신고·문의·법정 보존과 공개 콘텐츠 수명주기를 모두 확인했습니까?",
    );
    await review.getByRole("button", { name: "취소" }).click();
    expect(completes).toEqual([]);

    await complete.click();
    await page.getByRole("dialog", { name: "연계 파기" }).getByRole("textbox").fill(REQUEST_ID);
    await page
      .getByRole("dialog", { name: "연계 파기" })
      .getByRole("button", { name: "다음" })
      .click();
    await page
      .getByRole("alertdialog", { name: "연계 파기" })
      .getByRole("button", { name: "파기" })
      .click();
    await expect.poll(() => completes).toHaveLength(1);
    expect(native).toEqual([]);
  });

  test("댓글·게시글 삭제는 취소하면 요청이 없고 확인하면 한 번만 보낸다", async ({ page }) => {
    test.skip(targetsRemoteHost, "응답 가로채기 기반 — 로컬 프론트 전용");
    const feedResponse = await page.request.get(`${apiBaseUrl}/api/v1/community/posts?limit=1`);
    expect(feedResponse.ok()).toBeTruthy();
    const posts = (await feedResponse.json()) as Array<{ readonly id: string }>;
    const postId = posts[0]?.id;
    expect(postId, "E2E 커뮤니티 시드 게시글이 필요합니다").toBeDefined();

    const native = recordNativeDialogs(page);
    await signInAs(page, { accountId: "author-1", role: "USER" });
    let postDeletes = 0;
    await page.route(`**/api/v1/community/posts/${postId}?*`, async (route) => {
      if (route.request().method() !== "DELETE") return route.fallback();
      postDeletes += 1;
      await route.fulfill({ status: 204, body: "" });
    });
    await page.route(`**/api/v1/community/posts/${postId}`, (route) =>
      route.fulfill({
        json: {
          id: postId,
          authorId: "author-1",
          content: "레고 보관 팁을 공유합니다.",
          imageUrls: [],
          type: "tip",
          likeCount: 0,
          createdAt: "2026-09-03T00:00:00Z",
        },
      }),
    );
    await page.route(`**/api/v1/community/posts/${postId}/comments`, (route) =>
      route.fulfill({
        json: [
          {
            id: "comment-1",
            authorId: "author-1",
            content: "내 댓글",
            createdAt: "2026-09-03T01:00:00Z",
          },
        ],
      }),
    );
    let commentDeletes = 0;
    await page.route(`**/api/v1/community/posts/${postId}/comments/comment-1`, async (route) => {
      commentDeletes += 1;
      await route.fulfill({ status: 204, body: "" });
    });

    await page.goto(`/community/${postId}`);
    const comment = page.locator("#comment-comment-1");
    await comment.getByRole("button", { name: "삭제" }).click();
    const confirmDialog = page.getByRole("alertdialog", { name: "댓글 삭제" });
    await expect(confirmDialog).toContainText("이 댓글을 삭제할까요? 화면에서 즉시 숨겨집니다.");
    // 위험 조치라 첫 초점은 취소에 둔다.
    const cancel = confirmDialog.getByRole("button", { name: "취소" });
    await expect(cancel).toBeFocused();
    // Tab 은 모달 안에서만 돈다. 열린 동안 뒤 화면은 스크롤 잠금(overflow-hidden)이다.
    await page.keyboard.press("Tab");
    await expect(confirmDialog.getByRole("button", { name: "삭제" })).toBeFocused();
    await page.keyboard.press("Tab");
    await expect(cancel).toBeFocused();
    await page.keyboard.press("Shift+Tab");
    await expect(confirmDialog.getByRole("button", { name: "삭제" })).toBeFocused();
    await expect(page.locator("body")).toHaveClass(/(^|\s)overflow-hidden(\s|$)/);
    await cancel.click();
    await expect(page.locator("body")).not.toHaveClass(/(^|\s)overflow-hidden(\s|$)/);
    await expect(confirmDialog).toHaveCount(0);
    expect(commentDeletes).toBe(0);

    await comment.getByRole("button", { name: "삭제" }).click();
    await page
      .getByRole("alertdialog", { name: "댓글 삭제" })
      .getByRole("button", { name: "삭제" })
      .click();
    await expect(comment).toHaveCount(0);
    expect(commentDeletes).toBe(1);

    const deletePost = page.getByRole("button", { name: "삭제", exact: true });
    await deletePost.click();
    const postDialog = page.getByRole("alertdialog", { name: "게시글 삭제" });
    await expect(postDialog).toContainText("이 게시글을 삭제할까요? 되돌릴 수 없습니다.");
    // 바깥(배경)을 눌러도 취소다.
    await page.mouse.click(5, 5);
    await expect(postDialog).toHaveCount(0);
    expect(postDeletes).toBe(0);

    await deletePost.click();
    await page
      .getByRole("alertdialog", { name: "게시글 삭제" })
      .getByRole("button", { name: "삭제" })
      .click();
    await expect(page).toHaveURL(/\/community$/);
    expect(postDeletes).toBe(1);
    expect(native).toEqual([]);
  });
});
