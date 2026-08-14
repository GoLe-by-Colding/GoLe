import { test, expect, type Page } from "@playwright/test";

/**
 * 운영자 콘솔 — 정산(수수료·정산액)과 결제수단 노출.
 *
 * <p>백엔드 응답을 가로채 <b>화면 계약</b>만 검증한다. 정산·결제수단은 "값이 없는 상태"와
 * "값이 0인 상태"를 화면이 구분해야 하는데, 실제 백엔드로는 미정산 주문과 정산 주문을 한
 * 화면에 나란히 놓기가 번거롭고 실행 순서에 따라 흔들린다. 여기서는 그 두 상태를 고정한다.
 *
 * <p>백엔드가 실제로 그 값을 만들어 내는지는 서버 쪽 통합 테스트가 담당한다
 * (SettlementPersistenceIntegrationTest, PaymentMethodPersistenceIntegrationTest).
 */

const ADMIN_SESSION = { accountId: "e2e-admin", sessionToken: "t", role: "ADMIN" };

/** 정산·결제까지 끝난 주문. */
const COMPLETED_ORDER = {
  id: "ORD-A001-completed",
  status: "COMPLETED",
  amount: 280000,
  buyerId: "buyer-0001",
  sellerId: "seller-0001",
  catalogSetNumber: "10307",
  fee: 14000,
  payout: 266000,
  feeRate: 0.05,
  paymentMethod: { type: "EASY_PAY", provider: "KAKAOPAY" },
  createdAt: "2026-08-14T01:00:00Z",
};

/** 아직 결제도 정산도 안 된 주문 — 모든 금액 칸이 비어 있어야 한다. */
const PENDING_ORDER = {
  id: "ORD-B002-pending",
  status: "PAYMENT_PENDING",
  amount: 50000,
  buyerId: "buyer-0002",
  sellerId: "seller-0002",
  catalogSetNumber: null,
  fee: null,
  payout: null,
  feeRate: null,
  paymentMethod: null,
  createdAt: "2026-08-14T02:00:00Z",
};

const OVERVIEW = {
  counts: { orders: 2, listings: 5 },
  gmv: 280000,
  platformRevenue: 14000,
  ordersByStatus: { COMPLETED: 1, PAYMENT_PENDING: 1 },
  activeListings: 5,
};

async function signInAsAdmin(page: Page): Promise<void> {
  await page.addInitScript((session) => {
    window.localStorage.setItem("gole.session", JSON.stringify(session));
  }, ADMIN_SESSION);

  // AdminShell이 미처리 신고 배지를 위해 항상 호출한다.
  await page.route("**/api/admin/reports**", (route) => route.fulfill({ json: [] }));
  await page.route("**/api/admin/audit**", (route) => route.fulfill({ json: [] }));
}

test.describe("운영자 콘솔 — 정산·결제수단", () => {
  // 응답을 가로채므로 배포 환경을 대상으로 돌려도 안전하지만, 실데이터와 섞이면
  // 무엇을 검증했는지 흐려지므로 로컬 프론트 기준으로만 돌린다.
  test.skip(!!process.env.E2E_BASE_URL, "응답 가로채기 기반 — 로컬 프론트 전용");

  test.beforeEach(async ({ page }) => {
    await signInAsAdmin(page);
  });

  test("완료 주문에 수수료·요율·정산액·결제수단이 모두 보인다", async ({ page }) => {
    await page.route("**/api/admin/orders**", (route) =>
      route.fulfill({ json: [COMPLETED_ORDER, PENDING_ORDER] }),
    );

    await page.goto("/admin/orders");

    const row = page.getByRole("row").filter({ hasText: "ORD-A001" });
    await expect(row).toBeVisible();
    await expect(row).toContainText("₩280,000"); // 결제 금액
    await expect(row).toContainText("₩14,000"); // 플랫폼 수수료
    await expect(row).toContainText("5%"); // 정산 시점 요율
    await expect(row).toContainText("₩266,000"); // 판매자 정산액
    // 간편결제는 분류명이 아니라 사업자명으로 보여야 한다.
    await expect(row).toContainText("카카오페이");
  });

  test("미정산·미결제 주문은 금액 칸이 0이 아니라 빈 값으로 보인다", async ({ page }) => {
    await page.route("**/api/admin/orders**", (route) =>
      route.fulfill({ json: [COMPLETED_ORDER, PENDING_ORDER] }),
    );

    await page.goto("/admin/orders");

    const row = page.getByRole("row").filter({ hasText: "ORD-B002" });
    await expect(row).toBeVisible();
    await expect(row).toContainText("₩50,000");
    // "수수료 0원"으로 오해되면 정산 대사가 틀어진다.
    await expect(row).not.toContainText("₩0");
    expect(await row.getByText("—").count()).toBeGreaterThanOrEqual(3);
  });

  test("주문 표에 정산·결제수단 열이 있다", async ({ page }) => {
    await page.route("**/api/admin/orders**", (route) => route.fulfill({ json: [] }));

    await page.goto("/admin/orders");

    for (const header of ["수수료", "정산액", "결제수단"]) {
      await expect(page.getByRole("columnheader", { name: header })).toBeVisible();
    }
  });

  test("대시보드는 GMV와 플랫폼 수익(수수료)을 나란히 보여준다", async ({ page }) => {
    await page.route("**/api/admin/overview**", (route) => route.fulfill({ json: OVERVIEW }));

    await page.goto("/admin");

    // GMV는 플랫폼을 통과한 돈, 수수료가 플랫폼 매출 — 둘을 구분해 노출해야 한다.
    await expect(page.getByText("거래액(GMV · 완료)")).toBeVisible();
    await expect(page.getByText("플랫폼 수익(수수료)")).toBeVisible();
    await expect(page.getByText("₩280,000")).toBeVisible();
    await expect(page.getByText("₩14,000")).toBeVisible();
  });
});
