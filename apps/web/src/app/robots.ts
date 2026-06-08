import type { MetadataRoute } from "next";
import { env } from "@shared/config";

/** robots.txt — 인증/주문 등 비공개 경로는 크롤링에서 제외한다. */
export default function robots(): MetadataRoute.Robots {
  return {
    rules: {
      userAgent: "*",
      allow: "/",
      disallow: ["/login", "/signup", "/verify", "/orders/", "/sell"],
    },
    sitemap: `${env.siteUrl}/sitemap.xml`,
    host: env.siteUrl,
  };
}
