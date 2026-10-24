import { expect, test } from "@playwright/test";

// 전용 API(8093)와 웹(3003)에서 실행한다. 사용자 프리뷰의 API를 중단하지 않고 SSR 실패를 재현한다.
test.describe("검색 서버 응답 복구", () => {
  test.skip(process.env.E2E_SEARCH_FIXTURE !== "1", "검색 전용 읽기 서버에서 실행");

  test("현재 조건으로 다시 조회해 오류 화면을 실제 상품 목록으로 복구한다", async ({ page }) => {
    const query = `__e2e_retry_${crypto.randomUUID()}`;
    await page.goto(`/search?query=${query}&category=set&minPrice=10000`);
    await expect(
      page.getByRole("heading", { name: "상품 목록을 불러오지 못했어요" }),
    ).toBeVisible();
    await expect(page.getByText("검색 결과 0개")).toHaveCount(0);
    const before = page.url();
    await page.getByRole("button", { name: "다시 불러오기" }).click();
    await expect(page.getByTestId("listing-card")).toContainText("다시 찾은 브릭 세트");
    await expect(page.getByRole("heading", { name: "상품 목록을 불러오지 못했어요" })).toHaveCount(
      0,
    );
    expect(page.url()).toBe(before);
    // Next의 전환 중 보존된 숨김 트리 대신 현재 접근 가능한 입력을 검사한다.
    await expect(page.getByRole("textbox", { name: "최소가 (원)" })).toHaveValue("10000");
  });

  test("예약·판매 완료·판매 종료와 부품 누락을 상품 사진 옆에 고지한다", async ({ page }) => {
    await page.goto("/search?query=__e2e_card_states");
    const cards = page.getByTestId("listing-card");
    await expect(cards).toHaveCount(4);
    for (const [status, label] of [
      ["reserved", "예약 중"],
      ["sold", "판매 완료"],
      ["deleted", "판매 종료"],
    ]) {
      const card = cards.filter({ hasText: `브릭 ${status}` });
      await expect(card.getByText(label!, { exact: true })).toBeVisible();
      await expect(card.getByText("부품 누락", { exact: true })).toBeVisible();
    }
    await expect(cards.filter({ hasText: "브릭 active" })).not.toContainText("판매 완료");
  });

  test("출처 세트 번호가 있는 부품·미니피그·창작품도 종류를 숨기지 않는다", async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 900 });
    await page.goto("/search?query=__e2e_non_set_cards");
    for (const [category, label] of [
      ["parts", "부품"],
      ["minifig", "미니피그"],
      ["moc", "창작품(MOC)"],
    ] as const) {
      const card = page.getByTestId("listing-card").filter({ hasText: `출처 세트 ${category}` });
      await expect(card.getByText(label, { exact: true })).toBeVisible();
      await expect(card.getByText("#10307", { exact: true })).toBeVisible();
    }
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth),
    ).toBeLessThanOrEqual(1);
  });
});
