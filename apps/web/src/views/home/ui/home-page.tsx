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
        <section className="flex flex-col gap-5 rounded-xl px-8 py-12 text-white shadow-lg bg-gradient-to-br from-brand-500 to-brand-700 max-sm:px-5 max-sm:py-8">
          <span className="inline-flex items-center gap-2 self-start rounded-full bg-white/20 px-3 py-1 text-sm font-semibold">
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
              className="border border-white/50 text-white hover:bg-white/15 hover:text-white"
            >
              시세 확인하기
            </LinkButton>
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
            <Text tone="muted">
              표시할 세트가 없습니다. 백엔드(API)가 실행 중인지 확인해 주세요.
            </Text>
          )}
        </section>
      </div>
    </Container>
  );
}
