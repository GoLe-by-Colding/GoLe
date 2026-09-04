import type { NextConfig } from "next";

const isDevelopment = process.env.NODE_ENV === "development";
const isHttpsDeployment = (process.env.NEXT_PUBLIC_SITE_URL ?? "").startsWith("https://");
const paymentMode = process.env.NEXT_PUBLIC_PAYMENT_MODE;
const isPortOneEnabled = paymentMode === "portone-test" || paymentMode === "portone-live";
const isAnalyticsEnabled =
  (process.env.NEXT_PUBLIC_GA_MEASUREMENT_ID ?? "") !== "" ||
  (process.env.NEXT_PUBLIC_GTM_ID ?? "") !== "";
const apiOrigin = (() => {
  const raw = process.env.NEXT_PUBLIC_API_BASE_URL;
  if (raw === undefined || raw.length === 0) return "";
  try {
    return new URL(raw).origin;
  } catch {
    return "";
  }
})();
const contentSecurityPolicy = [
  "default-src 'self'",
  `script-src 'self' 'unsafe-inline'${isDevelopment ? " 'unsafe-eval'" : ""}${isPortOneEnabled ? " https://cdn.portone.io" : ""}${isAnalyticsEnabled ? " https://www.googletagmanager.com" : ""}`,
  "style-src 'self' 'unsafe-inline'",
  `img-src 'self' blob: data:${apiOrigin === "" ? "" : ` ${apiOrigin}`}${isAnalyticsEnabled ? " https://www.google-analytics.com https://*.google-analytics.com https://www.googletagmanager.com" : ""}`,
  "font-src 'self' data:",
  `connect-src 'self'${apiOrigin === "" ? "" : ` ${apiOrigin}`}${isDevelopment ? " http://localhost:* ws://localhost:*" : ""}${isPortOneEnabled ? " https:" : ""}${isAnalyticsEnabled ? " https://www.google-analytics.com https://*.google-analytics.com" : ""}`,
  isPortOneEnabled ? "frame-src https:" : "frame-src 'none'",
  "object-src 'none'",
  "base-uri 'self'",
  "form-action 'self'",
  "frame-ancestors 'none'",
  ...(isHttpsDeployment ? ["upgrade-insecure-requests"] : []),
].join("; ");

const nextConfig: NextConfig = {
  // 공개 응답에서 프레임워크 버전 식별 단서를 노출하지 않는다.
  poweredByHeader: false,
  // Playwright는 이미 실행 중인 개발 서버를 건드리지 않고 별도 빌드 캐시를 쓸 수 있다.
  distDir: process.env.NEXT_DIST_DIR ?? ".next",
  async headers() {
    return [
      {
        source: "/(.*)",
        headers: [
          { key: "Content-Security-Policy", value: contentSecurityPolicy },
          { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
          { key: "X-Content-Type-Options", value: "nosniff" },
          { key: "X-Frame-Options", value: "DENY" },
          {
            key: "Permissions-Policy",
            value: "camera=(), microphone=(), geolocation=(), browsing-topics=()",
          },
          { key: "Strict-Transport-Security", value: "max-age=31536000" },
        ],
      },
    ];
  },
};

export default nextConfig;
