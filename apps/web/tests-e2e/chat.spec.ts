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

async function mockChatApis(page: Page): Promise<void> {
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
}

test.describe("채팅 UX", () => {
  test.skip(targetsRemoteHost, "응답 가로채기 기반 — 로컬 프론트 전용");

  test.beforeEach(async ({ page }) => {
    await seedSession(page);
    await mockChatApis(page);
  });

  test("같은 화면에서 direct 상대가 바뀌면 작성기 입력도 새 상대와 동기화된다", async ({
    page,
  }) => {
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
    await page.goto("/chat");

    const messageLog = page.getByRole("log", { name: "대화 메시지" });
    await expect(messageLog).toBeVisible();
    await expect(messageLog).toHaveAttribute("aria-live", "polite");
    await expect(messageLog).toContainText("안녕하세요");
    await expect(page.getByRole("textbox", { name: "메시지 입력" })).toBeVisible();
  });
});
