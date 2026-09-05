export type AnalyticsProvider = "disabled" | "ga" | "gtm";

export const ANALYTICS_CONSENT_STORAGE_KEY = "gole.analytics-consent.v1";
export const OPEN_ANALYTICS_SETTINGS_EVENT = "gole:open-analytics-settings";

export interface AnalyticsRuntimeConfig {
  readonly provider: AnalyticsProvider;
  readonly id: string;
  readonly gaMeasurementId: string;
  readonly gtmId: string;
}

const GA_MEASUREMENT_ID_PATTERN = /^G-[A-Z0-9]+$/;
const GTM_ID_PATTERN = /^GTM-[A-Z0-9]+$/;

/**
 * NEXT_PUBLIC_* 분석 ID는 브라우저 번들에 빌드 타임으로 고정된다.
 * 공백이나 비정상 ID를 조용히 비활성화하지 않고 빌드를 실패시켜 운영 설정 오류를 드러낸다.
 */
export function validateOptionalAnalyticsId(
  name: "NEXT_PUBLIC_GA_MEASUREMENT_ID" | "NEXT_PUBLIC_GTM_ID",
  value: string | undefined,
): string {
  if (value === undefined || value === "") {
    return "";
  }

  const pattern =
    name === "NEXT_PUBLIC_GA_MEASUREMENT_ID" ? GA_MEASUREMENT_ID_PATTERN : GTM_ID_PATTERN;
  if (value.trim() !== value || !pattern.test(value)) {
    const expected = name === "NEXT_PUBLIC_GA_MEASUREMENT_ID" ? "G-" : "GTM-";
    throw new Error(`${name} must be empty or a valid ${expected} identifier`);
  }
  return value;
}

/**
 * 중복 수집 방지 계약: GTM ID가 있으면 GTM만 로드하고 직접 gtag.js는 절대 로드하지 않는다.
 * GTM이 없을 때에만 GA 측정 ID를 직접 사용한다.
 */
export function resolveAnalyticsRuntimeConfig({
  gaMeasurementId,
  gtmId,
}: {
  readonly gaMeasurementId: string;
  readonly gtmId: string;
}): AnalyticsRuntimeConfig {
  if (gtmId !== "") {
    return Object.freeze({
      provider: "gtm",
      id: gtmId,
      gaMeasurementId,
      gtmId,
    });
  }
  if (gaMeasurementId !== "") {
    return Object.freeze({
      provider: "ga",
      id: gaMeasurementId,
      gaMeasurementId,
      gtmId,
    });
  }
  return Object.freeze({
    provider: "disabled",
    id: "",
    gaMeasurementId,
    gtmId,
  });
}
