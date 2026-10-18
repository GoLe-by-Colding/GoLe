import { expect, test } from "@playwright/test";
import sitemap from "../src/app/sitemap";

test("사이트맵은 100건을 넘는 매물·상점·게시글을 모두 포함한다", async () => {
  const originalFetch = globalThis.fetch;
  const listings = Array.from({ length: 101 }, (_, index) => ({
    id: `listing-${index}`,
    sellerId: `seller-${index}`,
    catalogSetNumber: `set-${index}`,
  }));
  const posts = Array.from({ length: 101 }, (_, index) => ({ id: `post-${index}` }));
  const pageRequests: URL[] = [];

  globalThis.fetch = async (input) => {
    const url = new URL(String(input));
    if (url.pathname === "/api/v1/listings") {
      return Response.json(listings);
    }
    if (url.pathname === "/api/v1/catalog/sets/featured") {
      return Response.json([]);
    }
    if (url.pathname === "/api/v1/community/posts/page") {
      pageRequests.push(url);
      const offset = Number(url.searchParams.get("beforeId") ?? 0);
      const items = posts.slice(offset, offset + 50);
      return Response.json({
        items,
        nextCursor:
          offset + items.length < posts.length
            ? { beforeCreatedAt: "2026-10-18T00:00:00Z", beforeId: String(offset + items.length) }
            : null,
      });
    }
    throw new Error(`예상하지 못한 요청: ${url.pathname}`);
  };

  try {
    const routes = await sitemap();
    expect(routes.some(({ url }) => url.endsWith("/listings/listing-100"))).toBe(true);
    expect(routes.some(({ url }) => url.endsWith("/shops/seller-100"))).toBe(true);
    expect(routes.some(({ url }) => url.endsWith("/community/post-100"))).toBe(true);
    expect(routes.some(({ url }) => url.endsWith("/sets/set-100"))).toBe(true);
    expect(pageRequests).toHaveLength(3);
    expect(pageRequests[1]?.searchParams.get("beforeCreatedAt")).toBe("2026-10-18T00:00:00Z");
  } finally {
    globalThis.fetch = originalFetch;
  }
});
