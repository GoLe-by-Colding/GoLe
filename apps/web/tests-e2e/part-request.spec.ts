import { expect, test, type Page, type Route } from "@playwright/test";
import { E2E_SELLER, signInAs } from "./support/e2e-session";

/**
 * 부족 부품 요청(.kiro/specs/wanted-parts F1).
 *
 * 게시판·작성·상세는 모두 브라우저가 API를 부르는 화면이라 응답을 가로채 검증한다 — 실제 API 없이
 * 돈다. 세트 상세의 진입 링크("부품 요청 n건")는 서버 렌더라 맨 아래 실제 백엔드 스펙이 확인한다.
 */

const externalBaseUrl = process.env.E2E_BASE_URL;
const targetsRemoteHost =
  externalBaseUrl !== undefined &&
  !["localhost", "127.0.0.1"].includes(new URL(externalBaseUrl).hostname);

const REQUESTER_ID = "acc-part-requester";
const HOUR = 60 * 60 * 1000;

const OPEN_REQUEST = {
  id: "pr-open-1",
  requesterId: REQUESTER_ID,
  setNumber: "10307",
  items: [
    { partNumber: "3062b", colorName: "검정", quantity: 4 },
    { partNumber: "3023", colorName: "밝은 회색", quantity: 2 },
  ],
  note: "판교 직거래 가능해요.",
  status: "open",
  createdAt: new Date(Date.now() - 3 * HOUR).toISOString(),
  closedAt: null,
} as const;

const CLOSED_REQUEST = {
  id: "pr-closed-1",
  requesterId: "acc-someone-else",
  setNumber: null,
  items: [{ partNumber: "32062", colorName: "검정", quantity: 1 }],
  note: "",
  status: "closed",
  createdAt: new Date(Date.now() - 30 * HOUR).toISOString(),
  closedAt: new Date(Date.now() - 2 * HOUR).toISOString(),
} as const;

/** 게시판·내 요청·단건·마감·삭제를 한 번에 받는다. 경로 모양으로 나눠 각 테스트가 응답을 고른다. */
const PART_REQUESTS_API = /\/api\/v1\/part-requests(?:[/?].*)?$/;

async function seedSession(page: Page, accountId: string): Promise<void> {
  // 앱이 새로고침 이후 실제로 갖는 모양 — 토큰은 HttpOnly 쿠키에 있고 여기는 비어 있다.
  await page.addInitScript((id) => {
    window.localStorage.setItem(
      "gole.session",
      JSON.stringify({ accountId: id, sessionToken: "", role: "USER" }),
    );
  }, accountId);
}

/** 대상과 무관한 전역 요청. 실백엔드 401이 합성 세션을 지우지 않도록 격리한다. */
async function isolateLayoutRequests(page: Page): Promise<void> {
  await page.route(/\/api\/v1\/users\/[^/]+\/notifications\/unread-count(?:\?.*)?$/, (route) =>
    route.fulfill({ json: { unreadCount: 0 } }),
  );
  await page.route("**/api/v1/accounts/me/onboarding", (route) =>
    route.fulfill({
      json: {
        required: false,
        legacyExempt: true,
        nicknameCompleted: true,
        nickname: "e2e",
        phoneVerificationRequired: true,
        phoneCompleted: true,
        maskedPhoneNumber: "010-****-0000",
        interestTagsCompleted: true,
        interestTags: [],
        privacyConsented: true,
        marketingConsented: false,
      },
    }),
  );
  await page.route("**/api/v1/config/launch", (route) =>
    route.fulfill({
      json: {
        stage: 0,
        tradeMode: "DIRECT_CHAT",
        features: { payments: false, reviews: false, partnerPayout: false },
        sellerIdentityVerificationReady: true,
        updatedAt: null,
      },
    }),
  );
}

function pathOf(route: Route): string {
  return new URL(route.request().url()).pathname;
}

test.describe("부품 요청 게시판", () => {
  test.skip(targetsRemoteHost, "응답 가로채기 기반 — 로컬 프론트 전용");

  test.beforeEach(async ({ page }) => {
    await isolateLayoutRequests(page);
  });

  test("열린 요청을 보여 주고 세트 필터·상태 탭을 주소에 남긴다", async ({ page }) => {
    const queries: URLSearchParams[] = [];
    await page.route(PART_REQUESTS_API, (route) => {
      const url = new URL(route.request().url());
      queries.push(url.searchParams);
      return route.fulfill({
        json: url.searchParams.get("status") === "closed" ? [CLOSED_REQUEST] : [OPEN_REQUEST],
      });
    });

    await page.goto("/parts");

    const card = page.getByTestId("part-request-card");
    await expect(card).toHaveCount(1);
    await expect(card.getByRole("link", { name: "3062b 검정 4개 외 1종" })).toHaveAttribute(
      "href",
      "/parts/pr-open-1",
    );
    await expect(card.getByText("찾는 중", { exact: true })).toBeVisible();
    await expect(card.getByText("3시간 전", { exact: true })).toBeVisible();
    expect(queries.at(-1)?.get("status")).toBe("open");
    // 비로그인에게는 "내 요청" 탭이 없다.
    await expect(page.getByRole("button", { name: "내 요청" })).toHaveCount(0);

    await page.getByLabel("세트 번호", { exact: true }).fill("10307");
    await page.getByRole("button", { name: "찾기" }).click();

    await expect.poll(() => queries.at(-1)?.get("setNumber")).toBe("10307");
    await expect(page).toHaveURL(/\/parts\?set=10307$/);
    await expect(page.getByRole("link", { name: "부품 요청하기" }).first()).toHaveAttribute(
      "href",
      "/parts/new?set=10307",
    );

    await page.getByRole("button", { name: "마감", exact: true }).click();

    await expect(page).toHaveURL(/\/parts\?set=10307&status=closed$/);
    await expect.poll(() => queries.at(-1)?.get("status")).toBe("closed");
    await expect(
      page.getByTestId("part-request-card").getByRole("link", { name: "32062 검정 1개" }),
    ).toBeVisible();
  });

  test("목록을 받지 못하면 다시 시도할 수 있다", async ({ page }) => {
    // 개발 모드는 이펙트를 두 번 돌리므로 호출 횟수가 아니라 스위치로 장애를 낸다.
    let healthy = false;
    await page.route(PART_REQUESTS_API, (route) =>
      healthy
        ? route.fulfill({ json: [OPEN_REQUEST] })
        : route.fulfill({ status: 503, json: { code: "UNAVAILABLE", message: "down" } }),
    );

    await page.goto("/parts");
    await expect(page.getByText("부품 요청을 불러오지 못했어요")).toBeVisible();

    healthy = true;
    await page.getByRole("button", { name: "다시 시도" }).click();
    await expect(page.getByTestId("part-request-card")).toHaveCount(1);
  });
});

test.describe("부품 요청 작성", () => {
  test.skip(targetsRemoteHost, "응답 가로채기 기반 — 로컬 프론트 전용");

  test.beforeEach(async ({ page }) => {
    await isolateLayoutRequests(page);
  });

  test("세트를 미리 채우고, 틀린 칸을 짚은 뒤 서버 규칙에 맞는 본문을 보낸다", async ({ page }) => {
    await seedSession(page, REQUESTER_ID);
    let postBody: unknown = null;
    await page.route(PART_REQUESTS_API, (route) => {
      if (route.request().method() === "POST" && pathOf(route).endsWith("/part-requests")) {
        postBody = route.request().postDataJSON();
        return route.fulfill({ status: 201, json: { ...OPEN_REQUEST, id: "pr-new" } });
      }
      return route.fulfill({ json: { ...OPEN_REQUEST, id: "pr-new" } });
    });

    await page.goto("/parts/new?set=10307");

    await expect(page.getByRole("heading", { name: "부품 요청하기" })).toBeVisible();
    await expect(page.getByLabel("세트 번호 (선택)")).toHaveValue("10307");

    await page.getByLabel("1번째 부품 번호").fill("3062b!");
    await page.getByLabel("1번째 부품 색상").fill("검정");
    await page.getByLabel("1번째 부품 수량").fill("4");
    await page.getByRole("button", { name: "부품 요청 올리기" }).click();

    await expect(page.getByText("빨간 칸을 확인해 주세요.", { exact: true })).toBeVisible();
    await expect(page.getByLabel("1번째 부품 번호")).toHaveAttribute("aria-invalid", "true");
    expect(postBody).toBeNull();

    await page.getByLabel("1번째 부품 번호").fill("3062b");
    await page.getByRole("button", { name: "+ 부품 줄 추가" }).click();
    await page.getByLabel("2번째 부품 번호").fill("3023");
    await page.getByLabel("2번째 부품 색상").fill(" 밝은 회색 ");
    await page.getByLabel("2번째 부품 수량").fill("2");
    await page.getByLabel("메모 (선택)").fill("판교 직거래 가능해요.");
    await page.getByRole("button", { name: "부품 요청 올리기" }).click();

    await expect
      .poll(() => postBody)
      .toEqual({
        setNumber: "10307",
        items: [
          { partNumber: "3062b", colorName: "검정", quantity: 4 },
          { partNumber: "3023", colorName: "밝은 회색", quantity: 2 },
        ],
        note: "판교 직거래 가능해요.",
      });
    await expect(page).toHaveURL(/\/parts\/pr-new$/);
  });

  test("카탈로그에 없는 세트면 세트 칸에 이유를 붙인다", async ({ page }) => {
    await seedSession(page, REQUESTER_ID);
    await page.route(PART_REQUESTS_API, (route) =>
      route.fulfill({
        status: 404,
        json: { code: "PART_REQUEST_SET_NOT_FOUND", message: "set not found" },
      }),
    );

    await page.goto("/parts/new?set=99999");
    await page.getByLabel("1번째 부품 번호").fill("3062b");
    await page.getByLabel("1번째 부품 색상").fill("검정");
    await page.getByRole("button", { name: "부품 요청 올리기" }).click();

    await expect(
      page.getByText("카탈로그에 없는 세트 번호예요. 번호를 확인하거나 비워 두세요."),
    ).toBeVisible();
    await expect(page.getByLabel("세트 번호 (선택)")).toHaveAttribute("aria-invalid", "true");
    await expect(page).toHaveURL(/\/parts\/new\?set=99999$/);
  });

  test("비로그인이면 폼 대신 로그인 후 돌아올 링크를 준다", async ({ page }) => {
    await page.goto("/parts/new?set=10307");

    await expect(page.getByRole("button", { name: "부품 요청 올리기" })).toHaveCount(0);
    await expect(page.getByRole("link", { name: "로그인하러 가기" })).toHaveAttribute(
      "href",
      `/login?returnTo=${encodeURIComponent("/parts/new?set=10307")}`,
    );
  });
});

test.describe("부품 요청 상세", () => {
  test.skip(targetsRemoteHost, "응답 가로채기 기반 — 로컬 프론트 전용");

  test.beforeEach(async ({ page }) => {
    await isolateLayoutRequests(page);
  });

  test("방문자는 도와줄게요로 요청자와 1:1 대화를 연다", async ({ page }) => {
    await seedSession(page, "acc-part-helper");
    let directBody: unknown = null;
    await page.route(PART_REQUESTS_API, (route) => route.fulfill({ json: OPEN_REQUEST }));
    await page.route("**/api/v1/chat/social/rooms/direct", (route) => {
      directBody = route.request().postDataJSON();
      return route.fulfill({ json: { id: "room-part-1" } });
    });

    await page.goto("/parts/pr-open-1");

    await expect(page.getByRole("heading", { name: "3062b 검정 4개 외 1종" })).toBeVisible();
    await expect(page.getByRole("link", { name: "#10307 세트" })).toHaveAttribute(
      "href",
      "/sets/10307",
    );
    await expect(page.getByRole("cell", { name: "밝은 회색" })).toBeVisible();
    await expect(page.getByText("판교 직거래 가능해요.")).toBeVisible();
    await expect(page.getByRole("button", { name: "마감", exact: true })).toHaveCount(0);

    // 채팅 화면이 방 쿼리를 소비하며 주소를 바꾸므로, 이동을 먼저 기다려 둔다.
    const openedChat = page.waitForURL(/\/chat\?room=room-part-1$/);
    await page.getByRole("button", { name: "도와줄게요" }).click();
    await openedChat;
    expect(directBody).toEqual({ peerId: REQUESTER_ID });
  });

  test("비로그인 방문자는 로그인하고 도와줄 수 있다", async ({ page }) => {
    await page.route(PART_REQUESTS_API, (route) => route.fulfill({ json: OPEN_REQUEST }));

    await page.goto("/parts/pr-open-1");

    await expect(page.getByRole("link", { name: "로그인하고 도와주기" })).toHaveAttribute(
      "href",
      `/login?returnTo=${encodeURIComponent("/parts/pr-open-1")}`,
    );
  });

  test("작성자는 마감한 뒤 삭제하고 내 요청으로 돌아간다", async ({ page }) => {
    await seedSession(page, REQUESTER_ID);
    let closeCalls = 0;
    let deleteCalls = 0;
    await page.route(PART_REQUESTS_API, (route) => {
      const path = pathOf(route);
      if (path.endsWith("/pr-open-1/close")) {
        closeCalls += 1;
        return route.fulfill({
          json: { ...OPEN_REQUEST, status: "closed", closedAt: new Date().toISOString() },
        });
      }
      if (path.endsWith("/part-requests/mine") || path.endsWith("/part-requests")) {
        return route.fulfill({ json: [] });
      }
      if (route.request().method() === "DELETE") {
        deleteCalls += 1;
        return route.fulfill({ status: 204, body: "" });
      }
      return route.fulfill({ json: OPEN_REQUEST });
    });
    page.on("dialog", (dialog) => void dialog.accept());

    await page.goto("/parts/pr-open-1");

    await expect(page.getByRole("heading", { name: "내 요청 관리" })).toBeVisible();
    await expect(page.getByRole("button", { name: "도와줄게요" })).toHaveCount(0);

    await page.getByRole("button", { name: "마감", exact: true }).click();
    await expect(page.getByText("마감된 요청이에요. 필요 없으면 삭제할 수 있어요.")).toBeVisible();
    await expect(page.getByRole("button", { name: "마감", exact: true })).toHaveCount(0);
    expect(closeCalls).toBe(1);

    await page.getByRole("button", { name: "삭제", exact: true }).click();
    await expect(page).toHaveURL(/\/parts\?status=mine$/);
    expect(deleteCalls).toBe(1);
    await expect(page.getByRole("button", { name: "내 요청" })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
    await expect(page.getByText("아직 올린 요청이 없어요")).toBeVisible();
  });

  test("없는 요청이면 게시판으로 돌려보낸다", async ({ page }) => {
    await page.route(PART_REQUESTS_API, (route) =>
      route.fulfill({
        status: 404,
        json: { code: "PART_REQUEST_NOT_FOUND", message: "not found" },
      }),
    );

    await page.goto("/parts/pr-missing");

    await expect(page.getByText("요청을 찾을 수 없어요")).toBeVisible();
    await expect(page.getByRole("link", { name: "부품 요청 게시판으로" })).toHaveAttribute(
      "href",
      "/parts",
    );
  });
});

test.describe("부품 요청 알림", () => {
  test.skip(targetsRemoteHost, "응답 가로채기 기반 — 로컬 프론트 전용");

  test("보유 세트 부품 요청 알림은 요청 상세로 이어진다", async ({ page }) => {
    const accountId = "acc-part-owner";
    await seedSession(page, accountId);
    await isolateLayoutRequests(page);
    await page.route(`**/api/v1/users/${accountId}/notifications`, (route) =>
      route.fulfill({
        json: [
          {
            id: "notification-part-request",
            type: "PART_REQUEST_FOR_OWNED_SET",
            message: "보유한 10307 세트의 부품을 찾는 요청이 있어요",
            link: "/parts/pr-open-1",
            read: false,
            createdAt: "2026-10-09T00:00:00Z",
          },
        ],
      }),
    );

    await page.goto("/notifications");

    await expect(page.getByRole("link", { name: /보유한 10307 세트의 부품/ })).toHaveAttribute(
      "href",
      "/parts/pr-open-1",
    );
  });
});

// 세트 상세는 서버에서 그려지므로 실제 API가 필요하다. CI E2E 잡(E2E_WITH_BACKEND=1)에서 돈다.
// 사전 조건: scripts/seed-e2e-accounts.sh 로 셀러 세션을 심어야 한다. 요청을 만들고 끝에 지운다
// (열린 요청은 계정당 10건까지라 남기면 반복 실행이 409로 막힌다).
test.describe("부품 요청 — 세트 상세 진입부터 삭제까지", () => {
  test.skip(
    process.env.E2E_WITH_BACKEND !== "1" || externalBaseUrl !== undefined,
    "실제 API가 렌더하는 세트 상세 — 로컬 백엔드 전용(쓰기 발생)",
  );

  test("세트 상세에서 요청을 올리면 게시판에 보이고, 작성자가 마감·삭제한다", async ({ page }) => {
    const apiBaseUrl = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";
    // 카탈로그에 실제로 있는 세트여야 한다(W2: 없으면 404). 홈 추천 세트는 카탈로그에서 나온다.
    const response = await page.request.get(`${apiBaseUrl}/api/v1/catalog/sets/featured`);
    expect(response.ok()).toBeTruthy();
    const featured = (await response.json()) as Array<{ setNumber: string }>;
    const setNumber = featured[0]?.setNumber;
    expect(setNumber, "카탈로그 추천 세트가 하나 이상 필요합니다").toBeTruthy();

    await signInAs(page, E2E_SELLER);
    page.on("dialog", (dialog) => void dialog.accept());
    await page.goto(`/sets/${setNumber}`);

    const entry = page.getByTestId("set-part-requests");
    await expect(
      entry.getByRole("link", { name: /^부품 요청 (\d+건( 이상)?|보기)$/ }),
    ).toHaveAttribute("href", `/parts?set=${setNumber}`);
    await entry.getByRole("link", { name: "부품 요청하기" }).click();

    await expect(page.getByLabel("세트 번호 (선택)")).toHaveValue(setNumber ?? "");
    const partNumber = `e2e${Date.now() % 1_000_000}`;
    await page.getByLabel("1번째 부품 번호").fill(partNumber);
    await page.getByLabel("1번째 부품 색상").fill("검정");
    await page.getByLabel("1번째 부품 수량").fill("2");
    await page.getByRole("button", { name: "부품 요청 올리기" }).click();

    const title = `${partNumber} 검정 2개`;
    await expect(page).toHaveURL(/\/parts\/[^/?]+$/);
    await expect(page.getByRole("heading", { name: title })).toBeVisible();
    await expect(page.getByRole("heading", { name: "내 요청 관리" })).toBeVisible();

    await page.goto(`/parts?set=${setNumber}`);
    await page.getByRole("link", { name: title }).click();
    // 게시판 목록도 제목을 heading으로 그린다. 상세로 넘어간 것을 주소로 먼저 확인해야, 아래 "마감"이
    // 게시판의 상태 필터 버튼을 누르지 않는다.
    await expect(page).toHaveURL(/\/parts\/[^/?]+$/);
    await expect(page.getByRole("heading", { name: "내 요청 관리" })).toBeVisible();

    await page.getByRole("button", { name: "마감", exact: true }).click();
    await expect(page.getByText("마감된 요청이에요. 필요 없으면 삭제할 수 있어요.")).toBeVisible();
    await page.getByRole("button", { name: "삭제", exact: true }).click();
    await expect(page).toHaveURL(/\/parts\?status=mine$/);
    await expect(page.getByRole("link", { name: title })).toHaveCount(0);
  });
});
