import type { NextConfig } from "next";

const isDevelopment = process.env.NODE_ENV === "development";
const isHttpsDeployment = (process.env.NEXT_PUBLIC_SITE_URL ?? "").startsWith("https://");
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
  `script-src 'self' 'unsafe-inline'${isDevelopment ? " 'unsafe-eval'" : ""} https://cdn.portone.io`,
  "style-src 'self' 'unsafe-inline'",
  `img-src 'self' blob: data: https:${apiOrigin === "" ? "" : ` ${apiOrigin}`}`,
  "font-src 'self' data:",
  "connect-src 'self' http://localhost:* https: wss:",
  "frame-src https:",
  "object-src 'none'",
  "base-uri 'self'",
  "form-action 'self'",
  "frame-ancestors 'none'",
  ...(isHttpsDeployment ? ["upgrade-insecure-requests"] : []),
].join("; ");

const nextConfig: NextConfig = {
  // @gole/core는 빌드 산출물이 아니라 TS 소스를 내보낸다(웹·앱이 각자 번들러로 컴파일).
  // 이 선언이 없으면 node_modules 안의 .ts를 Next가 그대로 파싱하려다 실패한다.
  transpilePackages: ["@gole/core"],
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
