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
