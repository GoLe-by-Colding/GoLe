import { expect, test, type Page, type Route } from "@playwright/test";

const SESSION = {
  accountId: "account-me",
  sessionToken: "",
  role: "USER",
} as const;

const NOTICE_VERSION = "third-party-2026-09-04";

function socialRoom(id: string, type: "DIRECT" | "GROUP" | "SUPPORT" = "DIRECT") {
  return {
    id,
    type,
    memberIds: type === "SUPPORT" ? ["account-me"] : ["account-me", "account-peer"],
    ownerId: type === "GROUP" || type === "SUPPORT" ? "account-me" : null,
    title: type === "SUPPORT" ? "일반 이용 문의" : null,
    listingId: null,
    createdAt: "2026-09-04T09:00:00Z",
    lastMessageAt: "2026-09-04T09:00:00Z",
    closedAt: null,
    supportStatus: type === "SUPPORT" ? "UNASSIGNED" : null,
    assigneeId: null,
    supportCategory: type === "SUPPORT" ? "GENERAL" : null,
    responseDueAt: null,
  };
}

async function seedSession(page: Page): Promise<void> {
  await page.addInitScript((session) => {
    window.localStorage.setItem("gole.session", JSON.stringify(session));
  }, SESSION);
}

async function mockPageShell(page: Page, rooms: readonly unknown[] = []): Promise<void> {
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
        phoneVerificationRequired: false,
        phoneCompleted: false,
        maskedPhoneNumber: null,
        interestTagsCompleted: true,
        interestTags: ["TECHNIC"],
        privacyConsented: true,
        marketingConsented: false,
      },
    }),
  );
  await page.route("**/api/v1/chat/rooms", (route) => route.fulfill({ json: [] }));
  await page.route("**/api/v1/chat/social/blocks", (route) => route.fulfill({ json: [] }));
  await page.route("**/api/v1/chat/unread-counts", (route) => route.fulfill({ json: {} }));
  await page.route("**/api/v1/config/launch", (route) =>
    route.fulfill({
      json: {
        stage: 0,
        tradeMode: "DIRECT_CHAT",
        features: { payments: false, reviews: false, partnerPayout: false },
        sellerIdentityVerificationReady: true,
        updatedAt: null,
      },
    }),
  );
  await page.route("**/api/v1/chat/social/rooms", (route) => route.fulfill({ json: rooms }));
  await page.route("**/api/v1/policies/current", (route) =>
    route.fulfill({
      json: {
        termsVersion: "2026-09-04",
        privacyVersion: "2026-09-05",
        thirdPartyProvisionVersion: NOTICE_VERSION,
        minimumAge: 14,
      },
    }),
  );
}

async function acceptConsent(dialog: ReturnType<Page["getByRole"]>): Promise<void> {
  await expect(dialog).toContainText("제공받는 자: 대화방 참여자");
  await expect(dialog).toContainText("제공받는 자: 거래 상대방");
  await expect(dialog).toContainText("정보주체의 전체 전화번호");
  await expect(dialog.getByRole("button", { name: "동의하고 계속" })).toBeDisabled();
  await dialog.getByRole("checkbox", { name: /개인정보 제3자 제공에 동의합니다/ }).check();
  await dialog.getByRole("button", { name: "동의하고 계속" }).click();
}

async function consentRecorder(route: Route, requests: unknown[]): Promise<void> {
  requests.push(route.request().postDataJSON());
  await route.fulfill({ status: 204 });
}

test.describe("제3자 제공 JIT 동의", () => {
  test.beforeEach(async ({ page }) => {
    await seedSession(page);
  });

  test("1:1 대화 생성이 거부되면 동의 기록 뒤 원 요청을 한 번만 재시도한다", async ({ page }) => {
    await mockPageShell(page);
    let createAttempts = 0;
    const consentRequests: unknown[] = [];
    await page.route("**/api/v1/chat/social/rooms/direct", async (route) => {
      createAttempts += 1;
      if (createAttempts === 1) {
        await route.fulfill({
          status: 403,
          json: {
            code: "THIRD_PARTY_PROVISION_CONSENT_REQUIRED",
            message: "제3자 제공 동의가 필요합니다.",
          },
        });
        return;
      }
      await route.fulfill({ status: 201, json: socialRoom("room-created") });
    });
    await page.route("**/api/v1/accounts/me/third-party-provision-consents", (route) =>
      consentRecorder(route, consentRequests),
    );
    await page.route("**/api/v1/chat/rooms/room-created/messages**", (route) =>
      route.fulfill({ json: [] }),
    );
    await page.route("**/api/v1/chat/rooms/room-created/stream**", (route) =>
      route.fulfill({ status: 200, contentType: "text/event-stream", body: "" }),
    );

    await page.goto("/chat?direct=account-peer");
    await page.getByRole("button", { name: "대화 시작", exact: true }).click();
    const dialog = page.getByRole("dialog", { name: "제3자 제공 동의가 필요합니다" });
    await acceptConsent(dialog);

    await expect.poll(() => createAttempts).toBe(2);
    await expect.poll(() => consentRequests).toHaveLength(1);
    expect(consentRequests[0]).toMatchObject({
      noticeVersion: NOTICE_VERSION,
      accepted: true,
      path: "SOCIAL_DIRECT_CHAT",
    });
    expect((consentRequests[0] as { requestId?: string }).requestId).toBeTruthy();
    await expect(page.getByRole("log", { name: "대화 메시지" })).toBeVisible();
  });

  test("동의 중 고지 버전이 바뀌면 최신 안내를 다시 받고 체크와 원 요청 재시도를 초기화한다", async ({
    page,
  }) => {
    await seedSession(page);
    let policyLoads = 0;
    let createAttempts = 0;
    const consentRequests: Array<{
      noticeVersion: string;
      requestId: string;
    }> = [];
    await mockPageShell(page);
    await page.route("**/api/v1/policies/current", async (route) => {
      policyLoads += 1;
      await route.fulfill({
        json: {
          termsVersion: "2026-09-04",
          privacyVersion: "2026-09-05",
          thirdPartyProvisionVersion: policyLoads === 1 ? NOTICE_VERSION : `${NOTICE_VERSION}-2`,
          minimumAge: 14,
        },
      });
    });
    await page.route("**/api/v1/chat/social/rooms/direct", async (route) => {
      createAttempts += 1;
      if (createAttempts === 1) {
        await route.fulfill({
          status: 403,
          json: {
            code: "THIRD_PARTY_PROVISION_CONSENT_REQUIRED",
            message: "제3자 제공 동의가 필요합니다.",
          },
        });
        return;
      }
      await route.fulfill({ status: 201, json: socialRoom("room-version-refreshed") });
    });
    await page.route("**/api/v1/accounts/me/third-party-provision-consents", async (route) => {
      const request = route.request().postDataJSON() as {
        noticeVersion: string;
        requestId: string;
      };
      consentRequests.push(request);
      if (consentRequests.length === 1) {
        await route.fulfill({
          status: 400,
          json: {
            code: "THIRD_PARTY_PROVISION_VERSION_STALE",
            message: "동의 안내가 변경되었습니다.",
          },
        });
        return;
      }
      await route.fulfill({
        json: {
          noticeVersion: `${NOTICE_VERSION}-2`,
          consented: true,
          lastDecisionAt: "2026-09-04T09:05:00Z",
        },
      });
    });
    await page.route("**/api/v1/chat/rooms/room-version-refreshed/messages**", (route) =>
      route.fulfill({ json: [] }),
    );
    await page.route("**/api/v1/chat/rooms/room-version-refreshed/stream**", (route) =>
      route.fulfill({ status: 200, contentType: "text/event-stream", body: "" }),
    );

    await page.goto("/chat?direct=account-peer");
    await page.getByRole("button", { name: "대화 시작", exact: true }).click();
    const dialog = page.getByRole("dialog", { name: "제3자 제공 동의가 필요합니다" });
    const checkbox = dialog.getByRole("checkbox", { name: /개인정보 제3자 제공에 동의합니다/ });
    await checkbox.check();
    await dialog.getByRole("button", { name: "동의하고 계속" }).click();

    await expect(dialog).toContainText(`${NOTICE_VERSION}-2`);
    await expect(dialog).toContainText("최신 내용을 확인하고 다시 동의해 주세요");
    await expect(checkbox).not.toBeChecked();
    await expect(dialog.getByRole("button", { name: "동의하고 계속" })).toBeDisabled();
    expect(createAttempts).toBe(1);

    await checkbox.check();
    await dialog.getByRole("button", { name: "동의하고 계속" }).click();
    await expect.poll(() => createAttempts).toBe(2);
    expect(consentRequests.map(({ noticeVersion }) => noticeVersion)).toEqual([
      NOTICE_VERSION,
      `${NOTICE_VERSION}-2`,
    ]);
    expect(consentRequests[1]?.requestId).toBe(consentRequests[0]?.requestId);
  });

  test("일반 대화 메시지는 동의 뒤 한 번만 재전송하고 운영팀 문의는 동의를 요구하지 않는다", async ({
    page,
  }) => {
    await mockPageShell(page, [socialRoom("room-direct")]);
    const consentRequests: unknown[] = [];
    let messageAttempts = 0;
    await page.route("**/api/v1/accounts/me/third-party-provision-consents", (route) =>
      consentRecorder(route, consentRequests),
    );
    await page.route("**/api/v1/chat/rooms/room-direct/messages**", async (route) => {
      if (route.request().method() === "GET") {
        await route.fulfill({ json: [] });
        return;
      }
      messageAttempts += 1;
      if (messageAttempts === 1) {
        await route.fulfill({
          status: 403,
          json: {
            code: "THIRD_PARTY_PROVISION_CONSENT_REQUIRED",
            message: "제3자 제공 동의가 필요합니다.",
          },
        });
        return;
      }
      await route.fulfill({
        status: 201,
        json: {
          id: "message-sent",
          roomId: "room-direct",
          senderId: "account-me",
          content: "동의 뒤 전송",
          sentAt: "2026-09-04T09:10:00Z",
        },
      });
    });
    await page.route("**/api/v1/chat/rooms/room-direct/stream**", (route) =>
      route.fulfill({ status: 200, contentType: "text/event-stream", body: "" }),
    );

    await page.goto("/chat");
    await page.getByRole("textbox", { name: "메시지 입력" }).fill("동의 뒤 전송");
    await page.getByRole("button", { name: "전송" }).click();
    await acceptConsent(page.getByRole("dialog", { name: "제3자 제공 동의가 필요합니다" }));

    await expect.poll(() => messageAttempts).toBe(2);
    expect(consentRequests).toHaveLength(1);
    expect(consentRequests[0]).toMatchObject({ path: "CHAT_MESSAGE" });
    await expect(page.getByRole("log", { name: "대화 메시지" })).toContainText("동의 뒤 전송");
  });

  test("운영팀 문의 생성은 제3자 제공 JIT 흐름에서 제외한다", async ({ page }) => {
    await mockPageShell(page);
    let supportAttempts = 0;
    let consentAttempts = 0;
    await page.route("**/api/v1/accounts/me/third-party-provision-consents", async (route) => {
      consentAttempts += 1;
      await route.fulfill({ status: 204 });
    });
    await page.route("**/api/v1/chat/social/rooms/support", async (route) => {
      supportAttempts += 1;
      await route.fulfill({ status: 201, json: socialRoom("room-support", "SUPPORT") });
    });
    await page.route("**/api/v1/chat/rooms/room-support/messages**", (route) =>
      route.fulfill({ json: [] }),
    );
    await page.route("**/api/v1/chat/rooms/room-support/stream**", (route) =>
      route.fulfill({ status: 200, contentType: "text/event-stream", body: "" }),
    );

    await page.goto("/chat?compose=support");
    await page.getByRole("textbox", { name: "문의 내용" }).fill("개인정보 문의입니다.");
    await page.getByRole("button", { name: "문의 시작" }).click();

    await expect.poll(() => supportAttempts).toBe(1);
    expect(consentAttempts).toBe(0);
    await expect(page.getByRole("dialog", { name: "제3자 제공 동의가 필요합니다" })).toHaveCount(0);
  });

  test("계정 보안에서 철회 이력을 남기고 같은 안내에 다시 동의할 수 있다", async ({ page }) => {
    await mockPageShell(page);
    const withdrawalRequests: unknown[] = [];
    const consentRequests: unknown[] = [];
    await page.route("**/api/v1/accounts/me/third-party-provision-consents/current", (route) =>
      route.fulfill({
        json: {
          noticeVersion: NOTICE_VERSION,
          consented: true,
          lastDecisionAt: "2026-09-04T09:00:00Z",
        },
      }),
    );
    await page.route(
      "**/api/v1/accounts/me/third-party-provision-consent-withdrawals",
      async (route) => {
        withdrawalRequests.push(route.request().postDataJSON());
        await route.fulfill({
          json: {
            noticeVersion: NOTICE_VERSION,
            consented: false,
            lastDecisionAt: "2026-09-04T09:10:00Z",
          },
        });
      },
    );
    await page.route("**/api/v1/accounts/me/third-party-provision-consents", async (route) => {
      consentRequests.push(route.request().postDataJSON());
      await route.fulfill({
        json: {
          noticeVersion: NOTICE_VERSION,
          consented: true,
          lastDecisionAt: "2026-09-04T09:20:00Z",
        },
      });
    });
    page.on("dialog", (dialog) => void dialog.accept());

    await page.goto("/profile/security");
    await expect(page.getByText("현재 상태: 동의함")).toBeVisible();
    await page.getByRole("button", { name: "동의 철회" }).click();

    await expect(page.getByText("현재 상태: 동의하지 않음")).toBeVisible();
    expect(withdrawalRequests).toHaveLength(1);
    expect(withdrawalRequests[0]).toMatchObject({ noticeVersion: NOTICE_VERSION });
    expect((withdrawalRequests[0] as { requestId?: string }).requestId).toBeTruthy();

    await page.getByRole("checkbox", { name: /개인정보 제3자 제공에 동의합니다/ }).check();
    await page.getByRole("button", { name: "동의하기" }).click();

    await expect(page.getByText("현재 상태: 동의함")).toBeVisible();
    expect(consentRequests).toHaveLength(1);
    expect(consentRequests[0]).toMatchObject({
      noticeVersion: NOTICE_VERSION,
      accepted: true,
      path: "ACCOUNT_SETTINGS",
    });
    expect((consentRequests[0] as { requestId?: string }).requestId).toBeTruthy();
  });

  test("상대방 연락처는 JIT 동의 뒤 최소 응답만 한 번 다시 조회한다", async ({ page }) => {
    await mockPageShell(page);
    const consentRequests: unknown[] = [];
    let contactAttempts = 0;
    await page.route(/\/api\/v1\/orders\/order-consent$/, (route) =>
      route.fulfill({
        json: {
          id: "order-consent",
          listingId: "listing-consent",
          buyerId: "account-me",
          sellerId: "account-peer",
          catalogSetNumber: "10307",
          amount: 49_900,
          status: "funds_held",
          paymentMethod: null,
          buyerPhoneMasked: "010-****-0000",
          disputeReason: null,
          disputeDetail: null,
          disputeOpenedAt: null,
          createdAt: "2026-09-04T09:00:00Z",
          history: [{ status: "funds_held", occurredAt: "2026-09-04T09:00:00Z" }],
        },
      }),
    );
    await page.route("**/api/v1/orders/order-consent/shipment", (route) =>
      route.fulfill({
        status: 404,
        json: { code: "SHIPMENT_NOT_FOUND", message: "배송 정보가 없습니다." },
      }),
    );
    await page.route("**/api/v1/orders/order-consent/contacts", async (route) => {
      contactAttempts += 1;
      if (contactAttempts === 1) {
        await route.fulfill({
          status: 403,
          json: {
            code: "THIRD_PARTY_PROVISION_CONSENT_REQUIRED",
            message: "제3자 제공 동의가 필요합니다.",
          },
        });
        return;
      }
      await route.fulfill({
        json: {
          counterpartPhone: "010-2222-3333",
          notice: "거래 분쟁 대응 목적으로만 사용할 수 있습니다.",
        },
      });
    });
    await page.route("**/api/v1/accounts/me/third-party-provision-consents", (route) =>
      consentRecorder(route, consentRequests),
    );

    await page.goto("/orders/order-consent");
    await page.getByRole("button", { name: "상대방 연락처 확인" }).click();
    await acceptConsent(page.getByRole("dialog", { name: "제3자 제공 동의가 필요합니다" }));

    await expect.poll(() => contactAttempts).toBe(2);
    expect(consentRequests).toHaveLength(1);
    expect(consentRequests[0]).toMatchObject({ path: "ORDER_CONTACTS" });
    await expect(page.getByText("010-2222-3333")).toBeVisible();
    await expect(page.getByText("거래 분쟁 대응 목적으로만 사용할 수 있습니다.")).toBeVisible();
  });
});
