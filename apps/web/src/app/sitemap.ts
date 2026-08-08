import type { MetadataRoute } from "next";
import { env } from "@shared/config";

const REVALIDATE = { next: { revalidate: 3600 } } as const;

/** 백엔드 조회 실패 시 sitemap 전체를 잃지 않도록 개별 섹션을 격리한다. */
async function fetchJson<T>(path: string): Promise<T | null> {
  try {
    const res = await fetch(`${env.apiBaseUrl}${path}`, REVALIDATE);
    if (!res.ok) {
      return null;
    }
    return (await res.json()) as T;
  } catch {
    return null;
  }
}

/**
 * sitemap.xml — 공개 색인 대상.
 *
 * 개인 전용 화면(`/collection`, `/profile`, `/orders` 등)은 제외한다.
 * 로그인 사용자 본인 데이터라 색인 가치가 없고, 크롤러에는 빈 화면으로 보인다.
 */
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
  ];

  const listings = await fetchJson<
    ReadonlyArray<{
      id: string;
      sellerId?: string;
      catalogSetNumber?: string | null;
      createdAt?: string;
    }>
  >("/api/v1/listings");

  const listingRoutes: MetadataRoute.Sitemap = (listings ?? []).slice(0, 100).map((l) => ({
    url: `${env.siteUrl}/listings/${l.id}`,
    lastModified: l.createdAt ? new Date(l.createdAt) : now,
    changeFrequency: "weekly" as const,
    priority: 0.6,
  }));

  // 매물을 보유한 셀러의 샵만 넣는다. 빈 샵은 색인 가치가 없다. (R4.2)
  const sellerIds = [
    ...new Set((listings ?? []).map((l) => l.sellerId).filter((id): id is string => Boolean(id))),
  ];
  const shopRoutes: MetadataRoute.Sitemap = sellerIds.slice(0, 100).map((sellerId) => ({
    url: `${env.siteUrl}/shops/${sellerId}`,
    lastModified: now,
    changeFrequency: "weekly" as const,
    priority: 0.5,
  }));

  const posts =
    await fetchJson<ReadonlyArray<{ id: string; createdAt?: string }>>("/api/v1/community/posts");
  const communityRoutes: MetadataRoute.Sitemap = (posts ?? []).slice(0, 100).map((p) => ({
    url: `${env.siteUrl}/community/${p.id}`,
    lastModified: p.createdAt ? new Date(p.createdAt) : now,
    changeFrequency: "weekly" as const,
    priority: 0.5,
  }));

  // 세트 상세 — 롱테일 검색 유입의 핵심이라 매물·게시글보다 우선순위를 높게 준다. (R4.1)
  // 카탈로그 전체 조회 API가 없어 featured로 시작하고, 매물이 참조하는 세트번호를 합집합한다.
  const featured = await fetchJson<ReadonlyArray<{ setNumber: string }>>(
    "/api/v1/catalog/sets/featured",
  );
  const setNumbers = [
    ...new Set([
      ...(featured ?? []).map((s) => s.setNumber),
      ...(listings ?? [])
        .map((l) => l.catalogSetNumber)
        .filter((n): n is string => typeof n === "string" && n.length > 0),
    ]),
  ];
  const setRoutes: MetadataRoute.Sitemap = setNumbers.map((setNumber) => ({
    url: `${env.siteUrl}/sets/${setNumber}`,
    lastModified: now,
    changeFrequency: "daily" as const,
    priority: 0.8,
  }));

  return [...staticRoutes, ...setRoutes, ...listingRoutes, ...shopRoutes, ...communityRoutes];
}
