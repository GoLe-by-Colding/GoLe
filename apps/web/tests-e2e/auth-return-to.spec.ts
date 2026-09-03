import { test, expect, type Page } from "@playwright/test";

/**
 * 로그인 복귀 경로(`?returnTo=`) 허용목록과 컬렉션 왕복 흐름.
 *
 * 복귀 경로는 사용자가 조작할 수 있는 입력이므로 같은 오리진 상대 경로만 통과해야 한다.
 * 검증을 통과한 값만 안내 문구(`return-to-notice`)로 노출되므로, 그 존재 여부로 판정을 관찰한다.
 */

const notice = (page: Page) => page.getByTestId("return-to-notice");

async function mockMe(page: Page, role: "USER" | "ADMIN"): Promise<void> {
  await page.route("**/api/v1/accounts/me", async (route) => {
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({ accountId: "acc-1", email: "tester@gole.test", role }),
    });
  });
  // 이 스펙은 권한/복귀 경로가 대상이다. HttpOnly 쿠키가 없는 합성 세션에서 헤더의
  // 알림 폴링이 실제 API 401을 받아 세션을 지우지 않도록 전역 요청을 격리한다.
  await page.route(/\/api\/v1\/users\/[^/]+\/notifications\/unread-count(?:\?.*)?$/, (route) =>
    route.fulfill({ json: { unreadCount: 0 } }),
  );
}

/** 로그인 API를 흉내 내 실제 폼 제출로 복귀 동작까지 확인한다. */
async function mockSignIn(page: Page, role: "USER" | "ADMIN"): Promise<void> {
  await page.route("**/api/v1/accounts/sessions", async (route) => {
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({ accountId: "acc-1", sessionToken: "session-token", role }),
    });
  });
}

test.describe("returnTo 허용목록", () => {
  const accepted = ["/collection", "/prices?set=10307", "/orders/abc#top"];

  for (const target of accepted) {
    test(`같은 오리진 상대 경로는 허용한다: ${target}`, async ({ page }) => {
      await page.goto(`/login?returnTo=${encodeURIComponent(target)}`);
      await expect(notice(page)).toHaveAttribute("data-return-to", target);
    });
  }

  const rejected: readonly (readonly [string, string])[] = [
    ["절대 URL", "https://evil.test/steal"],
    ["프로토콜 상대 경로", "//evil.test/steal"],
    ["백슬래시 우회", "/\\evil.test/steal"],
    ["javascript 스킴", "javascript:alert(1)"],
    ["data 스킴", "data:text/html,<script>alert(1)</script>"],
    ["상대 경로(슬래시 없음)", "collection"],
    ["인증 루프", "/login"],
    ["인증 루프(회원가입)", "/signup?returnTo=/collection"],
  ];

  for (const [label, target] of rejected) {
    test(`거부한다: ${label}`, async ({ page }) => {
      await page.goto(`/login?returnTo=${encodeURIComponent(target)}`);
      await expect(page.getByRole("heading", { name: "로그인" })).toBeVisible();
      await expect(notice(page)).toHaveCount(0);
    });
  }

  test("빈 값이면 안내 없이 기본 로그인 화면이다", async ({ page }) => {
    await page.goto("/login?returnTo=");
    await expect(page.getByRole("heading", { name: "로그인" })).toBeVisible();
    await expect(notice(page)).toHaveCount(0);
  });

  test("탭을 전환해도 복귀 경로를 유지한다", async ({ page }) => {
    await page.goto(`/login?returnTo=${encodeURIComponent("/collection")}`);
    await page.getByRole("tab", { name: "회원가입" }).click();
    await expect(page).toHaveURL(/\/signup\?returnTo=%2Fcollection$/);
    await expect(notice(page)).toHaveAttribute("data-return-to", "/collection");
  });

  test("안내 문구가 포커스를 받아 문맥을 먼저 알린다", async ({ page }) => {
    await page.goto(`/login?returnTo=${encodeURIComponent("/collection")}`);
    await expect(notice(page)).toBeFocused();
  });

  test("알 수 없는 경로 문자열은 로그인 안내에 그대로 노출하지 않는다", async ({ page }) => {
    const injected = "/무료-쿠폰을-받으세요";
    await page.goto(`/login?returnTo=${encodeURIComponent(injected)}`);
    await expect(notice(page)).toContainText("이전 화면");
    await expect(notice(page)).not.toContainText(injected);
  });
});

test.describe("관리자 복귀 경로는 ADMIN에게만", () => {
  test("환영 화면은 세션이 확인되기 전 관리자 복귀 CTA를 활성화하지 않는다", async ({ page }) => {
    await page.goto(`/signup?welcome=1&returnTo=${encodeURIComponent("/admin")}`);
    await expect(page.getByRole("button", { name: "세션 확인 중…" })).toBeDisabled();
  });

  test("확인된 ADMIN 세션의 환영 CTA는 관리자 복귀 경로를 유지한다", async ({ page }) => {
    await page.addInitScript(() => {
      window.localStorage.setItem(
        "gole.session",
        JSON.stringify({ accountId: "admin-1", sessionToken: "", role: "ADMIN" }),
      );
    });
    await mockMe(page, "ADMIN");
    await page.route("**/api/admin/overview", async (route) => {
      await route.fulfill({ contentType: "application/json", body: '{"pendingReports":0}' });
    });
    await page.goto(`/signup?welcome=1&returnTo=${encodeURIComponent("/admin")}`);
    await page.getByRole("button", { name: "시작하기" }).click();
    await expect(page).toHaveURL(/\/admin$/);
  });

  test("USER로 로그인하면 관리자 복귀 요청을 무시하고 홈으로 간다", async ({ page }) => {
    await mockMe(page, "USER");
    await mockSignIn(page, "USER");

    await page.goto(`/login?returnTo=${encodeURIComponent("/admin/reports")}`);
    await page.getByLabel("이메일").fill("user@gole.test");
    await page.getByLabel("비밀번호").fill("password1");
    await page.getByRole("button", { name: "로그인" }).click();

    await expect(page).toHaveURL(/\/$/);
  });

  test("ADMIN으로 로그인하면 요청한 콘솔 경로로 돌아간다", async ({ page }) => {
    await mockMe(page, "ADMIN");
    await mockSignIn(page, "ADMIN");
    await page.route("**/api/admin/**", (route) =>
      route.fulfill({
        json: new URL(route.request().url()).pathname.endsWith("/reports") ? [] : {},
      }),
    );

    await page.goto(`/login?returnTo=${encodeURIComponent("/admin/reports")}`);
    await page.getByLabel("이메일").fill("admin@gole.test");
    await page.getByLabel("비밀번호").fill("password1");
    await page.getByRole("button", { name: "로그인" }).click();

    await expect(page).toHaveURL(/\/admin\/reports$/);
  });
});

test.describe("컬렉션 로그인 왕복", () => {
  test("비로그인 컬렉션 CTA가 returnTo를 달고 로그인으로 보낸다", async ({ page }) => {
    await page.goto("/collection");

    const cta = page.getByRole("link", { name: "로그인하고 시작하기" });
    await expect(cta).toHaveAttribute(
      "href",
      `/login?returnTo=${encodeURIComponent("/collection")}`,
    );
  });

  test("컬렉션 조회 실패를 화면에서 복구하고 미처리 오류를 남기지 않는다", async ({ page }) => {
    const pageErrors: Error[] = [];
    let recovered = false;
    page.on("pageerror", (error) => pageErrors.push(error));
    await page.addInitScript(() => {
      window.localStorage.setItem(
        "gole.session",
        JSON.stringify({
          accountId: "acc-1",
          email: "tester@gole.test",
          sessionToken: "session-token",
          role: "USER",
        }),
      );
    });
    await mockMe(page, "USER");
    await page.route("**/api/v1/collections/acc-1/items", (route) =>
      recovered
        ? route.fulfill({ json: [] })
        : route.fulfill({
            status: 503,
            json: { code: "TEMPORARY", message: "컬렉션 서버가 잠시 응답하지 않습니다." },
          }),
    );
    await page.route("**/api/v1/collections/acc-1/estimate", (route) =>
      recovered
        ? route.fulfill({ json: { ownedEstimatedValue: 0 } })
        : route.fulfill({
            status: 503,
            json: { code: "TEMPORARY", message: "컬렉션 서버가 잠시 응답하지 않습니다." },
          }),
    );

    await page.goto("/collection");
    await expect(page.getByText("컬렉션을 불러오지 못했어요")).toBeVisible();
    await expect(page.getByText("컬렉션 서버가 잠시 응답하지 않습니다.")).toBeVisible();

    recovered = true;
    await page.getByRole("button", { name: "다시 시도" }).click();
    await expect(page.getByText("아직 담은 세트가 없어요")).toBeVisible();
    expect(pageErrors).toEqual([]);
  });

  test("로그인에 성공하면 컬렉션으로 돌아오고 뒤로가기가 로그인으로 되돌아가지 않는다", async ({
    page,
  }) => {
    await mockMe(page, "USER");
    await mockSignIn(page, "USER");
    await page.route("**/api/v1/collections/*/items", (route) => route.fulfill({ json: [] }));
    await page.route("**/api/v1/collections/*/estimate", (route) =>
      route.fulfill({ json: { ownedEstimatedValue: 0 } }),
    );

    await page.goto("/collection");
    await page.getByRole("link", { name: "로그인하고 시작하기" }).click();
    await expect(page).toHaveURL(/\/login\?returnTo=%2Fcollection$/);
    await expect(notice(page)).toBeVisible();

    await page.getByLabel("이메일").fill("user@gole.test");
    await page.getByLabel("비밀번호").fill("password1");
    await page.getByRole("button", { name: "로그인" }).click();

    await expect(page).toHaveURL(/\/collection$/);

    // /login은 replace로 대체되므로 뒤로가기가 인증 화면을 다시 열지 않는다.
    await page.goBack();
    await expect(page).not.toHaveURL(/\/login/);
  });

  test("회원가입과 이메일 인증을 거쳐도 컬렉션 복귀 경로를 유지한다", async ({ page }) => {
    await page.route("**/api/v1/policies/current", (route) =>
      route.fulfill({
        json: {
          termsVersion: "2026-09-03",
          privacyVersion: "2026-09-03",
          minimumAge: 14,
        },
      }),
    );
    await page.route("**/api/v1/accounts", async (route) => {
      await route.fulfill({ contentType: "application/json", body: '{"accountId":"acc-1"}' });
    });
    await page.route("**/api/v1/accounts/verification", async (route) => {
      await route.fulfill({ status: 204, body: "" });
    });
    await mockSignIn(page, "USER");
    await mockMe(page, "USER");
    await page.route("**/api/v1/collections/*/items", (route) => route.fulfill({ json: [] }));
    await page.route("**/api/v1/collections/*/estimate", (route) =>
      route.fulfill({ json: { ownedEstimatedValue: 0 } }),
    );

    await page.goto(`/signup?returnTo=${encodeURIComponent("/collection")}`);
    await page.getByRole("checkbox", { name: /이용약관/ }).check();
    await page.getByRole("checkbox", { name: /개인정보처리방침/ }).check();
    await page.getByRole("checkbox", { name: /만 14세 이상/ }).check();
    await page.getByLabel("이메일").fill("new@gole.test");
    await page.getByLabel("비밀번호").fill("password1");
    await page.getByRole("button", { name: "가입하기" }).click();
    await expect(page).toHaveURL(/\/verify\?email=new%40gole\.test&returnTo=%2Fcollection$/);

    await page.getByLabel("인증 코드").fill("123456");
    await page.getByRole("button", { name: "인증하기" }).click();
    await expect(page).toHaveURL(/\/login\?returnTo=%2Fcollection$/);

    await page.getByLabel("이메일").fill("new@gole.test");
    await page.getByLabel("비밀번호").fill("password1");
    await page.getByRole("button", { name: "로그인" }).click();
    await expect(page).toHaveURL(/\/collection$/);
  });
});
