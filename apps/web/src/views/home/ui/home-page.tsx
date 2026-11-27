import type { ReactNode } from "react";
import Link from "next/link";
import { LegoSetCard, fetchFeaturedLegoSets } from "@entities/lego-set";
import { fetchFeed } from "@entities/community";
import { fetchTrendingSets } from "@entities/pricing";
import { fetchLaunchConfig } from "@entities/launch";
import { fetchActiveListings, ListingCard } from "@entities/listing";
import { TrendingSets } from "@widgets/trending-sets";
import { PostCard } from "@widgets/post-card";
import { BrickIcon, Container, EmptyState, Heading, LinkButton, Logo, Text } from "@shared/ui";
import { serverSessionHeaders } from "@shared/api/server-session-headers";
import { PriceTicker } from "./price-ticker";
import { emptyListingsContent } from "../model/empty-listings";
import { homeIntroduction, loadHomeCollection } from "../model/discovery-state";

/** 실제 목록의 제목과 다음 이동을 같은 기준선에 놓는다. */
function SectionHeader({ title, aside }: { readonly title: string; readonly aside?: ReactNode }) {
  return (
    <div className="flex flex-wrap items-end justify-between gap-3">
      <Heading level={2}>{title}</Heading>
      {aside}
    </div>
  );
}

export async function HomePage() {
  const [featured, trending, community, listings, launch] = await Promise.all([
    loadHomeCollection(fetchFeaturedLegoSets),
    loadHomeCollection(() => fetchTrendingSets(8)),
    loadHomeCollection(async () => {
      const posts = await fetchFeed({ headers: await serverSessionHeaders(), limit: 4 });
      return posts.slice(0, 4);
    }),
    loadHomeCollection(fetchActiveListings),
    fetchLaunchConfig(),
  ]);
  const sellerTradingOpen = launch.sellerIdentityVerificationReady;
  const emptyListings = emptyListingsContent(sellerTradingOpen);
  const introduction = homeIntroduction(sellerTradingOpen);
  const txCount = trending?.reduce((sum, set) => sum + set.tradeCount, 0) ?? 0;
  // 데이터가 없는 칸을 마케팅 문구로 채우면 "실시간"이라는 라벨이 거짓이 된다.
  const liveStats: ReadonlyArray<{ readonly label: string; readonly value: string }> = [
    ...(listings !== null && listings.length > 0
      ? [{ label: "활성 매물", value: `${listings.length.toLocaleString("ko-KR")}개` }]
      : []),
    ...(txCount > 0
      ? [{ label: "거래 상위 세트 체결 합계", value: `${txCount.toLocaleString("ko-KR")}건` }]
      : []),
  ];

  return (
    <div className="flex flex-col">
      <section className="bg-white">
        <Container width="xl">
          <div className="grid items-center gap-10 py-10 sm:py-14 lg:grid-cols-[minmax(0,1.15fr)_minmax(0,0.85fr)] lg:gap-16 lg:py-16">
            <div className="flex min-w-0 flex-col items-start gap-5 sm:gap-6">
              <div className="flex w-full items-center justify-between gap-4">
                <p className="text-sm font-semibold break-keep text-brand-700">
                  {introduction.label}
                </p>
                <Logo size={64} showWordmark={false} className="lg:hidden" />
              </div>
              <h1 className="text-[clamp(2.5rem,5vw,4.25rem)] font-extrabold leading-[1.12] tracking-[-0.035em]">
                다음 브릭을 <span className="block">만나는 곳.</span>
              </h1>
              <p className="max-w-[38ch] text-base leading-relaxed break-keep text-neutral-600 sm:text-lg">
                {introduction.description}
              </p>
              <div className="flex flex-wrap gap-2 pt-1 sm:gap-3">
                <LinkButton href="/search" variant="primary">
                  상품 둘러보기
                </LinkButton>
                <LinkButton href={sellerTradingOpen ? "/prices" : "/community"} variant="secondary">
                  {sellerTradingOpen ? "시세 확인하기" : "커뮤니티 보기"}
                </LinkButton>
              </div>
              {liveStats.length > 0 ? (
                <dl
                  aria-label="공개 거래 현황"
                  className="flex flex-wrap gap-x-6 gap-y-2 pt-2 text-sm"
                >
                  {liveStats.map((stat) => (
                    <div key={stat.label} className="flex items-baseline gap-2">
                      <dt className="text-neutral-500">{stat.label}</dt>
                      <dd className="font-semibold tabular-nums text-neutral-800">{stat.value}</dd>
                    </div>
                  ))}
                </dl>
              ) : null}
              {txCount > 0 ? (
                <p className="text-xs leading-relaxed text-neutral-500">
                  체결 기록 수 상위 최대 8세트의 합계예요. 최근 30일 기준이며, 이 기간에 기록이
                  없으면 전체 기간을 집계해요.
                </p>
              ) : null}
            </div>
            <div className="relative hidden aspect-[1.3] items-center justify-center overflow-hidden rounded-2xl bg-brand-50 lg:flex">
              <Logo size={300} showWordmark={false} spout />
              <span className="absolute inset-x-0 bottom-7 text-center font-display text-base font-bold text-brand-700">
                브릭으로 이어지는 취향
              </span>
            </div>
          </div>
        </Container>
        <PriceTicker items={trending ?? []} />
      </section>

      <Container width="xl">
        <div className="flex flex-col gap-12 pt-10 pb-16 sm:gap-16 sm:pt-12 lg:gap-20 lg:pt-16 lg:pb-24">
          <section className="flex flex-col gap-6" aria-label="최근 판매 매물">
            <SectionHeader
              title="방금 올라온 매물"
              aside={
                <LinkButton href="/search" variant="ghost" size="sm">
                  전체 보기
                </LinkButton>
              }
            />
            {listings === null ? (
              <EmptyState
                variant="inline"
                title="매물을 불러오지 못했어요"
                description="잠시 후 상품 탐색에서 다시 확인해 주세요."
                action={
                  <LinkButton href="/search" variant="secondary">
                    상품 탐색에서 확인하기
                  </LinkButton>
                }
              />
            ) : listings.length === 0 ? (
              <EmptyState
                variant="inline"
                title={emptyListings.title}
                description={emptyListings.description}
                action={
                  <LinkButton href={emptyListings.href} variant="secondary">
                    {emptyListings.action}
                  </LinkButton>
                }
              />
            ) : (
              <div className="grid grid-cols-1 gap-3 min-[360px]:grid-cols-2 sm:grid-cols-3 sm:gap-5">
                {listings.slice(0, 6).map((listing) => (
                  <Link key={listing.id} href={`/listings/${listing.id}`}>
                    <ListingCard listing={listing} />
                  </Link>
                ))}
              </div>
            )}
          </section>
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
            {trending === null ? (
              <EmptyState
                variant="inline"
                title="인기 세트를 불러오지 못했어요"
                description="잠시 후 시세 화면에서 다시 확인해 주세요."
                action={
                  <LinkButton href="/prices" variant="secondary">
                    시세에서 확인하기
                  </LinkButton>
                }
              />
            ) : (
              <TrendingSets items={trending} />
            )}
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
            {featured === null ? (
              <EmptyState
                variant="inline"
                title="추천 세트를 불러오지 못했어요"
                description="잠시 후 탐색 화면에서 다시 확인해 주세요."
                action={
                  <LinkButton href="/search" variant="secondary">
                    세트 탐색하기
                  </LinkButton>
                }
              />
            ) : featured.length > 0 ? (
              // 휴대폰은 검색 목록과 같이 360px부터 2열로 나란히 본다(1열은 카드 한 장이 화면을 다 차지했다).
              <div className="grid grid-cols-1 gap-3 min-[360px]:grid-cols-2 sm:[grid-template-columns:repeat(auto-fill,minmax(240px,1fr))] sm:gap-5">
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
          <section className="flex flex-col gap-6" aria-label="커뮤니티 소식">
            <SectionHeader
              title="커뮤니티"
              aside={
                <LinkButton href="/community" variant="ghost" size="sm">
                  전체 보기
                </LinkButton>
              }
            />
            {community === null ? (
              <EmptyState
                variant="inline"
                title="브릭 이야기를 불러오지 못했어요"
                description="잠시 후 커뮤니티에서 다시 확인해 주세요."
                action={
                  <LinkButton href="/community" variant="secondary">
                    커뮤니티에서 확인하기
                  </LinkButton>
                }
              />
            ) : community.length === 0 ? (
              <EmptyState
                variant="inline"
                title="첫 브릭 이야기를 기다리고 있어요"
                description="좋아하는 세트와 조립 경험을 함께 나눠 보세요."
                action={
                  <LinkButton href="/community" variant="secondary">
                    커뮤니티 둘러보기
                  </LinkButton>
                }
              />
            ) : (
              <div className="grid gap-5 [grid-template-columns:repeat(auto-fill,minmax(240px,1fr))]">
                {community.map((post) => (
                  <PostCard key={post.id} post={post} />
                ))}
              </div>
            )}
          </section>

          <section className="rounded-xl border border-brand-200 bg-brand-50 px-5 py-8 sm:px-10 sm:py-12">
            <div className="flex flex-wrap items-center justify-between gap-6">
              <div className="flex flex-col gap-2">
                <h2 className="text-2xl font-bold tracking-tight text-neutral-900">
                  {sellerTradingOpen
                    ? "잠자는 브릭, 바다로 보내세요"
                    : "GoLe 공개 준비에 함께해 주세요"}
                </h2>
                <p className="text-neutral-600">
                  {sellerTradingOpen
                    ? "브릭의 상태와 사진을 남기고, 다음 컬렉터를 만나보세요."
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
