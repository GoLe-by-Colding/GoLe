import { env } from "@shared/config";
import { readSessionAuthorization } from "./session-auth";

export interface ApiErrorBody {
  readonly code: string;
  readonly message: string;
}

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly retryAfterMs: number | null;

  constructor(status: number, body: ApiErrorBody, retryAfterMs: number | null = null) {
    super(body.message);
    this.name = "ApiError";
    this.status = status;
    this.code = body.code;
    this.retryAfterMs = retryAfterMs;
  }
}

export interface RequestOptions {
  readonly method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  readonly body?: unknown;
  readonly signal?: AbortSignal;
  readonly headers?: Readonly<Record<string, string>>;
  readonly cache?: RequestCache;
  /**
   * Next.js fetch 확장. 서버 컴포넌트에서 ISR 캐시를 쓸 때 지정한다.
   * 색인 대상 페이지(세트 상세 등)가 크롤러 요청마다 백엔드를 때리지 않게 하는 용도다.
   */
  readonly next?: { readonly revalidate?: number | false; readonly tags?: readonly string[] };
}

/**
 * 백엔드(Spring Boot)와 통신하는 얇은 타입 안전 클라이언트.
 * 응답 본문은 호출부가 제네릭으로 형태를 명시한다(암묵적 any 금지).
 */
export async function apiRequest<TResponse>(
  path: string,
  options: RequestOptions = {},
): Promise<TResponse> {
  const { method = "GET", body, signal, headers, cache, next } = options;
  const sessionHeader = readSessionAuthorization();

  const response = await fetch(`${env.apiBaseUrl}${path}`, {
    method,
    credentials: "include",
    signal: signal ?? null,
    ...(cache === undefined ? {} : { cache }),
    ...(next === undefined
      ? {}
      : {
          next: {
            ...(next.revalidate === undefined ? {} : { revalidate: next.revalidate }),
            ...(next.tags === undefined ? {} : { tags: [...next.tags] }),
          },
        }),
    headers: {
      "Content-Type": "application/json",
      ...sessionHeader,
      ...headers,
    },
    body: body === undefined ? null : JSON.stringify(body),
  });

  if (!response.ok) {
    const fallback: ApiErrorBody = {
      code: "UNKNOWN",
      message: `Request failed with status ${response.status}`,
    };
    const parsed = (await response.json().catch(() => fallback)) as ApiErrorBody;
    throw new ApiError(
      response.status,
      parsed,
      parseRetryAfter(response.headers.get("Retry-After")),
    );
  }

  if (response.status === 204) {
    return undefined as TResponse;
  }

  return (await response.json()) as TResponse;
}

function parseRetryAfter(value: string | null): number | null {
  if (value === null) return null;
  const seconds = Number(value);
  if (Number.isFinite(seconds) && seconds >= 0) return Math.ceil(seconds * 1_000);

  const retryAt = Date.parse(value);
  if (Number.isNaN(retryAt)) return null;
  return Math.max(0, retryAt - Date.now());
}
