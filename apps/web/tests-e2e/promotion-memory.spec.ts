import { execFile } from "node:child_process";
import { randomUUID } from "node:crypto";
import { existsSync, readFileSync } from "node:fs";
import { join, resolve } from "node:path";
import { promisify } from "node:util";
import { expect, request, test, type APIRequestContext } from "@playwright/test";
import type {
  AdminPromotionFeedback,
  AdminPromotionGuideline,
  AdminPromotionPost,
  AdminPromotionRun,
  PromotionRunMemoryContext,
} from "@gole/core/admin";

const execute = promisify(execFile);
const AGENT_DIR = resolve(import.meta.dirname, "../../support-agent");
const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";
const BOT_EMAIL = process.env.PROMOTION_AGENT_ADMIN_EMAIL ?? "e2e-promotion-bot@gole.test";
const BOT_PASSWORD =
  process.env.PROMOTION_AGENT_ADMIN_PASSWORD ?? "e2e-promotion-bot-not-a-real-secret";

function assertLocalOrigin(value: string) {
  const url = new URL(value);
  expect(url.protocol, "쓰기 테스트는 로컬 HTTP에서만 실행합니다").toBe("http:");
  expect(
    ["localhost", "127.0.0.1", "[::1]"],
    "운영 API/화면에 테스트 데이터를 만들 수 없습니다",
  ).toContain(url.hostname);
  expect(url.username + url.password + url.search + url.hash).toBe("");
  expect(url.pathname).toBe("/");
}

function pythonPath(): string {
  if (process.env.E2E_PROMOTION_PYTHON) return process.env.E2E_PROMOTION_PYTHON;
  const executable = [
    join(AGENT_DIR, ".venv", "Scripts", "python.exe"),
    join(AGENT_DIR, ".venv", "bin", "python"),
  ].find(existsSync);
  if (!executable)
    throw new Error(
      "apps/support-agent에서 uv sync --locked --extra promotion으로 E2E Python 환경을 준비하세요.",
    );
  return executable;
}

interface RunnerOutput {
  postId: string;
  runKey: string;
  outcome: string;
  reasonCode: string;
  requestsFile: string;
  feedbackIds: string[];
  memoryContext: PromotionRunMemoryContext;
}

interface ModelRequest {
  engine: string;
  system?: string;
  prompt: string;
  json_schema?: { properties?: { proposals?: unknown } };
}

async function list<T>(api: APIRequestContext, path: string): Promise<T[]> {
  const response = await api.get(`${API_BASE}${path}`);
  expect(response.ok(), `목록 조회 실패: ${path}`).toBe(true);
  return response.json() as Promise<T[]>;
}

test.describe("홍보 메모리 실제 백엔드 왕복", () => {
  test.describe.configure({ mode: "serial" });
  test.skip(
    () => process.env.E2E_PROMOTION_MEMORY !== "1",
    "E2E_PROMOTION_MEMORY=1에서 실제 백엔드·고정 gateway 검증 실행",
  );
  test.setTimeout(240_000);

  test("반려 보존 → 지침 제안 → 사람 확정 → 두 모델 반영 → 해제 후 제외", async ({
    page,
    baseURL,
  }, testInfo) => {
    assertLocalOrigin(API_BASE);
    assertLocalOrigin(baseURL ?? "");
    const adminEmail = process.env.GOLE_ADMIN_EMAIL ?? "e2e-admin@gole.test";
    const adminPassword = process.env.GOLE_ADMIN_PASSWORD ?? "e2e-admin-not-a-real-secret";
    expect(adminEmail.endsWith("@gole.test") && BOT_EMAIL.endsWith("@gole.test")).toBe(true);

    const login = await page.request.post(`${API_BASE}/api/v1/accounts/sessions`, {
      data: { email: adminEmail, password: adminPassword },
    });
    expect(login.ok()).toBe(true);
    const admin = await login.json();
    expect(admin.role).toBe("ADMIN");
    expect(admin.accountId).toMatch(/^e2e-/);
    await page.addInitScript(
      (session) => window.localStorage.setItem("gole.session", JSON.stringify(session)),
      { accountId: admin.accountId, role: admin.role, sessionToken: "" },
    );

    const botApi = await request.newContext({ baseURL: API_BASE });
    const botLogin = await botApi.post("/api/v1/accounts/sessions", {
      data: { email: BOT_EMAIL, password: BOT_PASSWORD },
    });
    expect(botLogin.ok()).toBe(true);
    const bot = await botLogin.json();
    expect(bot.role).toBe("ADMIN");
    expect(bot.accountId).not.toBe(admin.accountId);

    const runPrefix = `e2e-memory-${randomUUID()}`;
    const createdPosts: string[] = [];
    let guidelineId: string | undefined;
    const approvedContent = `화면 글자를 작게 줄이지 말고 읽을 수 있는 크기를 유지한다. ${runPrefix}`;
    const reason = `화면 글자가 너무 작아 읽기 어렵습니다. ${runPrefix}`;

    async function run(phase: "propose" | "apply", name: string): Promise<RunnerOutput> {
      const runDir = testInfo.outputPath(name);
      const output = join(runDir, "result.json");
      await execute(
        pythonPath(),
        [
          join(AGENT_DIR, "tests", "promotion", "run_memory_e2e.py"),
          "--api-url",
          API_BASE,
          "--run-dir",
          runDir,
          "--phase",
          phase,
          "--run-key",
          `${runPrefix}-${name}`,
          "--output",
          output,
        ],
        {
          cwd: AGENT_DIR,
          env: {
            ...process.env,
            PROMOTION_AGENT_ADMIN_EMAIL: BOT_EMAIL,
            PROMOTION_AGENT_ADMIN_PASSWORD: BOT_PASSWORD,
          },
          timeout: 60_000,
          maxBuffer: 1024 * 1024,
        },
      );
      const result = JSON.parse(readFileSync(output, "utf8")) as RunnerOutput;
      expect(result.outcome, result.reasonCode).toBe("submitted");
      createdPosts.push(result.postId);
      return result;
    }

    async function approveFixture(postId: string) {
      const pending = await list<AdminPromotionPost>(
        page.request,
        "/api/admin/promotion-posts?status=PENDING_REVIEW&limit=100",
      );
      if (pending.some((post) => post.id === postId && post.authorId === bot.accountId)) {
        expect(
          (await page.request.post(`${API_BASE}/api/admin/promotion-posts/${postId}/approve`)).ok(),
        ).toBe(true);
      }
    }

    try {
      // 재실행 시 전용 봇의 고정 fixture만 정리한다. 사람의 초안·발행에는 손대지 않는다.
      const pending = await list<AdminPromotionPost>(
        page.request,
        "/api/admin/promotion-posts?status=PENDING_REVIEW&limit=100",
      );
      for (const post of pending.filter(
        (post) =>
          post.authorId === bot.accountId &&
          post.caption === "갖고 있는 레고 세트를 한곳에 모아 보세요.",
      ))
        await approveFixture(post.id);
      const prior = await list<AdminPromotionGuideline>(
        page.request,
        "/api/admin/promotion-guidelines?limit=100",
      );
      for (const guideline of prior.filter(
        (entry) =>
          entry.proposedBy === bot.accountId && entry.reflectionRunKey.startsWith("e2e-memory-"),
      )) {
        if (guideline.status === "ACTIVE" || guideline.status === "PROPOSED")
          expect(
            (
              await page.request.post(
                `${API_BASE}/api/admin/promotion-guidelines/${guideline.id}/${guideline.status === "ACTIVE" ? "retire" : "dismiss"}`,
              )
            ).ok(),
          ).toBe(true);
      }

      const first = await run("apply", "first");
      await page.goto("/admin/promotion");
      const postRow = page
        .getByRole("row")
        .filter({ hasText: "갖고 있는 레고 세트를 한곳에 모아 보세요." });
      await postRow.getByRole("button", { name: "반려", exact: true }).click();
      const rejection = page.getByRole("dialog", { name: "홍보 게시 반려" });
      await rejection.getByLabel("조치 사유").fill(reason);
      await rejection.getByRole("button", { name: "반려하기", exact: true }).click();
      await expect(rejection).toHaveCount(0);

      const [feedback] = await list<AdminPromotionFeedback>(
        page.request,
        `/api/admin/promotion-feedback?postId=${first.postId}&limit=50`,
      );
      expect(feedback?.reason).toBe(reason);
      expect(feedback?.snapshot.captures[0]?.originalUrl).toBeTruthy();
      if (!feedback) throw new Error("반려 snapshot이 저장되지 않았습니다.");
      expect((await botApi.post(`/api/admin/promotion-posts/${first.postId}/submit`)).ok()).toBe(
        true,
      );
      expect(
        await list<AdminPromotionFeedback>(
          page.request,
          `/api/admin/promotion-feedback?postId=${first.postId}&limit=50`,
        ),
      ).toEqual([feedback]);
      await approveFixture(first.postId);

      const reflected = await run("propose", "proposed");
      const proposed = (
        await list<AdminPromotionGuideline>(
          page.request,
          "/api/admin/promotion-guidelines?status=PROPOSED&limit=100",
        )
      ).find(
        (entry) =>
          entry.reflectionRunKey === reflected.runKey &&
          entry.sourceFeedbackIds.includes(feedback.id),
      );
      expect(proposed).toBeTruthy();
      if (!proposed) throw new Error("이번 반려에서 나온 지침 제안이 없습니다.");
      guidelineId = proposed.id;
      expect(proposed.proposedBy).toBe(bot.accountId);
      expect(reflected.memoryContext.guidelines.some((entry) => entry.id === guidelineId)).toBe(
        false,
      );
      const beforeApproval = JSON.parse(
        readFileSync(reflected.requestsFile, "utf8"),
      ) as ModelRequest[];
      expect(
        beforeApproval
          .filter((entry) => entry.engine === "codex")
          .every((entry) => !entry.prompt.includes(proposed.content)),
      ).toBe(true);
      expect(
        (
          await botApi.post(`/api/admin/promotion-guidelines/${guidelineId}/activate`, {
            data: { expectedVersion: proposed.version },
          })
        ).status(),
      ).toBe(403);
      await approveFixture(reflected.postId);

      await page.reload();
      const proposalCard = page.getByRole("group", {
        name: `홍보 지침 ${guidelineId}`,
        exact: true,
      });
      const editor = proposalCard.getByRole("textbox", { name: "지침 내용", exact: true });
      await expect(editor).toHaveValue(proposed.content);
      const competingContent = `다른 관리자의 최신 검토 문구. ${runPrefix}`;
      const competingInput = {
        content: competingContent,
        targets: proposed.targets,
        categories: proposed.categories,
      };
      const concurrentEdit = await botApi.patch(`/api/admin/promotion-guidelines/${guidelineId}`, {
        data: { ...competingInput, expectedVersion: proposed.version },
      });
      expect(concurrentEdit.ok()).toBe(true);
      const changed = (await concurrentEdit.json()) as AdminPromotionGuideline;
      expect(changed.version).toBe(proposed.version + 1);
      await editor.fill("오래된 내용으로 확정하려는 검토");
      const staleSave = page.waitForResponse(
        (response) =>
          response.url().endsWith(`/promotion-guidelines/${guidelineId}`) &&
          response.request().method() === "PATCH",
      );
      await proposalCard.getByRole("button", { name: "수정 후 확정", exact: true }).click();
      expect((await staleSave).status()).toBe(409);
      await expect(editor).toHaveValue(competingContent);
      await expect(
        page.getByText("다른 관리자가 지침을 변경했습니다. 최신 내용을 다시 검토해 주세요."),
      ).toBeVisible();

      const saved = await page.request.patch(
        `${API_BASE}/api/admin/promotion-guidelines/${guidelineId}`,
        { data: { ...competingInput, content: approvedContent, expectedVersion: changed.version } },
      );
      expect(saved.ok()).toBe(true);
      const reviewed = (await saved.json()) as AdminPromotionGuideline;
      const betweenSaveAndApproval = await botApi.patch(
        `/api/admin/promotion-guidelines/${guidelineId}`,
        { data: { ...competingInput, expectedVersion: reviewed.version } },
      );
      expect(betweenSaveAndApproval.ok()).toBe(true);
      const latest = (await betweenSaveAndApproval.json()) as AdminPromotionGuideline;
      const staleActivation = await page.request.post(
        `${API_BASE}/api/admin/promotion-guidelines/${guidelineId}/activate`,
        { data: { expectedVersion: reviewed.version } },
      );
      expect(staleActivation.status()).toBe(409);
      expect(await staleActivation.json()).toMatchObject({
        code: "PROMOTION_GUIDELINE_VERSION_CONFLICT",
      });
      const afterConflict = (
        await list<AdminPromotionGuideline>(
          page.request,
          "/api/admin/promotion-guidelines?limit=100",
        )
      ).find((entry) => entry.id === guidelineId);
      expect(afterConflict).toMatchObject({
        status: "PROPOSED",
        content: competingContent,
        version: latest.version,
        confirmedBy: null,
      });
      await page.reload();
      await expect(editor).toHaveValue(competingContent);
      await editor.fill(approvedContent);
      await proposalCard.getByRole("button", { name: "수정 후 확정", exact: true }).click();
      await expect(page.getByText("지침을 확정했습니다. 다음 실행부터 적용됩니다.")).toBeVisible();
      const active = (
        await list<AdminPromotionGuideline>(
          page.request,
          "/api/admin/promotion-guidelines?status=ACTIVE&limit=100",
        )
      ).find((entry) => entry.id === guidelineId);
      expect(active).toMatchObject({
        proposedBy: bot.accountId,
        confirmedBy: admin.accountId,
        content: approvedContent,
      });

      const applied = await run("apply", "applied");
      const appliedRequests = JSON.parse(
        readFileSync(applied.requestsFile, "utf8"),
      ) as ModelRequest[];
      const choose = appliedRequests.find(
        (entry) => entry.engine === "claude" && !entry.json_schema?.properties?.proposals,
      );
      const polish = appliedRequests.find((entry) => entry.engine === "codex");
      expect(choose?.system).toContain(approvedContent);
      expect(polish?.prompt).toContain(approvedContent);
      expect(applied.memoryContext.guidelines).toEqual(
        expect.arrayContaining([
          expect.objectContaining({ id: guidelineId, content: approvedContent }),
        ]),
      );
      await approveFixture(applied.postId);
      await page.reload();
      const activeCard = page.getByRole("group", { name: `홍보 지침 ${guidelineId}`, exact: true });
      await activeCard.getByRole("button", { name: "활성 해제", exact: true }).click();
      await expect(
        page.getByText("지침을 해제했습니다. 다음 실행부터 적용되지 않습니다."),
      ).toBeVisible();

      const retired = await run("apply", "retired");
      expect(retired.memoryContext.guidelines.some((entry) => entry.id === guidelineId)).toBe(
        false,
      );
      const retiredRequests = JSON.parse(
        readFileSync(retired.requestsFile, "utf8"),
      ) as ModelRequest[];
      expect(
        retiredRequests.every(
          (entry) => !`${entry.system ?? ""}\n${entry.prompt}`.includes(approvedContent),
        ),
      ).toBe(true);
      const recorded = (
        await list<AdminPromotionRun>(page.request, "/api/admin/promotion-runs?limit=100")
      ).find((entry) => entry.runKey === applied.runKey);
      expect(recorded?.memoryContext).toEqual(applied.memoryContext);
      await page.reload();
      await page.getByText(/^실행에 사용한 기억 · 최근/).click();
      await expect(
        page.getByText(`[글 작성 · 화면 선택 · 이미지 다듬기] ${approvedContent}`, { exact: true }),
      ).toBeVisible();
    } finally {
      for (const postId of createdPosts) await approveFixture(postId);
      if (guidelineId) {
        const guideline = (
          await list<AdminPromotionGuideline>(
            page.request,
            "/api/admin/promotion-guidelines?limit=100",
          )
        ).find((entry) => entry.id === guidelineId);
        if (guideline?.status === "ACTIVE")
          await page.request.post(
            `${API_BASE}/api/admin/promotion-guidelines/${guidelineId}/retire`,
          );
      }
      await botApi.dispose();
    }
  });
});
