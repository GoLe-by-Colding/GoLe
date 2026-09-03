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
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==",
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
