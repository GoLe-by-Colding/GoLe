import { test, expect, request } from "@playwright/test";

/**
 * 운영자 콘솔 — 권한 게이트와 정지 실효성. (admin-console 요구사항 1, 6)
 *
 * 화면 게이트는 백엔드 없이도 검증되고, API 가드는 백엔드가 떠 있을 때만 의미가 있으므로
 * 백엔드 미기동 시에는 해당 테스트를 skip 한다(로컬 프론트 전용 실행을 막지 않기 위해).
 */

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";
const ADMIN_EMAIL = process.env.GOLE_ADMIN_EMAIL ?? "";
const ADMIN_PASSWORD = process.env.GOLE_ADMIN_PASSWORD ?? "";

/** 로컬 세션(비신뢰 표시 상태)만 심는다. 권한 판정은 서버 응답으로만 이뤄져야 한다. */
async function seedLocalSession(
  page: import("@playwright/test").Page,
  session: Record<string, unknown>,
): Promise<void> {
  await page.addInitScript((value) => {
    window.localStorage.setItem("gole.session", value as string);
  }, JSON.stringify(session));
}

/** 권위 있는 현재 사용자 조회(GET /me)를 흉내 낸다. */
async function mockMe(
  page: import("@playwright/test").Page,
  result: { status: number; body?: unknown },
): Promise<void> {
  await page.route("**/api/v1/accounts/me", async (route) => {
    await route.fulfill({
      status: result.status,
      contentType: "application/json",
      body: JSON.stringify(result.body ?? { code: "UNAUTHORIZED", message: "unauthorized" }),
    });
  });
  // HttpOnly 쿠키를 직접 심지 않는 화면 게이트 테스트에서는 헤더의 전역 알림 폴링이
  // 실제 API로 새면 INVALID_SESSION이 합성 로컬 세션을 지운다. 게이트와 무관한 요청은
  // 모든 mockMe 호출에서 함께 격리해 테스트가 권한 응답 하나만 관찰하게 한다.
  await page.route(/\/api\/v1\/users\/[^/]+\/notifications\/unread-count(?:\?.*)?$/, (route) =>
    route.fulfill({ json: { unreadCount: 0 } }),
  );
  // 권한 확인을 통과한 직후 셸이 요청하는 집계도 격리한다. 합성 세션에는 HttpOnly
  // 쿠키가 없으므로 실제 API로 새면 INVALID_SESSION 401이 localStorage를 지워
  // 게이트 테스트가 비결정적으로 로그인 화면으로 되돌아간다. 각 테스트가 뒤에서
  // 등록하는 더 구체적인 overview 핸들러가 이 기본 응답을 덮어쓸 수 있다.
  await page.route("**/api/admin/overview", (route) =>
    route.fulfill({
      json: {
        counts: {},
        gmv: 0,
        ordersByStatus: {},
        activeListings: 0,
        pendingReports: 0,
        pendingSettlements: 0,
      },
    }),
  );
}

test.beforeEach(async ({ page }) => {
  // 대부분의 화면 게이트 테스트는 HttpOnly 쿠키 없이 localStorage 메타데이터와 /me
  // 응답만 합성한다. 사이트 헤더의 독립적인 알림 폴링까지 실제 API로 보내면 그 401이
  // 세션을 정리하므로, 이 스펙 전체에서 전역 요청을 격리한다.
  await page.route(/\/api\/v1\/users\/[^/]+\/notifications\/unread-count(?:\?.*)?$/, (route) =>
    route.fulfill({ json: { unreadCount: 0 } }),
  );
});

test.describe("운영자 콘솔 — 화면 게이트", () => {
  test("비로그인 사용자에게 /admin은 로그인 안내를 보여준다 (R1.3)", async ({ page }) => {
    await page.goto("/admin");
    await expect(page.getByRole("heading", { name: "관리자 로그인이 필요합니다" })).toBeVisible();
    await expect(page.getByRole("link", { name: "로그인" })).toBeVisible();
  });

  test("비로그인 사용자에게 콘솔 하위 경로도 동일하게 차단된다 (R1.3)", async ({ page }) => {
    for (const path of [
      "/admin/reports",
      "/admin/accounts",
      "/admin/settlements",
      "/admin/audit",
    ]) {
      await page.goto(path);
      await expect(page.getByRole("heading", { name: "관리자 로그인이 필요합니다" })).toBeVisible();
    }
  });

  test("서버가 USER로 확인한 계정에는 권한 없음 안내를 보여준다 (R1.4)", async ({ page }) => {
    await seedLocalSession(page, { accountId: "u-1", sessionToken: "", role: "USER" });
    await mockMe(page, {
      status: 200,
      body: { accountId: "u-1", email: "user@gole.test", role: "USER" },
    });
    await page.goto("/admin");
    await expect(page.getByRole("heading", { name: "접근 권한이 없습니다" })).toBeVisible();
    // 운영 데이터가 새어나오지 않아야 한다.
    await expect(page.getByRole("heading", { name: "운영자 콘솔" })).toHaveCount(0);
  });

  test("일반 사용자에게 온사이트 어드민 바가 렌더되지 않는다 (R1.7)", async ({ page }) => {
    await page.addInitScript(() => {
      window.localStorage.setItem(
        "gole.session",
        JSON.stringify({ accountId: "u-1", sessionToken: "fake", role: "USER" }),
      );
    });
    await page.goto("/");
    await expect(page.getByText("ADMIN", { exact: true })).toHaveCount(0);
  });

  test("/admin은 색인되지 않는다 (R1.5)", async ({ page }) => {
    await page.goto("/admin");
    await expect(page.locator('meta[name="robots"]')).toHaveAttribute(
      "content",
      /noindex.*nofollow/,
    );
  });

  test("위조한 로컬 role=ADMIN + 빈 토큰으로는 콘솔이 렌더되지 않는다", async ({ page }) => {
    let overviewRequested = false;
    // 콘솔 셸이 직접 쓰는 운영 집계만 관찰한다.
    // (사이트 레이아웃의 admin-bar는 별도 위젯이라 이 테스트 범위 밖이다.)
    await page.route("**/api/admin/overview**", async (route) => {
      overviewRequested = true;
      await route.fulfill({ status: 401, contentType: "application/json", body: "{}" });
    });
    await seedLocalSession(page, {
      accountId: "forged-1",
      sessionToken: "",
      role: "ADMIN",
    });
    await mockMe(page, { status: 401 });

    await page.goto("/admin");

    await expect(page.getByRole("heading", { name: "관리자 로그인이 필요합니다" })).toBeVisible();
    await expect(page.getByRole("heading", { name: "운영자 콘솔" })).toHaveCount(0);
    await expect(page.getByRole("navigation", { name: "운영자 메뉴" })).toHaveCount(0);
    // 서버 확인을 통과하기 전에는 콘솔이 운영 집계를 요청하지 않는다.
    expect(overviewRequested).toBe(false);
  });

  test("유효하지 않은 토큰이면 콘솔 대신 로그인 안내가 나온다", async ({ page }) => {
    await seedLocalSession(page, {
      accountId: "forged-2",
      sessionToken: "not-a-real-token",
      role: "ADMIN",
    });
    await mockMe(page, { status: 401 });

    await page.goto("/admin/settlements");

    await expect(page.getByRole("heading", { name: "관리자 로그인이 필요합니다" })).toBeVisible();
    await expect(page.getByRole("heading", { name: "판매자 정산" })).toHaveCount(0);
  });

  test("권한 확인에 실패하면 열어주지 않는다(fail closed)", async ({ page }) => {
    await seedLocalSession(page, { accountId: "a-1", sessionToken: "", role: "ADMIN" });
    await page.route("**/api/v1/accounts/me", async (route) => {
      await route.abort("failed");
    });

    await page.goto("/admin");

    await expect(page.getByRole("heading", { name: "권한을 확인할 수 없습니다" })).toBeVisible();
    await expect(page.getByRole("heading", { name: "운영자 콘솔" })).toHaveCount(0);
    await expect(page.getByRole("button", { name: "다시 시도" })).toBeVisible();
    await expect(page.getByRole("link", { name: "홈으로" })).toHaveAttribute("href", "/");
    await expect(page.getByRole("link", { name: "로그인" })).toHaveCount(0);
  });

  test("권한 확인 오류에서 재시도하면 서버 권한을 다시 확인한다", async ({ page }) => {
    await seedLocalSession(page, { accountId: "admin-1", sessionToken: "", role: "ADMIN" });
    let attempts = 0;
    await page.route("**/api/v1/accounts/me", async (route) => {
      attempts += 1;
      if (attempts === 1) {
        await route.abort("failed");
        return;
      }
      await route.fulfill({
        contentType: "application/json",
        body: JSON.stringify({ accountId: "admin-1", email: "admin@gole.test", role: "ADMIN" }),
      });
    });
    await page.route("**/api/admin/overview", async (route) => {
      await route.fulfill({
        json: {
          counts: {},
          gmv: 0,
          ordersByStatus: {},
          activeListings: 0,
          pendingReports: 0,
          pendingSettlements: 0,
        },
      });
    });
    await page.route("**/api/admin/audit?limit=8", (route) => route.fulfill({ json: [] }));

    await page.goto("/admin");
    await page.getByRole("button", { name: "다시 시도" }).click();
    await expect(page.getByRole("heading", { name: "운영자 콘솔" })).toBeVisible();
    expect(attempts).toBe(2);
  });

  test("서버가 ADMIN으로 확인하면 콘솔이 렌더된다", async ({ page }) => {
    await seedLocalSession(page, { accountId: "admin-1", sessionToken: "", role: "ADMIN" });
    await mockMe(page, {
      status: 200,
      body: { accountId: "admin-1", email: "admin@gole.test", role: "ADMIN" },
    });
    // 헤더의 알림 폴링까지 실제 API로 새면 INVALID_SESSION이 로컬 테스트 세션을
    // 정리해 관리자 게이트가 닫힌다. 셸과 무관한 전역 요청도 명시적으로 격리한다.
    await page.route(/\/api\/v1\/users\/[^/]+\/notifications\/unread-count(?:\?.*)?$/, (route) =>
      route.fulfill({ json: { unreadCount: 0 } }),
    );
    await page.route("**/api/admin/overview", async (route) => {
      await route.fulfill({
        json: {
          counts: {},
          gmv: 0,
          ordersByStatus: {},
          activeListings: 0,
          pendingReports: 0,
          pendingSettlements: 0,
        },
      });
    });
    await page.route("**/api/admin/audit?limit=8", (route) => route.fulfill({ json: [] }));

    await page.goto("/admin");

    await expect(page.getByRole("heading", { name: "운영자 콘솔" })).toBeVisible();
    await expect(page.getByRole("navigation", { name: "운영자 메뉴" })).toBeVisible();
  });

  test("운영 문의는 개인정보 권리 유형과 처리 목표를 표시하고 유형으로 필터한다", async ({
    page,
  }) => {
    await seedLocalSession(page, { accountId: "admin-1", sessionToken: "", role: "ADMIN" });
    await mockMe(page, {
      status: 200,
      body: { accountId: "admin-1", email: "admin@gole.test", role: "ADMIN" },
    });
    const requestedUrls: string[] = [];
    await page.route(/\/api\/admin\/support(?:\?.*)?$/, async (route) => {
      requestedUrls.push(route.request().url());
      await route.fulfill({
        json: [
          {
            roomId: "privacy-room",
            requesterId: "user-privacy",
            title: "내 정보 열람",
            category: "PRIVACY_ACCESS",
            status: "UNASSIGNED",
            assigneeId: null,
            createdAt: "2026-09-03T09:00:00Z",
            updatedAt: "2026-09-03T09:00:00Z",
            resolvedAt: null,
            responseDueAt: "2099-09-13T09:00:00Z",
          },
        ],
      });
    });

    await page.goto("/admin/support");

    await expect(page.getByRole("heading", { name: "운영 문의" })).toBeVisible();
    const inbox = page.getByRole("complementary");
    await expect(inbox.getByText("개인정보 열람", { exact: true })).toBeVisible();
    await expect(inbox.getByText(/일 남음/)).toBeVisible();

    await page.getByRole("combobox", { name: "유형" }).selectOption("PRIVACY_ACCESS");
    await expect
      .poll(() => requestedUrls.some((url) => url.includes("category=PRIVACY_ACCESS")))
      .toBe(true);
  });

  test("콘솔 로그인 안내는 원래 경로를 returnTo로 전달한다", async ({ page }) => {
    await page.goto("/admin/reports");

    await expect(page.getByRole("link", { name: "로그인" })).toHaveAttribute(
      "href",
      `/login?returnTo=${encodeURIComponent("/admin/reports")}`,
    );
  });

  test("콘솔 로그인 안내는 현재 검색 조건도 returnTo로 보존한다", async ({ page }) => {
    await page.goto("/admin/reports?status=pending");
    await expect(page.getByRole("link", { name: "로그인" })).toHaveAttribute(
      "href",
      `/login?returnTo=${encodeURIComponent("/admin/reports?status=pending")}`,
    );
  });
});

test.describe("운영자 콘솔 — 대시보드 셸", () => {
  test.beforeEach(async ({ page }) => {
    await seedLocalSession(page, {
      accountId: "admin-1",
      email: "admin@gole.test",
      sessionToken: "admin-test-token",
      role: "ADMIN",
    });
    await mockMe(page, {
      status: 200,
      body: { accountId: "admin-1", email: "admin@gole.test", role: "ADMIN" },
    });
    // 헤더의 알림 폴링까지 실제 API로 새면 INVALID_SESSION이 로컬 테스트 세션을
    // 정리해 관리자 게이트가 닫힌다. 셸과 무관한 전역 요청도 명시적으로 격리한다.
    await page.route(/\/api\/v1\/users\/[^/]+\/notifications\/unread-count(?:\?.*)?$/, (route) =>
      route.fulfill({ json: { unreadCount: 0 } }),
    );
    await page.route("**/api/admin/overview", async (route) => {
      await route.fulfill({
        contentType: "application/json",
        body: JSON.stringify({
          counts: { accounts: 42, listings: 9, orders: 15, posts: 6 },
          activeListings: 9,
          gmv: 1_248_000,
          ordersByStatus: {
            PAYMENT_PENDING: 2,
            PAYMENT_REVIEW: 1,
            PAYMENT_FAILED: 1,
            COMPLETED: 12,
          },
          pendingReports: 3,
          pendingSettlements: 2,
        }),
      });
    });
    await page.route("**/api/admin/audit**", async (route) => {
      await route.fulfill({ contentType: "application/json", body: "[]" });
    });
  });

  test("우선 작업과 현재 메뉴를 실제 집계로 표시한다", async ({ page }) => {
    await page.goto("/admin");

    await expect(page.getByRole("heading", { name: "운영자 콘솔" })).toBeVisible();
    await expect(page.getByRole("navigation", { name: "운영자 메뉴" })).toBeVisible();
    await expect(page.getByRole("link", { name: "대시보드" })).toHaveAttribute(
      "aria-current",
      "page",
    );
    await expect(page.getByText("미처리 신고").locator("..").getByText("3건")).toBeVisible();
    await expect(page.getByText("결제 실패 주문").locator("..").getByText("1건")).toBeVisible();
    await expect(page.getByText("결제 확인 필요").locator("..").getByText("1건")).toBeVisible();
    await expect(page.getByText("지급 대기 정산").locator("..").getByText("2건")).toBeVisible();
  });

  test("비활성 메뉴는 Figma Admin/Navigation Item 규격을 따른다", async ({ page }) => {
    await page.goto("/admin");

    const navigationItem = page.getByRole("link", { name: "주문", exact: true });
    await expect(navigationItem).toHaveCSS("width", "200px");
    await expect(navigationItem).toHaveCSS("height", "40px");
    await expect(navigationItem).toHaveCSS("padding", "12px");
    await expect(navigationItem).toHaveCSS("border-radius", "10px");
    await expect(navigationItem).toHaveCSS("background-color", "rgb(252, 251, 248)");
    await expect(navigationItem).toHaveCSS("color", "rgb(91, 82, 75)");
    await expect(navigationItem).toHaveCSS("font-size", "14px");
    await expect(navigationItem).toHaveCSS("font-weight", "500");
  });

  test("분쟁 판정은 사유를 필수로 받아 서버 감사 기록에 전달한다", async ({ page }) => {
    let submittedBody: unknown;
    await page.route("**/api/admin/exception-queue", async (route) => {
      await route.fulfill({
        contentType: "application/json",
        body: JSON.stringify([
          {
            type: "dispute",
            typeLabel: "분쟁",
            orderId: "order-dispute-1",
            orderStatus: "disputed",
            buyerId: "buyer-1",
            sellerId: "seller-1",
            amount: 120000,
            since: "2026-08-20T01:00:00Z",
            reason: "상품 상태 상이",
            disputeDetail: "설명과 다른 부품이 왔습니다.",
            shipment: null,
          },
        ]),
      });
    });
    await page.route("**/api/admin/orders/order-dispute-1/dispute-resolution", async (route) => {
      submittedBody = route.request().postDataJSON();
      await route.fulfill({ contentType: "application/json", body: "[]" });
    });

    await page.goto("/admin/exceptions");
    await page.getByRole("button", { name: "환불 판정" }).click();

    const dialog = page.getByRole("dialog", { name: "전액 환불 판정" });
    await expect(dialog.getByRole("button", { name: "환불 확정" })).toBeDisabled();
    await dialog.getByRole("textbox", { name: "조치 사유" }).fill("배송 사실과 제출 사진을 확인함");
    await dialog.getByRole("button", { name: "환불 확정" }).click();

    await expect(dialog).toHaveCount(0);
    expect(submittedBody).toEqual({
      resolution: "refund",
      note: "배송 사실과 제출 사진을 확인함",
    });
  });

  test("좁은 화면에서 콘솔 셸이 페이지 전체 가로 스크롤을 만들지 않는다", async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto("/admin");

    const navigation = page.getByRole("navigation", { name: "운영자 메뉴" });
    await expect(navigation).toBeVisible();
    const hasPageOverflow = await page.evaluate(
      () => document.documentElement.scrollWidth > document.documentElement.clientWidth,
    );
    expect(hasPageOverflow).toBe(false);

    const navigationBounds = await navigation.evaluate((element) => ({
      clientWidth: element.clientWidth,
      scrollWidth: element.scrollWidth,
    }));
    expect(navigationBounds.scrollWidth).toBeLessThanOrEqual(navigationBounds.clientWidth);

    const [dashboardBox, reportsBox] = await Promise.all([
      navigation.getByRole("link", { name: "대시보드", exact: true }).boundingBox(),
      navigation.getByRole("link", { name: /신고/ }).boundingBox(),
    ]);
    expect(dashboardBox).not.toBeNull();
    expect(reportsBox).not.toBeNull();
    expect(reportsBox!.y).toBeGreaterThan(dashboardBox!.y + dashboardBox!.height);
  });

  test("좁은 화면에서 카탈로그 폼은 한 열로 흐르고 표 스크롤은 카드 안에 머문다", async ({
    page,
  }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.route("**/api/admin/catalog/sets**", async (route) => {
      await route.fulfill({
        json: [
          {
            setNumber: "10307",
            name: "Eiffel Tower",
            theme: "Icons",
            pieceCount: 10001,
            releaseYear: 2022,
            retirementStatus: "ACTIVE",
            imageUrl: null,
            featured: true,
          },
        ],
      });
    });

    await page.goto("/admin/catalog");
    await expect(page.getByRole("table", { name: /레고 세트 카탈로그 목록/ })).toBeVisible();

    const pieceCount = page.getByRole("spinbutton", { name: "피스 수" });
    const releaseYear = page.getByRole("spinbutton", { name: "출시 연도" });
    const [pieceBox, yearBox] = await Promise.all([
      pieceCount.boundingBox(),
      releaseYear.boundingBox(),
    ]);
    expect(pieceBox).not.toBeNull();
    expect(yearBox).not.toBeNull();
    expect(yearBox!.y).toBeGreaterThan(pieceBox!.y + pieceBox!.height);

    const tableScroller = page
      .getByRole("table", { name: /레고 세트 카탈로그 목록/ })
      .locator("..");
    const dimensions = await tableScroller.evaluate((element) => ({
      clientWidth: element.clientWidth,
      scrollWidth: element.scrollWidth,
      pageClientWidth: document.documentElement.clientWidth,
      pageScrollWidth: document.documentElement.scrollWidth,
    }));
    expect(dimensions.scrollWidth).toBeGreaterThan(dimensions.clientWidth);
    expect(dimensions.pageScrollWidth - dimensions.pageClientWidth).toBeLessThanOrEqual(1);
  });

  test("카탈로그를 번호·이름·테마로 즉시 찾는다", async ({ page }) => {
    await page.route("**/api/admin/catalog/sets**", async (route) => {
      await route.fulfill({
        contentType: "application/json",
        body: JSON.stringify([
          {
            setNumber: "10307",
            name: "Eiffel Tower",
            theme: "Icons",
            pieceCount: 10001,
            releaseYear: 2022,
            retirementStatus: "ACTIVE",
            imageUrl: null,
            featured: true,
          },
          {
            setNumber: "42143",
            name: "Ferrari Daytona SP3",
            theme: "Technic",
            pieceCount: 3778,
            releaseYear: 2022,
            retirementStatus: "ACTIVE",
            imageUrl: null,
            featured: false,
          },
        ]),
      });
    });

    await page.goto("/admin/catalog");
    const search = page.getByRole("searchbox", { name: "번호·이름·테마 검색" });
    await expect(page.getByRole("table").getByText("Eiffel Tower")).toBeVisible();
    await expect(page.getByRole("table").getByText("Ferrari Daytona SP3")).toBeVisible();

    await search.fill("technic");

    await expect(page.getByRole("table").getByText("Ferrari Daytona SP3")).toBeVisible();
    await expect(page.getByRole("table").getByText("Eiffel Tower")).toHaveCount(0);
    await expect(page.getByText("검색 결과 1개 / 조회 2개")).toBeVisible();
  });

  test("결제·판매자 지급이 준비되지 않으면 Stage 2 상향을 잠근다", async ({ page }) => {
    await page.route("**/api/admin/overview", async (route) => {
      await route.fulfill({
        json: {
          counts: {},
          activeListings: 0,
          gmv: 0,
          ordersByStatus: {},
          pendingReports: 0,
          pendingSettlements: 0,
          paymentReadiness: {
            enabled: false,
            ready: false,
            state: "DISABLED",
            channelType: "UNKNOWN",
            methods: ["KAKAOPAY"],
            currency: "KRW",
            issues: [],
          },
        },
      });
    });
    await page.route("**/api/admin/launch/history**", (route) => route.fulfill({ json: [] }));
    await page.route("**/api/admin/launch", (route) =>
      route.fulfill({
        json: {
          config: {
            stage: 1,
            tradeMode: "DIRECT_CHAT",
            features: { payments: false, reviews: false, partnerPayout: false },
            updatedAt: "2026-09-03T01:00:00Z",
          },
          requestedStage: 1,
          overrides: {},
          readiness: {
            businessDisclosure: false,
            termsPrivacy: false,
            paymentFlow: false,
            payoutFlow: false,
          },
          updatedBy: "admin-1",
          settlementMode: "DISABLED",
          payoutContractVerified: false,
        },
      }),
    );

    await page.goto("/admin/launch");

    await expect(page.getByRole("heading", { name: "출시 단계" })).toBeVisible();
    await expect(page.getByText("PortOne · 카카오페이")).toBeVisible();
    await expect(page.getByText("비활성", { exact: true })).toBeVisible();
    await expect(
      page.getByText("판매자 지급 계약").locator("..").getByText("미준비"),
    ).toBeVisible();

    const paymentStage = page.getByRole("group", { name: "Stage 2 · 결제" });
    await expect(paymentStage.getByText("지급 계약 서면 확인이 필요함")).toBeVisible();
    await expect(paymentStage.getByRole("button", { name: "전환" })).toBeDisabled();
  });

  test("운영 승인 세 항목을 기록해야 Stage 2 전환이 열린다", async ({ page }) => {
    const readiness: Record<string, boolean> = {
      businessDisclosure: false,
      termsPrivacy: false,
      paymentFlow: false,
      payoutFlow: false,
    };
    const payload = () => ({
      config: {
        stage: 1,
        tradeMode: "DIRECT_CHAT",
        features: { payments: false, reviews: false, partnerPayout: false },
        updatedAt: "2026-09-03T01:00:00Z",
      },
      requestedStage: 1,
      overrides: {},
      readiness,
      updatedBy: "admin-1",
      settlementMode: "MANUAL",
      payoutContractVerified: true,
    });
    const submitted: { check: string; confirmed: boolean; reason: string }[] = [];
    await page.route("**/api/admin/overview", (route) =>
      route.fulfill({
        json: {
          counts: {},
          activeListings: 0,
          gmv: 0,
          ordersByStatus: {},
          pendingReports: 0,
          pendingSettlements: 0,
          paymentReadiness: {
            enabled: true,
            ready: true,
            state: "READY",
            channelType: "TEST",
            methods: ["KAKAOPAY"],
            currency: "KRW",
            issues: [],
          },
        },
      }),
    );
    await page.route("**/api/admin/launch/history**", (route) =>
      route.fulfill({
        json: submitted
          .map((entry, index) => ({
            id: `readiness-${index}`,
            type: "READINESS",
            target: entry.check,
            before: "false",
            after: String(entry.confirmed),
            reason: entry.reason,
            actorId: "admin-1",
            actorEmail: "admin@gole.local",
            occurredAt: `2026-09-03T01:0${index}:00Z`,
          }))
          .reverse(),
      }),
    );
    await page.route("**/api/admin/launch/readiness/**", async (route) => {
      const check = new URL(route.request().url()).pathname.split("/").at(-1) ?? "";
      const body = route.request().postDataJSON() as { confirmed: boolean; reason: string };
      readiness[check] = body.confirmed;
      submitted.push({ check, ...body });
      await route.fulfill({ json: payload() });
    });
    await page.route("**/api/admin/launch", (route) => route.fulfill({ json: payload() }));

    await page.goto("/admin/launch");

    const paymentStage = page.getByRole("group", { name: "Stage 2 · 결제" });
    await expect(paymentStage.getByText(/운영 승인 미확인/)).toBeVisible();
    for (const [title, check] of [
      ["사업자·고객센터 고지", "businessDisclosure"],
      ["약관·개인정보 검토", "termsPrivacy"],
      ["결제·웹훅·환불 실거래", "paymentFlow"],
    ] as const) {
      await page.getByLabel("변경 사유").fill(`${title} 증빙 확인`);
      await page
        .getByRole("group", { name: title })
        .getByRole("button", { name: "확인 완료" })
        .click();
      await expect(page.getByRole("group", { name: title }).getByText("승인됨")).toBeVisible();
      await expect(
        page.getByRole("group", { name: title }).getByText(`${title} 증빙 확인`),
      ).toBeVisible();
      await expect(
        page.getByRole("group", { name: title }).getByText(/admin@gole\.local/),
      ).toBeVisible();
      expect(submitted.at(-1)).toEqual({ check, confirmed: true, reason: `${title} 증빙 확인` });
    }

    await expect(paymentStage.getByRole("button", { name: "전환" })).toBeEnabled();
  });

  test("결제 진단 API가 실패해도 긴급 단계 하향 통로를 유지한다", async ({ page }) => {
    await page.route("**/api/admin/overview", (route) => route.abort("failed"));
    await page.route("**/api/admin/launch/history**", (route) => route.fulfill({ json: [] }));
    await page.route("**/api/admin/launch", (route) =>
      route.fulfill({
        json: {
          config: {
            stage: 2,
            tradeMode: "MANUAL_SETTLEMENT",
            features: { payments: true, reviews: true, partnerPayout: false },
            updatedAt: "2026-09-03T01:00:00Z",
          },
          requestedStage: 2,
          overrides: {},
          readiness: {
            businessDisclosure: true,
            termsPrivacy: true,
            paymentFlow: true,
            payoutFlow: false,
          },
          updatedBy: "admin-1",
          settlementMode: "MANUAL",
          payoutContractVerified: true,
        },
      }),
    );

    await page.goto("/admin/launch");

    await expect(page.getByRole("heading", { name: "출시 단계" })).toBeVisible();
    await expect(page.getByText("상태 확인 불가")).toBeVisible();
    await expect(
      page.getByRole("group", { name: "Stage 1 · 커뮤니티" }).getByRole("button", {
        name: "전환",
      }),
    ).toBeEnabled();
    await expect(
      page.getByRole("group", { name: "Stage 3 · 지급대행" }).getByRole("button", {
        name: "전환",
      }),
    ).toBeDisabled();
  });

  test("정산 원장에서 지급 증빙을 입력해 완료 처리한다", async ({ page }) => {
    let markedPaid = false;
    let settlementStatus: "PENDING" | "PAYOUT_IN_PROGRESS" = "PENDING";
    const settlement = () => ({
      orderId: "order-settlement-1",
      sellerId: "seller-1",
      grossAmount: 125_000,
      fee: 6_250,
      payout: 118_750,
      feeRate: 0.05,
      status: settlementStatus,
      paymentReference: null,
      createdAt: "2026-08-09T01:00:00Z",
      payableAt: "2026-08-09T01:30:00Z",
      paidAt: null,
      payoutAttempts: settlementStatus === "PAYOUT_IN_PROGRESS" ? 1 : 0,
      payoutOperatorId: settlementStatus === "PAYOUT_IN_PROGRESS" ? "admin-1" : null,
      payoutAttemptedAt: settlementStatus === "PAYOUT_IN_PROGRESS" ? "2026-08-09T01:45:00Z" : null,
      payoutNextAttemptAt: null,
      payoutError: null,
    });
    await page.route("**/api/admin/launch", (route) =>
      route.fulfill({
        json: {
          config: {
            stage: 2,
            tradeMode: "MANUAL_SETTLEMENT",
            features: { payments: true, reviews: true, partnerPayout: false },
            updatedAt: "2026-08-09T00:00:00Z",
          },
          requestedStage: 2,
          overrides: {},
          readiness: {
            businessDisclosure: true,
            termsPrivacy: true,
            paymentFlow: true,
            payoutFlow: false,
          },
          updatedBy: "admin-1",
          settlementMode: "MANUAL",
          payoutContractVerified: true,
        },
      }),
    );
    await page.route("**/api/admin/settlements**", async (route) => {
      const request = route.request();
      const path = new URL(request.url()).pathname;
      if (request.method() === "POST" && path.endsWith("/claim")) {
        settlementStatus = "PAYOUT_IN_PROGRESS";
        await route.fulfill({ json: settlement() });
        return;
      }
      if (request.method() === "POST" && path.endsWith("/paid")) {
        const body = request.postDataJSON() as { paymentReference?: string };
        expect(body.paymentReference).toBe("BANK-20260809-001");
        markedPaid = true;
        await route.fulfill({
          contentType: "application/json",
          body: JSON.stringify({
            orderId: "order-settlement-1",
            sellerId: "seller-1",
            grossAmount: 125_000,
            fee: 6_250,
            payout: 118_750,
            feeRate: 0.05,
            status: "PAID",
            paymentReference: body.paymentReference,
            createdAt: "2026-08-09T01:00:00Z",
            payableAt: "2026-08-09T01:30:00Z",
            paidAt: "2026-08-09T02:00:00Z",
            payoutAttempts: 1,
            payoutOperatorId: "admin-1",
            payoutAttemptedAt: "2026-08-09T01:45:00Z",
            payoutNextAttemptAt: null,
            payoutError: null,
          }),
        });
        return;
      }
      await route.fulfill({
        contentType: "application/json",
        body: JSON.stringify(markedPaid ? [] : [settlement()]),
      });
    });

    await page.goto("/admin/settlements");
    await expect(page.getByRole("heading", { name: "판매자 정산" })).toBeVisible();
    await expect(page.getByRole("link", { name: "정산" })).toHaveAttribute("aria-current", "page");
    await expect(page.getByText("₩118,750")).toBeVisible();

    await page.getByRole("button", { name: "지급 작업 시작" }).click();
    await page.getByRole("textbox", { name: /지급 증빙 번호/ }).fill("BANK-20260809-001");
    page.once("dialog", (dialog) => void dialog.accept());
    await page.getByRole("button", { name: "지급 완료" }).click();

    await expect.poll(() => markedPaid).toBe(true);
    await expect(page.getByText("해당 상태의 정산이 없습니다.")).toBeVisible();
  });

  test("결제 확인 필요 주문을 PG 원장과 재조정한다", async ({ page }) => {
    await page.route("**/api/admin/orders**", async (route) => {
      if (route.request().method() === "POST") {
        await route.fulfill({
          contentType: "application/json",
          body: JSON.stringify({ orderId: "order-payment-1", status: "FUNDS_HELD" }),
        });
        return;
      }
      await route.fulfill({
        contentType: "application/json",
        body: JSON.stringify([
          {
            id: "order-payment-1",
            status: "PAYMENT_REVIEW",
            amount: 280_000,
            buyerId: "buyer-1",
            sellerId: "seller-1",
            catalogSetNumber: "10307",
            createdAt: "2026-08-09T01:00:00Z",
          },
        ]),
      });
    });

    await page.goto("/admin/orders");
    await expect(page.getByRole("table").getByText("결제확인필요", { exact: true })).toBeVisible();
    await page.getByRole("button", { name: "PG 재확인" }).click();
    await expect(page.getByRole("table").getByText("자금보유", { exact: true })).toBeVisible();
  });
});

test.describe("관리자 API 가드", () => {
  test.skip(
    () => !process.env.E2E_WITH_BACKEND,
    "백엔드 대상 테스트는 E2E_WITH_BACKEND=1 에서만 실행",
  );

  test("토큰이 없으면 401 (R1.2)", async () => {
    const ctx = await request.newContext({ baseURL: API_BASE });
    expect((await ctx.get("/api/admin/overview")).status()).toBe(401);
    expect((await ctx.get("/api/admin/audit")).status()).toBe(401);
    expect((await ctx.get("/api/admin/accounts")).status()).toBe(401);
    await ctx.dispose();
  });

  test("잘못된 토큰이면 401 (R1.2)", async () => {
    const ctx = await request.newContext({ baseURL: API_BASE });
    const res = await ctx.get("/api/admin/overview", {
      headers: { Authorization: "Bearer not-a-real-token" },
    });
    expect(res.status()).toBe(401);
    await ctx.dispose();
  });

  test("ADMIN 토큰이면 대시보드와 감사 로그를 조회할 수 있다 (R2.2, R8.3)", async () => {
    test.skip(ADMIN_EMAIL === "", "GOLE_ADMIN_EMAIL/PASSWORD 미설정");
    const ctx = await request.newContext({ baseURL: API_BASE });

    const signIn = await ctx.post("/api/v1/accounts/sessions", {
      data: { email: ADMIN_EMAIL, password: ADMIN_PASSWORD },
    });
    expect(signIn.ok()).toBeTruthy();
    const { sessionToken, role } = await signIn.json();
    expect(role).toBe("ADMIN");

    const headers = { Authorization: `Bearer ${sessionToken}` };
    const overview = await ctx.get("/api/admin/overview", { headers });
    expect(overview.status()).toBe(200);
    expect(await overview.json()).toHaveProperty("gmv");

    const audit = await ctx.get("/api/admin/audit?limit=5", { headers });
    expect(audit.status()).toBe(200);
    expect(Array.isArray(await audit.json())).toBeTruthy();

    await ctx.dispose();
  });

  test("사유 없는 모더레이션 요청은 400으로 거부된다 (R4.2, R8)", async () => {
    test.skip(ADMIN_EMAIL === "", "GOLE_ADMIN_EMAIL/PASSWORD 미설정");
    const ctx = await request.newContext({ baseURL: API_BASE });
    const signIn = await ctx.post("/api/v1/accounts/sessions", {
      data: { email: ADMIN_EMAIL, password: ADMIN_PASSWORD },
    });
    const { sessionToken } = await signIn.json();

    const res = await ctx.post("/api/admin/listings/does-not-matter/takedown", {
      headers: { Authorization: `Bearer ${sessionToken}` },
      data: { reason: "" },
    });
    expect(res.status()).toBe(400);
    await ctx.dispose();
  });

  test("자기 자신 정지는 ADMIN_SELF_TARGET으로 거부된다 (R6.8)", async () => {
    test.skip(ADMIN_EMAIL === "", "GOLE_ADMIN_EMAIL/PASSWORD 미설정");
    const ctx = await request.newContext({ baseURL: API_BASE });
    const signIn = await ctx.post("/api/v1/accounts/sessions", {
      data: { email: ADMIN_EMAIL, password: ADMIN_PASSWORD },
    });
    const { sessionToken, accountId } = await signIn.json();

    const res = await ctx.post(`/api/admin/accounts/${accountId}/suspend`, {
      headers: { Authorization: `Bearer ${sessionToken}` },
      data: { reason: "self-test" },
    });
    expect(res.status()).toBe(400);
    expect((await res.json()).code).toBe("ADMIN_SELF_TARGET");
    await ctx.dispose();
  });
});
