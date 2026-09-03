import { expect, test, type Page } from "@playwright/test";

const e2eBaseUrl = process.env.E2E_BASE_URL;
const isRemoteTarget =
  e2eBaseUrl !== undefined &&
  !/^https?:\/\/(?:localhost|127\.0\.0\.1)(?::\d+)?(?:\/|$)/.test(e2eBaseUrl);

interface SeedSession {
  readonly accountId: string;
  readonly email: string;
}

const review = {
  id: "review-1",
  orderId: "order-1",
  reviewerId: "buyer-1",
  revieweeId: "seller-1",
  rating: 5,
  content: "포장도 꼼꼼하고 설명과 같은 상태였어요.",
  createdAt: "2026-09-03T00:00:00Z",
  reply: null,
  repliedAt: null,
} as const;

async function seedSession(page: Page, session: SeedSession): Promise<void> {
  await page.addInitScript(({ accountId }) => {
    window.localStorage.setItem(
      "gole.session",
      JSON.stringify({ accountId, sessionToken: "", role: "USER" }),
    );
  }, session);

  await page.route("**/api/v1/accounts/me", (route) =>
    route.fulfill({
      json: { accountId: session.accountId, email: session.email, role: "USER" },
    }),
  );
  await page.route(`**/api/v1/users/${session.accountId}/notifications/unread-count`, (route) =>
    route.fulfill({ json: { unreadCount: 0 } }),
  );
  await page.route(`**/api/v1/users/${session.accountId}/following`, (route) =>
    route.fulfill({ json: [] }),
  );
}

async function mockSellerShop(page: Page): Promise<void> {
  await page.route("**/api/v1/config/launch", (route) =>
    route.fulfill({
      json: {
        stage: 2,
        tradeMode: "MANUAL_SETTLEMENT",
        features: { payments: true, reviews: true, partnerPayout: false },
        updatedAt: "2026-09-03T00:00:00Z",
      },
    }),
  );
  await page.route("**/api/v1/shops/seller-1", (route) => route.fulfill({ json: [] }));
  await page.route("**/api/v1/sellers/seller-1/rating", (route) =>
    route.fulfill({ json: { sellerId: "seller-1", average: 5, count: 1 } }),
  );
  await page.route("**/api/v1/sellers/seller-1/reviews", (route) =>
    route.fulfill({ json: [review] }),
  );
}

test.describe("후기 신뢰 흐름", () => {
  test.skip(isRemoteTarget, "응답 가로채기 기반 — 로컬 프론트 전용");

  test("판매자는 본인 샵에서 후기에 공개 답글을 남긴다", async ({ page }) => {
    await seedSession(page, { accountId: "seller-1", email: "seller@gole.test" });
    await mockSellerShop(page);

    let replyBody: unknown;
    await page.route("**/api/v1/reviews/review-1/reply", async (route) => {
      replyBody = route.request().postDataJSON();
      await route.fulfill({
        json: {
          ...review,
          reply: "좋은 거래 고맙습니다. 즐거운 조립 되세요!",
          repliedAt: "2026-09-03T01:00:00Z",
        },
      });
    });

    await page.goto("/shops/seller-1");
    await expect(page.getByText(review.content)).toBeVisible();
    await page.getByRole("button", { name: "답글 남기기" }).click();
    await page.getByLabel("판매자 답글").fill("좋은 거래 고맙습니다. 즐거운 조립 되세요!");
    await page.getByRole("button", { name: "답글 저장" }).click();

    expect(replyBody).toEqual({ content: "좋은 거래 고맙습니다. 즐거운 조립 되세요!" });
    await expect(page.getByText("판매자 답글", { exact: true })).toBeVisible();
    await expect(page.getByText("좋은 거래 고맙습니다. 즐거운 조립 되세요!")).toBeVisible();
    await expect(page.getByRole("button", { name: "답글 수정" })).toBeVisible();
  });

  test("이용자는 후기 전용 사유로 신고하고 운영 조치 안내를 받는다", async ({ page }) => {
    await seedSession(page, { accountId: "viewer-1", email: "viewer@gole.test" });
    await mockSellerShop(page);

    let reportBody: unknown;
    await page.route("**/api/v1/reports", async (route) => {
      reportBody = route.request().postDataJSON();
      await route.fulfill({ status: 201, json: { id: "report-1" } });
    });

    await page.goto("/shops/seller-1");
    await page.getByRole("button", { name: "신고하기" }).click();

    const dialog = page.getByRole("dialog", { name: "후기 신고" });
    await expect(dialog).toBeVisible();
    await expect(dialog.getByText("가품·위조품 의심")).toHaveCount(0);
    await expect(dialog.getByText("사기·허위 매물")).toHaveCount(0);
    await dialog.getByLabel("욕설·스팸 등 부적절").check();
    await dialog
      .getByPlaceholder("상세 내용 (선택, 최대 1000자)")
      .fill("거래와 무관한 욕설입니다.");
    await dialog.getByRole("button", { name: "신고 접수" }).click();

    expect(reportBody).toEqual({
      reporterId: "viewer-1",
      targetType: "REVIEW",
      targetId: "review-1",
      reason: "INAPPROPRIATE",
      detail: "거래와 무관한 욕설입니다.",
    });
    await expect(dialog.getByText("신고가 접수되었어요")).toBeVisible();
    await expect(dialog.getByText(/필요하면 후기를 블라인드/)).toBeVisible();
  });
});
