import { expect, test } from "@playwright/test";

const CORE_ROUTES = [
  "/",
  "/search",
  "/prices",
  "/community",
  "/feed",
  "/collection",
  "/chat",
  "/profile",
  "/notifications",
  "/sell",
  "/community/new",
  "/profile/security",
  "/login",
  "/signup",
  "/forgot-password",
  "/verify",
  "/onboarding",
  "/privacy",
  "/terms",
  "/review-policy",
  "/admin",
  "/admin/launch",
  "/this-route-does-not-exist",
] as const;

interface InteractionDefect {
  readonly kind:
    | "dead-link"
    | "missing-button-name"
    | "missing-button-type"
    | "missing-link-name"
    | "unknown-internal-route";
  readonly html: string;
}

/**
 * 버튼/링크의 전역 HTML 계약.
 *
 * 동작별 상세 E2E와 별개로, 한 화면의 작은 수정이 클릭 구조 전체를 깨뜨리는 흔한 회귀
 * (링크 안 버튼, 무명 아이콘 버튼, 실수로 submit이 된 버튼, # 자리표시자 링크)를 빠르게 잡는다.
 */
test("핵심 화면의 모든 버튼과 링크가 유효한 단일 상호작용 역할을 갖는다", async ({ page }) => {
  for (const route of CORE_ROUTES) {
    await page.goto(route);
    await expect(page.locator("body"), `${route}: 문서가 렌더돼야 함`).toBeVisible();

    await expect(
      page.locator("a button, button a, a a, button button"),
      `${route}: 상호작용 요소를 서로 중첩하면 안 됨`,
    ).toHaveCount(0);

    const defects = await page.locator("body").evaluate<InteractionDefect[]>((body) => {
      const compact = (element: Element): string =>
        element.outerHTML.replace(/\s+/g, " ").slice(0, 180);
      const accessibleName = (element: Element): string => {
        const ariaLabel = element.getAttribute("aria-label")?.trim();
        if (ariaLabel) return ariaLabel;

        const labelledBy = element.getAttribute("aria-labelledby")?.trim().split(/\s+/) ?? [];
        const labelledText = labelledBy
          .map((id) => document.getElementById(id)?.textContent?.trim() ?? "")
          .filter(Boolean)
          .join(" ");
        if (labelledText) return labelledText;

        const text = element.textContent?.trim();
        if (text) return text;

        const imageText = Array.from(element.querySelectorAll("img[alt]"))
          .map((image) => image.getAttribute("alt")?.trim() ?? "")
          .filter(Boolean)
          .join(" ");
        return imageText || element.getAttribute("title")?.trim() || "";
      };
      const result: InteractionDefect[] = [];
      const knownInternalRoutes = [
        /^\/$/,
        /^\/(?:search|prices|community|feed|collection|chat|profile|notifications|sell|login|signup|forgot-password|verify|onboarding|privacy|terms|review-policy)\/?$/,
        /^\/profile\/security\/?$/,
        /^\/(?:listings|orders|sets|shops|community)\/[^/]+\/?$/,
        /^\/payments\/portone\/return\/?$/,
        /^\/auth\/callback\/[^/]+\/?$/,
        /^\/admin(?:\/(?:account-deletions|accounts|audit|catalog|community|exceptions|launch|listings|orders|reports|settlements|support))?\/?$/,
      ];

      for (const button of body.querySelectorAll("button")) {
        if (!button.hasAttribute("type")) {
          result.push({ kind: "missing-button-type", html: compact(button) });
        }
        if (!accessibleName(button)) {
          result.push({ kind: "missing-button-name", html: compact(button) });
        }
      }

      for (const link of body.querySelectorAll("a[href]")) {
        const href = link.getAttribute("href")?.trim() ?? "";
        if (!href || href === "#" || href.toLowerCase().startsWith("javascript:")) {
          result.push({ kind: "dead-link", html: compact(link) });
          continue;
        }
        if (!accessibleName(link)) {
          result.push({ kind: "missing-link-name", html: compact(link) });
        }
        if (href.startsWith("#")) continue;
        const url = new URL(href, window.location.origin);
        if (
          url.origin === window.location.origin &&
          !knownInternalRoutes.some((pattern) => pattern.test(url.pathname))
        ) {
          result.push({ kind: "unknown-internal-route", html: compact(link) });
        }
      }

      return result;
    });

    expect(defects, `${route}: 잘못된 버튼/링크 계약`).toEqual([]);
  }
});
