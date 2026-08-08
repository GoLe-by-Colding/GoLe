import Link from "next/link";
import type { ReactNode } from "react";
import { LegoSetCard, fetchFeaturedLegoSets, type LegoSet } from "@entities/lego-set";
import { fetchFeed, type Post } from "@entities/community";
import { fetchTrendingSets, type TrendingSet } from "@entities/pricing";
import { TrendingSets } from "@widgets/trending-sets";
import { PostCard } from "@widgets/post-card";
import { Container, Heading, LinkButton, Logo, Text } from "@shared/ui";
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

/** 브릭 스터드 3점 + 제목 — GoLe 섹션 헤더 시그니처 */
function SectionHeader({ title, aside }: { readonly title: string; readonly aside?: ReactNode }) {
  return (
    <div className="flex items-end justify-between gap-4">
      <div className="flex flex-col gap-2.5">
        <span aria-hidden="true" className="flex gap-1">
          <span className="h-1.5 w-1.5 rounded-full bg-brand-600" />
          <span className="h-1.5 w-1.5 rounded-full bg-brand-400" />
          <span className="h-1.5 w-1.5 rounded-full bg-accent-400" />
        </span>
        <Heading level={2}>{title}</Heading>
      </div>
      {aside}
    </div>
  );
}

/** 시세 마퀴 티커 — 트렌딩 세트의 평균 체결가가 흐른다 (hover 시 일시정지) */
function PriceTicker({ items }: { readonly items: readonly TrendingSet[] }) {
  if (items.length === 0) return null;
  const doubled = [...items, ...items];
  return (
    <div className="group relative overflow-hidden border-t border-white/10 bg-brand-950/70 py-3 backdrop-blur-sm">
      <div className="flex w-max animate-marquee items-center gap-10 pl-10 group-hover:[animation-play-state:paused] motion-reduce:animate-none">
        {doubled.map((set, i) => (
          <Link
            key={`${set.setNumber}-${i}`}
            href={`/prices?set=${encodeURIComponent(set.setNumber)}`}
            aria-hidden={i >= items.length ? "true" : undefined}
            tabIndex={i >= items.length ? -1 : undefined}
            className="flex items-center gap-2.5 whitespace-nowrap text-sm transition-opacity hover:opacity-80"
          >
            <span className="font-mono font-bold text-accent-400">#{set.setNumber}</span>
            <span className="max-w-[18ch] truncate text-white/70">{set.name}</span>
            <span className="font-semibold tabular-nums text-white">
              {formatKrw(set.averagePrice)}
            </span>
            <span className="rounded-full bg-white/10 px-2 py-0.5 text-xs text-brand-200">
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
      {/* ── Hero: 딥 오션 (고래가 사는 깊은 바다 + 떠오르는 브릭 골드) ── */}
      <section className="ocean-surface relative overflow-hidden">
        {/* 브릭 스터드 패턴 */}
        <div
          aria-hidden="true"
          className="stud-pattern pointer-events-none absolute inset-0 text-white/[0.05]"
        />
        {/* 부유하는 고래 브릭 마스코트 — 워터마크가 아니라 실제로 보이는 일러스트로 둔다. */}
        <div
          aria-hidden="true"
          className="pointer-events-none absolute right-6 top-1/2 -translate-y-[54%] opacity-60 max-lg:hidden motion-safe:animate-float xl:right-16"
        >
          <Logo size={380} showWordmark={false} spout />
        </div>

        <Container width="xl" className="relative">
          <div className="flex flex-col gap-7 py-24 max-sm:py-14">
            <span className="animate-rise inline-flex items-center gap-2 self-start rounded-full border border-white/15 bg-white/10 px-3.5 py-1.5 text-sm font-semibold text-accent-300 backdrop-blur-sm">
              🐋 깊은 바다에서 건져 올린 브릭
            </span>
            <h1 className="animate-rise max-w-[18ch] text-[clamp(2.6rem,6vw,4.25rem)] font-extrabold leading-[1.05] tracking-[-0.03em] text-white [animation-delay:60ms]">
              레고를{" "}
              <span className="bg-gradient-to-r from-accent-300 via-accent-400 to-accent-300 bg-clip-text text-transparent">
                가장 합리적으로
              </span>
            </h1>
            <p className="animate-rise max-w-[44ch] text-lg leading-relaxed text-brand-100/80 [animation-delay:120ms]">
              체결가 기반 시세 · 에스크로 안전거래 · 셀러 샵 · 컬렉션.
              <br className="max-sm:hidden" />
              흩어져 있던 레고 거래를 한곳에서.
            </p>
            <div className="animate-rise mt-3 flex flex-wrap gap-3 [animation-delay:180ms]">
              <LinkButton href="/search" variant="accent" size="lg">
                상품 둘러보기
              </LinkButton>
              <LinkButton href="/prices" variant="inverse" size="lg">
                시세 확인하기
              </LinkButton>
            </div>

            {/* 글래스 스탯 카드 */}
            <div className="animate-rise mt-8 grid max-w-2xl grid-cols-3 gap-3 [animation-delay:240ms] max-sm:grid-cols-1">
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
                <div
                  key={stat.label}
                  className="flex flex-col gap-1 rounded-xl border border-white/10 bg-white/[0.07] px-5 py-4 backdrop-blur-md"
                >
                  <span className="text-xs font-medium uppercase tracking-wide text-brand-200/70">
                    {stat.label}
                  </span>
                  <span className="text-lg font-bold tracking-tight text-white">{stat.value}</span>
                </div>
              ))}
            </div>
          </div>
        </Container>

        {/* 시세 티커 */}
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
              <div className="flex flex-col items-center gap-3 rounded-2xl border border-dashed border-neutral-200 px-6 py-16 text-center">
                <span aria-hidden="true" className="text-4xl">
                  🧱
                </span>
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

          {/* 판매 유도 CTA 밴드 */}
          <section className="relative overflow-hidden rounded-3xl bg-gradient-to-r from-brand-600 to-brand-800 px-10 py-12 max-sm:px-6">
            <div
              aria-hidden="true"
              className="stud-pattern pointer-events-none absolute inset-0 text-white/[0.06]"
            />
            <div className="relative flex flex-wrap items-center justify-between gap-6">
              <div className="flex flex-col gap-2">
                <h2 className="text-2xl font-extrabold tracking-tight text-white">
                  잠자는 브릭, 바다로 보내세요
                </h2>
                <p className="text-brand-100/80">
                  사진 5장이면 등록 끝 — 시세 기반 추천가로 빠르게 판매됩니다.
                </p>
              </div>
              <LinkButton href="/sell" variant="accent" size="lg">
                판매 시작하기
              </LinkButton>
            </div>
          </section>
        </div>
      </Container>
    </div>
  );
}
