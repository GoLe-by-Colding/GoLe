import { test, expect } from "@playwright/test";

// 백엔드(MongoDB replica set + MinIO) 기동 필요. 데이터를 생성하므로 배포(prod) 대상에서는 건너뛴다.
test.describe("Purchase flow", () => {
  test.skip(!!process.env.E2E_BASE_URL, "로컬 백엔드 전용 플로우(쓰기 발생)");

  test.beforeEach(async ({ page }) => {
    await page.addInitScript(() => {
      window.localStorage.setItem(
        "gole.session",
        JSON.stringify({ accountId: "e2e-buyer", sessionToken: "t", role: "USER" }),
      );
    });
  });

  test("상품 등록 → 구매 → 결제 → 구매확정", async ({ page }) => {
    // 1) 셀러가 상품 등록
    await page.goto("/sell");
    const title = `구매플로우 ${Date.now()}`;
    await page.getByLabel("제목").fill(title);
    await page.getByLabel("설명").fill("E2E");
    await page.getByLabel("가격 (원)").fill("99000");
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

    // 2) 구매하기 → 주문 생성 → 체크아웃 이동
    await page.getByRole("button", { name: "구매하기" }).click();
    await expect(page).toHaveURL(/\/orders\/.+/);
    await expect(page.getByTestId("order-status")).toHaveText("결제 대기");

    // 3) 결제 → 판매자 정산 대기
    await page.getByRole("button", { name: "결제하기" }).click();
    await expect(page.getByTestId("order-status")).toHaveText("결제 완료(정산 대기)");

    // 4) 구매 확정 → 거래 완료
    await page.getByRole("button", { name: "구매 확정" }).click();
    await expect(page.getByTestId("order-status")).toHaveText("거래 완료");
  });
});
