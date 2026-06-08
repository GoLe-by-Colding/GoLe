/**
 * 타입 안전한 환경설정. 런타임에 한 번 검증하고 동결한다.
 * 덕 타이핑 방지: 값 형태를 명시적으로 좁혀서 노출한다.
 */
interface AppEnv {
  readonly apiBaseUrl: string;
  readonly siteUrl: string;
  readonly portOneStoreId: string;
  readonly portOneChannelKey: string;
  readonly nodeEnv: "development" | "production" | "test";
}

function readApiBaseUrl(): string {
  const raw = process.env["NEXT_PUBLIC_API_BASE_URL"];
  if (raw === undefined || raw.length === 0) {
    return "http://localhost:8080";
  }
  return raw.replace(/\/+$/, "");
}

function readSiteUrl(): string {
  const raw = process.env["NEXT_PUBLIC_SITE_URL"];
  if (raw === undefined || raw.length === 0) {
    return "https://gole.kscold.com";
  }
  return raw.replace(/\/+$/, "");
}

function readNodeEnv(): AppEnv["nodeEnv"] {
  const raw = process.env["NODE_ENV"];
  if (raw === "production" || raw === "test") {
    return raw;
  }
  return "development";
}

export const env: AppEnv = Object.freeze({
  apiBaseUrl: readApiBaseUrl(),
  siteUrl: readSiteUrl(),
  portOneStoreId: process.env["NEXT_PUBLIC_PORTONE_STORE_ID"] ?? "",
  portOneChannelKey: process.env["NEXT_PUBLIC_PORTONE_CHANNEL_KEY"] ?? "",
  nodeEnv: readNodeEnv(),
});
