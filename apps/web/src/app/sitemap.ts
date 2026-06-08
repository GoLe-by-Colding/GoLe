import type { MetadataRoute } from "next";
import { env } from "@shared/config";

/** sitemap.xml — 공개 색인 대상 정적 경로. (상품/시세 상세는 추후 동적 확장) */
export default function sitemap(): MetadataRoute.Sitemap {
  const now = new Date();
  const routes: ReadonlyArray<{ path: string; priority: number; freq: MetadataRoute.Sitemap[number]["changeFrequency"] }> = [
    { path: "/", priority: 1.0, freq: "daily" },
    { path: "/search", priority: 0.9, freq: "daily" },
    { path: "/prices", priority: 0.8, freq: "daily" },
    { path: "/community", priority: 0.7, freq: "hourly" },
    { path: "/collection", priority: 0.5, freq: "weekly" },
  ];
  return routes.map((r) => ({
    url: `${env.siteUrl}${r.path}`,
    lastModified: now,
    changeFrequency: r.freq,
    priority: r.priority,
  }));
}
