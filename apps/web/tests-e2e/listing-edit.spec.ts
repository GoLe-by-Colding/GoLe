import { expect, test, type Page } from "@playwright/test";
import { E2E_BUYER, E2E_SELLER, signInAs, switchTo } from "./support/e2e-session";

/**
 * 매물 수정·끌올·찜 매물 가격 인하(.kiro/specs/listing-edit-and-bump).
 *
 * 매물 상세는 서버 컴포넌트라 `page.route`로 가로챌 수 없다. 그래서 응답 가로채기 스펙은
 * 클라이언트가 직접 부르는 화면(수정 화면, 프로필 "내 매물")만 다루고, 상세의 판매자 패널·
 * 찜 버튼·인하 표시는 맨 아래 실제 백엔드 스펙이 확인한다.
 */

const externalBaseUrl = process.env.E2E_BASE_URL;
const targetsRemoteHost =
  externalBaseUrl !== undefined &&
  !["localhost", "127.0.0.1"].includes(new URL(externalBaseUrl).hostname);

const SELLER_ID = "account-seller";
const PHOTO_KEY = "images/11111111-1111-4111-8111-111111111111.png";

const LISTING = {
  id: "listing-edit-1",
  sellerId: SELLER_ID,
  title: "밀레니엄 팰컨 75192",
  description: "박스 보관, 한 번 조립",
  price: 980000,
  condition: "like_new",
  completeness: "full_box",
  hasBox: true,
  hasManual: true,
  hasMissingParts: false,
  missingPartsNote: "",
  defectsNote: "",
  photoUrls: [`/api/v1/media/${PHOTO_KEY}`],
  catalogSetNumber: "75192",
  category: "set",
  interestTag: "star-wars",
  status: "active",
  createdAt: "2026-10-01T00:00:00Z",
  listedAt: "2026-10-01T00:00:00Z",
  bumpedAt: null,
  previousPrice: null,
  priceChangedAt: null,
  photoKeys: [PHOTO_KEY],
} as const;

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
  // 미리보기 이미지가 꺼진 API 서버로 새지 않게 한다.
  await page.route("**/api/v1/media/**", (route) => route.fulfill({ status: 404, body: "" }));
}

test.describe("매물 수정 화면", () => {
  test.skip(targetsRemoteHost, "응답 가로채기 기반 — 로컬 프론트 전용");

  test.beforeEach(async ({ page }) => {
    await isolateLayoutRequests(page);
  });

  test("판매자가 가격을 내려 저장하면 통째 교체 본문을 보내고 상세로 돌아간다", async ({
    page,
  }) => {
    await seedSession(page, SELLER_ID);
    let putBody: unknown = null;
    await page.route(`**/api/v1/listings/${LISTING.id}`, async (route) => {
      if (route.request().method() === "PUT") {
        putBody = route.request().postDataJSON();
        await route.fulfill({
          json: { ...LISTING, price: 900000, previousPrice: 980000 },
        });
        return;
      }
      await route.fulfill({ json: LISTING });
    });

    await page.goto(`/listings/${LISTING.id}/edit`);

    await expect(page.getByRole("heading", { name: "매물 수정" })).toBeVisible();
    // 세트·카테고리는 읽기 전용으로만 보인다.
    await expect(page.getByText("바꿀 수 없는 정보")).toBeVisible();
    await expect(page.getByText("#75192")).toBeVisible();
    await expect(page.getByLabel("카테고리")).toHaveCount(0);
    await expect(page.getByLabel("브릭 세트 번호 (선택)")).toHaveCount(0);
    // 기존 값이 채워져 있어야 손대지 않은 필드도 그대로 다시 보낸다.
    await expect(page.getByLabel("제목")).toHaveValue(LISTING.title);
    await expect(page.getByLabel("관심 테마")).toHaveValue("star-wars");
    await expect(page.getByRole("img", { name: "상품 이미지 1" })).toBeVisible();

    await page.getByLabel("가격 (원)").fill("900000");
    await expect(page.getByText(/80,000원 내려요/)).toBeVisible();
    await page.getByRole("button", { name: "수정 완료" }).click();

    await expect.poll(() => putBody).not.toBeNull();
    expect(putBody).toEqual({
      title: LISTING.title,
      description: LISTING.description,
      price: 900000,
      condition: "LIKE_NEW",
      completeness: "FULL_BOX",
      hasBox: true,
      hasManual: true,
      hasMissingParts: false,
      missingPartsNote: "",
      defectsNote: "",
      photoKeys: [PHOTO_KEY],
      interestTag: "star-wars",
    });
    await expect(page).toHaveURL(new RegExp(`/listings/${LISTING.id}$`));
  });

  test("예약이 먼저 잡혀 수정이 지면 이유를 보여 주고 다시 시도할 수 있다", async ({ page }) => {
    await seedSession(page, SELLER_ID);
    await page.route(`**/api/v1/listings/${LISTING.id}`, async (route) => {
      if (route.request().method() === "PUT") {
        await route.fulfill({
          status: 409,
          json: { code: "LISTING_ORDER_IN_PROGRESS", message: "listing has an order" },
        });
        return;
      }
      await route.fulfill({ json: LISTING });
    });

    await page.goto(`/listings/${LISTING.id}/edit`);
    await page.getByLabel("가격 (원)").fill("900000");
    await page.getByRole("button", { name: "수정 완료" }).click();

    // Next 경로 안내(#__next-route-announcer__)도 role=alert 라 역할이 아니라 문구로 집는다.
    await expect(
      page.getByText("거래가 진행 중인 매물은 수정할 수 없어요.", { exact: true }),
    ).toBeVisible();
    await expect(page).toHaveURL(new RegExp(`/listings/${LISTING.id}/edit$`));
    await expect(page.getByRole("button", { name: "수정 완료" })).toBeEnabled();
  });

  test("판매자가 아니면 폼 대신 안내를 본다", async ({ page }) => {
    await seedSession(page, "account-visitor");
    let putCalls = 0;
    await page.route(`**/api/v1/listings/${LISTING.id}`, async (route) => {
      if (route.request().method() === "PUT") putCalls += 1;
      await route.fulfill({ json: LISTING });
    });

    await page.goto(`/listings/${LISTING.id}/edit`);

    await expect(page.getByText("내 매물만 수정할 수 있어요")).toBeVisible();
    await expect(page.getByRole("button", { name: "수정 완료" })).toHaveCount(0);
    await expect(page.getByRole("link", { name: "매물 보기" })).toHaveAttribute(
      "href",
      `/listings/${LISTING.id}`,
    );
    expect(putCalls).toBe(0);
  });
});

/** 프로필 "내 매물" 탭이 채워지는 데 필요한 응답. 활성 매물 하나만 둔다. */
async function mockMyListings(page: Page, bumpAvailableAt?: string): Promise<void> {
  await page.route("**/api/v1/accounts/me", (route) =>
    route.fulfill({
      json: { accountId: "acc-1", email: "seller@gole.test", role: "USER", nickname: "레고매니아" },
    }),
  );
  await page.route("**/api/v1/accounts/me/third-party-provision-consents/current", (route) =>
    route.fulfill({
      json: { noticeVersion: "2026-09-04", consented: false, lastDecisionAt: null },
    }),
  );
  await page.route("**/api/v1/orders?buyerId=**", (route) => route.fulfill({ json: [] }));
  await page.route("**/api/v1/orders/sales", (route) => route.fulfill({ json: [] }));
  await page.route("**/api/v1/orders/settlements", (route) => route.fulfill({ json: [] }));
  await page.route("**/api/v1/listings/mine**", (route) =>
    route.fulfill({
      json: [
        {
          ...LISTING,
          id: "listing-active-1",
          sellerId: "acc-1",
          ...(bumpAvailableAt === undefined ? {} : { bumpAvailableAt }),
        },
      ],
    }),
  );
}

test.describe("내 매물 — 수정·끌올", () => {
  test.skip(targetsRemoteHost, "응답 가로채기 기반 — 로컬 프론트 전용");

  test.beforeEach(async ({ page }) => {
    await seedSession(page, "acc-1");
    await isolateLayoutRequests(page);
  });

  test("판매 중 매물에 수정 링크가 있고, 쿨다운 중 끌올은 남은 시간을 알려 준다", async ({
    page,
  }) => {
    // 서버 시각과 화면 시각이 어긋난 경우: 화면은 끌올 가능으로 보지만 서버가 429로 막는다.
    await mockMyListings(page, new Date(Date.now() - 60_000).toISOString());
    let bumpCalls = 0;
    await page.route("**/api/v1/listings/listing-active-1/bump", (route) => {
      bumpCalls += 1;
      expect(route.request().method()).toBe("POST");
      return route.fulfill({
        status: 429,
        headers: { "Access-Control-Expose-Headers": "Retry-After", "Retry-After": "3600" },
        json: { code: "LISTING_BUMP_COOLDOWN", message: "cooldown" },
      });
    });

    await page.goto("/profile");
    await page.getByRole("button", { name: "내 매물" }).click();

    await expect(page.getByRole("link", { name: "수정", exact: true })).toHaveAttribute(
      "href",
      "/listings/listing-active-1/edit",
    );
    await page.getByRole("button", { name: "끌올", exact: true }).click();

    await expect(
      page.getByText("아직 끌올할 수 없어요. 1시간 후에 다시 할 수 있어요.", { exact: true }),
    ).toBeVisible();
    await expect(page.getByRole("button", { name: "끌올", exact: true })).toBeDisabled();
    expect(bumpCalls).toBe(1);
  });

  test("끌올에 성공하면 알리고 다음 가능 시각까지 버튼을 잠근다", async ({ page }) => {
    await mockMyListings(page);
    await page.route("**/api/v1/listings/listing-active-1/bump", (route) =>
      route.fulfill({
        json: {
          ...LISTING,
          id: "listing-active-1",
          sellerId: "acc-1",
          bumpedAt: new Date().toISOString(),
          listedAt: new Date().toISOString(),
          bumpAvailableAt: new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString(),
        },
      }),
    );

    await page.goto("/profile");
    await page.getByRole("button", { name: "내 매물" }).click();
    await page.getByRole("button", { name: "끌올", exact: true }).click();

    await expect(page.getByText("끌올했어요. 최신순 맨 앞에 다시 보여요.")).toBeVisible();
    await expect(page.getByRole("button", { name: "끌올", exact: true })).toBeDisabled();
    await expect(page.getByText(/^(24시간|23시간 \d+분) 후 끌올 가능$/)).toBeVisible();
  });
});

// 매물 상세는 서버에서 그려지므로 실제 API가 필요하다. CI E2E 잡(E2E_WITH_BACKEND=1)에서 돈다.
// 사전 조건: scripts/seed-e2e-accounts.sh 로 셀러·바이어 세션을 심어야 한다. 매물을 새로 만든다.
test.describe("매물 상세 — 판매자 패널과 찜·가격 인하", () => {
  test.skip(
    process.env.E2E_WITH_BACKEND !== "1" || externalBaseUrl !== undefined,
    "실제 API가 렌더하는 상세 화면 — 로컬 백엔드 전용(쓰기 발생)",
  );

  test("판매자는 수정·끌올을 보고, 가격을 내리면 다른 사람에게 인하가 보인다", async ({ page }) => {
    await signInAs(page, E2E_SELLER);
    await page.goto("/sell");

    const title = `E2E 수정 세트 ${Date.now()}`;
    await page.getByLabel("제목").fill(title);
    await page.getByLabel("설명", { exact: true }).fill("E2E 수정·끌올 확인용");
    await page.getByLabel("가격 (원)").fill("12345");
    await page.getByLabel("상품 이미지").setInputFiles({
      name: "e2e.png",
      mimeType: "image/png",
      buffer: Buffer.from(
        "iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAEUlEQVR4XmPQqLizBYQZYAwASsIIwRFEXsMAAAAASUVORK5CYII=",
        "base64",
      ),
    });
    await expect(page.getByRole("img", { name: /상품 이미지 1/ })).toBeVisible();
    await page.getByRole("button", { name: "상품 등록" }).click();

    await expect(page).toHaveURL(/\/listings\/[^/]+$/);
    const listingId = new URL(page.url()).pathname.split("/").at(-1) ?? "";
    expect(listingId).not.toBe("");

    const panel = page.getByTestId("listing-seller-panel");
    await expect(panel.getByRole("link", { name: "수정" })).toHaveAttribute(
      "href",
      `/listings/${listingId}/edit`,
    );
    // 방금 등록한 매물은 쿨다운 안이다(등록 시각 기준).
    await expect(panel.getByRole("button", { name: "끌올" })).toBeDisabled();
    await expect(panel.getByText(/후 끌올 가능$/)).toBeVisible();
    await expect(page.getByRole("button", { name: "찜하기" })).toHaveCount(0);

    await panel.getByRole("link", { name: "수정" }).click();
    await expect(page.getByRole("heading", { name: "매물 수정" })).toBeVisible();
    await expect(page.getByLabel("제목")).toHaveValue(title);
    await page.getByLabel("가격 (원)").fill("10000");
    await page.getByRole("button", { name: "수정 완료" }).click();

    await expect(page).toHaveURL(new RegExp(`/listings/${listingId}$`));
    await expect(page.getByText("₩10,000", { exact: true })).toBeVisible();
    await expect(page.getByText("2,345원 내림")).toBeVisible();

    await switchTo(page, E2E_BUYER);
    await page.reload();

    await expect(page.getByTestId("listing-seller-panel")).toHaveCount(0);
    await expect(page.getByRole("button", { name: "찜하기" })).toBeEnabled();
    await expect(page.getByText("2,345원 내림")).toBeVisible();
  });
});
