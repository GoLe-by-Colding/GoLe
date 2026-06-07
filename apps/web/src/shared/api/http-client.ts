import { env } from "@shared/config";

export interface ApiErrorBody {
  readonly code: string;
  readonly message: string;
}

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;

  constructor(status: number, body: ApiErrorBody) {
    super(body.message);
    this.name = "ApiError";
    this.status = status;
    this.code = body.code;
  }
}

export interface RequestOptions {
  readonly method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  readonly body?: unknown;
  readonly signal?: AbortSignal;
  readonly headers?: Readonly<Record<string, string>>;
}

/**
 * 백엔드(Spring Boot)와 통신하는 얇은 타입 안전 클라이언트.
 * 응답 본문은 호출부가 제네릭으로 형태를 명시한다(암묵적 any 금지).
 */
export async function apiRequest<TResponse>(
  path: string,
  options: RequestOptions = {},
): Promise<TResponse> {
  const { method = "GET", body, signal, headers } = options;

  const response = await fetch(`${env.apiBaseUrl}${path}`, {
    method,
    signal: signal ?? null,
    headers: {
      "Content-Type": "application/json",
      ...headers,
    },
    body: body === undefined ? null : JSON.stringify(body),
  });

  if (!response.ok) {
    const fallback: ApiErrorBody = {
      code: "UNKNOWN",
      message: `Request failed with status ${response.status}`,
    };
    const parsed = (await response
      .json()
      .catch(() => fallback)) as ApiErrorBody;
    throw new ApiError(response.status, parsed);
  }

  if (response.status === 204) {
    return undefined as TResponse;
  }

  return (await response.json()) as TResponse;
}
