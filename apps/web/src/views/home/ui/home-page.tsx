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
        <section className="rounded-2xl border border-neutral-200/70 bg-white px-10 py-16 shadow-soft max-sm:px-6 max-sm:py-12">
          <div className="flex flex-col gap-5">
            <span className="inline-flex items-center gap-2 self-start rounded-full bg-accent-100 px-3 py-1 text-sm font-semibold text-accent-700">
              🧱 레고 마켓플레이스
            </span>
            <h1 className="max-w-[20ch] text-5xl font-bold leading-[1.12] tracking-tight text-neutral-900 max-sm:text-4xl">
              레고를 <span className="text-brand-500">더 깔끔하게</span> 사고팔다
            </h1>
            <p className="max-w-[46ch] text-lg leading-relaxed text-neutral-600">
              검수 기반 안전거래부터 동네 직거래까지. 시세를 확인하고 컬렉션을
              자랑하세요.
            </p>
            <div className="mt-3 flex flex-wrap gap-3">
              <LinkButton href="/search" size="lg">
                상품 둘러보기
              </LinkButton>
              <LinkButton href="/prices" variant="secondary" size="lg">
                시세 확인하기
              </LinkButton>
            </div>

            <dl className="mt-8 flex flex-wrap gap-x-12 gap-y-4 border-t border-neutral-100 pt-7">
              {HERO_STATS.map((stat) => (
                <div key={stat.label} className="flex flex-col gap-1">
                  <dt className="text-sm text-neutral-500">{stat.label}</dt>
                  <dd className="text-xl font-bold tracking-tight text-neutral-900">
                    {stat.value}
                  </dd>
                </div>
              ))}
            </dl>
          </div>
        </section>

        <section className="flex flex-col gap-6">
          <div className="flex items-baseline justify-between gap-4">
            <Heading level={2}>오늘의 추천 세트</Heading>
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
            <div className="flex flex-col items-center gap-2 rounded-2xl border border-dashed border-neutral-200 px-6 py-16 text-center">
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
  { label: "안전결제", value: "에스크로 보호" },
  { label: "실시간 시세", value: "체결가 기반" },
  { label: "거래 방식", value: "직거래·택배" },
];
