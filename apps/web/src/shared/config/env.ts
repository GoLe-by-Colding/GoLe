/**
 * 타입 안전한 환경설정. 런타임에 한 번 검증하고 동결한다.
 * 덕 타이핑 방지: 값 형태를 명시적으로 좁혀서 노출한다.
 */
interface AppEnv {
  readonly apiBaseUrl: string;
  /** 브라우저가 이미지처럼 직접 요청하는 공개 API 원점. 운영의 동일 출처는 빈 문자열이다. */
  readonly publicApiBaseUrl: string;
  readonly siteUrl: string;
  readonly portOneStoreId: string;
  readonly portOneChannelKey: string;
  /** 카드(KG이니시스) 채널. 빈 문자열이면 카드 결제를 노출하지 않는다. */
  readonly portOneCardChannelKey: string;
  readonly paymentMode: "disabled" | "stub" | "portone-test" | "portone-live";
  readonly nodeEnv: "development" | "production" | "test";
  readonly gaMeasurementId: string;
  readonly gtmId: string;
}

import {
  resolveAnalyticsRuntimeConfig,
  validateOptionalAnalyticsId,
  type AnalyticsRuntimeConfig,
} from "./analytics";

export type PaymentRuntimeConfig = Pick<
  AppEnv,
  "paymentMode" | "nodeEnv" | "portOneStoreId" | "portOneChannelKey"
>;

function readPaymentMode(nodeEnv: AppEnv["nodeEnv"]): AppEnv["paymentMode"] {
  const raw = process.env.NEXT_PUBLIC_PAYMENT_MODE;
  if (raw === "disabled" || raw === "stub" || raw === "portone-test" || raw === "portone-live") {
    return raw;
  }
  // 공개 빌드는 결제 설정을 명시하지 않으면 닫힌다. 개발만 결정론적 스텁을 기본으로 쓴다.
  return nodeEnv === "production" ? "disabled" : "stub";
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
  return process.env.NODE_ENV === "production" ? "https://gole.co.kr" : "http://localhost:3000";
}

function readNodeEnv(): AppEnv["nodeEnv"] {
  const raw = process.env["NODE_ENV"];
  if (raw === "production" || raw === "test") {
    return raw;
  }
  return "development";
}

const nodeEnv = readNodeEnv();

const gaMeasurementId = validateOptionalAnalyticsId(
  "NEXT_PUBLIC_GA_MEASUREMENT_ID",
  process.env.NEXT_PUBLIC_GA_MEASUREMENT_ID,
);
const gtmId = validateOptionalAnalyticsId("NEXT_PUBLIC_GTM_ID", process.env.NEXT_PUBLIC_GTM_ID);

export const env: AppEnv = Object.freeze({
  apiBaseUrl: readApiBaseUrl(),
  publicApiBaseUrl: readPublicApiBaseUrl(),
  siteUrl: readSiteUrl(),
  portOneStoreId: process.env.NEXT_PUBLIC_PORTONE_STORE_ID ?? "",
  portOneChannelKey: process.env.NEXT_PUBLIC_PORTONE_CHANNEL_KEY ?? "",
  portOneCardChannelKey: process.env.NEXT_PUBLIC_PORTONE_CARD_CHANNEL_KEY ?? "",
  paymentMode: readPaymentMode(nodeEnv),
  nodeEnv,
  gaMeasurementId,
  gtmId,
});

export const analyticsRuntimeConfig: AnalyticsRuntimeConfig = resolveAnalyticsRuntimeConfig({
  gaMeasurementId,
  gtmId,
});

/**
 * 공개 launch 플래그와 별개로, 이 웹 빌드가 신규 결제를 실제로 시작할 수 있는지 판정한다.
 *
 * 운영에서 `stub`을 명시해도 허용하지 않고, PortOne 모드는 결제창에 반드시 필요한 공개 키가
 * 모두 있을 때만 연다. 서버 플래그가 잘못 열려도 브라우저가 스텁 API로 조용히 우회하지 않는
 * 두 번째 fail-closed 경계다.
 */
export function isPaymentRuntimeAvailable(configuration: PaymentRuntimeConfig = env): boolean {
  if (configuration.paymentMode === "disabled") {
    return false;
  }
  if (configuration.paymentMode === "stub") {
    return configuration.nodeEnv !== "production";
  }
  return configuration.portOneStoreId.length > 0 && configuration.portOneChannelKey.length > 0;
}
