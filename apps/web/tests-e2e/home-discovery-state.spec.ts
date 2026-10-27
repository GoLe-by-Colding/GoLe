import { expect, test } from "@playwright/test";
import { homeIntroduction, loadHomeCollection } from "../src/views/home/model/discovery-state";

test("정상 빈 목록을 조회 실패로 바꾸지 않는다", async () => {
  expect(await loadHomeCollection(async () => [])).toEqual([]);
});

test("조회 실패를 빈 목록으로 숨기지 않는다", async () => {
  expect(
    await loadHomeCollection(async () => {
      throw new Error("unavailable");
    }),
  ).toBeNull();
});

test("실패한 구획이 다른 구획의 결과를 없애지 않는다", async () => {
  const results = await Promise.all([
    loadHomeCollection(async () => {
      throw new Error("unavailable");
    }),
    loadHomeCollection(async () => [{ id: "post-1" }]),
  ]);
  expect(results).toEqual([null, [{ id: "post-1" }]]);
});

test("거래 준비 전 소개는 실제 가능한 활동을 안내한다", () => {
  expect(homeIntroduction(false).label).not.toContain("마켓");
  expect(homeIntroduction(false).description).toContain("브릭 이야기를");
  expect(homeIntroduction(false).description).not.toContain("새로운 주인");
  expect(homeIntroduction(true).description).toContain("새로운 주인");
});
