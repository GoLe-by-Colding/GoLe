import { defineConfig, devices } from "@playwright/test";

// 로컬에서 이미 개발 서버가 떠 있어도 격리된 E2E 서버를 띄울 수 있게 포트를 바꿀 수 있다.
// CI 기본값은 그대로 3000이다.
const PORT = process.env.E2E_WEB_PORT ?? "3000";
// E2E_BASE_URL 지정 시 외부(배포) 환경을 대상으로 테스트하고 dev 서버를 띄우지 않는다.
// 미지정 시 로컬 dev 서버(pnpm dev)를 자동 기동한다.
const EXTERNAL = process.env.E2E_BASE_URL;
const BASE_URL = EXTERNAL ?? `http://localhost:${PORT}`;
const ANALYTICS_CONSENT_STORAGE_KEY = "gole.analytics-consent.v1";
const DENIED_ANALYTICS_STATE = JSON.stringify({
  version: 1,
  decision: "denied",
  updatedAt: "2026-09-04T00:00:00.000Z",
});

export default defineConfig({
  testDir: "./tests-e2e",
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 1,
  workers: process.env.CI ? 1 : undefined,
  reporter: process.env.CI ? "github" : "html",
  use: {
    baseURL: BASE_URL,
    trace: "on-first-retry",
    // 분석 테스트 한 건을 제외한 E2E는 선택 배너의 영향과 외부 요청 없이 제품 기능만 검증한다.
    storageState: {
      cookies: [],
      origins: [
        {
          origin: new URL(BASE_URL).origin,
          localStorage: [{ name: ANALYTICS_CONSENT_STORAGE_KEY, value: DENIED_ANALYTICS_STATE }],
        },
      ],
    },
  },
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
      testIgnore: /mobile\.spec\.ts/,
    },
    {
      // 모바일 뷰포트(Pixel 5, 터치). mobile.spec 만 실행.
      name: "mobile-chrome",
      use: { ...devices["Pixel 5"] },
      testMatch: /mobile\.spec\.ts/,
    },
  ],
  // 외부 대상이 아니면 로컬 dev 서버를 자동 기동한다.
  ...(EXTERNAL
    ? {}
    : {
        webServer: {
          command: `pnpm exec next dev --port ${PORT}`,
          url: BASE_URL,
          reuseExistingServer: !process.env.CI,
          timeout: 120_000,
          // 브라우저는 항상 스텁 결제로 돈다. 실제 PortOne 결제창은 자동화할 수 없으므로,
          // portone-test 모드가 켜지면 구매 플로우가 결제창을 기다리다 "결제 대기"에서 멈춘다.
          //
          // 결제 요청 조립 계약 테스트(portone-request.spec.ts)는 브라우저가 아니라 이
          // Node 프로세스에서 돌기 때문에, 여기서 덮어써도 그 테스트의 환경은 그대로다.
          env: {
            ...process.env,
            NEXT_DIST_DIR: process.env.NEXT_DIST_DIR ?? ".next/playwright-e2e",
            NEXT_PUBLIC_PAYMENT_MODE: "stub",
            // 유효한 테스트용 공개 ID다. 둘 다 설정해 GTM 우선·GA 미중복 계약을 검증한다.
            NEXT_PUBLIC_GA_MEASUREMENT_ID: "G-PLAYWRIGHT01",
            NEXT_PUBLIC_GTM_ID: "GTM-PLAYWRIGHT01",
          },
        },
      }),
});
