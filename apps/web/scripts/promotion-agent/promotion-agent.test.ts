import assert from "node:assert/strict";
import { mkdtemp, writeFile } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import { findCaptureScenario } from "./capture";
import {
  captionWithCommitMarker,
  encodeCommitMarker,
  extractCommitMarker,
  publishDraft,
  type PublishDependencies,
} from "./publish-draft";
import { buildReadableSummary, inferRoute } from "./scan";

const SHA = "0123456789abcdef0123456789abcdef01234567";

test("커밋 SHA는 보이지 않는 지문으로 캡션에 저장하고 복원한다", () => {
  const marker = encodeCommitMarker(SHA);
  assert.equal(marker.includes(SHA), false);
  assert.equal(extractCommitMarker(`새 기능을 붙였어.${marker}`), SHA);
  assert.equal(captionWithCommitMarker("새 기능을 붙였어.", SHA).length <= 500, true);
});

test("변경 파일에서 직접 라우트와 FSD 힌트 라우트를 고른다", () => {
  assert.equal(inferRoute(["apps/web/src/app/(main)/prices/page.tsx"]), "/prices");
  assert.equal(inferRoute(["apps/web/src/views/community/ui/community-page.tsx"]), "/community");
  assert.match(
    buildReadableSummary(["apps/web/src/features/create-listing/ui/form.tsx"], "/search"),
    /상품 등록/u,
  );
});

test("기존 E2E 기반 안전 시나리오가 없으면 정적 폴백 대상으로 남긴다", () => {
  assert.equal(findCaptureScenario("/prices")?.sourceSpec, "tests-e2e/prices.spec.ts");
  assert.equal(findCaptureScenario("/profile"), undefined);
});

test("초안 생성 직후 같은 호출에서 제출하고 발행은 호출하지 않는다", async () => {
  const outputDir = await mkdtemp(path.join(os.tmpdir(), "promotion-agent-"));
  const screenshotPath = path.join(outputDir, "capture.png");
  await writeFile(screenshotPath, new Uint8Array([137, 80, 78, 71]));
  const calls: string[] = [];
  const dependencies: PublishDependencies = {
    upload: async () => {
      calls.push("upload");
      return [{ key: "images/capture.png", url: "/media/capture.png" }];
    },
    create: async (_token, input) => {
      calls.push(`create:${extractCommitMarker(input.caption)}`);
      return { id: "draft-1" };
    },
    submit: async (_token, id) => {
      calls.push(`submit:${id}`);
      return {
        id,
        channel: "THREADS",
        caption: "caption",
        mediaUrls: [],
        authorId: "bot",
        status: "PENDING_REVIEW",
        createdAt: null,
        submittedAt: "2026-09-10T00:00:00Z",
        reviewerId: null,
        reviewedAt: null,
        rejectionReason: null,
        publishedAt: null,
        externalPostId: null,
      };
    },
  };

  const result = await publishDraft(
    "token",
    SHA,
    "새 기능을 붙였어.",
    [screenshotPath],
    dependencies,
  );
  assert.equal(result.status, "PENDING_REVIEW");
  assert.deepEqual(calls, ["upload", `create:${SHA}`, "submit:draft-1"]);
});
