import Link from "next/link";
import type { ReactNode } from "react";
import { LegoSetCard, fetchFeaturedLegoSets, type LegoSet } from "@entities/lego-set";
import { fetchFeed, type Post } from "@entities/community";
import { fetchTrendingSets, type TrendingSet } from "@entities/pricing";
import { fetchLaunchConfig } from "@entities/launch";
import { TrendingSets } from "@widgets/trending-sets";
import { PostCard } from "@widgets/post-card";
import { BrickIcon, Container, EmptyState, Heading, LinkButton, Logo, Text } from "@shared/ui";
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
  const [featured, trending, community, stats, launch] = await Promise.all([
    loadFeatured(),
    loadTrending(),
    loadCommunity(),
    loadStats(),
    fetchLaunchConfig(),
  ]);
  const paymentsOpen = launch.features.payments;
  // 데이터가 없는 칸을 마케팅 문구로 채우면 "실시간"이라는 라벨이 거짓이 된다.
  const liveStats: ReadonlyArray<{ readonly label: string; readonly value: string }> = [
    ...(stats.listings > 0
      ? [{ label: "활성 매물", value: `${stats.listings.toLocaleString("ko-KR")}개` }]
      : []),
    ...(stats.txCount > 0
      ? [{ label: "누적 체결", value: `${stats.txCount.toLocaleString("ko-KR")}건` }]
      : []),
  ];

  return (
    <div className="flex flex-col">
      <section className="border-b border-brand-900 bg-brand-950 text-white">
        <Container width="xl">
          <div className="grid gap-10 py-16 min-[960px]:grid-cols-[minmax(0,1.25fr)_minmax(330px,0.75fr)] min-[960px]:items-center min-[960px]:gap-8 xl:gap-12 max-sm:py-14">
            <div className="flex flex-col gap-7">
              <span className="self-start border-l-2 border-accent-400 pl-3 text-sm font-semibold text-accent-300">
                깊은 바다에서 건져 올린 브릭
              </span>
              <h1 className="max-w-[18ch] text-[clamp(2.6rem,5vw,4rem)] font-bold leading-[1.05] tracking-[-0.03em] text-white">
                레고를 <span className="inline-block text-accent-300">가장 합리적으로</span>
              </h1>
              <p className="max-w-[44ch] text-lg leading-relaxed text-brand-100">
                {paymentsOpen
                  ? "체결가 기반 시세 · 안전결제 · 셀러 샵 · 컬렉션."
                  : "체결가 기반 시세 · 판매자 직거래 · 셀러 샵 · 컬렉션."}
                <br className="max-sm:hidden" />
                흩어져 있던 레고 거래를 한곳에서.
              </p>
              <p className="max-w-[40ch] text-sm leading-relaxed text-brand-200/90">
                가격은 감이 아니라 체결 기록에서 나옵니다. 오른쪽 숫자가 지금 이 순간의 GoLe입니다.
              </p>
            </div>

            {/* 우측 레일 — 분수(브릭이 솟는다) → 라이브 지표(지금 얼마나 도는가) →
                CTA(그래서 무엇을 하는가) 순으로 한 줄기 시선을 만든다. */}
            <div className="gole-hero-flow flex min-w-0 flex-col gap-5 rounded-[1.75rem] border border-white/10 bg-white/[0.035] p-5 sm:max-[959px]:grid sm:max-[959px]:grid-cols-[minmax(260px,0.8fr)_minmax(260px,1.2fr)] sm:max-[959px]:items-center sm:p-6">
              <div className="relative flex min-h-48 items-center justify-center sm:max-[959px]:row-span-2 max-sm:min-h-40">
                <span
                  aria-hidden="true"
                  className="absolute right-[8%] bottom-3 left-[8%] h-px bg-gradient-to-r from-transparent via-brand-700 to-transparent"
                />
                <Logo
                  size={286}
                  showWordmark={false}
                  spout
                  className="gole-mascot-float drop-shadow-[0_18px_24px_rgba(3,10,35,0.22)]"
                />
              </div>

              {liveStats.length > 0 ? (
                <div
                  aria-label="실시간 거래 현황"
                  className="divide-y divide-white/15 border-y border-white/20"
                >
                  <div className="flex items-center gap-2 py-2.5">
                    <span aria-hidden="true" className="h-1.5 w-1.5 rounded-full bg-accent-300" />
                    <span className="text-xs font-semibold tracking-wide text-accent-300">
                      실시간 거래 현황
                    </span>
                  </div>
                  {liveStats.map((stat) => (
                    <div
                      key={stat.label}
                      className="flex items-baseline justify-between gap-4 py-3"
                    >
                      <span className="text-xs font-medium uppercase tracking-wide text-brand-200">
                        {stat.label}
                      </span>
                      <span className="text-base font-bold tracking-tight text-white">
                        {stat.value}
                      </span>
                    </div>
                  ))}
                </div>
              ) : null}

              <div className="flex flex-col gap-3 xl:flex-row">
                <LinkButton href="/search" variant="accent" size="lg" fullWidth>
                  상품 둘러보기
                </LinkButton>
                <LinkButton
                  href={paymentsOpen ? "/prices" : "/chat"}
                  variant="inverse"
                  size="lg"
                  fullWidth
                >
                  {paymentsOpen ? "시세 확인하기" : "대화 이어가기"}
                </LinkButton>
              </div>
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
              <EmptyState
                variant="inline"
                icon={<BrickIcon className="h-10 w-10 text-brand-300" strokeWidth={1.5} />}
                title="표시할 세트가 아직 없어요"
              />
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
