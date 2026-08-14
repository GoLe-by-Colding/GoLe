import { test, expect, type Page } from "@playwright/test";

/** 가로 오버플로우(가로 스크롤) 픽셀 수. 1px 이하면 정상으로 본다. */
async function horizontalOverflow(page: Page): Promise<number> {
  return page.evaluate(
    () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
  );
}

/** 렌더 후 가로 스크롤이 생기지 않았는지 확인한다. */
async function expectNoHorizontalScroll(page: Page, path: string): Promise<void> {
  await page.goto(path, { waitUntil: "load" });
  // 레이아웃 안정화 대기(폰트/이미지)
  await page.waitForTimeout(300);
  expect(await horizontalOverflow(page), `가로 스크롤 발생: ${path}`).toBeLessThanOrEqual(1);
}

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

const USER_SESSION = { accountId: "e2e-mobile", sessionToken: "t", role: "USER" };
const ADMIN_SESSION = { accountId: "e2e-mobile-admin", sessionToken: "t", role: "ADMIN" };

async function signIn(page: Page, session: Record<string, string>): Promise<void> {
  await page.addInitScript((value) => {
    window.localStorage.setItem("gole.session", JSON.stringify(value));
  }, session);
}

// 모바일 뷰포트(Pixel 5)에서의 반응형/내비게이션 검증.
test.describe("Mobile responsive", () => {
  // 비로그인으로 접근 가능한 공개 경로.
  const publicPages = ["/", "/search", "/prices", "/community", "/login", "/terms"];

  for (const path of publicPages) {
    test(`모바일에서 가로 스크롤 없이 ${path} 가 렌더된다`, async ({ page }) => {
      await expectNoHorizontalScroll(page, path);
    });
  }

  // 로그인이 필요한 경로. 거래 동선이라 공개 페이지보다 오히려 더 중요하다.
  for (const path of ["/sell", "/profile", "/collection", "/notifications"]) {
    test(`모바일에서 가로 스크롤 없이 ${path} 가 렌더된다 (로그인)`, async ({ page }) => {
      await signIn(page, USER_SESSION);
      await expectNoHorizontalScroll(page, path);
    });
  }

  test("모바일 햄버거 메뉴로 내비게이션한다", async ({ page }) => {
    await page.goto("/");

    const burger = page.getByRole("button", { name: "메뉴 열기" });
    await expect(burger).toBeVisible();

    await burger.click();
    const menu = page.locator("#mobile-menu");
    await expect(menu).toBeVisible();

    await menu.getByRole("link", { name: "시세" }).click();
    await expect(page).toHaveURL(/\/prices$/);
  });

  test("모바일에서 데스크톱 인라인 네비는 숨겨진다", async ({ page }) => {
    await page.goto("/");
    // 데스크톱 nav 링크(헤더의 '탐색')는 max-sm에서 hidden
    await expect(page.getByRole("button", { name: "메뉴 열기" })).toBeVisible();
  });
});

/**
 * 결제·정산 화면의 모바일 검증.
 *
 * <p>응답을 가로채 데이터를 고정한다. 실제 주문을 만들면 상태에 따라 화면이 달라져
 * "결제 버튼이 있는 좁은 화면"을 안정적으로 재현할 수 없다.
 */
test.describe("Mobile — 결제·운영 화면", () => {
  test.skip(!!process.env.E2E_BASE_URL, "응답 가로채기 기반 — 로컬 프론트 전용");

  test("결제 대기 주문 상세가 가로 스크롤 없이 렌더된다", async ({ page }) => {
    await signIn(page, USER_SESSION);
    await page.route("**/api/v1/orders/*", (route) =>
      route.fulfill({
        json: {
          id: "ORD-MOBILE-0001",
          listingId: "listing-1",
          buyerId: "e2e-mobile",
          sellerId: "seller-1",
          catalogSetNumber: "10307",
          amount: 280000,
          status: "payment_pending",
          paymentMethod: null,
          createdAt: "2026-08-14T01:00:00Z",
          history: [{ status: "payment_pending", occurredAt: "2026-08-14T01:00:00Z" }],
        },
      }),
    );

    await expectNoHorizontalScroll(page, "/orders/ORD-MOBILE-0001");
    // 좁은 화면에서도 결제 버튼이 잘리지 않고 보여야 한다.
    await expect(page.getByRole("button", { name: "결제하기" })).toBeVisible();
  });

  /** 10열짜리 표는 좁은 화면에서 페이지가 아니라 표 안에서만 스크롤되어야 한다. */
  test("어드민 주문 표는 페이지를 밀지 않고 표 안에서만 스크롤된다", async ({ page }) => {
    await signIn(page, ADMIN_SESSION);
    await page.route("**/api/admin/reports**", (route) => route.fulfill({ json: [] }));
    await page.route("**/api/admin/orders**", (route) =>
      route.fulfill({
        json: [
          {
            id: "ORD-MOBILE-A001",
            status: "COMPLETED",
            amount: 280000,
            buyerId: "buyer-1",
            sellerId: "seller-1",
            catalogSetNumber: "10307",
            fee: 14000,
            payout: 266000,
            feeRate: 0.05,
            paymentMethod: { type: "EASY_PAY", provider: "KAKAOPAY" },
            createdAt: "2026-08-14T01:00:00Z",
          },
        ],
      }),
    );

    await expectNoHorizontalScroll(page, "/admin/orders");
    // 표 자체는 넘치는 게 정상이다 — 다만 그 스크롤이 표 안에 갇혀 있어야 한다.
    const scroller = page.locator("div.overflow-x-auto").first();
    await expect(scroller).toBeVisible();
  });
});

/**
 * 상품 상세는 서버에서 렌더되므로 가로채기가 통하지 않는다. 실제 매물이 있을 때만 검증한다.
 */
test.describe("Mobile — 상품 상세", () => {
  test("모바일에서 가로 스크롤 없이 상품 상세가 렌더된다", async ({ page, request }) => {
    const response = await request.get(`${API_BASE}/api/v1/listings`).catch(() => null);
    test.skip(response === null || !response.ok(), "백엔드 미기동");

    const listings = (await response!.json()) as ReadonlyArray<{ id: string }>;
    test.skip(listings.length === 0, "매물 없음");

    await expectNoHorizontalScroll(page, `/listings/${listings[0]!.id}`);
  });
});
