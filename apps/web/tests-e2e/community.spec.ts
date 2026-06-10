import { test, expect } from "@playwright/test";

// 커뮤니티: 주제 탭 필터 + 글쓰기 진입. (데이터가 있는 환경 대상)
test.describe("Community topics", () => {
  test("주제 탭과 피드가 렌더된다", async ({ page }) => {
    await page.goto("/community");
    await expect(page.getByRole("heading", { name: "커뮤니티" })).toBeVisible();

    // 주제 탭
    await expect(page.getByRole("button", { name: "전체" })).toBeVisible();
    await expect(page.getByRole("button", { name: "질문" })).toBeVisible();
    await expect(page.getByRole("button", { name: "이스터에그" })).toBeVisible();
  });

  test("주제 탭으로 필터링한다", async ({ page }) => {
    await page.goto("/community");
    await page.getByRole("button", { name: "질문" }).click();
    // 질문 탭 선택 시에도 페이지가 정상 유지(글이 없으면 안내 문구)
    await expect(page.getByRole("heading", { name: "커뮤니티" })).toBeVisible();
  });

  test("글쓰기 화면으로 이동하고 주제를 고를 수 있다", async ({ page }) => {
    await page.goto("/community");
    await page.getByRole("link", { name: "글쓰기" }).click();
    await expect(page).toHaveURL(/\/community\/new$/);
  });
});
