import { mkdir, rm } from "node:fs/promises";
import path from "node:path";
import { chromium, type BrowserContext, type Page } from "@playwright/test";
import type { CaptureResult, PromotionCandidate } from "./types";

const BASE_URL = process.env.PROMOTION_AGENT_BASE_URL ?? "https://gole.co.kr";

type Interaction =
  | { readonly kind: "clickRole"; readonly role: "button"; readonly name: string }
  | { readonly kind: "selectLabel"; readonly label: string; readonly value: string };

interface CaptureScenario {
  readonly route: string;
  readonly sourceSpec: string;
  readonly interactions: readonly Interaction[];
}

// 운영에서 안전한 읽기 전용 상호작용만 기존 E2E의 selector와 순서 그대로 옮긴다.
const SCENARIOS: readonly CaptureScenario[] = [
  {
    route: "/prices",
    sourceSpec: "tests-e2e/prices.spec.ts",
    interactions: [
      { kind: "clickRole", role: "button", name: "1개월" },
      { kind: "selectLabel", label: "정렬", value: "recent" },
    ],
  },
  {
    route: "/search",
    sourceSpec: "tests-e2e/search-and-listing.spec.ts",
    interactions: [
      { kind: "selectLabel", label: "카테고리", value: "parts" },
      { kind: "clickRole", role: "button", name: "검색" },
    ],
  },
  {
    route: "/community",
    sourceSpec: "tests-e2e/community.spec.ts",
    interactions: [
      { kind: "clickRole", role: "button", name: "질문" },
      { kind: "clickRole", role: "button", name: "이스터에그" },
    ],
  },
];

export function findCaptureScenario(route: string): CaptureScenario | undefined {
  return SCENARIOS.find((scenario) => scenario.route === route);
}

async function blockMutatingRequests(context: BrowserContext): Promise<void> {
  await context.route("**/*", async (route) => {
    const method = route.request().method().toUpperCase();
    if (["GET", "HEAD", "OPTIONS"].includes(method)) {
      await route.continue();
      return;
    }
    await route.abort("blockedbyclient");
  });
}

async function settle(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(1_000);
}

async function screenshot(page: Page, outputDir: string, index: number): Promise<string> {
  const outputPath = path.join(outputDir, `${String(index).padStart(2, "0")}.png`);
  await page.screenshot({ path: outputPath, animations: "disabled" });
  return outputPath;
}

async function replay(page: Page, interaction: Interaction): Promise<void> {
  if (interaction.kind === "clickRole") {
    await page.getByRole(interaction.role, { name: interaction.name, exact: true }).click({
      timeout: 5_000,
    });
    return;
  }
  await page.getByLabel(interaction.label, { exact: true }).selectOption(interaction.value, {
    timeout: 5_000,
  });
}

export async function captureCandidate(
  candidate: PromotionCandidate,
  outputRoot: string,
): Promise<CaptureResult> {
  const outputDir = path.join(outputRoot, candidate.sha);
  await rm(outputDir, { recursive: true, force: true });
  await mkdir(outputDir, { recursive: true });

  const browser = await chromium.launch();
  const context = await browser.newContext({ viewport: { width: 1440, height: 1024 } });
  await blockMutatingRequests(context);
  const page = await context.newPage();
  const scenario = findCaptureScenario(candidate.route);

  try {
    await page.goto(new URL(candidate.route, BASE_URL).toString(), {
      waitUntil: "domcontentloaded",
      timeout: 30_000,
    });
    await settle(page);

    if (scenario === undefined || scenario.interactions.length === 0) {
      return {
        paths: [await screenshot(page, outputDir, 1)],
        route: candidate.route,
        sourceSpec: null,
        fallback: true,
      };
    }

    const paths = [await screenshot(page, outputDir, 1)];
    try {
      for (const interaction of scenario.interactions.slice(0, 3)) {
        await replay(page, interaction);
        await settle(page);
        paths.push(await screenshot(page, outputDir, paths.length + 1));
      }
      return { paths, route: candidate.route, sourceSpec: scenario.sourceSpec, fallback: false };
    } catch (cause) {
      console.warn(
        `[capture] ${scenario.sourceSpec} 재생 실패, 정적 캡처로 대체함: ${cause instanceof Error ? cause.message : String(cause)}`,
      );
      await rm(outputDir, { recursive: true, force: true });
      await mkdir(outputDir, { recursive: true });
      return {
        paths: [await screenshot(page, outputDir, 1)],
        route: candidate.route,
        sourceSpec: scenario.sourceSpec,
        fallback: true,
      };
    }
  } finally {
    await browser.close();
  }
}
