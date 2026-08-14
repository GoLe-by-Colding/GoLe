import { test, expect } from "@playwright/test";

// 백엔드(MongoDB replica set + MinIO) 기동 필요. 데이터를 생성하므로 배포(prod) 대상에서는 건너뛴다.
test.describe("Purchase flow", () => {
  test.skip(!!process.env.E2E_BASE_URL, "로컬 백엔드 전용 플로우(쓰기 발생)");

  test.beforeEach(async ({ page }) => {
    // 이미 세션이 있으면 덮지 않는다 — 테스트 도중 구매자 계정으로 전환한 뒤
    // reload 해도 셀러 세션으로 되돌아가지 않도록.
    await page.addInitScript(() => {
      if (!window.localStorage.getItem("gole.session")) {
        window.localStorage.setItem(
          "gole.session",
          JSON.stringify({ accountId: "e2e-seller", sessionToken: "t", role: "USER" }),
        );
      }
    });
  });

  test("상품 등록 → 구매 → 결제 → 구매확정", async ({ page }) => {
    // 1) 셀러가 상품 등록
    await page.goto("/sell");
    const title = `구매플로우 ${Date.now()}`;
    await page.getByLabel("제목").fill(title);
    // "설명서 포함" 체크박스와 부분 일치하므로 정확 일치로 집는다.
    await page.getByLabel("설명", { exact: true }).fill("E2E");
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

    // 2) 자기 매물은 구매 동선이 열리지 않는다(자기거래 금지)
    await expect(page.getByRole("button", { name: "내 매물" })).toBeDisabled();
    await expect(page.getByRole("button", { name: "구매하기" })).toHaveCount(0);

    // 3) 구매자 계정으로 전환
    await page.evaluate(() => {
      window.localStorage.setItem(
        "gole.session",
        JSON.stringify({ accountId: "e2e-buyer", sessionToken: "t", role: "USER" }),
      );
    });
    await page.reload();

    // 4) 구매하기 → 주문 생성 → 체크아웃 이동
    await page.getByRole("button", { name: "구매하기" }).click();
    await expect(page).toHaveURL(/\/orders\/.+/);
    await expect(page.getByTestId("order-status")).toHaveText("결제 대기");
    // 포트원 미설정 환경에서는 서버 스텁이 승인하므로 결제수단 선택이 뜨면 안 된다.
    await expect(page.getByTestId("payment-method-choice")).toHaveCount(0);

    // 5) 결제 → 에스크로 보관
    // 결제 전에는 결제수단 줄이 아예 없어야 한다("미결제"와 "확인 불가"는 다른 뜻).
    await expect(page.getByTestId("order-payment-method")).toHaveCount(0);
    await page.getByRole("button", { name: "결제하기" }).click();
    await expect(page.getByTestId("order-status")).toHaveText("결제 완료(에스크로 보관)");
    // 승인 응답에 실려 온 결제수단이 주문에 새겨진다. 로컬 스텁 PG는 카드로 승인한다.
    await expect(page.getByTestId("order-payment-method")).toHaveText("카드");

    // 6) 구매 확정 → 거래 완료
    await page.getByRole("button", { name: "구매 확정" }).click();
    await expect(page.getByTestId("order-status")).toHaveText("거래 완료");
  });
});
