import assert from "node:assert/strict";
import { mkdtemp, writeFile } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import { PromotionBrowserSession } from "./capture";
import { createPromotionDraftSubmitter, type PublishDependencies } from "./publish-draft";
import { isPublicCaptureRoute, listPublicRoutes } from "./tools/routes";

const SHA = "0123456789abcdef0123456789abcdef01234567";
const SECOND_SHA = "89abcdef0123456789abcdef0123456789abcdef";

function submittedPost(sha: string) {
  return {
    id: "draft-1",
    channel: "THREADS" as const,
    caption: "새 기능을 붙였어.",
    mediaUrls: [] as const,
    authorId: "bot",
    sourceCommitSha: sha,
    status: "PENDING_REVIEW" as const,
    createdAt: null,
    submittedAt: "2026-09-10T00:00:00Z",
    reviewerId: null,
    reviewedAt: null,
    rejectionReason: null,
    publishedAt: null,
    externalPostId: null,
  };
}

async function screenshotFixture(): Promise<string> {
  const outputDir = await mkdtemp(path.join(os.tmpdir(), "promotion-agent-"));
  const screenshotPath = path.join(outputDir, "capture.png");
  await writeFile(screenshotPath, new Uint8Array([137, 80, 78, 71]));
  return screenshotPath;
}

function dependencies(options: {
  readonly exists?: boolean;
  readonly calls: string[];
}): PublishDependencies {
  return {
    exists: async (_token, sha) => {
      options.calls.push(`exists:${sha}`);
      return { exists: options.exists ?? false };
    },
    upload: async () => {
      options.calls.push("upload");
      return [{ key: "images/capture.png", url: "/media/capture.png" }];
    },
    create: async (_token, input) => {
      options.calls.push(`create:${input.sourceCommitSha}`);
      return { id: "draft-1" };
    },
    submit: async (_token, id) => {
      options.calls.push(`submit:${id}`);
      return submittedPost(SHA);
    },
  };
}

test("브라우저는 허용목록 밖과 로그인·결제·관리자 라우트를 거부한다", async () => {
  assert.equal(isPublicCaptureRoute("/prices"), true);
  assert.equal(isPublicCaptureRoute("/login"), false);
  assert.equal(isPublicCaptureRoute("/payments/portone/return"), false);
  assert.equal(isPublicCaptureRoute("/admin/promotion"), false);
  const routes = await listPublicRoutes();
  assert.equal(routes.includes("/prices"), true);
  assert.equal(
    routes.some((route) => route.includes("[")),
    false,
  );

  const browser = new PromotionBrowserSession(new Set(["/prices"]), os.tmpdir());
  await assert.rejects(browser.goto("/profile"), /허용되지 않은 공개 라우트/u);
});

test("제출 툴은 실행당 초안 수 한도를 넘으면 원격 호출 전에 거부한다", async () => {
  const calls: string[] = [];
  const submit = createPromotionDraftSubmitter("secret-token", 1, dependencies({ calls }));
  const screenshotPath = await screenshotFixture();

  await submit({ sha: SHA, caption: "첫 초안이야.", screenshotPaths: [screenshotPath] });
  await assert.rejects(
    submit({ sha: SECOND_SHA, caption: "두 번째 초안이야.", screenshotPaths: [screenshotPath] }),
    /한도\(1건\)를 초과/u,
  );
  assert.deepEqual(calls, [`exists:${SHA}`, "upload", `create:${SHA}`, "submit:draft-1"]);
});

test("제출 툴은 기존 sourceCommitSha 중복을 업로드 전에 거부한다", async () => {
  const calls: string[] = [];
  const submit = createPromotionDraftSubmitter(
    "secret-token",
    3,
    dependencies({ calls, exists: true }),
  );

  await assert.rejects(
    submit({ sha: SHA, caption: "중복 초안이야.", screenshotPaths: [await screenshotFixture()] }),
    /이미 홍보 초안이 존재/u,
  );
  assert.deepEqual(calls, [`exists:${SHA}`]);
});
