import { mkdir, readFile } from "node:fs/promises";
import path from "node:path";
import { chromium, type Browser, type BrowserContext, type Page } from "@playwright/test";

const DEFAULT_BASE_URL = "https://gole.co.kr";
const INTERACTION_TIMEOUT_MS = 5_000;

export interface CapturedScreenshot {
  readonly label: string;
  readonly path: string;
  readonly base64: string;
}

export async function blockMutatingRequests(
  context: BrowserContext,
  isNavigationAllowed: (url: string) => boolean = () => true,
): Promise<void> {
  await context.route("**/*", async (route) => {
    const request = route.request();
    const method = request.method().toUpperCase();
    if (request.isNavigationRequest() && !isNavigationAllowed(request.url())) {
      await route.abort("blockedbyclient");
      return;
    }
    if (["GET", "HEAD", "OPTIONS"].includes(method)) {
      await route.continue();
      return;
    }
    await route.abort("blockedbyclient");
  });
}

function safeScreenshotName(label: string, index: number): string {
  const slug = label
    .normalize("NFKC")
    .replace(/[^\p{Letter}\p{Number}]+/gu, "-")
    .replace(/^-|-$/gu, "")
    .slice(0, 48);
  return `${String(index).padStart(2, "0")}-${slug || "screenshot"}.png`;
}

export class PromotionBrowserSession {
  readonly #allowedRoutes: ReadonlySet<string>;
  readonly #baseUrl: URL;
  readonly #outputDir: string;
  #browser: Browser | undefined;
  #page: Page | undefined;
  #screenshots: CapturedScreenshot[] = [];

  constructor(allowedRoutes: ReadonlySet<string>, outputDir: string) {
    this.#allowedRoutes = allowedRoutes;
    this.#baseUrl = new URL(process.env.PROMOTION_AGENT_BASE_URL?.trim() || DEFAULT_BASE_URL);
    this.#outputDir = path.resolve(outputDir);
  }

  get screenshots(): readonly CapturedScreenshot[] {
    return this.#screenshots;
  }

  async goto(route: string): Promise<string> {
    if (!this.#allowedRoutes.has(route)) throw new Error(`허용되지 않은 공개 라우트: ${route}`);
    const page = await this.#ensurePage();
    await page.goto(new URL(route, this.#baseUrl).toString(), {
      waitUntil: "domcontentloaded",
      timeout: 30_000,
    });
    await this.#settle(page);
    return `이동 완료: ${route}`;
  }

  async click(role: string, name: string): Promise<string> {
    const page = this.#requirePage();
    await page.getByRole(role as Parameters<Page["getByRole"]>[0], { name, exact: true }).click({
      timeout: INTERACTION_TIMEOUT_MS,
    });
    await this.#settle(page);
    return `클릭 완료: ${role} "${name}"`;
  }

  async select(label: string, value: string): Promise<string> {
    const page = this.#requirePage();
    await page.getByLabel(label, { exact: true }).selectOption(value, {
      timeout: INTERACTION_TIMEOUT_MS,
    });
    await this.#settle(page);
    return `선택 완료: ${label}=${value}`;
  }

  async screenshot(label: string): Promise<CapturedScreenshot> {
    const page = this.#requirePage();
    await mkdir(this.#outputDir, { recursive: true });
    const outputPath = path.resolve(
      this.#outputDir,
      safeScreenshotName(label, this.#screenshots.length + 1),
    );
    if (path.dirname(outputPath) !== this.#outputDir) {
      throw new Error("스크린샷은 현재 세션 디렉터리에만 저장할 수 있음");
    }
    await page.screenshot({ path: outputPath, animations: "disabled" });
    const captured = {
      label,
      path: outputPath,
      base64: (await readFile(outputPath)).toString("base64"),
    };
    this.#screenshots.push(captured);
    return captured;
  }

  async close(): Promise<void> {
    await this.#browser?.close();
    this.#browser = undefined;
    this.#page = undefined;
  }

  async #ensurePage(): Promise<Page> {
    if (this.#page !== undefined) return this.#page;
    this.#browser = await chromium.launch({ args: ["--disable-dev-shm-usage"] });
    const context = await this.#browser.newContext({ viewport: { width: 1440, height: 1024 } });
    await blockMutatingRequests(context, (url) => {
      const destination = new URL(url);
      return (
        destination.origin === this.#baseUrl.origin && this.#allowedRoutes.has(destination.pathname)
      );
    });
    this.#page = await context.newPage();
    return this.#page;
  }

  #requirePage(): Page {
    if (this.#page === undefined) throw new Error("browser_goto를 먼저 호출해야 함");
    return this.#page;
  }

  async #settle(page: Page): Promise<void> {
    await page.waitForLoadState("domcontentloaded");
    await page.waitForTimeout(1_000);
  }
}
