import {
  LegoSetCard,
  fetchFeaturedLegoSets,
  type LegoSet,
} from "@entities/lego-set";
import { Container, Heading, LinkButton, Text } from "@shared/ui";

async function loadFeatured(): Promise<readonly LegoSet[]> {
  try {
    return await fetchFeaturedLegoSets();
  } catch {
    // 백엔드 미기동 등으로 조회 실패 시 빈 상태로 렌더한다.
    return [];
  }
}

export async function HomePage() {
  const featured = await loadFeatured();

  return (
    <Container width="xl">
      <div className="flex flex-col gap-16 pt-12 pb-20">
        <section className="relative isolate overflow-hidden rounded-2xl px-8 py-14 text-white shadow-lift bg-gradient-to-br from-brand-500 via-brand-600 to-brand-700 max-sm:px-5 max-sm:py-10">
          {/* 장식: 은은한 빛망울 + 그리드로 평면적인 히어로에 깊이를 준다. */}
          <div
            aria-hidden="true"
            className="pointer-events-none absolute -right-16 -top-24 h-72 w-72 rounded-full bg-brand-300/30 blur-3xl"
          />
          <div
            aria-hidden="true"
            className="pointer-events-none absolute -bottom-28 -left-10 h-72 w-72 rounded-full bg-brand-900/40 blur-3xl"
          />
          <div
            aria-hidden="true"
            className="pointer-events-none absolute inset-0 opacity-[0.07] [background-image:linear-gradient(white_1px,transparent_1px),linear-gradient(90deg,white_1px,transparent_1px)] [background-size:32px_32px]"
          />

          <div className="relative flex flex-col gap-5">
            <span className="inline-flex items-center gap-2 self-start rounded-full bg-white/15 px-3 py-1 text-sm font-semibold ring-1 ring-inset ring-white/25 backdrop-blur-sm">
              🧱 레고 마켓플레이스
            </span>
            <h1 className="max-w-[18ch] text-5xl font-bold leading-tight tracking-tight max-sm:text-4xl">
              GoLe — 레고 중고거래 플랫폼
            </h1>
            <p className="max-w-[48ch] text-lg leading-relaxed text-white/90">
              안전하게 사고팔고, 시세를 확인하고, 컬렉션을 자랑하세요. 검수 기반
              안전거래부터 동네 직거래까지 한곳에서.
            </p>
            <div className="mt-2 flex flex-wrap gap-3">
              <LinkButton href="/search" variant="secondary" size="lg">
                상품 둘러보기
              </LinkButton>
              <LinkButton
                href="/prices"
                variant="ghost"
                size="lg"
                className="border border-white/40 text-white hover:bg-white/15 hover:text-white"
              >
                시세 확인하기
              </LinkButton>
            </div>

            <dl className="mt-6 flex flex-wrap gap-x-10 gap-y-4 border-t border-white/15 pt-6">
              {HERO_STATS.map((stat) => (
                <div key={stat.label} className="flex flex-col gap-0.5">
                  <dt className="text-sm text-white/70">{stat.label}</dt>
                  <dd className="text-2xl font-bold tracking-tight">{stat.value}</dd>
                </div>
              ))}
            </dl>
          </div>
        </section>

        <section className="flex flex-col gap-6">
          <div className="flex items-baseline justify-between gap-4">
            <div className="flex items-center gap-3">
              <span
                aria-hidden="true"
                className="h-7 w-1.5 rounded-full bg-gradient-to-b from-brand-400 to-brand-600"
              />
              <Heading level={2}>오늘의 추천 세트</Heading>
            </div>
            <Text tone="secondary" size="sm">
              인기 테마에서 엄선한 세트
            </Text>
          </div>
          {featured.length > 0 ? (
            <div className="grid gap-5 [grid-template-columns:repeat(auto-fill,minmax(220px,1fr))]">
              {featured.map((set) => (
                <LegoSetCard key={set.setNumber} set={set} />
              ))}
            </div>
          ) : (
            <div className="flex flex-col items-center gap-2 rounded-2xl border border-dashed border-neutral-300 bg-white/60 px-6 py-16 text-center shadow-soft">
              <span aria-hidden="true" className="text-4xl">
                🧱
              </span>
              <Text tone="secondary" weight="medium">
                표시할 세트가 아직 없어요
              </Text>
              <Text tone="muted" size="sm">
                백엔드(API)가 실행 중인지 확인해 주세요.
              </Text>
            </div>
          )}
        </section>
      </div>
    </Container>
  );
}

const HERO_STATS: ReadonlyArray<{ readonly label: string; readonly value: string }> = [
  { label: "안전거래", value: "에스크로" },
  { label: "실시간 시세", value: "KREAM식" },
  { label: "동네 직거래", value: "당근식" },
];
