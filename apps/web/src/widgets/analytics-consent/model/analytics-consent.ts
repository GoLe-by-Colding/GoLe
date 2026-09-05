import { ANALYTICS_CONSENT_STORAGE_KEY, type AnalyticsRuntimeConfig } from "@shared/config";

export type AnalyticsConsentDecision = "granted" | "denied";

interface StoredAnalyticsConsent {
  readonly version: 1;
  readonly decision: AnalyticsConsentDecision;
  readonly updatedAt: string;
}

type AnalyticsWindow = Window & {
  dataLayer?: unknown[];
  gtag?: (...args: unknown[]) => void;
};

const GOOGLE_SCRIPT_SELECTOR = 'script[data-gole-analytics="true"]';
const ANALYTICS_COOKIE_EXPIRES_SECONDS = 7_776_000;
const GTM_PAGE_VIEW_EVENT = "gole_page_view";

/** GTM 컨테이너 게시 전 검증에서 그대로 대조하는 기계 판독 가능 정책이다. */
export const GTM_ANALYTICS_POLICY = Object.freeze({
  event: "gole_analytics_policy",
  gole_send_page_view: false,
  gole_cookie_expires: ANALYTICS_COOKIE_EXPIRES_SECONDS,
  gole_cookie_update: false,
  gole_page_view_event: GTM_PAGE_VIEW_EVENT,
});

export function readAnalyticsConsent(storage: Storage): AnalyticsConsentDecision | null {
  const raw = storage.getItem(ANALYTICS_CONSENT_STORAGE_KEY);
  if (raw === null) return null;

  try {
    const parsed = JSON.parse(raw) as Partial<StoredAnalyticsConsent>;
    if (
      parsed.version === 1 &&
      (parsed.decision === "granted" || parsed.decision === "denied") &&
      typeof parsed.updatedAt === "string"
    ) {
      return parsed.decision;
    }
  } catch {
    // 손상되거나 이전 형식인 값은 동의로 간주하지 않는다.
  }
  storage.removeItem(ANALYTICS_CONSENT_STORAGE_KEY);
  return null;
}

export function writeAnalyticsConsent(storage: Storage, decision: AnalyticsConsentDecision): void {
  const value: StoredAnalyticsConsent = {
    version: 1,
    decision,
    updatedAt: new Date().toISOString(),
  };
  storage.setItem(ANALYTICS_CONSENT_STORAGE_KEY, JSON.stringify(value));
}

export function resetAnalyticsConsent(storage: Storage): void {
  storage.removeItem(ANALYTICS_CONSENT_STORAGE_KEY);
}

/** GA4가 만드는 자사 쿠키만 지운다. 로그인·OAuth 등 필수 쿠키는 건드리지 않는다. */
export function clearKnownGoogleAnalyticsCookies(documentValue: Document): void {
  const cookieNames = documentValue.cookie
    .split(";")
    .map((part) => part.split("=", 1)[0]?.trim() ?? "")
    .filter(
      (name) =>
        name === "_ga" ||
        name.startsWith("_ga_") ||
        name === "_gid" ||
        name === "_gat" ||
        name.startsWith("_gac_") ||
        name === "_gcl_au",
    );

  const hostname = documentValue.location.hostname.toLowerCase();
  const domains = new Set<string>();
  if (hostname === "gole.co.kr" || hostname.endsWith(".gole.co.kr")) {
    domains.add("gole.co.kr");
    domains.add(".gole.co.kr");
    domains.add(hostname);
    domains.add(`.${hostname}`);
  }

  for (const name of cookieNames) {
    const encodedName = encodeURIComponent(name);
    documentValue.cookie = `${encodedName}=; Max-Age=0; Path=/; SameSite=Lax`;
    for (const domain of domains) {
      documentValue.cookie = `${encodedName}=; Max-Age=0; Path=/; Domain=${domain}; SameSite=Lax`;
    }
  }
}

function createGtag(analyticsWindow: AnalyticsWindow): (...args: unknown[]) => void {
  const gtag = (...args: unknown[]): void => {
    analyticsWindow.dataLayer?.push(args);
  };
  analyticsWindow.gtag = gtag;
  return gtag;
}

function queuePrivacyDefaults(gtag: (...args: unknown[]) => void): void {
  // 이 함수 자체도 명시적 분석 동의 뒤에만 호출한다. 광고 목적 저장·전송은 항상 거부한다.
  gtag("consent", "default", {
    analytics_storage: "granted",
    ad_storage: "denied",
    ad_user_data: "denied",
    ad_personalization: "denied",
    functionality_storage: "denied",
    personalization_storage: "denied",
    security_storage: "denied",
  });
  gtag("set", "ads_data_redaction", true);
  gtag("set", "url_passthrough", false);
  gtag("set", "allow_google_signals", false);
  gtag("set", "allow_ad_personalization_signals", false);
  gtag("set", "cookie_expires", ANALYTICS_COOKIE_EXPIRES_SECONDS);
  gtag("set", "cookie_update", false);
}

function loadExternalScript(id: string, source: string): Promise<void> {
  const existing = document.getElementById(id) as HTMLScriptElement | null;
  if (existing?.dataset.loaded === "true") return Promise.resolve();

  return new Promise((resolve, reject) => {
    const script = existing ?? document.createElement("script");
    const onLoad = (): void => {
      script.dataset.loaded = "true";
      resolve();
    };
    const onError = (): void => reject(new Error("analytics script failed to load"));
    script.addEventListener("load", onLoad, { once: true });
    script.addEventListener("error", onError, { once: true });

    if (existing === null) {
      script.id = id;
      script.async = true;
      script.src = source;
      script.dataset.goleAnalytics = "true";
      document.head.append(script);
    }
  });
}

/** 명시적 동의 뒤 호출한다. 반환 Promise는 외부 스크립트 실행 준비가 끝났을 때 완료된다. */
export async function activateAnalytics(configuration: AnalyticsRuntimeConfig): Promise<void> {
  if (configuration.provider === "disabled") return;

  const analyticsWindow = window as AnalyticsWindow;
  analyticsWindow.dataLayer ??= [];
  const gtag = analyticsWindow.gtag ?? createGtag(analyticsWindow);
  queuePrivacyDefaults(gtag);

  if (configuration.provider === "gtm") {
    analyticsWindow.dataLayer.push({ ...GTM_ANALYTICS_POLICY });
    analyticsWindow.dataLayer.push({ "gtm.start": Date.now(), event: "gtm.js" });
    await loadExternalScript(
      "gole-google-tag-manager",
      `https://www.googletagmanager.com/gtm.js?id=${encodeURIComponent(configuration.id)}`,
    );
    return;
  }

  const disableFlags = window as unknown as Record<string, unknown>;
  disableFlags[`ga-disable-${configuration.id}`] = false;
  await loadExternalScript(
    "gole-google-analytics",
    `https://www.googletagmanager.com/gtag/js?id=${encodeURIComponent(configuration.id)}`,
  );
  gtag("js", new Date());
  gtag("config", configuration.id, {
    send_page_view: false,
    cookie_expires: ANALYTICS_COOKIE_EXPIRES_SECONDS,
    cookie_update: false,
    cookie_flags: window.location.protocol === "https:" ? "SameSite=Lax;Secure" : "SameSite=Lax",
  });
}

/** URL 식별자·쿼리·해시를 분석 차원에서 제거해 개인/거래 객체와 연결되지 않게 한다. */
export function sanitizeAnalyticsPagePath(pagePath: string): string {
  const pathOnly = pagePath.split(/[?#]/, 1)[0] ?? "/";
  if (!pathOnly.startsWith("/")) return "/";

  const segments = pathOnly.split("/").filter((segment) => segment !== "");
  const [section, detail] = segments;
  if (detail !== undefined) {
    if (section === "orders") return "/orders/:id";
    if (section === "listings") return "/listings/:id";
    if (section === "shops") return "/shops/:sellerId";
    if (section === "community" && detail !== "new") return "/community/:id";
    if (section === "auth" && detail === "callback") return "/auth/callback/:provider";
  }
  return pathOnly === "" ? "/" : pathOnly;
}

function sanitizedReferrer(): string {
  if (document.referrer === "") return "";
  try {
    const referrer = new URL(document.referrer);
    if (referrer.origin === window.location.origin) {
      return `${referrer.origin}${sanitizeAnalyticsPagePath(referrer.pathname)}`;
    }
    return referrer.origin;
  } catch {
    return "";
  }
}

export function trackAnalyticsPageView(
  configuration: AnalyticsRuntimeConfig,
  pagePath: string,
): void {
  const analyticsWindow = window as AnalyticsWindow;
  const safePagePath = sanitizeAnalyticsPagePath(pagePath);
  const fields = {
    page_location: `${window.location.origin}${safePagePath}`,
    page_path: safePagePath,
    page_title: `GoLe ${safePagePath}`,
    page_referrer: sanitizedReferrer(),
  };
  if (configuration.provider === "ga") {
    analyticsWindow.gtag?.("event", "page_view", fields);
    return;
  }
  if (configuration.provider === "gtm") {
    analyticsWindow.dataLayer?.push({ event: GTM_PAGE_VIEW_EVENT, ...fields });
  }
}

/**
 * 이미 실행된 GTM 태그의 리스너까지 확실히 종료하려면 거부 상태 저장 후 페이지를 새로 읽어야 한다.
 * 여기서는 새로고침 전에 가능한 동기 정리를 수행한다.
 */
export function deactivateAnalytics(configuration: AnalyticsRuntimeConfig): void {
  for (const script of document.querySelectorAll(GOOGLE_SCRIPT_SELECTOR)) {
    script.remove();
  }

  const disableFlags = window as unknown as Record<string, unknown>;
  if (configuration.gaMeasurementId !== "") {
    disableFlags[`ga-disable-${configuration.gaMeasurementId}`] = true;
  }
  clearKnownGoogleAnalyticsCookies(document);

  const analyticsWindow = window as AnalyticsWindow;
  analyticsWindow.dataLayer = [];
  delete analyticsWindow.gtag;
}
