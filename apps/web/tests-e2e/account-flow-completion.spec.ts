import { expect, test, type Page } from "@playwright/test";

const ACCOUNT_ID = "account-flow-e2e";
const NOTIFICATIONS = `**/api/v1/users/${ACCOUNT_ID}/notifications`;
const DEVICES = "**/api/v1/notifications/devices*";

/** 실제 계정·발송을 건드리지 않는 UI 회귀. 실제 API 검증과 구분한다. */
async function prepareAccount(page: Page, accountId = ACCOUNT_ID): Promise<void> {
  await page.addInitScript((accountId) => {
    localStorage.setItem(
      "gole.session",
      JSON.stringify({ accountId, sessionToken: "", role: "USER" }),
    );
  }, accountId);
  await page.route("**/api/v1/accounts/me/onboarding", (route) =>
    route.fulfill({
      json: {
        required: false,
        legacyExempt: true,
        nicknameCompleted: true,
        nickname: "계정흐름",
        phoneCompleted: false,
        interestTagsCompleted: true,
        interestTags: [],
        privacyConsented: true,
        marketingConsented: false,
      },
    }),
  );
  const notifications = "**/api/v1/users/*/notifications";
  await page.route(`${notifications}/unread-count`, (route) =>
    route.fulfill({ json: { unreadCount: 0 } }),
  );
  await page.route(notifications, (route) => route.fulfill({ json: [] }));
}

function notification(id: string, link: string | null = null) {
  return {
    id,
    type: "COMMENT_ADDED",
    message: `${id} 알림 내용`,
    link,
    read: false,
    createdAt: "2026-10-21T00:00:00Z",
  };
}

test("전체 읽음 성공 직후 헤더 배지를 갱신한다", async ({ page }) => {
  await prepareAccount(page);
  let unreadCount = 2;
  await page.route(`${NOTIFICATIONS}/unread-count`, (route) =>
    route.fulfill({ json: { unreadCount } }),
  );
  await page.route(NOTIFICATIONS, (route) =>
    route.fulfill({ json: [notification("첫째"), notification("둘째")] }),
  );
  await page.route(`${NOTIFICATIONS}/read-all`, (route) => {
    unreadCount = 0;
    return route.fulfill({ status: 204 });
  });
  await page.goto("/notifications");
  await expect(page.getByRole("link", { name: "알림 2건", exact: true })).toBeVisible();
  await page.getByRole("button", { name: "전체 읽음" }).click();
  await expect(page.getByRole("link", { name: "알림 2건", exact: true })).toHaveCount(0);
  await expect(page.getByText("첫째 알림 내용")).toBeVisible();
});

test("한 건 읽음 실패는 목록을 보존하고 재시도 성공 시 배지를 갱신한다", async ({ page }) => {
  await prepareAccount(page);
  let unreadCount = 1;
  let attempts = 0;
  await page.route(`${NOTIFICATIONS}/unread-count`, (route) =>
    route.fulfill({ json: { unreadCount } }),
  );
  await page.route(NOTIFICATIONS, (route) => route.fulfill({ json: [notification("다시읽기")] }));
  await page.route(`${NOTIFICATIONS}/*/read`, (route) => {
    attempts += 1;
    if (attempts === 1) {
      return route.fulfill({ status: 503, json: { code: "TEMPORARY", message: "읽음 저장 실패" } });
    }
    unreadCount = 0;
    return route.fulfill({ status: 204 });
  });
  await page.goto("/notifications");
  await expect(page.getByRole("link", { name: "알림 1건", exact: true })).toBeVisible();
  await page.getByRole("button", { name: /다시읽기 알림 내용/ }).click();
  await expect(page.getByRole("alert").filter({ hasText: "읽음 저장 실패" })).toBeVisible();
  await page.getByRole("button", { name: /다시읽기 알림 내용/ }).click();
  await expect.poll(() => attempts).toBe(2);
  await expect(page.getByRole("link", { name: "알림 1건", exact: true })).toHaveCount(0);
});

test("알림 설정을 연속 변경해도 저장을 겹치지 않고 새로고침 후 유지한다", async ({ page }) => {
  await prepareAccount(page);
  let categories = [
    { key: "trade", label: "거래 진행", enabled: true, mandatory: true },
    { key: "offer", label: "가격 제안·입찰", enabled: true, mandatory: false },
    { key: "watch", label: "찜·관심 세트", enabled: true, mandatory: false },
  ];
  let active = 0;
  let maximumActive = 0;
  let writes = 0;
  await page.route(`**/api/v1/users/${ACCOUNT_ID}/notification-preferences`, async (route) => {
    if (route.request().method() === "GET") {
      await route.fulfill({ json: { categories } });
      return;
    }
    active += 1;
    maximumActive = Math.max(maximumActive, active);
    const { enabled } = route.request().postDataJSON() as { enabled: Record<string, boolean> };
    // API의 조회 후 전체 문서 저장을 재현한다. 병렬 요청이면 둘 다 같은 이전 값을 읽는다.
    const next = categories.map((category) => ({
      ...category,
      enabled: enabled[category.key] ?? category.enabled,
    }));
    if (enabled["offer"] !== undefined) await new Promise((resolve) => setTimeout(resolve, 400));
    categories = next;
    active -= 1;
    writes += 1;
    await route.fulfill({ json: { categories } });
  });
  await page.goto("/profile/notifications");
  await page.getByRole("switch", { name: "가격 제안·입찰" }).click();
  await page.getByRole("switch", { name: "찜·관심 세트" }).click();
  await expect.poll(() => writes).toBe(2);
  expect(maximumActive).toBe(1);
  await page.reload();
  await expect(page.getByRole("switch", { name: "가격 제안·입찰" })).not.toBeChecked();
  await expect(page.getByRole("switch", { name: "찜·관심 세트" })).not.toBeChecked();
});

test("WebView 새로고침의 세션 복원을 로그아웃으로 처리하지 않는다", async ({ page }) => {
  await prepareAccount(page);
  await page.addInitScript((accountId) => {
    Object.assign(window, { __GOLE_APP_PUSH__: { token: "fake-device-token", platform: "IOS" } });
    localStorage.setItem(
      "gole.app-push.registered",
      JSON.stringify({ accountId, token: "fake-device-token" }),
    );
  }, ACCOUNT_ID);
  const methods: string[] = [];
  await page.route(DEVICES, (route) => {
    methods.push(route.request().method());
    return route.fulfill({ status: 204 });
  });
  await page.goto("/notifications");
  await expect(page.getByText("아직 알림이 없어요")).toBeVisible();
  await page.waitForLoadState("networkidle");
  expect(methods).not.toContain("DELETE");
});

for (const resumeEvent of ["focus", "online"]) {
  test(`푸시 완료 캐시가 있어도 진입과 ${resumeEvent} 복귀에서 서버 등록을 복구한다`, async ({
    page,
  }) => {
    await prepareAccount(page);
    await page.addInitScript((accountId) => {
      Object.assign(window, { __GOLE_APP_PUSH__: { token: "fake-device-token", platform: "IOS" } });
      localStorage.setItem(
        "gole.app-push.registered",
        JSON.stringify({ accountId, token: "fake-device-token", confirmed: true }),
      );
    }, ACCOUNT_ID);
    let serverOwner: string | null = null;
    let posts = 0;
    await page.route(DEVICES, (route) => {
      expect(route.request().method()).toBe("POST");
      posts += 1;
      serverOwner = ACCOUNT_ID;
      return route.fulfill({ status: 204 });
    });
    await page.goto("/notifications");
    await expect.poll(() => serverOwner).toBe(ACCOUNT_ID);
    await page.waitForLoadState("networkidle");
    expect(posts).toBe(1);
    // 종료된 문서의 지연 DELETE처럼 현재 문서에 이벤트 없이 서버 등록만 사라진 상태다.
    serverOwner = null;
    await page.evaluate((eventName) => {
      window.dispatchEvent(new Event(eventName));
      window.dispatchEvent(new Event(eventName));
    }, resumeEvent);
    await expect.poll(() => serverOwner).toBe(ACCOUNT_ID);
    await page.waitForLoadState("networkidle");
    expect(posts).toBe(2);
  });
}

test("WebView 푸시 등록 중 로그아웃하면 늦게 완료된 토큰도 해제한다", async ({ page }) => {
  await prepareAccount(page);
  await page.addInitScript(() => {
    Object.assign(window, { __GOLE_APP_PUSH__: { token: "fake-device-token", platform: "IOS" } });
  });
  let finishRegistration: (() => void) | undefined;
  const registering = new Promise<void>((resolve) => {
    finishRegistration = resolve;
  });
  const methods: string[] = [];
  await page.route(DEVICES, async (route) => {
    methods.push(route.request().method());
    if (route.request().method() === "POST") await registering;
    await route.fulfill({ status: 204 });
  });
  await page.goto("/notifications");
  await expect.poll(() => methods.includes("POST")).toBe(true);
  await page.evaluate(() => {
    localStorage.removeItem("gole.session");
    window.dispatchEvent(new Event("gole:session-change"));
  });
  finishRegistration?.();
  await expect.poll(() => methods).toEqual(["POST", "DELETE"]);
  await expect
    .poll(() => page.evaluate(() => localStorage.getItem("gole.app-push.registered")))
    .toBeNull();
});

test("WebView 토큰이 교체되면 이전 토큰을 해제한 뒤 새 토큰을 등록한다", async ({ page }) => {
  await prepareAccount(page);
  await page.addInitScript(() => {
    Object.assign(window, { __GOLE_APP_PUSH__: { token: "old-device-token", platform: "IOS" } });
  });
  const calls: string[] = [];
  await page.route(DEVICES, async (route) => {
    const request = route.request();
    const token =
      request.method() === "DELETE"
        ? new URL(request.url()).searchParams.get("token")
        : (request.postDataJSON() as { token: string }).token;
    calls.push(`${request.method()}:${token}`);
    await route.fulfill({ status: 204 });
  });
  await page.goto("/notifications");
  await expect.poll(() => calls).toEqual(["POST:old-device-token"]);
  await page.evaluate(() => {
    Object.assign(window, { __GOLE_APP_PUSH__: { token: "new-device-token", platform: "IOS" } });
    window.dispatchEvent(new Event("gole:app-push-token"));
  });
  await expect
    .poll(() => calls)
    .toEqual(["POST:old-device-token", "DELETE:old-device-token", "POST:new-device-token"]);
});

test("WebView 등록 응답을 잃은 뒤 로그아웃해도 시도한 토큰을 해제한다", async ({ page }) => {
  await prepareAccount(page);
  await page.addInitScript(() => {
    Object.assign(window, { __GOLE_APP_PUSH__: { token: "fake-device-token", platform: "IOS" } });
  });
  let posts = 0;
  let deletes = 0;
  await page.route(DEVICES, (route) => {
    if (route.request().method() === "POST") {
      posts += 1;
      return route.abort("failed");
    }
    deletes += 1;
    return route.fulfill({ status: 204 });
  });
  await page.goto("/notifications");
  await expect.poll(() => posts).toBeGreaterThan(0);
  await page.waitForLoadState("networkidle");
  await page.evaluate(() => {
    localStorage.removeItem("gole.session");
    window.dispatchEvent(new Event("gole:session-change"));
  });
  await expect.poll(() => deletes).toBe(1);
  await expect
    .poll(() => page.evaluate(() => localStorage.getItem("gole.app-push.registered")))
    .toBeNull();
});

test("이전 토큰이 남은 WebView가 다른 WebView의 최신 토큰을 해제하지 않는다", async ({
  page,
  context,
}) => {
  await prepareAccount(page);
  await page.addInitScript(() => {
    Object.assign(window, { __GOLE_APP_PUSH__: { token: "old-device-token", platform: "IOS" } });
  });
  const calls: string[] = [];
  const serverTokens = new Set<string>();
  await context.route(DEVICES, (route) => {
    const request = route.request();
    const token =
      request.method() === "DELETE"
        ? new URL(request.url()).searchParams.get("token")!
        : (request.postDataJSON() as { token: string }).token;
    calls.push(`${request.method()}:${token}`);
    // 잘못된 무효화가 무한 재등록으로 이어져도 실패 뒤 요청이 계속 쌓이지 않게 한다.
    if (calls.length > 40) return route.abort("failed");
    if (request.method() === "DELETE") serverTokens.delete(token);
    else serverTokens.add(token);
    return route.fulfill({ status: 204 });
  });
  await page.goto("/notifications");
  await expect.poll(() => serverTokens.has("old-device-token")).toBe(true);
  await page.waitForLoadState("networkidle");

  const nextView = await context.newPage();
  try {
    await prepareAccount(nextView);
    await nextView.addInitScript(() => {
      Object.assign(window, { __GOLE_APP_PUSH__: { token: "new-device-token", platform: "IOS" } });
    });
    await nextView.goto("/notifications");
    await expect.poll(() => calls.includes("POST:new-device-token")).toBe(true);
    const oldRegistrations = calls.filter((call) => call === "POST:old-device-token").length;
    await page.evaluate(() => window.dispatchEvent(new Event("focus")));
    await expect
      .poll(() => calls.filter((call) => call === "POST:old-device-token").length)
      .toBeGreaterThan(oldRegistrations);
    expect(calls).not.toContain("DELETE:new-device-token");
    expect(serverTokens.has("new-device-token")).toBe(true);

    // 같은 문서가 실제로 새 토큰을 받으면 자기 이전 토큰만 정리한다.
    await page.evaluate(() => {
      Object.assign(window, { __GOLE_APP_PUSH__: { token: "new-device-token", platform: "IOS" } });
      window.dispatchEvent(new Event("gole:app-push-token"));
    });
    await expect.poll(() => calls.includes("DELETE:old-device-token")).toBe(true);
    await page.waitForLoadState("networkidle");
    await nextView.waitForLoadState("networkidle");
    expect(calls).not.toContain("DELETE:new-device-token");
    expect(serverTokens.has("new-device-token")).toBe(true);
    expect(calls.length).toBeLessThan(20);

    await page.evaluate(() => {
      localStorage.removeItem("gole.session");
      window.dispatchEvent(new Event("gole:session-change"));
    });
    await expect.poll(() => serverTokens.size).toBe(0);
  } finally {
    await nextView.close();
  }
});

test("WebView 로그아웃 토큰 해제 실패는 기록을 보존하고 포커스 복귀에서 재시도한다", async ({
  page,
}) => {
  await prepareAccount(page);
  await page.addInitScript((accountId) => {
    Object.assign(window, { __GOLE_APP_PUSH__: { token: "fake-device-token", platform: "IOS" } });
    localStorage.setItem(
      "gole.app-push.registered",
      JSON.stringify({ accountId, token: "fake-device-token" }),
    );
  }, ACCOUNT_ID);
  let fail = true;
  let deletes = 0;
  await page.route(DEVICES, (route) => {
    if (route.request().method() === "POST") return route.fulfill({ status: 204 });
    deletes += 1;
    return fail
      ? route.fulfill({ status: 503, json: { code: "TEMPORARY", message: "일시 실패" } })
      : route.fulfill({ status: 204 });
  });
  await page.goto("/notifications");
  await expect(page.getByText("아직 알림이 없어요")).toBeVisible();
  await page.waitForLoadState("networkidle");
  await page.evaluate(() => {
    localStorage.removeItem("gole.session");
    window.dispatchEvent(new Event("gole:session-change"));
  });
  await expect.poll(() => deletes).toBe(1);
  await page.waitForLoadState("networkidle");
  expect(await page.evaluate(() => localStorage.getItem("gole.app-push.registered") !== null)).toBe(
    true,
  );
  fail = false;
  await page.evaluate(() => window.dispatchEvent(new Event("focus")));
  await expect.poll(() => deletes).toBe(2);
  await expect
    .poll(() => page.evaluate(() => localStorage.getItem("gole.app-push.registered")))
    .toBeNull();
});

test("WebView 해제 응답이 유실돼도 같은 계정으로 다시 로그인하면 등록을 복구한다", async ({
  page,
}) => {
  await prepareAccount(page);
  await page.addInitScript((accountId) => {
    Object.assign(window, { __GOLE_APP_PUSH__: { token: "fake-device-token", platform: "IOS" } });
    localStorage.setItem(
      "gole.app-push.registered",
      JSON.stringify({ accountId, token: "fake-device-token", confirmed: true }),
    );
  }, ACCOUNT_ID);
  let serverOwner: string | null = ACCOUNT_ID;
  let deletes = 0;
  let posts = 0;
  await page.route(DEVICES, (route) => {
    if (route.request().method() === "DELETE") {
      deletes += 1;
      serverOwner = null;
      return route.abort("failed");
    }
    posts += 1;
    serverOwner = ACCOUNT_ID;
    return route.fulfill({ status: 204 });
  });
  await page.goto("/notifications");
  await expect(page.getByText("아직 알림이 없어요")).toBeVisible();
  await page.waitForLoadState("networkidle");
  posts = 0;
  await page.evaluate(() => {
    localStorage.removeItem("gole.session");
    window.dispatchEvent(new Event("gole:session-change"));
  });
  await expect.poll(() => deletes).toBe(1);
  await page.waitForLoadState("networkidle");
  expect(serverOwner).toBeNull();
  await page.evaluate((accountId) => {
    localStorage.setItem(
      "gole.session",
      JSON.stringify({ accountId, sessionToken: "", role: "USER" }),
    );
    window.dispatchEvent(new Event("gole:session-change"));
    window.dispatchEvent(new Event("focus"));
  }, ACCOUNT_ID);
  await expect.poll(() => posts).toBe(1);
  expect(serverOwner).toBe(ACCOUNT_ID);
});

for (const { delayedRegistrationResponse, hasRandomUuid } of [
  { delayedRegistrationResponse: false, hasRandomUuid: true },
  { delayedRegistrationResponse: true, hasRandomUuid: true },
  { delayedRegistrationResponse: false, hasRandomUuid: false },
  { delayedRegistrationResponse: true, hasRandomUuid: false },
]) {
  test(`다른 WebView의 등록${delayedRegistrationResponse ? " 응답 대기" : " 완료"} 중 늦은 해제도 복구한다${hasRandomUuid ? "" : " (HTTP 개발 환경)"}`, async ({
    page,
    context,
  }) => {
    if (!hasRandomUuid) {
      await context.addInitScript(() => {
        Object.defineProperty(window.crypto, "randomUUID", {
          value: undefined,
          configurable: true,
        });
      });
    }
    await prepareAccount(page);
    await page.addInitScript((accountId) => {
      Object.assign(window, { __GOLE_APP_PUSH__: { token: "fake-device-token", platform: "IOS" } });
      localStorage.setItem(
        "gole.app-push.registered",
        JSON.stringify({ accountId, token: "fake-device-token", confirmed: true }),
      );
    }, ACCOUNT_ID);
    let finishDeletion: (() => void) | undefined;
    const deletion = new Promise<void>((resolve) => {
      finishDeletion = resolve;
    });
    let finishRegistration: (() => void) | undefined;
    const registration = new Promise<void>((resolve) => {
      finishRegistration = resolve;
    });
    const nextAccount = "account-flow-next";
    let activeAccount = ACCOUNT_ID;
    let serverOwner: string | null = ACCOUNT_ID;
    let deletes = 0;
    let completedDeletes = 0;
    let posts = 0;
    await context.route(DEVICES, async (route) => {
      if (route.request().method() === "DELETE") {
        deletes += 1;
        await deletion;
        serverOwner = null;
        completedDeletes += 1;
      } else {
        posts += 1;
        serverOwner = activeAccount;
        if (activeAccount === nextAccount && posts === 1 && delayedRegistrationResponse)
          await registration;
      }
      await route.fulfill({ status: 204 });
    });
    await page.goto("/notifications");
    await expect(page.getByText("아직 알림이 없어요")).toBeVisible();
    await page.waitForLoadState("networkidle");
    posts = 0;
    await page.evaluate(() => {
      localStorage.removeItem("gole.session");
      window.dispatchEvent(new Event("gole:session-change"));
    });
    await expect.poll(() => deletes).toBe(1);

    activeAccount = nextAccount;
    const nextView = await context.newPage();
    await prepareAccount(nextView, nextAccount);
    await nextView.addInitScript(() => {
      Object.assign(window, { __GOLE_APP_PUSH__: { token: "fake-device-token", platform: "IOS" } });
    });
    await nextView.goto("/notifications");
    await expect.poll(() => posts).toBe(1);
    if (!delayedRegistrationResponse) await nextView.waitForLoadState("networkidle");
    expect(serverOwner).toBe(nextAccount);
    finishDeletion?.();
    await expect.poll(() => completedDeletes).toBe(1);
    finishRegistration?.();
    await page.evaluate(() => window.dispatchEvent(new Event("focus")));
    await nextView.evaluate(() => window.dispatchEvent(new Event("focus")));
    await expect.poll(() => posts).toBeGreaterThan(1);
    await nextView.waitForLoadState("networkidle");
    expect(serverOwner).toBe(nextAccount);
    await nextView.close();
  });
}

test("온보딩 조회 일시 실패는 같은 화면에서 재시도한다", async ({ page }) => {
  await prepareAccount(page);
  let recovered = false;
  await page.route("**/api/v1/accounts/me/onboarding", (route) => {
    return !recovered
      ? route.fulfill({ status: 503, json: { code: "TEMPORARY", message: "일시적인 조회 실패" } })
      : route.fulfill({
          json: {
            required: true,
            legacyExempt: false,
            nicknameCompleted: false,
            nickname: null,
            phoneCompleted: false,
            interestTagsCompleted: false,
            interestTags: [],
            privacyConsented: false,
            marketingConsented: false,
          },
        });
  });
  await page.goto("/onboarding?returnTo=%2Fprofile");
  await expect(page.getByText("일시적인 조회 실패")).toBeVisible();
  recovered = true;
  await page.getByRole("button", { name: "다시 시도" }).click();
  await expect(page.getByRole("heading", { name: /닉네임/ })).toBeVisible();
  await expect(page).toHaveURL(/\/onboarding\?returnTo=%2Fprofile$/);
});
