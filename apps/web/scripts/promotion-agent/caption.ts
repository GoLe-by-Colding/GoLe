import { readFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const ANTHROPIC_MESSAGES_URL = "https://api.anthropic.com/v1/messages";
const DEFAULT_MODEL = "claude-sonnet-4-6";
export const MAX_VISIBLE_CAPTION_LENGTH = 450;

interface AnthropicResponse {
  readonly content?: readonly { readonly type?: string; readonly text?: string }[];
  readonly error?: { readonly message?: string };
}

function requiredEnvironment(name: string): string {
  const value = process.env[name]?.trim();
  if (!value) throw new Error(`${name} 환경변수가 필요함`);
  return value;
}

function normalizeCaption(value: string): string {
  const trimmed = value
    .trim()
    .replace(/^(["'])|(["'])$/g, "")
    .trim();
  if (trimmed.length === 0) throw new Error("LLM이 빈 캡션을 반환함");
  if (trimmed.length > MAX_VISIBLE_CAPTION_LENGTH) {
    throw new Error(`LLM 캡션이 ${MAX_VISIBLE_CAPTION_LENGTH}자를 초과함`);
  }
  return trimmed;
}

export async function generateCaption(
  screenshotPaths: readonly string[],
  humanReadableSummary: string,
): Promise<string> {
  if (screenshotPaths.length === 0) throw new Error("캡션 생성에는 스크린샷이 한 장 이상 필요함");

  const promptPath = path.join(
    path.dirname(fileURLToPath(import.meta.url)),
    "prompts",
    "caption-tone.md",
  );
  const systemPrompt = await readFile(promptPath, "utf8");
  const images = await Promise.all(
    screenshotPaths.map(async (screenshotPath) => ({
      type: "image" as const,
      source: {
        type: "base64" as const,
        media_type: "image/png" as const,
        data: (await readFile(screenshotPath)).toString("base64"),
      },
    })),
  );

  const response = await fetch(ANTHROPIC_MESSAGES_URL, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      "x-api-key": requiredEnvironment("ANTHROPIC_API_KEY"),
      "anthropic-version": "2023-06-01",
    },
    body: JSON.stringify({
      model: process.env.ANTHROPIC_MODEL?.trim() || DEFAULT_MODEL,
      max_tokens: 512,
      system: systemPrompt,
      messages: [
        {
          role: "user",
          content: [
            ...images,
            {
              type: "text",
              text: `다음 변경 요약과 시간순 스크린샷만 근거로 Threads 캡션 본문만 작성해. 따옴표나 설명은 붙이지 마.\n\n변경 요약: ${humanReadableSummary}`,
            },
          ],
        },
      ],
    }),
  });
  const payload = (await response.json().catch(() => ({}))) as AnthropicResponse;
  if (!response.ok) {
    throw new Error(
      `Anthropic Messages API 실패(${response.status}): ${payload.error?.message ?? "응답 본문 없음"}`,
    );
  }
  const caption = payload.content?.find((block) => block.type === "text")?.text;
  if (caption === undefined) throw new Error("Anthropic 응답에 텍스트 캡션이 없음");
  return normalizeCaption(caption);
}

export { normalizeCaption };
