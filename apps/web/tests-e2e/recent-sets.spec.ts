import { expect, test } from "@playwright/test";
import { parseRecentSets, visitRecentSet } from "../src/features/recent-sets/model/history";

const key = "gole.recent-sets.v1";
const previous = { setNumber: "10305", name: "사자 기사의 성" };

test("최근 본 세트는 재방문을 앞으로 옮기고 이름을 갱신함", () => {
  const entries = [{ setNumber: "10307", name: "에펠탑" }, previous];
  expect(visitRecentSet(entries, { ...previous, name: "갱신한 이름" })).toEqual([
    { ...previous, name: "갱신한 이름" },
    entries[0],
  ]);
});

test("최근 본 세트는 최대 8개만 저장함", () => {
  const entries = Array.from({ length: 8 }, (_, i) => ({
    setNumber: String(i),
    name: `세트 ${i}`,
  }));
  const next = visitRecentSet(entries, previous);
  expect(next).toHaveLength(8);
  expect(next[0]).toEqual(previous);
  expect(next.some((entry) => entry.setNumber === "7")).toBe(false);
});

test("잘못된 기록과 위험한 경로를 버리고 중복과 추가 필드를 제거함", () => {
  for (const raw of [null, "{", "null", "{}", "x".repeat(32_769)]) {
    expect(parseRecentSets(raw)).toEqual([]);
  }
  expect(
    parseRecentSets(
      JSON.stringify([
        { setNumber: "../admin", name: "경로" },
        { setNumber: "12", name: " " },
        { setNumber: "13", name: "x".repeat(301) },
        null,
        { ...previous, secret: "보관 금지" },
        previous,
      ]),
    ),
  ).toEqual([previous]);
});

test("빈 탐색 기록은 검색 화면에 표시하지 않음", async ({ page }) => {
  await page.goto("/search");
  await expect(page.getByRole("heading", { name: "상품 탐색", exact: true })).toBeVisible();
  await expect(page.getByRole("region", { name: "최근 본 세트" })).toHaveCount(0);
});

test("상세 방문을 기록하고 검색에서 다시 열며 중복 없이 유지함", async ({ page }) => {
  await page.addInitScript(
    ({ key, previous }) => {
      if (!sessionStorage.getItem(key)) sessionStorage.setItem(key, JSON.stringify([previous]));
    },
    { key, previous },
  );
  await page.goto("/sets/10307");
  const history = page.getByRole("region", { name: "최근 본 세트" });
  await expect(history.getByRole("link", { name: /사자 기사의 성/ })).toBeVisible();
  await expect(history.getByRole("link", { name: /10307/ })).toHaveCount(0);
  await page.goto("/search");
  await expect(history.getByRole("link")).toHaveCount(2);
  await history.getByRole("link", { name: /10307/ }).click();
  await expect(page).toHaveURL(/\/sets\/10307$/);
  await page.goto("/search");
  await page.reload();
  await expect(history.getByRole("link")).toHaveCount(2);
  await expect(history.getByRole("link").first()).toHaveAttribute("href", "/sets/10307");
  await history.getByRole("button", { name: /10305.*기록 삭제/ }).click();
  await expect(history.getByRole("link")).toHaveCount(1);
  await history.getByRole("button", { name: "기록 전체 지우기" }).click();
  await expect(history.getByRole("link")).toHaveCount(0);
  await expect(history.getByRole("status")).toHaveText("탐색 기록을 모두 지웠어요.");
  expect(await page.evaluate((key) => sessionStorage.getItem(key), key)).toBeNull();
});

test("저장소 차단은 상세 조회를 막지 않음", async ({ page }) => {
  await page.addInitScript(() => {
    Object.defineProperty(window, "sessionStorage", {
      get() {
        throw new DOMException("차단", "SecurityError");
      },
    });
  });
  await page.goto("/sets/10307");
  await expect(page.getByRole("heading", { level: 1 })).toContainText("10307");
  await expect(page.getByRole("region", { name: "최근 본 세트" })).toHaveCount(0);
});

test("손상된 기록은 상세 재방문으로 복구하며 새 탭과 공유하지 않음", async ({ page, context }) => {
  await page.goto("/search");
  await page.evaluate((key) => sessionStorage.setItem(key, "잘못된 JSON"), key);
  await page.goto("/sets/10307");
  await page.goto("/search");
  await expect(page.getByRole("region", { name: "최근 본 세트" }).getByRole("link")).toHaveCount(1);
  const other = await context.newPage();
  await other.goto("/search");
  await expect(other.getByRole("heading", { name: "상품 탐색", exact: true })).toBeVisible();
  await expect(other.getByRole("region", { name: "최근 본 세트" })).toHaveCount(0);
  await other.close();
  await page.getByRole("button", { name: "기록 전체 지우기" }).click();
  await page.reload();
  await expect(page.getByRole("region", { name: "최근 본 세트" })).toHaveCount(0);
});

test("삭제 실패는 성공으로 표시하지 않으며 기존 링크를 유지함", async ({ page }) => {
  await page.addInitScript(
    ({ key, previous }) => {
      sessionStorage.setItem(key, JSON.stringify([previous]));
      Storage.prototype.removeItem = () => {
        throw new DOMException("차단", "SecurityError");
      };
    },
    { key, previous },
  );
  await page.goto("/search");
  const history = page.getByRole("region", { name: "최근 본 세트" });
  await history.getByRole("button", { name: "기록 전체 지우기" }).click();
  await expect(history.getByRole("status")).toContainText("지우지 못했어요");
  await expect(history.getByRole("link")).toHaveCount(1);
});
