import { expect, test } from "@playwright/test";
import { emptyListingsContent } from "../src/views/home/model/empty-listings";

test("판매 준비가 닫혔으면 빈 목록에서도 판매를 유도하지 않는다", () => {
  const content = emptyListingsContent(false);
  expect(content.href).toBe("/community");
  expect(content.title).toContain("준비");
  expect(content.description).toContain("본인확인");
  expect(content.action).not.toBe("판매 시작하기");
});

test("판매 준비가 열리면 첫 판매 등록으로 연결한다", () => {
  const content = emptyListingsContent(true);
  expect(content.href).toBe("/sell");
  expect(content.action).toBe("판매 시작하기");
});
