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

async function mockChatApis(
  page: Page,
): Promise<{ readRequests: Array<{ lastMessageId?: string }> }> {
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

async function mockChatDeepLinkApis(
  page: Page,
  options: {
    readonly includeResolvedRoomInList?: boolean;
    readonly roomResolveFailures?: number;
    readonly roomResolveFailureStatus?: number;
    readonly roomResolveRetryAfter?: string;
    readonly counterpartyConfirmed?: boolean;
  } = {},
): Promise<{
  requestedMessageRoomIds: string[];
  roomResolveAttempts: () => number;
  tradeConfirmations: () => number;
}> {
  const requestedMessageRoomIds: string[] = [];
  let roomResolveAttempts = 0;
  let tradeConfirmations = 0;
  await page.route("**/api/v1/chat/rooms", (route) =>
    route.fulfill({
      json:
        options.includeResolvedRoomInList === false
          ? []
          : [
              {
                id: "room-listing",
                listingId: "listing-12345678",
                buyerId: "account-me",
                sellerId: "account-seller",
                createdAt: "2026-08-30T08:00:00Z",
                lastMessageAt: "2026-08-30T08:00:00Z",
                buyerConfirmedAt: null,
                sellerConfirmedAt: options.counterpartyConfirmed
                  ? "2026-08-30T09:05:00Z"
                  : null,
                directTradeCompletedAt: null,
              },
            ],
    }),
  );
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
  await page.route("**/api/v1/chat/unread-counts", (route) => route.fulfill({ json: {} }));
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
  await page.route("**/api/v1/chat/rooms/room-listing", (route) => {
    roomResolveAttempts += 1;
    if (roomResolveAttempts <= (options.roomResolveFailures ?? 0)) {
      return route.fulfill({
        status: options.roomResolveFailureStatus ?? 503,
        headers:
          options.roomResolveRetryAfter === undefined
            ? undefined
            : {
                "Access-Control-Expose-Headers": "Retry-After",
                "Retry-After": options.roomResolveRetryAfter,
              },
        json: { code: "CHAT_TEMPORARILY_UNAVAILABLE", message: "temporary" },
      });
    }
    return route.fulfill({
      json: {
        kind: "LISTING",
        listingRoom: {
          id: "room-listing",
          listingId: "listing-12345678",
          buyerId: "account-me",
          sellerId: "account-seller",
          createdAt: "2026-08-30T08:00:00Z",
          lastMessageAt: "2026-08-30T08:00:00Z",
          buyerConfirmedAt: null,
          sellerConfirmedAt: options.counterpartyConfirmed ? "2026-08-30T09:05:00Z" : null,
          directTradeCompletedAt: null,
        },
        socialRoom: null,
      },
    });
  });
  await page.route("**/api/v1/chat/rooms/room-not-readable", (route) =>
    route.fulfill({
      status: 403,
      json: { code: "CHAT_ROOM_ACCESS_DENIED", message: "접근할 수 없습니다" },
    }),
  );
  await page.route("**/api/v1/chat/rooms/room-listing/direct-trade/confirmation", (route) => {
    tradeConfirmations += 1;
    return route.fulfill({
      json: {
        id: "room-listing",
        listingId: "listing-12345678",
        buyerId: "account-me",
        sellerId: "account-seller",
        createdAt: "2026-08-30T08:00:00Z",
        lastMessageAt: "2026-08-30T08:00:00Z",
        buyerConfirmedAt: "2026-08-30T09:10:00Z",
        sellerConfirmedAt: options.counterpartyConfirmed ? "2026-08-30T09:05:00Z" : null,
        directTradeCompletedAt: options.counterpartyConfirmed
          ? "2026-08-30T09:10:00Z"
          : null,
      },
    });
  });
  await page.route("**/api/v1/chat/rooms/*/messages**", (route) => {
    const match = new URL(route.request().url()).pathname.match(/\/rooms\/([^/]+)\/messages$/);
    const roomId = match?.[1] ?? "unknown";
    requestedMessageRoomIds.push(roomId);
    return route.fulfill({
      json: [
        {
          id: `message-${roomId}`,
          roomId,
          senderId: "account-me",
          content: roomId === "room-listing" ? "직거래 확인 알림에서 이어진 대화" : "개인 대화",
          sentAt: "2026-08-30T09:00:00Z",
        },
      ],
    });
  });
  await page.route("**/api/v1/chat/rooms/*/stream**", (route) =>
    route.fulfill({ status: 200, contentType: "text/event-stream", body: "" }),
  );
  return {
    requestedMessageRoomIds,
    roomResolveAttempts: () => roomResolveAttempts,
    tradeConfirmations: () => tradeConfirmations,
  };
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

  test("직거래 알림의 room 딥링크는 읽을 수 있는 매물 대화를 모바일에서 바로 연다", async ({
    page,
  }) => {
    const mock = await mockChatDeepLinkApis(page, { includeResolvedRoomInList: false });
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto("/chat?source=trade-confirmation&room=room-listing");

    await expect(page).toHaveURL(/\/chat\?source=trade-confirmation$/);
    await expect(page.getByRole("log", { name: "대화 메시지" })).toContainText(
      "직거래 확인 알림에서 이어진 대화",
    );
    await expect(page.locator('button[aria-current="true"]')).toHaveCount(1);
    await expect(page.getByText("account-seller", { exact: true }).last()).toBeVisible();

    // 최근 목록 상한 밖의 방도 다음 10초 폴링에서 닫히지 않아야 한다.
    await page.waitForTimeout(10_500);
    await expect(page.getByRole("log", { name: "대화 메시지" })).toContainText(
      "직거래 확인 알림에서 이어진 대화",
    );

    await page.getByRole("button", { name: "거래 완료" }).click();
    const confirmation = page.getByRole("dialog", { name: "거래 완료를 확인할까요?" });
    await expect(confirmation).toBeVisible();
    expect(mock.tradeConfirmations()).toBe(0);
    await confirmation.getByRole("button", { name: "거래 완료 확인" }).click();
    await expect.poll(mock.tradeConfirmations).toBe(1);
    await expect(page.getByRole("button", { name: "확인 취소" })).toBeVisible();
  });

  test("읽을 수 없는 room 딥링크는 목록을 유지하고 다른 방을 임의로 열지 않는다", async ({
    page,
  }) => {
    const mock = await mockChatDeepLinkApis(page);
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto("/chat?room=room-not-readable");

    await expect(page).toHaveURL(/\/chat$/);
    await expect(page.getByText("전체 대화 2", { exact: true })).toBeVisible();
    await expect(page.getByRole("log", { name: "대화 메시지" })).toHaveCount(0);
    await page.waitForTimeout(250);
    expect(mock.requestedMessageRoomIds).toEqual([]);

    await page.getByRole("button", { name: /account-peer/ }).click();
    await expect(page.getByRole("log", { name: "대화 메시지" })).toContainText("개인 대화");
  });

  test("상대방이 먼저 확인한 직거래는 되돌릴 수 없는 판매 완료를 다시 확인한다", async ({
    page,
  }) => {
    const mock = await mockChatDeepLinkApis(page, {
      includeResolvedRoomInList: false,
      counterpartyConfirmed: true,
    });
    await page.goto("/chat?room=room-listing");

    await page.getByRole("button", { name: "거래 완료" }).click();
    const dialog = page.getByRole("dialog", { name: "이 거래를 최종 완료할까요?" });
    await expect(dialog).toBeVisible();
    expect(mock.tradeConfirmations()).toBe(0);
    await expect(dialog.getByRole("button", { name: "돌아가기" })).toBeFocused();
    await page.keyboard.press("Shift+Tab");
    await expect(dialog.getByRole("button", { name: "판매 완료 확정" })).toBeFocused();
    await page.keyboard.press("Tab");
    await expect(dialog.getByRole("button", { name: "돌아가기" })).toBeFocused();

    await page.keyboard.press("Escape");
    await expect(dialog).toBeHidden();
    await expect(page.getByRole("button", { name: "거래 완료" })).toBeFocused();
    expect(mock.tradeConfirmations()).toBe(0);

    await page.getByRole("button", { name: "거래 완료" }).click();
    await dialog.getByRole("button", { name: "판매 완료 확정" }).click();

    await expect.poll(mock.tradeConfirmations).toBe(1);
    await expect(page.getByRole("status")).toContainText("양쪽이 확인해 거래가 완료됐어요");
  });

  test("단건 방 조회가 일시 실패하면 딥링크를 보존하고 복구 뒤 자동으로 연다", async ({ page }) => {
    const mock = await mockChatDeepLinkApis(page, { roomResolveFailures: 1 });
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto("/chat?source=notification&room=room-listing");

    await expect.poll(mock.roomResolveAttempts).toBe(1);
    await expect(page).toHaveURL(/room=room-listing/);
    await expect(page.getByText("연결이 복구되면 자동으로 다시 열게요.")).toBeVisible();

    await expect.poll(mock.roomResolveAttempts, { timeout: 8_000 }).toBeGreaterThanOrEqual(2);
    await expect(page).toHaveURL(/\/chat\?source=notification$/);
    await expect(page.getByRole("log", { name: "대화 메시지" })).toContainText(
      "직거래 확인 알림에서 이어진 대화",
    );
  });

  test("단건 방 조회 인증이 만료되면 자동 재시도를 멈추고 복귀 경로를 보존한다", async ({
    page,
  }) => {
    const mock = await mockChatDeepLinkApis(page, {
      roomResolveFailures: 100,
      roomResolveFailureStatus: 401,
    });
    await page.goto("/chat?source=notification&room=room-listing");

    await expect(
      page.getByText("로그인이 만료되어 알림의 대화방을 열지 못했습니다."),
    ).toBeVisible();
    await expect(page.getByRole("link", { name: "다시 로그인" })).toHaveAttribute(
      "href",
      /returnTo=%2Fchat%3Fsource%3Dnotification%26room%3Droom-listing/,
    );
    await page.waitForTimeout(1_500);
    expect(mock.roomResolveAttempts()).toBe(1);
    await expect(page).toHaveURL(/room=room-listing/);
  });

  test("단건 방 서버 장애는 제한된 지수 백오프 뒤 수동 재시도로 전환한다", async ({ page }) => {
    const mock = await mockChatDeepLinkApis(page, { roomResolveFailures: 100 });
    await page.goto("/chat?room=room-listing");

    const retry = page.getByRole("button", { name: "다시 시도" });
    await expect(retry).toBeVisible({ timeout: 12_000 });
    expect(mock.roomResolveAttempts()).toBe(4);
    await page.waitForTimeout(1_500);
    expect(mock.roomResolveAttempts()).toBe(4);

    await retry.click();
    await expect.poll(mock.roomResolveAttempts).toBe(5);
  });

  test("단건 방 요청 제한은 서버의 Retry-After 뒤에만 다시 시도한다", async ({ page }) => {
    await page.clock.install();
    const mock = await mockChatDeepLinkApis(page, {
      roomResolveFailures: 1,
      roomResolveFailureStatus: 429,
      roomResolveRetryAfter: "60",
    });
    await page.goto("/chat?room=room-listing");

    await expect.poll(mock.roomResolveAttempts).toBe(1);
    await page.clock.fastForward(31_000);
    expect(mock.roomResolveAttempts()).toBe(1);
    await page.clock.fastForward(29_000);
    await expect.poll(mock.roomResolveAttempts).toBe(2);
    await expect(page).toHaveURL(/\/chat$/);
  });
});
