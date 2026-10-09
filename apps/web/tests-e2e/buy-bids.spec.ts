import { expect, test, type Page } from "@playwright/test";
import { E2E_BUYER, E2E_SELLER, signInAs, switchTo } from "./support/e2e-session";

/**
 * 구매 입찰(.kiro/specs/buy-bids F1).
 *
 * 프로필 "입찰" 탭과 알림은 브라우저가 API를 부르므로 응답을 가로채 검증한다 — 실제 API 없이 돈다.
 * 세트 상세의 호가창·입찰 폼과 매물 상세의 "최고 입찰가에 바로 판매"는 서버 렌더 화면이라 맨 아래
 * 실제 백엔드 스펙이 확인한다.
 */

const externalBaseUrl = process.env.E2E_BASE_URL;
const targetsRemoteHost =
  externalBaseUrl !== undefined &&
  !["localhost", "127.0.0.1"].includes(new URL(externalBaseUrl).hostname);

const ACCOUNT_ID = "acc-bidder";
const DAY = 24 * 60 * 60 * 1000;

type BidStatus = "active" | "canceled" | "filled" | "expired";

function bid(id: string, status: BidStatus, overrides: Record<string, unknown> = {}) {
  const placedAt = new Date(Date.now() - 2 * DAY).toISOString();
  return {
    id,
    setNumber: "10307",
    condition: "used_good",
    price: 250000,
    durationDays: 30,
    status,
    createdAt: placedAt,
    placedAt,
    expiresAt: new Date(Date.now() + 28 * DAY).toISOString(),
    filledListingId: null,
    offerId: null,
    ...overrides,
  };
}

const MY_BIDS = [
  bid("bid-active", "active"),
  bid("bid-filled", "filled", {
    setNumber: "75192",
    condition: "new_sealed",
    price: 1200000,
    filledListingId: "listing-filled-1",
    offerId: "offer-from-bid-1",
  }),
  bid("bid-expired", "expired", {
    setNumber: "21318",
    condition: "like_new",
    price: 99000,
    expiresAt: new Date(Date.now() - DAY).toISOString(),
  }),
  bid("bid-canceled", "canceled", { setNumber: "10305", price: 300000 }),
];

async function seedSession(page: Page): Promise<void> {
  // 앱이 새로고침 이후 실제로 갖는 모양 — 토큰은 HttpOnly 쿠키에 있고 여기는 비어 있다.
  await page.addInitScript((id) => {
    window.localStorage.setItem(
      "gole.session",
      JSON.stringify({ accountId: id, sessionToken: "", role: "USER" }),
    );
  }, ACCOUNT_ID);
}

/** 프로필 첫 화면이 채워지는 데 필요한 응답과 레이아웃 요청. 실백엔드 401이 합성 세션을 지우지 않게 한다. */
async function mockProfileShell(page: Page): Promise<void> {
  await page.route(/\/api\/v1\/users\/[^/]+\/notifications\/unread-count(?:\?.*)?$/, (route) =>
    route.fulfill({ json: { unreadCount: 0 } }),
  );
  await page.route("**/api/v1/accounts/me", (route) =>
    route.fulfill({
      json: { accountId: ACCOUNT_ID, email: "bidder@gole.test", role: "USER", nickname: "입찰왕" },
    }),
  );
  await page.route("**/api/v1/accounts/me/onboarding", (route) =>
    route.fulfill({
      json: {
        required: false,
        legacyExempt: true,
        nicknameCompleted: true,
        nickname: "입찰왕",
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
  await page.route("**/api/v1/accounts/me/third-party-provision-consents/current", (route) =>
    route.fulfill({
      json: { noticeVersion: "2026-09-04", consented: false, lastDecisionAt: null },
    }),
  );
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
  await page.route("**/api/v1/orders?buyerId=**", (route) => route.fulfill({ json: [] }));
  await page.route("**/api/v1/orders/sales", (route) => route.fulfill({ json: [] }));
  await page.route("**/api/v1/orders/settlements", (route) => route.fulfill({ json: [] }));
  await page.route("**/api/v1/listings/mine**", (route) => route.fulfill({ json: [] }));
}

test.describe("프로필 — 내 입찰", () => {
  test.skip(targetsRemoteHost, "응답 가로채기 기반 — 로컬 프론트 전용");

  test.beforeEach(async ({ page }) => {
    await seedSession(page);
    await mockProfileShell(page);
  });

  test("탭을 열 때 읽고, 진행 중은 취소·체결은 매물 링크·만료와 취소는 흐리게 보인다", async ({
    page,
  }) => {
    let mineReads = 0;
    const cancelled: string[] = [];
    await page.route("**/api/v1/bids/mine", (route) => {
      mineReads += 1;
      return route.fulfill({ json: MY_BIDS });
    });
    await page.route("**/api/v1/bids/bid-active", (route) => {
      expect(route.request().method()).toBe("DELETE");
      cancelled.push("bid-active");
      return route.fulfill({ status: 204, body: "" });
    });

    await page.goto("/profile");
    await expect(page.getByRole("heading", { name: "입찰왕" })).toBeVisible();
    // 프로필 첫 화면은 입찰을 묻지 않는다 — 탭을 열기 전에는 요청이 없다.
    expect(mineReads).toBe(0);

    await page.getByRole("button", { name: "입찰", exact: true }).click();
    const rows = page.getByTestId("my-bid");
    await expect(rows).toHaveCount(4);

    const active = rows.filter({ hasText: "#10307" });
    await expect(active.getByText("진행 중", { exact: true })).toBeVisible();
    await expect(active.getByText("250,000원", { exact: true })).toBeVisible();
    await expect(active.getByRole("link", { name: "#10307 · 중고-양호" })).toHaveAttribute(
      "href",
      "/sets/10307",
    );

    const filled = rows.filter({ hasText: "#75192" });
    await expect(filled.getByText("체결됨", { exact: true })).toBeVisible();
    await expect(filled.getByRole("link", { name: "매물 보기" })).toHaveAttribute(
      "href",
      "/listings/listing-filled-1",
    );
    await expect(filled.getByRole("button", { name: "입찰 취소" })).toHaveCount(0);

    const expired = rows.filter({ hasText: "#21318" });
    await expect(expired.getByText("만료됨", { exact: true })).toBeVisible();
    await expect(expired).toHaveClass(/opacity-60/);
    await expect(rows.filter({ hasText: "#10305" })).toHaveClass(/opacity-60/);
    await expect(active).not.toHaveClass(/opacity-60/);

    await active.getByRole("button", { name: "입찰 취소" }).click();
    await expect.poll(() => cancelled).toEqual(["bid-active"]);
    await expect(active.getByText("취소됨", { exact: true })).toBeVisible();
    await expect(active.getByText("취소한 입찰이에요")).toBeVisible();
    await expect(active).toHaveClass(/opacity-60/);
    await expect(active.getByRole("button", { name: "입찰 취소" })).toHaveCount(0);
  });

  test("이미 끝난 입찰을 취소하려 하면 이유를 그 줄에 보여 준다", async ({ page }) => {
    await page.route("**/api/v1/bids/mine", (route) =>
      route.fulfill({ json: [bid("bid-active", "active")] }),
    );
    await page.route("**/api/v1/bids/bid-active", (route) =>
      route.fulfill({ status: 409, json: { code: "BID_NOT_ACTIVE", message: "not active" } }),
    );

    await page.goto("/profile");
    await page.getByRole("button", { name: "입찰", exact: true }).click();
    await page.getByRole("button", { name: "입찰 취소" }).click();

    await expect(page.getByTestId("my-bid").getByText("이미 끝난 입찰이에요.")).toBeVisible();
  });

  test("입찰이 없으면 세트를 찾으러 가게 하고, 못 불러오면 다시 시도할 수 있다", async ({
    page,
  }) => {
    let healthy = false;
    await page.route("**/api/v1/bids/mine", (route) =>
      healthy
        ? route.fulfill({ json: [] })
        : route.fulfill({ status: 503, json: { code: "UNAVAILABLE", message: "down" } }),
    );

    await page.goto("/profile");
    await page.getByRole("button", { name: "입찰", exact: true }).click();
    await expect(page.getByText("입찰을 불러오지 못했어요")).toBeVisible();

    healthy = true;
    await page.getByRole("button", { name: "다시 시도" }).click();
    await expect(page.getByText("걸어 둔 입찰이 없어요")).toBeVisible();
    await expect(page.getByRole("link", { name: "시세에서 세트 찾기" })).toHaveAttribute(
      "href",
      "/prices",
    );
  });
});

test.describe("구매 입찰 알림", () => {
  test.skip(targetsRemoteHost, "응답 가로채기 기반 — 로컬 프론트 전용");

  test("체결·매칭 알림은 매물 상세로 이어진다", async ({ page }) => {
    await seedSession(page);
    await mockProfileShell(page);
    await page.route(`**/api/v1/users/${ACCOUNT_ID}/notifications`, (route) =>
      route.fulfill({
        json: [
          {
            id: "notification-bid-filled",
            type: "BID_FILLED",
            message:
              "판매자가 입찰가 1,200,000원에 75192 판매를 수락했어요. 72시간 안에 진행해 주세요",
            link: "/listings/listing-filled-1",
            read: false,
            createdAt: "2026-10-09T00:00:00Z",
          },
          {
            id: "notification-bid-match",
            type: "BID_LISTING_MATCHED",
            message: "입찰가 이하 매물이 올라왔어요: 에펠탑 240,000원",
            link: "/listings/listing-match-1",
            read: false,
            createdAt: "2026-10-09T00:01:00Z",
          },
        ],
      }),
    );

    await page.goto("/notifications");

    await expect(page.getByRole("link", { name: /75192 판매를 수락했어요/ })).toHaveAttribute(
      "href",
      "/listings/listing-filled-1",
    );
    await expect(page.getByRole("link", { name: /입찰가 이하 매물이 올라왔어요/ })).toHaveAttribute(
      "href",
      "/listings/listing-match-1",
    );
  });
});

const apiBaseUrl = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

interface SeedListing {
  readonly id: string;
  readonly sellerId: string;
  readonly price: number;
  readonly condition: string;
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

function emptyBook(setNumber: string) {
  return {
    setNumber,
    conditions: ["new_sealed", "like_new", "used_good", "used_fair", "damaged"].map(
      (condition) => ({ condition, highestPrice: null, bidCount: 0, levels: [] }),
    ),
  };
}

/** 상세 화면이 브라우저에서 부르는 세션 필요 조회. 합성 세션이 실백엔드 401로 지워지지 않게 한다. */
async function isolateDetailRequests(page: Page, accountId: string): Promise<void> {
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
  await page.route("**/api/v1/accounts/me", (route) =>
    route.fulfill({ json: { accountId, email: "e2e@gole.test", role: "USER" } }),
  );
  await page.route(/\/api\/v1\/users\/[^/]+\/wishlist(?:[/?].*)?$/, (route) =>
    route.request().method() === "GET"
      ? route.fulfill({ json: [] })
      : route.fulfill({ status: 204, body: "" }),
  );
  await page.route(/\/api\/v1\/offers(?:\?.*)?$/, (route) => route.fulfill({ json: [] }));
}

// 세트·매물 상세는 실제 API가 서버에서 그리고, 그 위에서 브라우저가 부르는 입찰 요청만 가로챈다 —
// 쓰기는 실제 API로 나가지 않는다. 카탈로그·시드 매물이 있는 백엔드가 필요하므로 E2E_WITH_BACKEND=1 에서 돈다.
test.describe("세트·매물 상세 — 호가창·입찰 폼·즉시 판매 (응답 가로채기)", () => {
  test.skip(
    process.env.E2E_WITH_BACKEND !== "1" || targetsRemoteHost,
    "실제 API가 렌더하는 세트·매물 상세 — 로컬 백엔드 전용",
  );

  test("비로그인 방문자는 상태별 호가창을 보고 로그인한 뒤 입찰하러 온다", async ({ page }) => {
    await page.goto("/sets/10307");

    await expect(page.getByRole("heading", { name: "구매 입찰" })).toBeVisible();
    const rows = page.getByTestId("bid-book-row");
    await expect(rows).toHaveCount(5);
    await expect(rows.first()).toContainText("미개봉");
    await expect(rows.first()).toContainText("지금 팔면 받는 값");
    await expect(page.getByRole("link", { name: "로그인하고 입찰하기" })).toHaveAttribute(
      "href",
      `/login?returnTo=${encodeURIComponent("/sets/10307")}`,
    );
    await expect(page.getByRole("form", { name: "입찰하기" })).toHaveCount(0);
  });

  test("입찰을 걸면 서버 규칙대로 보내고 호가창을 다시 읽는다", async ({ page }) => {
    await seedSession(page);
    await isolateDetailRequests(page, ACCOUNT_ID);
    const placed: unknown[] = [];
    let bookReads = 0;
    await page.route("**/api/v1/bids", async (route) => {
      const body = route.request().postDataJSON() as Record<string, unknown>;
      placed.push(body);
      await route.fulfill({
        json: bid("bid-new", "active", {
          condition: body["condition"],
          price: body["price"],
          durationDays: body["durationDays"],
        }),
      });
    });
    await page.route("**/api/v1/bids/book/10307", async (route) => {
      bookReads += 1;
      const book = emptyBook("10307");
      await route.fulfill({
        json: {
          ...book,
          conditions: book.conditions.map((row) =>
            row.condition === "used_good"
              ? {
                  condition: "used_good",
                  highestPrice: 260000,
                  bidCount: 2,
                  levels: [
                    { price: 260000, count: 1 },
                    { price: 250000, count: 1 },
                  ],
                }
              : row,
          ),
        },
      });
    });

    await page.goto("/sets/10307");
    const form = page.getByRole("form", { name: "입찰하기" });
    await form.getByLabel("상태").selectOption("used_good");
    await form.getByLabel("입찰가 (원)").fill("0");
    await form.getByRole("button", { name: "입찰하기" }).click();
    await expect(form.getByText("입찰가는 1원 이상 1억 원 이하로 입력해 주세요.")).toBeVisible();
    expect(placed).toEqual([]);

    await form.getByLabel("입찰가 (원)").fill("260000");
    await form.getByLabel("기간").selectOption("60");
    await form.getByRole("button", { name: "입찰하기" }).click();

    await expect
      .poll(() => placed)
      .toEqual([{ setNumber: "10307", condition: "used_good", price: 260000, durationDays: 60 }]);
    await expect(
      form.getByText("중고-양호 상태에 260,000원 입찰을 걸었어요.", { exact: false }),
    ).toBeVisible();
    await expect.poll(() => bookReads).toBeGreaterThan(0);
    const usedGood = page.locator('[data-testid="bid-book-row"][data-condition="used_good"]');
    await expect(usedGood).toContainText("입찰 2건");
    await expect(usedGood.getByRole("list", { name: "중고-양호 호가" })).toContainText("250,000원");
  });

  /** 판매자 화면을 띄우고 그 세트·상태에 `highest` 입찰이 걸린 호가창과 체결·수정 요청을 가로챈다. */
  async function openSellerListingWithBid(page: Page, bidOverAsk: number) {
    const listing = await findSeedListing(page);
    const setNumber = listing.catalogSetNumber ?? "";
    const highest = listing.price + bidOverAsk;
    await page.addInitScript((id) => {
      window.localStorage.setItem(
        "gole.session",
        JSON.stringify({ accountId: id, sessionToken: "", role: "USER" }),
      );
    }, listing.sellerId);
    await isolateDetailRequests(page, listing.sellerId);
    await page.route(`**/api/v1/bids/book/${setNumber}`, (route) => {
      const book = emptyBook(setNumber);
      return route.fulfill({
        json: {
          ...book,
          conditions: book.conditions.map((row) =>
            row.condition === listing.condition
              ? {
                  condition: listing.condition,
                  highestPrice: highest,
                  bidCount: 3,
                  levels: [{ price: highest, count: 1 }],
                }
              : row,
          ),
        },
      });
    });
    const calls: string[] = [];
    const revisions: Array<{ price: number }> = [];
    await page.route(`**/api/v1/listings/${listing.id}`, async (route) => {
      if (route.request().method() !== "PUT") return route.fallback();
      calls.push("PUT");
      const body = route.request().postDataJSON() as { price: number };
      revisions.push({ price: body.price });
      await route.fulfill({ json: { ...listing, price: body.price } });
    });
    await page.route(`**/api/v1/bids/book/${setNumber}/fill`, async (route) => {
      calls.push("FILL");
      await route.fulfill({
        json: { bidPrice: highest, offerId: "offer-from-fill", listingId: listing.id },
      });
    });
    await page.goto(`/listings/${listing.id}`);
    return { listing, highest, calls, revisions, section: page.getByTestId("sell-to-bid") };
  }

  test("입찰가가 판매가보다 높으면 판매가를 입찰가로 올린 뒤 판다", async ({ page }) => {
    const { listing, highest, calls, revisions, section } = await openSellerListingWithBid(
      page,
      20000,
    );
    const highestLabel = `${highest.toLocaleString("ko-KR")}원`;
    const askLabel = `${listing.price.toLocaleString("ko-KR")}원`;
    await expect(section).toContainText(`입찰 3건 · 최고 입찰가 ${highestLabel}`);
    // 주문 금액은 min(입찰가, 판매가)라서 "입찰가에 판매"만 쓰면 판매자가 입찰가를 받는다고 오해한다.
    await expect(section).toContainText(`지금 판매가 ${askLabel}보다 20,000원 높아요`);
    const raise = section.getByRole("button", {
      name: `판매가를 ${highestLabel}으로 올리고 바로 판매`,
    });

    // 확인 대화상자에서 물러나면 가격도 바꾸지 않고 팔지도 않는다.
    page.once("dialog", (dialog) => void dialog.dismiss());
    await raise.click();
    expect(calls).toEqual([]);

    page.once("dialog", (dialog) => {
      expect(dialog.message()).toContain(`판매가를 ${highestLabel}으로 올리고`);
      expect(dialog.message()).toContain(`주문 금액은 ${highestLabel}`);
      void dialog.accept();
    });
    await raise.click();

    await expect.poll(() => calls).toEqual(["PUT", "FILL"]);
    expect(revisions).toEqual([{ price: highest }]);
    await expect(
      section.getByText(`입찰가 ${highestLabel}에 판매를 수락했어요.`, { exact: false }),
    ).toBeVisible();
    await expect(section.getByRole("button", { name: /바로 판매|그대로 판매/ })).toHaveCount(0);
  });

  test("입찰가가 판매가보다 높아도 지금 판매가 그대로 팔 수 있다", async ({ page }) => {
    const { listing, highest, calls, section } = await openSellerListingWithBid(page, 20000);
    const askLabel = `${listing.price.toLocaleString("ko-KR")}원`;

    page.once("dialog", (dialog) => {
      expect(dialog.message()).toContain(`주문 금액은 지금 판매가 ${askLabel}`);
      void dialog.accept();
    });
    await section.getByRole("button", { name: `판매가 ${askLabel} 그대로 판매` }).click();

    await expect.poll(() => calls).toEqual(["FILL"]);
    await expect(
      section.getByText(`주문 금액은 판매가 ${askLabel}이에요.`, { exact: false }),
    ).toBeVisible();
    expect(highest).toBeGreaterThan(listing.price);
  });

  test("입찰가가 판매가 이하면 최고 입찰가에 바로 판다", async ({ page }) => {
    const { highest, calls, section } = await openSellerListingWithBid(page, -10000);
    const highestLabel = `${highest.toLocaleString("ko-KR")}원`;

    await expect(section.getByRole("button", { name: /올리고/ })).toHaveCount(0);
    page.once("dialog", (dialog) => void dialog.accept());
    await section.getByRole("button", { name: `최고 입찰가 ${highestLabel}에 바로 판매` }).click();

    await expect.poll(() => calls).toEqual(["FILL"]);
    await expect(
      section.getByText(`입찰가 ${highestLabel}에 판매를 수락했어요.`, { exact: false }),
    ).toBeVisible();
  });

  test("받을 입찰이 없으면 즉시 판매 실패 이유를 알려 준다", async ({ page }) => {
    const listing = await findSeedListing(page);
    const setNumber = listing.catalogSetNumber ?? "";
    await page.addInitScript((id) => {
      window.localStorage.setItem(
        "gole.session",
        JSON.stringify({ accountId: id, sessionToken: "", role: "USER" }),
      );
    }, listing.sellerId);
    await isolateDetailRequests(page, listing.sellerId);
    await page.route(`**/api/v1/bids/book/${setNumber}`, (route) => {
      const book = emptyBook(setNumber);
      return route.fulfill({
        json: {
          ...book,
          conditions: book.conditions.map((row) =>
            row.condition === listing.condition
              ? { condition: listing.condition, highestPrice: 1000, bidCount: 1, levels: [] }
              : row,
          ),
        },
      });
    });
    await page.route(`**/api/v1/bids/book/${setNumber}/fill`, (route) =>
      route.fulfill({
        status: 409,
        json: { code: "BID_NOT_FOUND", message: "no matching bid" },
      }),
    );

    await page.goto(`/listings/${listing.id}`);
    page.once("dialog", (dialog) => void dialog.accept());
    await page
      .getByTestId("sell-to-bid")
      .getByRole("button", { name: /바로 판매/ })
      .click();

    await expect(
      page.getByText("지금 이 세트·상태에 받을 수 있는 입찰이 없어요. 내가 건 입찰은 제외돼요."),
    ).toBeVisible();
  });
});

// 세트 상세·매물 상세는 서버에서 그려지므로 실제 API가 필요하다. CI E2E 잡(E2E_WITH_BACKEND=1)에서 돈다.
// 사전 조건: scripts/seed-e2e-accounts.sh 로 셀러·바이어 세션을 심어야 한다. 입찰과 매물을 새로 만든다.
test.describe("구매 입찰 — 세트 상세 입찰부터 판매자 즉시 판매까지", () => {
  test.skip(
    process.env.E2E_WITH_BACKEND !== "1" || externalBaseUrl !== undefined,
    "실제 API가 렌더하는 세트·매물 상세 — 로컬 백엔드 전용(쓰기 발생)",
  );

  test("구매자가 입찰을 걸고, 판매자가 최고 입찰가에 팔면 입찰이 체결된다", async ({ page }) => {
    const apiBaseUrl = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";
    // 카탈로그에 실제로 있는 세트여야 한다(D2: 없으면 404). 홈 추천 세트는 카탈로그에서 나온다.
    const response = await page.request.get(`${apiBaseUrl}/api/v1/catalog/sets/featured`);
    expect(response.ok()).toBeTruthy();
    const featured = (await response.json()) as Array<{ setNumber: string }>;
    const setNumber = featured[0]?.setNumber ?? "";
    expect(setNumber, "카탈로그 추천 세트가 하나 이상 필요합니다").not.toBe("");
    // 앞선 실행이 남긴 입찰보다 높아야 판매자가 이 입찰을 받는다. 시각으로 매번 올린다.
    const bidPrice = 50_000_000 + (Math.floor(Date.now() / 1000) % 40_000_000);
    const bidLabel = `${bidPrice.toLocaleString("ko-KR")}원`;

    await signInAs(page, E2E_BUYER);
    await page.goto(`/sets/${setNumber}`);
    await expect(page.getByRole("heading", { name: "구매 입찰" })).toBeVisible();
    await expect(page.getByTestId("bid-book-row")).toHaveCount(5);

    const form = page.getByRole("form", { name: "입찰하기" });
    await form.getByLabel("상태").selectOption("used_good");
    await form.getByLabel("입찰가 (원)").fill(String(bidPrice));
    await form.getByLabel("기간").selectOption("7");
    await form.getByRole("button", { name: "입찰하기" }).click();
    await expect(form.getByText(/입찰을 걸었어요|입찰가를 .*바꿨어요/)).toBeVisible();
    const usedGood = page.locator('[data-testid="bid-book-row"][data-condition="used_good"]');
    await expect(usedGood).toContainText(bidLabel);

    // 판매자가 같은 세트·상태로 매물을 올린다.
    await switchTo(page, E2E_SELLER);
    await page.goto("/sell");
    await page.getByLabel("제목").fill(`E2E 입찰 판매 ${Date.now()}`);
    await page.getByLabel("설명", { exact: true }).fill("E2E 구매 입찰 확인용");
    await page.getByLabel("가격 (원)").fill("12345");
    await page.getByLabel("상품 상태").selectOption("used_good");
    await page.getByLabel("브릭 세트 번호 (선택)").fill(setNumber);
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

    const sellToBid = page.getByTestId("sell-to-bid");
    // 입찰가가 판매가(12,345원)보다 높으니 판매가를 입찰가로 올린 뒤 판다(실제 PUT → 체결).
    page.once("dialog", (dialog) => void dialog.accept());
    await sellToBid
      .getByRole("button", { name: `판매가를 ${bidLabel}으로 올리고 바로 판매` })
      .click();
    await expect(sellToBid.getByText(`입찰가 ${bidLabel}에 판매를 수락했어요`)).toBeVisible();
    // 판매가를 입찰가로 올렸으니 주문 금액 상한 안내는 붙지 않는다.
    await expect(sellToBid.getByText("주문 금액은 판매가", { exact: false })).toHaveCount(0);
    await expect(page.getByTestId("received-offers")).toContainText("구매 입찰");

    // 구매자의 입찰은 체결되어 그 매물로 이어진다.
    await switchTo(page, E2E_BUYER);
    await page.goto("/profile");
    await page.getByRole("button", { name: "입찰", exact: true }).click();
    const mine = page.getByTestId("my-bid").filter({ hasText: bidLabel });
    await expect(mine.getByText("체결됨", { exact: true })).toBeVisible();
    await expect(mine.getByRole("link", { name: "매물 보기" })).toHaveAttribute(
      "href",
      `/listings/${listingId}`,
    );
  });
});
