import { LegoSetCard, fetchFeaturedLegoSets, type LegoSet } from "@entities/lego-set";
import { fetchFeed, type Post } from "@entities/community";
import { fetchTrendingSets, type TrendingSet } from "@entities/pricing";
import { TrendingSets } from "@widgets/trending-sets";
import { PostCard } from "@widgets/post-card";
import { Container, Heading, LinkButton, Text } from "@shared/ui";

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

export async function HomePage() {
  const [featured, trending, community] = await Promise.all([
    loadFeatured(),
    loadTrending(),
    loadCommunity(),
  ]);

  return (
    <Container width="xl">
      <div className="flex flex-col gap-20 pt-14 pb-24">
        {/* Hero */}
        <section className="relative overflow-hidden rounded-3xl bg-gradient-to-br from-brand-50 via-white to-accent-50/30 px-10 py-20 shadow-soft max-sm:px-6 max-sm:py-14">
          {/* subtle decorative circle */}
          <div
            aria-hidden="true"
            className="pointer-events-none absolute -right-24 -top-24 h-72 w-72 rounded-full bg-brand-100/40 blur-3xl"
          />
          <div className="relative flex flex-col gap-6">
            <span className="inline-flex items-center gap-2 self-start rounded-full bg-brand-500/10 px-3.5 py-1.5 text-sm font-semibold text-brand-600 backdrop-blur-sm">
              🧱 레고 중고거래
            </span>
            <h1 className="max-w-[18ch] text-[clamp(2.4rem,5.5vw,3.75rem)] font-extrabold leading-[1.08] tracking-[-0.03em] text-neutral-900">
              레고를{" "}
              <span className="bg-gradient-to-r from-brand-600 to-brand-800 bg-clip-text text-transparent">
                가장 합리적으로
              </span>
            </h1>
            <p className="max-w-[44ch] text-lg leading-relaxed text-neutral-500">
              체결가 시세 · 에스크로 안전거래 · 셀러 샵 · 컬렉션을 한곳에서.
            </p>
            <div className="mt-4 flex flex-wrap gap-3">
              <LinkButton href="/search" size="lg">
                상품 둘러보기
              </LinkButton>
              <LinkButton href="/prices" variant="secondary" size="lg">
                시세 확인하기
              </LinkButton>
            </div>

            <div className="mt-10 grid grid-cols-3 gap-4 max-sm:grid-cols-1">
              {HERO_STATS.map((stat) => (
                <div
                  key={stat.label}
                  className="flex flex-col gap-1 rounded-xl border border-neutral-200/60 bg-white/70 px-5 py-4 backdrop-blur-sm"
                >
                  <span className="text-xs font-medium uppercase tracking-wide text-neutral-400">
                    {stat.label}
                  </span>
                  <span className="text-lg font-bold tracking-tight text-neutral-900">
                    {stat.value}
                  </span>
                </div>
              ))}
            </div>
          </div>
        </section>

        {/* Trending */}
        <section className="flex flex-col gap-6">
          <div className="flex items-baseline justify-between gap-4">
            <Heading level={2}>지금 뜨는 세트</Heading>
            <Text tone="muted" size="sm">
              최근 거래 활발
            </Text>
          </div>
          <TrendingSets items={trending} />
        </section>

        {/* Featured */}
        <section className="flex flex-col gap-6">
          <div className="flex items-baseline justify-between gap-4">
            <Heading level={2}>오늘의 추천</Heading>
            <Text tone="muted" size="sm">
              인기 테마 엄선
            </Text>
          </div>
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
            <div className="flex items-baseline justify-between gap-4">
              <Heading level={2}>커뮤니티</Heading>
              <LinkButton href="/community" variant="ghost" size="sm">
                전체 보기
              </LinkButton>
            </div>
            <div className="grid gap-5 [grid-template-columns:repeat(auto-fill,minmax(240px,1fr))]">
              {community.map((post) => (
                <PostCard key={post.id} post={post} />
              ))}
            </div>
          </section>
        ) : null}
      </div>
    </Container>
  );
}

const HERO_STATS: ReadonlyArray<{ readonly label: string; readonly value: string }> = [
  { label: "안전결제", value: "에스크로 보호" },
  { label: "실시간 시세", value: "체결가 기반" },
  { label: "거래 방식", value: "직거래 · 택배" },
];
