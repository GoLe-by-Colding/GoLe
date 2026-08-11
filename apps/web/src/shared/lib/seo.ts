/**
 * schema.org 매핑 헬퍼. 구조화 데이터를 만드는 여러 화면(세트 상세·매물 상세)이
 * 같은 매핑을 쓰도록 한 곳에 모은다.
 *
 * 문자열 키를 받는 이유: `shared` 레이어는 `entities`를 import 할 수 없다(FSD 경계).
 * 호출부가 `Listing.status` / `Listing.condition`을 그대로 넘기면 된다.
 */

const AVAILABILITY: Record<string, string> = {
  active: "https://schema.org/InStock",
  reserved: "https://schema.org/LimitedAvailability",
  sold: "https://schema.org/SoldOut",
};

/** 매물 상태 → schema.org Offer.availability. 미지의 상태는 품절로 본다(과장 방지). */
export function schemaAvailability(status: string): string {
  return AVAILABILITY[status] ?? "https://schema.org/SoldOut";
}

/**
 * 매물 상태등급 → schema.org itemCondition. (condition-disclosure 5단계)
 *
 * schema.org에는 우리 등급과 1:1로 맞는 값이 없다. NewCondition/UsedCondition/DamagedCondition
 * 세 갈래로만 접는다. 레거시 값(used_complete 등)도 중고로 떨어지므로 별도 처리가 필요 없다.
 */
export function schemaItemCondition(condition: string): string {
  if (condition === "new_sealed") return "https://schema.org/NewCondition";
  if (condition === "damaged") return "https://schema.org/DamagedCondition";
  return "https://schema.org/UsedCondition";
}

/**
 * 상대 경로를 절대 URL로 승격한다. OG 이미지·구조화 데이터의 `image`는
 * 절대 URL이어야 크롤러가 해석한다.
 */
export function absoluteUrl(pathOrUrl: string, siteUrl: string): string {
  if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) {
    return pathOrUrl;
  }
  return `${siteUrl}${pathOrUrl.startsWith("/") ? "" : "/"}${pathOrUrl}`;
}

export interface BreadcrumbItem {
  readonly name: string;
  readonly path: string;
}

/** BreadcrumbList JSON-LD. path는 사이트 루트 기준 상대 경로. */
export function breadcrumbJsonLd(
  items: readonly BreadcrumbItem[],
  siteUrl: string,
): Record<string, unknown> {
  return {
    "@context": "https://schema.org",
    "@type": "BreadcrumbList",
    itemListElement: items.map((item, index) => ({
      "@type": "ListItem",
      position: index + 1,
      name: item.name,
      item: `${siteUrl}${item.path}`,
    })),
  };
}
