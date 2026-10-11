import { expect, test, type Page, type Route } from "@playwright/test";
import {
  E2E_BUYER,
  E2E_SELLER,
  recordThirdPartyConsent,
  signInAs,
  switchTo,
} from "./support/e2e-session";

/**
 * 가격 제안(.kiro/specs/price-offer F2).
 *
 * 채팅 화면(`/chat`)은 브라우저가 API를 부르므로 응답을 가로채 배너·제안·응답·동의 흐름을 검증한다 —
 * 실제 API 없이 돈다. 매물 상세(인라인 채팅·구매 버튼·판매자 패널)는 서버 렌더라 맨 아래 실제 백엔드
 * 스펙이 확인한다.
 */

const externalBaseUrl = process.env.E2E_BASE_URL;
const targetsRemoteHost =
  externalBaseUrl !== undefined &&
  !["localhost", "127.0.0.1"].includes(new URL(externalBaseUrl).hostname);

const BUYER_ID = "account-offer-buyer";
const SELLER_ID = "account-offer-seller";
const LISTING_ID = "listing-offer-1";
const ROOM_ID = "room-offer-1";
const NOTICE_VERSION = "third-party-2026-09-04";
const HOUR = 60 * 60 * 1000;

const ROOM = {
  id: ROOM_ID,
  listingId: LISTING_ID,
  buyerId: BUYER_ID,
  sellerId: SELLER_ID,
  createdAt: "2026-10-09T00:00:00Z",
  lastMessageAt: "2026-10-09T00:00:00Z",
  buyerConfirmedAt: null,
  sellerConfirmedAt: null,
  directTradeCompletedAt: null,
} as const;

const LISTING = {
  id: LISTING_ID,
  sellerId: SELLER_ID,
  title: "에펠탑 10307",
  description: "한 번 조립",
  price: 100000,
  condition: "like_new",
  completeness: "full_box",
  hasBox: true,
  hasManual: true,
  hasMissingParts: false,
  missingPartsNote: "",
  defectsNote: "",
  photoUrls: [],
  catalogSetNumber: "10307",
  category: "set",
  interestTag: null,
  status: "active",
  createdAt: "2026-10-01T00:00:00Z",
} as const;

type OfferStatus = "pending" | "accepted" | "declined" | "withdrawn" | "expired";

interface TestOffer {
  readonly id: string;
  readonly listingId: string;
  readonly roomId: string | null;
  readonly buyerId: string;
  readonly sellerId: string;
  readonly price: number;
  readonly listingPriceAtOffer: number;
  readonly origin: "chat" | "bid";
  readonly status: OfferStatus;
  readonly createdAt: string;
  readonly respondedAt: string | null;
  readonly expiresAt: string;
}

function offer(overrides: Partial<TestOffer> = {}): TestOffer {
  return {
    id: "offer-1",
    listingId: LISTING_ID,
    roomId: ROOM_ID,
    buyerId: BUYER_ID,
    sellerId: SELLER_ID,
    price: 90000,
    listingPriceAtOffer: 100000,
    origin: "chat",
    status: "pending",
    createdAt: new Date(Date.now() - HOUR).toISOString(),
    respondedAt: null,
    expiresAt: new Date(Date.now() + 47 * HOUR).toISOString(),
    ...overrides,
  };
}

const DIRECT_TRADE_LAUNCH = {
  stage: 0,
  tradeMode: "DIRECT_CHAT",
  features: { payments: false, reviews: false, partnerPayout: false },
  sellerIdentityVerificationReady: true,
  updatedAt: null,
} as const;

const PAYMENTS_LAUNCH = {
  stage: 2,
  tradeMode: "MANUAL_SETTLEMENT",
  features: { payments: true, reviews: true, partnerPayout: false },
  sellerIdentityVerificationReady: true,
  updatedAt: "2026-10-09T00:00:00Z",
} as const;

interface OfferApi {
  /** GET 이 돌려줄 제안 목록. 테스트가 바꿔 끼운다. */
  offers: TestOffer[];
  /** POST /offers 응답을 순서대로 꺼낸다. 비면 받은 가격으로 대기 제안을 만든다. */
  readonly makeResponses: Array<{ readonly status: number; readonly json: unknown }>;
  readonly makeBodies: unknown[];
  readonly responses: Array<{ readonly offerId: string; readonly action: string }>;
  listReads: number;
}

async function seedSession(page: Page, accountId: string): Promise<void> {
  // 앱이 새로고침 이후 실제로 갖는 모양 — 토큰은 HttpOnly 쿠키에 있고 여기는 비어 있다.
  await page.addInitScript((id) => {
    window.localStorage.setItem(
      "gole.session",
      JSON.stringify({ accountId: id, sessionToken: "", role: "USER" }),
    );
  }, accountId);
}

/** 대상과 무관한 전역 요청. 실백엔드 401이 합성 세션을 지우지 않도록 격리한다. */
async function isolateLayoutRequests(page: Page): Promise<void> {
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
        phoneVerificationRequired: true,
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

/**
 * 매물 방 하나가 열린 `/chat`. 데스크톱 폭에서는 가장 최근 방이 자동으로 열린다.
 * `stream`을 넘기면 실시간 스트림 응답을 테스트가 정한다.
 */
async function mockListingRoom(
  page: Page,
  options: {
    readonly launch?: unknown;
    readonly offers?: TestOffer[];
    readonly stream?: (route: Route) => Promise<void>;
  } = {},
): Promise<OfferApi> {
  const api: OfferApi = {
    offers: options.offers ?? [],
    makeResponses: [],
    makeBodies: [],
    responses: [],
    listReads: 0,
  };
  await isolateLayoutRequests(page);
  await page.route("**/api/v1/config/launch", (route) =>
    route.fulfill({ json: options.launch ?? DIRECT_TRADE_LAUNCH }),
  );
  await page.route("**/api/v1/chat/rooms", (route) => route.fulfill({ json: [ROOM] }));
  await page.route("**/api/v1/chat/social/rooms", (route) => route.fulfill({ json: [] }));
  await page.route("**/api/v1/chat/social/blocks", (route) => route.fulfill({ json: [] }));
  await page.route("**/api/v1/chat/unread-counts", (route) => route.fulfill({ json: {} }));
  await page.route(`**/api/v1/chat/rooms/${ROOM_ID}/read`, (route) =>
    route.fulfill({ status: 204, body: "" }),
  );
  await page.route(`**/api/v1/chat/rooms/${ROOM_ID}/messages**`, (route) =>
    route.fulfill({ json: [] }),
  );
  await page.route(
    `**/api/v1/chat/rooms/${ROOM_ID}/stream**`,
    options.stream ??
      ((route) => route.fulfill({ status: 200, contentType: "text/event-stream", body: "" })),
  );
  await page.route(`**/api/v1/listings/${LISTING_ID}`, (route) => route.fulfill({ json: LISTING }));
  await page.route(/\/api\/v1\/offers(?:[/?].*)?$/, async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    if (request.method() === "GET") {
      api.listReads += 1;
      expect(url.searchParams.get("listingId")).toBe(LISTING_ID);
      await route.fulfill({ json: api.offers });
      return;
    }
    if (url.pathname.endsWith("/offers")) {
      const body = request.postDataJSON() as { roomId: string; price: number };
      api.makeBodies.push(body);
      const queued = api.makeResponses.shift();
      if (queued !== undefined) {
        await route.fulfill({ status: queued.status, json: queued.json });
        return;
      }
      const created = offer({ id: `offer-new-${api.makeBodies.length}`, price: body.price });
      api.offers = [created, ...api.offers];
      await route.fulfill({ status: 201, json: created });
      return;
    }
    const match = url.pathname.match(/\/offers\/([^/]+)\/(accept|decline|withdraw)$/);
    if (match === null) {
      await route.fulfill({ status: 404, json: { code: "NOT_FOUND", message: "unknown" } });
      return;
    }
    const [, offerId = "", action = ""] = match;
    api.responses.push({ offerId, action });
    const status: OfferStatus =
      action === "accept" ? "accepted" : action === "decline" ? "declined" : "withdrawn";
    const current = api.offers.find((item) => item.id === offerId) ?? offer({ id: offerId });
    const updated = {
      ...current,
      status,
      respondedAt: new Date().toISOString(),
      expiresAt:
        action === "accept" ? new Date(Date.now() + 72 * HOUR).toISOString() : current.expiresAt,
    };
    api.offers = api.offers.map((item) => (item.id === offerId ? updated : item));
    await route.fulfill({ json: updated });
  });
  return api;
}

test.describe("채팅 가격 제안 — 구매자", () => {
  test.skip(targetsRemoteHost, "응답 가로채기 기반 — 로컬 프론트 전용");

  test.beforeEach(async ({ page }) => {
    await seedSession(page, BUYER_ID);
  });

  test("판매가보다 낮은 금액을 할인율과 함께 제안하고, 대기 중인 제안을 철회한다", async ({
    page,
  }) => {
    const api = await mockListingRoom(page);
    await page.goto("/chat");

    const panel = page.getByRole("region", { name: "가격 제안" });
    await expect(panel).toContainText("원하는 가격이 있으면 판매자에게 제안해 보세요.");
    await panel.getByRole("button", { name: "가격 제안" }).click();

    const amount = panel.getByLabel("제안 금액 (원)");
    await amount.fill("120000");
    await panel.getByRole("button", { name: "제안하기" }).click();
    await expect(panel.getByText("판매가보다 낮은 금액만 제안할 수 있어요.")).toBeVisible();
    expect(api.makeBodies).toEqual([]);

    await amount.fill("90000");
    await expect(panel.getByText("판매가 100,000원 · 10% 할인")).toBeVisible();
    await panel.getByRole("button", { name: "제안하기" }).click();

    await expect.poll(() => api.makeBodies).toEqual([{ roomId: ROOM_ID, price: 90000 }]);
    await expect(panel.getByText("90,000원 제안 · 판매자 응답을 기다리고 있어요")).toBeVisible();
    await expect(panel.getByText("응답 대기", { exact: true })).toBeVisible();
    await expect(panel.getByText(/판매가 100,000원 · 10% 할인 · (46|47)시간 남음/)).toBeVisible();

    await panel.getByRole("button", { name: "철회" }).click();
    await expect
      .poll(() => api.responses)
      .toEqual([{ offerId: "offer-new-1", action: "withdraw" }]);
    await expect(panel).toContainText("지난 제안 90,000원은 철회했어요.");
    await expect(panel.getByRole("button", { name: "가격 제안" })).toBeVisible();
  });

  test("제안이 제3자 제공 동의를 요구하면 채팅 메시지 경로로 동의한 뒤 한 번만 다시 보낸다", async ({
    page,
  }) => {
    const api = await mockListingRoom(page);
    api.makeResponses.push({
      status: 403,
      json: { code: "THIRD_PARTY_PROVISION_CONSENT_REQUIRED", message: "동의가 필요합니다." },
    });
    const consentRequests: unknown[] = [];
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
    await page.route("**/api/v1/accounts/me/third-party-provision-consents", async (route) => {
      consentRequests.push(route.request().postDataJSON());
      await route.fulfill({ status: 204, body: "" });
    });

    await page.goto("/chat");
    const panel = page.getByRole("region", { name: "가격 제안" });
    await panel.getByRole("button", { name: "가격 제안" }).click();
    await panel.getByLabel("제안 금액 (원)").fill("85000");
    await panel.getByRole("button", { name: "제안하기" }).click();

    const dialog = page.getByRole("dialog", { name: "제3자 제공 동의가 필요합니다" });
    await dialog.getByRole("checkbox", { name: /개인정보 제3자 제공에 동의합니다/ }).check();
    await dialog.getByRole("button", { name: "동의하고 계속" }).click();

    await expect.poll(() => api.makeBodies).toHaveLength(2);
    expect(consentRequests).toHaveLength(1);
    expect(consentRequests[0]).toMatchObject({
      noticeVersion: NOTICE_VERSION,
      path: "CHAT_MESSAGE",
    });
    await expect(panel.getByText("85,000원 제안 · 판매자 응답을 기다리고 있어요")).toBeVisible();
  });

  test("이미 대기 중인 제안이 있으면 이유를 알려 주고 지금 상태로 다시 읽는다", async ({
    page,
  }) => {
    const api = await mockListingRoom(page);
    api.makeResponses.push({
      status: 409,
      json: { code: "OFFER_ALREADY_PENDING", message: "already pending" },
    });

    await page.goto("/chat");
    const panel = page.getByRole("region", { name: "가격 제안" });
    await panel.getByRole("button", { name: "가격 제안" }).click();
    await panel.getByLabel("제안 금액 (원)").fill("80000");
    // 다른 기기에서 먼저 넣은 제안이 서버에 있다.
    api.offers = [offer({ id: "offer-elsewhere", price: 95000 })];
    await panel.getByRole("button", { name: "제안하기" }).click();

    await expect(panel.getByText(/응답을 기다리는 제안이 이미 있어요/)).toBeVisible();
    await expect(panel.getByText("95,000원 제안 · 판매자 응답을 기다리고 있어요")).toBeVisible();
  });

  test("판매자가 수락하면 결제 단계에서는 제안가로 구매하기로 매물에 간다", async ({ page }) => {
    await mockListingRoom(page, {
      launch: PAYMENTS_LAUNCH,
      offers: [offer({ status: "accepted", respondedAt: new Date().toISOString() })],
    });
    await page.goto("/chat");

    const panel = page.getByRole("region", { name: "가격 제안" });
    await expect(panel.getByText("판매자가 90,000원 제안을 수락했어요")).toBeVisible();
    await expect(panel.getByText("매물 화면에서 제안가로 구매할 수 있어요.")).toBeVisible();
    await expect(panel.getByRole("link", { name: "제안가로 구매하기" })).toHaveAttribute(
      "href",
      `/listings/${LISTING_ID}`,
    );
    // 수락된 제안이 있는 동안에는 새 제안을 받지 않는다.
    await expect(panel.getByRole("button", { name: "가격 제안" })).toHaveCount(0);
  });

  test("입찰 체결로 생긴 수락 제안도 직거래 단계에서는 합의한 가격으로 보인다", async ({
    page,
  }) => {
    await mockListingRoom(page, {
      offers: [
        offer({
          id: "offer-from-bid",
          roomId: null,
          origin: "bid",
          price: 120000,
          status: "accepted",
          respondedAt: new Date().toISOString(),
        }),
      ],
    });
    await page.goto("/chat");

    const panel = page.getByRole("region", { name: "가격 제안" });
    await expect(panel.getByText("판매자가 입찰가 120,000원에 판매를 수락했어요")).toBeVisible();
    await expect(panel.getByText("합의한 가격 120,000원 — 직거래로 진행해요.")).toBeVisible();
    await expect(panel.getByRole("link", { name: "제안가로 구매하기" })).toHaveCount(0);
  });

  test("방에 새 메시지가 오면 폴링을 기다리지 않고 제안을 다시 읽는다", async ({ page }) => {
    let releaseStream: () => void = () => undefined;
    const streamGate = new Promise<void>((resolve) => {
      releaseStream = resolve;
    });
    const api = await mockListingRoom(page, {
      offers: [offer()],
      stream: async (route) => {
        await streamGate;
        // 판매자의 수락은 방에 메시지로 남는다(O7). 그 메시지가 스트림으로 도착한다.
        api.offers = [offer({ status: "accepted", respondedAt: new Date().toISOString() })];
        await route
          .fulfill({
            status: 200,
            contentType: "text/event-stream",
            body: `data: ${JSON.stringify({
              id: "message-accepted",
              senderId: SELLER_ID,
              content: "[가격 제안] 90,000원 제안을 수락했어요",
              sentAt: new Date().toISOString(),
            })}\n\n`,
          })
          .catch(() => undefined);
      },
    });

    await page.goto("/chat");
    const panel = page.getByRole("region", { name: "가격 제안" });
    await expect(panel.getByText("90,000원 제안 · 판매자 응답을 기다리고 있어요")).toBeVisible();

    releaseStream();
    await expect(page.getByRole("log", { name: "대화 메시지" })).toContainText(
      "90,000원 제안을 수락했어요",
    );
    // 10초 폴링보다 짧게 기다린다 — 메시지 도착이 다시 읽기를 일으켜야 통과한다.
    await expect(panel.getByText("판매자가 90,000원 제안을 수락했어요")).toBeVisible({
      timeout: 4_000,
    });
  });
});

test.describe("채팅 가격 제안 — 판매자", () => {
  test.skip(targetsRemoteHost, "응답 가로채기 기반 — 로컬 프론트 전용");

  test.beforeEach(async ({ page }) => {
    await seedSession(page, SELLER_ID);
  });

  test("대기 제안을 수락하면 직거래 단계에서는 합의한 가격으로 안내하고 수락을 취소할 수 있다", async ({
    page,
  }) => {
    const api = await mockListingRoom(page, {
      offers: [
        offer(),
        // 같은 매물의 다른 구매자 제안은 이 방에 보이지 않는다.
        offer({ id: "offer-other-buyer", buyerId: "account-other", roomId: "room-other" }),
      ],
    });
    await page.goto("/chat");

    const panel = page.getByRole("region", { name: "가격 제안" });
    await expect(panel.getByText("구매자가 90,000원을 제안했어요")).toBeVisible();
    await expect(panel.getByText(/판매가 100,000원 · 10% 할인/)).toBeVisible();
    await expect(panel.getByRole("button", { name: "수락", exact: true })).toHaveCount(1);
    // 판매자에게는 제안 입력이 없다.
    await expect(panel.getByRole("button", { name: "가격 제안" })).toHaveCount(0);

    await panel.getByRole("button", { name: "수락", exact: true }).click();
    await expect.poll(() => api.responses).toEqual([{ offerId: "offer-1", action: "accept" }]);
    await expect(panel.getByText("90,000원 제안을 수락했어요")).toBeVisible();
    await expect(panel.getByText("합의한 가격 90,000원 — 직거래로 진행해요.")).toBeVisible();

    await panel.getByRole("button", { name: "수락 취소" }).click();
    await expect
      .poll(() => api.responses.at(-1))
      .toEqual({ offerId: "offer-1", action: "decline" });
    // 응답할 제안이 없으면 판매자 화면에서는 배너가 사라진다.
    await expect(page.getByRole("region", { name: "가격 제안" })).toHaveCount(0);
  });

  test("구매자가 먼저 철회해 수락이 지면 이유를 보여 주고 목록을 다시 맞춘다", async ({ page }) => {
    const api = await mockListingRoom(page, { offers: [offer()] });
    await page.route("**/api/v1/offers/offer-1/accept", async (route) => {
      api.offers = [offer({ status: "withdrawn", respondedAt: new Date().toISOString() })];
      await route.fulfill({
        status: 409,
        json: { code: "OFFER_NOT_PENDING", message: "not pending" },
      });
    });
    await page.goto("/chat");

    const panel = page.getByRole("region", { name: "가격 제안" });
    await panel.getByRole("button", { name: "수락", exact: true }).click();

    // 대기 제안이 없어져 배너가 닫힌다 — 다시 읽은 결과를 따른 것이다.
    await expect(page.getByRole("region", { name: "가격 제안" })).toHaveCount(0);
  });
});

test.describe("가격 제안 알림", () => {
  test.skip(targetsRemoteHost, "응답 가로채기 기반 — 로컬 프론트 전용");

  test("받은 제안은 대화방으로, 수락된 제안은 매물로 이어진다", async ({ page }) => {
    await seedSession(page, SELLER_ID);
    await isolateLayoutRequests(page);
    await page.route(`**/api/v1/users/${SELLER_ID}/notifications`, (route) =>
      route.fulfill({
        json: [
          {
            id: "notification-offer-received",
            type: "OFFER_RECEIVED",
            message: "에펠탑 10307에 90,000원 가격 제안이 왔어요",
            link: `/chat?room=${ROOM_ID}`,
            read: false,
            createdAt: "2026-10-09T00:00:00Z",
          },
          {
            id: "notification-offer-accepted",
            type: "OFFER_ACCEPTED",
            message: "판매자가 90,000원 제안을 수락했어요",
            link: `/listings/${LISTING_ID}`,
            read: false,
            createdAt: "2026-10-09T00:01:00Z",
          },
        ],
      }),
    );

    await page.goto("/notifications");

    await expect(page.getByRole("link", { name: /90,000원 가격 제안이 왔어요/ })).toHaveAttribute(
      "href",
      `/chat?room=${ROOM_ID}`,
    );
    await expect(
      page.getByRole("link", { name: /판매자가 90,000원 제안을 수락했어요/ }),
    ).toHaveAttribute("href", `/listings/${LISTING_ID}`);
  });
});

const apiBaseUrl = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

interface SeedListing {
  readonly id: string;
  readonly sellerId: string;
  readonly price: number;
  readonly status: string;
  readonly catalogSetNumber: string | null;
}

/** 실제 API의 판매 중 시드 매물. 상세는 서버가 이 매물로 그린다. */
async function findSeedListing(page: Page): Promise<SeedListing> {
  const response = await page.request.get(`${apiBaseUrl}/api/v1/listings`);
  expect(response.ok()).toBeTruthy();
  const listings = (await response.json()) as SeedListing[];
  const listing = listings.find(
    (item) => item.status === "active" && item.catalogSetNumber !== null,
  );
  expect(listing, "세트 번호가 있는 판매 중 시드 매물이 필요합니다").toBeDefined();
  return listing!;
}

/** 상세 화면이 브라우저에서 부르는 세션 필요 조회. 합성 세션이 실백엔드 401로 지워지지 않게 한다. */
async function isolateDetailRequests(page: Page, accountId: string): Promise<void> {
  await isolateLayoutRequests(page);
  await page.route("**/api/v1/accounts/me", (route) =>
    route.fulfill({ json: { accountId, email: "e2e@gole.test", role: "USER" } }),
  );
  await page.route(/\/api\/v1\/users\/[^/]+\/wishlist(?:[/?].*)?$/, (route) =>
    route.request().method() === "GET"
      ? route.fulfill({ json: [] })
      : route.fulfill({ status: 204, body: "" }),
  );
}

// 매물 상세는 실제 API가 서버에서 그리고, 그 위에서 브라우저가 부르는 제안·주문·채팅 요청만 가로챈다 —
// 쓰기는 실제 API로 나가지 않는다. 시드 매물이 있는 백엔드가 필요하므로 E2E_WITH_BACKEND=1 에서 돈다.
test.describe("매물 상세 — 제안가 구매·인라인 제안·받은 제안 (응답 가로채기)", () => {
  test.skip(
    process.env.E2E_WITH_BACKEND !== "1" || targetsRemoteHost,
    "실제 API가 렌더하는 상세 화면 — 로컬 백엔드 전용",
  );

  test("수락 제안이 있으면 제안가로 주문하고, 서버가 거부하면 정가 주문으로 이어 간다", async ({
    page,
  }) => {
    const launchResponse = await page.request.get(`${apiBaseUrl}/api/v1/config/launch`);
    const launch = (await launchResponse.json()) as {
      stage: number;
      features: { payments: boolean };
      sellerIdentityVerificationReady: boolean;
    };
    test.skip(
      !(launch.sellerIdentityVerificationReady && launch.features.payments && launch.stage >= 2),
      "결제가 닫힌 단계에서는 구매 버튼이 없다",
    );
    const listing = await findSeedListing(page);
    const offerPrice = listing.price - 1000;
    await seedSession(page, BUYER_ID);
    await isolateDetailRequests(page, BUYER_ID);
    await page.route(/\/api\/v1\/offers(?:\?.*)?$/, (route) =>
      route.fulfill({
        json: [
          offer({
            id: "offer-accepted",
            listingId: listing.id,
            sellerId: listing.sellerId,
            price: offerPrice,
            listingPriceAtOffer: listing.price,
            status: "accepted",
            respondedAt: new Date().toISOString(),
          }),
        ],
      }),
    );
    const orderBodies: Array<Record<string, unknown>> = [];
    await page.route("**/api/v1/orders", async (route) => {
      orderBodies.push(route.request().postDataJSON() as Record<string, unknown>);
      if (orderBodies.length === 1) {
        await route.fulfill({
          status: 409,
          json: { code: "OFFER_NOT_USABLE", message: "not usable" },
        });
        return;
      }
      await route.fulfill({ status: 201, json: { id: "order-full-price" } });
    });

    await page.goto(`/listings/${listing.id}`);
    const offerLabel = `${offerPrice.toLocaleString("ko-KR")}원`;
    await page.getByRole("button", { name: `제안가 ${offerLabel}으로 구매` }).click();
    await expect(
      page.getByText(`판매자가 수락한 제안가 ${offerLabel}으로 주문해요.`),
    ).toBeVisible();
    await page.getByLabel("CS 연락처").fill("010-1234-5678");
    await page.getByRole("button", { name: "주문하기" }).click();

    await expect(page.getByText(/이 제안으로는 주문할 수 없어요/)).toBeVisible();
    expect(orderBodies[0]).toMatchObject({ listingId: listing.id, offerId: "offer-accepted" });
    await page.getByRole("button", { name: "정가로 주문하기" }).click();

    await expect(page).toHaveURL(/\/orders\/order-full-price$/);
    expect(orderBodies).toHaveLength(2);
    expect(orderBodies[1]).toMatchObject({ listingId: listing.id, buyerPhone: "010-1234-5678" });
    expect(orderBodies[1]).not.toHaveProperty("offerId");
  });

  test("인라인 거래 대화에서 가격을 제안한다", async ({ page }) => {
    const listing = await findSeedListing(page);
    await seedSession(page, BUYER_ID);
    await isolateDetailRequests(page, BUYER_ID);
    const room = { ...ROOM, id: "room-inline", listingId: listing.id, sellerId: listing.sellerId };
    await page.route("**/api/v1/chat/rooms", (route) =>
      route.request().method() === "POST"
        ? route.fulfill({ json: room })
        : route.fulfill({ json: [room] }),
    );
    await page.route("**/api/v1/chat/rooms/room-inline/messages**", (route) =>
      route.fulfill({ json: [] }),
    );
    await page.route("**/api/v1/chat/rooms/room-inline/stream**", (route) =>
      route.fulfill({ status: 200, contentType: "text/event-stream", body: "" }),
    );
    const offerBodies: unknown[] = [];
    let created: TestOffer | null = null;
    await page.route(/\/api\/v1\/offers(?:\?.*)?$/, async (route) => {
      if (route.request().method() === "GET") {
        await route.fulfill({ json: created === null ? [] : [created] });
        return;
      }
      const body = route.request().postDataJSON() as { price: number };
      offerBodies.push(body);
      created = offer({
        id: "offer-inline",
        roomId: room.id,
        listingId: listing.id,
        sellerId: listing.sellerId,
        price: body.price,
        listingPriceAtOffer: listing.price,
      });
      await route.fulfill({ status: 201, json: created });
    });

    await page.goto(`/listings/${listing.id}`);
    await page.getByRole("button", { name: /거래 문의하기|판매자와 채팅하기/ }).click();
    const panel = page.getByRole("region", { name: "가격 제안" });
    await panel.getByRole("button", { name: "가격 제안" }).click();
    const price = listing.price - 5000;
    await panel.getByLabel("제안 금액 (원)").fill(String(price));
    // 판매가는 실제 매물에서 읽는다.
    await expect(
      panel.getByText(`판매가 ${listing.price.toLocaleString("ko-KR")}원`, { exact: false }),
    ).toBeVisible();
    await panel.getByRole("button", { name: "제안하기" }).click();

    await expect.poll(() => offerBodies).toEqual([{ roomId: "room-inline", price }]);
    await expect(
      panel.getByText(`${price.toLocaleString("ko-KR")}원 제안 · 판매자 응답을 기다리고 있어요`),
    ).toBeVisible();
  });

  test("인라인 채팅에서 수락을 보면 같은 화면의 구매 버튼도 제안가로 바뀐다", async ({ page }) => {
    const launchResponse = await page.request.get(`${apiBaseUrl}/api/v1/config/launch`);
    const launch = (await launchResponse.json()) as {
      stage: number;
      features: { payments: boolean };
      sellerIdentityVerificationReady: boolean;
    };
    test.skip(
      !(launch.sellerIdentityVerificationReady && launch.features.payments && launch.stage >= 2),
      "결제가 닫힌 단계에서는 구매 버튼이 없다",
    );
    const listing = await findSeedListing(page);
    const offerPrice = listing.price - 7000;
    await seedSession(page, BUYER_ID);
    await isolateDetailRequests(page, BUYER_ID);
    const room = { ...ROOM, id: "room-sync", listingId: listing.id, sellerId: listing.sellerId };
    const base = offer({
      id: "offer-sync",
      roomId: room.id,
      listingId: listing.id,
      sellerId: listing.sellerId,
      price: offerPrice,
      listingPriceAtOffer: listing.price,
    });
    let offers: TestOffer[] = [base];
    let releaseStream: () => void = () => undefined;
    const streamGate = new Promise<void>((resolve) => {
      releaseStream = resolve;
    });
    await page.route("**/api/v1/chat/rooms", (route) => route.fulfill({ json: room }));
    await page.route("**/api/v1/chat/rooms/room-sync/messages**", (route) =>
      route.fulfill({ json: [] }),
    );
    await page.route("**/api/v1/chat/rooms/room-sync/read", (route) =>
      route.fulfill({ status: 204, body: "" }),
    );
    await page.route("**/api/v1/chat/rooms/room-sync/stream**", async (route) => {
      await streamGate;
      offers = [{ ...base, status: "accepted", respondedAt: new Date().toISOString() }];
      await route
        .fulfill({
          status: 200,
          contentType: "text/event-stream",
          body: `data: ${JSON.stringify({
            id: "message-sync",
            senderId: listing.sellerId,
            content: "[가격 제안] 제안을 수락했어요",
            sentAt: new Date().toISOString(),
          })}\n\n`,
        })
        .catch(() => undefined);
    });
    await page.route(/\/api\/v1\/offers(?:\?.*)?$/, (route) => route.fulfill({ json: offers }));

    await page.goto(`/listings/${listing.id}`);
    await expect(page.getByRole("button", { name: "구매하기", exact: true })).toBeVisible();
    await page.getByRole("button", { name: /판매자와 채팅하기/ }).click();
    const panel = page.getByRole("region", { name: "가격 제안" });
    await expect(panel.getByText(/판매자 응답을 기다리고 있어요/)).toBeVisible();

    releaseStream();
    await expect(panel.getByText("위의 구매 버튼이 제안가로 바뀌었어요.")).toBeVisible();
    // 같은 화면에 매물 상세로 가는 링크를 두지 않는다.
    await expect(panel.getByRole("link", { name: "제안가로 구매하기" })).toHaveCount(0);
    await expect(
      page.getByRole("button", {
        name: `제안가 ${offerPrice.toLocaleString("ko-KR")}원으로 구매`,
      }),
    ).toBeVisible({ timeout: 4_000 });
  });

  test("판매자 패널에서 받은 제안을 수락한다", async ({ page }) => {
    const listing = await findSeedListing(page);
    await seedSession(page, listing.sellerId);
    await isolateDetailRequests(page, listing.sellerId);
    let offers: TestOffer[] = [
      offer({
        id: "offer-pending",
        listingId: listing.id,
        sellerId: listing.sellerId,
        price: listing.price - 2000,
        listingPriceAtOffer: listing.price,
      }),
      offer({
        id: "offer-bid",
        listingId: listing.id,
        sellerId: listing.sellerId,
        buyerId: "account-bidder",
        roomId: null,
        origin: "bid",
        price: listing.price - 3000,
        listingPriceAtOffer: listing.price,
        status: "accepted",
        respondedAt: new Date().toISOString(),
      }),
    ];
    const accepted: string[] = [];
    await page.route(/\/api\/v1\/offers(?:[/?].*)?$/, async (route) => {
      const url = new URL(route.request().url());
      if (route.request().method() === "GET") {
        await route.fulfill({ json: offers });
        return;
      }
      const offerId = url.pathname.split("/").at(-2) ?? "";
      accepted.push(offerId);
      offers = offers.map((item) =>
        item.id === offerId
          ? { ...item, status: "accepted", respondedAt: new Date().toISOString() }
          : item,
      );
      await route.fulfill({ json: offers.find((item) => item.id === offerId) });
    });
    // 호가창은 공개 조회지만 이 테스트의 관심사가 아니다 — 입찰 없음으로 고정한다.
    await page.route(`**/api/v1/bids/book/${listing.catalogSetNumber}`, (route) =>
      route.fulfill({ json: { setNumber: listing.catalogSetNumber, conditions: [] } }),
    );

    await page.goto(`/listings/${listing.id}`);
    const section = page.getByTestId("received-offers");
    await expect(section.getByRole("heading", { name: "받은 가격 제안" })).toBeVisible();
    await expect(section.getByText("구매 입찰 · 구매자 account-")).toBeVisible();
    await expect(section.getByRole("link", { name: "대화 보기" })).toHaveAttribute(
      "href",
      `/chat?room=${ROOM_ID}`,
    );

    await section.getByRole("button", { name: "수락", exact: true }).click();
    await expect.poll(() => accepted).toEqual(["offer-pending"]);
    await expect(section.getByRole("button", { name: "수락", exact: true })).toHaveCount(0);
    await expect(section.getByRole("button", { name: "수락 취소" })).toHaveCount(2);
    await expect(page.getByTestId("sell-to-bid")).toContainText(
      "아직 이 세트·상태에 걸린 입찰이 없어요",
    );
  });
});

// 매물 상세는 서버에서 그려지므로 실제 API가 필요하다. CI E2E 잡(E2E_WITH_BACKEND=1)에서 돈다.
// 사전 조건: scripts/seed-e2e-accounts.sh 로 셀러·바이어 세션을 심어야 한다. 매물을 새로 만든다.
test.describe("가격 제안 — 매물 채팅에서 제안가 주문까지", () => {
  test.skip(
    process.env.E2E_WITH_BACKEND !== "1" || externalBaseUrl !== undefined,
    "실제 API가 렌더하는 상세 화면 — 로컬 백엔드 전용(쓰기 발생)",
  );

  async function acceptConsentIfAsked(page: Page): Promise<void> {
    const dialog = page.getByRole("dialog", { name: "제3자 제공 동의가 필요합니다" });
    const asked = await dialog
      .waitFor({ state: "visible", timeout: 3_000 })
      .then(() => true)
      .catch(() => false);
    if (!asked) return;
    await dialog.getByRole("checkbox", { name: /개인정보 제3자 제공에 동의합니다/ }).check();
    await dialog.getByRole("button", { name: "동의하고 계속" }).click();
  }

  test("구매자가 제안하고 판매자가 수락하면 구매자는 그 가격으로 진행한다", async ({ page }) => {
    // 매물 채팅방은 구매자 본인 동의와 함께 판매자 쪽 동의(requireCurrentSubject)도 요구한다. 판매자 동의는
    // 이 흐름의 화면에 경로가 없고, 동의 대화상자 자체는 third-party-provision-consent 스펙이 검증한다.
    // 새로 시드한 DB(CI)에는 두 계정 모두 동의가 없으므로 API로 먼저 기록한다. 동의는 덧붙이기 기록이라 멱등하다.
    await recordThirdPartyConsent(page, E2E_SELLER);
    await recordThirdPartyConsent(page, E2E_BUYER);
    await signInAs(page, E2E_SELLER);
    await page.goto("/sell");

    const title = `E2E 가격 제안 ${Date.now()}`;
    await page.getByLabel("제목").fill(title);
    await page.getByLabel("설명", { exact: true }).fill("E2E 가격 제안 확인용");
    await page.getByLabel("가격 (원)").fill("50000");
    await page.getByLabel("상품 이미지").setInputFiles({
      name: "e2e.png",
      mimeType: "image/png",
      buffer: Buffer.from(
        "iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAEUlEQVR4XmPQqLizBYQZYAwASsIIwRFEXsMAAAAASUVORK5CYII=",
        "base64",
      ),
    });
    await expect(page.getByRole("img", { name: /상품 이미지 1/ })).toBeVisible();
    await page.getByRole("button", { name: "상품 등록" }).click();
    await expect(page).toHaveURL(/\/listings\/[^/]+$/);
    const listingId = new URL(page.url()).pathname.split("/").at(-1) ?? "";
    expect(listingId).not.toBe("");

    // 판매자 패널에 받은 제안 구획이 비어 있다.
    await expect(page.getByTestId("received-offers")).toContainText("아직 받은 제안이 없어요");

    await switchTo(page, E2E_BUYER);
    await page.reload();
    await page.getByRole("button", { name: /거래 문의하기|판매자와 채팅하기/ }).click();
    await acceptConsentIfAsked(page);

    const panel = page.getByRole("region", { name: "가격 제안" });
    await panel.getByRole("button", { name: "가격 제안" }).click();
    await panel.getByLabel("제안 금액 (원)").fill("45000");
    await panel.getByRole("button", { name: "제안하기" }).click();
    await acceptConsentIfAsked(page);
    await expect(panel.getByText("45,000원 제안 · 판매자 응답을 기다리고 있어요")).toBeVisible();

    const roomHref =
      (await page.getByRole("link", { name: "전체 대화에서 보기" }).getAttribute("href")) ?? "";
    expect(roomHref).toMatch(/^\/chat\?room=/);

    await switchTo(page, E2E_SELLER);
    await page.goto(roomHref);
    const sellerPanel = page.getByRole("region", { name: "가격 제안" });
    await expect(sellerPanel.getByText("구매자가 45,000원을 제안했어요")).toBeVisible();
    await sellerPanel.getByRole("button", { name: "수락", exact: true }).click();
    await expect(sellerPanel.getByText("45,000원 제안을 수락했어요")).toBeVisible();

    await switchTo(page, E2E_BUYER);
    await page.goto(`/listings/${listingId}`);
    const offerPurchase = page.getByRole("button", { name: "제안가 45,000원으로 구매" });
    // 결제 단계면 제안 조회가 끝난 뒤 구매 버튼이 바뀌고, 직거래 단계면 안내문이 보인다.
    await expect(offerPurchase.or(page.getByText("지금은 판매자와 직접 거래해요"))).toBeVisible();
    if (await offerPurchase.isVisible()) {
      // 결제 단계: 제안 id를 실어 주문하고 주문 금액이 합의가다.
      await offerPurchase.click();
      await page.getByLabel("CS 연락처").fill("010-1234-5678");
      await page.getByRole("button", { name: "주문하기" }).click();
      await expect(page).toHaveURL(/\/orders\/.+/);
      await expect(page.getByText("₩45,000").first()).toBeVisible();
    } else {
      // 직거래 단계: 채팅 배너가 합의한 가격을 보여 준다.
      await page.getByRole("button", { name: /거래 문의하기|판매자와 채팅하기/ }).click();
      await expect(
        page
          .getByRole("region", { name: "가격 제안" })
          .getByText("합의한 가격 45,000원 — 직거래로 진행해요."),
      ).toBeVisible();
    }
  });
});
