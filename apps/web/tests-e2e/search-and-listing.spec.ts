import { test, expect } from "@playwright/test";

const externalBaseUrl = process.env.E2E_BASE_URL;
const apiBaseUrl = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8090";
const targetsRemoteHost =
  externalBaseUrl !== undefined &&
  !["localhost", "127.0.0.1"].includes(new URL(externalBaseUrl).hostname);

// 탐색 → 카테고리 필터 → 상세(갤러리 + 시세) 흐름. (데이터가 있는 환경 대상)
test.describe("Search & listing detail", () => {
  test("탐색 페이지에 필터(카테고리 포함)와 목록이 보인다", async ({ page }) => {
    await page.goto("/search");
    await expect(page.getByRole("heading", { name: "상품 탐색" })).toBeVisible();
    await expect(page.getByLabel("카테고리")).toBeVisible();
    await expect(page.getByTestId("listing-card").first()).toBeVisible();
  });

  test("카테고리로 필터링한다", async ({ page }) => {
    await page.goto("/search");
    await page.getByLabel("카테고리").selectOption("parts");
    await page.getByRole("button", { name: "검색" }).click();
    await expect(page).toHaveURL(/category=parts/);
  });

  test("예전 상태 링크도 현재 5단계 등급으로 안전하게 이어진다", async ({ page }) => {
    test.skip(targetsRemoteHost, "로컬 시드 매물을 사용하는 레거시 URL 회귀 테스트");

    await page.goto("/search?condition=used_complete");

    await expect(page.getByLabel("상태")).toHaveValue("used_good");
    await expect(page.getByTestId("listing-card").first()).toContainText("중고-양호");
  });

  test("사용감 있는 매물의 구성과 누락 부품을 구매 전에 고지한다", async ({ page }) => {
    test.skip(targetsRemoteHost, "로컬 시드 매물과 API를 사용하는 상태 고지 회귀 테스트");

    const response = await page.request.get(`${apiBaseUrl}/api/v1/listings?condition=USED_FAIR`);
    expect(response.ok()).toBeTruthy();
    const listings = (await response.json()) as Array<{
      id: string;
      condition: string;
      completeness: string;
      hasMissingParts: boolean;
      missingPartsNote: string;
    }>;
    const disclosed = listings.find(
      (listing) =>
        listing.condition === "used_fair" &&
        listing.completeness === "bulk" &&
        listing.hasMissingParts &&
        listing.missingPartsNote.length > 0,
    );
    expect(disclosed, "사용감·벌크·누락 고지가 있는 시드 매물이 필요합니다").toBeDefined();

    await page.goto(`/listings/${disclosed!.id}`);

    await expect(page.getByText("중고-사용감", { exact: true })).toBeVisible();
    await expect(page.getByText("벌크(부품)", { exact: true }).first()).toBeVisible();
    await expect(page.getByText("부품 누락", { exact: true })).toBeVisible();
    await expect(
      page.getByText(`누락: ${disclosed!.missingPartsNote}`, { exact: true }),
    ).toBeVisible();
    await expect(page.getByText("구성: 벌크(부품)", { exact: true })).toBeVisible();
  });

  test("매물 카드를 클릭하면 상세(갤러리·설명)가 보인다", async ({ page }) => {
    await page.goto("/search");
    await page.getByTestId("listing-card").first().click();
    await expect(page).toHaveURL(/\/listings\//);
    await expect(page.getByText("상품 설명")).toBeVisible();
  });

  test("비로그인 거래 문의는 로그인 뒤 같은 매물 채팅을 자동으로 다시 연다", async ({ page }) => {
    test.skip(targetsRemoteHost, "로컬 시드 매물을 사용하는 회귀 테스트");
    await page.goto("/search");
    await page.getByTestId("listing-card").first().click();
    await expect(page).toHaveURL(/\/listings\/[^/?]+$/);

    const listingPath = new URL(page.url()).pathname;
    const chatLogin = page.getByRole("link", { name: /로그인하고 .*문의|로그인하고 .*채팅/ });
    await expect(chatLogin).toHaveAttribute(
      "href",
      `/login?returnTo=${encodeURIComponent(`${listingPath}?chat=1`)}`,
    );
  });

  test("인라인 거래 문의에서 전체 대화 화면으로 이어진다", async ({ page }) => {
    test.skip(targetsRemoteHost, "로컬 시드 매물과 응답 가로채기를 사용하는 회귀 테스트");
    await page.route("**/api/v1/accounts/me", (route) =>
      route.fulfill({
        json: { accountId: "chat-e2e-buyer", email: "buyer@gole.test", role: "USER" },
      }),
    );
    await page.route("**/api/v1/users/chat-e2e-buyer/notifications/unread-count", (route) =>
      route.fulfill({ json: { unreadCount: 0 } }),
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
    await page.addInitScript(() => {
      window.localStorage.setItem(
        "gole.session",
        JSON.stringify({ accountId: "chat-e2e-buyer", sessionToken: "", role: "USER" }),
      );
    });
    await page.route("**/api/v1/chat/rooms", async (route) => {
      if (route.request().method() !== "POST") {
        await route.fallback();
        return;
      }
      const body = route.request().postDataJSON() as {
        listingId: string;
        buyerId: string;
        sellerId: string;
      };
      await route.fulfill({
        json: {
          id: "listing-room-e2e",
          listingId: body.listingId,
          buyerId: body.buyerId,
          sellerId: body.sellerId,
          createdAt: "2026-09-02T00:00:00Z",
          lastMessageAt: "2026-09-02T00:00:00Z",
          buyerConfirmedAt: null,
          sellerConfirmedAt: null,
          directTradeCompletedAt: null,
        },
      });
    });
    await page.route("**/api/v1/chat/rooms/listing-room-e2e/messages**", (route) =>
      route.fulfill({ json: [] }),
    );
    await page.route("**/api/v1/chat/rooms/listing-room-e2e/stream**", (route) =>
      route.fulfill({ status: 200, contentType: "text/event-stream", body: "" }),
    );

    await page.goto("/search");
    await page.getByTestId("listing-card").first().click();
    await expect(page).toHaveURL(/\/listings\/[^/?]+$/);
    await page.getByRole("button", { name: /거래 문의하기|판매자와 채팅하기/ }).click();

    await expect(page.getByText("판매자와 거래 대화")).toBeVisible();
    await expect(page.getByRole("link", { name: "전체 대화에서 보기" })).toHaveAttribute(
      "href",
      "/chat?room=listing-room-e2e&source=listing",
    );
  });
});
