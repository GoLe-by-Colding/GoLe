/**
 * 타입 안전한 환경설정. 런타임에 한 번 검증하고 동결한다.
 * 덕 타이핑 방지: 값 형태를 명시적으로 좁혀서 노출한다.
 */
interface AppEnv {
  readonly apiBaseUrl: string;
  /** 브라우저가 이미지처럼 직접 요청하는 공개 API 원점. 운영의 동일 출처는 빈 문자열이다. */
  readonly publicApiBaseUrl: string;
  readonly siteUrl: string;
  readonly discordInviteUrl: string;
  readonly portOneStoreId: string;
  /** 포트원 채널 키는 결제수단(=PG사)마다 다르다. 미설정 수단은 빈 문자열이다. */
  readonly portOneChannelKeyCard: string;
  readonly portOneChannelKeyKakaopay: string;
  readonly nodeEnv: "development" | "production" | "test";
}

function readPublicApiBaseUrl(): string {
  const raw = process.env.NEXT_PUBLIC_API_BASE_URL;
  if (raw !== undefined && raw.length > 0) {
    return raw.replace(/\/+$/, "");
  }
  return process.env.NODE_ENV === "production" ? "" : "http://localhost:8080";
}

function readApiBaseUrl(): string {
  // 점 표기(process.env.NEXT_PUBLIC_*)여야 클라이언트 번들에 정적 인라인된다.
  // (대괄호 표기는 브라우저에서 undefined로 남아 fallback이 적용되는 버그가 있었다.)
  const raw = process.env.NEXT_PUBLIC_API_BASE_URL;
  if (raw !== undefined && raw.length > 0) {
    return raw.replace(/\/+$/, "");
  }
  // 로컬 개발에서는 브라우저와 SSR 모두 8080의 Spring Boot를 직접 사용한다.
  // 이 기본값 덕분에 개인 환경 파일이 없어도 요청이 Next.js(3000)의 404로 빠지지 않는다.
  if (process.env.NODE_ENV !== "production") {
    return "http://localhost:8080";
  }
  // 운영 미설정 시: 서버(SSR)는 동일 컨테이너의 백엔드를 사용하고,
  // 브라우저는 동일 출처 상대경로(nginx가 /api → 백엔드 프록시)를 사용한다.
  if (typeof window === "undefined") {
    return "http://localhost:8080";
  }
  return "";
}

function readSiteUrl(): string {
  const raw = process.env.NEXT_PUBLIC_SITE_URL;
  if (raw !== undefined && raw.length > 0) {
    return raw.replace(/\/+$/, "");
  }
  return process.env.NODE_ENV === "production"
    ? "https://gole.kscold.com"
    : "http://localhost:3000";
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
  publicApiBaseUrl: readPublicApiBaseUrl(),
  siteUrl: readSiteUrl(),
  discordInviteUrl: process.env.NEXT_PUBLIC_DISCORD_INVITE_URL ?? "https://discord.gg/ExbG5MPjbK",
  // 점 표기여야 클라이언트 번들에 정적 인라인된다(위 readApiBaseUrl 주석 참고).
  portOneStoreId: process.env.NEXT_PUBLIC_PORTONE_STORE_ID ?? "",
  portOneChannelKeyCard: process.env.NEXT_PUBLIC_PORTONE_CHANNEL_KEY_CARD ?? "",
  portOneChannelKeyKakaopay: process.env.NEXT_PUBLIC_PORTONE_CHANNEL_KEY_KAKAOPAY ?? "",
  nodeEnv: readNodeEnv(),
});
