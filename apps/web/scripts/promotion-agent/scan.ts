import { execFile } from "node:child_process";
import { promisify } from "node:util";
import type { PromotionCandidate } from "./types";

const execFileAsync = promisify(execFile);
const FEATURE_COMMIT = /^feat(?:\([^)]*\))?!?:\s/u;
const GIT_SHA = /^[0-9a-f]{40}$/u;
const DEFAULT_LOOKBACK_HOURS = 27;

export function promotionLookbackHours(): number {
  const value = Number(
    process.env.PROMOTION_AGENT_LOOKBACK_HOURS?.trim() || DEFAULT_LOOKBACK_HOURS,
  );
  if (!Number.isInteger(value) || value < 24 || value > 27) {
    throw new Error("PROMOTION_AGENT_LOOKBACK_HOURS는 24~27 사이의 정수여야 함");
  }
  return value;
}

export function assertGitSha(sha: string): void {
  if (!GIT_SHA.test(sha)) throw new Error(`올바르지 않은 git SHA: ${sha}`);
}

export async function readFrontendCommitDiff(sha: string): Promise<string> {
  assertGitSha(sha);
  const { stdout } = await execFileAsync(
    "git",
    ["diff-tree", "--no-commit-id", "--patch", "--stat", "-r", sha, "--", "apps/web/src"],
    { encoding: "utf8", maxBuffer: 2 * 1024 * 1024 },
  );
  if (stdout.trim().length === 0) throw new Error(`${sha}에 apps/web/src 변경이 없음`);
  return stdout;
}

export async function listRecentFrontendCommits(): Promise<readonly PromotionCandidate[]> {
  const { stdout } = await execFileAsync(
    "git",
    [
      "log",
      `--since=${promotionLookbackHours()} hours ago`,
      "--format=%H%x09%s",
      "--",
      "apps/web/src",
    ],
    { encoding: "utf8" },
  );

  return stdout
    .split(/\r?\n/u)
    .filter(Boolean)
    .flatMap((line) => {
      const separator = line.indexOf("\t");
      if (separator < 0) return [];
      const sha = line.slice(0, separator);
      const subject = line.slice(separator + 1);
      return GIT_SHA.test(sha) && FEATURE_COMMIT.test(subject) ? [{ sha, subject }] : [];
    });
}
