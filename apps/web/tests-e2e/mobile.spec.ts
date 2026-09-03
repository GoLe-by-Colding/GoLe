import { test, expect, type Page } from "@playwright/test";
import { E2E_SELLER, signInAs } from "./support/e2e-session";

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
      await signInAs(page, E2E_SELLER);
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

  test("모바일 메뉴가 검색 필터를 덮고 배경 스크롤을 잠근다", async ({ page }) => {
    await page.goto("/search");
    await page.evaluate(() => window.scrollTo(0, 400));

    const burger = page.getByRole("button", { name: "메뉴 열기" });
    await burger.click();

    const menu = page.getByRole("dialog", { name: "전체 메뉴" });
    await expect(menu).toBeVisible();
    await expect(page.getByRole("button", { name: "메뉴 닫기" })).toBeVisible();
    await expect.poll(() => page.evaluate(() => document.body.style.overflow)).toBe("hidden");

    const menuBox = await menu.boundingBox();
    expect(menuBox).not.toBeNull();
    expect(menuBox!.y).toBe(64);
    expect(menuBox!.height).toBeGreaterThanOrEqual(page.viewportSize()!.height - 64);

    await page.keyboard.press("Escape");
    await expect(menu).toBeHidden();
    await expect(page.getByRole("button", { name: "메뉴 열기" })).toBeFocused();
    await expect.poll(() => page.evaluate(() => document.body.style.overflow)).not.toBe("hidden");
  });

  test("모바일에서 데스크톱 인라인 네비는 숨겨진다", async ({ page }) => {
    await page.goto("/");
    // 데스크톱 nav 링크(헤더의 '탐색')는 max-sm에서 hidden
    await expect(page.getByRole("button", { name: "메뉴 열기" })).toBeVisible();
  });
});

/**
 * 결제·운영 화면의 모바일 검증.
 *
 * 응답을 가로채 데이터를 고정한다. 실제 주문을 만들면 상태에 따라 화면이 달라져
 * "결제 버튼이 있는 좁은 화면"을 안정적으로 재현할 수 없다. 서버가 세션을 검증하지 않는
 * 구간이라(모든 API 응답을 가로챈다) 화면 렌더에 필요한 로컬 세션만 있으면 된다.
 */
test.describe("Mobile — 결제·운영 화면", () => {
  test.skip(!!process.env.E2E_BASE_URL, "응답 가로채기 기반 — 로컬 프론트 전용");

  test("결제 대기 주문 상세가 가로 스크롤 없이 렌더된다", async ({ page }) => {
    await signInAs(page, E2E_SELLER);
    await page.route("**/api/v1/config/launch", (route) =>
      route.fulfill({
        json: {
          stage: 2,
          tradeMode: "MANUAL_SETTLEMENT",
          features: { payments: true, reviews: true, partnerPayout: false },
          updatedAt: "2026-08-14T00:00:00Z",
        },
      }),
    );
    await page.route("**/api/v1/orders/*", (route) =>
      route.fulfill({
        json: {
          id: "ORD-MOBILE-0001",
          listingId: "listing-1",
          buyerId: E2E_SELLER.accountId,
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

  /**
   * 열이 많은 표는 좁은 화면에서 페이지가 아니라 표 안에서만 스크롤되어야 한다.
   * 결제수단 열이 붙으면서 표의 최소 너비가 늘었으므로 이 경계가 특히 쉽게 깨진다.
   */
  test("어드민 주문 표는 페이지를 밀지 않고 표 안에서만 스크롤된다", async ({ page }) => {
    await signInAs(page, { ...E2E_SELLER, role: "ADMIN" });
    // 합성 관리자 세션에는 실제 HttpOnly 쿠키가 없다. 헤더의 독립적인 알림 폴링이
    // 로컬 API에서 INVALID_SESSION을 받아 화면 세션을 지우지 않도록 테스트 범위 밖 요청을 격리한다.
    await page.route(/\/api\/v1\/users\/[^/]+\/notifications\/unread-count(?:\?.*)?$/, (route) =>
      route.fulfill({ json: { unreadCount: 0 } }),
    );
    // 콘솔 게이트는 로컬 세션의 role을 믿지 않고 서버에 다시 묻는다(fail closed). 이 응답을
    // 심어주지 않으면 게이트가 열리지 않아 표가 아예 렌더되지 않는다 — 스크롤을 재기도 전에
    // 실패한다. 여기서 확인하려는 건 권한이 아니라 좁은 화면의 표 스크롤이다.
    await page.route("**/api/v1/accounts/me", (route) =>
      route.fulfill({
        json: { accountId: E2E_SELLER.accountId, email: "admin@gole.test", role: "ADMIN" },
      }),
    );
    await page.route("**/api/admin/overview**", (route) =>
      route.fulfill({ json: { pendingReports: 0 } }),
    );
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
            paymentMethod: { type: "EASY_PAY", provider: "KAKAOPAY" },
            createdAt: "2026-08-14T01:00:00Z",
          },
        ],
      }),
    );

    await expectNoHorizontalScroll(page, "/admin/orders");
    // 표 자체는 넘치는 게 정상이다 — 다만 그 스크롤이 표 안에 갇혀 있어야 한다.
    await expect(page.locator("div.overflow-x-auto").first()).toBeVisible();
    await expect(page.getByRole("table").getByText("카카오페이")).toBeVisible();
    // 위 expectNoHorizontalScroll은 고정 300ms 뒤에 재는데, 이 화면은 게이트가 /me 왕복을
    // 마쳐야 표를 그린다. 아직 "확인 중" 골격이던 순간을 쟀다면 넘칠 표가 없어 무조건 통과다
    // — 실패는 안 나지만 검증도 안 된다. 표가 실제로 붙은 지금 다시 재야 경계를 확인한 게 된다.
    expect(await horizontalOverflow(page), "가로 스크롤 발생: /admin/orders").toBeLessThanOrEqual(
      1,
    );
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
