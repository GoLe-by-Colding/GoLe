import { betaZodTool } from "@anthropic-ai/sdk/helpers/beta/zod";
import { z } from "zod";
import { PromotionBrowserSession } from "../capture";
import type { PromotionDraftInput } from "../publish-draft";
import { listRecentFrontendCommits, readFrontendCommitDiff } from "../scan";
import { listPublicRoutes } from "./routes";

interface AgentToolOptions {
  readonly browser: PromotionBrowserSession;
  readonly candidateSha: string;
  readonly submitDraft: (input: PromotionDraftInput) => Promise<unknown>;
}

export function createPromotionAgentTools({
  browser,
  candidateSha,
  submitDraft,
}: AgentToolOptions) {
  let browserQueue = Promise.resolve();
  const runBrowserOperation = <T>(operation: () => Promise<T>): Promise<T> => {
    const result = browserQueue.then(operation);
    browserQueue = result.then(
      () => undefined,
      () => undefined,
    );
    return result;
  };

  return [
    betaZodTool({
      name: "list_recent_frontend_commits",
      description: "고정된 lookback 범위에서 apps/web/src를 변경한 최근 feat 커밋을 나열한다.",
      inputSchema: z.object({}),
      run: async () => JSON.stringify(await listRecentFrontendCommits()),
    }),
    betaZodTool({
      name: "read_commit_diff",
      description: "검증된 git SHA의 apps/web/src diff만 git diff-tree로 읽는다.",
      inputSchema: z.object({ sha: z.string() }),
      run: async ({ sha }) => {
        if (sha !== candidateSha) {
          throw new Error(`현재 후보가 아닌 커밋은 읽을 수 없음: ${sha}`);
        }
        return readFrontendCommitDiff(sha);
      },
    }),
    betaZodTool({
      name: "list_routes",
      description: "현재 앱의 캡처 가능한 정적 공개 라우트를 나열한다.",
      inputSchema: z.object({}),
      run: async () => JSON.stringify(await listPublicRoutes()),
    }),
    betaZodTool({
      name: "browser_goto",
      description: "list_routes가 반환한 공개 라우트로만 브라우저를 이동한다.",
      inputSchema: z.object({ route: z.string() }),
      run: async ({ route }) => runBrowserOperation(() => browser.goto(route)),
    }),
    betaZodTool({
      name: "browser_click",
      description:
        "접근성 role과 정확한 이름으로 요소를 클릭한다. 변경 요청은 네트워크에서 차단된다.",
      inputSchema: z.object({ role: z.string().min(1), name: z.string().min(1) }),
      run: async ({ role, name }) => runBrowserOperation(() => browser.click(role, name)),
    }),
    betaZodTool({
      name: "browser_select",
      description: "정확한 접근성 label의 select 값을 선택한다. 변경 요청은 네트워크에서 차단된다.",
      inputSchema: z.object({ label: z.string().min(1), value: z.string().min(1) }),
      run: async ({ label, value }) => runBrowserOperation(() => browser.select(label, value)),
    }),
    betaZodTool({
      name: "browser_screenshot",
      description: "현재 화면을 현재 후보 세션 디렉터리에 저장하고 이미지를 확인한다.",
      inputSchema: z.object({ label: z.string().min(1).max(80) }),
      run: async ({ label }) =>
        runBrowserOperation(async () => {
          if (browser.screenshots.some((screenshot) => screenshot.label === label)) {
            throw new Error(`이미 사용한 스크린샷 라벨: ${label}`);
          }
          const screenshot = await browser.screenshot(label);
          return [
            { type: "text" as const, text: `스크린샷 ${label}: ${screenshot.path}` },
            {
              type: "image" as const,
              source: {
                type: "base64" as const,
                media_type: "image/png" as const,
                data: screenshot.base64,
              },
            },
          ];
        }),
    }),
    betaZodTool({
      name: "submit_promotion_draft",
      description: "선택한 스크린샷과 캡션으로 홍보 초안을 만들고 검토 요청 상태로 제출한다.",
      inputSchema: z.object({
        sha: z.string(),
        caption: z.string(),
        screenshotLabels: z.array(z.string().min(1)).min(1).max(10),
      }),
      run: async ({ sha, caption, screenshotLabels }) => {
        if (sha !== candidateSha) {
          throw new Error(`현재 후보 커밋과 다른 SHA는 제출할 수 없음: ${sha}`);
        }
        const screenshotPaths = screenshotLabels.map((label) => {
          const screenshot = browser.screenshots.find((item) => item.label === label);
          if (screenshot === undefined) {
            throw new Error(`현재 세션에 없는 스크린샷 라벨: ${label}`);
          }
          return screenshot.path;
        });
        const post = await submitDraft({ sha, caption, screenshotPaths });
        return JSON.stringify(post);
      },
    }),
  ];
}

export { isPublicCaptureRoute, listPublicRoutes, routeFromPageFile } from "./routes";
