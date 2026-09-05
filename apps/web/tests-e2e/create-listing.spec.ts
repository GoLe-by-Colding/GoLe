import { test, expect } from "@playwright/test";
import { E2E_SELLER, signInAs } from "./support/e2e-session";

test.describe("Seller fee disclosure", () => {
  test.beforeEach(async ({ page }) => {
    await signInAs(page, E2E_SELLER);
    // 수수료 UI 스펙은 실제 Redis 세션에 의존하지 않는다. 헤더 알림 폴링의 401이
    // 합성 세션을 지우지 않도록 대상과 무관한 전역 요청을 격리한다.
    await page.route(/\/api\/v1\/users\/[^/]+\/notifications\/unread-count(?:\?.*)?$/, (route) =>
      route.fulfill({ json: { unreadCount: 0 } }),
    );
    // (main) 레이아웃의 OnboardingBanner도 같은 이유로 격리한다.
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
    await page.route("**/api/v1/config/launch", (route) =>
      route.fulfill({
        json: {
          stage: 2,
          tradeMode: "MANUAL_SETTLEMENT",
          features: { payments: true, reviews: true, partnerPayout: false },
          sellerIdentityVerificationReady: true,
          updatedAt: null,
        },
      }),
    );
  });

  test("공개 수수료 정책으로 예상 정산액을 계산한다", async ({ page }) => {
    await page.route("**/api/v1/config/fees", async (route) => {
      expect(route.request().headers()["content-type"]).toBeUndefined();
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ rate: 0.05, minFee: 1_000, maxFee: 50_000 }),
      });
    });

    await page.goto("/sell");
    await expect(page.getByText("판매 금액의 5% · 최소 ₩1,000 · 최대 ₩50,000")).toBeVisible();

    await page.getByLabel("가격 (원)").fill("10000");
    await expect(page.getByText("₩1,000", { exact: true })).toBeVisible();
    await expect(page.getByText("₩9,000", { exact: true })).toBeVisible();

    await page.getByLabel("가격 (원)").fill("2000000");
    await expect(page.getByText("₩50,000", { exact: true })).toBeVisible();
    await expect(page.getByText("₩1,950,000", { exact: true })).toBeVisible();
  });

  test("수수료 API가 실패해도 값을 꾸며내지 않고 상품 등록을 유지한다", async ({ page }) => {
    await page.route("**/api/v1/config/fees", async (route) => {
      await route.fulfill({
        status: 503,
        contentType: "application/json",
        body: JSON.stringify({
          code: "FEE_CONFIG_UNAVAILABLE",
          message: "temporarily unavailable",
        }),
      });
    });

    await page.goto("/sell");

    await expect(page.getByText(/수수료와 예상 정산액을 불러오지 못했습니다/)).toBeVisible();
    await expect(page.getByText(/판매 금액의 \d/)).toHaveCount(0);
    await expect(page.getByRole("button", { name: "상품 등록" })).toBeEnabled();
  });

  test("결제가 닫힌 단계에서는 수수료 대신 직접 거래 방식을 안내한다", async ({ page }) => {
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
    let feeRequests = 0;
    await page.route("**/api/v1/config/fees", (route) => {
      feeRequests += 1;
      return route.fulfill({ json: { rate: 0.05, minFee: 1_000, maxFee: 50_000 } });
    });

    await page.goto("/sell");

    await expect(page.getByText(/플랫폼 결제와 정산 수수료 없이/)).toBeVisible();
    await expect(page.getByText("판매 수수료와 예상 정산액")).toHaveCount(0);
    expect(feeRequests).toBe(0);
  });

  test("판매자 신원확인 준비 전에는 신규 등록을 숨기고 이용 가능한 경로만 안내한다", async ({
    page,
  }) => {
    await page.unroute("**/api/v1/config/launch");
    await page.route("**/api/v1/config/launch", (route) =>
      route.fulfill({
        json: {
          stage: 0,
          tradeMode: "DIRECT_CHAT",
          features: { payments: false, reviews: false, partnerPayout: false },
          sellerIdentityVerificationReady: false,
          updatedAt: null,
        },
      }),
    );

    await page.goto("/sell");

    await expect(page.getByText("신규 상품 등록 준비 중")).toBeVisible();
    await expect(page.getByRole("button", { name: "상품 등록" })).toHaveCount(0);
    await expect(page.getByRole("link", { name: "상품 둘러보기" })).toHaveAttribute(
      "href",
      "/search",
    );
    await expect(page.getByRole("link", { name: "운영 문의" })).toHaveAttribute(
      "href",
      "/chat?compose=support&category=PRODUCT_FEEDBACK",
    );
  });

  test("운영 준비 후에도 인증된 전화번호가 없으면 판매 양식을 열지 않는다", async ({ page }) => {
    await page.unroute("**/api/v1/accounts/me/onboarding");
    await page.route("**/api/v1/accounts/me/onboarding", (route) =>
      route.fulfill({
        json: {
          required: true,
          legacyExempt: false,
          nicknameCompleted: true,
          nickname: "e2e",
          phoneVerificationRequired: true,
          phoneCompleted: false,
          maskedPhoneNumber: null,
          interestTagsCompleted: true,
          interestTags: [],
          privacyConsented: true,
          marketingConsented: false,
        },
      }),
    );

    await page.goto("/sell");

    await expect(page.getByText("판매자 전화번호 확인이 필요해요")).toBeVisible();
    await expect(page.getByRole("button", { name: "상품 등록" })).toHaveCount(0);
    await expect(page.getByRole("link", { name: "본인확인 진행하기" })).toHaveAttribute(
      "href",
      "/onboarding",
    );
  });
});

// 백엔드(MongoDB + MinIO)가 떠 있는 로컬/테스트 환경 전용 풀 플로우 E2E.
// 데이터를 생성하므로 배포(prod) 대상(E2E_BASE_URL)에서는 건너뛴다.
// 사전 조건: scripts/seed-e2e-accounts.sh 로 계정·세션을 심어야 한다.
test.describe("Create listing", () => {
  test.skip(!!process.env.E2E_BASE_URL, "로컬 백엔드 전용 플로우(쓰기 발생)");

  test.beforeEach(async ({ page }) => {
    await signInAs(page, E2E_SELLER);
  });

  test("로그인 셀러가 상품을 등록하면 상세로 이동한다", async ({ page }) => {
    await page.goto("/sell");

    const title = `E2E 테스트 세트 ${Date.now()}`;
    await page.getByLabel("제목").fill(title);
    // "설명서 포함" 체크박스와 부분 일치하므로 정확 일치로 집는다.
    await page.getByLabel("설명", { exact: true }).fill("E2E 자동 등록 상품");
    await page.getByLabel("가격 (원)").fill("12345");

    // 파일 업로드(1x1 PNG) — MinIO 업로드 후 미리보기 표시까지 대기
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

    await expect(page).toHaveURL(/\/listings\/.+/);
    await expect(page.getByRole("heading", { name: title })).toBeVisible();
    // Next의 라우트 알림(#__next-route-announcer__)이 문서 제목을 그대로 읽어 주는데,
    // 제목에 가격이 들어 있어 부분 일치로는 가격 노드와 함께 2개가 잡힌다(strict mode 위반).
    // 알림 텍스트는 "<제목> — ₩12,345 · GoLe"라 정확 일치에는 걸리지 않는다.
    await expect(page.getByText("₩12,345", { exact: true })).toBeVisible();
  });
});
