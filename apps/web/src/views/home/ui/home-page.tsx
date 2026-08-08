import Link from "next/link";
import type { ReactNode } from "react";
import { LegoSetCard, fetchFeaturedLegoSets, type LegoSet } from "@entities/lego-set";
import { fetchFeed, type Post } from "@entities/community";
import { fetchTrendingSets, type TrendingSet } from "@entities/pricing";
import { TrendingSets } from "@widgets/trending-sets";
import { PostCard } from "@widgets/post-card";
import { Container, Heading, LinkButton, Text } from "@shared/ui";
import { formatKrw } from "@shared/lib";
import { env } from "@shared/config";

async function loadFeatured(): Promise<readonly LegoSet[]> {
  try {
    return await fetchFeaturedLegoSets();
  } catch {
    return [];
  }
}

async function loadTrending(): Promise<readonly TrendingSet[]> {
  try {
    return await fetchTrendingSets(8);
  } catch {
    return [];
  }
}

async function loadCommunity(): Promise<readonly Post[]> {
  try {
    const posts = await fetchFeed();
    return posts.slice(0, 4);
  } catch {
    return [];
  }
}

async function loadStats(): Promise<{ listings: number; txCount: number }> {
  try {
    const [listingsRes, trendingRes] = await Promise.all([
      fetch(`${env.apiBaseUrl}/api/v1/listings`, { cache: "no-store" }),
      fetch(`${env.apiBaseUrl}/api/v1/pricing/trending?limit=10`, { cache: "no-store" }),
    ]);
    const listings = listingsRes.ok ? ((await listingsRes.json()) as unknown[]).length : 0;
    const trending = trendingRes.ok
      ? ((await trendingRes.json()) as Array<{ tradeCount: number }>)
      : [];
    const txCount = trending.reduce((sum, s) => sum + s.tradeCount, 0);
    return { listings, txCount };
  } catch {
    return { listings: 0, txCount: 0 };
  }
}

/** 절제된 골드 라인과 제목으로 구성한 섹션 헤더. */
function SectionHeader({ title, aside }: { readonly title: string; readonly aside?: ReactNode }) {
  return (
    <div className="flex items-end justify-between gap-4">
      <div className="flex flex-col gap-2">
        <span aria-hidden="true" className="h-0.5 w-8 bg-accent-400" />
        <Heading level={2}>{title}</Heading>
      </div>
      {aside}
    </div>
  );
}

/** 트렌딩 세트의 평균 체결가를 연속해서 보여주는 시세 티커. */
function PriceTicker({ items }: { readonly items: readonly TrendingSet[] }) {
  if (items.length === 0) return null;
  const doubled = [...items, ...items];
  return (
    <div className="group overflow-hidden border-y border-neutral-200 bg-neutral-50 py-3">
      <div className="flex w-max animate-market-ticker items-center gap-10 pl-10 group-hover:[animation-play-state:paused] motion-reduce:animate-none">
        {doubled.map((set, index) => (
          <Link
            key={`${set.setNumber}-${index}`}
            href={`/prices?set=${encodeURIComponent(set.setNumber)}`}
            aria-hidden={index >= items.length ? "true" : undefined}
            tabIndex={index >= items.length ? -1 : undefined}
            className="flex items-center gap-2.5 whitespace-nowrap text-sm hover:text-brand-700"
          >
            <span className="font-mono font-bold text-brand-700">#{set.setNumber}</span>
            <span className="max-w-[18ch] truncate text-neutral-600">{set.name}</span>
            <span className="font-semibold tabular-nums text-neutral-900">
              {formatKrw(set.averagePrice)}
            </span>
            <span className="text-xs text-neutral-500">
              {set.tradeCount.toLocaleString("ko-KR")}건 체결
            </span>
          </Link>
        ))}
      </div>
    </div>
  );
}

export async function HomePage() {
  const [featured, trending, community, stats] = await Promise.all([
    loadFeatured(),
    loadTrending(),
    loadCommunity(),
    loadStats(),
  ]);

  return (
    <div className="flex flex-col">
      <section className="border-b border-brand-900 bg-brand-950 text-white">
        <Container width="xl">
          <div className="grid gap-12 py-20 lg:grid-cols-[minmax(0,1.2fr)_minmax(280px,0.8fr)] lg:items-end max-sm:py-14">
            <div className="flex flex-col gap-7">
              <span className="self-start border-l-2 border-accent-400 pl-3 text-sm font-semibold text-accent-300">
                깊은 바다에서 건져 올린 브릭
              </span>
              <h1 className="max-w-[18ch] text-[clamp(2.6rem,6vw,4rem)] font-bold leading-[1.05] tracking-[-0.03em] text-white">
                레고를 <span className="text-accent-300">가장 합리적으로</span>
              </h1>
              <p className="max-w-[44ch] text-lg leading-relaxed text-brand-100">
                체결가 기반 시세 · 에스크로 안전거래 · 셀러 샵 · 컬렉션.
                <br className="max-sm:hidden" />
                흩어져 있던 레고 거래를 한곳에서.
              </p>
              <div className="mt-3 flex flex-wrap gap-3">
                <LinkButton href="/search" variant="accent" size="lg">
                  상품 둘러보기
                </LinkButton>
                <LinkButton href="/prices" variant="inverse" size="lg">
                  시세 확인하기
                </LinkButton>
              </div>
            </div>

            <div className="divide-y divide-white/15 border-y border-white/20">
              {[
                {
                  label: "활성 매물",
                  value:
                    stats.listings > 0
                      ? `${stats.listings.toLocaleString("ko-KR")}개`
                      : "에스크로 보호",
                },
                {
                  label: "체결 시세",
                  value:
                    stats.txCount > 0
                      ? `${stats.txCount.toLocaleString("ko-KR")}건`
                      : "체결가 기반",
                },
                { label: "거래 방식", value: "직거래 · 택배" },
              ].map((stat) => (
                <div key={stat.label} className="flex items-baseline justify-between gap-4 py-4">
                  <span className="text-xs font-medium uppercase tracking-wide text-brand-200">
                    {stat.label}
                  </span>
                  <span className="text-lg font-bold tracking-tight text-white">{stat.value}</span>
                </div>
              ))}
            </div>
          </div>
        </Container>

        <PriceTicker items={trending} />
      </section>

      <Container width="xl">
        <div className="flex flex-col gap-20 pt-16 pb-24">
          {/* Trending */}
          <section className="flex flex-col gap-6">
            <SectionHeader
              title="지금 뜨는 세트"
              aside={
                <Text tone="muted" size="sm">
                  최근 거래 활발
                </Text>
              }
            />
            <TrendingSets items={trending} />
          </section>

          {/* Featured */}
          <section className="flex flex-col gap-6">
            <SectionHeader
              title="오늘의 추천"
              aside={
                <Text tone="muted" size="sm">
                  인기 테마 엄선
                </Text>
              }
            />
            {featured.length > 0 ? (
              <div className="grid gap-5 [grid-template-columns:repeat(auto-fill,minmax(240px,1fr))]">
                {featured.map((set) => (
                  <LegoSetCard key={set.setNumber} set={set} />
                ))}
              </div>
            ) : (
              <div className="flex flex-col items-center gap-3 rounded-lg border border-dashed border-neutral-300 px-6 py-16 text-center">
                <Text tone="secondary" weight="medium">
                  표시할 세트가 아직 없어요
                </Text>
              </div>
            )}
          </section>

          {/* Community */}
          {community.length > 0 ? (
            <section className="flex flex-col gap-6">
              <SectionHeader
                title="커뮤니티"
                aside={
                  <LinkButton href="/community" variant="ghost" size="sm">
                    전체 보기
                  </LinkButton>
                }
              />
              <div className="grid gap-5 [grid-template-columns:repeat(auto-fill,minmax(240px,1fr))]">
                {community.map((post) => (
                  <PostCard key={post.id} post={post} />
                ))}
              </div>
            </section>
          ) : null}

          <section className="rounded-lg border border-brand-200 bg-brand-50 px-10 py-12 max-sm:px-6">
            <div className="flex flex-wrap items-center justify-between gap-6">
              <div className="flex flex-col gap-2">
                <h2 className="text-2xl font-bold tracking-tight text-neutral-900">
                  잠자는 브릭, 바다로 보내세요
                </h2>
                <p className="text-neutral-600">
                  사진 5장이면 등록 끝 — 시세 기반 추천가로 빠르게 판매됩니다.
                </p>
              </div>
              <LinkButton href="/sell" variant="primary" size="lg">
                판매 시작하기
              </LinkButton>
            </div>
          </section>
        </div>
      </Container>
    </div>
  );
}
