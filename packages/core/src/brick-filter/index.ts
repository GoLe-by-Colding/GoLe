import { ApiError, requireConfig, getSessionStore } from "../runtime";
export type BrickMode = "MINIFIGURE" | "BRICK_OBJECT";
export interface BrickQuota {
  readonly day: string;
  readonly remaining: number;
  readonly limit: number;
  readonly enabled: boolean;
  readonly retryAvailable: boolean;
  readonly resetsAt: string;
}
export interface BrickJob {
  readonly id: string;
  readonly mode: BrickMode;
  readonly status: "RESERVED" | "SUCCEEDED" | "FAILED";
  readonly leaseUntil: string;
  readonly resultUntil: string;
}
/** 조회도 deadline을 둬 polling이 영원히 멈추지 않도록 한다. */
export const fetchBrickQuota = async () => (await (await request("/quota")).json()) as BrickQuota;
export const fetchBrickJob = async (id: string) =>
  (await (await request(`/jobs/${encodeURIComponent(id)}`)).json()) as BrickJob;
export const fetchBrickJobs = async () => (await (await request("/jobs")).json()) as BrickJob[];

async function request(path: string, init: RequestInit = {}): Promise<Response> {
  const response = await fetch(`${requireConfig().apiBaseUrl}/api/v1/brick-filter${path}`, {
    ...init,
    credentials: "include",
    cache: "no-store",
    headers: { ...getSessionStore().readAuthorizationHeader(), ...init.headers },
    signal: AbortSignal.timeout(init.method === "POST" ? 180_000 : 15_000),
  });
  if (!response.ok) {
    const parsed: unknown = await response.json().catch(() => null);
    const body = parsed !== null && typeof parsed === "object" ? parsed : {};
    throw new ApiError(response.status, {
      code: "code" in body && typeof body.code === "string" ? body.code : "BRICK_REQUEST_FAILED",
      message:
        "message" in body && typeof body.message === "string" && body.message.trim()
          ? body.message
          : "요청을 완료하지 못했습니다. 연결을 확인하고 요청 상태를 다시 확인해 주세요.",
    });
  }
  // 지연된 401로 다른 계정의 현재 세션을 지우지 않는다. 화면에서 재로그인을 안내한다.
  return response;
}

export async function createBrickJob(id: string, mode: BrickMode, file: File): Promise<BrickJob> {
  const body = new FormData();
  body.append("mode", mode);
  body.append("image", file);
  return (await (
    await request("/jobs", {
      method: "POST",
      body,
      headers: { "Idempotency-Key": id },
    })
  ).json()) as BrickJob;
}

/** 인증한 바이트만 받아 공개 이미지 최적화기에 비공개 서버 URL을 넘기지 않는다. */
export async function fetchBrickResult(id: string): Promise<Blob> {
  const response = await request(`/jobs/${encodeURIComponent(id)}/result`);
  const invalid = () =>
    new ApiError(502, {
      code: "BRICK_INVALID_RESULT",
      message: "이미지 결과를 확인하지 못했습니다. 작업을 다시 열어 주세요.",
    });
  const maximum = 8 * 1024 * 1024;
  if (
    response.headers.get("content-type")?.split(";")[0]?.trim().toLowerCase() !== "image/png" ||
    Number(response.headers.get("content-length")) > maximum ||
    !response.body
  ) {
    await response.body?.cancel();
    throw invalid();
  }
  const reader = response.body.getReader();
  const chunks: BlobPart[] = [];
  let size = 0;
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      size += value.byteLength;
      if (size > maximum) {
        await reader.cancel();
        throw invalid();
      }
      chunks.push(value.slice().buffer);
    }
  } finally {
    reader.releaseLock();
  }
  const blob = new Blob(chunks, { type: "image/png" });
  const signature = new Uint8Array(await blob.slice(0, 8).arrayBuffer());
  if (![137, 80, 78, 71, 13, 10, 26, 10].every((byte, index) => signature[index] === byte)) {
    throw invalid();
  }
  return blob;
}
