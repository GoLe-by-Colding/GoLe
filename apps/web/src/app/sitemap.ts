import type { MetadataRoute } from "next";
import { env } from "@shared/config";

/** sitemap.xml — 공개 색인 대상. 정적 경로 + 활성 매물 상세. */
export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  const now = new Date();

  const staticRoutes: MetadataRoute.Sitemap = [
    { url: `${env.siteUrl}/`, lastModified: now, changeFrequency: "daily", priority: 1.0 },
    { url: `${env.siteUrl}/search`, lastModified: now, changeFrequency: "daily", priority: 0.9 },
    { url: `${env.siteUrl}/prices`, lastModified: now, changeFrequency: "daily", priority: 0.8 },
    {
      url: `${env.siteUrl}/community`,
      lastModified: now,
      changeFrequency: "hourly",
      priority: 0.7,
    },
    {
      url: `${env.siteUrl}/collection`,
      lastModified: now,
      changeFrequency: "weekly",
      priority: 0.5,
    },
  ];

  // 활성 매물 상세 URL을 동적으로 추가한다(최대 100개, 실패 시 정적만).
  let listingRoutes: MetadataRoute.Sitemap = [];
  try {
    const res = await fetch(`${env.apiBaseUrl}/api/v1/listings`, { next: { revalidate: 3600 } });
    if (res.ok) {
      const listings = (await res.json()) as ReadonlyArray<{
        id: string;
        createdAt?: string;
      }>;
      listingRoutes = listings.slice(0, 100).map((l) => ({
        url: `${env.siteUrl}/listings/${l.id}`,
        lastModified: l.createdAt ? new Date(l.createdAt) : now,
        changeFrequency: "weekly" as const,
        priority: 0.6,
      }));
    }
  } catch {
    // 백엔드 미기동 시 정적 경로만 반환
  }

  return [...staticRoutes, ...listingRoutes];
}
