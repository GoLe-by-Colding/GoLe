import type { BrowserOptions } from "@sentry/nextjs";
import { createSentryPolicy } from "@gole/core/operations";

/** 환경·사용자·요청을 추론하지 않고 배포의 명시적 선택만 허용한다. */
export function sentryOptions(
  enabled: string | undefined,
  environment: string | undefined,
  dsn: string | undefined,
) {
  let validDsn = false;
  try {
    const url = new URL(dsn ?? "");
    validDsn =
      url.protocol === "https:" &&
      url.hostname.endsWith(".sentry.io") &&
      /^[a-zA-Z0-9]{1,64}$/.test(url.username) &&
      url.password === "" &&
      /^\/[0-9]+$/.test(url.pathname) &&
      url.search === "" &&
      url.hash === "";
  } catch {
    /* 잘못된 DSN 원문을 SDK의 진단 로그에 넘기지 않는다. */
  }
  const active = enabled === "true" && environment === "production" && validDsn;
  const policy = createSentryPolicy({ enabled: active });
  return {
    enabled: active,
    dsn: active ? dsn : undefined,
    environment: "production",
    defaultIntegrations: false,
    sendDefaultPii: false,
    transportOptions: { fetchOptions: { referrerPolicy: "no-referrer", credentials: "omit" } },
    sendClientReports: false,
    enableLogs: false,
    tracesSampleRate: 0,
    replaysSessionSampleRate: 0,
    replaysOnErrorSampleRate: 0,
    beforeSend(event, hint) {
      hint.attachments = [];
      const safe = policy({
        level: event.level ?? "error",
        environment: event.environment ?? "",
        tags: { component: "web" },
      });
      return safe ? { ...safe, type: undefined } : null;
    },
    beforeSendTransaction: () => null,
    beforeBreadcrumb: () => null,
  } satisfies BrowserOptions;
}
