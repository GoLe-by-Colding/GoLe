import { execFile } from "node:child_process";
import { mkdir } from "node:fs/promises";
import path from "node:path";
import { promisify } from "node:util";
import { pathToFileURL } from "node:url";
import { captureCandidate } from "./capture";
import { generateCaption } from "./caption";
import { authenticatePromotionAgent, fetchExistingCommitShas, publishDraft } from "./publish-draft";
import type { PromotionCandidate } from "./types";

const execFileAsync = promisify(execFile);
const FEAT_COMMIT = /^feat(?:\([^)]*\))?!?:\s/u;
const DEFAULT_LOOKBACK_HOURS = 27;

const ROUTE_HINTS: readonly [string, string][] = [
  ["pricing", "/prices"],
  ["price", "/prices"],
  ["search", "/search"],
  ["listing", "/search"],
  ["community", "/community"],
  ["feed", "/feed"],
  ["chat", "/chat"],
  ["profile", "/profile"],
  ["notification", "/notifications"],
  ["collection", "/collection"],
  ["order", "/orders"],
  ["admin", "/admin"],
];

const ROUTE_LABELS: Readonly<Record<string, string>> = {
  "/": "홈",
  "/prices": "시세",
  "/search": "상품 탐색",
  "/community": "커뮤니티",
  "/feed": "팔로잉 피드",
  "/chat": "대화",
  "/profile": "프로필",
  "/notifications": "알림",
  "/collection": "컬렉션",
  "/admin": "관리자",
};

const AREA_LABELS: Readonly<Record<string, string>> = {
  "create-listing": "상품 등록",
  listing: "상품",
  pricing: "시세",
  community: "커뮤니티",
  discovery: "탐색",
  notification: "알림",
  wishlist: "관심 상품",
  collection: "컬렉션",
  profile: "프로필",
  chat: "대화",
  order: "거래",
};

function lookbackHours(): number {
  const value = Number(process.env.PROMOTION_AGENT_LOOKBACK_HOURS ?? DEFAULT_LOOKBACK_HOURS);
  if (!Number.isInteger(value) || value < 24 || value > 27) {
    throw new Error("PROMOTION_AGENT_LOOKBACK_HOURS는 24~27 사이의 정수여야 함");
  }
  return value;
}

function routeFromAppPage(file: string): string | null {
  const match = /^apps\/web\/src\/app\/(.+)\/page\.tsx$/u.exec(file.replaceAll("\\", "/"));
  if (match?.[1] === undefined) return null;
  const segments = match[1].split("/").filter((segment) => !/^\(.+\)$/u.test(segment));
  if (segments.some((segment) => segment.startsWith("["))) return null;
  return `/${segments.join("/")}`.replace(/\/$/u, "") || "/";
}

export function inferRoute(changedFiles: readonly string[]): string {
  for (const file of changedFiles) {
    const direct = routeFromAppPage(file);
    if (direct !== null) return direct;
  }
  const joined = changedFiles.join("/").toLowerCase();
  return ROUTE_HINTS.find(([hint]) => joined.includes(hint))?.[1] ?? "/";
}

export function buildReadableSummary(changedFiles: readonly string[], route: string): string {
  const areas = new Set<string>();
  for (const file of changedFiles) {
    const normalized = file.replaceAll("\\", "/");
    const match = /apps\/web\/src\/(?:views|widgets|features|entities)\/([^/]+)/u.exec(normalized);
    const area = match?.[1];
    const label = area === undefined ? undefined : AREA_LABELS[area];
    if (label !== undefined) areas.add(label);
  }
  const areaText = [...areas].slice(0, 3).join(", ");
  const routeLabel = ROUTE_LABELS[route] ?? "서비스";
  return areaText.length > 0
    ? `GoLe의 ${routeLabel} 화면에서 ${areaText} 관련 사용자 경험이 바뀌었어.`
    : `GoLe의 ${routeLabel} 화면에서 사용자가 보는 인터페이스가 바뀌었어.`;
}

async function changedFiles(sha: string): Promise<readonly string[]> {
  const { stdout } = await execFileAsync(
    "git",
    ["diff-tree", "--no-commit-id", "--name-only", "-r", sha, "--", "apps/web/src"],
    { encoding: "utf8" },
  );
  return stdout.split(/\r?\n/u).filter(Boolean);
}

export async function scanCandidates(): Promise<readonly PromotionCandidate[]> {
  const { stdout } = await execFileAsync(
    "git",
    ["log", `--since=${lookbackHours()} hours ago`, "--format=%H%x09%s", "--", "apps/web/src"],
    { encoding: "utf8" },
  );
  const candidates: PromotionCandidate[] = [];
  for (const line of stdout.split(/\r?\n/u).filter(Boolean)) {
    const separator = line.indexOf("\t");
    if (separator < 0) continue;
    const sha = line.slice(0, separator);
    const subject = line.slice(separator + 1);
    if (!FEAT_COMMIT.test(subject)) continue;
    const files = await changedFiles(sha);
    if (files.length === 0) continue;
    const route = inferRoute(files);
    candidates.push({
      sha,
      changedFiles: files,
      route,
      summary: buildReadableSummary(files, route),
    });
  }
  return candidates;
}

export async function run(): Promise<void> {
  const token = await authenticatePromotionAgent();
  const [candidates, existingShas] = await Promise.all([
    scanCandidates(),
    fetchExistingCommitShas(token),
  ]);
  const pending = candidates.filter(({ sha }) => !existingShas.has(sha));
  console.log(`[scan] 후보 ${candidates.length}개, 신규 ${pending.length}개`);

  const outputRoot = path.resolve(
    process.env.PROMOTION_AGENT_OUTPUT_DIR ?? path.join(process.cwd(), "promotion-agent-output"),
  );
  await mkdir(outputRoot, { recursive: true });
  for (const candidate of pending) {
    console.log(`[agent] ${candidate.sha.slice(0, 12)} · ${candidate.route} 처리 시작`);
    const capture = await captureCandidate(candidate, outputRoot);
    const caption = await generateCaption(capture.paths, candidate.summary);
    const post = await publishDraft(token, candidate.sha, caption, capture.paths);
    console.log(
      `[agent] ${candidate.sha.slice(0, 12)} · ${post.id} ${post.status} · ${capture.paths.length}장${capture.fallback ? "(정적 폴백)" : ""}`,
    );
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
