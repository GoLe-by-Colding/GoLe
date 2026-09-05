import { expect, test } from "@playwright/test";
import { NextRequest } from "next/server.js";
import { proxy } from "../src/proxy";

test.describe.configure({ mode: "serial" });

test.describe("동적 SEO 경로 HTTP 상태", () => {
  for (const [label, path] of [
    ["세트", "/sets/e2e-missing-set-404"],
    ["매물", "/listings/e2e-missing-listing-404"],
    ["게시글", "/community/e2e-missing-post-404"],
  ] as const) {
    test(`없는 ${label} 상세는 실제 HTTP 404를 반환한다`, async ({ request }) => {
      const response = await request.get(path, { headers: { Accept: "text/html" } });

      expect(response.status()).toBe(404);
      await expect(response.text()).resolves.toContain("페이지를 찾을 수 없어요");
    });
  }

  test("상위 API 5xx는 404 rewrite로 위장하지 않는다", async () => {
    const originalFetch = globalThis.fetch;
    globalThis.fetch = async () => new Response(null, { status: 503 });

    try {
      const response = await proxy(
        new NextRequest("http://localhost:3000/listings/upstream-unavailable"),
      );

      expect(response.status).toBe(200);
      expect(response.headers.get("x-middleware-next")).toBe("1");
      expect(response.headers.get("x-middleware-rewrite")).toBeNull();
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  test("커뮤니티 글쓰기 정적 경로는 게시글 존재 검사에서 제외한다", async () => {
    const originalFetch = globalThis.fetch;
    let probeCalled = false;
    globalThis.fetch = async () => {
      probeCalled = true;
      return new Response(null, { status: 404 });
    };

    try {
      const response = await proxy(new NextRequest("http://localhost:3000/community/new"));

      expect(probeCalled).toBe(false);
      expect(response.headers.get("x-middleware-next")).toBe("1");
      expect(response.headers.get("x-middleware-rewrite")).toBeNull();
    } finally {
      globalThis.fetch = originalFetch;
    }
  });
});
