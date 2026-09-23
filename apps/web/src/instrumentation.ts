import * as Sentry from "@sentry/nextjs";
import { sentryOptions } from "../sentry.options";

export function register() {
  Sentry.init(
    sentryOptions(
      process.env.SENTRY_ENABLED,
      process.env.SENTRY_ENVIRONMENT,
      process.env.SENTRY_DSN,
    ),
  );
}

/** Next 서버 오류의 원문 요청·컨텍스트는 수집하지 않는다. */
export function onRequestError() {
  Sentry.captureMessage("UNEXPECTED_ERROR", "error");
}
