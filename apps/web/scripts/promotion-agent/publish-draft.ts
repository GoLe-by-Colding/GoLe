import { readFile } from "node:fs/promises";
import path from "node:path";
import { configureCore, setSessionStore, uploadImages, type UploadableImage } from "@gole/core";
import {
  createAdminPromotionPost,
  promotionPostExistsForCommit,
  submitAdminPromotionPost,
  type AdminPromotionPost,
} from "@gole/core/admin";
import { signIn } from "@gole/core/user";
import { assertGitSha } from "./scan";

const DEFAULT_BASE_URL = "https://gole.co.kr";
const DEFAULT_MAX_DRAFTS = 3;
export const MAX_CAPTION_LENGTH = 450;

export interface PromotionDraftInput {
  readonly sha: string;
  readonly caption: string;
  readonly screenshotPaths: readonly string[];
}

export interface PublishDependencies {
  readonly exists: typeof promotionPostExistsForCommit;
  readonly upload: typeof uploadImages;
  readonly create: typeof createAdminPromotionPost;
  readonly submit: typeof submitAdminPromotionPost;
}

const DEFAULT_DEPENDENCIES: PublishDependencies = {
  exists: promotionPostExistsForCommit,
  upload: uploadImages,
  create: createAdminPromotionPost,
  submit: submitAdminPromotionPost,
};

function maximumDrafts(): number {
  const value = Number(process.env.PROMOTION_AGENT_MAX_DRAFTS?.trim() || DEFAULT_MAX_DRAFTS);
  if (!Number.isInteger(value) || value < 1 || value > 10) {
    throw new Error("PROMOTION_AGENT_MAX_DRAFTS는 1~10 사이의 정수여야 함");
  }
  return value;
}

export function normalizeCaption(value: string): string {
  const caption = value.trim();
  if (caption.length === 0 || caption.length > MAX_CAPTION_LENGTH) {
    throw new Error(`캡션 본문은 1~${MAX_CAPTION_LENGTH}자여야 함`);
  }
  return caption;
}

export function createPromotionDraftSubmitter(
  token: string,
  maxDrafts = maximumDrafts(),
  dependencies: PublishDependencies = DEFAULT_DEPENDENCIES,
): (input: PromotionDraftInput) => Promise<AdminPromotionPost> {
  let submittedDrafts = 0;
  const submittedShas = new Set<string>();
  let submissionQueue = Promise.resolve();

  const submit = async ({ sha, caption, screenshotPaths }: PromotionDraftInput) => {
    assertGitSha(sha);
    const normalizedCaption = normalizeCaption(caption);
    if (screenshotPaths.length === 0 || screenshotPaths.length > 10) {
      throw new Error("초안에는 스크린샷이 1~10장 필요함");
    }
    if (submittedDrafts >= maxDrafts) {
      throw new Error(`이번 실행의 초안 생성 한도(${maxDrafts}건)를 초과함`);
    }
    if (submittedShas.has(sha) || (await dependencies.exists(token, sha)).exists) {
      throw new Error(`이미 홍보 초안이 존재하는 커밋: ${sha}`);
    }

    const files: UploadableImage[] = await Promise.all(
      screenshotPaths.map(async (screenshotPath) => {
        const bytes = await readFile(screenshotPath);
        return new File([new Uint8Array(bytes)], path.basename(screenshotPath), {
          type: "image/png",
        });
      }),
    );
    const uploaded = await dependencies.upload(files);
    const created = await dependencies.create(token, {
      channel: "THREADS",
      caption: normalizedCaption,
      mediaKeys: uploaded.map(({ key }) => key),
      sourceCommitSha: sha,
    });
    const submitted = await dependencies.submit(token, created.id);
    submittedDrafts += 1;
    submittedShas.add(sha);
    return submitted;
  };

  return (input) => {
    const result = submissionQueue.then(() => submit(input));
    submissionQueue = result.then(
      () => undefined,
      () => undefined,
    );
    return result;
  };
}

async function authenticatePromotionAgent(): Promise<string> {
  configureCore({ apiBaseUrl: process.env.PROMOTION_AGENT_BASE_URL?.trim() || DEFAULT_BASE_URL });
  let sessionToken = "";
  setSessionStore({
    readAuthorizationHeader: () =>
      sessionToken.length > 0 ? { Authorization: `Bearer ${sessionToken}` } : {},
    clear: () => {
      sessionToken = "";
    },
  });
  const email = process.env.PROMOTION_AGENT_ADMIN_EMAIL?.trim();
  const password = process.env.PROMOTION_AGENT_ADMIN_PASSWORD;
  if (!email || !password) throw new Error("홍보 에이전트 관리자 계정 환경변수가 필요함");
  const session = await signIn(email, password);
  if (session.role !== "ADMIN") throw new Error("홍보 에이전트 계정에 ADMIN 권한이 없음");
  sessionToken = session.sessionToken;
  return sessionToken;
}

export function createAuthenticatedPromotionDraftSubmitter(): (
  input: PromotionDraftInput,
) => Promise<AdminPromotionPost> {
  let submitter: Promise<ReturnType<typeof createPromotionDraftSubmitter>> | undefined;
  return async (input) => {
    submitter ??= authenticatePromotionAgent().then((token) =>
      createPromotionDraftSubmitter(token),
    );
    return (await submitter)(input);
  };
}
