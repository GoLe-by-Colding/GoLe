import Link from "next/link";
import type { ReactNode } from "react";
import { LegoSetCard, fetchFeaturedLegoSets, type LegoSet } from "@entities/lego-set";
import { fetchFeed, type Post } from "@entities/community";
import { fetchTrendingSets, type TrendingSet } from "@entities/pricing";
import { fetchLaunchConfig } from "@entities/launch";
import { TrendingSets } from "@widgets/trending-sets";
import { PostCard } from "@widgets/post-card";
import { BrickIcon, Container, EmptyState, Heading, LinkButton, Logo, Text } from "@shared/ui";
import { cn, formatKrw } from "@shared/lib";
import { env, isPaymentRuntimeAvailable } from "@shared/config";
import { serverSessionHeaders } from "@shared/api/server-session-headers";

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
    const posts = await fetchFeed({ headers: await serverSessionHeaders(), limit: 4 });
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

/** 짧은 브랜드 라인과 제목으로 구성한 섹션 헤더. 골드는 히어로 제목과 고래 스터드에만 남긴다. */
function SectionHeader({ title, aside }: { readonly title: string; readonly aside?: ReactNode }) {
  return (
    <div className="flex items-end justify-between gap-4">
      <div className="flex flex-col gap-2">
        <span aria-hidden="true" className="h-0.5 w-8 bg-brand-500" />
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
  const sellerTradingOpen = launch.sellerIdentityVerificationReady;
  const paymentsOpen = sellerTradingOpen && launch.features.payments && isPaymentRuntimeAvailable();
  // 데이터가 없는 칸을 마케팅 문구로 채우면 "실시간"이라는 라벨이 거짓이 된다.
  const liveStats: ReadonlyArray<{ readonly label: string; readonly value: string }> = [
    ...(stats.listings > 0
      ? [{ label: "활성 매물", value: `${stats.listings.toLocaleString("ko-KR")}개` }]
      : []),
    ...(stats.txCount > 0
      ? [{ label: "누적 체결", value: `${stats.txCount.toLocaleString("ko-KR")}건` }]
      : []),
  ];

  // 히어로 CTA. 넓은 화면은 우측 레일(분수 → 지표 → CTA) 끝에, 모바일은 문구 바로 아래에 같은 것을 둔다.
  // 처음 온 사람에게도 참이어야 하므로 보조 CTA는 로그인·대화 이력과 무관한 공개 화면으로만 보낸다
  // ("대화 이어가기"는 대화가 없는 신규 방문자에게 거짓이었다).
  const heroActions = (
    <div className="flex flex-col gap-3 xl:flex-row">
      <LinkButton href="/search" variant="primary" size="lg" fullWidth>
        상품 둘러보기
      </LinkButton>
      <LinkButton
        href={sellerTradingOpen ? "/prices" : "/community"}
        variant="secondary"
        size="lg"
        fullWidth
      >
        {sellerTradingOpen ? "시세 확인하기" : "커뮤니티 둘러보기"}
      </LinkButton>
    </div>
  );

  return (
    <div className="flex flex-col">
      {/*
        밝은 히어로 — 흰 바탕에 연한 블루 레일, 코발트 CTA, 골드는 제목 밑줄과 고래 스터드 두 곳뿐이다
        (brand-identity: 흰색 기반·accent 화면당 1~2곳). 짙은 남색 히어로는 무겁고, 그 위의 brand-500
        고래는 대비가 3:1이라 캐릭터가 묻혔다. 연한 레일 위 기본 brand-600 고래는 약 6:1이다.
      */}
      <section className="bg-white">
        <Container width="xl">
          <div className="grid gap-10 py-16 min-[960px]:grid-cols-[minmax(0,1.25fr)_minmax(330px,0.75fr)] min-[960px]:items-center min-[960px]:gap-8 xl:gap-12 max-sm:gap-8 max-sm:py-10">
            <div className="flex flex-col gap-7 max-sm:gap-6">
              {/* 모바일은 레일의 큰 고래가 CTA 아래로 밀리므로, 작은 고래를 첫 줄 오른쪽에 둔다. */}
              <div className="flex items-end justify-between gap-4">
                <span className="border-l-2 border-brand-500 pl-3 text-sm font-semibold text-brand-700">
                  깊은 바다에서 건져 올린 브릭
                </span>
                <Logo
                  size={104}
                  showWordmark={false}
                  spout
                  className="gole-mascot-float shrink-0 sm:hidden"
                />
              </div>
              <h1 className="max-w-[18ch] text-[clamp(2.6rem,5vw,4rem)] font-bold leading-[1.05] tracking-[-0.03em] text-neutral-900">
                브릭을{" "}
                <span className="relative isolate inline-block text-brand-700">
                  가장 합리적으로
                  <span
                    aria-hidden="true"
                    className="absolute inset-x-0 bottom-[0.04em] -z-10 h-[0.3em] rounded-sm bg-accent-300"
                  />
                </span>
              </h1>
              {/*
                두 문장을 block으로 두어 넓은 화면에서만 줄을 나눈다. `<br>`를 숨기는 방식은
                모바일에서 공백까지 함께 사라져 "컬렉션.흩어져"로 붙어버린다.
              */}
              <p className="max-w-[44ch] text-lg leading-relaxed text-neutral-600">
                <span className="block max-sm:inline">
                  {paymentsOpen
                    ? "체결가 기반 시세 · 안전결제 · 셀러 샵 · 컬렉션."
                    : sellerTradingOpen
                      ? "체결가 기반 시세 · 판매자 직거래 · 셀러 샵 · 컬렉션."
                      : "체결가 기반 시세 · 브릭 탐색 · 커뮤니티 · 컬렉션."}
                </span>{" "}
                <span className="block max-sm:inline">흩어져 있던 브릭 거래를 한곳에서.</span>
              </p>
              {/* 한 칸짜리 모바일 레이아웃에서는 우측 레일이 문구 아래로 내려가 CTA가 첫 화면 밖으로
                  밀린다(402×874에서 폴드보다 109px 아래). 모바일만 CTA를 문구 바로 아래에 둔다. */}
              <div className="sm:hidden">{heroActions}</div>
            </div>

            {/* 우측 레일 — 분수(브릭이 솟는다) → 라이브 지표(지금 얼마나 도는가) →
                CTA(그래서 무엇을 하는가) 순으로 한 줄기 시선을 만든다. 모바일에서는 고래·CTA가 문구 쪽에
                있으므로 지표만 남고, 지표도 없으면 빈 상자가 되지 않게 레일을 숨긴다. */}
            <div
              className={cn(
                "gole-hero-flow flex min-w-0 flex-col gap-5 rounded-[1.75rem] border border-brand-100 bg-brand-50 p-5 sm:max-[959px]:grid sm:max-[959px]:grid-cols-[minmax(260px,0.8fr)_minmax(260px,1.2fr)] sm:max-[959px]:items-center sm:p-6",
                liveStats.length === 0 && "max-sm:hidden",
              )}
            >
              <div className="relative flex min-h-48 items-center justify-center sm:max-[959px]:row-span-2 max-sm:hidden">
                <span
                  aria-hidden="true"
                  className="absolute right-[8%] bottom-3 left-[8%] h-px bg-gradient-to-r from-transparent via-brand-200 to-transparent"
                />
                <Logo size={256} showWordmark={false} spout className="gole-mascot-float" />
              </div>

              {liveStats.length > 0 ? (
                <div className="flex flex-col gap-3">
                  <div
                    aria-label={sellerTradingOpen ? "실시간 거래 현황" : "공개 콘텐츠 현황"}
                    className="divide-y divide-brand-100 border-y border-brand-100"
                  >
                    <div className="flex items-center gap-2 py-2.5">
                      <span aria-hidden="true" className="h-1.5 w-1.5 rounded-full bg-brand-500" />
                      <span className="text-xs font-semibold tracking-wide text-brand-700">
                        {sellerTradingOpen ? "실시간 거래 현황" : "공개 콘텐츠 현황"}
                      </span>
                    </div>
                    {liveStats.map((stat) => (
                      <div
                        key={stat.label}
                        className="flex items-baseline justify-between gap-4 py-3"
                      >
                        <span className="text-xs font-medium uppercase tracking-wide text-neutral-500">
                          {stat.label}
                        </span>
                        <span className="text-base font-bold tracking-tight text-neutral-900">
                          {stat.value}
                        </span>
                      </div>
                    ))}
                  </div>
                  {/* 지표 바로 아래에 붙여, 어느 레이아웃에서도 "어느 숫자"인지 위치어 없이 읽힌다. */}
                  <p className="text-sm leading-relaxed text-neutral-600">
                    가격은 감이 아니라 체결 기록에서 나옵니다.
                  </p>
                </div>
              ) : null}

              <div className="max-sm:hidden">{heroActions}</div>
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
                  {sellerTradingOpen
                    ? "잠자는 브릭, 바다로 보내세요"
                    : "GoLe 공개 준비에 함께해 주세요"}
                </h2>
                <p className="text-neutral-600">
                  {sellerTradingOpen
                    ? "사진 5장이면 등록 끝 — 시세 기반 추천가로 빠르게 판매됩니다."
                    : "신규 판매 등록은 본인확인 준비가 끝난 뒤 열립니다. 지금은 브릭을 탐색하고 커뮤니티에서 의견을 나눌 수 있어요."}
                </p>
              </div>
              {sellerTradingOpen ? (
                <LinkButton href="/sell" variant="primary" size="lg">
                  판매 시작하기
                </LinkButton>
              ) : (
                <div className="flex flex-wrap gap-2">
                  <LinkButton href="/community" variant="primary" size="lg">
                    커뮤니티 참여하기
                  </LinkButton>
                  <LinkButton
                    href="/chat?compose=support&category=PRODUCT_FEEDBACK"
                    variant="secondary"
                    size="lg"
                  >
                    의견 보내기
                  </LinkButton>
                </div>
              )}
            </div>
          </section>
        </div>
      </Container>
    </div>
  );
}
