export interface ApiErrorBody {
  readonly code: string;
  readonly message: string;
}

/**
 * 백엔드가 내려준 `{code, message}` 오류.
 *
 * <b>http-client 가 아니라 여기 둔다.</b> 오류를 판정하는 쪽(온보딩 가드 등)이 이 클래스를
 * 필요로 하는데, http-client 에 두면 그쪽이 http-client 를 부르고 http-client 는 다시 판정
 * 모듈을 불러 순환 import 가 된다. Metro 는 순환을 허용하지만 "초기화되지 않은 값"을 만들 수 있다.
 */
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
