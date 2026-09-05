import { expect, test } from "@playwright/test";
import {
  resolveAnalyticsRuntimeConfig,
  validateOptionalAnalyticsId,
} from "../src/shared/config/analytics";
import {
  GTM_ANALYTICS_POLICY,
  sanitizeAnalyticsPagePath,
} from "../src/widgets/analytics-consent/model/analytics-consent";

const STORAGE_KEY = "gole.analytics-consent.v1";
const TEST_GA_ID = "G-PLAYWRIGHT01";
const TEST_GTM_ID = "GTM-PLAYWRIGHT01";
const isExternal = process.env.E2E_BASE_URL !== undefined;

test.describe("분석 공개 환경설정", () => {
  test("빈 값은 완전 비활성이고 GTM은 직접 GA보다 우선한다", () => {
    expect(resolveAnalyticsRuntimeConfig({ gaMeasurementId: "", gtmId: "" })).toMatchObject({
      provider: "disabled",
      id: "",
    });
    expect(
      resolveAnalyticsRuntimeConfig({ gaMeasurementId: TEST_GA_ID, gtmId: TEST_GTM_ID }),
    ).toMatchObject({ provider: "gtm", id: TEST_GTM_ID });
    expect(resolveAnalyticsRuntimeConfig({ gaMeasurementId: TEST_GA_ID, gtmId: "" })).toMatchObject(
      { provider: "ga", id: TEST_GA_ID },
    );
  });

  test("비정상·공백 포함 ID는 빌드 계약에서 거부한다", () => {
    expect(validateOptionalAnalyticsId("NEXT_PUBLIC_GA_MEASUREMENT_ID", undefined)).toBe("");
    expect(validateOptionalAnalyticsId("NEXT_PUBLIC_GTM_ID", "")).toBe("");
    expect(validateOptionalAnalyticsId("NEXT_PUBLIC_GA_MEASUREMENT_ID", TEST_GA_ID)).toBe(
      TEST_GA_ID,
    );
    expect(validateOptionalAnalyticsId("NEXT_PUBLIC_GTM_ID", TEST_GTM_ID)).toBe(TEST_GTM_ID);
    expect(() => validateOptionalAnalyticsId("NEXT_PUBLIC_GA_MEASUREMENT_ID", "UA-OLD")).toThrow(
      /G-/,
    );
    expect(() => validateOptionalAnalyticsId("NEXT_PUBLIC_GTM_ID", " GTM-BAD")).toThrow(/GTM-/);
  });

  test("분석 경로에서 쿼리와 개인·거래 객체 식별자를 제거한다", () => {
    expect(sanitizeAnalyticsPagePath("/orders/REG-secret?paymentId=private")).toBe("/orders/:id");
    expect(sanitizeAnalyticsPagePath("/shops/account-123")).toBe("/shops/:sellerId");
    expect(sanitizeAnalyticsPagePath("/community/new")).toBe("/community/new");
    expect(sanitizeAnalyticsPagePath("/community/private-post#comments")).toBe("/community/:id");
    expect(sanitizeAnalyticsPagePath("not-a-path")).toBe("/");
  });
});

test.describe("분석 동의", () => {
  test.skip(isExternal, "운영의 실제 분석 ID와 선택 상태를 변경하지 않는 로컬 전용 테스트");

  test.beforeEach(async ({ page }) => {
    await page.addInitScript((storageKey) => {
      const marker = `${storageKey}.test-initialized`;
      if (window.sessionStorage.getItem(marker) === null) {
        window.localStorage.removeItem(storageKey);
        window.sessionStorage.setItem(marker, "true");
      }
    }, STORAGE_KEY);
  });

  test("동의 전·거부 후 요청이 없고 허용 시 GTM만 한 번 로드한다", async ({ page }) => {
    const googleRequests: string[] = [];
    page.on("request", (request) => {
      const hostname = new URL(request.url()).hostname;
      if (hostname.endsWith("google-analytics.com") || hostname === "www.googletagmanager.com") {
        googleRequests.push(request.url());
      }
    });
    await page.route("https://www.googletagmanager.com/**", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/javascript",
        body: "window.__goleTestGtmLoaded = true;",
      }),
    );
    await page.route("https://*.google-analytics.com/**", (route) =>
      route.abort("blockedbyclient"),
    );

    const response = await page.goto("/terms");
    expect(response?.headers()["content-security-policy"]).toContain(
      "https://www.googletagmanager.com",
    );
    const dialog = page.getByTestId("analytics-consent-dialog");
    await expect(dialog).toBeVisible();
    await expect(page.getByRole("dialog", { name: "서비스 이용 분석 설정" })).toBeVisible();
    await expect(dialog).toHaveAttribute("aria-modal", "false");
    expect(await dialog.evaluate((element) => element.matches(":modal"))).toBe(false);
    await expect(dialog).toContainText("현재 선택: 미선택");
    const rejectButton = page.getByRole("button", { name: "거부", exact: true });
    const allowButton = page.getByRole("button", { name: "분석 허용" });
    await expect(rejectButton).toBeFocused();
    expect(await rejectButton.getAttribute("class")).toBe(await allowButton.getAttribute("class"));
    await page.waitForTimeout(150);
    expect(googleRequests).toEqual([]);

    await page.keyboard.press("Escape");
    await expect(dialog).not.toBeVisible();
    await page.getByRole("link", { name: "분석 설정" }).click();
    await page.getByRole("button", { name: "거부", exact: true }).click();
    await expect(dialog).not.toBeVisible();
    await page.getByRole("link", { name: "탐색", exact: true }).first().click();
    await expect(page).toHaveURL(/\/search$/);
    await expect(page.getByRole("heading", { name: "상품 탐색" })).toBeVisible();
    await page.reload();
    await expect(dialog).not.toBeVisible();
    expect(googleRequests).toEqual([]);

    await page.goto("/terms");
    await page.getByRole("link", { name: "분석 설정" }).click();
    await expect(dialog).toBeVisible();
    await expect(dialog).toContainText("현재 선택: 거부");
    await page.getByRole("button", { name: "분석 허용" }).click();
    await expect.poll(() => googleRequests.filter((url) => url.includes("/gtm.js")).length).toBe(1);
    expect(googleRequests.some((url) => url.includes("/gtag/js"))).toBe(false);
    expect(googleRequests.some((url) => url.includes("google-analytics.com"))).toBe(false);
    await expect
      .poll(() =>
        page.evaluate(() => {
          const dataLayer = (window as unknown as { dataLayer?: unknown[] }).dataLayer ?? [];
          return dataLayer.filter(
            (entry) =>
              !Array.isArray(entry) &&
              (entry as Record<string, unknown>)["event"] === "gole_page_view",
          ).length;
        }),
      )
      .toBe(1);

    const dataLayer = await page.evaluate(() => {
      const dataLayer = (window as unknown as { dataLayer?: unknown[] }).dataLayer ?? [];
      return dataLayer;
    });
    const queuedCommands = dataLayer.map((entry) =>
      Array.isArray(entry) ? entry : Object.values(entry as Record<string, unknown>),
    );
    expect(queuedCommands).toContainEqual(["set", "cookie_expires", 7_776_000]);
    expect(queuedCommands).toContainEqual(["set", "cookie_update", false]);
    expect(queuedCommands).toContainEqual(["set", "allow_google_signals", false]);
    expect(queuedCommands).toEqual(
      expect.arrayContaining([
        expect.arrayContaining([
          "consent",
          "default",
          expect.objectContaining({
            analytics_storage: "granted",
            ad_storage: "denied",
            ad_user_data: "denied",
            ad_personalization: "denied",
          }),
        ]),
      ]),
    );
    expect(
      dataLayer.filter(
        (entry) =>
          !Array.isArray(entry) &&
          (entry as Record<string, unknown>)["event"] === "gole_analytics_policy",
      ),
    ).toEqual([GTM_ANALYTICS_POLICY]);
    expect(
      dataLayer.filter(
        (entry) =>
          !Array.isArray(entry) && (entry as Record<string, unknown>)["event"] === "gole_page_view",
      ),
    ).toHaveLength(1);
    expect(
      dataLayer.some(
        (entry) => Array.isArray(entry) && entry[0] === "event" && entry[1] === "page_view",
      ),
    ).toBe(false);
  });

  test("철회는 분석 쿠키만 지우고 재설정은 최초 선택으로 되돌린다", async ({ page }) => {
    const googleRequests: string[] = [];
    page.on("request", (request) => {
      const hostname = new URL(request.url()).hostname;
      if (hostname.endsWith("google-analytics.com") || hostname === "www.googletagmanager.com") {
        googleRequests.push(request.url());
      }
    });
    await page.route("https://www.googletagmanager.com/**", (route) =>
      route.abort("blockedbyclient"),
    );
    await page.goto("/terms");
    await page.getByRole("button", { name: "분석 허용" }).click();
    await expect.poll(() => googleRequests.length).toBe(1);
    await page.evaluate(() => {
      document.cookie = "_ga=test-client; Path=/; SameSite=Lax";
      document.cookie = "_ga_TEST=test-session; Path=/; SameSite=Lax";
      document.cookie = "gole_required_test=keep; Path=/; SameSite=Lax";
    });

    const requestCountBeforeWithdrawal = googleRequests.length;
    await page.getByRole("link", { name: "분석 설정" }).click();
    await Promise.all([
      page.waitForNavigation({ waitUntil: "domcontentloaded" }),
      page.getByRole("button", { name: "동의 철회" }).click(),
    ]);
    const cookiesAfterWithdrawal = await page.evaluate(() => document.cookie);
    expect(cookiesAfterWithdrawal).not.toContain("_ga=");
    expect(cookiesAfterWithdrawal).not.toContain("_ga_TEST=");
    expect(cookiesAfterWithdrawal).toContain("gole_required_test=keep");
    await page.waitForTimeout(150);
    expect(googleRequests).toHaveLength(requestCountBeforeWithdrawal);
    await expect(page.locator('script[data-gole-analytics="true"]')).toHaveCount(0);
    expect(await page.evaluate((key) => window.localStorage.getItem(key), STORAGE_KEY)).toContain(
      '"decision":"denied"',
    );

    await page.getByRole("link", { name: "분석 설정" }).click();
    await page.getByRole("button", { name: "선택 초기화" }).click();
    await expect(page.getByTestId("analytics-consent-dialog")).toContainText("현재 선택: 미선택");
    expect(await page.evaluate((key) => window.localStorage.getItem(key), STORAGE_KEY)).toBeNull();
  });
});
