import { expect, test, type Page } from "@playwright/test";

const SESSION = {
  accountId: "account-me",
  sessionToken: "",
  role: "USER",
} as const;

const externalBaseUrl = process.env.E2E_BASE_URL;
const targetsRemoteHost =
  externalBaseUrl !== undefined &&
  !["localhost", "127.0.0.1"].includes(new URL(externalBaseUrl).hostname);

async function seedSession(page: Page): Promise<void> {
  await page.addInitScript((session) => {
    window.localStorage.setItem("gole.session", JSON.stringify(session));
  }, SESSION);
}

async function mockChatApis(page: Page): Promise<{ readRequests: Array<{ lastMessageId?: string }> }> {
  const readRequests: Array<{ lastMessageId?: string }> = [];
  await page.route("**/api/v1/chat/rooms", (route) => route.fulfill({ json: [] }));
  await page.route("**/api/v1/chat/social/rooms", (route) =>
    route.fulfill({
      json: [
        {
          id: "room-direct",
          type: "DIRECT",
          memberIds: ["account-me", "account-peer"],
          ownerId: null,
          title: null,
          listingId: null,
          createdAt: "2026-08-30T09:00:00Z",
          lastMessageAt: "2026-08-30T09:00:00Z",
          closedAt: null,
          supportStatus: null,
          assigneeId: null,
        },
      ],
    }),
  );
  await page.route("**/api/v1/chat/social/blocks", (route) => route.fulfill({ json: [] }));
  await page.route("**/api/v1/chat/unread-counts", (route) =>
    route.fulfill({ json: { "room-direct": 3 } }),
  );
  await page.route("**/api/v1/chat/rooms/room-direct/read", async (route) => {
    const body = route.request().postDataJSON() as { lastMessageId?: string };
    readRequests.push(body);
    if (body.lastMessageId !== "message-1") {
      await route.fulfill({ status: 400, json: { code: "INVALID", message: "invalid cursor" } });
      return;
    }
    await route.fulfill({ status: 204 });
  });
  await page.route("**/api/v1/config/launch", (route) =>
    route.fulfill({
      json: {
        stage: 0,
        tradeMode: "DIRECT_CHAT",
        features: { payments: false, reviews: false, partnerPayout: false },
        updatedAt: null,
      },
    }),
  );
  await page.route("**/api/v1/chat/rooms/room-direct/messages**", (route) =>
    route.fulfill({
      json: [
        {
          id: "message-1",
          roomId: "room-direct",
          senderId: "account-peer",
          content: "안녕하세요",
          sentAt: "2026-08-30T09:00:00Z",
        },
      ],
    }),
  );
  await page.route("**/api/v1/chat/rooms/room-direct/stream**", (route) =>
    route.fulfill({
      status: 200,
      contentType: "text/event-stream",
      body: "",
    }),
  );
  return { readRequests };
}

test.describe("채팅 UX", () => {
  test.skip(targetsRemoteHost, "응답 가로채기 기반 — 로컬 프론트 전용");

  test.beforeEach(async ({ page }) => {
    await seedSession(page);
  });

  test("같은 화면에서 direct 상대가 바뀌면 작성기 입력도 새 상대와 동기화된다", async ({
    page,
  }) => {
    await mockChatApis(page);
    await page.goto("/chat?direct=account-a");
    const peerInput = page.getByRole("textbox", { name: "상대 계정 ID" });
    await expect(peerInput).toHaveValue("account-a");

    await page.evaluate(() => {
      window.history.pushState(null, "", "/chat?direct=account-b");
      window.dispatchEvent(new PopStateEvent("popstate"));
    });

    await expect(page).toHaveURL(/\/chat\?direct=account-b$/);
    await expect(peerInput).toHaveValue("account-b");
  });

  test("메시지 목록과 입력창이 보조기술에 명확한 이름과 갱신 방식을 제공한다", async ({ page }) => {
    await mockChatApis(page);
    await page.goto("/chat");

    const messageLog = page.getByRole("log", { name: "대화 메시지" });
    await expect(messageLog).toBeVisible();
    await expect(messageLog).toHaveAttribute("aria-live", "polite");
    await expect(messageLog).toContainText("안녕하세요");
    await expect(page.getByRole("textbox", { name: "메시지 입력" })).toBeVisible();
  });

  test("모바일에서는 방을 열기 전까지 안 읽음 수를 유지하고 읽은 뒤에만 지운다", async ({
    page,
  }) => {
    const mock = await mockChatApis(page);
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto("/chat");

    const unreadLabel = page.getByText("읽지 않은 메시지 3개", { exact: true });
    await expect(unreadLabel).toHaveCount(1);
    expect(mock.readRequests).toHaveLength(0);
    const readResponse = page.waitForResponse(
      (response) =>
        response.request().method() === "POST" &&
        response.url().endsWith("/chat/rooms/room-direct/read"),
    );
    await page.getByRole("button", { name: /account-peer/ }).click();

    await readResponse;
    await expect.poll(() => mock.readRequests).toEqual([{ lastMessageId: "message-1" }]);
    await page.getByRole("button", { name: "목록" }).click();
    await expect(unreadLabel).toHaveCount(0);
  });
});
