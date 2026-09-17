import Anthropic from "@anthropic-ai/sdk";
import type { BetaMessageParam } from "@anthropic-ai/sdk/resources/beta/messages";
import { appendFile, mkdir, readdir, rm, stat, readFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { PromotionBrowserSession } from "./capture";
import { loadPromotionAgentEnvironment } from "./env";
import {
  createAuthenticatedPromotionDraftSubmitter,
  createPromotionDraftSubmitter,
} from "./publish-draft";
import { listRecentFrontendCommits } from "./scan";
import { createPromotionAgentTools, listPublicRoutes } from "./tools/index";
import type { PromotionCandidate } from "./types";

const DEFAULT_MODEL = "claude-opus-5";
const DEFAULT_OUTPUT_DIRECTORY = "/var/lib/gole/promotion-agent";
const MAX_CONTEXT_IMAGES = 4;
const RETENTION_MS = 7 * 24 * 60 * 60 * 1_000;

type LogValue =
  null | boolean | number | string | readonly LogValue[] | { readonly [key: string]: LogValue };

function requiredEnvironment(name: string): string {
  const value = process.env[name]?.trim();
  if (!value) throw new Error(`${name} 환경변수가 필요함`);
  return value;
}

async function removeExpiredSessions(outputRoot: string, now = Date.now()): Promise<void> {
  const entries = await readdir(outputRoot, { withFileTypes: true });
  for (const entry of entries) {
    if (!entry.isDirectory()) continue;
    const target = path.resolve(outputRoot, entry.name);
    if (path.dirname(target) !== outputRoot) continue;
    const metadata = await stat(target);
    if (now - metadata.mtimeMs > RETENTION_MS) await rm(target, { recursive: true, force: true });
  }
}

function redactImages(value: unknown, screenshotPath: string | null = null): LogValue {
  if (
    value === null ||
    typeof value === "boolean" ||
    typeof value === "number" ||
    typeof value === "string"
  ) {
    return value;
  }
  if (Array.isArray(value)) {
    const pathSummary = value.find(
      (item): item is { type: "text"; text: string } =>
        typeof item === "object" &&
        item !== null &&
        "type" in item &&
        item.type === "text" &&
        "text" in item &&
        typeof item.text === "string" &&
        item.text.startsWith("스크린샷 "),
    );
    const capturedPath =
      pathSummary?.text.slice(pathSummary.text.indexOf(": ") + 2) ?? screenshotPath;
    return value.map((item) => redactImages(item, capturedPath));
  }
  if (typeof value === "object") {
    const record = value as Record<string, unknown>;
    if (record.type === "image") {
      return { type: "image_path", path: screenshotPath ?? "경로 없음" };
    }
    return Object.fromEntries(
      Object.entries(record).map(([key, item]) => [key, redactImages(item, screenshotPath)]),
    );
  }
  return String(value);
}

async function appendSessionEvent(logPath: string, type: string, payload: unknown): Promise<void> {
  await appendFile(
    logPath,
    `${JSON.stringify({ at: new Date().toISOString(), type, payload: redactImages(payload) })}\n`,
    "utf8",
  );
}

function keepRecentImages(messages: readonly BetaMessageParam[]): BetaMessageParam[] {
  const compacted: BetaMessageParam[] = structuredClone([...messages]);
  let retainedImages = 0;

  for (let messageIndex = compacted.length - 1; messageIndex >= 0; messageIndex -= 1) {
    const message = compacted[messageIndex];
    if (message === undefined || !Array.isArray(message.content)) continue;
    for (let blockIndex = message.content.length - 1; blockIndex >= 0; blockIndex -= 1) {
      const block = message.content[blockIndex];
      if (block?.type !== "tool_result" || !Array.isArray(block.content)) continue;
      const content = [...block.content];
      for (let itemIndex = content.length - 1; itemIndex >= 0; itemIndex -= 1) {
        if (content[itemIndex]?.type !== "image") continue;
        retainedImages += 1;
        if (retainedImages > MAX_CONTEXT_IMAGES) {
          content[itemIndex] = {
            type: "text",
            text: "이전 스크린샷 이미지는 같은 도구 결과의 파일 경로 요약으로 대체됨.",
          };
        }
      }
      message.content[blockIndex] = { ...block, content };
    }
  }
  return compacted;
}

async function runCandidateSession(
  client: Anthropic,
  candidate: PromotionCandidate,
  outputRoot: string,
  submitDraft: ReturnType<typeof createPromotionDraftSubmitter>,
): Promise<void> {
  const sessionName = `${new Date().toISOString().replace(/[:.]/gu, "-")}-${candidate.sha}`;
  const sessionDirectory = path.resolve(outputRoot, sessionName);
  await mkdir(sessionDirectory, { recursive: false });
  const logPath = path.join(sessionDirectory, "session.jsonl");
  const browser = new PromotionBrowserSession(new Set(await listPublicRoutes()), sessionDirectory);
  const promptPath = path.join(
    path.dirname(fileURLToPath(import.meta.url)),
    "prompts",
    "caption-tone.md",
  );
  const toneGuide = await readFile(promptPath, "utf8");

  try {
    const runner = client.beta.messages.toolRunner({
      model: process.env.ANTHROPIC_MODEL?.trim() || DEFAULT_MODEL,
      max_tokens: 16_000,
      max_iterations: 20,
      system: `${toneGuide}\n\n너는 GoLe 홍보 초안 에이전트다. 커밋 diff와 공개 라우트를 직접 조사하고, 사용자에게 보이는 변화가 잘 드러나는 화면을 순서대로 캡처해. 캡션은 diff 원문이 아니라 캡처한 화면에 근거해 작성하고, 가치 있는 초안이라고 판단할 때 submit_promotion_draft를 호출해.`,
      messages: [
        {
          role: "user",
          content: `후보 커밋 ${candidate.sha}를 조사해 홍보 초안을 만들지 판단해. 커밋 제목은 탐색 단서로만 사용해: ${candidate.subject}`,
        },
      ],
      tools: createPromotionAgentTools({ browser, candidateSha: candidate.sha, submitDraft }),
    });

    let finalStopReason: string | null = null;
    for await (const message of runner) {
      finalStopReason = message.stop_reason;
      if (message.stop_reason === "refusal") {
        await appendSessionEvent(logPath, "refusal", { stopReason: message.stop_reason });
        throw new Error(`모델이 후보 처리를 거부함: ${candidate.sha}`);
      }

      await appendSessionEvent(logPath, "assistant", message);
      const toolResponse = await runner.generateToolResponse();
      if (toolResponse === null) continue;
      await appendSessionEvent(logPath, "tools", toolResponse);
      const assistantMessage: BetaMessageParam = { role: message.role, content: message.content };
      runner.setMessagesParams((params) => ({
        ...params,
        messages: keepRecentImages([...params.messages, assistantMessage, toolResponse]),
      }));
    }

    if (finalStopReason === "tool_use" || finalStopReason === "max_tokens") {
      throw new Error(`에이전트 세션이 완료되지 않음(stop_reason=${finalStopReason})`);
    }
  } finally {
    await browser.close();
  }
}

export async function run(): Promise<void> {
  loadPromotionAgentEnvironment();
  const outputRoot = path.resolve(
    process.env.PROMOTION_AGENT_OUTPUT_DIR?.trim() || DEFAULT_OUTPUT_DIRECTORY,
  );
  await mkdir(outputRoot, { recursive: true });
  await removeExpiredSessions(outputRoot);

  const client = new Anthropic({ apiKey: requiredEnvironment("ANTHROPIC_API_KEY") });
  const submitDraft = createAuthenticatedPromotionDraftSubmitter();
  const candidates = await listRecentFrontendCommits();
  console.log(`[agent] 후보 ${candidates.length}개`);

  const failures: string[] = [];
  for (const candidate of candidates) {
    try {
      console.log(`[agent] ${candidate.sha.slice(0, 12)} 처리 시작`);
      await runCandidateSession(client, candidate, outputRoot, submitDraft);
      console.log(`[agent] ${candidate.sha.slice(0, 12)} 처리 완료`);
    } catch (cause) {
      failures.push(candidate.sha);
      console.error(
        `[agent] ${candidate.sha.slice(0, 12)} 처리 실패: ${cause instanceof Error ? cause.stack : String(cause)}`,
      );
    }
  }
  if (failures.length > 0) {
    throw new Error(`후보 ${failures.length}개 처리 실패: ${failures.join(", ")}`);
  }
}

const invokedPath = process.argv[1];
if (
  invokedPath !== undefined &&
  import.meta.url === pathToFileURL(path.resolve(invokedPath)).href
) {
  void run().catch((cause: unknown) => {
    console.error(cause instanceof Error ? cause.stack : cause);
    process.exitCode = 1;
  });
}
