import { readFile } from "node:fs/promises";
import path from "node:path";
import { configureCore, setSessionStore, uploadImages, type UploadableImage } from "@gole/core";
import {
  createAdminPromotionPost,
  fetchAdminPromotionPosts,
  submitAdminPromotionPost,
  type AdminPromotionPost,
} from "@gole/core/admin";
import { signIn } from "@gole/core/user";
import { MAX_VISIBLE_CAPTION_LENGTH } from "./caption";

const DEFAULT_BASE_URL = "https://gole.co.kr";
const MARKER_PREFIX = "\u2060\u2063\u2060";
const MARKER_SUFFIX = "\u2060\u2063\u2063";
const HEX = /^[0-9a-f]{40}$/;

let sessionToken = "";

export function encodeCommitMarker(sha: string): string {
  const normalized = sha.toLowerCase();
  if (!HEX.test(normalized)) throw new Error(`올바르지 않은 git SHA: ${sha}`);
  const encoded = [...normalized]
    .map((digit) => String.fromCharCode(0xfe00 + Number.parseInt(digit, 16)))
    .join("");
  return `${MARKER_PREFIX}${encoded}${MARKER_SUFFIX}`;
}

export function extractCommitMarker(caption: string): string | null {
  const start = caption.lastIndexOf(MARKER_PREFIX);
  if (start < 0 || !caption.endsWith(MARKER_SUFFIX)) return null;
  const encoded = caption.slice(start + MARKER_PREFIX.length, -MARKER_SUFFIX.length);
  if (encoded.length !== 40) return null;
  const digits = [...encoded].map((character) => character.charCodeAt(0) - 0xfe00);
  if (digits.some((digit) => digit < 0 || digit > 15)) return null;
  return digits.map((digit) => digit.toString(16)).join("");
}

export function captionWithCommitMarker(caption: string, sha: string): string {
  const visible = caption.trim();
  if (visible.length === 0 || visible.length > MAX_VISIBLE_CAPTION_LENGTH) {
    throw new Error(`캡션 본문은 1~${MAX_VISIBLE_CAPTION_LENGTH}자여야 함`);
  }
  const result = `${visible}${encodeCommitMarker(sha)}`;
  if (result.length > 500) throw new Error("커밋 지문을 포함한 캡션이 500자를 초과함");
  return result;
}

export async function authenticatePromotionAgent(): Promise<string> {
  configureCore({ apiBaseUrl: process.env.PROMOTION_AGENT_BASE_URL ?? DEFAULT_BASE_URL });
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

export async function fetchExistingCommitShas(token: string): Promise<ReadonlySet<string>> {
  const posts = await fetchAdminPromotionPosts(token, 500);
  return new Set(
    posts
      .map((post) => extractCommitMarker(post.caption))
      .filter((sha): sha is string => sha !== null),
  );
}

export interface PublishDependencies {
  readonly upload: typeof uploadImages;
  readonly create: typeof createAdminPromotionPost;
  readonly submit: typeof submitAdminPromotionPost;
}

const DEFAULT_DEPENDENCIES: PublishDependencies = {
  upload: uploadImages,
  create: createAdminPromotionPost,
  submit: submitAdminPromotionPost,
};

export async function publishDraft(
  token: string,
  sha: string,
  caption: string,
  screenshotPaths: readonly string[],
  dependencies: PublishDependencies = DEFAULT_DEPENDENCIES,
): Promise<AdminPromotionPost> {
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
    caption: captionWithCommitMarker(caption, sha),
    mediaKeys: uploaded.map(({ key }) => key),
  });
  return dependencies.submit(token, created.id);
}
