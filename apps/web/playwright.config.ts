import { defineConfig, devices } from "@playwright/test";

const PORT = 3000;
// E2E_BASE_URL 지정 시 외부(배포) 환경을 대상으로 테스트하고 dev 서버를 띄우지 않는다.
// 미지정 시 로컬 dev 서버(pnpm dev)를 자동 기동한다.
const EXTERNAL = process.env.E2E_BASE_URL;
const BASE_URL = EXTERNAL ?? `http://localhost:${PORT}`;
// unit 프로젝트만 돌릴 때는 브라우저도 dev 서버도 필요 없다. 켜두면 순수 로직 한 줄 고칠 때마다
// Next 부팅을 기다리게 되어 TDD 반복이 느려진다.
const UNIT_ONLY = process.env.PW_UNIT_ONLY === "1";

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
  },
  projects: [
    {
      // 브라우저 없이 도는 순수 로직 테스트(결제 페이로드 조립 등).
      // 별도 러너를 들이지 않고 Playwright를 그대로 쓴다. `--project=unit`으로 빠르게 반복한다.
      name: "unit",
      testDir: "./tests-unit",
    },
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
  ...(EXTERNAL || UNIT_ONLY
    ? {}
    : {
        webServer: {
          command: "pnpm dev",
          url: BASE_URL,
          reuseExistingServer: !process.env.CI,
          timeout: 120_000,
        },
      }),
});
