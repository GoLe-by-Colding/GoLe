import * as Sentry from "@sentry/nextjs";
import { sentryOptions } from "../sentry.options";

const options = sentryOptions(
  process.env.NEXT_PUBLIC_SENTRY_ENABLED,
  process.env.NEXT_PUBLIC_SENTRY_ENVIRONMENT,
  process.env.NEXT_PUBLIC_SENTRY_DSN,
);
Sentry.init(options);
if (options.enabled) {
  // 오류 객체·rejection reason·URL을 SDK에 넘기지 않는다.
  window.addEventListener("error", () => Sentry.captureMessage("UNEXPECTED_ERROR", "error"));
  window.addEventListener("unhandledrejection", () =>
    Sentry.captureMessage("UNEXPECTED_ERROR", "error"),
  );
}
