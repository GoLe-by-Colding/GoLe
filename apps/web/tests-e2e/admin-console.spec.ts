import { test, expect, request } from "@playwright/test";

/**
 * 운영자 콘솔 — 권한 게이트와 정지 실효성. (admin-console 요구사항 1, 6)
 *
 * 화면 게이트는 백엔드 없이도 검증되고, API 가드는 백엔드가 떠 있을 때만 의미가 있으므로
 * 백엔드 미기동 시에는 해당 테스트를 skip 한다(로컬 프론트 전용 실행을 막지 않기 위해).
 */

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";
const ADMIN_EMAIL = process.env.GOLE_ADMIN_EMAIL ?? "";
const ADMIN_PASSWORD = process.env.GOLE_ADMIN_PASSWORD ?? "";

test.describe("운영자 콘솔 — 화면 게이트", () => {
  test("비로그인 사용자에게 /admin은 로그인 안내를 보여준다 (R1.3)", async ({ page }) => {
    await page.goto("/admin");
    await expect(page.getByRole("heading", { name: "관리자 로그인이 필요합니다" })).toBeVisible();
    await expect(page.getByRole("link", { name: "로그인" })).toBeVisible();
  });

  test("비로그인 사용자에게 콘솔 하위 경로도 동일하게 차단된다 (R1.3)", async ({ page }) => {
    for (const path of ["/admin/reports", "/admin/accounts", "/admin/audit"]) {
      await page.goto(path);
      await expect(page.getByRole("heading", { name: "관리자 로그인이 필요합니다" })).toBeVisible();
    }
  });

  test("일반 사용자에게 /admin은 권한 없음 안내를 보여준다 (R1.4)", async ({ page }) => {
    await page.addInitScript(() => {
      window.localStorage.setItem(
        "gole.session",
        JSON.stringify({ accountId: "u-1", sessionToken: "fake", role: "USER" }),
      );
    });
    await page.goto("/admin");
    await expect(page.getByRole("heading", { name: "접근 권한이 없습니다" })).toBeVisible();
    // 운영 데이터가 새어나오지 않아야 한다.
    await expect(page.getByRole("heading", { name: "운영자 콘솔" })).toHaveCount(0);
  });

  test("일반 사용자에게 온사이트 어드민 바가 렌더되지 않는다 (R1.7)", async ({ page }) => {
    await page.addInitScript(() => {
      window.localStorage.setItem(
        "gole.session",
        JSON.stringify({ accountId: "u-1", sessionToken: "fake", role: "USER" }),
      );
    });
    await page.goto("/");
    await expect(page.getByText("ADMIN", { exact: true })).toHaveCount(0);
  });

  test("/admin은 색인되지 않는다 (R1.5)", async ({ page }) => {
    await page.goto("/admin");
    await expect(page.locator('meta[name="robots"]')).toHaveAttribute(
      "content",
      /noindex.*nofollow/,
    );
  });
});

test.describe("관리자 API 가드", () => {
  test.skip(() => !process.env.E2E_WITH_BACKEND, "백엔드 대상 테스트는 E2E_WITH_BACKEND=1 에서만 실행");

  test("토큰이 없으면 401 (R1.2)", async () => {
    const ctx = await request.newContext({ baseURL: API_BASE });
    expect((await ctx.get("/api/admin/overview")).status()).toBe(401);
    expect((await ctx.get("/api/admin/audit")).status()).toBe(401);
    expect((await ctx.get("/api/admin/accounts")).status()).toBe(401);
    await ctx.dispose();
  });

  test("잘못된 토큰이면 401 (R1.2)", async () => {
    const ctx = await request.newContext({ baseURL: API_BASE });
    const res = await ctx.get("/api/admin/overview", {
      headers: { Authorization: "Bearer not-a-real-token" },
    });
    expect(res.status()).toBe(401);
    await ctx.dispose();
  });

  test("ADMIN 토큰이면 대시보드와 감사 로그를 조회할 수 있다 (R2.2, R8.3)", async () => {
    test.skip(ADMIN_EMAIL === "", "GOLE_ADMIN_EMAIL/PASSWORD 미설정");
    const ctx = await request.newContext({ baseURL: API_BASE });

    const signIn = await ctx.post("/api/v1/accounts/sessions", {
      data: { email: ADMIN_EMAIL, password: ADMIN_PASSWORD },
    });
    expect(signIn.ok()).toBeTruthy();
    const { sessionToken, role } = await signIn.json();
    expect(role).toBe("ADMIN");

    const headers = { Authorization: `Bearer ${sessionToken}` };
    const overview = await ctx.get("/api/admin/overview", { headers });
    expect(overview.status()).toBe(200);
    expect(await overview.json()).toHaveProperty("gmv");

    const audit = await ctx.get("/api/admin/audit?limit=5", { headers });
    expect(audit.status()).toBe(200);
    expect(Array.isArray(await audit.json())).toBeTruthy();

    await ctx.dispose();
  });

  test("사유 없는 모더레이션 요청은 400으로 거부된다 (R4.2, R8)", async () => {
    test.skip(ADMIN_EMAIL === "", "GOLE_ADMIN_EMAIL/PASSWORD 미설정");
    const ctx = await request.newContext({ baseURL: API_BASE });
    const signIn = await ctx.post("/api/v1/accounts/sessions", {
      data: { email: ADMIN_EMAIL, password: ADMIN_PASSWORD },
    });
    const { sessionToken } = await signIn.json();

    const res = await ctx.post("/api/admin/listings/does-not-matter/takedown", {
      headers: { Authorization: `Bearer ${sessionToken}` },
      data: { reason: "" },
    });
    expect(res.status()).toBe(400);
    await ctx.dispose();
  });

  test("자기 자신 정지는 ADMIN_SELF_TARGET으로 거부된다 (R6.8)", async () => {
    test.skip(ADMIN_EMAIL === "", "GOLE_ADMIN_EMAIL/PASSWORD 미설정");
    const ctx = await request.newContext({ baseURL: API_BASE });
    const signIn = await ctx.post("/api/v1/accounts/sessions", {
      data: { email: ADMIN_EMAIL, password: ADMIN_PASSWORD },
    });
    const { sessionToken, accountId } = await signIn.json();

    const res = await ctx.post(`/api/admin/accounts/${accountId}/suspend`, {
      headers: { Authorization: `Bearer ${sessionToken}` },
      data: { reason: "self-test" },
    });
    expect(res.status()).toBe(400);
    expect((await res.json()).code).toBe("ADMIN_SELF_TARGET");
    await ctx.dispose();
  });
});
