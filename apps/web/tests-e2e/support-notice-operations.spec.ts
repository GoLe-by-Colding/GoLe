import { expect, test, type Page } from "@playwright/test";

const now = new Date().toISOString();
const day = 86_400_000;
const ticket = {
  roomId: "notice-room",
  requesterId: "notice-user",
  title: "거래 문의 안내 확인",
  category: "TRADE",
  status: "IN_PROGRESS",
  assigneeId: "notice-admin",
  createdAt: new Date(Date.now() - 4 * day).toISOString(),
  updatedAt: now,
  resolvedAt: null,
  progressDueAt: new Date(Date.now() - 3_600_000).toISOString(),
  responseDueAt: new Date(Date.now() + 6 * day).toISOString(),
  progressNotifiedAt: null as string | null,
  resultNotifiedAt: null as string | null,
};
const summary = {
  generatedAt: now,
  total: 122,
  progress: { pending: 120, dueSoon: 0, overdue: 2, onTime: 0, late: 0 },
  result: { pending: 120, dueSoon: 0, overdue: 2, onTime: 0, late: 0 },
  activeProgress: { pending: 120, dueSoon: 0, overdue: 1, onTime: 0, late: 0 },
  activeResult: { pending: 120, dueSoon: 0, overdue: 1, onTime: 0, late: 0 },
  resolvedMissingProgress: 1,
  resolvedMissingResult: 1,
};

async function mockSupport(page: Page) {
  await page.addInitScript(() => {
    localStorage.setItem(
      "gole.session",
      JSON.stringify({ accountId: "notice-admin", sessionToken: "notice-fixture", role: "ADMIN" }),
    );
  });
  await page.route("**/api/v1/accounts/me", (route) =>
    route.fulfill({
      json: { accountId: "notice-admin", email: "notice@gole.test", role: "ADMIN" },
    }),
  );
  await page.route("**/api/v1/accounts/me/onboarding", (route) =>
    route.fulfill({ json: { required: false, legacyExempt: true } }),
  );
  await page.route(/\/api\/v1\/users\/[^/]+\/notifications\/unread-count(?:\?.*)?$/, (route) =>
    route.fulfill({ json: { unreadCount: 0 } }),
  );
  await page.route("**/api/admin/overview", (route) =>
    route.fulfill({
      json: {
        counts: {},
        gmv: 0,
        ordersByStatus: {},
        activeListings: 0,
        pendingReports: 0,
        unassignedSupportTickets: 0,
        pendingSettlements: 0,
      },
    }),
  );
  await page.route(/\/api\/admin\/support(?:\?.*)?$/, (route) => route.fulfill({ json: [ticket] }));
  await page.route("**/api/admin/support/notice-summary", (route) =>
    route.fulfill({ json: summary }),
  );
  await page.route(/\/api\/admin\/support\/notice-room\/messages(?:\?.*)?$/, (route) =>
    route.fulfill({
      json: [
        {
          id: "question",
          roomId: ticket.roomId,
          senderId: ticket.requesterId,
          content: "처리 상황을 알려 주세요.",
          sentAt: now,
        },
      ],
    }),
  );
  await page.route("**/api/admin/support/notice-room/notes", (route) =>
    route.fulfill({ json: [] }),
  );
  await page.route("**/api/admin/support/notice-room/notices", (route) =>
    route.fulfill({ json: [] }),
  );
}

test.beforeEach(async ({ page }) => mockSupport(page));

test("전체 초과 수에서 이동하면 목록 제한·기존 필터와 무관하게 서버의 오래된 문의 큐를 연다", async ({
  page,
}) => {
  const requests: URL[] = [];
  await page.route(/\/api\/admin\/support(?:\?.*)?$/, (route) => {
    requests.push(new URL(route.request().url()));
    return route.fulfill({ json: [ticket] });
  });
  await page.goto("/admin/support");
  await expect(page.getByText(/보존 문의 전체 122건/)).toBeVisible();
  await page.getByRole("combobox", { name: "상태", exact: true }).selectOption("RESOLVED");
  await page.getByRole("combobox", { name: "유형", exact: true }).selectOption("TRADE");
  await page.getByRole("button", { name: "결과·처리방안 미해결 초과 1건", exact: true }).click();
  await expect(page.getByRole("combobox", { name: "안내 목표" })).toHaveValue("RESULT_OVERDUE");
  await expect(page.getByRole("combobox", { name: "상태", exact: true })).toHaveValue("ALL");
  await expect(page.getByRole("combobox", { name: "유형", exact: true })).toHaveValue("ALL");
  await expect
    .poll(() =>
      requests.some(
        (url) =>
          url.searchParams.get("notice") === "RESULT_OVERDUE" &&
          url.searchParams.get("noticeScope") === "ACTIVE" &&
          !url.searchParams.has("status") &&
          !url.searchParams.has("category"),
      ),
    )
    .toBe(true);
  await expect(page.getByText("1개 문의 · 오래된 접수순")).toBeVisible();
  await expect(page.getByText(/진행 안내 1일 미만 초과 · 미기록/).first()).toBeVisible();
});

test("결과 안내 답변이 저장된 뒤에만 실제 이력과 두 목표의 이행 시각을 표시한다", async ({
  page,
}) => {
  let sent = false;
  let finishSending = () => {};
  const sending = new Promise<void>((resolve) => {
    finishSending = resolve;
  });
  const bodies: unknown[] = [];
  const message = {
    id: "notice-message",
    roomId: ticket.roomId,
    senderId: "notice-admin",
    content: "확인한 결과와 다음 처리방안을 안내합니다.",
    sentAt: now,
  };
  await page.route(/\/api\/admin\/support(?:\?.*)?$/, (route) =>
    route.fulfill({
      json: [
        {
          ...ticket,
          ...(sent
            ? { status: "WAITING_USER", progressNotifiedAt: now, resultNotifiedAt: now }
            : {}),
        },
      ],
    }),
  );
  await page.route(/\/api\/admin\/support\/notice-room\/messages(?:\?.*)?$/, async (route) => {
    if (route.request().method() === "POST") {
      bodies.push(route.request().postDataJSON());
      await sending;
      sent = true;
      return route.fulfill({ json: message });
    }
    return route.fulfill({ json: sent ? [message] : [] });
  });
  await page.route("**/api/admin/support/notice-room/notices", (route) =>
    route.fulfill({
      json: sent
        ? [
            {
              kind: "RESULT",
              messageId: message.id,
              senderId: message.senderId,
              sentAt: message.sentAt,
            },
          ]
        : [],
    }),
  );
  await page.goto("/admin/support");
  await page.getByRole("combobox", { name: "답변 종류" }).selectOption("RESULT");
  await page.getByRole("textbox", { name: "사용자에게 보낼 답변" }).fill(message.content);
  await page.getByRole("button", { name: /^(보내기|진행 안내 보내기|결과 안내 보내기)$/ }).click();
  await expect(page.getByRole("button", { name: "답변 전송 중…" })).toBeDisabled();
  finishSending();
  await expect.poll(() => bodies).toEqual([{ content: message.content, noticeKind: "RESULT" }]);
  await expect(page.getByRole("textbox", { name: "사용자에게 보낼 답변" })).toHaveValue("");
  await expect(page.getByRole("combobox", { name: "답변 종류" })).toHaveValue("GENERAL");
  await expect(
    page
      .getByRole("region", { name: "실제 안내 이력" })
      .getByText("결과·처리방안 안내", { exact: true }),
  ).toBeVisible();
  await expect(page.getByText("진행 안내 목표 후 안내").first()).toBeVisible();
  await expect(page.getByText("결과·방안 목표 내 안내").first()).toBeVisible();
  await expect(
    page.getByRole("status").filter({ hasText: "결과·처리방안 안내를 대화에 남겼습니다." }),
  ).toBeVisible();
  const dates = page.getByRole("region", { name: "접수와 안내 목표" });
  await expect(dates.locator(`time[datetime="${ticket.createdAt}"]`)).toBeVisible();
  await expect(dates.locator(`time[datetime="${ticket.progressDueAt}"]`)).toBeVisible();
  await expect(dates.locator(`time[datetime="${ticket.responseDueAt}"]`)).toBeVisible();
});

test("전송 실패 시 답변과 종류를 보존하고 키보드 재시도에서 일반 답변 호환도 유지한다", async ({
  page,
}) => {
  const bodies: unknown[] = [];
  await page.route(/\/api\/admin\/support\/notice-room\/messages(?:\?.*)?$/, (route) => {
    if (route.request().method() !== "POST") return route.fulfill({ json: [] });
    bodies.push(route.request().postDataJSON());
    if (bodies.length === 1)
      return route.fulfill({
        status: 503,
        json: { code: "TEMPORARY", message: "잠시 후 다시 보내 주세요." },
      });
    return route.fulfill({
      json: {
        id: "retry",
        roomId: ticket.roomId,
        senderId: "notice-admin",
        content: "확인 중인 내용을 안내합니다.",
        sentAt: now,
      },
    });
  });
  await page.goto("/admin/support");
  await page.getByRole("combobox", { name: "답변 종류" }).selectOption("PROGRESS");
  const reply = page.getByRole("textbox", { name: "사용자에게 보낼 답변" });
  await reply.fill("확인 중인 내용을 안내합니다.");
  await reply.press("Tab");
  await expect(
    page.getByRole("button", { name: /^(보내기|진행 안내 보내기|결과 안내 보내기)$/ }),
  ).toBeFocused();
  await page.keyboard.press("Enter");
  await expect(
    page.getByRole("alert").filter({ hasText: "잠시 후 다시 보내 주세요." }),
  ).toBeVisible();
  await expect(reply).toHaveValue("확인 중인 내용을 안내합니다.");
  await expect(page.getByRole("combobox", { name: "답변 종류" })).toHaveValue("PROGRESS");
  await expect(
    page.getByRole("region", { name: "실제 안내 이력" }).getByText("기록된 안내가 없습니다."),
  ).toBeVisible();
  await page.getByRole("button", { name: /^(보내기|진행 안내 보내기|결과 안내 보내기)$/ }).click();
  await expect(reply).toHaveValue("");
  expect(bodies).toEqual([
    { content: "확인 중인 내용을 안내합니다.", noticeKind: "PROGRESS" },
    { content: "확인 중인 내용을 안내합니다.", noticeKind: "PROGRESS" },
  ]);
  await reply.fill("일반 확인 질문입니다.");
  await page.getByRole("button", { name: /^(보내기|진행 안내 보내기|결과 안내 보내기)$/ }).click();
  await expect.poll(() => bodies[2]).toEqual({ content: "일반 확인 질문입니다." });
});

test("집계 조회 실패를 0건으로 숨기지 않고 재시도한다", async ({ page }) => {
  let recover = false;
  await page.route("**/api/admin/support/notice-summary", (route) =>
    recover
      ? route.fulfill({ json: summary })
      : route.fulfill({ status: 503, json: { message: "현황을 확인할 수 없습니다." } }),
  );
  await page.goto("/admin/support");
  const panel = page.getByRole("region", { name: "안내 이행 현황" });
  await expect(panel.getByRole("alert")).toBeVisible();
  await expect(panel.getByText(/보존 문의 전체/)).toHaveCount(0);
  recover = true;
  await panel.getByRole("button", { name: "안내 현황 다시 확인" }).click();
  await expect(panel.getByText(/보존 문의 전체 122건/)).toBeVisible();
});

test("안내 이력만 실패해도 대화는 유지하고 빈 이력으로 오인시키지 않는다", async ({ page }) => {
  let recover = false;
  await page.route("**/api/admin/support/notice-room/notices", (route) =>
    recover
      ? route.fulfill({ json: [] })
      : route.fulfill({ status: 503, json: { message: "안내 이력을 확인할 수 없습니다." } }),
  );
  await page.goto("/admin/support");
  await expect(page.getByText("처리 상황을 알려 주세요.")).toBeVisible();
  const history = page.getByRole("region", { name: "실제 안내 이력" });
  await expect(history.getByRole("alert")).toBeVisible();
  await expect(history.getByText("기록된 안내가 없습니다.")).toHaveCount(0);
  recover = true;
  await history.getByRole("button", { name: "안내 이력 다시 확인" }).click();
  await expect(history.getByText("기록된 안내가 없습니다.")).toBeVisible();
});

test("담당자가 아닌 관리자는 실제 안내 이력과 답변 본문을 조회하지 않는다", async ({ page }) => {
  let privateReads = 0;
  await page.route(/\/api\/admin\/support(?:\?.*)?$/, (route) =>
    route.fulfill({ json: [{ ...ticket, assigneeId: "another-admin" }] }),
  );
  await page.route(
    /\/api\/admin\/support\/notice-room\/(messages|notes|notices)(?:\?.*)?$/,
    (route) => {
      privateReads += 1;
      return route.fulfill({ json: [] });
    },
  );
  await page.goto("/admin/support");
  await expect(page.getByText("다른 관리자가 담당 중입니다")).toBeVisible();
  await expect(page.getByRole("combobox", { name: "답변 종류" })).toHaveCount(0);
  await expect(page.getByRole("region", { name: "실제 안내 이력" })).toHaveCount(0);
  expect(privateReads).toBe(0);
});

test("해결한 구버전 문의를 안내 완료로 꾸미지 않고 재개 경로를 제공한다", async ({ page }) => {
  await page.route(/\/api\/admin\/support(?:\?.*)?$/, (route) =>
    route.fulfill({ json: [{ ...ticket, status: "RESOLVED", resolvedAt: now }] }),
  );
  await page.goto("/admin/support");
  await expect(page.getByText(/해결 처리했지만 결과 안내 기록은 없습니다/)).toBeVisible();
  await expect(page.getByRole("button", { name: "문의 재개", exact: true })).toBeEnabled();
  await expect(page.getByRole("textbox", { name: "사용자에게 보낼 답변" })).toBeDisabled();
  await expect(page.getByText("진행 안내 기록 확인").first()).toBeVisible();
  await expect(page.getByText(/초과 · 미기록/)).toHaveCount(0);
  await expect(page.getByText("진행 안내 목표 내 안내")).toHaveCount(0);
});

test("해결 미기록은 미해결 업무와 분리된 서버 큐로 연결하고 같은 버튼 재선택도 복구한다", async ({
  page,
}) => {
  const requests: URL[] = [];
  await page.route(/\/api\/admin\/support(?:\?.*)?$/, (route) => {
    requests.push(new URL(route.request().url()));
    return route.fulfill({ json: [ticket] });
  });
  await page.goto("/admin/support");
  const button = page.getByRole("button", { name: "결과 안내 미기록 1건", exact: true });
  await button.click();
  await expect
    .poll(() =>
      requests.some(
        (url) =>
          url.searchParams.get("notice") === "RESULT_MISSING" &&
          url.searchParams.get("noticeScope") === "RESOLVED",
      ),
    )
    .toBe(true);
  await expect(button).toHaveAttribute("aria-pressed", "true");
  await expect(page.getByRole("button", { name: /거래 문의 안내 확인/ })).toBeVisible();
  const before = requests.length;
  await button.click();
  await expect.poll(() => requests.length).toBeGreaterThan(before);
  await expect(page.getByText("1개 문의 · 오래된 접수순")).toBeVisible();
});

test("문의 목록 초기 실패와 성공한 빈 목록을 구분하고 재시도한다", async ({ page }) => {
  let recover = false;
  await page.route(/\/api\/admin\/support(?:\?.*)?$/, (route) =>
    recover
      ? route.fulfill({ json: [] })
      : route.fulfill({ status: 503, json: { message: "목록 조회 실패" } }),
  );
  await page.goto("/admin/support");
  await expect(page.getByText("문의 목록 확인 필요")).toBeVisible();
  await expect(page.getByText("해당 상태의 문의가 없어요.")).toHaveCount(0);
  recover = true;
  await page.getByRole("button", { name: "문의 목록 다시 확인" }).click();
  await expect(page.getByText("해당 상태의 문의가 없어요.")).toBeVisible();
});

test("새 필터 조회가 실패하면 이전 문의를 새 결과처럼 보이지 않고 초안은 복원한다", async ({
  page,
}) => {
  await page.route(/\/api\/admin\/support(?:\?.*)?$/, (route) =>
    new URL(route.request().url()).searchParams.get("category") === "PAYMENT"
      ? route.fulfill({ status: 503, json: { message: "새 조건 조회 실패" } })
      : route.fulfill({ json: [ticket] }),
  );
  await page.goto("/admin/support");
  const reply = page.getByRole("textbox", { name: "사용자에게 보낼 답변" });
  await reply.fill("이동해도 남겨야 할 안내 초안");
  await page.getByRole("combobox", { name: "답변 종류" }).selectOption("PROGRESS");
  await page.getByRole("combobox", { name: "유형", exact: true }).selectOption("PAYMENT");
  await expect(page.getByRole("alert").filter({ hasText: "새 조건 조회 실패" })).toBeVisible();
  await expect(page.getByRole("button", { name: /거래 문의 안내 확인/ })).toHaveCount(0);
  await expect(page.getByRole("heading", { name: ticket.title, exact: true })).toHaveCount(0);
  await page.getByRole("combobox", { name: "유형", exact: true }).selectOption("ALL");
  await page.getByRole("button", { name: /거래 문의 안내 확인/ }).click();
  await expect(reply).toHaveValue("이동해도 남겨야 할 안내 초안");
  await expect(page.getByRole("combobox", { name: "답변 종류" })).toHaveValue("PROGRESS");
});

test("대화 초기 조회 중과 실패를 대화 없음으로 표시하지 않는다", async ({ page }) => {
  let release: () => void = () => {};
  const pending = new Promise<void>((resolve) => {
    release = resolve;
  });
  let recover = false;
  await page.route(/\/api\/admin\/support\/notice-room\/messages(?:\?.*)?$/, async (route) => {
    await pending;
    return recover
      ? route.fulfill({ json: [] })
      : route.fulfill({ status: 503, json: { message: "대화 조회 실패" } });
  });
  await page.goto("/admin/support");
  await expect(page.getByText("문의 대화를 불러오고 있어요.")).toBeVisible();
  await expect(page.getByText("아직 대화가 없습니다.")).toHaveCount(0);
  release();
  await expect(page.getByRole("button", { name: "문의 대화 다시 확인" })).toBeVisible();
  await expect(page.getByText("아직 대화가 없습니다.")).toHaveCount(0);
  recover = true;
  await page.getByRole("button", { name: "문의 대화 다시 확인" }).click();
  await expect(page.getByText("아직 대화가 없습니다.")).toBeVisible();
});

test("문의별 초안을 보존하고 담당자 권한이 사라진 뒤에는 복원하지 않는다", async ({ page }) => {
  let owner = "notice-admin";
  const second = { ...ticket, roomId: "second-room", title: "다른 문의" };
  await page.route(/\/api\/admin\/support(?:\?.*)?$/, (route) =>
    route.fulfill({ json: [{ ...ticket, assigneeId: owner }, second] }),
  );
  await page.route(
    /\/api\/admin\/support\/second-room\/(messages|notes|notices)(?:\?.*)?$/,
    (route) => route.fulfill({ json: [] }),
  );
  await page.goto("/admin/support");
  const reply = page.getByRole("textbox", { name: "사용자에게 보낼 답변" });
  await reply.fill("첫 문의 민감 초안");
  await page.getByRole("combobox", { name: "답변 종류" }).selectOption("RESULT");
  await page.getByRole("button", { name: /^다른 문의/ }).click();
  await expect(reply).toHaveValue("");
  await reply.fill("두 번째 문의 초안");
  await page.getByRole("button", { name: /거래 문의 안내 확인/ }).click();
  await expect(reply).toHaveValue("첫 문의 민감 초안");
  await expect(page.getByRole("combobox", { name: "답변 종류" })).toHaveValue("RESULT");
  owner = "other-admin";
  await page.getByRole("button", { name: "새로고침", exact: true }).click();
  await expect(page.getByText("다른 관리자가 담당 중입니다")).toBeVisible();
  owner = "notice-admin";
  await page.getByRole("button", { name: "새로고침", exact: true }).click();
  await expect(reply).toHaveValue("");
  await expect(page.getByRole("combobox", { name: "답변 종류" })).toHaveValue("GENERAL");
});

test("계정이 바뀌면 이전 담당자의 문의 초안을 복원하지 않는다", async ({ page }) => {
  let accountId = "notice-admin";
  await page.route(/\/api\/admin\/support(?:\?.*)?$/, (route) =>
    route.fulfill({ json: [{ ...ticket, assigneeId: accountId }] }),
  );
  await page.route("**/api/v1/accounts/me", (route) =>
    route.fulfill({ json: { accountId, role: "ADMIN" } }),
  );
  await page.goto("/admin/support");
  const reply = page.getByRole("textbox", { name: "사용자에게 보낼 답변" });
  await reply.fill("이전 계정의 민감 초안");
  await page.getByRole("combobox", { name: "답변 종류" }).selectOption("RESULT");
  accountId = "next-admin";
  await page.evaluate((nextAccountId) => {
    localStorage.setItem(
      "gole.session",
      JSON.stringify({ accountId: nextAccountId, sessionToken: "notice-fixture", role: "ADMIN" }),
    );
    window.dispatchEvent(new Event("gole:session-change"));
  }, accountId);
  await expect(reply).toBeEnabled();
  await expect(reply).toHaveValue("");
  await expect(page.getByRole("combobox", { name: "답변 종류" })).toHaveValue("GENERAL");
});

test("안내 저장 후 초과 목록에서 사라져도 성공 결과를 알려 준다", async ({ page }) => {
  let sent = false;
  await page.route(/\/api\/admin\/support(?:\?.*)?$/, (route) =>
    route.fulfill({ json: sent ? [] : [ticket] }),
  );
  await page.route(/\/api\/admin\/support\/notice-room\/messages(?:\?.*)?$/, (route) => {
    if (route.request().method() !== "POST") return route.fulfill({ json: [] });
    sent = true;
    return route.fulfill({
      json: {
        id: "sent",
        roomId: ticket.roomId,
        senderId: "notice-admin",
        content: "진행 상황입니다.",
        sentAt: now,
      },
    });
  });
  await page.goto("/admin/support");
  await page.getByRole("button", { name: "진행 안내 미해결 초과 1건" }).click();
  await page.getByRole("button", { name: /거래 문의 안내 확인/ }).click();
  await page.getByRole("combobox", { name: "답변 종류" }).selectOption("PROGRESS");
  await page.getByRole("textbox", { name: "사용자에게 보낼 답변" }).fill("진행 상황입니다.");
  await page.getByRole("button", { name: "진행 안내 보내기" }).click();
  await expect(
    page.getByRole("status").filter({ hasText: "진행 안내를 대화에 남겼습니다. 현재 조건" }),
  ).toBeVisible();
  await expect(page.getByRole("textbox", { name: "사용자에게 보낼 답변" })).toHaveCount(0);
});

for (const stayOnOtherRoom of [false, true]) {
  test(`전송 중 문의 이동 후 ${stayOnOtherRoom ? "다른 문의에 있어도" : "원래 문의로 돌아와도"} 전송 상태와 완료를 보존한다`, async ({
    page,
  }) => {
    let complete = () => {};
    const response = new Promise<void>((resolve) => {
      complete = resolve;
    });
    let posts = 0;
    await page.route(/\/api\/admin\/support(?:\?.*)?$/, (route) =>
      route.fulfill({ json: [ticket, { ...ticket, roomId: "second-room", title: "다른 문의" }] }),
    );
    await page.route(
      /\/api\/admin\/support\/second-room\/(messages|notes|notices)(?:\?.*)?$/,
      (route) => route.fulfill({ json: [] }),
    );
    await page.route(/\/api\/admin\/support\/notice-room\/messages(?:\?.*)?$/, async (route) => {
      if (route.request().method() !== "POST") return route.fulfill({ json: [] });
      posts += 1;
      await response;
      return route.fulfill({
        json: {
          id: "once",
          roomId: ticket.roomId,
          senderId: "notice-admin",
          content: "진행 중인 답변",
          sentAt: now,
        },
      });
    });
    await page.goto("/admin/support");
    const reply = page.getByRole("textbox", { name: "사용자에게 보낼 답변" });
    await reply.fill("진행 중인 답변");
    await page.getByRole("combobox", { name: "답변 종류" }).selectOption("PROGRESS");
    await page.getByRole("button", { name: "진행 안내 보내기" }).click();
    await page.getByRole("button", { name: /^다른 문의/ }).click();
    await reply.fill("다른 문의의 초안");
    if (!stayOnOtherRoom) {
      await page.getByRole("button", { name: /거래 문의 안내 확인/ }).click();
      await expect(page.getByRole("button", { name: "답변 전송 중…" })).toBeDisabled();
      await expect(reply).toBeDisabled();
    }
    complete();
    if (stayOnOtherRoom) {
      await expect.poll(() => posts).toBe(1);
      await expect(reply).toHaveValue("다른 문의의 초안");
      await page.getByRole("button", { name: /거래 문의 안내 확인/ }).click();
    }
    await expect(reply).toHaveValue("");
    await expect(page.getByRole("combobox", { name: "답변 종류" })).toHaveValue("GENERAL");
    expect(posts).toBe(1);
  });
}

for (const endpoint of ["messages", "notices"]) {
  test(`${endpoint} 조회에서 담당 권한을 잃으면 목록 재조회 실패 중에도 민감 초안을 폐기한다`, async ({
    page,
  }) => {
    await page.clock.install();
    let denied = false;
    let listOffline = false;
    await page.route(/\/api\/admin\/support(?:\?.*)?$/, (route) =>
      listOffline
        ? route.fulfill({ status: 503, json: { message: "목록 조회 실패" } })
        : route.fulfill({ json: [ticket] }),
    );
    await page.route(
      new RegExp(`/api/admin/support/notice-room/${endpoint}(?:\\?.*)?$`),
      (route) =>
        denied
          ? route.fulfill({
              status: 403,
              json: { code: "SUPPORT_ASSIGNEE_ONLY", message: "담당자만 조회할 수 있습니다." },
            })
          : route.fulfill({ json: [] }),
    );
    await page.goto("/admin/support");
    const reply = page.getByRole("textbox", { name: "사용자에게 보낼 답변" });
    await reply.fill("민감한 이전 담당 초안");
    await page.getByPlaceholder("운영팀만 보는 메모").fill("민감한 내부 메모 초안");
    await page.getByRole("combobox", { name: "답변 종류" }).selectOption("RESULT");
    denied = true;
    listOffline = true;
    await page.clock.fastForward(5100);
    await expect(reply).toHaveCount(0);
    await expect(page.getByRole("region", { name: "실제 안내 이력" })).toHaveCount(0);
    denied = false;
    listOffline = false;
    await page.getByRole("button", { name: "새로고침", exact: true }).click();
    await page.getByRole("button", { name: /거래 문의 안내 확인/ }).click();
    await expect(reply).toHaveValue("");
    await expect(page.getByPlaceholder("운영팀만 보는 메모")).toHaveValue("");
    await expect(page.getByRole("combobox", { name: "답변 종류" })).toHaveValue("GENERAL");
  });
}

test("내부 메모 저장 중에도 문의를 오가면 중복 제출을 막고 성공한 초안을 비운다", async ({
  page,
}) => {
  let complete = () => {};
  const response = new Promise<void>((resolve) => {
    complete = resolve;
  });
  let posts = 0;
  await page.route(/\/api\/admin\/support(?:\?.*)?$/, (route) =>
    route.fulfill({ json: [ticket, { ...ticket, roomId: "second-room", title: "다른 문의" }] }),
  );
  await page.route(
    /\/api\/admin\/support\/second-room\/(messages|notes|notices)(?:\?.*)?$/,
    (route) => route.fulfill({ json: [] }),
  );
  await page.route("**/api/admin/support/notice-room/notes", async (route) => {
    if (route.request().method() !== "POST") return route.fulfill({ json: [] });
    posts += 1;
    await response;
    return route.fulfill({ status: 204 });
  });
  await page.goto("/admin/support");
  const note = page.getByPlaceholder("운영팀만 보는 메모");
  await note.fill("확인 중인 내부 메모");
  await page.getByRole("button", { name: "메모 남기기" }).click();
  await page.getByRole("button", { name: /^다른 문의/ }).click();
  await page.getByRole("button", { name: /거래 문의 안내 확인/ }).click();
  await expect(note).toBeDisabled();
  await expect(page.getByRole("button", { name: "메모 남기기" })).toBeDisabled();
  complete();
  await expect(note).toHaveValue("");
  expect(posts).toBe(1);
});

test("이전 답변 성공 문구를 다음 전송 실패 뒤 목록에 다시 표시하지 않는다", async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 900 });
  let posts = 0;
  await page.route(/\/api\/admin\/support\/notice-room\/messages(?:\?.*)?$/, (route) => {
    if (route.request().method() !== "POST") return route.fulfill({ json: [] });
    posts += 1;
    return posts === 1
      ? route.fulfill({
          json: {
            id: "first",
            roomId: ticket.roomId,
            senderId: "notice-admin",
            content: "첫 답변",
            sentAt: now,
          },
        })
      : route.fulfill({ status: 503, json: { code: "TEMPORARY", message: "두 번째 저장 실패" } });
  });
  await page.goto("/admin/support");
  const reply = page.getByRole("textbox", { name: "사용자에게 보낼 답변" });
  await reply.fill("첫 답변");
  await page.getByRole("button", { name: "보내기", exact: true }).click();
  await expect(
    page.getByRole("status").filter({ hasText: "답변을 대화에 남겼습니다." }),
  ).toBeVisible();
  await reply.fill("두 번째 답변");
  await page.getByRole("button", { name: "보내기", exact: true }).click();
  await expect(page.getByRole("alert").filter({ hasText: "두 번째 저장 실패" })).toBeVisible();
  await page.getByRole("button", { name: "목록", exact: true }).click();
  await expect(page.getByRole("status").filter({ hasText: "대화에 남겼습니다." })).toHaveCount(0);
});

for (const width of [320, 375, 414, 768]) {
  test(`${width}px에서 운영 필터·안내 종류·답변 버튼이 화면 안에 들어온다`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await page.clock.install();
    const title =
      "거래 문의 안내 확인 · 배송 조사와 교환 가능 여부를 함께 확인해야 하는 긴 문의 제목";
    await page.route(/\/api\/admin\/support(?:\?.*)?$/, (route) =>
      route.fulfill({ json: [{ ...ticket, title }] }),
    );
    await page.goto("/admin/support");
    const kind = page.getByRole("combobox", { name: "답변 종류" });
    await expect(kind).toBeVisible();
    await kind.selectOption("RESULT");
    await page
      .getByRole("textbox", { name: "사용자에게 보낼 답변" })
      .fill("모바일에서도 안내를 작성합니다.");
    const send = page.getByRole("button", { name: /^(보내기|진행 안내 보내기|결과 안내 보내기)$/ });
    await expect(send).toBeEnabled();
    for (const control of [kind, send, page.getByRole("combobox", { name: "안내 목표" })]) {
      const box = await control.boundingBox();
      expect(box).not.toBeNull();
      expect(box!.x).toBeGreaterThanOrEqual(0);
      expect(box!.x + box!.width).toBeLessThanOrEqual(width + 1);
    }
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
      true,
    );
    const heading = page.getByRole("heading", { name: title, exact: true });
    expect(await heading.evaluate((element) => element.scrollHeight <= element.clientHeight)).toBe(
      true,
    );
    await page.getByRole("button", { name: "목록", exact: true }).click();
    const row = page.getByRole("button", { name: new RegExp(title) });
    await expect(row).toBeFocused();
    await page.clock.fastForward(11_000);
    await expect(heading).toHaveCount(0);
    await expect(row).toBeVisible();
    await row.press("Enter");
    await expect(heading).toBeFocused();
    await expect(page.getByRole("textbox", { name: "사용자에게 보낼 답변" })).toHaveValue(
      "모바일에서도 안내를 작성합니다.",
    );
    await expect(kind).toHaveValue("RESULT");
  });
}
