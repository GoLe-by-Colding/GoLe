import { expect, test, type Page } from "@playwright/test";

const externalBaseUrl = process.env.E2E_BASE_URL;
const SAFE_METHODS = new Set(["GET", "HEAD", "OPTIONS"]);

async function blockMutatingRequests(page: Page): Promise<string[]> {
  const blocked: string[] = [];
  await page.route("**/*", async (route) => {
    const request = route.request();
    if (SAFE_METHODS.has(request.method())) {
      await route.continue();
      return;
    }

    blocked.push(`${request.method()} ${new URL(request.url()).pathname}`);
    await route.abort("blockedbyclient");
  });
  return blocked;
}

async function expectHealthyPage(page: Page, path: string, heading: string): Promise<void> {
  const blocked = await blockMutatingRequests(page);
  const response = await page.goto(path, { waitUntil: "domcontentloaded" });

  expect(response, `${path}: navigation response`).not.toBeNull();
  expect(response!.status(), `${path}: HTTP status`).toBeLessThan(400);
  await expect(page.getByRole("heading", { name: heading }).first()).toBeVisible();
  expect(blocked, `${path}: live smoke must not attempt a mutating request`).toEqual([]);
}

test.describe("gole.co.kr read-only live smoke", () => {
  test.skip(externalBaseUrl === undefined, "E2E_BASE_URL을 지정한 배포 대상 전용 smoke");

  test("public readiness and launch configuration respond", async ({ request }) => {
    const readiness = await request.get("/actuator/health/readiness");
    expect(readiness.ok()).toBeTruthy();

    const launch = await request.get("/api/v1/config/launch");
    expect(launch.ok()).toBeTruthy();
    await expect(launch.json()).resolves.toEqual(
      expect.objectContaining({
        stage: expect.any(Number),
        tradeMode: "DIRECT_CHAT",
        features: expect.objectContaining({
          payments: false,
          partnerPayout: false,
        }),
        sellerIdentityVerificationReady: false,
      }),
    );
  });

  test("public SEO discovery endpoints expose legal pages", async ({ request }) => {
    const sitemap = await request.get("/sitemap.xml");
    expect(sitemap.ok()).toBeTruthy();
    const sitemapXml = await sitemap.text();
    expect(sitemapXml).toMatch(/<loc>https?:\/\/[^<]+\/terms<\/loc>/);
    expect(sitemapXml).toMatch(/<loc>https?:\/\/[^<]+\/privacy<\/loc>/);
    expect(sitemapXml).toMatch(/<loc>https?:\/\/[^<]+\/review-policy<\/loc>/);

    const robots = await request.get("/robots.txt");
    expect(robots.ok()).toBeTruthy();
    const robotsText = await robots.text();
    expect(robotsText).toMatch(/User-Agent:\s*\*/i);
    expect(robotsText).toMatch(/Sitemap:\s*https?:\/\/\S+\/sitemap\.xml/i);
  });

  test("home footer exposes the operator and brick wording without marketplace branding", async ({
    page,
  }) => {
    const blocked = await blockMutatingRequests(page);
    const response = await page.goto("/", { waitUntil: "domcontentloaded" });

    expect(response?.status()).toBeLessThan(400);
    const footer = page.locator("footer");
    await expect(footer).toContainText("상호 콜딩(Colding)");
    await expect(footer).toContainText("대표 김승찬");
    await expect(footer).toContainText("사업자등록번호 457-49-00942");
    await expect(footer).toContainText("호스팅서비스 제공자 Google Cloud Platform");
    await expect(footer.getByRole("link", { name: "공정거래위원회에서 확인" })).toHaveAttribute(
      "href",
      "https://www.ftc.go.kr/bizCommPop.do?wrkr_no=4574900942",
    );
    await expect(footer.getByRole("link", { name: "후기 운영정책" })).toHaveAttribute(
      "href",
      "/review-policy",
    );
    await expect(footer).toContainText("브릭");
    await expect(page.getByText("GoLe 공개 준비에 함께해 주세요")).toBeVisible();
    await expect(page.getByRole("link", { name: "커뮤니티 참여하기" })).toHaveAttribute(
      "href",
      "/community",
    );
    await expect(page.getByRole("link", { name: "판매 시작하기" })).toHaveCount(0);
    await expect(page.locator("body")).not.toContainText(/LEGO Marketplace|레고 마켓플레이스/i);
    expect(blocked, "home footer: live smoke must not attempt a mutating request").toEqual([]);
  });

  test("sell page is fail-closed with working browse and support routes", async ({ page }) => {
    const blocked = await blockMutatingRequests(page);
    const response = await page.goto("/sell", { waitUntil: "domcontentloaded" });

    expect(response?.status()).toBeLessThan(400);
    await expect(page.getByText("신규 상품 등록 준비 중")).toBeVisible();
    await expect(page.getByRole("button", { name: "상품 등록" })).toHaveCount(0);
    await expect(page.getByRole("link", { name: "상품 둘러보기" })).toHaveAttribute(
      "href",
      "/search",
    );
    await expect(page.getByRole("link", { name: "운영 문의" })).toHaveAttribute(
      "href",
      "/chat?compose=support&category=PRODUCT_FEEDBACK",
    );
    expect(blocked, "sell: live smoke must not attempt a mutating request").toEqual([]);
  });

  test("existing listing Q&A stays read-only while seller verification is closed", async ({
    page,
    request,
  }) => {
    const response = await request.get("/api/v1/listings");
    expect(response.ok()).toBeTruthy();
    const listings = (await response.json()) as Array<{ readonly id: string }>;
    test.skip(
      listings.length === 0,
      "공개 중인 기존 매물이 없어 상세 읽기 전용 상태를 확인할 수 없음",
    );

    const blocked = await blockMutatingRequests(page);
    await page.goto(`/listings/${listings[0]!.id}`, { waitUntil: "domcontentloaded" });

    await expect(page.getByRole("heading", { name: "문의 Q&A" })).toBeVisible();
    await expect(
      page.getByText(/과거 문의만 볼 수 있고 새 상품 문의는 받지 않습니다/),
    ).toBeVisible();
    await expect(page.getByPlaceholder("궁금한 점을 남겨주세요.")).toHaveCount(0);
    await expect(page.getByRole("link", { name: "운영팀에 문의하기" })).toHaveAttribute(
      "href",
      "/chat?compose=support&category=TRADE",
    );
    expect(blocked, "listing Q&A: live smoke must not attempt a mutating request").toEqual([]);
  });

  for (const [path, heading] of [
    ["/", "브릭을 가장 합리적으로"],
    ["/search", "상품 탐색"],
    ["/prices", "시세"],
    ["/community", "커뮤니티"],
  ] as const) {
    test(`${path} renders without mutating production`, async ({ page }) => {
      await expectHealthyPage(page, path, heading);
    });
  }

  test("mobile home renders without mutating production", async ({ page }) => {
    await page.setViewportSize({ width: 393, height: 851 });
    const blocked = await blockMutatingRequests(page);
    const response = await page.goto("/", { waitUntil: "domcontentloaded" });

    expect(response?.status()).toBeLessThan(400);
    await expect(page.getByRole("button", { name: "메뉴 열기" })).toBeVisible();
    expect(blocked, "mobile home: live smoke must not attempt a mutating request").toEqual([]);
  });
});
